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
import org.redkale.net.client.*;
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

    private boolean skipClose;

    private final AtomicBoolean closed = new AtomicBoolean();

    final AsyncIOThread[] ioReadThreads;

    final AsyncIOThread[] ioWriteThreads;

    final AsyncIOThread connectThread;

    final int bufferCapacity;

    private final AtomicInteger readIndex = new AtomicInteger();

    private final AtomicInteger writeIndex = new AtomicInteger();

    //创建数
    protected final LongAdder connCreateCounter = new LongAdder();

    //在线数
    protected final LongAdder connLivingCounter = new LongAdder();

    //关闭数
    protected final LongAdder connClosedCounter = new LongAdder();

    protected final ScheduledThreadPoolExecutor timeoutExecutor;

    public AsyncIOGroup(final int bufferCapacity, final int bufferPoolSize) {
        this(true, "Redkale-AnonymousClient-IOThread-%s", Utility.cpus(), null, bufferCapacity, bufferPoolSize);
    }

    public AsyncIOGroup(boolean clientMode, String threadNameFormat, final ExecutorService workExecutor, final int bufferCapacity, final int bufferPoolSize) {
        this(clientMode, threadNameFormat, Utility.cpus(), workExecutor, bufferCapacity, bufferPoolSize);
    }

    public AsyncIOGroup(boolean clientMode, String threadNameFormat, int threads, final ExecutorService workExecutor, final int bufferCapacity, final int bufferPoolSize) {
        this(clientMode, threadNameFormat, threads, workExecutor, bufferCapacity, ObjectPool.createSafePool(null, null, bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) {
                    return false;
                }
                e.clear();
                return true;
            }));
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public AsyncIOGroup(boolean clientMode, String threadNameFormat, ExecutorService workExecutor, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        this(clientMode, threadNameFormat, Utility.cpus(), workExecutor, bufferCapacity, safeBufferPool);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public AsyncIOGroup(boolean clientMode, String threadNameFormat, int threads, ExecutorService workExecutor, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        this.bufferCapacity = bufferCapacity;
        this.ioReadThreads = new AsyncIOThread[threads];
        this.ioWriteThreads = new AsyncIOThread[threads];
        final ThreadGroup g = new ThreadGroup(String.format(threadNameFormat, "Group"));

        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r, String.format(threadNameFormat, "Timeout"));
            t.setDaemon(true);
            return t;
        });
        try {
            for (int i = 0; i < threads; i++) {
                String indexfix = WorkThread.formatIndex(threads, i + 1);
                if (clientMode) {
                    this.ioReadThreads[i] = createClientReadIOThread(g, String.format(threadNameFormat, "Read-" + indexfix), i, threads, workExecutor, safeBufferPool);
                    this.ioWriteThreads[i] = createClientWriteIOThread(g, String.format(threadNameFormat, "Write-" + indexfix), i, threads, workExecutor, safeBufferPool);
                } else {
                    this.ioReadThreads[i] = createAsyncIOThread(g, String.format(threadNameFormat, indexfix), i, threads, workExecutor, safeBufferPool);
                    this.ioWriteThreads[i] = this.ioReadThreads[i];
                }
            }
            if (clientMode) {
                this.connectThread = createClientReadIOThread(g, String.format(threadNameFormat, "Connect"), 0, 0, workExecutor, safeBufferPool);
            } else {
                this.connectThread = null;
            }
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    protected AsyncIOThread createAsyncIOThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        return new AsyncIOThread(g, name, index, threads, workExecutor, safeBufferPool);
    }

    protected AsyncIOThread createClientReadIOThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        return new ClientReadIOThread(g, name, index, threads, workExecutor, safeBufferPool);
    }

    protected AsyncIOThread createClientWriteIOThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        return new ClientWriteIOThread(g, name, index, threads, workExecutor, safeBufferPool);
    }

    @Override
    public AsyncGroup start() {
        if (started) {
            return this;
        }
        if (closed.get()) {
            throw new RedkaleException("group is closed");
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
        if (closed.compareAndSet(false, true)) {
            for (AsyncIOThread t : this.ioReadThreads) {
                t.close();
            }
            for (AsyncIOThread t : this.ioWriteThreads) {
                t.close();
            }
            if (connectThread != null) {
                connectThread.close();
            }
            this.timeoutExecutor.shutdownNow();
        }
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

    public AsyncIOThread nextReadIOThread() {
        int i = Math.abs(readIndex.getAndIncrement()) % ioReadThreads.length;
        return ioReadThreads[i];
    }

    public AsyncIOThread nextWriteIOThread() {
        int i = Math.abs(writeIndex.getAndIncrement()) % ioWriteThreads.length;
        return ioWriteThreads[i];
    }

    public AsyncIOThread connectThread() {
        return connectThread;
    }

    @Override
    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    //创建一个AsyncConnection对象，只给测试代码使用
    public AsyncConnection newTCPClientConnection() {
        try {
            return newTCPClientConnection(null);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    private AsyncNioTcpConnection newTCPClientConnection(final SocketAddress address) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        AsyncIOThread readThread = null;
        AsyncIOThread writeThread = null;
        AsyncIOThread currThread = AsyncIOThread.currAsyncIOThread();
        if (currThread != null) {
            if (this.ioReadThreads[0].getThreadGroup() == currThread.getThreadGroup()) {
                for (int i = 0; i < this.ioReadThreads.length; i++) {
                    if (this.ioReadThreads[i].index() == currThread.index()) {
                        readThread = this.ioReadThreads[i];
                        break;
                    }
                }
            }
            if (this.ioWriteThreads[0].getThreadGroup() == currThread.getThreadGroup()) {
                for (int i = 0; i < this.ioWriteThreads.length; i++) {
                    if (this.ioWriteThreads[i].index() == currThread.index()) {
                        writeThread = this.ioWriteThreads[i];
                        break;
                    }
                }
            }
        }
        if (readThread == null) {
            readThread = nextReadIOThread();
        }
        if (writeThread == null) {
            writeThread = nextWriteIOThread();
        }
        return new AsyncNioTcpConnection(true, this, readThread, writeThread, channel, null, null, address);
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
            throw new RedkaleException(e);
        }
    }

    private AsyncNioUdpConnection newUDPClientConnection(final SocketAddress address) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        AsyncIOThread readThread = null;
        AsyncIOThread writeThread = null;
        AsyncIOThread currThread = AsyncIOThread.currAsyncIOThread();
        if (currThread != null) {
            for (int i = 0; i < this.ioReadThreads.length; i++) {
                if (this.ioReadThreads[i].index() == currThread.index()) {
                    readThread = this.ioReadThreads[i];
                    break;
                }
            }
            for (int i = 0; i < this.ioWriteThreads.length; i++) {
                if (this.ioWriteThreads[i].index() == currThread.index()) {
                    writeThread = this.ioWriteThreads[i];
                    break;
                }
            }
        }
        if (readThread == null) {
            readThread = nextReadIOThread();
        }
        if (writeThread == null) {
            writeThread = nextWriteIOThread();
        }
        return new AsyncNioUdpConnection(true, this, readThread, writeThread, channel, null, null, address);
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
