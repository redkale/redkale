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
import java.util.function.*;

/**
 * 传输客户端
 *
 * @author zhangjx
 */
public final class Transport {

    protected static final int MAX_POOL_LIMIT = 16;

    protected final String name;

    protected final int bufferPoolSize;

    protected final int bufferCapacity;

    protected final String protocol;

    protected final AsynchronousChannelGroup group;

    protected final InetSocketAddress[] remoteAddres;

    protected final ObjectPool<ByteBuffer> bufferPool;

    protected final AtomicInteger index = new AtomicInteger();

    protected final ConcurrentHashMap<SocketAddress, BlockingQueue<AsyncConnection>> connPool = new ConcurrentHashMap<>();

    public Transport(Transport transport, InetSocketAddress localAddress, Collection<Transport> transports) {
        this(transport.name, transport.protocol, null, transport.bufferPoolSize, parse(localAddress, transports));
    }

    public Transport(String name, String protocol, WatchFactory watch, int bufferPoolSize, Collection<InetSocketAddress> addresses) {
        this.name = name;
        this.protocol = protocol;
        this.bufferPoolSize = bufferPoolSize;
        this.bufferCapacity = 8192;
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
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber(Transport.class.getSimpleName() + "-" + name + "-" + protocol + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber(Transport.class.getSimpleName() + "-" + name + "-" + protocol + ".Buffer.cycleCounter");
        final int rcapacity = bufferCapacity;
        this.bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        this.remoteAddres = addresses.toArray(new InetSocketAddress[addresses.size()]);
    }

    private static Collection<InetSocketAddress> parse(InetSocketAddress addr, Collection<Transport> transports) {
        final Set<InetSocketAddress> set = new LinkedHashSet<>();
        for (Transport t : transports) {
            set.addAll(Arrays.asList(t.remoteAddres));
        }
        set.remove(addr);
        return set;
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

    public InetSocketAddress[] getRemoteAddress() {
        return remoteAddres;
    }

    @Override
    public String toString() {
        return Transport.class.getSimpleName() + "{name=" + name + ",protocol=" + protocol + ",remoteAddres=" + Arrays.toString(remoteAddres) + "}";
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public ByteBuffer pollBuffer() {
        return bufferPool.get();
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return bufferPool;
    }

    public void offerBuffer(ByteBuffer buffer) {
        bufferPool.offer(buffer);
    }

    public void offerBuffer(ByteBuffer... buffers) {
        for (ByteBuffer buffer : buffers) offerBuffer(buffer);
    }

    public boolean isTCP() {
        return "TCP".equalsIgnoreCase(protocol);
    }

    public AsyncConnection pollConnection(SocketAddress addr) {
        if (addr == null && remoteAddres.length == 1) addr = remoteAddres[0];
        final boolean rand = addr == null;
        if (rand && remoteAddres.length < 1) throw new RuntimeException("Transport (" + this.name + ") has no remoteAddress list");
        try {
            if ("TCP".equalsIgnoreCase(protocol)) {
                AsynchronousSocketChannel channel = null;
                if (rand) {   //随机取地址
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
                            channel.connect(addr).get(2, TimeUnit.SECONDS);
                            break;
                        } catch (Exception iex) {
                            iex.printStackTrace();
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
                return AsyncConnection.create(channel, addr, 3000, 3000);
            } else { // UDP
                if (rand) addr = remoteAddres[0];
                DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(true);
                channel.connect(addr);
                return AsyncConnection.create(channel, addr, true, 3000, 3000);
//                AsyncDatagramChannel channel = AsyncDatagramChannel.open(group);
//                channel.connect(addr);
//                return AsyncConnection.create(channel, addr, true, 3000, 3000);
            }
        } catch (Exception ex) {
            throw new RuntimeException("transport address = " + addr, ex);
        }
    }

    public void offerConnection(final boolean forceClose, AsyncConnection conn) {
        if (!forceClose && conn.isTCP()) {  //暂时每次都关闭
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
                        offerConnection(false, conn);
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        offerBuffer(buffer);
                        offerConnection(true, conn);
                    }
                });

            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                offerBuffer(buffer);
                offerConnection(true, conn);
            }
        });
    }

}
