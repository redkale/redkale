/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.redkale.annotation.ResourceType;
import org.redkale.util.*;

/**
 * 协议处理的IO线程组
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
@ResourceType(AsyncGroup.class)
public class AsyncIOGroup extends AsyncGroup {

    private final AtomicBoolean started = new AtomicBoolean();

    private boolean skipClose;

    private final AtomicBoolean closed = new AtomicBoolean();

    final AsyncIOThread[] ioReadThreads;

    final AsyncIOThread[] ioWriteThreads;

    private final AtomicBoolean connectThreadInited = new AtomicBoolean();

    private final AsyncIOThread connectThread;

    final int bufferCapacity;

    private final AtomicInteger readIndex = new AtomicInteger();

    private final AtomicInteger writeIndex = new AtomicInteger();

    // 创建数
    protected final LongAdder connCreateCounter = new LongAdder();

    // 在线数
    protected final LongAdder connLivingCounter = new LongAdder();

    // 关闭数
    protected final LongAdder connClosedCounter = new LongAdder();

    // 超时器
    protected final ScheduledExecutorService timeoutExecutor;

    public AsyncIOGroup(final int bufferCapacity, final int bufferPoolSize) {
        this("Redkale-AnonymousClient-IOThread-%s", null, bufferCapacity, bufferPoolSize);
    }

    public AsyncIOGroup(
            String threadNameFormat,
            final ExecutorService workExecutor,
            final int bufferCapacity,
            final int bufferPoolSize) {
        this(threadNameFormat, workExecutor, ByteBufferPool.createSafePool(bufferPoolSize, bufferCapacity));
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public AsyncIOGroup(String threadNameFormat, ExecutorService workExecutor, final ByteBufferPool safeBufferPool) {
        final int threads = Utility.cpus(); // 固定值,不可改
        this.bufferCapacity = safeBufferPool.getBufferCapacity();
        this.ioReadThreads = new AsyncIOThread[threads];
        this.ioWriteThreads = new AsyncIOThread[threads];
        final ThreadGroup g = new ThreadGroup(String.format(threadNameFormat, "Group"));
        this.timeoutExecutor = Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r, String.format(threadNameFormat, "Timeout"));
            t.setDaemon(true);
            return t;
        });
        try {
            for (int i = 0; i < threads; i++) {
                String indexFix = WorkThread.formatIndex(threads, i + 1);
                this.ioReadThreads[i] = createAsyncIOThread(
                        g, String.format(threadNameFormat, indexFix), i, threads, workExecutor, safeBufferPool);
                this.ioWriteThreads[i] = this.ioReadThreads[i];
            }
            this.connectThread = createConnectIOThread(
                    g, String.format(threadNameFormat, "Connect"), 0, 0, workExecutor, safeBufferPool);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    protected AsyncIOThread createConnectIOThread(
            ThreadGroup g,
            String name,
            int index,
            int threads,
            ExecutorService workExecutor,
            ByteBufferPool safeBufferPool)
            throws IOException {
        return new AsyncIOThread(g, name, index, threads, workExecutor, safeBufferPool);
    }

    protected AsyncIOThread createAsyncIOThread(
            ThreadGroup g,
            String name,
            int index,
            int threads,
            ExecutorService workExecutor,
            ByteBufferPool safeBufferPool)
            throws IOException {
        return new AsyncIOThread(g, name, index, threads, workExecutor, safeBufferPool);
    }

    AsyncIOThread connectThread() {
        if (connectThreadInited.compareAndSet(false, true)) {
            this.connectThread.start();
        }
        return this.connectThread;
    }

    @Override
    public AsyncIOGroup start() {
        if (closed.get()) {
            throw new RedkaleException("group is closed");
        }
        if (started.compareAndSet(false, true)) {
            for (int i = 0; i < this.ioReadThreads.length; i++) {
                this.ioReadThreads[i].start();
                if (this.ioWriteThreads[i] != this.ioReadThreads[i]) {
                    this.ioWriteThreads[i].start();
                }
            }
            // connectThread用时才初始化
        }
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

    @Override
    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    // 创建一个AsyncConnection对象，只给测试代码使用
    public AsyncConnection newTCPClientConnection() {
        try {
            return newTCPClientConnection(-1, null);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    private AsyncNioTcpConnection newTCPClientConnection(int ioIndex, SocketAddress address) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        AsyncIOThread readThread = null;
        AsyncIOThread writeThread = null;
        AsyncIOThread currThread = AsyncIOThread.currentAsyncIOThread();
        if (ioIndex >= 0 && ioIndex < this.ioReadThreads.length) {
            readThread = this.ioReadThreads[ioIndex];
            writeThread = this.ioWriteThreads[ioIndex];
        } else if (currThread != null) {
            if (this.ioReadThreads[0].getThreadGroup() == currThread.getThreadGroup()) {
                for (AsyncIOThread ioReadThread : this.ioReadThreads) {
                    if (ioReadThread.index() == currThread.index()) {
                        readThread = ioReadThread;
                        break;
                    }
                }
            }
            if (this.ioWriteThreads[0].getThreadGroup() == currThread.getThreadGroup()) {
                for (AsyncIOThread ioWriteThread : this.ioWriteThreads) {
                    if (ioWriteThread.index() == currThread.index()) {
                        writeThread = ioWriteThread;
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
    public CompletableFuture<AsyncConnection> createTCPClientConnection(
            int ioIndex, SocketAddress address, int connectTimeoutSeconds) {
        Objects.requireNonNull(address);
        AsyncNioTcpConnection conn;
        try {
            conn = newTCPClientConnection(ioIndex, address);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        final CompletableFuture future = new CompletableFuture();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                connCreateCounter.increment();
                connLivingCounter.increment();
                if (conn.sslEngine == null) {
                    future.complete(conn);
                } else {
                    conn.startHandshake(new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            future.complete(conn);
                        }

                        @Override
                        public void failed(Throwable t, Void attachment) {
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
        int seconds = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 6;
        final Supplier<String> timeoutMsg = () -> address + " tcp-connect timeout";
        return Utility.orTimeout(future, timeoutMsg, seconds, TimeUnit.SECONDS);
    }

    // 创建一个AsyncConnection对象，只给测试代码使用
    public AsyncConnection newUDPClientConnection() {
        try {
            return newUDPClientConnection(-1, null);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    private AsyncNioUdpConnection newUDPClientConnection(int ioIndex, SocketAddress address) throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        AsyncIOThread readThread = null;
        AsyncIOThread writeThread = null;
        AsyncIOThread currThread = AsyncIOThread.currentAsyncIOThread();
        if (ioIndex >= 0 && ioIndex < this.ioReadThreads.length) {
            readThread = this.ioReadThreads[ioIndex];
            writeThread = this.ioWriteThreads[ioIndex];
        } else if (currThread != null) {
            for (AsyncIOThread ioReadThread : this.ioReadThreads) {
                if (ioReadThread.index() == currThread.index()) {
                    readThread = ioReadThread;
                    break;
                }
            }
            for (AsyncIOThread ioWriteThread : this.ioWriteThreads) {
                if (ioWriteThread.index() == currThread.index()) {
                    writeThread = ioWriteThread;
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
    public CompletableFuture<AsyncConnection> createUDPClientConnection(
            int ioIndex, SocketAddress address, int connectTimeoutSeconds) {
        AsyncNioUdpConnection conn;
        try {
            conn = newUDPClientConnection(ioIndex, address);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        final CompletableFuture future = new CompletableFuture();
        conn.connect(address, null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                if (conn.sslEngine == null) {
                    future.complete(conn);
                } else {
                    conn.startHandshake(new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            future.complete(conn);
                        }

                        @Override
                        public void failed(Throwable t, Void attachment) {
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
        int seconds = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 6;
        final Supplier<String> timeoutMsg = () -> address + " udp-connect timeout";
        return Utility.orTimeout(future, timeoutMsg, seconds, TimeUnit.SECONDS);
    }
}
