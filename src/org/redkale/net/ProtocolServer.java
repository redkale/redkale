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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.net.AsyncConnection.NIOTCPAsyncConnection;
import org.redkale.util.AnyValue;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class ProtocolServer {

    protected static final boolean supportTcpNoDelay;

    protected static final boolean supportTcpKeepAlive;

    static {
        boolean tcpNoDelay = false;
        boolean keepAlive = false;
        try {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            tcpNoDelay = channel.supportedOptions().contains(StandardSocketOptions.TCP_NODELAY);
            keepAlive = channel.supportedOptions().contains(StandardSocketOptions.SO_KEEPALIVE);
            channel.close();
        } catch (Exception e) {
        }
        supportTcpNoDelay = tcpNoDelay;
        supportTcpKeepAlive = keepAlive;
    }

    //创建数
    protected final AtomicLong createCounter = new AtomicLong();

    //关闭数
    protected final AtomicLong closedCounter = new AtomicLong();

    //在线数
    protected final AtomicLong livingCounter = new AtomicLong();

    //最大连接数，小于1表示无限制
    protected int maxconns;

    public abstract void open(AnyValue config) throws IOException;

    public abstract void bind(SocketAddress local, int backlog) throws IOException;

    public abstract <T> Set<SocketOption<?>> supportedOptions();

    public abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;

    public abstract void accept() throws IOException;

    public void setMaxconns(int maxconns) {
        this.maxconns = maxconns;
    }

    public abstract void close() throws IOException;

    public long getCreateCount() {
        return createCounter.longValue();
    }

    public long getClosedCount() {
        return closedCounter.longValue();
    }

    public long getLivingCount() {
        return livingCounter.longValue();
    }

    //---------------------------------------------------------------------
    public static ProtocolServer create(String protocol, Context context) {
        if ("TCP".equalsIgnoreCase(protocol)) return new ProtocolAIOTCPServer(context);
        if ("UDP".equalsIgnoreCase(protocol)) return new ProtocolUDPServer(context);
        throw new RuntimeException("ProtocolServer not support protocol " + protocol);
    }

    public static boolean supportTcpNoDelay() {
        return supportTcpNoDelay;
    }

    public static boolean supportTcpKeepAlive() {
        return supportTcpKeepAlive;
    }

    static final class ProtocolUDPServer extends ProtocolServer {

        private boolean running;

        private final Context context;

        private DatagramChannel serverChannel;

        public ProtocolUDPServer(Context context) {
            this.context = context;
        }

        @Override
        public void open(AnyValue config) throws IOException {
            DatagramChannel ch = DatagramChannel.open();
            ch.configureBlocking(true);
            this.serverChannel = ch;
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
        public void accept() throws IOException {
            final DatagramChannel serchannel = this.serverChannel;
            final int readTimeoutSeconds = this.context.readTimeoutSeconds;
            final int writeTimeoutSeconds = this.context.writeTimeoutSeconds;
            final CountDownLatch cdl = new CountDownLatch(1);
            this.running = true;
            new Thread() {
                @Override
                public void run() {
                    cdl.countDown();
                    while (running) {
                        final ByteBuffer buffer = context.pollBuffer();
                        try {
                            SocketAddress address = serchannel.receive(buffer);
                            buffer.flip();
                            AsyncConnection conn = AsyncConnection.create(serchannel, address, false, readTimeoutSeconds, writeTimeoutSeconds);
                            context.runAsync(new PrepareRunner(context, conn, buffer, null));
                        } catch (Exception e) {
                            context.offerBuffer(buffer);
                        }
                    }
                }
            }.start();
            try {
                cdl.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws IOException {
            this.running = false;
            this.serverChannel.close();
        }

        @Override
        public long getCreateCount() {
            return -1;
        }

        @Override
        public long getClosedCount() {
            return -1;
        }

        @Override
        public long getLivingCount() {
            return -1;
        }
    }

    static final class ProtocolAIOTCPServer extends ProtocolServer {

        private final Context context;

        private AsynchronousChannelGroup group;

        private AsynchronousServerSocketChannel serverChannel;

        public ProtocolAIOTCPServer(Context context) {
            this.context = context;
        }

        @Override
        public void open(AnyValue config) throws IOException {
            group = AsynchronousChannelGroup.withCachedThreadPool(context.executor, 1);
            this.serverChannel = AsynchronousServerSocketChannel.open(group);
        }

        @Override
        public void bind(SocketAddress local, int backlog) throws IOException {
            this.serverChannel.bind(local, backlog);
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
        public void accept() throws IOException {
            final AsynchronousServerSocketChannel serchannel = this.serverChannel;
            serchannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

                private boolean supportInited;

                private boolean supportTcpLay;

                private boolean supportAlive;

                private boolean supportReuse;

                private boolean supportRcv;

                private boolean supportSnd;

                @Override
                public void completed(final AsynchronousSocketChannel channel, Void attachment) {
                    serchannel.accept(null, this);
                    if (maxconns > 0 && livingCounter.get() >= maxconns) {
                        try {
                            channel.close();
                        } catch (Exception e) {
                        }
                        return;
                    }
                    createCounter.incrementAndGet();
                    livingCounter.incrementAndGet();
                    AsyncConnection conn = AsyncConnection.create(channel, null, context);
                    conn.livingCounter = livingCounter;
                    conn.closedCounter = closedCounter;
                    try {
                        if (!supportInited) {
                            synchronized (this) {
                                if (!supportInited) {
                                    supportInited = true;
                                    final Set<SocketOption<?>> options = channel.supportedOptions();
                                    supportTcpLay = options.contains(StandardSocketOptions.TCP_NODELAY);
                                    supportAlive = options.contains(StandardSocketOptions.SO_KEEPALIVE);
                                    supportReuse = options.contains(StandardSocketOptions.SO_REUSEADDR);
                                    supportRcv = options.contains(StandardSocketOptions.SO_RCVBUF);
                                    supportSnd = options.contains(StandardSocketOptions.SO_SNDBUF);
                                }
                            }
                        }
                        if (supportTcpLay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        if (supportAlive) channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                        if (supportReuse) channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                        if (supportRcv) channel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
                        if (supportSnd) channel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    context.runAsync(new PrepareRunner(context, conn, null, null));
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    serchannel.accept(null, this);
                    //if (exc != null) context.logger.log(Level.FINEST, AsynchronousServerSocketChannel.class.getSimpleName() + " accept erroneous", exc);
                }
            });
        }

        @Override
        public void close() throws IOException {
            this.serverChannel.close();
        }

    }

    static final class ProtocolNIOTCPServer extends ProtocolServer {

        private final Context context;

        private Selector acceptSelector;

        private ServerSocketChannel serverChannel;

        private NIOThreadWorker[] workers;

        private NIOThreadWorker currWorker;

        private boolean running;

        public ProtocolNIOTCPServer(Context context) {
            this.context = context;
        }

        @Override
        public void open(AnyValue config) throws IOException {
            acceptSelector = Selector.open();
            this.serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            ServerSocket socket = serverChannel.socket();
            socket.setReceiveBufferSize(16 * 1024);
            socket.setReuseAddress(true);
        }

        @Override
        public void bind(SocketAddress local, int backlog) throws IOException {
            this.serverChannel.bind(local, backlog);
        }

        @Override
        public <T> Set<SocketOption<?>> supportedOptions() {
            return this.serverChannel.supportedOptions();
        }

        @Override
        public <T> void setOption(SocketOption<T> name, T value) throws IOException {
            this.serverChannel.setOption(name, value);
        }

        @Override
        public void accept() throws IOException {
            this.serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
            final CountDownLatch cdl = new CountDownLatch(1);
            this.running = true;
            this.workers = new NIOThreadWorker[Runtime.getRuntime().availableProcessors()];
            for (int i = 0; i < workers.length; i++) {
                workers[i] = new NIOThreadWorker();
                workers[i].setDaemon(true);
                workers[i].start();
            }
            for (int i = 0; i < workers.length - 1; i++) { //构成环形
                workers[i].next = workers[i + 1];
            }
            workers[workers.length - 1].next = workers[0];
            currWorker = workers[0];
            new Thread() {
                @Override
                public void run() {
                    cdl.countDown();
                    while (running) {
                        try {
                            acceptSelector.select();
                            Set<SelectionKey> selectedKeys = acceptSelector.selectedKeys();
                            synchronized (selectedKeys) {
                                Iterator<?> iter = selectedKeys.iterator();
                                while (iter.hasNext()) {
                                    SelectionKey key = (SelectionKey) iter.next();
                                    iter.remove();
                                    if (key.isAcceptable()) {
                                        try {
                                            SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
                                            channel.configureBlocking(false);
                                            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                                            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                                            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                                            channel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
                                            channel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
                                            createCounter.incrementAndGet();
                                            livingCounter.incrementAndGet();
                                            currWorker.addChannel(channel);
                                            currWorker = currWorker.next;
                                        } catch (IOException io) {
                                            io.printStackTrace();
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            }.start();
            try {
                cdl.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws IOException {
            if (!this.running) return;
            this.running = false;
            serverChannel.close();
            acceptSelector.close();
            for (NIOThreadWorker worker : workers) {
                worker.interrupt();
            }
        }

        class NIOThreadWorker extends Thread {

            final Selector selector;

            NIOThreadWorker next;

            public NIOThreadWorker() {
                try {
                    this.selector = Selector.open();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public void addChannel(SocketChannel channel) throws IOException {
                AsyncConnection conn = AsyncConnection.create(channel, null, this.selector, context);
                context.runAsync(new PrepareRunner(context, conn, null, null));
            }

            @Override
            public void run() {
                while (running) {
                    try {
                        selector.select(50);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        Set<SelectionKey> selectedKeys = selector.selectedKeys();
                        synchronized (selectedKeys) {
                            Iterator<?> iter = selectedKeys.iterator();
                            while (iter.hasNext()) {
                                SelectionKey key = (SelectionKey) iter.next();
                                iter.remove();
                                processKey(key);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            private void processKey(SelectionKey key) {
                if (key == null || !key.isValid()) return;
                SocketChannel socket = (SocketChannel) key.channel();
                NIOTCPAsyncConnection conn = (NIOTCPAsyncConnection) key.attachment();
                if(conn == null) return;
                if (key.isReadable()) {
                    if (conn.readHandler != null) readOP(key, socket, conn);
                } else if (key.isWritable()) {
                    if (conn.writeHandler != null) writeOP(key, socket, conn);
                }
            }

            private void readOP(SelectionKey key, SocketChannel socket, NIOTCPAsyncConnection conn) {
                try {
                    final int rs = socket.read(conn.readBuffer);
                    //System.out.println(conn + "------readbuf:" + conn.readBuffer + "-------handler:" + conn.readHandler + "-------read: " + rs);
                    context.runAsync(() -> conn.completeRead(rs));
                } catch (Throwable t) {
                    context.runAsync(() -> conn.faileRead(t));
                }
            }

            private void writeOP(SelectionKey key, SocketChannel socket, NIOTCPAsyncConnection conn) {
                try {
                    int rs = 0;
                    if (conn.writeOneBuffer == null) {
                        final ByteBuffer[] buffers = conn.writeBuffers;
                        int offset = conn.writeOffset;
                        int length = conn.writeLength;
                        for (;;) {
                            long sr = socket.write(buffers, offset, length);
                            if (sr > 0) rs += sr;
                            boolean over = true;
                            int end = offset + length;
                            for (int i = offset; i < end; i++) {
                                if (buffers[i].hasRemaining()) {
                                    over = false;
                                    length -= i - offset;
                                    offset = i;
                                }
                            }
                            if (over) break;
                        }
                    } else {
                        final ByteBuffer buffer = conn.writeOneBuffer;
                        while (buffer.hasRemaining()) rs += socket.write(buffer);
                    }
                    key.interestOps(SelectionKey.OP_READ);
                    final int rs0 = rs;
                    //System.out.println(conn + "------buffers:" + conn.writeBuffers + "---onebuf:" + conn.writeOneBuffer + "-------handler:" + conn.writeHandler + "-------write: " + rs);
                    context.runAsync(() -> conn.completeWrite(rs0));
                } catch (Throwable t) {
                    context.runAsync(() -> conn.faileWrite(t));
                }
            }

        }
    }

}
