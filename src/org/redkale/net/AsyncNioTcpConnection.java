/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.net.ssl.SSLContext;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
class AsyncNioTcpConnection extends AsyncNioConnection {

    private final SocketChannel channel;

    public AsyncNioTcpConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread,
        SocketChannel ch, SSLContext sslContext, final SocketAddress addr0, AtomicLong livingCounter, AtomicLong closedCounter) {
        super(client, ioGroup, ioThread, connectThread, ioGroup.bufferCapacity, ioThread.getBufferSupplier(), ioThread.getBufferConsumer(), sslContext, livingCounter, closedCounter);
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
        ioThread.connCounter.incrementAndGet();
    }

    public AsyncNioTcpConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread, Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer,
        SocketChannel ch, SSLContext sslContext, final SocketAddress addr0, AtomicLong livingCounter, AtomicLong closedCounter) {
        super(client, ioGroup, ioThread, connectThread, ioGroup.bufferCapacity, bufferSupplier, bufferConsumer, sslContext, livingCounter, closedCounter);
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
        return true;
    }

    @Override
    public boolean shutdownInput() {
        try {
            this.channel.shutdownInput();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean shutdownOutput() {
        try {
            this.channel.shutdownOutput();
            return true;
        } catch (IOException e) {
            return false;
        }
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
        return this.channel;
    }

    @Override
    public WritableByteChannel writableByteChannel() {
        return this.channel;
    }

    @Override
    public boolean isConnected() {
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
        return this.channel.write(src);
    }

    @Override
    protected int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return (int) this.channel.write(srcs, offset, length);
    }

    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        if (channel.isConnected()) {
            throw new AlreadyConnectedException();
        }
        if (connectPending) {
            throw new ConnectionPendingException();
        }
        connectPending = true;
        this.connectAttachment = attachment;
        this.connectCompletionHandler = (CompletionHandler<Void, Object>) handler;
        this.remoteAddress = remote;
        doConnect();
    }

    @Override
    public void doConnect() {
        try {
            boolean connected = channel.isConnectionPending();
            if (connected || channel.connect(remoteAddress)) {
                connected = channel.finishConnect();
            }
            if (connected) {
                handleConnect(null);
            } else if (connectKey == null) {
                connectThread.register(selector -> {
                    try {
                        connectKey = channel.register(selector, SelectionKey.OP_CONNECT);
                        connectKey.attach(this);
                    } catch (ClosedChannelException e) {
                        handleConnect(e);
                    }
                });
            } else {
                handleConnect(new IOException());
            }
        } catch (IOException e) {
            handleConnect(e);
        }
    }

    @Override
    public final void close() throws IOException {
        super.close();
        ioThread.connCounter.decrementAndGet();
        channel.shutdownInput();
        channel.shutdownOutput();
        channel.close();
        if (this.connectKey != null) this.connectKey.cancel();
        if (this.readKey != null) this.readKey.cancel();
        if (this.writeKey != null) this.writeKey.cancel();
    }
}
