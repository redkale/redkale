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
import java.util.concurrent.atomic.AtomicLong;
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

    //TCP Channel组
    protected AsynchronousChannelGroup group;

    protected final ArrayBlockingQueue<AsyncConnection> connQueue;

    public PoolTcpSource(String rwtype, ArrayBlockingQueue queue, Properties prop, Logger logger, ObjectPool<ByteBuffer> bufferPool, ThreadPoolExecutor executor) {
        super(rwtype, prop, logger);
        this.bufferPool = bufferPool;
        this.executor = executor;
        try {
            this.group = AsynchronousChannelGroup.withCachedThreadPool(executor, executor.getCorePoolSize());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.connQueue = queue == null ? new ArrayBlockingQueue<>(this.maxconns) : queue;
    }

    @Override
    public void offerConnection(final AsyncConnection conn) {
        if (conn == null) return;
        if (connQueue.offer(conn)) {
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

    private static final AtomicLong nowlong = new AtomicLong();

    protected CompletableFuture<AsyncConnection> pollAsync(final int count) {
        if (count >= 3) {
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
        //logqueue.add("-------semaphore: " + semaphore.availablePermits());
        if (!semaphore.tryAcquire()) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return connQueue.poll(3, TimeUnit.SECONDS);
                } catch (Exception t) {
                    System.out.println("超时了");
                    t.printStackTrace();
                    return null;
                }
            }).thenCompose((conn2) -> {
                if (conn2 != null && conn2.isOpen()) {
                    cycleCounter.incrementAndGet();
                    usingCounter.incrementAndGet();
                    return CompletableFuture.completedFuture(conn2);
                }
                return pollAsync(count + 1);
            });
        }

        return AsyncConnection.createTCP(group, this.servaddr, this.readTimeoutSeconds, this.writeTimeoutSeconds).thenCompose(conn -> {
            conn.beforeCloseListener((c) -> {
                semaphore.release();
                usingCounter.decrementAndGet();
            });
            CompletableFuture<AsyncConnection> future = new CompletableFuture();
            final ByteBuffer buffer = reqConnectBuffer(conn);

            if (buffer == null) {
                final ByteBuffer rbuffer = bufferPool.get();
                conn.read(rbuffer, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment2) {
                        if (result < 0) {
                            failed(new SQLException("Read Buffer Error"), attachment2);
                            return;
                        }
                        rbuffer.flip();
                        respConnectBuffer(rbuffer, future, conn);
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment2) {
                        bufferPool.accept(rbuffer);
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
                        conn.read(buffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment2) {
                                if (result < 0) {
                                    failed(new SQLException("Read Buffer Error"), attachment2);
                                    return;
                                }
                                buffer.flip();
                                respConnectBuffer(buffer, future, conn);
                            }

                            @Override
                            public void failed(Throwable exc, Void attachment2) {
                                bufferPool.accept(buffer);
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
