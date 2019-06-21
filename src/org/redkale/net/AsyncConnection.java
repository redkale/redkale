/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.net.ssl.SSLContext;
import org.redkale.util.ObjectPool;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class AsyncConnection implements ReadableByteChannel, WritableByteChannel, AutoCloseable {

    protected SSLContext sslContext;

    protected Map<String, Object> attributes; //用于存储绑定在Connection上的对象集合

    protected Object subobject; //用于存储绑定在Connection上的对象， 同attributes， 只绑定单个对象时尽量使用subobject而非attributes

    protected volatile long readtime;

    protected volatile long writetime;

    protected final Supplier<ByteBuffer> bufferSupplier;

    protected final Consumer<ByteBuffer> bufferConsumer;

    protected ByteBuffer readBuffer;

    //在线数
    protected AtomicLong livingCounter;

    //关闭数
    protected AtomicLong closedCounter;

    protected Consumer<AsyncConnection> beforeCloseListener;

    //关联的事件数， 小于1表示没有事件
    protected final AtomicInteger eventing = new AtomicInteger();

    protected AsyncConnection(ObjectPool<ByteBuffer> bufferPool, SSLContext sslContext) {
        this(bufferPool, bufferPool, sslContext);
    }

    protected AsyncConnection(Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer, SSLContext sslContext) {
        Objects.requireNonNull(bufferSupplier);
        Objects.requireNonNull(bufferConsumer);
        this.bufferSupplier = bufferSupplier;
        this.bufferConsumer = bufferConsumer;
        this.sslContext = sslContext;
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return this.bufferSupplier;
    }

    public Consumer<ByteBuffer> getBufferConsumer() {
        return this.bufferConsumer;
    }

    public final long getLastReadTime() {
        return readtime;
    }

    public final long getLastWriteTime() {
        return writetime;
    }

    public final int increEventing() {
        return eventing.incrementAndGet();
    }

    public final int decreEventing() {
        return eventing.decrementAndGet();
    }

    @Override
    public abstract boolean isOpen();

    public abstract boolean isTCP();

    public abstract boolean shutdownInput();

    public abstract boolean shutdownOutput();

    public abstract <T> boolean setOption(SocketOption<T> name, T value);

    public abstract Set<SocketOption<?>> supportedOptions();

    public abstract SocketAddress getRemoteAddress();

    public abstract SocketAddress getLocalAddress();

    public abstract int getReadTimeoutSeconds();

    public abstract int getWriteTimeoutSeconds();

    public abstract void setReadTimeoutSeconds(int readTimeoutSeconds);

    public abstract void setWriteTimeoutSeconds(int writeTimeoutSeconds);

    @Override
    public abstract int read(ByteBuffer dst) throws IOException;

    public abstract void read(CompletionHandler<Integer, ByteBuffer> handler);


    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    public abstract <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler);

    public final <A> void write(ByteBuffer[] srcs, A attachment, CompletionHandler<Integer, ? super A> handler) {
        write(srcs, 0, srcs.length, attachment, handler);
    }

    public abstract <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler);

    public void setReadBuffer(Buffer buffer) {
        if (this.readBuffer != null) throw new RuntimeException("repeat AsyncConnection.setReadBuffer");
        this.readBuffer = (ByteBuffer) buffer;
    }

    public ByteBuffer pollReadBuffer() {
        ByteBuffer rs = this.readBuffer;
        if (rs != null) {
            this.readBuffer = null;
            return rs;
        }
        return bufferSupplier.get();
    }

    public void offerBuffer(Buffer buffer) {
        if (buffer == null) return;
        bufferConsumer.accept((ByteBuffer) buffer);
    }

    public void offerBuffer(Buffer... buffers) {
        if (buffers == null) return;
        for (Buffer buffer : buffers) {
            bufferConsumer.accept((ByteBuffer) buffer);
        }
    }

    public ByteBuffer pollWriteBuffer() {
        return bufferSupplier.get();
    }

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
        if (beforeCloseListener != null) {
            try {
                beforeCloseListener.accept(this);
            } catch (Exception io) {
            }
        }
        if (this.readBuffer != null) {
            bufferConsumer.accept(this.readBuffer);
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
     * @param bufferPool          ByteBuffer对象池
     * @param address             连接点子
     * @param group               连接AsynchronousChannelGroup
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousChannelGroup group,
        final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return createTCP(bufferPool, group, null, address, readTimeoutSeconds, writeTimeoutSeconds);
    }

    /**
     * 创建TCP协议客户端连接
     *
     * @param bufferPool          ByteBuffer对象池
     * @param address             连接点子
     * @param sslContext          SSLContext
     * @param group               连接AsynchronousChannelGroup
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousChannelGroup group, final SSLContext sslContext,
        final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return createTCP(bufferPool, bufferPool, group, sslContext, address, readTimeoutSeconds, writeTimeoutSeconds);
    }

    /**
     * 创建TCP协议客户端连接
     *
     * @param bufferSupplier      ByteBuffer生产器
     * @param bufferConsumer      ByteBuffer回收器
     * @param address             连接点子
     * @param sslContext          SSLContext
     * @param group               连接AsynchronousChannelGroup
     * @param readTimeoutSeconds  读取超时秒数
     * @param writeTimeoutSeconds 写入超时秒数
     *
     * @return 连接CompletableFuture
     */
    public static CompletableFuture<AsyncConnection> createTCP(final Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer, final AsynchronousChannelGroup group, final SSLContext sslContext,
        final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        final CompletableFuture<AsyncConnection> future = new CompletableFuture<>();
        try {
            final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(group);
            try {
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            } catch (IOException e) {
            }
            channel.connect(address, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    future.complete(new TcpAioAsyncConnection(bufferSupplier, bufferConsumer, channel, sslContext, address, readTimeoutSeconds, writeTimeoutSeconds, null, null));
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

//    public static AsyncConnection create(final Socket socket) {
//        return create(socket, null, 0, 0);
//    }
//    public static AsyncConnection create(final Socket socket, final SocketAddress addr0, final int readTimeoutSecond0, final int writeTimeoutSecond0) {
//        return new TcpBioAsyncConnection(socket, addr0, readTimeoutSecond0, writeTimeoutSecond0, null, null);
//    }
//
//    public static AsyncConnection create(final Socket socket, final SocketAddress addr0, final int readTimeoutSecond0,
//        final int writeTimeoutSecond0, final AtomicLong livingCounter, final AtomicLong closedCounter) {
//        return new TcpBioAsyncConnection(socket, addr0, readTimeoutSecond0, writeTimeoutSecond0, livingCounter, closedCounter);
//    }
//
//    public static AsyncConnection create(final SocketChannel ch, SocketAddress addr, final Selector selector,
//        final int readTimeoutSeconds0, final int writeTimeoutSeconds0) {
//        return new TcpNioAsyncConnection(ch, addr, selector, readTimeoutSeconds0, writeTimeoutSeconds0, null, null);
//    }
//
//    public static AsyncConnection create(final SocketChannel ch, final SocketAddress addr0, final Selector selector, final Context context) {
//        return new TcpNioAsyncConnection(ch, addr0, selector, context.readTimeoutSeconds, context.writeTimeoutSeconds, null, null);
//    }
//
//    public static AsyncConnection create(final SocketChannel ch, SocketAddress addr, final Selector selector,
//        final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
//        final AtomicLong livingCounter, final AtomicLong closedCounter) {
//        return new TcpNioAsyncConnection(ch, addr, selector, readTimeoutSeconds0, writeTimeoutSeconds0, livingCounter, closedCounter);
//    }
    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final DatagramChannel ch,
        SocketAddress addr, final boolean client0,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0) {
        return new UdpBioAsyncConnection(bufferPool, bufferPool, ch, null, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, null, null);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final DatagramChannel ch,
        SocketAddress addr, final boolean client0,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new UdpBioAsyncConnection(bufferPool, bufferPool, ch, null, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final DatagramChannel ch, SSLContext sslContext,
        SocketAddress addr, final boolean client0,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0) {
        return new UdpBioAsyncConnection(bufferPool, bufferPool, ch, sslContext, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, null, null);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final DatagramChannel ch, SSLContext sslContext,
        SocketAddress addr, final boolean client0,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new UdpBioAsyncConnection(bufferPool, bufferPool, ch, sslContext, addr, client0, readTimeoutSeconds0, writeTimeoutSeconds0, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousSocketChannel ch) {
        return create(bufferPool, ch, null, 0, 0);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousSocketChannel ch,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return new TcpAioAsyncConnection(bufferPool, bufferPool, ch, null, addr0, readTimeoutSeconds, writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousSocketChannel ch, SSLContext sslContext,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return new TcpAioAsyncConnection(bufferPool, bufferPool, ch, sslContext, addr0, readTimeoutSeconds, writeTimeoutSeconds, null, null);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousSocketChannel ch,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpAioAsyncConnection(bufferPool, bufferPool, ch, null, addr0, readTimeoutSeconds, writeTimeoutSeconds, livingCounter, closedCounter);
    }

    public static AsyncConnection create(final ObjectPool<ByteBuffer> bufferPool, final AsynchronousSocketChannel ch, SSLContext sslContext,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        return new TcpAioAsyncConnection(bufferPool, bufferPool, ch, sslContext, addr0, readTimeoutSeconds, writeTimeoutSeconds, livingCounter, closedCounter);
    }

}
