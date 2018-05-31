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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TcpAioAsyncConnection extends AsyncConnection {

    private int readTimeoutSeconds;

    private int writeTimeoutSeconds;

    private final AsynchronousSocketChannel channel;

    private final SocketAddress remoteAddress;

    public TcpAioAsyncConnection(final AsynchronousSocketChannel ch, SSLContext sslContext,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this.channel = ch;
        this.sslContext = sslContext;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        SocketAddress addr = addr0;
        if (addr == null) {
            try {
                addr = ch.getRemoteAddress();
            } catch (Exception e) {
                //do nothing
            }
        }
        this.remoteAddress = addr;
        this.livingCounter = livingCounter;
        this.closedCounter = closedCounter;
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
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.readtime = System.currentTimeMillis();
        if (readTimeoutSeconds > 0) {
            channel.read(dst, readTimeoutSeconds, TimeUnit.SECONDS, attachment, handler);
        } else {
            channel.read(dst, attachment, handler);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.readtime = System.currentTimeMillis();
        channel.read(dst, timeout < 0 ? 0 : timeout, unit, attachment, handler);
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.writetime = System.currentTimeMillis();
        if (writeTimeoutSeconds > 0) {
            channel.write(src, writeTimeoutSeconds, TimeUnit.SECONDS, attachment, handler);
        } else {
            channel.write(src, attachment, handler);
        }
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, final CompletionHandler<Integer, ? super A> handler) {
        this.writetime = System.currentTimeMillis();
        channel.write(srcs, offset, length, writeTimeoutSeconds > 0 ? writeTimeoutSeconds : 60, TimeUnit.SECONDS,
            attachment, new CompletionHandler<Long, A>() {

            @Override
            public void completed(Long result, A attachment) {
                handler.completed(result.intValue(), attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }

        });
    }

    @Override
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    @Override
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        this.writeTimeoutSeconds = writeTimeoutSeconds;
    }

    @Override
    public int getReadTimeoutSeconds() {
        return this.readTimeoutSeconds;
    }

    @Override
    public int getWriteTimeoutSeconds() {
        return this.writeTimeoutSeconds;
    }

    @Override
    public final SocketAddress getRemoteAddress() {
        return remoteAddress;
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
    public final Future<Integer> read(ByteBuffer dst) {
        return channel.read(dst);
    }

    @Override
    public final Future<Integer> write(ByteBuffer src) {
        return channel.write(src);
    }

    @Override
    public final void close() throws IOException {
        super.close();
        channel.close();
    }

    @Override
    public final boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public final boolean isTCP() {
        return true;
    }

}
