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
import java.util.*;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.redkale.util.ByteArray;
import org.redkale.util.ByteBufferWriter;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
abstract class AsyncNioConnection extends AsyncConnection {

    protected SocketAddress remoteAddress;

    // -------------------------------- 连操作 --------------------------------------
    protected Object connectAttachment;

    protected CompletionHandler<Void, Object> connectCompletionHandler;

    protected SelectionKey connectKey;

    // -------------------------------- 读操作 --------------------------------------
    protected ByteBuffer readByteBuffer;

    protected CompletionHandler<Integer, ByteBuffer> readCompletionHandler;

    protected SelectionKey readKey;

    // -------------------------------- 写操作 --------------------------------------
    protected byte[] writeByteTuple1Array;

    protected int writeByteTuple1Offset;

    protected int writeByteTuple1Length;

    protected byte[] writeByteTuple2Array;

    protected int writeByteTuple2Offset;

    protected int writeByteTuple2Length;

    // 写操作, 二选一，要么writeByteBuffer有值，要么writeByteBuffers、writeBuffersOffset、writeBuffersLength有值
    protected ByteBuffer writeByteBuffer;

    protected ByteBuffer[] writeByteBuffers;

    protected int writeBuffersOffset;

    protected int writeBuffersLength;

    protected int writeTotal;

    protected Object writeAttachment;

    protected CompletionHandler<Integer, Object> writeCompletionHandler;

    protected SelectionKey writeKey;

    //    protected CompletionHandler<Integer, Object> writeFastHandler;
    public AsyncNioConnection(
            boolean clientMode,
            AsyncIOGroup ioGroup,
            AsyncIOThread ioReadThread,
            AsyncIOThread ioWriteThread,
            final int bufferCapacity,
            SSLBuilder sslBuilder,
            SSLContext sslContext) {
        super(clientMode, ioGroup, ioReadThread, ioWriteThread, bufferCapacity, sslBuilder, sslContext);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    //    @Override
    //    public <A> AsyncConnection fastHandler(CompletionHandler<Integer, ? super A> handler) {
    //        Objects.requireNonNull(handler);
    //        this.writeFastHandler = (CompletionHandler) handler;
    //        return this;
    //    }
    @Override
    protected void startHandshake(final Consumer<Throwable> callback) {
        ioReadThread.register(t -> super.startHandshake(callback));
    }

    @Override
    protected void startRead(CompletionHandler<Integer, ByteBuffer> handler) {
        read(handler);
    }

    @Override
    protected final void readRegisterImpl(CompletionHandler<Integer, ByteBuffer> handler) {
        Objects.requireNonNull(handler);
        if (!this.isConnected()) {
            handler.failed(new NotYetConnectedException(), null);
            return;
        }
        if (handler != readCompletionHandler) { // 如果是Codec无需重复赋值
            if (this.readPending) {
                handler.failed(new ReadPendingException(), null);
                return;
            }
            this.readPending = true;
            this.readCompletionHandler = handler;
        } else {
            this.readPending = true;
        }
        try {
            if (readKey == null) {
                ioReadThread.register(selector -> {
                    try {
                        if (readKey == null) {
                            readKey = keyFor(selector);
                        }
                        if (readKey == null) {
                            readKey = implRegister(selector, SelectionKey.OP_READ);
                            readKey.attach(this);
                        } else {
                            readKey.interestOps(readKey.interestOps() | SelectionKey.OP_READ);
                        }
                    } catch (ClosedChannelException e) {
                        handleRead(0, e);
                    }
                });
            } else {
                ioReadThread.interestOpsOr(readKey, SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            handleRead(0, e);
        }
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
        this.readCompletionHandler = handler;
        doRead(this.ioReadThread.inCurrThread());
    }

    private void writeRegister(Selector selector) {
        try {
            if (writeKey == null) {
                writeKey = keyFor(selector);
            }
            if (writeKey == null) {
                writeKey = implRegister(selector, SelectionKey.OP_WRITE);
                writeKey.attach(this);
            } else {
                writeKey.interestOps(writeKey.interestOps() | SelectionKey.OP_WRITE);
            }
        } catch (ClosedChannelException e) {
            handleWrite(0, e);
        }
    }

    @Override
    protected void fastPrepareInIOThread(Object selector) {
        ByteArray array = this.fastWriteArray;
        if (!this.writePending) {
            array.clear();
        }
        Consumer<ByteArray> func;
        while ((func = fastWriteQueue.poll()) != null) {
            func.accept(array);
        }
        this.writePending = true;
        this.writeCompletionHandler = this.fastWriteHandler;
        this.writeAttachment = null;
        this.writeByteTuple1Array = array.content();
        this.writeByteTuple1Offset = array.offset();
        this.writeByteTuple1Length = array.length();
        writeRegister((Selector) selector);
    }

    @Override
    public void write(
            byte[] headerContent,
            int headerOffset,
            int headerLength,
            byte[] bodyContent,
            int bodyOffset,
            int bodyLength,
            CompletionHandler<Integer, Void> handler) {

        if (sslEngine != null) {
            super.write(headerContent, headerOffset, headerLength, bodyContent, bodyOffset, bodyLength, handler);
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
        this.writeAttachment = null;
        CompletionHandler<Integer, Void> newHandler = new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (writeByteBuffers != null) {
                    offerWriteBuffers(writeByteBuffers);
                } else {
                    offerWriteBuffer(writeByteBuffer);
                }
                handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (writeByteBuffers != null) {
                    offerWriteBuffers(writeByteBuffers);
                } else {
                    offerWriteBuffer(writeByteBuffer);
                }
                handler.failed(exc, attachment);
            }
        };
        this.writeCompletionHandler = (CompletionHandler) newHandler;
        doWrite(); // 如果不是true，则bodyCallback的执行可能会切换线程
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
        this.writeCompletionHandler = (CompletionHandler) handler;
        doWrite();
    }

    @Override
    public <A> void writeImpl(
            ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
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
        this.writeBuffersOffset = offset;
        this.writeBuffersLength = length;
        this.writeAttachment = attachment;
        this.writeCompletionHandler = (CompletionHandler) handler;
        doWrite();
    }

    public void doRead(boolean direct) {
        try {
            this.readTime = System.currentTimeMillis();
            int readCount = 0;
            if (direct) {
                if (this.readByteBuffer == null) {
                    this.readByteBuffer = sslEngine == null ? pollReadBuffer() : pollReadSSLBuffer();
                }
                readCount = implRead(readByteBuffer);
            }

            if (readCount != 0) {
                handleRead(readCount, null);
            } else if (readKey == null) {
                ioReadThread.register(selector -> {
                    try {
                        if (readKey == null) {
                            readKey = keyFor(selector);
                        }
                        if (readKey == null) {
                            readKey = implRegister(selector, SelectionKey.OP_READ);
                            readKey.attach(this);
                        } else {
                            readKey.interestOps(readKey.interestOps() | SelectionKey.OP_READ);
                        }
                    } catch (ClosedChannelException e) {
                        handleRead(0, e);
                    }
                });
            } else {
                ioReadThread.interestOpsOr(readKey, SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            handleRead(0, e);
        }
    }

    public void doWrite() {
        try {
            this.writeTime = System.currentTimeMillis();
            int totalCount = 0;
            boolean hasRemain = true;
            boolean writeCompleted = true;
            boolean error = false;
            int batchOffset = writeBuffersOffset;
            int batchLength = writeBuffersLength;
            while (hasRemain) { // 必须要将buffer写完为止
                if (writeByteTuple1Array != null) {
                    final ByteBuffer buffer = pollWriteBuffer();
                    if (buffer.remaining() >= writeByteTuple1Length + writeByteTuple2Length) {
                        buffer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            buffer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                        }
                        this.writeByteBuffer = buffer.flip();
                        this.writeByteTuple1Array = null;
                        this.writeByteTuple1Offset = 0;
                        this.writeByteTuple1Length = 0;
                        this.writeByteTuple2Array = null;
                        this.writeByteTuple2Offset = 0;
                        this.writeByteTuple2Length = 0;
                    } else {
                        ByteBufferWriter writer = ByteBufferWriter.create(getWriteBufferSupplier(), buffer);
                        writer.put(writeByteTuple1Array, writeByteTuple1Offset, writeByteTuple1Length);
                        if (writeByteTuple2Length > 0) {
                            writer.put(writeByteTuple2Array, writeByteTuple2Offset, writeByteTuple2Length);
                        }
                        final ByteBuffer[] buffers = writer.toBuffers();
                        this.writeByteBuffers = buffers;
                        this.writeBuffersOffset = 0;
                        this.writeBuffersLength = buffers.length;
                        batchOffset = writeBuffersOffset;
                        batchLength = writeBuffersLength;
                        this.writeByteTuple1Array = null;
                        this.writeByteTuple1Offset = 0;
                        this.writeByteTuple1Length = 0;
                        this.writeByteTuple2Array = null;
                        this.writeByteTuple2Offset = 0;
                        this.writeByteTuple2Length = 0;
                    }
                    if (this.fastWriteArray != null) {
                        this.fastWriteArray.clear();
                    }
                }
                int writeCount;
                if (writeByteBuffer != null) {
                    writeCount = implWrite(writeByteBuffer);
                    hasRemain = writeByteBuffer.hasRemaining();
                } else {
                    writeCount = implWrite(writeByteBuffers, batchOffset, batchLength);
                    boolean remain = false;
                    for (int i = 0; i < batchLength; i++) {
                        if (writeByteBuffers[batchOffset + i].hasRemaining()) {
                            remain = true;
                            batchOffset += i;
                            batchLength -= i;
                            break;
                        }
                    }
                    hasRemain = remain;
                }

                if (writeCount == 0) {
                    if (hasRemain) {
                        // writeCompleted = false;
                        // writeTotal = totalCount;
                        continue; // 要全部输出完才返回
                    }
                    break;
                } else if (writeCount < 0) {
                    error = true;
                    totalCount = writeCount;
                    break;
                } else {
                    totalCount += writeCount;
                }
                if (!hasRemain) {
                    break;
                }
            }

            if (error) {
                handleWrite(totalCount, new ClosedChannelException());
            } else if (writeCompleted && (totalCount != 0 || !hasRemain)) {
                handleWrite(this.writeTotal + totalCount, null);
                //                if (fastWriteCount.get() > 0) {
                //                    doWrite();
                //                }
            } else if (writeKey == null) {
                ioWriteThread.register(selector -> {
                    try {
                        if (writeKey == null) {
                            writeKey = keyFor(selector);
                        }
                        if (writeKey == null) {
                            writeKey = implRegister(selector, SelectionKey.OP_WRITE);
                            writeKey.attach(this);
                        } else {
                            writeKey.interestOps(writeKey.interestOps() | SelectionKey.OP_WRITE);
                        }
                    } catch (ClosedChannelException e) {
                        handleWrite(0, e);
                    }
                });
            } else {
                ioWriteThread.interestOpsOr(writeKey, SelectionKey.OP_WRITE);
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
        this.connectPending = false; // 必须放最后

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
        // 清空读参数
        this.readCompletionHandler = null;
        this.readByteBuffer = null;
        this.readPending = false; // 必须放最后

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
        // 清空写参数
        this.writeCompletionHandler = null;
        this.writeAttachment = null;
        this.writeByteBuffer = null;
        this.writeByteBuffers = null;
        this.writeBuffersOffset = 0;
        this.writeBuffersLength = 0;
        this.writeTotal = 0;
        this.writePending = false; // 必须放最后

        if (t == null) {
            handler.completed(totalCount, attach);
        } else {
            handler.failed(t, attach);
        }
    }

    @Deprecated(since = "2.5.0")
    protected abstract ReadableByteChannel readableByteChannel();

    @Deprecated(since = "2.5.0")
    protected abstract WritableByteChannel writableByteChannel();

    protected InputStream newInputStream() {
        final ReadableByteChannel reader = readableByteChannel();
        return new InputStream() {

            ByteBuffer bb;

            @Override
            public int read() throws IOException {
                if (bb == null || !bb.hasRemaining()) {
                    int r = readBuffer();
                    if (r < 1) {
                        return -1;
                    }
                }
                return bb.get() & 0xff;
            }

            @Override
            public int read(byte b[], int off, int len) throws IOException {
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

    protected abstract SelectionKey keyFor(Selector sel);

    protected abstract SelectionKey implRegister(Selector sel, int ops) throws ClosedChannelException;

    protected abstract int implRead(ByteBuffer dst) throws IOException;

    protected abstract int implWrite(ByteBuffer src) throws IOException;

    protected abstract int implWrite(ByteBuffer[] srcs, int offset, int length) throws IOException;

    public abstract boolean isConnected();

    public abstract void doConnect();
}
