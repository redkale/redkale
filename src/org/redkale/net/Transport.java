/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 * 传输客户端
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Transport {

    public static final String DEFAULT_PROTOCOL = "TCP";

    protected static final int MAX_POOL_LIMIT = Runtime.getRuntime().availableProcessors() * 16;

    protected static final boolean supportTcpNoDelay;

    static {
        boolean tcpNoDelay = false;
        try {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            tcpNoDelay = channel.supportedOptions().contains(StandardSocketOptions.TCP_NODELAY);
            channel.close();
        } catch (Exception e) {
        }
        supportTcpNoDelay = tcpNoDelay;
    }

    protected final TransportFactory factory;

    protected final String name; //即<group>的name属性

    protected final String subprotocol; //即<group>的subprotocol属性

    protected final boolean tcp;

    protected final String protocol;

    protected final AsynchronousChannelGroup group;

    protected final InetSocketAddress clientAddress;

    protected TransportAddress[] transportAddres = new TransportAddress[0];

    protected final ObjectPool<ByteBuffer> bufferPool;

    //负载均衡策略
    protected final TransportStrategy strategy;

    protected final ConcurrentHashMap<SocketAddress, BlockingQueue<AsyncConnection>> connPool = new ConcurrentHashMap<>();

    protected Transport(String name, String subprotocol, TransportFactory factory, final ObjectPool<ByteBuffer> transportBufferPool,
        final AsynchronousChannelGroup transportChannelGroup, final InetSocketAddress clientAddress,
        final Collection<InetSocketAddress> addresses, final TransportStrategy strategy) {
        this(name, DEFAULT_PROTOCOL, subprotocol, factory, transportBufferPool, transportChannelGroup, clientAddress, addresses, strategy);
    }

    protected Transport(String name, String protocol, String subprotocol,
        final TransportFactory factory, final ObjectPool<ByteBuffer> transportBufferPool,
        final AsynchronousChannelGroup transportChannelGroup, final InetSocketAddress clientAddress,
        final Collection<InetSocketAddress> addresses, final TransportStrategy strategy) {
        this.name = name;
        this.subprotocol = subprotocol == null ? "" : subprotocol.trim();
        this.protocol = protocol;
        this.factory = factory;
        this.tcp = "TCP".equalsIgnoreCase(protocol);
        this.group = transportChannelGroup;
        this.bufferPool = transportBufferPool;
        this.clientAddress = clientAddress;
        this.strategy = strategy;
        updateRemoteAddresses(addresses);
    }

    public final InetSocketAddress[] updateRemoteAddresses(final Collection<InetSocketAddress> addresses) {
        TransportAddress[] oldAddresses = this.transportAddres;
        List<TransportAddress> list = new ArrayList<>();
        if (addresses != null) {
            for (InetSocketAddress addr : addresses) {
                if (clientAddress != null && clientAddress.equals(addr)) continue;
                list.add(new TransportAddress(addr));
            }
        }
        this.transportAddres = list.toArray(new TransportAddress[list.size()]);

        InetSocketAddress[] rs = new InetSocketAddress[oldAddresses.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = oldAddresses[i].getAddress();
        }
        return rs;
    }

    public final boolean addRemoteAddresses(final InetSocketAddress addr) {
        if (addr == null) return false;
        synchronized (this) {
            if (this.transportAddres == null) {
                this.transportAddres = new TransportAddress[]{new TransportAddress(addr)};
            } else {
                for (TransportAddress i : this.transportAddres) {
                    if (addr.equals(i.address)) return false;
                }
                this.transportAddres = Utility.append(transportAddres, new TransportAddress(addr));
            }
            return true;
        }
    }

    public final boolean removeRemoteAddresses(InetSocketAddress addr) {
        if (addr == null) return false;
        if (this.transportAddres == null) return false;
        synchronized (this) {
            this.transportAddres = Utility.remove(transportAddres, new TransportAddress(addr));
        }
        return true;
    }

    public String getName() {
        return name;
    }

    public String getSubprotocol() {
        return subprotocol;
    }

    public void close() {
        connPool.forEach((k, v) -> v.forEach(c -> c.dispose()));
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public TransportAddress[] getTransportAddresses() {
        return transportAddres;
    }

    public InetSocketAddress[] getRemoteAddresses() {
        InetSocketAddress[] rs = new InetSocketAddress[transportAddres.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = transportAddres[i].getAddress();
        }
        return rs;
    }

    public ConcurrentHashMap<SocketAddress, BlockingQueue<AsyncConnection>> getAsyncConnectionPool() {
        return connPool;
    }

    @Override
    public String toString() {
        return Transport.class.getSimpleName() + "{name = " + name + ", protocol = " + protocol + ", clientAddress = " + clientAddress + ", remoteAddres = " + Arrays.toString(transportAddres) + "}";
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
        return tcp;
    }

    public AsyncConnection pollConnection(SocketAddress addr) {
        if (this.strategy != null) return strategy.pollConnection(addr, this);
        if (addr == null && this.transportAddres.length == 1) addr = this.transportAddres[0].address;
        final boolean rand = addr == null;
        if (rand && this.transportAddres.length < 1) throw new RuntimeException("Transport (" + this.name + ") have no remoteAddress list");
        try {
            if (tcp) {
                AsynchronousSocketChannel channel = null;
                if (rand) {   //取地址
                    TransportAddress transportAddr;
                    boolean tryed = false;
                    for (int i = 0; i < transportAddres.length; i++) {
                        transportAddr = transportAddres[i];
                        addr = transportAddr.address;
                        if (!transportAddr.enable) continue;
                        final BlockingQueue<AsyncConnection> queue = transportAddr.conns;
                        if (!queue.isEmpty()) {
                            AsyncConnection conn;
                            while ((conn = queue.poll()) != null) {
                                if (conn.isOpen()) return conn;
                            }
                        }
                        tryed = true;
                        if (channel == null) {
                            channel = AsynchronousSocketChannel.open(group);
                            if (supportTcpNoDelay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        }
                        try {
                            channel.connect(addr).get(2, TimeUnit.SECONDS);
                            transportAddr.enable = true;
                            break;
                        } catch (Exception iex) {
                            transportAddr.enable = false;
                            channel = null;
                        }
                    }
                    if (channel == null && !tryed) {
                        for (int i = 0; i < transportAddres.length; i++) {
                            transportAddr = transportAddres[i];
                            addr = transportAddr.address;
                            if (channel == null) {
                                channel = AsynchronousSocketChannel.open(group);
                                if (supportTcpNoDelay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                            }
                            try {
                                channel.connect(addr).get(2, TimeUnit.SECONDS);
                                transportAddr.enable = true;
                                break;
                            } catch (Exception iex) {
                                transportAddr.enable = false;
                                channel = null;
                            }
                        }
                    }
                } else {
                    channel = AsynchronousSocketChannel.open(group);
                    if (supportTcpNoDelay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    channel.connect(addr).get(2, TimeUnit.SECONDS);
                }
                if (channel == null) return null;
                return AsyncConnection.create(channel, addr, 3000, 3000);
            } else { // UDP
                if (rand) addr = this.transportAddres[0].address;
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
        if (!forceClose && conn.isTCP()) {
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

    public static class TransportAddress {

        protected InetSocketAddress address;

        protected volatile boolean enable;

        protected final BlockingQueue<AsyncConnection> conns = new ArrayBlockingQueue<>(MAX_POOL_LIMIT);

        public TransportAddress(InetSocketAddress address) {
            this.address = address;
            this.enable = true;
        }

        @java.beans.ConstructorProperties({"address", "enable"})
        public TransportAddress(InetSocketAddress address, boolean enable) {
            this.address = address;
            this.enable = enable;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public boolean isEnable() {
            return enable;
        }

        @ConvertColumn(ignore = true)
        public BlockingQueue<AsyncConnection> getConns() {
            return conns;
        }

        @Override
        public int hashCode() {
            return this.address.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final TransportAddress other = (TransportAddress) obj;
            return this.address.equals(other.address);
        }

        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
