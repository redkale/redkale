/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 传输客户端
 *
 * @author zhangjx
 */
public final class Transport {

    protected static final int MAX_POOL_LIMIT = 16;

    protected final String name;

    protected final String protocol;

    protected final AsynchronousChannelGroup group;

    protected final InetSocketAddress[] remoteAddres;

    protected final ObjectPool<ByteBuffer> bufferPool;

    protected final AtomicInteger index = new AtomicInteger();

    protected final ConcurrentHashMap<SocketAddress, BlockingQueue<AsyncConnection>> connPool = new ConcurrentHashMap<>();

    public Transport(String name, String protocol, WatchFactory watch, int bufferPoolSize, Collection<InetSocketAddress> addresses) {
        this.name = name;
        this.protocol = protocol;
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
        this.remoteAddres = addresses.toArray(new InetSocketAddress[addresses.size()]);
    }

    public void close() {
        connPool.forEach((k, v) -> v.forEach(c -> c.dispose()));
    }

    public boolean match(Collection<InetSocketAddress> addrs) {
        if (addrs == null) return false;
        if (addrs.size() != this.remoteAddres.length) return false;
        for (InetSocketAddress addr : this.remoteAddres) {
            if (!addrs.contains(addr)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return Transport.class.getSimpleName() + "{name=" + name + ",protocol=" + protocol + ",remoteAddres=" + Arrays.toString(remoteAddres) + "}";
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

    public AsyncConnection pollConnection(SocketAddress addr) {
        final boolean rand = addr == null;
        try {
            if ("TCP".equalsIgnoreCase(protocol)) {
                AsynchronousSocketChannel channel = null;
                if (rand) {
                    int p = 0;
                    for (int i = index.get(); i < remoteAddres.length; i++) {
                        p = i;
                        addr = remoteAddres[i];
                        BlockingQueue<AsyncConnection> queue = connPool.get(addr);
                        if (queue != null && !queue.isEmpty()) {
                            AsyncConnection conn;
                            while ((conn = queue.poll()) != null) {
                                if (conn.isOpen()) return conn;
                            }
                        }
                        if (channel == null) channel = AsynchronousSocketChannel.open(group);
                        try {
                            channel.connect(addr).get(1, TimeUnit.SECONDS);
                            break;
                        } catch (Exception iex) {
                            if (i == remoteAddres.length - 1) {
                                p = 0;
                                channel = null;
                            }
                        }
                    }
                    index.set(p);
                } else {
                    channel = AsynchronousSocketChannel.open(group);
                    channel.connect(addr).get(2, TimeUnit.SECONDS);
                }
                if (channel == null) return null;
                return AsyncConnection.create(channel, addr, 0, 0);
            } else {
                AsyncDatagramChannel channel = AsyncDatagramChannel.open(group);
                channel.connect(addr);
                return AsyncConnection.create(channel, addr, true, 0, 0);
            }
        } catch (Exception ex) {
            throw new RuntimeException("transport address = " + addr, ex);
        }
    }

    public void offerConnection(AsyncConnection conn) {
        if (conn.isTCP()) {
            if (conn.isOpen()) {
                BlockingQueue<AsyncConnection> queue = connPool.get(conn.getRemoteAddress());
                if (queue == null) {
                    queue = new ArrayBlockingQueue<>(MAX_POOL_LIMIT);
                    connPool.put(conn.getRemoteAddress(), queue);
                }
                if (!queue.offer(conn)) conn.dispose();
            }
        } else {
            conn.dispose();
        }
    }

    public <A> void async(SocketAddress addr, final ByteBuffer buffer, A att, final CompletionHandler<Integer, A> handler) {
        final AsyncConnection conn = pollConnection(addr);
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

    public ByteBuffer send(SocketAddress addr, ByteBuffer buffer) {
        AsyncConnection conn = pollConnection(addr);
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
