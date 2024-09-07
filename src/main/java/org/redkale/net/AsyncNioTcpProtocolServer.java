/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.annotation.Nullable;
import org.redkale.boot.Application;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
class AsyncNioTcpProtocolServer extends ProtocolServer {

    private ServerSocketChannel serverChannel;

    private Selector selector;

    private AsyncIOGroup ioGroup;

    private boolean closed;

    private Supplier<Response> responseSupplier;

    private Consumer<Response> responseConsumer;

    public AsyncNioTcpProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.selector = Selector.open();
        final Set<SocketOption<?>> options = this.serverChannel.supportedOptions();
        if (options.contains(StandardSocketOptions.TCP_NODELAY)) {
            this.serverChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        }
        if (options.contains(StandardSocketOptions.SO_KEEPALIVE)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        }
        if (options.contains(StandardSocketOptions.SO_REUSEADDR)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        if (options.contains(StandardSocketOptions.SO_RCVBUF)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, 32 * 1024);
        }
        if (options.contains(StandardSocketOptions.SO_SNDBUF)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, 32 * 1024);
        }
    }

    @Override
    public void bind(SocketAddress local, int backlog) throws IOException {
        this.serverChannel.bind(local, backlog);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return this.serverChannel.supportedOptions();
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        this.serverChannel.setOption(name, value);
    }

    @Override
    public void accept(@Nullable Application application, Server server) throws IOException {
        this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

        LongAdder createBufferCounter = new LongAdder();
        LongAdder cycleBufferCounter = new LongAdder();
        LongAdder createResponseCounter = new LongAdder();
        LongAdder cycleResponseCounter = new LongAdder();

        ObjectPool<Response> safeResponsePool =
                server.createSafeResponsePool(createResponseCounter, cycleResponseCounter, server.responsePoolSize);
        final int respPoolMax = server.getResponsePoolSize();
        ThreadLocal<ObjectPool<Response>> localResponsePool = Utility.withInitialThreadLocal(() -> {
            if (!(Thread.currentThread() instanceof WorkThread)) {
                return null;
            }
            return ObjectPool.createUnsafePool(
                    safeResponsePool,
                    safeResponsePool.getCreatCounter(),
                    safeResponsePool.getCycleCounter(),
                    respPoolMax,
                    safeResponsePool.getCreator(),
                    safeResponsePool.getPrepare(),
                    safeResponsePool.getRecycler());
        });
        this.responseSupplier = () -> {
            ObjectPool<Response> pool = localResponsePool.get();
            return pool == null ? safeResponsePool.get() : pool.get();
        };
        this.responseConsumer = v -> {
            WorkThread thread = v.channel != null ? v.channel.getWriteIOThread() : v.thread;
            if (thread != null && !thread.inCurrThread()) {
                thread.execute(() -> {
                    ObjectPool<Response> pool = localResponsePool.get();
                    (pool == null ? safeResponsePool : pool).accept(v);
                });
                return;
            }
            ObjectPool<Response> pool = localResponsePool.get();
            (pool == null ? safeResponsePool : pool).accept(v);
        };
        final String threadNameFormat = Utility.isEmpty(server.name)
                ? "Redkale-IOServletThread-%s"
                : ("Redkale-" + server.name.replace("Server-", "") + "-IOServletThread-%s");
        if (this.ioGroup == null) {
            if (application != null && application.getShareAsyncGroup() != null) {
                this.ioGroup = application.getShareAsyncGroup();
            } else {
                ByteBufferPool safeBufferPool =
                        server.createSafeBufferPool(createBufferCounter, cycleBufferCounter, server.bufferPoolSize);
                this.ioGroup = new AsyncIOGroup(threadNameFormat, null, safeBufferPool);
                this.ioGroup.start();
            }
        }

        Thread acceptThread = new Thread() {
            {
                setName(String.format(threadNameFormat, "Accept"));
            }

            @Override
            public void run() {
                final AsyncIOThread[] ioReadThreads = ioGroup.ioReadThreads;
                final AsyncIOThread[] ioWriteThreads = ioGroup.ioWriteThreads;
                final int reads = ioReadThreads.length;
                final int writes = ioWriteThreads.length;
                int readIndex = -1;
                int writeIndex = -1;
                Set<SelectionKey> keys = null;
                while (!closed) {
                    try {
                        int count = selector.select();
                        if (count == 0) {
                            continue;
                        }
                        if (keys == null) {
                            keys = selector.selectedKeys();
                        }
                        for (SelectionKey key : keys) {
                            if (key.isAcceptable()) {
                                if (++readIndex >= reads) {
                                    readIndex = 0;
                                }
                                if (++writeIndex >= writes) {
                                    writeIndex = 0;
                                }
                                accept(ioReadThreads[readIndex], ioWriteThreads[writeIndex]);
                            }
                        }
                        keys.clear();
                    } catch (Throwable t) {
                        server.logger.log(Level.SEVERE, "server accept error", t);
                    }
                }
            }
        };
        acceptThread.start();
    }

    private void accept(AsyncIOThread ioReadThread, AsyncIOThread ioWriteThread) throws IOException {
        SocketChannel channel = this.serverChannel.accept();
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        channel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
        channel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
        ioGroup.connCreateCounter.increment();
        ioGroup.connLivingCounter.increment();
        AsyncNioTcpConnection conn = new AsyncNioTcpConnection(
                false,
                ioGroup,
                ioReadThread,
                ioWriteThread,
                channel,
                context.getSSLBuilder(),
                context.getSSLContext(),
                null);
        ProtocolCodec codec = new ProtocolCodec(context, responseSupplier, responseConsumer, conn);
        conn.protocolCodec = codec;
        if (conn.sslEngine == null) {
            codec.start(null);
        } else {
            conn.startHandshake(t -> {
                if (t == null) {
                    codec.start(null);
                } else if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RedkaleException(t);
                }
            });
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return this.serverChannel.getLocalAddress();
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.selector.wakeup();
        this.ioGroup.close();
        this.serverChannel.close();
        this.selector.close();
    }

    @Override
    public AsyncGroup getAsyncGroup() {
        return ioGroup;
    }

    @Override
    public long getCreateConnectionCount() {
        return ioGroup.connCreateCounter == null ? -1 : ioGroup.connCreateCounter.longValue();
    }

    @Override
    public long getClosedConnectionCount() {
        return ioGroup.connClosedCounter == null ? -1 : ioGroup.connClosedCounter.longValue();
    }

    @Override
    public long getLivingConnectionCount() {
        return ioGroup.connLivingCounter == null ? -1 : ioGroup.connLivingCounter.longValue();
    }
}
