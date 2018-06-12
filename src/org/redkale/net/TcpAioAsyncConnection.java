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
import javax.net.ssl.SSLContext;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TcpAioAsyncConnection extends AsyncConnection {

    private final Semaphore semaphore = new Semaphore(1);

    private int readTimeoutSeconds;

    private int writeTimeoutSeconds;

    private final AsynchronousSocketChannel channel;

    private final SocketAddress remoteAddress;

    private BlockingQueue<WriteEntry> writeQueue;

    public TcpAioAsyncConnection(final AsynchronousSocketChannel ch, SSLContext sslContext,
        final SocketAddress addr0, final int readTimeoutSeconds, final int writeTimeoutSeconds,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this.channel = ch;
        this.sslContext = sslContext;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
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

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.readtime = System.currentTimeMillis();
        if (readTimeoutSeconds > 0) {
            channel.read(dst, readTimeoutSeconds, TimeUnit.SECONDS, attachment, handler);
        } else {
            channel.read(dst, attachment, handler);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.readtime = System.currentTimeMillis();
        channel.read(dst, timeout < 0 ? 0 : timeout, unit, attachment, handler);
    }

    private <A> void nextWrite(A attachment) {
        BlockingQueue<WriteEntry> queue = this.writeQueue;
        WriteEntry entry = queue == null ? null : queue.poll();
        if (entry != null) {
            try {
                if (entry.writeOneBuffer == null) {
                    write(false, entry.writeBuffers, entry.writeOffset, entry.writeLength, entry.writeAttachment, entry.writeHandler);
                } else {
                    write(false, entry.writeOneBuffer, entry.writeAttachment, entry.writeHandler);
                }
            } catch (Exception e) {
                entry.writeHandler.failed(e, entry.writeAttachment);
            }
        } else {
            semaphore.release();
        }
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        write(true, src, attachment, handler);
    }

    private <A> void write(boolean acquire, ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (acquire && !semaphore.tryAcquire()) {
            if (this.writeQueue == null) {
                synchronized (semaphore) {
                    if (this.writeQueue == null) {
                        this.writeQueue = new LinkedBlockingDeque<>();
                    }
                }
            }
            this.writeQueue.add(new WriteEntry(src, attachment, handler));
            return;
        }
        WriteOneCompletionHandler newHandler = new WriteOneCompletionHandler(src, handler);
        if (!channel.isOpen()) {
            newHandler.failed(new ClosedChannelException(), attachment);
            return;
        }
        this.writetime = System.currentTimeMillis();
        if (writeTimeoutSeconds > 0) {
            channel.write(src, writeTimeoutSeconds, TimeUnit.SECONDS, attachment, newHandler);
        } else {
            channel.write(src, attachment, newHandler);
        }
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, final CompletionHandler<Integer, ? super A> handler) {
        write(true, srcs, offset, length, attachment, handler);
    }

    private <A> void write(boolean acquire, ByteBuffer[] srcs, int offset, int length, A attachment, final CompletionHandler<Integer, ? super A> handler) {
        if (acquire && !semaphore.tryAcquire()) {
            if (this.writeQueue == null) {
                synchronized (semaphore) {
                    if (this.writeQueue == null) {
                        this.writeQueue = new LinkedBlockingDeque<>();
                    }
                }
            }
            this.writeQueue.add(new WriteEntry(srcs, offset, length, attachment, handler));
            return;
        }
        WriteMoreCompletionHandler newHandler = new WriteMoreCompletionHandler(srcs, offset, length, handler);
        if (!channel.isOpen()) {
            newHandler.failed(new ClosedChannelException(), attachment);
            return;
        }
        this.writetime = System.currentTimeMillis();
        channel.write(srcs, offset, length, writeTimeoutSeconds > 0 ? writeTimeoutSeconds : 60, TimeUnit.SECONDS, attachment, newHandler);
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
    public final Future<Integer> read(ByteBuffer dst) {
        return channel.read(dst);
    }

    @Override
    public final Future<Integer> write(ByteBuffer src) {
        return channel.write(src);
    }

    @Override
    public final void close() throws IOException {
        super.close();
        channel.close();
        BlockingQueue<WriteEntry> queue = this.writeQueue;
        if (queue == null) return;
        WriteEntry entry;
        Exception ex = null;
        while ((entry = queue.poll()) != null) {
            if (ex == null) ex = new ClosedChannelException();
            try {
                entry.writeHandler.failed(ex, entry.writeAttachment);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public final boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public final boolean isTCP() {
        return true;
    }

    private class WriteMoreCompletionHandler<A> implements CompletionHandler<Long, A> {

        private final CompletionHandler<Integer, A> writeHandler;

        private final ByteBuffer[] writeBuffers;

        private int writeOffset;

        private int writeLength;

        private int writeCount;

        public WriteMoreCompletionHandler(ByteBuffer[] buffers, int offset, int length, CompletionHandler handler) {
            this.writeBuffers = buffers;
            this.writeOffset = offset;
            this.writeLength = length;
            this.writeHandler = handler;
        }

        @Override
        public void completed(Long result, A attachment) {
            if (result >= 0) {
                writeCount += result;
                try {
                    int index = -1;
                    for (int i = writeOffset; i < (writeOffset + writeLength); i++) {
                        if (writeBuffers[i].hasRemaining()) {
                            index = i;
                            break;
                        }
                    }
                    if (index >= 0) {
                        writeOffset += index;
                        writeLength -= index;
                        channel.write(writeBuffers, writeOffset, writeLength, writeTimeoutSeconds > 0 ? writeTimeoutSeconds : 60, TimeUnit.SECONDS, attachment, this);
                        return;
                    }
                } catch (Exception e) {
                    failed(e, attachment);
                    return;
                }
                nextWrite(attachment);
                writeHandler.completed(writeCount, attachment);
            } else {
                nextWrite(attachment);
                writeHandler.completed(result.intValue(), attachment);
            }
        }

        @Override
        public void failed(Throwable exc, A attachment) {
            nextWrite(attachment);
            writeHandler.failed(exc, attachment);
        }

    }

    private class WriteOneCompletionHandler<A> implements CompletionHandler<Integer, A> {

        private final CompletionHandler writeHandler;

        private final ByteBuffer writeOneBuffer;

        public WriteOneCompletionHandler(ByteBuffer buffer, CompletionHandler handler) {
            this.writeOneBuffer = buffer;
            this.writeHandler = handler;
        }

        @Override
        public void completed(Integer result, A attachment) {
            try {
                if (writeOneBuffer.hasRemaining()) {
                    channel.write(writeOneBuffer, attachment, this);
                    return;
                }
            } catch (Exception e) {
                failed(e, attachment);
                return;
            }
            nextWrite(attachment);
            writeHandler.completed(result, attachment);
        }

        @Override
        public void failed(Throwable exc, A attachment) {
            nextWrite(attachment);
            writeHandler.failed(exc, attachment);
        }

    }

    private static class WriteEntry {

        ByteBuffer writeOneBuffer;

        ByteBuffer[] writeBuffers;

        int writingCount;

        int writeOffset;

        int writeLength;

        Object writeAttachment;

        CompletionHandler writeHandler;

        public WriteEntry(ByteBuffer writeOneBuffer, Object writeAttachment, CompletionHandler writeHandler) {
            this.writeOneBuffer = writeOneBuffer;
            this.writeAttachment = writeAttachment;
            this.writeHandler = writeHandler;
        }

        public WriteEntry(ByteBuffer[] writeBuffers, int writeOffset, int writeLength, Object writeAttachment, CompletionHandler writeHandler) {
            this.writeBuffers = writeBuffers;
            this.writeOffset = writeOffset;
            this.writeLength = writeLength;
            this.writeAttachment = writeAttachment;
            this.writeHandler = writeHandler;
        }
    }
}
