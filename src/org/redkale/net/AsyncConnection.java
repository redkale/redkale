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
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.net.ssl.SSLContext;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class AsyncConnection implements AutoCloseable {

    private SSLContext sslContext;

    private Map<String, Object> attributes; //用于存储绑定在Connection上的对象集合

    private Object subobject; //用于存储绑定在Connection上的对象， 同attributes， 只绑定单个对象时尽量使用subobject而非attributes

    protected volatile long readtime;

    protected volatile long writetime;

    protected final boolean client;

    protected final int bufferCapacity;

    private final Supplier<ByteBuffer> bufferSupplier;

    private final Consumer<ByteBuffer> bufferConsumer;

    private ByteBufferWriter pipelineWriter;

    private PipelineDataNode pipelineDataNode;

    private ByteBuffer readBuffer;

    //在线数
    private AtomicLong livingCounter;

    //关闭数
    private AtomicLong closedCounter;

    private Consumer<AsyncConnection> beforeCloseListener;

    //关联的事件数， 小于1表示没有事件
    private final AtomicInteger eventing = new AtomicInteger();

    //用于服务端的Socket, 等同于一直存在的readCompletionHandler
    ProtocolCodec protocolCodec;

    protected AsyncConnection(boolean client, final int bufferCapacity, ObjectPool<ByteBuffer> bufferPool, SSLContext sslContext,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this(client, bufferCapacity, bufferPool, bufferPool, sslContext, livingCounter, closedCounter);
    }

    protected AsyncConnection(boolean client, final int bufferCapacity, Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer,
        SSLContext sslContext, final AtomicLong livingCounter, final AtomicLong closedCounter) {
        Objects.requireNonNull(bufferSupplier);
        Objects.requireNonNull(bufferConsumer);
        this.client = client;
        this.bufferCapacity = bufferCapacity;
        this.bufferSupplier = bufferSupplier;
        this.bufferConsumer = bufferConsumer;
        this.sslContext = sslContext;
        this.livingCounter = livingCounter;
        this.closedCounter = closedCounter;
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

    protected abstract void continueRead();

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

    public abstract ReadableByteChannel readableByteChannel();

    public abstract WritableByteChannel writableByteChannel();

    public abstract void read(CompletionHandler<Integer, ByteBuffer> handler);

    //src会写完才会回调
    public abstract <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler);

    //srcs会写完才会回调
    public final <A> void write(ByteBuffer[] srcs, A attachment, CompletionHandler<Integer, ? super A> handler) {
        write(srcs, 0, srcs.length, attachment, handler);
    }

    public final void write(byte[] bytes, CompletionHandler<Integer, Void> handler) {
        write(bytes, 0, bytes.length, null, 0, 0, handler);
    }

    public final void write(ByteTuple array, CompletionHandler<Integer, Void> handler) {
        write(array.content(), array.offset(), array.length(), null, 0, 0, handler);
    }

    public final void write(byte[] bytes, int offset, int length, CompletionHandler<Integer, Void> handler) {
        write(bytes, offset, length, null, 0, 0, handler);
    }

    public final void write(ByteTuple header, ByteTuple body, CompletionHandler<Integer, Void> handler) {
        write(header.content(), header.offset(), header.length(), body == null ? null : body.content(), body == null ? 0 : body.offset(), body == null ? 0 : body.length(), handler);
    }

    public void write(byte[] headerContent, int headerOffset, int headerLength, byte[] bodyContent, int bodyOffset, int bodyLength, CompletionHandler<Integer, Void> handler) {
        final ByteBuffer buffer = pollWriteBuffer();
        if (buffer.remaining() >= headerLength + bodyLength) {
            buffer.put(headerContent, headerOffset, headerLength);
            if (bodyLength > 0) buffer.put(bodyContent, bodyOffset, bodyLength);
            buffer.flip();
            CompletionHandler<Integer, Void> newhandler = new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    offerBuffer(buffer);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    offerBuffer(buffer);
                    handler.failed(exc, attachment);
                }
            };
            write(buffer, null, newhandler);
        } else {
            ByteBufferWriter writer = ByteBufferWriter.create(bufferSupplier, buffer);
            writer.put(headerContent, headerOffset, headerLength);
            if (bodyLength > 0) writer.put(bodyContent, bodyOffset, bodyLength);
            final ByteBuffer[] buffers = writer.toBuffers();
            CompletionHandler<Integer, Void> newhandler = new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    offerBuffer(buffers);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    offerBuffer(buffers);
                    handler.failed(exc, attachment);
                }
            };
            write(buffers, null, newhandler);
        }
    }

    //srcs会写完才会回调
    public abstract <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler);

    public void setReadBuffer(ByteBuffer buffer) {
        if (this.readBuffer != null) throw new RuntimeException("repeat AsyncConnection.setReadBuffer");
        this.readBuffer = buffer;
    }

    public boolean hasPipelineData() {
        ByteBufferWriter writer = this.pipelineWriter;
        return writer != null && writer.position() > 0;
    }

    public final void flushPipelineData(CompletionHandler<Integer, Void> handler) {
        flushPipelineData(null, handler);
    }

    public <A> void flushPipelineData(A attachment, CompletionHandler<Integer, ? super A> handler) {
        ByteBufferWriter writer = this.pipelineWriter;
        this.pipelineWriter = null;
        if (writer == null) {
            handler.completed(0, attachment);
        } else {
            ByteBuffer[] srcs = writer.toBuffers();
            CompletionHandler<Integer, ? super A> newhandler = new CompletionHandler<Integer, A>() {
                @Override
                public void completed(Integer result, A attachment) {
                    offerBuffer(srcs);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, A attachment) {
                    offerBuffer(srcs);
                    handler.failed(exc, attachment);
                }
            };
            if (srcs.length == 1) {
                write(srcs[0], attachment, newhandler);
            } else {
                write(srcs, attachment, newhandler);
            }
        }
    }

    //返回 是否over
    public final boolean writePipelineData(int pipelineIndex, int pipelineCount, ByteTuple array) {
        return writePipelineData(pipelineIndex, pipelineCount, array.content(), array.offset(), array.length());
    }

    //返回 是否over
    public boolean writePipelineData(int pipelineIndex, int pipelineCount, byte[] bs, int offset, int length) {
        synchronized (this) {
            ByteBufferWriter writer = this.pipelineWriter;
            if (writer == null) {
                writer = ByteBufferWriter.create(getBufferSupplier());
                this.pipelineWriter = writer;
            }
            if (this.pipelineDataNode == null && pipelineIndex == writer.getWriteBytesCounter() + 1) {
                writer.put(bs, offset, length);
                return (pipelineIndex == pipelineCount);
            } else {
                PipelineDataNode dataNode = this.pipelineDataNode;
                if (dataNode == null) {
                    dataNode = new PipelineDataNode();
                    this.pipelineDataNode = dataNode;
                }
                if (pipelineIndex == pipelineCount) { //此时pipelineCount为最大值
                    dataNode.pipelineCount = pipelineCount;
                }
                dataNode.put(pipelineIndex, bs, offset, length);
                if (writer.getWriteBytesCounter() + dataNode.itemsize == dataNode.pipelineCount) {
                    for (PipelineDataItem item : dataNode.arrayItems()) {
                        writer.put(item.data);
                    }
                    this.pipelineDataNode = null;
                    return true;
                }
                return false;
            }
        }
    }

    private static class PipelineDataNode {

        public int pipelineCount;

        public int itemsize;

        private PipelineDataItem head;

        private PipelineDataItem tail;

        public PipelineDataItem[] arrayItems() {
            PipelineDataItem[] items = new PipelineDataItem[itemsize];
            PipelineDataItem item = head;
            int i = 0;
            while (item != null) {
                items[i] = item;
                item = item.next;
                items[i].next = null;
                i++;
            }
            Arrays.sort(items);
            return items;
        }

        public void put(int pipelineIndex, byte[] bs, int offset, int length) {
            if (tail == null) {
                head = new PipelineDataItem(pipelineIndex, bs, offset, length);
                tail = head;
            } else {
                PipelineDataItem item = new PipelineDataItem(pipelineIndex, bs, offset, length);
                tail.next = item;
                tail = item;
            }
            itemsize++;
        }
    }

    private static class PipelineDataItem implements Comparable<PipelineDataItem> {

        final byte[] data;

        final int index;

        public PipelineDataItem next;

        public PipelineDataItem(int index, byte[] bs, int offset, int length) {
            this.index = index;
            this.data = Arrays.copyOfRange(bs, offset, offset + length);
        }

        @Override
        public int compareTo(PipelineDataItem o) {
            return this.index - o.index;
        }

        @Override
        public String toString() {
            return "{\"index\":" + index + "}";
        }
    }

    public ByteBuffer pollReadBuffer() {
        ByteBuffer rs = this.readBuffer;
        if (rs != null) {
            this.readBuffer = null;
            return rs;
        }
        return bufferSupplier.get();
    }

    public void offerBuffer(ByteBuffer buffer) {
        if (buffer == null) return;
        bufferConsumer.accept(buffer);
    }

    public void offerBuffer(ByteBuffer... buffers) {
        if (buffers == null) return;
        Consumer<ByteBuffer> consumer = this.bufferConsumer;
        for (ByteBuffer buffer : buffers) {
            consumer.accept(buffer);
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
            Consumer<ByteBuffer> consumer = this.bufferConsumer;
            if (consumer != null) consumer.accept(this.readBuffer);
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

}
