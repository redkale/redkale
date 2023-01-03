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
import java.util.Objects;
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

    private boolean skipClose;

    //必须与ioWriteThreads数量相同
    private AsyncIOThread[] ioReadThreads;

    //必须与ioReadThreads数量相同
    private AsyncIOThread[] ioWriteThreads;

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
        final int threads = Utility.cpus();
        this.ioReadThreads = new AsyncIOThread[threads];
        this.ioWriteThreads = new AsyncIOThread[threads];
        try {
            for (int i = 0; i < threads; i++) {
                ObjectPool<ByteBuffer> unsafeReadBufferPool = ObjectPool.createUnsafePool(safeBufferPool, safeBufferPool.getCreatCounter(),
                    safeBufferPool.getCycleCounter(), 512, safeBufferPool.getCreator(), safeBufferPool.getPrepare(), safeBufferPool.getRecycler());
                String name = threadPrefixName + "-" + (i >= 9 ? (i + 1) : ("0" + (i + 1)));
                if (client) {
                    this.ioReadThreads[i] = new ClientIOThread(name, i, threads, workExecutor, Selector.open(), unsafeReadBufferPool, safeBufferPool);
                    this.ioWriteThreads[i] = this.ioReadThreads[i];
                } else {
                    this.ioReadThreads[i] = new AsyncIOThread(name, i, threads, workExecutor, Selector.open(), unsafeReadBufferPool, safeBufferPool);
                    this.ioWriteThreads[i] = this.ioReadThreads[i];
                }
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

    @Override
    public AsyncGroup start() {
        if (started) {
            return this;
        }
        if (closed) {
            throw new RuntimeException("group is closed");
        }
        for (int i = 0; i < this.ioReadThreads.length; i++) {
            this.ioReadThreads[i].start();
            if (this.ioWriteThreads[i] != this.ioReadThreads[i]) {
                this.ioWriteThreads[i].start();
            }
        }
        if (connectThread != null) {
            connectThread.start();
        }
        started = true;
        return this;
    }

    @Override
    public AsyncGroup close() {
        if (skipClose) {
            return this;
        } else {
            return dispose();
        }
    }

    public AsyncIOGroup skipClose(boolean skip) {
        this.skipClose = skip;
        return this;
    }

    public AsyncIOGroup dispose() {
        if (shareCount.decrementAndGet() > 0) {
            return this;
        }
        if (closed) {
            return this;
        }
        for (int i = 0; i < this.ioReadThreads.length; i++) {
            this.ioReadThreads[i].close();
            if (this.ioWriteThreads[i] != this.ioReadThreads[i]) {
                this.ioWriteThreads[i].close();
            }
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

    public AsyncIOThread[] nextIOThreads() {
        int i = Math.abs(readIndex.getAndIncrement()) % ioReadThreads.length;
        return new AsyncIOThread[]{ioReadThreads[i], ioWriteThreads[i]};
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

    //创建一个AsyncConnection对象，只给测试代码使用
    public AsyncConnection newTCPClientConnection() {
        try {
            return newTCPClientConnection(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AsyncNioTcpConnection newTCPClientConnection(final SocketAddress address) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        AsyncIOThread[] ioThreads = null;
        Thread currThread = Thread.currentThread();
        if (currThread instanceof AsyncIOThread) {
            for (int i = 0; i < this.ioReadThreads.length; i++) {
                if (this.ioReadThreads[i] == currThread || this.ioWriteThreads[i] == currThread) {
                    ioThreads = new AsyncIOThread[]{this.ioReadThreads[i], this.ioWriteThreads[i]};
                    break;
                }
            }
        }
        if (ioThreads == null) {
            ioThreads = nextIOThreads();
        }
        return new AsyncNioTcpConnection(true, this, ioThreads[0], ioThreads[1], connectThread, channel, null, null, address, connLivingCounter, connClosedCounter);
    }

    @Override
    public CompletableFuture<AsyncConnection> createTCPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        Objects.requireNonNull(address);
        AsyncNioTcpConnection conn;
        try {
            conn = newTCPClientConnection(address);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
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

    //创建一个AsyncConnection对象，只给测试代码使用
    public AsyncConnection newUDPClientConnection() {
        try {
            return newUDPClientConnection(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AsyncNioUdpConnection newUDPClientConnection(final SocketAddress address) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        AsyncIOThread[] ioThreads = null;
        Thread currThread = Thread.currentThread();
        if (currThread instanceof AsyncIOThread) {
            for (int i = 0; i < this.ioReadThreads.length; i++) {
                if (this.ioReadThreads[i] == currThread || this.ioWriteThreads[i] == currThread) {
                    ioThreads = new AsyncIOThread[]{this.ioReadThreads[i], this.ioWriteThreads[i]};
                    break;
                }
            }
        }
        if (ioThreads == null) {
            ioThreads = nextIOThreads();
        }
        return new AsyncNioUdpConnection(true, this, ioThreads[0], ioThreads[1], connectThread, channel, null, null, address, connLivingCounter, connClosedCounter);
    }

    @Override
    public CompletableFuture<AsyncConnection> createUDPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        AsyncNioUdpConnection conn;
        try {
            conn = newUDPClientConnection(address);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
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
