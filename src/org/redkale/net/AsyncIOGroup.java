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

    private final AtomicInteger readIndex = new AtomicInteger();

    //创建数
    final AtomicLong connCreateCounter = new AtomicLong();

    //关闭数
    final AtomicLong connLivingCounter = new AtomicLong();

    //在线数
    final AtomicLong connClosedCounter = new AtomicLong();

    private ScheduledThreadPoolExecutor timeoutExecutor;

    public AsyncIOGroup(final int bufferCapacity, final int bufferPoolSize) {
        this(null, Runtime.getRuntime().availableProcessors(), bufferCapacity, bufferPoolSize);
    }

    public AsyncIOGroup(final ExecutorService workExecutor,
        final int iothreads, final int bufferCapacity, final int bufferPoolSize) {
        this(null, workExecutor, iothreads, bufferCapacity, ObjectPool.createSafePool(null, null, bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) return false;
                e.clear();
                return true;
            }));
    }

    public AsyncIOGroup(String threadPrefixName0, ExecutorService workExecutor,
        int iothreads, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        this.bufferCapacity = bufferCapacity;
        final String threadPrefixName = threadPrefixName0 == null ? "Redkale-Client-IOThread" : threadPrefixName0;
        this.ioThreads = new AsyncIOThread[Math.max(iothreads, 1)];
        try {
            for (int i = 0; i < this.ioThreads.length; i++) {
                ObjectPool<ByteBuffer> unsafeBufferPool = ObjectPool.createUnsafePool(safeBufferPool, safeBufferPool.getCreatCounter(),
                    safeBufferPool.getCycleCounter(), 512, safeBufferPool.getCreator(), safeBufferPool.getPrepare(), safeBufferPool.getRecycler());
                String name = threadPrefixName + "-" + (i >= 9 ? (i + 1) : ("0" + (i + 1)));

                this.ioThreads[i] = new AsyncIOThread(true, name, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool);
            }
            {
                ObjectPool<ByteBuffer> unsafeBufferPool = ObjectPool.createUnsafePool(safeBufferPool, safeBufferPool.getCreatCounter(),
                    safeBufferPool.getCycleCounter(), 512, safeBufferPool.getCreator(), safeBufferPool.getPrepare(), safeBufferPool.getRecycler());
                String name = threadPrefixName.replace("ServletThread", "ConnectThread").replace("Redkale-Client-IOThread", "Redkale-Client-ConnectThread");
                this.connectThread = new AsyncIOThread(false, name, workExecutor, Selector.open(), unsafeBufferPool, safeBufferPool);
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

    public void start() {
        if (started) return;
        if (closed) throw new RuntimeException("group is closed");
        for (AsyncIOThread thread : ioThreads) {
            thread.start();
        }
        connectThread.start();
        started = true;
    }

    public void close() {
        if (closed) return;
        for (AsyncIOThread thread : ioThreads) {
            thread.close();
        }
        connectThread.close();
        this.timeoutExecutor.shutdownNow();
        closed = true;
    }

    public AtomicLong getCreateConnectionCount() {
        return connCreateCounter;
    }

    public AtomicLong getClosedConnectionCount() {
        return connLivingCounter;
    }

    public AtomicLong getLivingConnectionCount() {
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
        if (key == null) return;
        if (key.selector() != thread.selector) throw new RuntimeException("NioThread.selector not the same to SelectionKey.selector");
        if ((key.interestOps() & opt) != 0) return;
        key.interestOps(key.interestOps() | opt);
        if (thread.inCurrThread()) return;
        //非IO线程中
        key.selector().wakeup();
    }

    @Override
    public CompletableFuture<AsyncConnection> createTCP(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        SocketChannel channel;
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        } catch (IOException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        }
        AsyncIOThread readThread = nextIOThread();
        final AsyncNioTcpConnection conn = new AsyncNioTcpConnection(true, this, readThread, connectThread, channel, null, address, connLivingCounter, connClosedCounter);
        final CompletableFuture<AsyncConnection> future = new CompletableFuture<>();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                conn.setReadTimeoutSeconds(readTimeoutSeconds);
                conn.setWriteTimeoutSeconds(writeTimeoutSeconds);
                connCreateCounter.incrementAndGet();
                connLivingCounter.incrementAndGet();
                future.complete(conn);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return Utility.orTimeout(future, 30, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<AsyncConnection> createUDP(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        }
        AsyncIOThread readThread = nextIOThread();
        AsyncNioUdpConnection conn = new AsyncNioUdpConnection(true, this, readThread, connectThread, channel, null, address, connLivingCounter, connClosedCounter);
        CompletableFuture future = new CompletableFuture();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                future.complete(conn);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return future;
    }

}
