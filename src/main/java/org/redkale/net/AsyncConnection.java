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
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.*;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class AsyncConnection implements Channel, AutoCloseable {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    // SSL
    protected SSLEngine sslEngine;

    protected volatile long readTime;

    protected volatile long writeTime;

    protected volatile boolean connectPending;

    protected volatile boolean readPending;

    protected volatile boolean writePending;

    // 用于存储绑定在Connection上的对象集合
    private Map<String, Object> attributes;

    // 用于存储绑定在Connection上的对象， 同attributes， 只绑定单个对象时尽量使用subobject而非attributes
    private Object subobject;

    protected final AsyncIOGroup ioGroup;

    protected final boolean clientMode;

    protected final int bufferCapacity;

    private final ReentrantLock writeLock = new ReentrantLock();

    protected AsyncIOThread ioReadThread;

    protected AsyncIOThread ioWriteThread;

    private Supplier<ByteBuffer> readBufferSupplier;

    private Consumer<ByteBuffer> readBufferConsumer;

    private Supplier<ByteBuffer> writeBufferSupplier;

    private Consumer<ByteBuffer> writeBufferConsumer;

    private ByteBufferWriter pipelineWriter;

    private PipelineDataNode pipelineDataNode;

    private ByteBuffer readBuffer;

    private ByteBuffer readSSLHalfBuffer;

    // 在线数
    private LongAdder livingCounter;

    // 关闭数
    private LongAdder closedCounter;

    private Consumer<AsyncConnection> beforeCloseListener;

    // 用于服务端的Socket, 等同于一直存在的readCompletionHandler
    ProtocolCodec protocolCodec;

    protected AsyncConnection(
            boolean clientMode,
            AsyncIOGroup ioGroup,
            AsyncIOThread ioReadThread,
            AsyncIOThread ioWriteThread,
            int bufferCapacity,
            SSLBuilder sslBuilder,
            SSLContext sslContext) {
        Objects.requireNonNull(ioGroup);
        Objects.requireNonNull(ioReadThread);
        Objects.requireNonNull(ioWriteThread);
        this.clientMode = clientMode;
        this.ioGroup = ioGroup;
        this.ioReadThread = ioReadThread;
        this.ioWriteThread = ioWriteThread;
        this.bufferCapacity = bufferCapacity;
        this.readBufferSupplier = ioReadThread.getBufferSupplier();
        this.readBufferConsumer = ioReadThread.getBufferConsumer();
        this.writeBufferSupplier = ioWriteThread.getBufferSupplier();
        this.writeBufferConsumer = ioWriteThread.getBufferConsumer();
        this.livingCounter = ioGroup.connLivingCounter;
        this.closedCounter = ioGroup.connClosedCounter;
        if (clientMode) { // client模式下无SSLBuilder
            if (sslContext != null) {
                if (sslBuilder != null) {
                    this.sslEngine = sslBuilder.createSSLEngine(sslContext, clientMode);
                } else {
                    this.sslEngine = sslContext.createSSLEngine();
                }
            }
        } else {
            if (sslBuilder != null && sslContext != null) {
                this.sslEngine = sslBuilder.createSSLEngine(sslContext, clientMode);
            }
        }
    }

    void updateReadIOThread(AsyncIOThread ioReadThread) {
        Objects.requireNonNull(ioReadThread);
        this.ioReadThread = ioReadThread;
        this.readBufferSupplier = ioReadThread.getBufferSupplier();
        this.readBufferConsumer = ioReadThread.getBufferConsumer();
    }

    void updateWriteIOThread(AsyncIOThread ioWriteThread) {
        Objects.requireNonNull(ioWriteThread);
        this.ioWriteThread = ioWriteThread;
        this.writeBufferSupplier = ioWriteThread.getBufferSupplier();
        this.writeBufferConsumer = ioWriteThread.getBufferConsumer();
    }

    public Supplier<ByteBuffer> getReadBufferSupplier() {
        return this.readBufferSupplier;
    }

    public Consumer<ByteBuffer> getReadBufferConsumer() {
        return this.readBufferConsumer;
    }

    public Supplier<ByteBuffer> getWriteBufferSupplier() {
        return this.writeBufferSupplier;
    }

    public Consumer<ByteBuffer> getWriteBufferConsumer() {
        return this.writeBufferConsumer;
    }

    public final long getLastReadTime() {
        return readTime;
    }

    public final long getLastWriteTime() {
        return writeTime;
    }

    public final boolean ssl() {
        return sslEngine != null;
    }

    public final void executeRead(Runnable command) {
        ioReadThread.execute(command);
    }

    public final void executeRead(Runnable... commands) {
        ioReadThread.execute(commands);
    }

    public final void executeRead(Collection<Runnable> commands) {
        ioReadThread.execute(commands);
    }

    public final void executeWrite(Runnable command) {
        ioWriteThread.execute(command);
    }

    public final void executeWrite(Runnable... commands) {
        ioWriteThread.execute(commands);
    }

    public final void executeWrite(Collection<Runnable> commands) {
        ioWriteThread.execute(commands);
    }

    public final boolean inCurrReadThread() {
        return ioReadThread.inCurrThread();
    }

    public final boolean inCurrWriteThread() {
        return ioWriteThread.inCurrThread();
    }

    public final AsyncIOThread getReadIOThread() {
        return ioReadThread;
    }

    public final AsyncIOThread getWriteIOThread() {
        return ioWriteThread;
    }

    public final void lockWrite() {
        writeLock.lock();
    }

    public final void unlockWrite() {
        writeLock.unlock();
    }

    public abstract boolean isTCP();

    public abstract boolean shutdownInput();

    public abstract boolean shutdownOutput();

    public abstract <T> boolean setOption(SocketOption<T> name, T value);

    public abstract Set<SocketOption<?>> supportedOptions();

    public abstract SocketAddress getRemoteAddress();

    public abstract SocketAddress getLocalAddress();

    protected abstract void readRegisterImpl(CompletionHandler<Integer, ByteBuffer> handler);

    protected abstract void readImpl(CompletionHandler<Integer, ByteBuffer> handler);

    /**
     * src写完才会回调
     *
     * @see org.redkale.net.AsyncNioConnection#writeImpl(java.nio.ByteBuffer, java.lang.Object, java.nio.channels.CompletionHandler)
     * @param <A> A
     * @param src ByteBuffer
     * @param attachment A
     * @param handler CompletionHandler
     */
    protected abstract <A> void writeImpl(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler);

    /**
     *  srcs写完才会回调
     *
     * @see org.redkale.net.AsyncNioConnection#writeImpl(java.nio.ByteBuffer[], int, int, java.lang.Object, java.nio.channels.CompletionHandler)
     * @param <A> A
     * @param srcs ByteBuffer[]
     * @param offset offset
     * @param length length
     * @param attachment A
     * @param handler  CompletionHandler
     */
    protected abstract <A> void writeImpl(
            ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler);

    /**
     * src写完才会回调
     *
     * @see org.redkale.net.AsyncNioConnection#writeImpl(java.nio.ByteBuffer, java.util.function.Consumer, java.lang.Object, java.nio.channels.CompletionHandler)
     * @param <A> A
     * @param src ByteBuffer
     * @param consumer Consumer
     * @param attachment A
     * @param handler CompletionHandler
     */
    protected abstract <A> void writeImpl(
            ByteBuffer src, Consumer<ByteBuffer> consumer, A attachment, CompletionHandler<Integer, ? super A> handler);

    /**
     *  srcs写完才会回调
     *
     * @see org.redkale.net.AsyncNioConnection#writeImpl(java.nio.ByteBuffer[], int, int, java.util.function.Consumer, java.lang.Object, java.nio.channels.CompletionHandler)
     * @param <A> A
     * @param srcs ByteBuffer[]
     * @param offset offset
     * @param length length
     * @param consumer Consumer
     * @param attachment A
     * @param handler  CompletionHandler
     */
    protected abstract <A> void writeImpl(
            ByteBuffer[] srcs,
            int offset,
            int length,
            Consumer<ByteBuffer> consumer,
            A attachment,
            CompletionHandler<Integer, ? super A> handler);

    protected void startRead(CompletionHandler<Integer, ByteBuffer> handler) {
        read(handler);
    }

    public final void startReadInIOThread(CompletionHandler<Integer, ByteBuffer> handler) {
        if (inCurrReadThread()) {
            startRead(handler);
        } else {
            executeRead(() -> startRead(handler));
        }
    }

    public final void readRegister(CompletionHandler<Integer, ByteBuffer> handler) {
        if (sslEngine == null) {
            readRegisterImpl(handler);
        } else {
            sslReadRegisterImpl(false, handler);
        }
    }

    public final void read(CompletionHandler<Integer, ByteBuffer> handler) {
        if (sslEngine == null) {
            readImpl(handler);
        } else {
            sslReadImpl(false, handler);
        }
    }

    public final void readInIOThread(CompletionHandler<Integer, ByteBuffer> handler) {
        if (inCurrReadThread()) {
            read(handler);
        } else {
            executeRead(() -> read(handler));
        }
    }

    /**
     * src写完才会回调
     *
     * @see #lockWrite()
     * @see #unlockWrite()
     * @param array 内容
     * @param handler  回调函数
     */
    public final void write(ByteTuple array, CompletionHandler<Integer, Void> handler) {
        write(array.content(), array.offset(), array.length(), null, handler);
    }

    /**
     * src写完才会回调
     *
     * @see #lockWrite()
     * @see #unlockWrite()
     * @param buffer 内容
     * @param handler 回调函数
     */
    public final void write(ByteBuffer buffer, CompletionHandler<Integer, Void> handler) {
        write(buffer, null, handler);
    }

    private <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (sslEngine == null) {
            writeImpl(src, (Consumer) null, attachment, handler);
        } else {
            try {
                int remain = src.remaining();
                sslWriteImpl(false, src, t -> {
                    if (t == null) {
                        handler.completed(remain - src.remaining(), attachment);
                    } else {
                        handler.failed(t, attachment);
                    }
                });
            } catch (SSLException e) {
                handler.failed(e, attachment);
            }
        }
    }

    private <A> void write(
            ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (sslEngine == null) {
            writeImpl(srcs, offset, length, (Consumer) null, attachment, handler);
        } else {
            try {
                int remain = ByteBufferReader.remaining(srcs, offset, length);
                sslWriteImpl(false, srcs, offset, length, t -> {
                    if (t == null) {
                        handler.completed(remain - ByteBufferReader.remaining(srcs, offset, length), attachment);
                    } else {
                        handler.failed(t, attachment);
                    }
                });
            } catch (SSLException e) {
                handler.failed(e, attachment);
            }
        }
    }

    private void write(byte[] bytes, int offset, int length, Object attachment, CompletionHandler handler) {
        final ByteBuffer buffer = sslEngine == null ? pollWriteBuffer() : pollWriteSSLBuffer();
        if (buffer.remaining() >= length) {
            buffer.put(bytes, offset, length);
            buffer.flip();
            CompletionHandler<Integer, Object> newHandler = new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    offerWriteBuffer(buffer);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    offerWriteBuffer(buffer);
                    handler.failed(exc, attachment);
                }
            };
            write(buffer, attachment, newHandler);
        } else {
            ByteBufferWriter writer =
                    ByteBufferWriter.create(sslEngine == null ? writeBufferSupplier : this::pollWriteSSLBuffer, buffer);
            writer.put(bytes, offset, length);
            final ByteBuffer[] buffers = writer.toBuffers();
            CompletionHandler<Integer, Object> newHandler = new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    offerWriteBuffers(buffers);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    offerWriteBuffers(buffers);
                    handler.failed(exc, attachment);
                }
            };
            write(buffers, 0, buffers.length, attachment, newHandler);
        }
    }

    /**
     * src写完才会回调
     *
     * @see Response#finish(boolean, byte[], int, int)
     * @param <A> 泛型
     * @param src ByteBuffer
     * @param attachment 附件
     * @param handler 回调函数
     */
    public final <A> void writeInIOThread(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (inCurrWriteThread()) {
            write(src, attachment, handler);
        } else {
            executeWrite(() -> write(src, attachment, handler));
        }
    }
    /**
     * src写完才会回调
     *
     * @see Response#finish(boolean, byte[], int, int)
     * @param bytes 内容
     * @param offset 培偏移量
     * @param length 长度
     * @param handler 回调函数
     */
    public final void writeInIOThread(byte[] bytes, int offset, int length, CompletionHandler<Integer, Void> handler) {
        if (inCurrWriteThread()) {
            write(bytes, offset, length, null, handler);
        } else {
            executeWrite(() -> write(bytes, offset, length, null, handler));
        }
    }

    public final <A> void writeInIOThread(
            ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (inCurrWriteThread()) {
            write(srcs, offset, length, attachment, handler);
        } else {
            executeWrite(() -> write(srcs, offset, length, attachment, handler));
        }
    }

    public final <A> void writeInIOThread(
            ByteBuffer[] srcs, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (inCurrWriteThread()) {
            write(srcs, 0, srcs.length, attachment, handler);
        } else {
            executeWrite(() -> write(srcs, 0, srcs.length, attachment, handler));
        }
    }

    public final void writeInIOThread(byte[] bytes, CompletionHandler<Integer, Void> handler) {
        if (inCurrWriteThread()) {
            write(bytes, 0, bytes.length, null, handler);
        } else {
            executeWrite(() -> write(bytes, 0, bytes.length, null, handler));
        }
    }

    public final void writeInIOThread(ByteTuple array, CompletionHandler<Integer, Void> handler) {
        if (inCurrWriteThread()) {
            write(array, handler);
        } else {
            executeWrite(() -> write(array, handler));
        }
    }

    public void setReadBuffer(ByteBuffer buffer) {
        if (this.readBuffer != null) {
            throw new RedkaleException("repeat AsyncConnection.setReadBuffer");
        }
        this.readBuffer = buffer;
    }

    public boolean hasPipelineData() {
        ByteBufferWriter writer = this.pipelineWriter;
        return writer != null && writer.position() > 0;
    }

    void writePipeline(CompletionHandler<Integer, Void> handler) {
        writePipeline(null, handler);
    }

    <A> void writePipeline(A attachment, CompletionHandler<Integer, ? super A> handler) {
        ByteBufferWriter writer = this.pipelineWriter;
        this.pipelineWriter = null;
        if (writer == null) {
            handler.completed(0, attachment);
        } else {
            ByteBuffer[] srcs = writer.toBuffers();
            CompletionHandler<Integer, ? super A> newHandler = new CompletionHandler<Integer, A>() {
                @Override
                public void completed(Integer result, A attachment) {
                    offerWriteBuffers(srcs);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, A attachment) {
                    offerWriteBuffers(srcs);
                    handler.failed(exc, attachment);
                }
            };
            if (srcs.length == 1) {
                write(srcs[0], attachment, newHandler);
            } else {
                write(srcs, 0, srcs.length, attachment, newHandler);
            }
        }
    }

    public final void writePipelineInIOThread(CompletionHandler<Integer, Void> handler) {
        if (inCurrWriteThread()) {
            writePipeline(handler);
        } else {
            executeWrite(() -> writePipeline(handler));
        }
    }

    public final <A> void writePipelineInIOThread(A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (inCurrWriteThread()) {
            writePipeline(attachment, handler);
        } else {
            executeWrite(() -> writePipeline(attachment, handler));
        }
    }

    // 返回pipelineCount个数数据是否全部写入完毕
    public final boolean appendPipeline(int pipelineIndex, int pipelineCount, ByteTuple array) {
        return appendPipeline(pipelineIndex, pipelineCount, array.content(), array.offset(), array.length());
    }

    // 返回pipelineCount个数数据是否全部写入完毕
    public boolean appendPipeline(int pipelineIndex, int pipelineCount, byte[] bytes, int offset, int length) {
        writeLock.lock();
        try {
            ByteBufferWriter writer = this.pipelineWriter;
            if (writer == null) {
                writer = ByteBufferWriter.create(getWriteBufferSupplier());
                this.pipelineWriter = writer;
            }
            if (this.pipelineDataNode == null && pipelineIndex == writer.getWriteBytesCounter() + 1) {
                writer.put(bytes, offset, length);
                return (pipelineIndex == pipelineCount);
            } else {
                PipelineDataNode dataNode = this.pipelineDataNode;
                if (dataNode == null) {
                    dataNode = new PipelineDataNode();
                    this.pipelineDataNode = dataNode;
                }
                if (pipelineIndex == pipelineCount) { // 此时pipelineCount为最大值
                    dataNode.pipelineCount = pipelineCount;
                }
                dataNode.put(pipelineIndex, bytes, offset, length);
                if (writer.getWriteBytesCounter() + dataNode.size == dataNode.pipelineCount) { // pipeline全部完成
                    dataNode.write(writer);
                    this.pipelineDataNode = null;
                    return true;
                }
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static class PipelineDataNode {

        public int pipelineCount;

        public int size;

        private PipelineDataItem head;

        private PipelineDataItem tail;

        public void write(ByteBufferWriter writer) {
            PipelineDataItem item = head;
            while (item != null) {
                writer.put(item.data);
                item = item.next;
            }
        }

        public void put(int pipelineIndex, byte[] bs, int offset, int length) {
            size++;
            if (tail == null) {
                head = new PipelineDataItem(pipelineIndex, bs, offset, length);
                tail = head;
            } else {
                PipelineDataItem item = new PipelineDataItem(pipelineIndex, bs, offset, length);
                if (item.index > tail.index) { // 追加到最后
                    tail.next = item;
                    tail = item;
                } else if (head.index > item.index) { // 插入前面
                    item.next = head;
                    head = item;
                } else { // 中间插队
                    PipelineDataItem l = head;
                    PipelineDataItem d = head.next;
                    while (d != null) {
                        if (d.index > item.index) {
                            l.next = item;
                            item.next = d;
                            break;
                        }
                        l = d;
                        d = d.next;
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "{\"size\":" + size + ", \"item\":" + head + "}";
        }
    }

    private static class PipelineDataItem {

        final byte[] data;

        final int index;

        public PipelineDataItem next;

        public PipelineDataItem(int index, byte[] bs, int offset, int length) {
            this.index = index;
            this.data = Arrays.copyOfRange(bs, offset, offset + length);
        }

        @Override
        public String toString() {
            return "{\"index\":" + index + (next == null ? "" : (", \"next\":" + next)) + "}";
        }
    }

    protected void setReadSSLBuffer(ByteBuffer buffer) {
        if (this.readSSLHalfBuffer != null) {
            throw new RedkaleException("repeat AsyncConnection.setReadSSLBuffer");
        }
        this.readSSLHalfBuffer = buffer;
    }

    protected ByteBuffer pollReadSSLBuffer() {
        ByteBuffer rs = this.readSSLHalfBuffer;
        if (rs != null) {
            this.readSSLHalfBuffer = null;
            return rs;
        }
        return readBufferSupplier.get();
    }

    public ByteBuffer pollReadBuffer() {
        ByteBuffer rs = this.readBuffer;
        if (rs != null) {
            this.readBuffer = null;
            return rs;
        }
        return readBufferSupplier.get();
    }

    public void offerReadBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        readBufferConsumer.accept(buffer);
    }

    public void offerReadBuffers(ByteBuffer... buffers) {
        if (buffers == null) {
            return;
        }
        Consumer<ByteBuffer> consumer = this.readBufferConsumer;
        for (ByteBuffer buffer : buffers) {
            consumer.accept(buffer);
        }
    }

    public void offerWriteBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        writeBufferConsumer.accept(buffer);
    }

    public void offerWriteBuffers(ByteBuffer... buffers) {
        if (buffers == null) {
            return;
        }
        Consumer<ByteBuffer> consumer = this.writeBufferConsumer;
        for (ByteBuffer buffer : buffers) {
            consumer.accept(buffer);
        }
    }

    public ByteBuffer pollWriteSSLBuffer() {
        return writeBufferSupplier.get();
    }

    public ByteBuffer pollWriteBuffer() {
        return writeBufferSupplier.get();
    }

    public boolean isReadPending() {
        return this.readPending;
    }

    public boolean isWritePending() {
        return this.writePending;
    }

    public void dispose() { // 同close， 只是去掉throws IOException
        try {
            this.close();
        } catch (IOException io) {
            // do nothing
        }
    }

    public AsyncConnection beforeCloseListener(Consumer<AsyncConnection> beforeCloseListener) {
        this.beforeCloseListener = beforeCloseListener;
        return this;
    }

    @Override
    public void close() throws IOException {
        if (closedCounter != null) {
            closedCounter.increment();
            closedCounter = null;
        }
        if (livingCounter != null) {
            livingCounter.decrement();
            livingCounter = null;
        }
        if (sslEngine != null) {
            try {
                sslEngine.closeInbound();
                sslEngine.closeOutbound();
            } catch (SSLException e) {
                // do nothing
            }
            sslEngine = null;
        }
        if (beforeCloseListener != null) {
            try {
                beforeCloseListener.accept(this);
            } catch (Exception io) {
                // do nothing
            }
        }
        if (this.readBuffer != null && Thread.currentThread() == this.ioReadThread) {
            Consumer<ByteBuffer> consumer = this.readBufferConsumer;
            if (consumer != null) {
                consumer.accept(this.readBuffer);
            }
        }
        if (attributes == null) {
            return;
        }
        try {
            for (Object obj : attributes.values()) {
                if (obj instanceof AutoCloseable) {
                    ((AutoCloseable) obj).close();
                }
            }
            attributes.clear();
        } catch (Exception io) {
            // do nothing
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
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) (this.attributes == null ? null : this.attributes.get(name));
    }

    public final void removeAttribute(String name) {
        if (this.attributes != null) {
            this.attributes.remove(name);
        }
    }

    public final Map<String, Object> getAttributes() {
        return this.attributes;
    }

    public final void clearAttribute() {
        if (this.attributes != null) {
            this.attributes.clear();
        }
    }

    protected void startHandshake(final Consumer<Throwable> callback) {
        if (sslEngine == null) {
            callback.accept(null);
            return;
        }
        SSLEngine engine = this.sslEngine;
        try {
            engine.beginHandshake();
            doHandshake(callback);
        } catch (Throwable t) {
            callback.accept(t);
        }
    }

    // 解密ssl网络数据， 返回null表示CLOSED
    protected ByteBuffer sslUnwrap(final boolean handshake, ByteBuffer netBuffer) throws SSLException {
        ByteBuffer appBuffer = pollReadBuffer();
        SSLEngine engine = this.sslEngine;
        HandshakeStatus hss;
        do { // status只有 CLOSED、OK、BUFFER_UNDERFLOW、BUFFER_OVERFLOW
            // BUFFER_OVERFLOW:  appBuffer可用空间不足, redkale确保appBuffer不会出现空间不足的情况
            // BUFFER_UNDERFLOW: netBuffer可用内容不足
            SSLEngineResult engineResult = engine.unwrap(netBuffer, appBuffer);
            if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED
                    && (engineResult.getHandshakeStatus() == NOT_HANDSHAKING
                            || engineResult.getHandshakeStatus() == FINISHED)) {
                offerReadBuffer(netBuffer);
                offerReadBuffer(appBuffer);
                return null;
            }
            hss = engineResult.getHandshakeStatus();
            if (hss == NEED_TASK) {
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
                hss = engine.getHandshakeStatus();
            }
        } while (hss == NEED_UNWRAP && netBuffer.hasRemaining());
        if (netBuffer.hasRemaining()) {
            netBuffer.compact();
            setReadSSLBuffer(netBuffer);
        }
        return appBuffer;
    }

    protected void sslReadImpl(final boolean handshake, final CompletionHandler<Integer, ByteBuffer> handler) {
        readImpl(createSslCompletionHandler(handshake, handler));
    }

    protected void sslReadRegisterImpl(final boolean handshake, final CompletionHandler<Integer, ByteBuffer> handler) {
        readRegisterImpl(createSslCompletionHandler(handshake, handler));
    }

    private CompletionHandler<Integer, ByteBuffer> createSslCompletionHandler(
            final boolean handshake, final CompletionHandler<Integer, ByteBuffer> handler) {
        return new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer count, ByteBuffer attachment) {
                // System.out.println(AsyncConnection.this + " 进来了读到的字节数: " + count);
                if (count < 0) {
                    handler.completed(count, attachment);
                    return;
                }
                ByteBuffer netBuffer = attachment;
                netBuffer.flip();
                try {
                    ByteBuffer appBuffer = sslUnwrap(handshake, netBuffer);
                    if (appBuffer == null) {
                        return; // CLOSED，netBuffer已被回收
                    }
                    if (AsyncConnection.this.readSSLHalfBuffer != netBuffer) {
                        offerReadBuffer(netBuffer);
                    }
                    if (AsyncConnection.this.readBuffer != null) {
                        ByteBuffer rsBuffer = AsyncConnection.this.readBuffer;
                        AsyncConnection.this.readBuffer = null;
                        appBuffer.flip();
                        if (rsBuffer.remaining() >= appBuffer.remaining()) {
                            rsBuffer.put(appBuffer);
                            offerReadBuffer(appBuffer);
                            appBuffer = rsBuffer;
                        } else {
                            while (rsBuffer.hasRemaining()) rsBuffer.put(appBuffer.get());
                            AsyncConnection.this.readBuffer = appBuffer.compact();
                            appBuffer = rsBuffer;
                        }
                    }
                    handler.completed(count, appBuffer);
                } catch (SSLException e) {
                    failed(e, attachment);
                }
            }

            @Override
            public void failed(Throwable t, ByteBuffer attachment) {
                handler.failed(t, attachment);
            }
        };
    }

    // 加密ssl内容数据
    protected ByteBuffer[] sslWrap(final boolean handshake, ByteBuffer appBuffer) throws SSLException {
        final SSLEngine engine = this.sslEngine;
        final int netSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuffer = pollWriteBuffer();
        ByteBuffer[] netBuffers = new ByteBuffer[] {netBuffer};
        SSLEngineResult engineResult = engine.wrap(appBuffer, netBuffer);
        // status只有 CLOSED、OK、BUFFER_OVERFLOW
        // BUFFER_OVERFLOW:  netBuffer可用空间不足, redkale确保netBuffer不会出现空间不足的情况
        while (appBuffer.hasRemaining() || (handshake && engine.getHandshakeStatus() == NEED_WRAP)) {
            boolean enough = true;
            if (engineResult.getHandshakeStatus() == NEED_TASK) {
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
            } else if (engineResult.getStatus() == BUFFER_OVERFLOW) { // 需要重新wrap
                enough = false;
            }
            if (enough && netBuffer.remaining() >= netSize) {
                engineResult = engine.wrap(appBuffer, netBuffer);
                if (engineResult.getStatus() != OK) {
                    netBuffer.flip();
                    netBuffer = pollWriteBuffer();
                    netBuffers = Utility.append(netBuffers, netBuffer);
                    engineResult = engine.wrap(appBuffer, netBuffer);
                }
            } else {
                netBuffer.flip();
                netBuffer = pollWriteBuffer();
                netBuffers = Utility.append(netBuffers, netBuffer);
                engineResult = engine.wrap(appBuffer, netBuffer);
            }
        }
        netBuffer.flip();
        return netBuffers;
    }

    // 加密ssl内容数据
    protected ByteBuffer[] sslWrap(final boolean handshake, ByteBuffer[] appBuffers, int offset, int length)
            throws SSLException {
        final SSLEngine engine = this.sslEngine;
        final int netSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuffer = pollWriteSSLBuffer();
        ByteBuffer[] netBuffers = new ByteBuffer[] {netBuffer};
        SSLEngineResult engineResult = engine.wrap(appBuffers, offset, length, netBuffer);
        while (ByteBufferReader.remaining(appBuffers, offset, length) > 0) {
            boolean enough = true;
            if (engineResult.getHandshakeStatus() == NEED_TASK) {
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
            } else if (engineResult.getStatus() == BUFFER_OVERFLOW) { // 需要重新wrap
                enough = false;
            }
            if (enough && netBuffer.remaining() >= netSize) {
                engineResult = engine.wrap(appBuffers, offset, length, netBuffer);
                if (engineResult.getStatus() != OK) {
                    netBuffer.flip();
                    netBuffer = pollWriteSSLBuffer();
                    netBuffers = Utility.append(netBuffers, netBuffer);
                    engineResult = engine.wrap(appBuffers, offset, length, netBuffer);
                }
            } else {
                netBuffer.flip();
                netBuffer = pollWriteSSLBuffer();
                netBuffers = Utility.append(netBuffers, netBuffer);
                engineResult = engine.wrap(appBuffers, offset, length, netBuffer);
            }
        }
        netBuffer.flip();
        return netBuffers;
    }

    protected boolean sslWriteImpl(final boolean handshake, ByteBuffer appBuffer, final Consumer<Throwable> callback)
            throws SSLException {
        ByteBuffer[] netBuffers = sslWrap(handshake, appBuffer);
        if (netBuffers.length > 0) {
            if (netBuffers.length == 1) {
                writeImpl(netBuffers[0], null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer count, Void attachment) {
                        offerWriteBuffer(netBuffers[0]);
                        callback.accept(null);
                    }

                    @Override
                    public void failed(Throwable t, Void attachment) {
                        offerWriteBuffer(netBuffers[0]);
                        callback.accept(t);
                    }
                });
            } else {
                writeImpl(netBuffers, 0, netBuffers.length, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer count, Void attachment) {
                        offerWriteBuffers(netBuffers);
                        callback.accept(null);
                    }

                    @Override
                    public void failed(Throwable t, Void attachment) {
                        offerWriteBuffers(netBuffers);
                        callback.accept(t);
                    }
                });
            }
            return true;
        } else {
            offerWriteBuffers(netBuffers);
            return false;
        }
    }

    protected boolean sslWriteImpl(
            final boolean handshake,
            ByteBuffer[] appBuffers,
            int offset,
            int length,
            final Consumer<Throwable> callback)
            throws SSLException {
        ByteBuffer[] netBuffers = sslWrap(handshake, appBuffers, offset, length);
        if (netBuffers.length > 0) {
            if (netBuffers.length == 1) {
                writeImpl(netBuffers[0], null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer count, Void attachment) {
                        offerWriteBuffer(netBuffers[0]);
                        callback.accept(null);
                    }

                    @Override
                    public void failed(Throwable t, Void attachment) {
                        offerWriteBuffer(netBuffers[0]);
                        callback.accept(t);
                    }
                });
            } else {
                writeImpl(netBuffers, 0, netBuffers.length, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer count, Void attachment) {
                        offerWriteBuffers(netBuffers);
                        callback.accept(null);
                    }

                    @Override
                    public void failed(Throwable t, Void attachment) {
                        offerWriteBuffers(netBuffers);
                        callback.accept(t);
                    }
                });
            }
            return true;
        } else {
            offerWriteBuffers(netBuffers);
            return false;
        }
    }

    private void doHandshake(final Consumer<Throwable> callback) {
        HandshakeStatus handshakeStatus;
        final SSLEngine engine = this.sslEngine;
        while ((handshakeStatus = engine.getHandshakeStatus()) != null) {
            // System.out.println(AsyncConnection.this + " handshakeStatus = " + handshakeStatus);
            switch (handshakeStatus) {
                case FINISHED:
                case NOT_HANDSHAKING:
                    // System.out.println(AsyncConnection.this + " doHandshakde完毕，开始进入读写操作-----");
                    callback.accept(null);
                    return;
                case NEED_TASK: {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    break;
                }
                case NEED_WRAP: {
                    try { //
                        boolean rs = sslWriteImpl(true, EMPTY_BUFFER, t -> {
                            if (t == null) {
                                doHandshake(callback);
                            } else {
                                callback.accept(t);
                            }
                        });
                        if (rs) {
                            return;
                        }
                    } catch (SSLException e) {
                        callback.accept(e);
                        return;
                    }
                    break;
                }
                case NEED_UNWRAP: {
                    sslReadImpl(true, new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer count, ByteBuffer attachment) {
                            if (count < 1) {
                                callback.accept(new IOException("read data error"));
                            } else {
                                offerReadBuffer(attachment);
                                doHandshake(callback);
                            }
                        }

                        @Override
                        public void failed(Throwable t, ByteBuffer attachment) {
                            callback.accept(t);
                        }
                    });
                    return;
                }
                default:
                    return;
            }
        }
    }

    @Override
    public String toString() {
        String s = super.toString();
        int pos = s.lastIndexOf('@');
        if (pos < 1) {
            return s;
        }
        int cha = pos + 10 - s.length();
        if (cha < 1) {
            return s;
        }
        for (int i = 0; i < cha; i++) s += ' ';
        return s;
    }
}
