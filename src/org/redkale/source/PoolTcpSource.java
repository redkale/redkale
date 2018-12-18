/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.net.AsyncConnection;
import static org.redkale.source.DataSources.*;
import org.redkale.util.ObjectPool;

/**
 *
 * @author zhangjx
 */
public abstract class PoolTcpSource extends PoolSource<AsyncConnection> {

    //ByteBuffer池
    protected ObjectPool<ByteBuffer> bufferPool;

    //线程池
    protected ThreadPoolExecutor executor;

    //供supplyAsync->poll使用的线程池
    protected ExecutorService pollExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4, (r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    //TCP Channel组
    protected AsynchronousChannelGroup group;

    protected final ArrayBlockingQueue<AsyncConnection> connQueue;

    public PoolTcpSource(String rwtype, ArrayBlockingQueue queue, Semaphore semaphore, Properties prop, Logger logger, ObjectPool<ByteBuffer> bufferPool, ThreadPoolExecutor executor) {
        super(rwtype, semaphore, prop, logger);
        this.bufferPool = bufferPool;
        this.executor = executor;
        try {
            this.group = AsynchronousChannelGroup.withThreadPool(executor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.connQueue = queue == null ? new ArrayBlockingQueue<>(this.maxconns) : queue;
    }

    @Override
    public void offerConnection(final AsyncConnection conn) {
        if (conn == null) return;
        if (conn.isOpen() && connQueue.offer(conn)) {
            saveCounter.incrementAndGet();
            usingCounter.decrementAndGet();
        } else {
            //usingCounter 会在close方法中执行
            CompletableFuture<AsyncConnection> future = null;
            try {
                future = sendCloseCommand(conn);
            } catch (Exception e) {
            }
            if (future == null) {
                conn.dispose();
            } else {
                future.whenComplete((c, t) -> {
                    if (c != null) c.dispose();
                });
            }
        }
    }

    @Override
    public void change(Properties prop) {
        String newurl = prop.getProperty(JDBC_URL, this.url);
        String newuser = prop.getProperty(JDBC_USER, this.username);
        String newpassword = prop.getProperty(JDBC_PWD, this.password);
        if (Objects.equals(this.url, newurl) && Objects.equals(this.username, newuser) && Objects.equals(this.password, newpassword)) return;
        this.url = newurl;
        this.username = newuser;
        this.password = newpassword;
        parseAddressAndDbnameAndAttrs();
    }

    @Override
    public final AsyncConnection poll() {
        return pollAsync().join();
    }

    protected abstract ByteBuffer reqConnectBuffer(AsyncConnection conn);

    protected abstract void respConnectBuffer(final ByteBuffer buffer, CompletableFuture<AsyncConnection> future, AsyncConnection conn);

    @Override
    public CompletableFuture<AsyncConnection> pollAsync() {
        return pollAsync(0);
    }

    protected CompletableFuture<AsyncConnection> pollAsync(final int count) {
        if (count >= 5) {
            logger.log(Level.WARNING, "create datasource connection error");
            CompletableFuture<AsyncConnection> future = new CompletableFuture<>();
            future.completeExceptionally(new SQLException("create datasource connection error"));
            return future;
        }

        AsyncConnection conn0 = connQueue.poll();
        if (conn0 != null && conn0.isOpen()) {
            cycleCounter.incrementAndGet();
            usingCounter.incrementAndGet();
            return CompletableFuture.completedFuture(conn0);
        }

        if (!semaphore.tryAcquire()) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return connQueue.poll(1, TimeUnit.SECONDS);
                } catch (Exception t) {
                    return null;
                }
            }, pollExecutor).thenCompose((conn2) -> {
                if (conn2 != null && conn2.isOpen()) {
                    cycleCounter.incrementAndGet();
                    usingCounter.incrementAndGet();
                    return CompletableFuture.completedFuture(conn2);
                }
                return pollAsync(count + 1);
            });
        }

        return AsyncConnection.createTCP(bufferPool, group, this.servaddr, this.readTimeoutSeconds, this.writeTimeoutSeconds).thenCompose(conn -> {
            conn.beforeCloseListener((c) -> {
                semaphore.release();
                closeCounter.incrementAndGet();
                usingCounter.decrementAndGet();
            });
            CompletableFuture<AsyncConnection> future = new CompletableFuture();
            final ByteBuffer buffer = reqConnectBuffer(conn);

            if (buffer == null) {
                conn.read(new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer result, ByteBuffer rbuffer) {
                        if (result < 0) {
                            failed(new SQLException("Read Buffer Error"), rbuffer);
                            return;
                        }
                        rbuffer.flip();
                        respConnectBuffer(rbuffer, future, conn);
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer rbuffer) {
                        conn.offerBuffer(rbuffer);
                        future.completeExceptionally(exc);
                        conn.dispose();
                    }
                });
            } else {
                conn.write(buffer, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment1) {
                        if (result < 0) {
                            failed(new SQLException("Write Buffer Error"), attachment1);
                            return;
                        }
                        if (buffer.hasRemaining()) {
                            conn.write(buffer, attachment1, this);
                            return;
                        }
                        buffer.clear();
                        conn.setReadBuffer(buffer);
                        conn.read(new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer result, ByteBuffer rbuffer) {
                                if (result < 0) {
                                    failed(new SQLException("Read Buffer Error"), rbuffer);
                                    return;
                                }
                                buffer.flip();
                                respConnectBuffer(buffer, future, conn);
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer rbuffer) {
                                conn.offerBuffer(rbuffer);
                                future.completeExceptionally(exc);
                                conn.dispose();
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment1) {
                        bufferPool.accept(buffer);
                        future.completeExceptionally(exc);
                        conn.dispose();
                    }
                });
            }
            return future;
        }).whenComplete((c, t) -> {
            if (t == null) {
                creatCounter.incrementAndGet();
                usingCounter.incrementAndGet();
            } else {
                semaphore.release();
            }
        });
    }

    @Override
    public void close() {
        connQueue.stream().forEach(x -> {
            CompletableFuture<AsyncConnection> future = null;
            try {
                future = sendCloseCommand(x);
            } catch (Exception e) {
            }
            if (future == null) {
                x.dispose();
            } else {
                future.whenComplete((c, t) -> {
                    if (c != null) c.dispose();
                });
            }
        });
    }

    protected abstract CompletableFuture<AsyncConnection> sendCloseCommand(final AsyncConnection conn);
}
