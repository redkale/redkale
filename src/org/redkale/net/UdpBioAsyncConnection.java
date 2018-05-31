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

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class UdpBioAsyncConnection extends AsyncConnection {

    private int readTimeoutSeconds;

    private int writeTimeoutSeconds;

    private final DatagramChannel channel;

    private final SocketAddress remoteAddress;

    private final boolean client;

    public UdpBioAsyncConnection(final DatagramChannel ch, SocketAddress addr0,
        final boolean client0, final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this.channel = ch;
        this.client = client0;
        this.readTimeoutSeconds = readTimeoutSeconds0;
        this.writeTimeoutSeconds = writeTimeoutSeconds0;
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
    public boolean shutdownInput() {
        return false;
    }

    @Override
    public boolean shutdownOutput() {
        return false;
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
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = 0;
            for (int i = offset; i < offset + length; i++) {
                rs += channel.send(srcs[i], remoteAddress);
                if (i != offset) Thread.sleep(10);
            }
            this.writetime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (Exception e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = channel.read(dst);
            this.readtime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (IOException e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        read(dst, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        try {
            int rs = channel.read(dst);
            this.readtime = System.currentTimeMillis();
            return CompletableFuture.completedFuture(rs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = channel.send(src, remoteAddress);
            this.writetime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (IOException e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        try {
            int rs = channel.send(src, remoteAddress);
            this.writetime = System.currentTimeMillis();
            return CompletableFuture.completedFuture(rs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void close() throws IOException {
        super.close();
        if (client) channel.close();
    }

    @Override
    public final boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public final boolean isTCP() {
        return false;
    }
}
