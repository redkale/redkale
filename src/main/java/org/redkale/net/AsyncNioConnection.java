/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.redkale.util.ByteBufferWriter;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 */
abstract class AsyncNioConnection extends AsyncConnection {

    protected static final int MAX_INVOKER_ONSTACK = Integer.getInteger("redkale.net.invoker.max.onstack", 8);

    final AsyncIOThread connectThread;

    protected SocketAddress remoteAddress;

    //-------------------------------- 连操作 --------------------------------------
    protected Object connectAttachment;

    protected CompletionHandler<Void, Object> connectCompletionHandler;

    protected boolean connectPending;

    protected SelectionKey connectKey;

    //-------------------------------- 读操作 --------------------------------------
    protected final AsyncNioCompletionHandler<ByteBuffer> readTimeoutCompletionHandler = new AsyncNioCompletionHandler<>(true, this);

    protected int readTimeoutSeconds;

    int currReadInvoker;

    protected ByteBuffer readByteBuffer;

    protected CompletionHandler<Integer, ByteBuffer> readCompletionHandler;

    protected boolean readPending;

    protected SelectionKey readKey;

    //-------------------------------- 写操作 --------------------------------------
    protected final AsyncNioCompletionHandler<Object> writeTimeoutCompletionHandler = new AsyncNioCompletionHandler<>(false, this);

    protected int writeTimeoutSeconds;

    int currWriteInvoker;

    protected byte[] writeByteTuple1Array;

    protected int writeByteTuple1Offset;

    protected int writeByteTuple1Length;

    protected byte[] writeByteTuple2Array;

    protected int writeByteTuple2Offset;

    protected int writeByteTuple2Length;

    protected Consumer writeByteTuple2Callback;

    protected Object writeByteTuple2Attachment;

    //写操作, 二选一，要么writeByteBuffer有值，要么writeByteBuffers、writeOffset、writeLength有值
    protected ByteBuffer writeByteBuffer;

    protected ByteBuffer[] writeByteBuffers;

    protected int writeOffset;

    protected int writeLength;

    protected int writeTotal;

    protected Object writeAttachment;

    protected CompletionHandler<Integer, Object> writeCompletionHandler;

    protected boolean writePending;

    protected SelectionKey writeKey;

    public AsyncNioConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioReadThread, AsyncIOThread ioWriteThread, AsyncIOThread connectThread,
        final int bufferCapacity, SSLBuilder sslBuilder, SSLContext sslContext, LongAdder livingCounter, LongAdder closedCounter) {
        super(client, ioGroup, ioReadThread, ioWriteThread, bufferCapacity, sslBuilder, sslContext, livingCounter, closedCounter);
        this.connectThread = connectThread;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
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
    protected void startHandshake(final Consumer<Throwable> callback) {
        ioReadThread.register(t -> super.startHandshake(callback));
    }

    @Override
    protected void startRead(CompletionHandler<Integer, ByteBuffer> handler) {
        currReadInvoker = MAX_INVOKER_ONSTACK;
        read(handler);
    }

    @Override
    public void readImpl(CompletionHandler<Integer, ByteBuffer> handler) {
        Objects.requireNonNull(handler);
        if (!this.isConnected()) {
            handler.failed(new NotYetConnectedException(), null);
            return;
        }
        if (this.readPending) {
            handler.failed(new ReadPendingException(), null);
            return;
        }
        this.readPending = true;
        if (this.readTimeoutSeconds > 0) {
            AsyncNioCompletionHandler newhandler = this.readTimeoutCompletionHandler;
            newhandler.handler(handler, this.readByteBuffer);   // new AsyncNioCompletionHandler(handler, this.readByteBuffer);
            this.readCompletionHandler = newhandler;
            newhandler.timeoutFuture = ioGroup.scheduleTimeout(newhandler, this.readTimeoutSeconds, TimeUnit.SECONDS);
        } else {
            this.readCompletionHandler = handler;
        }
        if (client) {
            doRead(this.ioReadThread.inCurrThread());
        } else {
            doRead(this.ioReadThread.inCurrThread() || currReadInvoker < MAX_INVOKER_ONSTACK); //同一线程中Selector.wakeup无效
        }
    }

    @Override
    public void write(byte[] headerContent, int headerOffset, int headerLength, byte[] bodyContent, int bodyOffset, int bodyLength, Consumer bodyCallback, Object bodyAttachment, CompletionHandler<Integer, Void> handler) {
        if (sslEngine != null) {
            super.write(headerContent, headerOffset, headerLength, bodyContent, bodyOffset, bodyLength, bodyCallback, bodyAttachment, handler);
            return;
        }
        Objects.requireNonNull(headerContent);
        Objects.requireNonNull(handler);
        if (!this.isConnected()) {
            handler.failed(new NotYetConnectedException(), null);
            return;
        }
        if (this.writePending) {
            handler.failed(new WritePendingException(), null);
            return;
        }
        this.writePending = true;
        this.writeByteTuple1Array = headerContent;
        this.writeByteTuple1Offset = headerOffset;
        this.writeByteTuple1Length = headerLength;
        this.writeByteTuple2Array = bodyContent;
        this.writeByteTuple2Offset = bodyOffset;
        this.writeByteTuple2Length = bodyLength;
        this.writeByteTuple2Callback = bodyCallback;
        this.writeByteTuple2Attachment = bodyAttachment;
        this.writeAttachment = null;
        if (this.writeTimeoutSeconds > 0) {
            AsyncNioCompletionHandler newhandler = this.writeTimeoutCompletionHandler;
            newhandler.handler(handler, null);   // new AsyncNioCompletionHandler(handler, null);
            this.writeCompletionHandler = newhandler;
            newhandler.timeoutFuture = ioGroup.scheduleTimeout(newhandler, this.writeTimeoutSeconds, TimeUnit.SECONDS);
        } else {
            AsyncNioCompletionHandler newhandler = this.writeTimeoutCompletionHandler;
            newhandler.handler(handler, null);   // new AsyncNioCompletionHandler(handler, null);
            this.writeCompletionHandler = newhandler;
        }
        doWrite(true); //如果不是true，则bodyCallback的执行可能会切换线程
    }

    @Override
    public <A> void writeImpl(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        Objects.requireNonNull(src);
        Objects.requireNonNull(handler);
        if (!this.isConnected()) {
            handler.failed(new NotYetConnectedException(), attachment);
            return;
        }
        if (this.writePending) {
            handler.failed(new WritePendingException(), attachment);
            return;
        }
        this.writePending = true;
        this.writeByteBuffer = src;
        this.writeAttachment = attachment;
        if (this.writeTimeoutSeconds > 0) {
            AsyncNioCompletionHandler newhandler = this.writeTimeoutCompletionHandler;
            newhandler.handler(handler, attachment);   // new AsyncNioCompletionHandler(handler, attachment);
            this.writeCompletionHandler = newhandler;
            newhandler.timeoutFuture = ioGroup.scheduleTimeout(newhandler, this.writeTimeoutSeconds, TimeUnit.SECONDS);
        } else {
            this.writeCompletionHandler = (CompletionHandler) handler;
        }
        doWrite(true); // || !client || currWriteInvoker < MAX_INVOKER_ONSTACK // !client && ioThread.workExecutor == null
    }

    @Override
    public <A> void writeImpl(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        Objects.requireNonNull(srcs);
        Objects.requireNonNull(handler);
        if (!this.isConnected()) {
            handler.failed(new NotYetConnectedException(), attachment);
            return;
        }
        if (this.writePending) {
            handler.failed(new WritePendingException(), attachment);
            return;
        }
        this.writePending = true;
        this.writeByteBuffers = srcs;
        this.writeOffset = offset;
        this.writeLength = length;
        this.writeAttachment = attachment;
        if (this.writeTimeoutSeconds > 0) {
            AsyncNioCompletionHandler newhandler = this.writeTimeoutCompletionHandler;
            newhandler.handler(handler, attachment);   // new AsyncNioCompletionHandler(handler, attachment);
            this.writeCompletionHandler = newhandler;
            newhandler.timeoutFuture = ioGroup.scheduleTimeout(newhandler, this.writeTimeoutSeconds, TimeUnit.SECONDS);
        } else {
            this.writeCompletionHandler = (CompletionHandler) handler;
        }
        doWrite(true); // || !client || currWriteInvoker < MAX_INVOKER_ONSTACK // !client && ioThread.workExecutor == null
    }

    public void doRead(boolean direct) {
        try {
            this.readtime = System.currentTimeMillis();
            int readCount = 0;
            if (direct) {
                currReadInvoker++;
                if (this.readByteBuffer == null) {
                    this.readByteBuffer = sslEngine == null ? pollReadBuffer() : pollReadSSLBuffer();
                    if (this.readTimeoutSeconds > 0) {
                        this.readTimeoutCompletionHandler.attachment(this.readByteBuffer);
                    }
                }
                readCount = implRead(readByteBuffer);
            }

            if (readCount != 0) {
                handleRead(readCount, null);
            } else if (readKey == null) {
                ioReadThread.register(selector -> {
                    try {
                        readKey = implRegister(selector, SelectionKey.OP_READ);
                        readKey.attach(this);
                    } catch (ClosedChannelException e) {
                        handleRead(0, e);
                    }
                });
            } else {
                ((AsyncIOGroup) ioGroup).interestOpsOr(ioReadThread, readKey, SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            handleRead(0, e);
        }
    }

    public void doWrite(boolean direct) {
        try {
            this.writetime = System.currentTimeMillis();
            final boolean invokeDirect = direct;
            int totalCount = 0;
            boolean hasRemain = true;
            boolean writeOver = true;
            if (invokeDirect) {
                currWriteInvoker++;
            }
            while (invokeDirect && hasRemain) { //必须要将buffer写完为止
                if (writeByteTuple1Array != null) {
                    final ByteBuffer buffer = pollWriteBuffer();
                    if (buffer.remaining() >= writeByteTuple1Length + writeByteTuple2Length) {
                        buffer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            buffer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                            if (writeByteTuple2Callback != null) {
                                writeByteTuple2Callback.accept(writeByteTuple2Attachment);
                            }
                        }
                        buffer.flip();
                        writeByteBuffer = buffer;
                        writeByteTuple1Array = null;
                        writeByteTuple1Offset = 0;
                        writeByteTuple1Length = 0;
                        writeByteTuple2Array = null;
                        writeByteTuple2Offset = 0;
                        writeByteTuple2Length = 0;
                        writeByteTuple2Callback = null;
                        writeByteTuple2Attachment = null;
                    } else {
                        ByteBufferWriter writer = ByteBufferWriter.create(getWriteBufferSupplier(), buffer);
                        writer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            writer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                            if (writeByteTuple2Callback != null) {
                                writeByteTuple2Callback.accept(writeByteTuple2Attachment);
                            }
                        }
                        final ByteBuffer[] buffers = writer.toBuffers();
                        writeByteBuffers = buffers;
                        writeOffset = 0;
                        writeLength = buffers.length;
                        writeByteTuple1Array = null;
                        writeByteTuple1Offset = 0;
                        writeByteTuple1Length = 0;
                        writeByteTuple2Array = null;
                        writeByteTuple2Offset = 0;
                        writeByteTuple2Length = 0;
                        writeByteTuple2Callback = null;
                        writeByteTuple2Attachment = null;
                    }
                    if (this.writeCompletionHandler == this.writeTimeoutCompletionHandler) {
                        if (writeByteBuffer == null) {
                            this.writeTimeoutCompletionHandler.buffers(writeByteBuffers);
                        } else {
                            this.writeTimeoutCompletionHandler.buffer(writeByteBuffer);
                        }
                    }
                }
                int writeCount;
                if (writeByteBuffer != null) {
                    writeCount = implWrite(writeByteBuffer);
                    hasRemain = writeByteBuffer.hasRemaining();
                } else {
                    writeCount = implWrite(writeByteBuffers, writeOffset, writeLength);
                    boolean remain = false;
                    for (int i = writeByteBuffers.length - 1; i >= writeOffset; i--) {
                        if (writeByteBuffers[i].hasRemaining()) {
                            remain = true;
                            break;
                        }
                    }
                    hasRemain = remain;
                }
                if (writeCount == 0) {
                    if (hasRemain) {
                        writeOver = false;
                        writeTotal = totalCount;
                    }
                    break;
                } else if (writeCount < 0) {
                    if (totalCount == 0) {
                        totalCount = writeCount;
                    }
                    break;
                } else {
                    totalCount += writeCount;
                }
                if (!hasRemain) {
                    break;
                }
            }

            if (writeOver && (totalCount != 0 || !hasRemain)) {
                handleWrite(writeTotal + totalCount, null);
            } else if (writeKey == null) {
                ioWriteThread.register(selector -> {
                    try {
                        writeKey = implRegister(selector, SelectionKey.OP_WRITE);
                        writeKey.attach(this);
                    } catch (ClosedChannelException e) {
                        handleWrite(0, e);
                    }
                });
            } else {
                ((AsyncIOGroup) ioGroup).interestOpsOr(ioWriteThread, writeKey, SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            handleWrite(0, e);
        }
    }

    protected void handleConnect(Throwable t) {
        if (connectKey != null) {
            connectKey.cancel();
            connectKey = null;
        }
        CompletionHandler handler = this.connectCompletionHandler;
        Object attach = this.connectAttachment;

        this.connectCompletionHandler = null;
        this.connectAttachment = null;
        this.connectPending = false;//必须放最后

        if (handler != null) {
            if (!client || inCurrWriteThread()) {  //client模式下必须保证read、write在ioThread内运行
                if (t == null) {
                    handler.completed(null, attach);
                } else {
                    handler.failed(t, attach);
                }
            } else {
                ioWriteThread.execute(() -> {
                    if (t == null) {
                        handler.completed(null, attach);
                    } else {
                        handler.failed(t, attach);
                    }
                });
            }
        }
    }

    protected void handleRead(final int totalCount, Throwable t) {
        CompletionHandler<Integer, ByteBuffer> handler = this.readCompletionHandler;
        ByteBuffer attach = this.readByteBuffer;
        //清空读参数
        this.readCompletionHandler = null;
        this.readByteBuffer = null;
        this.readPending = false; //必须放最后

        if (handler == null) {
            if (t == null) {
                protocolCodec.completed(totalCount, attach);
            } else {
                protocolCodec.failed(t, attach);
            }
        } else {
            if (t == null) {
                handler.completed(totalCount, attach);
            } else {
                handler.failed(t, attach);
            }
        }
    }

    protected void handleWrite(final int totalCount, Throwable t) {
        CompletionHandler<Integer, Object> handler = this.writeCompletionHandler;
        Object attach = this.writeAttachment;
        //清空写参数
        this.writeCompletionHandler = null;
        this.writeAttachment = null;
        this.writeByteBuffer = null;
        this.writeByteBuffers = null;
        this.writeOffset = 0;
        this.writeLength = 0;
        this.writeTotal = 0;
        this.writePending = false; //必须放最后

        if (t == null) {
            handler.completed(totalCount, attach);
        } else {
            handler.failed(t, attach);
        }
    }

    @Deprecated //@since 2.5.0
    protected abstract ReadableByteChannel readableByteChannel();

    @Deprecated //@since 2.5.0
    protected abstract WritableByteChannel writableByteChannel();

    protected InputStream newInputStream() {
        final ReadableByteChannel reader = readableByteChannel();
        return new InputStream() {

            ByteBuffer bb;

            int count;

            @Override
            public synchronized int read() throws IOException {
                if (bb == null || !bb.hasRemaining()) {
                    int r = readBuffer();
                    if (r < 1) {
                        return -1;
                    }
                }
                return bb.get() & 0xff;
            }

            @Override
            public synchronized int read(byte b[], int off, int len) throws IOException {
                if (b == null) {
                    throw new NullPointerException();
                } else if (off < 0 || len < 0 || len > b.length - off) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }
                if (bb == null || !bb.hasRemaining()) {
                    int r = readBuffer();
                    if (r < 1) {
                        return -1;
                    }
                }
                int size = Math.min(b.length, Math.min(len, bb.remaining()));
                bb.get(b, off, size);
                return size;
            }

            @Override
            public void close() throws IOException {
                if (bb != null) {
                    offerReadBuffer(bb);
                    bb = null;
                }
                reader.close();
            }

            @Override
            public int available() throws IOException {
                if (bb == null || !bb.hasRemaining()) {
                    return 0;
                }
                return bb.remaining();
            }

            private int readBuffer() throws IOException {
                if (bb == null) {
                    bb = pollReadBuffer();
                } else {
                    bb.clear();
                }
                try {
                    int size = reader.read(bb);
                    bb.flip();
                    return size;
                } catch (IOException ioe) {
                    throw ioe;
                } catch (Exception e) {
                    throw new IOException(e);
                }

            }

        };
    }

    protected abstract SelectionKey implRegister(Selector sel, int ops) throws ClosedChannelException;

    protected abstract int implRead(ByteBuffer dst) throws IOException;

    protected abstract int implWrite(ByteBuffer src) throws IOException;

    protected abstract int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException;

    public abstract boolean isConnected();

    public abstract void doConnect();
}
