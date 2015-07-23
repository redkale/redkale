/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 传输客户端
 *
 * @author zhangjx
 */
public final class Transport {

    protected SocketAddress[] remoteAddres;

    protected final ObjectPool<ByteBuffer> bufferPool;

    protected final String name;

    protected final String protocol;

    private final boolean udp;

    protected final AsynchronousChannelGroup group;

    protected BlockingQueue<AsyncConnection> queue;

    public Transport(String name, String protocol, int clients, int bufferPoolSize, WatchFactory watch, SocketAddress... addresses) {
        this.name = name;
        this.protocol = protocol;
        this.udp = "UDP".equalsIgnoreCase(protocol);
        this.queue = this.udp ? null : new ArrayBlockingQueue<>(clients);
        AsynchronousChannelGroup g = null;
        try {
            final AtomicInteger counter = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 8, (Runnable r) -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("Transport-" + name + "-Thread-" + counter.incrementAndGet());
                return t;
            });
            g = AsynchronousChannelGroup.withCachedThreadPool(executor, 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.group = g;
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber(Transport.class.getSimpleName() + "_" + protocol + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber(Transport.class.getSimpleName() + "_" + protocol + ".Buffer.cycleCounter");
        int rcapacity = 8192;
        this.bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        this.remoteAddres = addresses;
    }

    public ByteBuffer pollBuffer() {
        return bufferPool.poll();
    }

    public void offerBuffer(ByteBuffer buffer) {
        bufferPool.offer(buffer);
    }

    public void offerBuffer(ByteBuffer... buffers) {
        for (ByteBuffer buffer : buffers) offerBuffer(buffer);
    }

    public boolean isUDP() {
        return udp;
    }

    public AsyncConnection pollConnection() {
        if (udp) return createConnection();
        AsyncConnection conn = queue.poll();
        return (conn != null && conn.isOpen()) ? conn : createConnection();
    }

    private AsyncConnection createConnection() {
        SocketAddress addr = remoteAddres[0];
        try {
            if (udp) {
                AsyncDatagramChannel channel = AsyncDatagramChannel.open(group);
                channel.connect(addr);
                return AsyncConnection.create(channel, addr, true, 0, 0);
            } else {
                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(group);
                channel.connect(addr).get(2, TimeUnit.SECONDS);
                return AsyncConnection.create(channel, 0, 0);
            }
        } catch (Exception ex) {
            throw new RuntimeException("transport address = " + addr, ex);
        }
    }

    public void offerConnection(AsyncConnection conn) {
        if (udp) {
            try {
                conn.close();
            } catch (IOException io) {
            }
        } else if (conn.isOpen()) {
            if (!queue.offer(conn)) {
                try {
                    conn.close();
                } catch (IOException io) {
                }
            }
        }
    }

    public <A> void async(final ByteBuffer buffer, A att, final CompletionHandler<Integer, A> handler) {
        final AsyncConnection conn = pollConnection();
        conn.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                buffer.clear();
                conn.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        if (handler != null) handler.completed(result, att);
                        offerBuffer(buffer);
                        offerConnection(conn);
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        offerBuffer(buffer);
                        offerConnection(conn);
                    }
                });

            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                offerBuffer(buffer);
                offerConnection(conn);
            }
        });
    }

    public ByteBuffer send(ByteBuffer buffer) {
        AsyncConnection conn = pollConnection();
        final int readto = conn.getReadTimeoutSecond();
        final int writeto = conn.getWriteTimeoutSecond();
        try {
            conn.write(buffer).get(writeto > 0 ? writeto : 5, TimeUnit.SECONDS);
            buffer.clear();
            conn.read(buffer).get(readto > 0 ? readto : 5, TimeUnit.SECONDS);
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            offerConnection(conn);
        }
    }

}
