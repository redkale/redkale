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
import java.util.concurrent.CountDownLatch;
import org.redkale.util.AnyValue;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TcpNioProtocolServer extends ProtocolServer {

    private Selector acceptSelector;

    private ServerSocketChannel serverChannel;

    private NIOThreadWorker[] workers;

    private NIOThreadWorker currWorker;

    private boolean running;

    public TcpNioProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        acceptSelector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        ServerSocket socket = serverChannel.socket();
        socket.setReceiveBufferSize(16 * 1024);
        socket.setReuseAddress(true);

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
            this.serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
        }
        if (options.contains(StandardSocketOptions.SO_SNDBUF)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
        }
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
            AsyncConnection conn = new TcpNioAsyncConnection(channel, null, selector, context.readTimeoutSeconds, context.writeTimeoutSeconds, null, null);
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
            TcpNioAsyncConnection conn = (TcpNioAsyncConnection) key.attachment();
            if (!socket.isOpen()) {
                if (conn == null) {
                    key.cancel();
                } else {
                    conn.dispose();
                }
                return;
            }
            if (conn == null) return;
            if (key.isWritable()) {
                if (conn.writeHandler != null) writeOP(key, socket, conn);
            } else if (key.isReadable()) {
                if (conn.readHandler != null) readOP(key, socket, conn);
            }
        }

        private void readOP(SelectionKey key, SocketChannel socket, TcpNioAsyncConnection conn) {
            final CompletionHandler handler = conn.removeReadHandler();
            final ByteBuffer buffer = conn.removeReadBuffer();
            final Object attach = conn.removeReadAttachment();
            //System.out.println(conn + "------readbuf:" + buffer + "-------handler:" + handler);
            if (handler == null || buffer == null) return;
            try {
                final int rs = socket.read(buffer);
                {  //测试
                    buffer.flip();
                    byte[] bs = new byte[buffer.remaining()];
                    buffer.get(bs);
                    //System.out.println(conn + "------readbuf:" + buffer + "-------handler:" + handler + "-------读内容: " + new String(bs));
                }
                //System.out.println(conn + "------readbuf:" + buffer + "-------handler:" + handler + "-------read: " + rs);
                context.runAsync(() -> {
                    try {
                        handler.completed(rs, attach);
                    } catch (Throwable e) {
                        handler.failed(e, attach);
                    }
                });
            } catch (Throwable t) {
                context.runAsync(() -> handler.failed(t, attach));
            }
        }

        private void writeOP(SelectionKey key, SocketChannel socket, TcpNioAsyncConnection conn) {
            final CompletionHandler handler = conn.writeHandler;
            final ByteBuffer oneBuffer = conn.removeWriteOneBuffer();
            final ByteBuffer[] buffers = conn.removeWriteBuffers();
            final Object attach = conn.removeWriteAttachment();
            final int writingCount = conn.removeWritingCount();
            final int writeOffset = conn.removeWriteOffset();
            final int writeLength = conn.removeWriteLength();
            if (handler == null || (oneBuffer == null && buffers == null)) return;
            //System.out.println(conn + "------buffers:" + Arrays.toString(buffers) + "---onebuf:" + oneBuffer + "-------handler:" + handler);
            try {
                int rs = 0;
                if (oneBuffer == null) {
                    int offset = writeOffset;
                    int length = writeLength;
                    rs = (int) socket.write(buffers, offset, length);
                    boolean over = true;
                    int end = offset + length;
                    for (int i = offset; i < end; i++) {
                        if (buffers[i].hasRemaining()) {
                            over = false;
                            length -= i - offset;
                            offset = i;
                        }
                    }
                    if (!over) {
                        conn.writingCount += rs;
                        conn.writeHandler = handler;
                        conn.writeAttachment = attach;
                        conn.writeBuffers = buffers;
                        conn.writeOffset = offset;
                        conn.writeLength = length;
                        key.interestOps(SelectionKey.OP_READ + SelectionKey.OP_WRITE);
                        key.selector().wakeup();
                        return;
                    }
                } else {
                    rs = socket.write(oneBuffer);
                    if (oneBuffer.hasRemaining()) {
                        conn.writingCount += rs;
                        conn.writeHandler = handler;
                        conn.writeAttachment = attach;
                        conn.writeOneBuffer = oneBuffer;
                        key.interestOps(SelectionKey.OP_READ + SelectionKey.OP_WRITE);
                        key.selector().wakeup();
                        return;
                    }
                }
                conn.removeWriteHandler();
                key.interestOps(SelectionKey.OP_READ); //OP_CONNECT
                final int rs0 = rs + writingCount;
                //System.out.println(conn + "------buffers:" + Arrays.toString(buffers) + "---onebuf:" + oneBuffer + "-------handler:" + handler + "-------write: " + rs);
                context.runAsync(() -> {
                    try {
                        handler.completed(rs0, attach);
                    } catch (Throwable e) {
                        handler.failed(e, attach);
                    }
                });
            } catch (Throwable t) {
                context.runAsync(() -> handler.failed(t, attach));
            }
        }

    }
}
