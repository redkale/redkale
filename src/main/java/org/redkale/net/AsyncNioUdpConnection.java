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
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.net.ssl.SSLContext;
import org.redkale.net.AsyncNioUdpProtocolServer.AsyncNioUdpServerChannel;
import org.redkale.util.Utility;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class AsyncNioUdpConnection extends AsyncNioConnection {

    private final DatagramChannel channel;

    private final ConcurrentLinkedDeque<ByteBuffer> revDataQueue = new ConcurrentLinkedDeque<>();

    AsyncNioUdpServerChannel udpServerChannel;

    public AsyncNioUdpConnection(
            boolean clientMode,
            AsyncIOGroup ioGroup,
            AsyncIOThread ioReadThread,
            AsyncIOThread ioWriteThread,
            DatagramChannel ch,
            SSLBuilder sslBuilder,
            SSLContext sslContext,
            final SocketAddress address) {
        super(clientMode, ioGroup, ioReadThread, ioWriteThread, ioGroup.bufferCapacity, sslBuilder, sslContext);
        this.channel = ch;
        SocketAddress addr = address;
        if (addr == null) {
            try {
                addr = ch.getRemoteAddress();
            } catch (Exception e) {
                // do nothing
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
        if (clientMode) {
            return this.channel.isConnected();
        } else {
            return true;
        }
    }

    @Override
    protected SelectionKey keyFor(Selector sel) {
        return this.channel.keyFor(sel);
    }

    @Override
    protected SelectionKey implRegister(Selector sel, int ops) throws ClosedChannelException {
        return this.channel.register(sel, ops);
    }

    @Override
    protected int implRead(ByteBuffer dst) throws IOException {
        if (clientMode) {
            return this.channel.read(dst);
        } else {
            int start = dst.position();
            while (dst.hasRemaining()) {
                ByteBuffer buf = revDataQueue.poll();
                if (buf == null) {
                    break;
                }
                dst.put(buf);
                if (buf.hasRemaining()) {
                    revDataQueue.offerFirst(buf);
                } else {
                    udpServerChannel.unsafeBufferPool.accept(buf);
                }
            }
            return dst.position() - start;
        }
    }

    void receiveData(ByteBuffer buf) {
        this.ioReadThread.execute(() -> {
            revDataQueue.offer(buf.flip());
            doRead(this.ioReadThread.inCurrThread());
        });
    }

    @Override
    protected int implWrite(ByteBuffer src) throws IOException {

        if (clientMode) {
            long now = System.currentTimeMillis();
            if (this.writeTime + 1 > now) {
                Utility.sleep(1); // 发送过频会丢包
                this.writeTime = System.currentTimeMillis();
            } else {
                this.writeTime = now;
            }
            return this.channel.send(src, remoteAddress);
        } else {
            udpServerChannel.writeLock.lock();
            try {
                long now = System.currentTimeMillis();
                if (udpServerChannel.writeTime + 1 > now) {
                    Utility.sleep(1); // 发送过频会丢包
                    udpServerChannel.writeTime = System.currentTimeMillis();
                } else {
                    udpServerChannel.writeTime = now;
                }
                return this.channel.send(src, remoteAddress);
            } finally {
                udpServerChannel.writeLock.unlock();
            }
        }
    }

    @Override
    protected int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            ByteBuffer buf = srcs[i];
            if (buf.hasRemaining()) {
                return implWrite(buf);
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
        if (clientMode) {
            channel.close();
        } else if (remoteAddress != null) {
            udpServerChannel.connections.remove(remoteAddress);
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
