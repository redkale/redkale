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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.annotation.Nullable;
import org.redkale.boot.Application;
import org.redkale.util.*;

/**
 * 协议底层Server
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
class AsyncNioUdpProtocolServer extends ProtocolServer {

    private AsyncNioUdpServerChannel udpServerChannel;

    private Selector selector;

    private AsyncIOGroup ioGroup;

    private boolean closed;

    private Supplier<Response> responseSupplier;

    private Consumer<Response> responseConsumer;

    public AsyncNioUdpProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        DatagramChannel serverChannel = DatagramChannel.open();
        this.udpServerChannel = new AsyncNioUdpServerChannel(serverChannel);
        serverChannel.configureBlocking(false);
        this.selector = Selector.open();
        final Set<SocketOption<?>> options = serverChannel.supportedOptions();
        if (options.contains(StandardSocketOptions.TCP_NODELAY)) {
            serverChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        }
        if (options.contains(StandardSocketOptions.SO_KEEPALIVE)) {
            serverChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        }
        if (options.contains(StandardSocketOptions.SO_REUSEADDR)) {
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        if (options.contains(StandardSocketOptions.SO_RCVBUF)) {
            serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, 32 * 1024);
        }
        if (options.contains(StandardSocketOptions.SO_SNDBUF)) {
            serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, 32 * 1024);
        }
    }

    @Override
    public void bind(SocketAddress local, int backlog) throws IOException {
        udpServerChannel.serverChannel.bind(local);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        udpServerChannel.serverChannel.setOption(name, value);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return udpServerChannel.serverChannel.supportedOptions();
    }

    @Override
    public void accept(@Nullable Application application, Server server) throws IOException {

        LongAdder createBufferCounter = new LongAdder();
        LongAdder cycleBufferCounter = new LongAdder();
        LongAdder createResponseCounter = new LongAdder();
        LongAdder cycleResponseCounter = new LongAdder();

        ByteBufferPool safeBufferPool =
                server.createSafeBufferPool(createBufferCounter, cycleBufferCounter, server.bufferPoolSize);
        ObjectPool<Response> safeResponsePool =
                server.createSafeResponsePool(createResponseCounter, cycleResponseCounter, server.responsePoolSize);
        ThreadLocal<ObjectPool<Response>> localResponsePool = Utility.withInitialThreadLocal(() -> {
            if (!(Thread.currentThread() instanceof WorkThread)) {
                return null;
            }
            return ObjectPool.createUnsafePool(
                    safeResponsePool,
                    safeResponsePool.getCreatCounter(),
                    safeResponsePool.getCycleCounter(),
                    16,
                    safeResponsePool.getCreator(),
                    safeResponsePool.getPrepare(),
                    safeResponsePool.getRecycler());
        });
        this.responseSupplier = () -> {
            ObjectPool<Response> pool = localResponsePool.get();
            return pool == null ? safeResponsePool.get() : pool.get();
        };
        this.responseConsumer = v -> {
            ObjectPool<Response> pool = localResponsePool.get();
            (pool == null ? safeResponsePool : pool).accept(v);
        };
        final String threadNameFormat = Utility.isEmpty(server.name)
                ? "Redkale-IOServletThread-%s"
                : ("Redkale-" + server.name.replace("Server-", "") + "-IOServletThread-%s");
        if (this.ioGroup == null) {
            this.ioGroup = new AsyncIOGroup(threadNameFormat, null, safeBufferPool);
            this.ioGroup.start();
        }
        udpServerChannel.serverChannel.register(this.selector, SelectionKey.OP_READ);
        Thread acceptThread = new Thread() {
            {
                setName(String.format(threadNameFormat, "Accept"));
            }

            @Override
            public void run() {
                udpServerChannel.unsafeBufferPool =
                        ByteBufferPool.createUnsafePool(Thread.currentThread(), 512, safeBufferPool);
                final AsyncIOThread[] ioReadThreads = ioGroup.ioReadThreads;
                final AsyncIOThread[] ioWriteThreads = ioGroup.ioWriteThreads;
                final int reads = ioReadThreads.length;
                final int writes = ioWriteThreads.length;
                int readIndex = -1;
                int writeIndex = -1;
                Set<SelectionKey> keys = null;
                final Selector sel = selector;
                final DatagramChannel serverChannel = udpServerChannel.serverChannel;
                final ByteBufferPool unsafeBufferPool = udpServerChannel.unsafeBufferPool;
                while (!closed) {
                    try {
                        int count = sel.select();
                        if (count == 0) {
                            continue;
                        }
                        if (keys == null) {
                            keys = selector.selectedKeys();
                        }
                        for (SelectionKey key : keys) {
                            if (key.isReadable()) {
                                final ByteBuffer buffer = unsafeBufferPool.get();
                                try {
                                    SocketAddress address = serverChannel.receive(buffer);
                                    serverChannel.register(sel, SelectionKey.OP_READ);
                                    if (++readIndex >= reads) {
                                        readIndex = 0;
                                    }
                                    if (++writeIndex >= writes) {
                                        writeIndex = 0;
                                    }
                                    AsyncNioUdpConnection conn = udpServerChannel.connections.get(address);
                                    if (conn == null) {
                                        accept(address, buffer, ioReadThreads[readIndex], ioWriteThreads[writeIndex]);
                                    } else {
                                        conn.receiveData(buffer);
                                    }
                                } catch (Throwable t) {
                                    unsafeBufferPool.accept(buffer);
                                }
                            }
                        }
                        keys.clear();
                    } catch (Throwable ex) {
                        if (!closed) {
                            server.logger.log(Level.FINE, getName() + " selector run failed", ex);
                        }
                    }
                }
            }
        };
        acceptThread.start();
    }

    private void accept(
            SocketAddress address, ByteBuffer buffer, AsyncIOThread ioReadThread, AsyncIOThread ioWriteThread)
            throws IOException {
        ioGroup.connCreateCounter.increment();
        ioGroup.connLivingCounter.increment();
        AsyncNioUdpConnection conn = new AsyncNioUdpConnection(
                false,
                ioGroup,
                ioReadThread,
                ioWriteThread,
                udpServerChannel.serverChannel,
                context.getSSLBuilder(),
                context.getSSLContext(),
                address);
        conn.udpServerChannel = udpServerChannel;
        udpServerChannel.connections.put(address, conn);
        ProtocolCodec codec = new ProtocolCodec(context, responseSupplier, responseConsumer, conn);
        conn.protocolCodec = codec;
        buffer.flip();
        if (conn.sslEngine == null) {
            codec.start(buffer);
        } else {
            conn.startHandshake(new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    codec.start(buffer);
                }

                @Override
                public void failed(Throwable t, Void attachment) {
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else {
                        throw new RedkaleException(t);
                    }
                }
            });
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return udpServerChannel.serverChannel.getLocalAddress();
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.ioGroup.close();
        udpServerChannel.serverChannel.close();
    }

    @Override
    public AsyncGroup getAsyncGroup() {
        return ioGroup;
    }

    @Override
    public long getCreateConnectionCount() {
        return -1;
    }

    @Override
    public long getClosedConnectionCount() {
        return -1;
    }

    @Override
    public long getLivingConnectionCount() {
        return -1;
    }

    static class AsyncNioUdpServerChannel {

        DatagramChannel serverChannel;

        ByteBufferPool unsafeBufferPool;

        final ReentrantLock writeLock = new ReentrantLock();

        volatile long writeTime;

        ConcurrentHashMap<SocketAddress, AsyncNioUdpConnection> connections = new ConcurrentHashMap<>();

        public AsyncNioUdpServerChannel(DatagramChannel serverChannel) {
            this.serverChannel = serverChannel;
        }
    }
}
