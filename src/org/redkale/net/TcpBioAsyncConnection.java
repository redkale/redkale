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

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TcpBioAsyncConnection extends AsyncConnection {

    static final Set<SocketOption<?>> defaultOptions = defaultOptions();

    private static Set<SocketOption<?>> defaultOptions() {
        HashSet<SocketOption<?>> set = new HashSet<>(5);
        set.add(StandardSocketOptions.SO_SNDBUF);
        set.add(StandardSocketOptions.SO_RCVBUF);
        set.add(StandardSocketOptions.SO_KEEPALIVE);
        set.add(StandardSocketOptions.SO_REUSEADDR);
        set.add(StandardSocketOptions.TCP_NODELAY);
        return Collections.unmodifiableSet(set);
    }

    private int readTimeoutSeconds;

    private int writeTimeoutSeconds;

    private final Socket socket;

    private final ReadableByteChannel readChannel;

    private final WritableByteChannel writeChannel;

    private final SocketAddress remoteAddress;

    public TcpBioAsyncConnection(final Socket socket, final SocketAddress addr0, final int readTimeoutSeconds0, final int writeTimeoutSeconds0,
        final AtomicLong livingCounter, final AtomicLong closedCounter) {
        this.socket = socket;
        ReadableByteChannel rc = null;
        WritableByteChannel wc = null;
        try {
            socket.setSoTimeout(Math.max(readTimeoutSeconds0, writeTimeoutSeconds0));
            rc = Channels.newChannel(socket.getInputStream());
            wc = Channels.newChannel(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.readChannel = rc;
        this.writeChannel = wc;
        this.readTimeoutSeconds = readTimeoutSeconds0;
        this.writeTimeoutSeconds = writeTimeoutSeconds0;
        SocketAddress addr = addr0;
        if (addr == null) {
            try {
                addr = socket.getRemoteSocketAddress();
            } catch (Exception e) {
                //do nothing
            }
        }
        this.remoteAddress = addr;
        this.livingCounter = livingCounter;
        this.closedCounter = closedCounter;
    }

    @Override
    public boolean isTCP() {
        return true;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    @Override
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    @Override
    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
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
    public boolean shutdownInput() {
        try {
            this.socket.shutdownInput();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean shutdownOutput() {
        try {
            this.socket.shutdownOutput();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public <T> boolean setOption(SocketOption<T> name, T value) {
        try {
            if (StandardSocketOptions.SO_REUSEADDR == name) {
                this.socket.setReuseAddress((Boolean) value);
                return true;
            }
            if (StandardSocketOptions.SO_KEEPALIVE == name) {
                this.socket.setKeepAlive((Boolean) value);
                return true;
            }
            if (StandardSocketOptions.TCP_NODELAY == name) {
                this.socket.setTcpNoDelay((Boolean) value);
                return true;
            }
            if (StandardSocketOptions.SO_RCVBUF == name) {
                this.socket.setReceiveBufferSize((Integer) value);
                return true;
            }
            if (StandardSocketOptions.SO_SNDBUF == name) {
                this.socket.setSendBufferSize((Integer) value);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return defaultOptions;
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = 0;
            for (int i = offset; i < offset + length; i++) {
                rs += writeChannel.write(srcs[i]);
            }
            this.writetime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (IOException e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = readChannel.read(dst);
            this.readtime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (IOException e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        read(dst, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        try {
            int rs = readChannel.read(dst);
            this.readtime = System.currentTimeMillis();
            return CompletableFuture.completedFuture(rs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        try {
            int rs = writeChannel.write(src);
            this.writetime = System.currentTimeMillis();
            if (handler != null) handler.completed(rs, attachment);
        } catch (IOException e) {
            if (handler != null) handler.failed(e, attachment);
        }
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        try {
            int rs = writeChannel.write(src);
            this.writetime = System.currentTimeMillis();
            return CompletableFuture.completedFuture(rs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.socket.close();
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }
}
