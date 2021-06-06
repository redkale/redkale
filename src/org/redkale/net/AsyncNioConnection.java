/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.net.ssl.SSLContext;
import org.redkale.util.*;

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

    protected static final int MAX_INVOKER_ONSTACK = Integer.getInteger("net.invoker.max.onstack", 16);

    final AsyncIOThread ioThread;

    final AsyncIOThread connectThread;

    final AsyncIOGroup ioGroup;

    protected SocketAddress remoteAddress;

    //-------------------------------- 连操作 --------------------------------------
    protected Object connectAttachment;

    protected CompletionHandler<Void, Object> connectCompletionHandler;

    protected boolean connectPending;

    protected SelectionKey connectKey;

    //-------------------------------- 读操作 --------------------------------------
    protected final AsyncNioCompletionHandler<ByteBuffer> readTimeoutCompletionHandler = new AsyncNioCompletionHandler<>(this);

    protected int readTimeoutSeconds;

    int currReadInvoker;

    protected ByteBuffer readByteBuffer;

    protected CompletionHandler<Integer, ByteBuffer> readCompletionHandler;

    protected boolean readPending;

    protected SelectionKey readKey;

    //-------------------------------- 写操作 --------------------------------------
    protected final AsyncNioCompletionHandler<Object> writeTimeoutCompletionHandler = new AsyncNioCompletionHandler<>(this);

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

    protected Object writeAttachment;

    protected CompletionHandler<Integer, Object> writeCompletionHandler;

    protected boolean writePending;

    protected SelectionKey writeKey;

    public AsyncNioConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread,
        final int bufferCapacity, ObjectPool<ByteBuffer> bufferPool, SSLContext sslContext, AtomicLong livingCounter, AtomicLong closedCounter) {
        super(client, bufferCapacity, bufferPool, sslContext, livingCounter, closedCounter);
        this.ioGroup = ioGroup;
        this.ioThread = ioThread;
        this.connectThread = connectThread;
    }

    public AsyncNioConnection(boolean client, AsyncIOGroup ioGroup, AsyncIOThread ioThread, AsyncIOThread connectThread,
        final int bufferCapacity, Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer, SSLContext sslContext, AtomicLong livingCounter, AtomicLong closedCounter) {
        super(client, bufferCapacity, bufferSupplier, bufferConsumer, sslContext, livingCounter, closedCounter);
        this.ioGroup = ioGroup;
        this.ioThread = ioThread;
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
    protected void continueRead() {
        if (readKey == null) {
            ioThread.register(selector -> {
                try {
                    readKey = implRegister(selector, SelectionKey.OP_READ);
                    readKey.attach(this);
                } catch (ClosedChannelException e) {
                    handleRead(0, e);
                }
            });
        } else {
            ioGroup.interestOpsOr(ioThread, readKey, SelectionKey.OP_READ);
        }
    }

    @Override
    public void read(CompletionHandler<Integer, ByteBuffer> handler) {
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
        doRead(currReadInvoker < MAX_INVOKER_ONSTACK || this.ioThread.inCurrThread()); //同一线程中Selector.wakeup无效
    }

    @Override
    public void write(byte[] headerContent, int headerOffset, int headerLength, byte[] bodyContent, int bodyOffset, int bodyLength, Consumer bodyCallback, Object bodyAttachment, CompletionHandler<Integer, Void> handler) {
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
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
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
        doWrite(true || !client || currWriteInvoker < MAX_INVOKER_ONSTACK); // this.ioThread.inCurrThread() // !client && ioThread.workExecutor == null
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
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
        doWrite(true || !client || currWriteInvoker < MAX_INVOKER_ONSTACK); // this.ioThread.inCurrThread() // !client && ioThread.workExecutor == null
    }

    public void doRead(boolean direct) {
        try {
            this.readtime = System.currentTimeMillis();
            int readCount = 0;
            if (direct) {
                currReadInvoker++;
                if (this.readByteBuffer == null) {
                    this.readByteBuffer = pollReadBuffer();
                    if (this.readTimeoutSeconds > 0) {
                        this.readTimeoutCompletionHandler.attachment(this.readByteBuffer);
                    }
                }
                readCount = implRead(readByteBuffer);
            }

            if (readCount != 0) {
                handleRead(readCount, null);
            } else if (readKey == null) {
                ioThread.register(selector -> {
                    try {
                        readKey = implRegister(selector, SelectionKey.OP_READ);
                        readKey.attach(this);
                    } catch (ClosedChannelException e) {
                        handleRead(0, e);
                    }
                });
            } else {
                if (client || !direct) ioGroup.interestOpsOr(ioThread, readKey, SelectionKey.OP_READ);
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
            if (invokeDirect) currWriteInvoker++;
            while (invokeDirect && hasRemain) { //必须要将buffer写完为止
                if (writeByteTuple1Array != null) {
                    final ByteBuffer buffer = pollWriteBuffer();
                    if (buffer.remaining() >= writeByteTuple1Length + writeByteTuple2Length) {
                        buffer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            buffer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                            if (writeByteTuple2Callback != null) writeByteTuple2Callback.accept(writeByteTuple2Attachment);
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
                        ByteBufferWriter writer = ByteBufferWriter.create(getBufferSupplier(), buffer);
                        writer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            writer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                            if (writeByteTuple2Callback != null) writeByteTuple2Callback.accept(writeByteTuple2Attachment);
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
                if (writeCount <= 0) {
                    if (totalCount == 0) totalCount = writeCount;
                    break;
                } else {
                    totalCount += writeCount;
                }
                if (!hasRemain) break;
            }

            if (totalCount != 0 || !hasRemain) {
                handleWrite(totalCount, null);
            } else if (writeKey == null) {
                ioThread.register(selector -> {
                    try {
                        writeKey = implRegister(selector, SelectionKey.OP_WRITE);
                        writeKey.attach(this);
                    } catch (ClosedChannelException e) {
                        handleWrite(0, e);
                    }
                });
            } else {
                ioGroup.interestOpsOr(ioThread, writeKey, SelectionKey.OP_WRITE);
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
            if (t == null) {
                handler.completed(null, attach);
            } else {
                handler.failed(t, attach);
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
        this.writePending = false; //必须放最后

        if (t == null) {
            handler.completed(totalCount, attach);
        } else {
            handler.failed(t, attach);
        }
    }

    protected abstract SelectionKey implRegister(Selector sel, int ops) throws ClosedChannelException;

    protected abstract int implRead(ByteBuffer dst) throws IOException;

    protected abstract int implWrite(ByteBuffer src) throws IOException;

    protected abstract int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException;

    public abstract boolean isConnected();

    public abstract void doConnect();
}
