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
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import static org.redkale.net.ProtocolServer.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class AsyncConnection implements AsynchronousByteChannel, AutoCloseable {

    protected SSLContext sslContext;

    protected Map<String, Object> attributes; //用于存储绑定在Connection上的对象集合

    protected Object subobject; //用于存储绑定在Connection上的对象， 同attributes， 只绑定单个对象时尽量使用subobject而非attributes

    protected volatile long readtime;

    protected volatile long writetime;

    //在线数
    protected AtomicLong livingCounter;

    //关闭数
    protected AtomicLong closedCounter;

    protected Consumer<AsyncConnection> beforeCloseListener;

    public final long getLastReadTime() {
        return readtime;
    }

    public final long getLastWriteTime() {
        return writetime;
    }

    public abstract boolean isTCP();

    public abstract SocketAddress getRemoteAddress();

    public abstract SocketAddress getLocalAddress();

    public abstract int getReadTimeoutSeconds();

    public abstract int getWriteTimeoutSeconds();

    public abstract void setReadTimeoutSeconds(int readTimeoutSeconds);

    public abstract void setWriteTimeoutSeconds(int writeTimeoutSeconds);

    @Override
    public abstract Future<Integer> read(ByteBuffer dst);

    @Override
    public abstract <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler);

    public abstract <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler);

    @Override
    public abstract Future<Integer> write(ByteBuffer src);

    @Override
    public abstract <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler);

    public final <A> void write(ByteBuffer[] srcs, A attachment, CompletionHandler<Integer, ? super A> handler) {
        write(srcs, 0, srcs.length, attachment, handler);
    }

    public abstract <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler);

    public void dispose() {//同close， 只是去掉throws IOException
        try {
            this.close();
        } catch (IOException io) {
        }
    }

    public AsyncConnection beforeCloseListener(Consumer<AsyncConnection> beforeCloseListener) {
        this.beforeCloseListener = beforeCloseListener;
        return this;
    }

    @Override
    public void close() throws IOException {
        if (closedCounter != null) {
            closedCounter.incrementAndGet();
            closedCounter = null;
        }
        if (livingCounter != null) {
            livingCounter.decrementAndGet();
            livingCounter = null;
        }
        if (beforeCloseListener != null)
            try {
                beforeCloseListener.accept(this);
            } catch (Exception io) {
            }
        if (attributes == null) return;
        try {
            for (Object obj : attributes.values()) {
                if (obj instanceof AutoCloseable) ((AutoCloseable) obj).close();
            }
        } catch (Exception io) {
        }
    }

    @SuppressWarnings("unchecked")
    public final <T> T getSubobject() {
        return (T) this.subobject;
    }

    public void setSubobject(Object value) {
        this.subobject = value;
    }

    public void setAttribute(String name, Object value) {
        if (this.attributes == null) this.attributes = new HashMap<>();
        this.attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) (this.attributes == null ? null : this.attributes.get(name));
    }

    public final void removeAttribute(String name) {
        if (this.attributes != null) this.attributes.remove(name);
    }

    public final Map<String, Object> getAttributes() {
        return this.attributes;
    }

    public final void clearAttribute() {
        if (this.attributes != null) this.attributes.clear();
    }

    //------------------------------------------------------------------------------------------------------------------------------
    /**
     * 创建TCP协议客户端连接
     *
     * @param address             连接点子
     * @param group               连接AsynchronousChannelGroup
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final AsynchronousChannelGroup group, final SocketAddress address,
        final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return createTCP(group, null, address, supportTcpNoDelay(), readTimeoutSeconds, writeTimeoutSeconds);
    }

    /**
     * 创建TCP协议客户端连接
     *
     * @param address             连接点子
     * @param sslContext          SSLContext
     * @param group               连接AsynchronousChannelGroup
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final AsynchronousChannelGroup group, final SSLContext sslContext,
        final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return createTCP(group, sslContext, address, false, readTimeoutSeconds, writeTimeoutSeconds);
    }

    /**
     * 创建TCP协议客户端连接
     *
     * @param address             连接点子
     * @param sslContext          SSLContext
     * @param group               连接AsynchronousChannelGroup
     * @param noDelay             TcpNoDelay
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final AsynchronousChannelGroup group, final SSLContext sslContext,
        final SocketAddress address, final boolean noDelay, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        final CompletableFuture<AsyncConnection> future = new CompletableFuture<>();
        try {
            final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(group);
            try {
                if (noDelay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                if (supportTcpKeepAlive()) channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            } catch (IOException e) {
            }
            channel.connect(address, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    future.complete(create(channel, sslContext, address, readTimeoutSeconds, writeTimeoutSeconds));
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    future.completeExceptionally(exc);
                }
            });
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * 通常用于 ssl socket
     *
     * @param socket Socket对象
     *
     * @return 连接对象
     */
    public static AsyncConnection create(final Socket socket) {
        return create(socket, null, 0, 0);
    }

    public static AsyncConnection create(final Socket socket, final SocketAddress addr0, final int readTimeoutSecond0, final int writeTimeoutSecond0) {
        return new TcpBioAsyncConnection(socket, addr0, readTimeoutSecond0, writeTimeoutSecond0, null, null);
    }

    public static AsyncConnection create(final Socket socket, final SocketAddress addr0, final int readTimeoutSecond0,
        final int writeTimeoutSecond0, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpBioAsyncConnection(socket, addr0, readTimeoutSecond0, writeTimeoutSecond0, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final SocketChannel ch, SocketAddress addr, final Selector selector,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0) {
        return new TcpNioAsyncConnection(ch, addr, selector, readTimeoutSeconds0, writeTimeoutSeconds0, null, null);
    }

    public static AsyncConnection create(final SocketChannel ch, final SocketAddress addr0, final Selector selector, final Context context) {
        return new TcpNioAsyncConnection(ch, addr0, selector, context.readTimeoutSeconds, context.writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final SocketChannel ch, SocketAddress addr, final Selector selector,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpNioAsyncConnection(ch, addr, selector, readTimeoutSeconds0, writeTimeoutSeconds0, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final DatagramChannel ch, SocketAddress addr,
        final boolean client0, final int readTimeoutSeconds0, final int writeTimeoutSeconds0) {
        return new UdpBioAsyncConnection(ch, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, null, null);
    }

    public static AsyncConnection create(final DatagramChannel ch, SocketAddress addr,
        final boolean client0, final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new UdpBioAsyncConnection(ch, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch) {
        return create(ch, null, 0, 0);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return new TcpAioAsyncConnection(ch, null, addr0, readTimeoutSeconds, writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, SSLContext sslContext, final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return new TcpAioAsyncConnection(ch, sslContext, addr0, readTimeoutSeconds, writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, final SocketAddress addr0, final Context context) {
        return new TcpAioAsyncConnection(ch, context.sslContext, addr0, context.readTimeoutSeconds, context.writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, final SocketAddress addr0, final int readTimeoutSeconds,
        final int writeTimeoutSeconds, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpAioAsyncConnection(ch, null, addr0, readTimeoutSeconds, writeTimeoutSeconds, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, SSLContext sslContext, final SocketAddress addr0, final int readTimeoutSeconds,
        final int writeTimeoutSeconds, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpAioAsyncConnection(ch, sslContext, addr0, readTimeoutSeconds, writeTimeoutSeconds, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final AsynchronousSocketChannel ch, final SocketAddress addr0,
        final Context context, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpAioAsyncConnection(ch, context.sslContext, addr0, context.readTimeoutSeconds, context.writeTimeoutSeconds, livingCounter, closedCounter);
    }
}
