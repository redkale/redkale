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
public class TcpNioAsyncConnection extends AsyncConnection {

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    protected final Selector selector;

    protected SelectionKey key;

    protected final SocketChannel channel;

    protected final SocketAddress remoteAddress;

    ByteBuffer readBuffer;

    Object readAttachment;

    CompletionHandler readHandler;

    ByteBuffer writeOneBuffer;

    ByteBuffer[] writeBuffers;

    int writingCount;

    int writeOffset;

    int writeLength;

    Object writeAttachment;

    CompletionHandler writeHandler;

    public TcpNioAsyncConnection(final SocketChannel ch, SocketAddress addr0,
        final Selector selector,
        final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this.channel = ch;
        this.selector = selector;
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

    CompletionHandler removeReadHandler() {
        CompletionHandler handler = this.readHandler;
        this.readHandler = null;
        return handler;
    }

    ByteBuffer removeReadBuffer() {
        ByteBuffer buffer = this.readBuffer;
        this.readBuffer = null;
        return buffer;
    }

    Object removeReadAttachment() {
        Object attach = this.readAttachment;
        this.readAttachment = null;
        return attach;
    }

    void completeRead(int rs) {
        Object attach = this.readAttachment;
        CompletionHandler handler = this.readHandler;
        this.readBuffer = null;
        this.readAttachment = null;
        this.readHandler = null;
        handler.completed(rs, attach);
    }

    void faileRead(Throwable t) {
        Object attach = this.readAttachment;
        CompletionHandler handler = this.readHandler;
        this.readBuffer = null;
        this.readAttachment = null;
        this.readHandler = null;
        handler.failed(t, attach);
    }

    CompletionHandler removeWriteHandler() {
        CompletionHandler handler = this.writeHandler;
        this.writeHandler = null;
        return handler;
    }

    ByteBuffer removeWriteOneBuffer() {
        ByteBuffer buffer = this.writeOneBuffer;
        this.writeOneBuffer = null;
        return buffer;
    }

    ByteBuffer[] removeWriteBuffers() {
        ByteBuffer[] buffers = this.writeBuffers;
        this.writeBuffers = null;
        return buffers;
    }

    int removeWritingCount() {
        int rs = this.writingCount;
        this.writingCount = 0;
        return rs;
    }

    int removeWriteOffset() {
        int rs = this.writeOffset;
        this.writeOffset = 0;
        return rs;
    }

    int removeWriteLength() {
        int rs = this.writeLength;
        this.writeLength = 0;
        return rs;
    }

    Object removeWriteAttachment() {
        Object attach = this.writeAttachment;
        this.writeAttachment = null;
        return attach;
    }

    void completeWrite(int rs) {
        Object attach = this.writeAttachment;
        CompletionHandler handler = this.writeHandler;
        this.writeOneBuffer = null;
        this.writeBuffers = null;
        this.writeOffset = 0;
        this.writeLength = 0;
        this.writeAttachment = null;
        this.writeHandler = null;
        handler.completed(rs, attach);
    }

    void faileWrite(Throwable t) {
        Object attach = this.writeAttachment;
        CompletionHandler handler = this.writeHandler;
        this.writeOneBuffer = null;
        this.writeBuffers = null;
        this.writeOffset = 0;
        this.writeLength = 0;
        this.writeAttachment = null;
        this.writeHandler = null;
        handler.failed(t, attach);
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (this.readHandler != null) throw new RuntimeException("pending read");
        try {
            this.readBuffer = dst;
            this.readAttachment = attachment;
            this.readHandler = handler;
            if (key == null) {
                key = channel.register(selector, SelectionKey.OP_READ);
                key.attach(this);
            } else {
                key.interestOps(SelectionKey.OP_READ);
            }
            selector.wakeup();
        } catch (Exception e) {
            faileRead(e);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        read(dst, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture future = new CompletableFuture();
        read(dst, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                future.complete(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return future;
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (this.writeHandler != null) throw new RuntimeException("pending write");
        try {
            this.writeBuffers = srcs;
            this.writeOffset = offset;
            this.writeLength = length;
            this.writingCount = 0;
            this.writeAttachment = attachment;
            this.writeHandler = handler;
            if (key == null) {
                key = channel.register(selector, SelectionKey.OP_WRITE);
                key.attach(this);
            } else {
                key.interestOps(SelectionKey.OP_WRITE);
            }
            selector.wakeup();
        } catch (Exception e) {
            faileWrite(e);
        }
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (this.writeHandler != null) throw new RuntimeException("pending write");
        try {
            this.writeOneBuffer = src;
            this.writingCount = 0;
            this.writeAttachment = attachment;
            this.writeHandler = handler;
            if (key == null) {
                key = channel.register(selector, SelectionKey.OP_WRITE);
                key.attach(this);
            } else {
                key.interestOps(SelectionKey.OP_WRITE);
            }
            selector.wakeup();
        } catch (Exception e) {
            faileWrite(e);
        }
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        CompletableFuture future = new CompletableFuture();
        write(src, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                future.complete(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });
        return future;
    }

    @Override
    public final void close() throws IOException {
        super.close();
        channel.close();
        key.cancel();
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
