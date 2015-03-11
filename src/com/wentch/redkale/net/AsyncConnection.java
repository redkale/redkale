/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 */
public abstract class AsyncConnection implements AsynchronousByteChannel, AutoCloseable {

    protected AsyncPooledConnection pooledConnection;

    public abstract SocketAddress getRemoteAddress();

    public abstract int getReadTimeoutSecond();

    public abstract int getWriteTimeoutSecond();

    public abstract void setReadTimeoutSecond(int readTimeoutSecond);

    public abstract void setWriteTimeoutSecond(int writeTimeoutSecond);

    public abstract void dispose(); //同close， 只是去掉throws IOException

    public static AsyncConnection create(final String protocol, final SocketAddress address) throws IOException {
        return create(protocol, address, 0, 0);
    }

    /**
     * 创建客户端连接
     *
     * @param protocol
     * @param address
     * @param readTimeoutSecond0
     * @param writeTimeoutSecond0
     * @return
     * @throws java.io.IOException
     */
    public static AsyncConnection create(final String protocol, final SocketAddress address,
            final int readTimeoutSecond0, final int writeTimeoutSecond0) throws IOException {
        if ("TCP".equalsIgnoreCase(protocol)) {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            try {
                channel.connect(address).get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IOException("AsyncConnection connect " + address, e);
            }
            return create(channel, readTimeoutSecond0, writeTimeoutSecond0);
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            AsyncDatagramChannel channel = AsyncDatagramChannel.open(null);
            channel.connect(address);
            return create(channel, address, true, readTimeoutSecond0, writeTimeoutSecond0);
        } else {
            throw new RuntimeException("AsyncConnection not support protocol " + protocol);
        }
    }

    public static AsyncConnection create(final AsyncDatagramChannel ch, SocketAddress addr, final boolean client0) {
        return create(ch, addr, client0, 0, 0);
    }

    public static AsyncConnection create(final AsyncDatagramChannel ch, SocketAddress addr,
            final boolean client0, final int readTimeoutSecond0, final int writeTimeoutSecond0) {
        return new AsyncConnection() {
            private int readTimeoutSecond;

            private int writeTimeoutSecond;

            private final AsyncDatagramChannel channel;

            private final SocketAddress remoteAddress;

            private final boolean client;

            {
                this.channel = ch;
                this.client = client0;
                this.readTimeoutSecond = readTimeoutSecond0;
                this.writeTimeoutSecond = writeTimeoutSecond0;
                this.remoteAddress = addr;
            }

            @Override
            public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
                if (readTimeoutSecond > 0) {
                    channel.read(dst, readTimeoutSecond, TimeUnit.SECONDS, attachment, handler);
                } else {
                    channel.read(dst, attachment, handler);
                }
            }

            @Override
            public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
                channel.send(src, remoteAddress, attachment, handler);
            }

            @Override
            public void setReadTimeoutSecond(int readTimeoutSecond) {
                this.readTimeoutSecond = readTimeoutSecond;
            }

            @Override
            public void setWriteTimeoutSecond(int writeTimeoutSecond) {
                this.writeTimeoutSecond = writeTimeoutSecond;
            }

            @Override
            public int getReadTimeoutSecond() {
                return this.readTimeoutSecond;
            }

            @Override
            public int getWriteTimeoutSecond() {
                return this.writeTimeoutSecond;
            }

            @Override
            public final SocketAddress getRemoteAddress() {
                return remoteAddress;
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
                if (client) {
                    if (pooledConnection == null) {
                        channel.close();
                    } else {
                        pooledConnection.fireConnectionClosed();
                    }
                }
            }

            @Override
            public void dispose() {
                try {
                    this.close();
                } catch (IOException io) {
                }
            }

            @Override
            public final boolean isOpen() {
                return channel.isOpen();
            }

        };
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch) {
        return create(ch, 0, 0);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, final int readTimeoutSecond0, final int writeTimeoutSecond0) {
        return new AsyncConnection() {
            private int readTimeoutSecond;

            private int writeTimeoutSecond;

            private final AsynchronousSocketChannel channel;

            private final SocketAddress remoteAddress;

            {
                this.channel = ch;
                this.readTimeoutSecond = readTimeoutSecond0;
                this.writeTimeoutSecond = writeTimeoutSecond0;
                SocketAddress addr = null;
                try {
                    addr = ch.getRemoteAddress();
                } catch (Exception e) {
                    //do nothing
                }
                this.remoteAddress = addr;
            }

            @Override
            public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
                if (readTimeoutSecond > 0) {
                    channel.read(dst, readTimeoutSecond, TimeUnit.SECONDS, attachment, handler);
                } else {
                    channel.read(dst, attachment, handler);
                }
            }

            @Override
            public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
                if (writeTimeoutSecond > 0) {
                    channel.write(src, writeTimeoutSecond, TimeUnit.SECONDS, attachment, handler);
                } else {
                    channel.write(src, attachment, handler);
                }
            }

            @Override
            public void setReadTimeoutSecond(int readTimeoutSecond) {
                this.readTimeoutSecond = readTimeoutSecond;
            }

            @Override
            public void setWriteTimeoutSecond(int writeTimeoutSecond) {
                this.writeTimeoutSecond = writeTimeoutSecond;
            }

            @Override
            public int getReadTimeoutSecond() {
                return this.readTimeoutSecond;
            }

            @Override
            public int getWriteTimeoutSecond() {
                return this.writeTimeoutSecond;
            }

            @Override
            public final SocketAddress getRemoteAddress() {
                return remoteAddress;
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
                if (pooledConnection == null) {
                    channel.close();
                } else {
                    pooledConnection.fireConnectionClosed();
                }
            }

            @Override
            public final boolean isOpen() {
                return channel.isOpen();
            }

            @Override
            public void dispose() {
                try {
                    this.close();
                } catch (IOException io) {
                }
            }
        };
    }

}
