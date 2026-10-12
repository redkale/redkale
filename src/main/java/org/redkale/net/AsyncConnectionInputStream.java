package org.redkale.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 将连接的异步读取适配为阻塞输入流，仅支持单个读取者独占连接的读取操作。
 *
 * <p>回调交付写模式的明文缓冲区；复制数据后立即回收缓冲区，避免关闭与迟到回调之间的所有权竞争。 阻塞读取必须在工作线程中执行，不能在 IO 线程中等待。关闭输入流同时关闭连接。
 *
 * @author zhangjx
 * @since 2.9.0
 */
class AsyncConnectionInputStream extends InputStream {

    private static final byte[] EMPTY = new byte[0];

    private final AsyncConnection connection;

    private final long timeoutNanos;

    private final Object monitor = new Object();

    private final ReentrantLock readLock = new ReentrantLock();

    // 以下状态均由 monitor 保护。
    private byte[] bytes = EMPTY;

    private int position;

    private boolean pending;

    private boolean eof;

    private boolean closed;

    private IOException failure;

    private final CompletionHandler<Integer, ByteBuffer> handler = new CompletionHandler<>() {
        @Override
        public void completed(Integer count, ByteBuffer buffer) {
            byte[] data = EMPTY;
            try {
                if (count >= 0 && buffer != null) {
                    buffer.flip();
                    data = new byte[buffer.remaining()];
                    buffer.get(data);
                }
            } finally {
                connection.offerReadBuffer(buffer);
            }
            synchronized (monitor) {
                pending = false;
                if (!closed) {
                    bytes = data;
                    position = 0;
                    eof = count < 0;
                }
                monitor.notifyAll();
            }
        }

        @Override
        public void failed(Throwable error, ByteBuffer buffer) {
            connection.offerReadBuffer(buffer);
            synchronized (monitor) {
                pending = false;
                if (!closed) {
                    failure = error instanceof IOException ioe ? ioe : new IOException(error);
                }
                monitor.notifyAll();
            }
        }
    };

    /**
     * 创建不限制读取等待时间的输入流。
     *
     * @param connection 独占读取的连接
     */
    public AsyncConnectionInputStream(AsyncConnection connection) {
        this(connection, 0);
    }

    /**
     * 创建输入流；超时包含直到获得下一批明文数据之前的所有空读取。
     *
     * @param connection 独占读取的连接
     * @param timeoutMillis 读取超时毫秒数，0 表示不限制
     */
    public AsyncConnectionInputStream(AsyncConnection connection, long timeoutMillis) {
        this.connection = Objects.requireNonNull(connection);
        if (!connection.isTCP()) {
            throw new IllegalArgumentException("Input stream requires a TCP connection");
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Negative read timeout");
        }
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    @Override
    public int read() throws IOException {
        checkIOThread();
        lockRead();
        try {
            synchronized (monitor) {
                return awaitData() ? bytes[position++] & 0xff : -1;
            }
        } catch (InterruptedIOException e) {
            closeAfterFailure(e);
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (length == 0) {
            return 0;
        }
        checkIOThread();
        lockRead();
        try {
            synchronized (monitor) {
                if (!awaitData()) {
                    return -1;
                }
                int count = Math.min(length, bytes.length - position);
                System.arraycopy(bytes, position, target, offset, count);
                position += count;
                return count;
            }
        } catch (InterruptedIOException e) {
            closeAfterFailure(e);
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int available() throws IOException {
        synchronized (monitor) {
            checkOpen();
            return bytes.length - position;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            bytes = EMPTY;
            position = 0;
            monitor.notifyAll();
        }
        connection.close();
    }

    // 调用方持有 monitor；wait 会释放 monitor，允许回调和 close 唤醒读取者。
    private boolean awaitData() throws IOException {
        final long start = System.nanoTime();
        while (true) {
            checkOpen();
            if (failure != null) {
                throw failure;
            }
            if (position < bytes.length) {
                return true;
            }
            bytes = EMPTY;
            position = 0;
            if (eof) {
                return false;
            }
            if (!connection.isOpen()) {
                throw new IOException("Connection is closed");
            }
            long waitNanos = TimeUnit.SECONDS.toNanos(1);
            if (timeoutNanos > 0) {
                long remaining = timeoutNanos - (System.nanoTime() - start);
                if (remaining <= 0) {
                    throw new SocketTimeoutException("Connection input stream read timed out");
                }
                waitNanos = Math.min(waitNanos, remaining);
            }
            if (!pending) {
                pending = true;
                try {
                    connection.executeRead(() -> {
                        synchronized (monitor) {
                            if (closed) {
                                return;
                            }
                        }
                        try {
                            connection.readInIOThread(handler);
                        } catch (RuntimeException e) {
                            handler.failed(e, null);
                        }
                    });
                } catch (RuntimeException e) {
                    pending = false;
                    failure = new IOException("Cannot start connection read", e);
                    throw failure;
                }
                // 某些连接实现可能同步完成回调。
                if (!pending) {
                    continue;
                }
            }
            try {
                // 连接从外部关闭时未必回调，以有限等待检查连接状态。
                TimeUnit.NANOSECONDS.timedWait(monitor, waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                InterruptedIOException error = new InterruptedIOException("Connection input stream read interrupted");
                error.initCause(e);
                throw error;
            }
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Input stream is closed");
        }
    }

    private void checkIOThread() throws IOException {
        if (AsyncIOThread.currentAsyncIOThread() != null) {
            throw new IOException("Blocking input stream reads are not allowed on IO threads");
        }
    }

    private void lockRead() throws InterruptedIOException {
        try {
            readLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException error = new InterruptedIOException("Connection input stream read interrupted");
            error.initCause(e);
            closeAfterFailure(error);
            throw error;
        }
    }

    private void closeAfterFailure(IOException error) {
        try {
            close();
        } catch (IOException closeError) {
            error.addSuppressed(closeError);
        }
    }
}
