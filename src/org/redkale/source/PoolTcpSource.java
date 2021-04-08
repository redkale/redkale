/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.net.*;
import static org.redkale.source.DataSources.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class PoolTcpSource extends PoolSource<AsyncConnection> {

    protected AsyncGroup asyncGroup;

    protected ScheduledThreadPoolExecutor scheduler;

    protected ArrayBlockingQueue<CompletableFuture<AsyncConnection>> pollQueue;

    protected ArrayBlockingQueue<AsyncConnection> connQueue;

    public PoolTcpSource(AsyncGroup asyncGroup, String rwtype, ArrayBlockingQueue queue, Semaphore semaphore, Properties prop, Logger logger) {
        super(rwtype, semaphore, prop, logger);
        this.asyncGroup = asyncGroup;
        this.connQueue = queue == null ? new ArrayBlockingQueue<>(this.maxconns) : queue;
        this.pollQueue = new ArrayBlockingQueue(this.connQueue.remainingCapacity() * 1000);
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-PoolSource-Scheduled-Thread");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(() -> {
            runPingTask();
        }, 60, 30, TimeUnit.SECONDS);
    }

    public void updateConnQueue(ArrayBlockingQueue queue) {
        ArrayBlockingQueue<AsyncConnection> old = this.connQueue;
        this.connQueue = queue;
        AsyncConnection conn;
        final Semaphore localSemaphore = this.semaphore;
        while ((conn = old.poll()) != null) {
            queue.offer(conn);
            if (localSemaphore != null) localSemaphore.tryAcquire();
        }
    }

    private void runPingTask() {
        try {
            if (connQueue.isEmpty()) return;
            long time = System.currentTimeMillis() - 30 * 1000;
            AsyncConnection first = connQueue.peek();
            if (first == null || first.getLastReadTime() >= time || first.getLastWriteTime() >= time) return;
            pollAsync().whenComplete((conn, e) -> {
                if (e != null) return;
                if (conn.getLastReadTime() >= time || conn.getLastWriteTime() >= time) {//半分钟内已经用过
                    offerConnection(conn);
                    return;
                }
                CompletableFuture<AsyncConnection> future = sendPingCommand(conn);
                if (future == null) {     //不支持ping                
                    offerConnection(conn);
                    return;
                }
                future.whenComplete((conn2, e2) -> {
                    if (e2 != null) return;
                    offerConnection(conn2);
                    runPingTask();
                });
            });
        } catch (Exception e) {
            logger.log(Level.FINEST, "PoolSource task ping failed", e);
        }
    }

    @Override
    public void offerConnection(final AsyncConnection conn) {
        if (conn == null) return;
        if (conn.isOpen()) {
            CompletableFuture<AsyncConnection> future = pollQueue.poll();
            if (future != null && future.complete(conn)) return;

            if (connQueue.offer(conn)) {
                saveCounter.incrementAndGet();
                usingCounter.decrementAndGet();
                return;
            }
        }
        //usingCounter 会在close方法中执行
        CompletableFuture<AsyncConnection> closefuture = null;
        try {
            closefuture = sendCloseCommand(conn);
        } catch (Exception e) {
        }
        if (closefuture == null) {
            conn.dispose();
        } else {
            closefuture.whenComplete((c, t) -> {
                if (c != null) c.dispose();
            });
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

    protected abstract ByteArray reqConnectBuffer(AsyncConnection conn);

    protected abstract void respConnectBuffer(final ByteBuffer buffer, CompletableFuture<AsyncConnection> future, AsyncConnection conn);

    @Override
    public CompletableFuture<AsyncConnection> pollAsync() {

        AsyncConnection conn0 = connQueue.poll();
        if (conn0 != null && conn0.isOpen()) {
            cycleCounter.incrementAndGet();
            usingCounter.incrementAndGet();
            return CompletableFuture.completedFuture(conn0);
        }
        final Semaphore localSemaphore = this.semaphore;
        if (!localSemaphore.tryAcquire()) {
            final CompletableFuture<AsyncConnection> future = Utility.orTimeout(new CompletableFuture<>(), 10, TimeUnit.SECONDS);
            future.whenComplete((r, t) -> pollQueue.remove(future));
            if (pollQueue.offer(future)) return future;
            future.completeExceptionally(new SQLException("create datasource connection error"));
            return future;
        }
        return asyncGroup.createTCP(this.servaddr, this.readTimeoutSeconds, this.writeTimeoutSeconds).thenCompose(conn -> {
            conn.beforeCloseListener((c) -> {
                localSemaphore.release();
                closeCounter.incrementAndGet();
                usingCounter.decrementAndGet();
            });
            CompletableFuture<AsyncConnection> future = new CompletableFuture();
            if (conn.getSubobject() == null) conn.setSubobject(new ByteArray());
            final ByteArray array = reqConnectBuffer(conn);
            if (array == null) {
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
                conn.write(array, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result0, Void attachment0) {
                        if (result0 < 0) {
                            failed(new SQLException("Write Buffer Error"), attachment0);
                            return;
                        }
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
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment0) {
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
                localSemaphore.release();
            }
        });
    }

    public ArrayBlockingQueue<CompletableFuture<AsyncConnection>> getPollQueue() {
        return pollQueue;
    }

    public void setPollQueue(ArrayBlockingQueue<CompletableFuture<AsyncConnection>> pollQueue) {
        this.pollQueue = pollQueue;
    }

    public ArrayBlockingQueue<AsyncConnection> getConnQueue() {
        return connQueue;
    }

    public void setConnQueue(ArrayBlockingQueue<AsyncConnection> connQueue) {
        this.connQueue = connQueue;
    }

    @Override
    public void close() {
        this.scheduler.shutdownNow();
        final List<CompletableFuture> futures = new ArrayList<>();
        connQueue.stream().forEach(x -> {
            CompletableFuture<AsyncConnection> future = null;
            try {
                future = sendCloseCommand(x);
            } catch (Exception e) {
            }
            if (future == null) {
                x.dispose();
            } else {
                futures.add(future.whenComplete((c, t) -> {
                    if (c != null) c.dispose();
                }));
            }
        });
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(new CompletableFuture[futures.size()]).join();
        }
    }

    protected abstract CompletableFuture<AsyncConnection> sendPingCommand(final AsyncConnection conn);

    protected abstract CompletableFuture<AsyncConnection> sendCloseCommand(final AsyncConnection conn);
}
