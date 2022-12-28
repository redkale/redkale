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
import javax.net.ssl.SSLContext;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class AsyncNioUdpConnection extends AsyncNioConnection {

    private final DatagramChannel channel;

    public AsyncNioUdpConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread, DatagramChannel ch,
        SSLBuilder sslBuilder, SSLContext sslContext, final SocketAddress addr0, LongAdder livingCounter, LongAdder closedCounter) {
        super(client, ioGroup, ioThread, connectThread, ioGroup.bufferCapacity, ioThread.getBufferSupplier(), ioThread.getBufferConsumer(), sslBuilder, sslContext, livingCounter, closedCounter);
        this.channel = ch;
        SocketAddress addr = addr0;
        if (addr == null) {
            try {
                addr = ch.getRemoteAddress();
            } catch (Exception e) {
                //do nothing
            }
        }
        this.remoteAddress = addr;
    }

    public AsyncNioUdpConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread,
        Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer,
        DatagramChannel ch, SSLBuilder sslBuilder, SSLContext sslContext, final SocketAddress addr0,
        LongAdder livingCounter, LongAdder closedCounter) {
        super(client, ioGroup, ioThread, connectThread, ioGroup.bufferCapacity, bufferSupplier, bufferConsumer, sslBuilder, sslContext, livingCounter, closedCounter);
        this.channel = ch;
        SocketAddress addr = addr0;
        if (addr == null) {
            try {
                addr = ch.getRemoteAddress();
            } catch (Exception e) {
                //do nothing
            }
        }
        this.remoteAddress = addr;
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }

    @Override
    public boolean isTCP() {
        return false;
    }

    @Override
    public boolean shutdownInput() {
        return true;
    }

    @Override
    public boolean shutdownOutput() {
        return true;
    }

    @Override
    public <T> boolean setOption(SocketOption<T> name, T value) {
        try {
            this.channel.setOption(name, value);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return this.channel.supportedOptions();
    }

    @Override
    public SocketAddress getLocalAddress() {
        try {
            return channel.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public ReadableByteChannel readableByteChannel() {
        if (this.sslEngine == null) {
            return this.channel;
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public WritableByteChannel writableByteChannel() {
        if (this.sslEngine == null) {
            return this.channel;
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isConnected() {
        if (!client) {
            return true;
        }
        return this.channel.isConnected();
    }

    @Override
    protected SelectionKey implRegister(Selector sel, int ops) throws ClosedChannelException {
        return this.channel.register(sel, ops);
    }

    @Override
    protected int implRead(ByteBuffer dst) throws IOException {
        return this.channel.read(dst);
    }

    @Override
    protected int implWrite(ByteBuffer src) throws IOException {
        return this.channel.send(src, remoteAddress);
    }

    @Override
    protected int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            ByteBuffer buf = srcs[i];
            if (buf.hasRemaining()) {
                return this.channel.send(buf, remoteAddress);
            }
        }
        return 0;
    }

    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        this.connectAttachment = attachment;
        this.connectCompletionHandler = (CompletionHandler<Void, Object>) handler;
        this.remoteAddress = remote;
        doConnect();
    }

    @Override
    public void doConnect() {
        try {
            channel.connect(remoteAddress);
            handleConnect(null);
        } catch (IOException e) {
            handleConnect(e);
        }
    }

    @Override
    public final void close() throws IOException {
        super.close();
        if (client) {
            channel.close(); //不能关闭channel
        }
        if (this.connectKey != null) {
            this.connectKey.cancel();
        }
        if (this.readKey != null) {
            this.readKey.cancel();
        }
        if (this.writeKey != null) {
            this.writeKey.cancel();
        }
    }

}
