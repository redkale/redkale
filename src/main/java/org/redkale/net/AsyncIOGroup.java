/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.redkale.annotation.ResourceType;
import org.redkale.util.*;

/**
 * 协议处理的IO线程组
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
@ResourceType(AsyncGroup.class)
public class AsyncIOGroup extends AsyncGroup {

    private boolean started;

    private boolean closed;

    AsyncIOThread[] ioThreads;

    private AsyncIOThread connectThread;

    final int bufferCapacity;

    final AtomicInteger shareCount = new AtomicInteger(1);

    private final AtomicInteger readIndex = new AtomicInteger();

    //创建数
    final LongAdder connCreateCounter = new LongAdder();

    //关闭数
    final LongAdder connLivingCounter = new LongAdder();

    //在线数
    final LongAdder connClosedCounter = new LongAdder();

    private ScheduledThreadPoolExecutor timeoutExecutor;

    public AsyncIOGroup(final int bufferCapacity, final int bufferPoolSize) {
        this(true, null, null, bufferCapacity, bufferPoolSize);
    }

    public AsyncIOGroup(boolean client, String threadPrefixName, final ExecutorService workExecutor, final int bufferCapacity, final int bufferPoolSize) {
        this(client, threadPrefixName, workExecutor, bufferCapacity, ObjectPool.createSafePool(null, null, bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) {
                    return false;
                }
                e.clear();
                return true;
            }));
    }

    public AsyncIOGroup(boolean client, String threadPrefixName0, ExecutorService workExecutor, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        this.bufferCapacity = bufferCapacity;
        final String threadPrefixName = threadPrefixName0 == null ? "Redkale-Client-IOThread" : threadPrefixName0;
        this.ioThreads = new AsyncIOThread[Utility.cpus()];
        try {
            for (int i = 0; i < this.ioThreads.length; i++) {
                ObjectPool<ByteBuffer> unsafeBufferPool = ObjectPool.createUnsafePool(safeBufferPool, safeBufferPool.getCreatCounter(),
                    safeBufferPool.getCycleCounter(), 512, safeBufferPool.getCreator(), safeBufferPool.getPrepare(), safeBufferPool.getRecycler());
                String name = threadPrefixName + "-" + (i >= 9 ? (i + 1) : ("0" + (i + 1)));
                this.ioThreads[i] = client ? new ClientIOThread(name, i, ioThreads.length, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool)
                    : new AsyncIOThread(name, i, ioThreads.length, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool);
            }
            if (client) {
                ObjectPool<ByteBuffer> unsafeBufferPool = ObjectPool.createUnsafePool(safeBufferPool, safeBufferPool.getCreatCounter(),
                    safeBufferPool.getCycleCounter(), 512, safeBufferPool.getCreator(), safeBufferPool.getPrepare(), safeBufferPool.getRecycler());
                String name = threadPrefixName.replace("ServletThread", "ConnectThread").replace("IOThread", "IOConnectThread");
                this.connectThread = client ? new ClientIOThread(name, 0, 0, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool)
                    : new AsyncIOThread(name, 0, 0, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setName(threadPrefixName + "-Timeout");
            t.setDaemon(true);
            return t;
        });
    }

    public int size() {
        return this.ioThreads.length;
    }

    @Override
    public AsyncGroup start() {
        if (started) {
            return this;
        }
        if (closed) {
            throw new RuntimeException("group is closed");
        }
        for (AsyncIOThread thread : ioThreads) {
            thread.start();
        }
        if (connectThread != null) {
            connectThread.start();
        }
        started = true;
        return this;
    }

    @Override
    public AsyncGroup close() {
        if (shareCount.decrementAndGet() > 0) {
            return this;
        }
        if (closed) {
            return this;
        }
        for (AsyncIOThread thread : ioThreads) {
            thread.close();
        }
        if (connectThread != null) {
            connectThread.close();
        }
        this.timeoutExecutor.shutdownNow();
        closed = true;
        return this;
    }

    public LongAdder getCreateConnectionCount() {
        return connCreateCounter;
    }

    public LongAdder getClosedConnectionCount() {
        return connLivingCounter;
    }

    public LongAdder getLivingConnectionCount() {
        return connClosedCounter;
    }

    public AsyncIOThread nextIOThread() {
        return ioThreads[Math.abs(readIndex.getAndIncrement()) % ioThreads.length];
    }

    public AsyncIOThread connectThread() {
        return connectThread;
    }

    @Override
    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    public void interestOpsOr(AsyncIOThread thread, SelectionKey key, int opt) {
        if (key == null) {
            return;
        }
        if (key.selector() != thread.selector) {
            throw new RuntimeException("NioThread.selector not the same to SelectionKey.selector");
        }
        if ((key.interestOps() & opt) != 0) {
            return;
        }
        key.interestOps(key.interestOps() | opt);
        if (thread.inCurrThread()) {
//            timeoutExecutor.execute(() -> {
//                try {
//                    key.selector().wakeup();
//                } catch (Throwable t) {
//                }
//            });
        } else {
            //非IO线程中
            key.selector().wakeup();
        }
    }

    @Override
    public CompletableFuture<AsyncConnection> createTCPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        SocketChannel channel;
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        AsyncIOThread ioThread = null;
        Thread currThread = Thread.currentThread();
        if (currThread instanceof AsyncIOThread) {
            for (AsyncIOThread thread : ioThreads) {
                if (thread == currThread) {
                    ioThread = thread;
                    break;
                }
            }
        }
        if (ioThread == null) {
            ioThread = nextIOThread();
        }
        final AsyncNioTcpConnection conn = new AsyncNioTcpConnection(true, this, ioThread, connectThread, channel, null, null, address, connLivingCounter, connClosedCounter);
        final CompletableFuture<AsyncConnection> future = new CompletableFuture<>();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                conn.setReadTimeoutSeconds(readTimeoutSeconds);
                conn.setWriteTimeoutSeconds(writeTimeoutSeconds);
                if (connCreateCounter != null) {
                    connCreateCounter.increment();
                }
                if (connLivingCounter != null) {
                    connLivingCounter.increment();
                }
                if (conn.sslEngine == null) {
                    future.complete(conn);
                } else {
                    conn.startHandshake(t -> {
                        if (t == null) {
                            future.complete(conn);
                        } else {
                            future.completeExceptionally(t);
                        }
                    });
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return Utility.orTimeout(future, 30, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<AsyncConnection> createUDPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        }
        AsyncIOThread ioThread = null;
        Thread currThread = Thread.currentThread();
        if (currThread instanceof AsyncIOThread) {
            for (AsyncIOThread thread : ioThreads) {
                if (thread == currThread) {
                    ioThread = thread;
                    break;
                }
            }
        }
        if (ioThread == null) {
            ioThread = nextIOThread();
        }
        AsyncNioUdpConnection conn = new AsyncNioUdpConnection(true, this, ioThread, connectThread, channel, null, null, address, connLivingCounter, connClosedCounter);
        CompletableFuture future = new CompletableFuture();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                if (conn.sslEngine == null) {
                    future.complete(conn);
                } else {
                    conn.startHandshake(t -> {
                        if (t == null) {
                            future.complete(conn);
                        } else {
                            future.completeExceptionally(t);
                        }
                    });
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return future;
    }

}
