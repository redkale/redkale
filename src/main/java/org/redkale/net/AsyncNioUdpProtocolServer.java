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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.boot.Application;
import org.redkale.util.*;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class AsyncNioUdpProtocolServer extends ProtocolServer {

    private DatagramChannel serverChannel;

    private Selector selector;

    private AsyncIOGroup ioGroup;

    private Thread acceptThread;

    private boolean closed;

    private Supplier<Response> responseSupplier;

    private Consumer<Response> responseConsumer;

    public AsyncNioUdpProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        this.serverChannel = DatagramChannel.open();
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
        this.serverChannel.bind(local);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        this.serverChannel.setOption(name, value);
    }

    @Override
    public <T> Set<SocketOption<?>> supportedOptions() {
        return this.serverChannel.supportedOptions();
    }

    @Override
    public void accept(Application application, Server server) throws IOException {

        LongAdder createBufferCounter = new LongAdder();
        LongAdder cycleBufferCounter = new LongAdder();
        LongAdder createResponseCounter = new LongAdder();
        LongAdder cycleResponseCounter = new LongAdder();

        ObjectPool<ByteBuffer> safeBufferPool = server.createSafeBufferPool(createBufferCounter, cycleBufferCounter, server.bufferPoolSize);
        ObjectPool<Response> safeResponsePool = server.createSafeResponsePool(createResponseCounter, cycleResponseCounter, server.responsePoolSize);
        ThreadLocal<ObjectPool<Response>> localResponsePool = ThreadLocal.withInitial(() -> {
            if (!(Thread.currentThread() instanceof WorkThread)) {
                return null;
            }
            return ObjectPool.createUnsafePool(safeResponsePool, safeResponsePool.getCreatCounter(),
                safeResponsePool.getCycleCounter(), 16, safeResponsePool.getCreator(), safeResponsePool.getPrepare(), safeResponsePool.getRecycler());
        });
        this.responseSupplier = () -> {
            ObjectPool<Response> pool = localResponsePool.get();
            return pool == null ? safeResponsePool.get() : pool.get();
        };
        this.responseConsumer = (v) -> {
            ObjectPool<Response> pool = localResponsePool.get();
            (pool == null ? safeResponsePool : pool).accept(v);
        };
        final String threadNameFormat = server.name == null || server.name.isEmpty() ? "Redkale-IOServletThread-%s" : ("Redkale-" + server.name.replace("Server-", "") + "-IOServletThread-%s");
        this.ioGroup = new AsyncIOGroup(false, threadNameFormat, null, server.bufferCapacity, safeBufferPool);
        this.ioGroup.start();
        this.serverChannel.register(this.selector, SelectionKey.OP_READ);
        this.acceptThread = new Thread() {
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
                final Selector sel = selector;
                ObjectPool<ByteBuffer> unsafeBufferPool = ObjectPool.createUnsafePool(null, 512, safeBufferPool);
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
                                    buffer.flip();
                                    if (++readIndex >= reads) {
                                        readIndex = 0;
                                    }
                                    if (++writeIndex >= writes) {
                                        writeIndex = 0;
                                    }
                                    accept(address, buffer, ioReadThreads[readIndex], ioWriteThreads[writeIndex]);
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
        this.acceptThread.start();
    }

    private void accept(SocketAddress address, ByteBuffer buffer, AsyncIOThread ioReadThread, AsyncIOThread ioWriteThread) throws IOException {
        ioGroup.connCreateCounter.increment();
        ioGroup.connLivingCounter.increment();
        AsyncNioUdpConnection conn = new AsyncNioUdpConnection(false, ioGroup, ioReadThread, ioWriteThread, this.serverChannel, context.getSSLBuilder(), context.getSSLContext(), address);
        ProtocolCodec codec = new ProtocolCodec(context, responseSupplier, responseConsumer, conn);
        conn.protocolCodec = codec;
        if (conn.sslEngine == null) {
            codec.start(buffer);
        } else {
            conn.startHandshake(t -> {
                if (t == null) {
                    codec.start(buffer);
                } else if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RuntimeException(t);
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
        this.ioGroup.close();
        this.serverChannel.close();
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
}
