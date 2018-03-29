/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
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

    protected static final int MAX_POOL_LIMIT = Runtime.getRuntime().availableProcessors() * 8;

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

    //不可能为null
    protected TransportAddress[] transportAddrs = new TransportAddress[0];

    protected final ObjectPool<ByteBuffer> bufferPool;

    protected final SSLContext sslContext;

    //负载均衡策略
    protected final TransportStrategy strategy;

    protected Transport(String name, String subprotocol, TransportFactory factory, final ObjectPool<ByteBuffer> transportBufferPool,
        final AsynchronousChannelGroup transportChannelGroup, final SSLContext sslContext, final InetSocketAddress clientAddress,
        final Collection<InetSocketAddress> addresses, final TransportStrategy strategy) {
        this(name, DEFAULT_PROTOCOL, subprotocol, factory, transportBufferPool, transportChannelGroup, sslContext, clientAddress, addresses, strategy);
    }

    protected Transport(String name, String protocol, String subprotocol,
        final TransportFactory factory, final ObjectPool<ByteBuffer> transportBufferPool,
        final AsynchronousChannelGroup transportChannelGroup, final SSLContext sslContext, final InetSocketAddress clientAddress,
        final Collection<InetSocketAddress> addresses, final TransportStrategy strategy) {
        this.name = name;
        this.subprotocol = subprotocol == null ? "" : subprotocol.trim();
        this.protocol = protocol;
        this.factory = factory;
        factory.transportReferences.add(new WeakReference<>(this));
        this.tcp = "TCP".equalsIgnoreCase(protocol);
        this.group = transportChannelGroup;
        this.sslContext = sslContext;
        this.bufferPool = transportBufferPool;
        this.clientAddress = clientAddress;
        this.strategy = strategy;
        updateRemoteAddresses(addresses);
    }

    public final InetSocketAddress[] updateRemoteAddresses(final Collection<InetSocketAddress> addresses) {
        final TransportAddress[] oldAddresses = this.transportAddrs;
        synchronized (this) {
            List<TransportAddress> list = new ArrayList<>();
            if (addresses != null) {
                for (InetSocketAddress addr : addresses) {
                    if (clientAddress != null && clientAddress.equals(addr)) continue;
                    boolean hasold = false;
                    for (TransportAddress oldAddr : oldAddresses) {
                        if (oldAddr.getAddress().equals(addr)) {
                            list.add(oldAddr);
                            hasold = true;
                            break;
                        }
                    }
                    if (hasold) continue;
                    list.add(new TransportAddress(addr));
                }
            }
            this.transportAddrs = list.toArray(new TransportAddress[list.size()]);
        }
        InetSocketAddress[] rs = new InetSocketAddress[oldAddresses.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = oldAddresses[i].getAddress();
        }
        return rs;
    }

    public final boolean addRemoteAddresses(final InetSocketAddress addr) {
        if (addr == null) return false;
        if (clientAddress != null && clientAddress.equals(addr)) return false;
        synchronized (this) {
            if (this.transportAddrs.length == 0) {
                this.transportAddrs = new TransportAddress[]{new TransportAddress(addr)};
            } else {
                for (TransportAddress i : this.transportAddrs) {
                    if (addr.equals(i.address)) return false;
                }
                this.transportAddrs = Utility.append(transportAddrs, new TransportAddress(addr));
            }
            return true;
        }
    }

    public final boolean removeRemoteAddresses(InetSocketAddress addr) {
        if (addr == null) return false;
        synchronized (this) {
            this.transportAddrs = Utility.remove(transportAddrs, new TransportAddress(addr));
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
        TransportAddress[] taddrs = this.transportAddrs;
        if (taddrs == null) return;
        for (TransportAddress taddr : taddrs) {
            if (taddr != null) taddr.dispose();
        }
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public TransportAddress[] getTransportAddresses() {
        return transportAddrs;
    }

    public TransportAddress findTransportAddress(SocketAddress addr) {
        for (TransportAddress taddr : this.transportAddrs) {
            if (taddr.address.equals(addr)) return taddr;
        }
        return null;
    }

    public InetSocketAddress[] getRemoteAddresses() {
        InetSocketAddress[] rs = new InetSocketAddress[transportAddrs.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = transportAddrs[i].getAddress();
        }
        return rs;
    }

    @Override
    public String toString() {
        return Transport.class.getSimpleName() + "{name = " + name + ", protocol = " + protocol + ", clientAddress = " + clientAddress + ", remoteAddres = " + Arrays.toString(transportAddrs) + "}";
    }

    public ByteBuffer pollBuffer() {
        return bufferPool.get();
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return bufferPool;
    }

    public void offerBuffer(ByteBuffer buffer) {
        bufferPool.accept(buffer);
    }

    public void offerBuffer(ByteBuffer... buffers) {
        for (ByteBuffer buffer : buffers) offerBuffer(buffer);
    }

    public AsynchronousChannelGroup getTransportChannelGroup() {
        return group;
    }

    public boolean isTCP() {
        return tcp;
    }

    public CompletableFuture<AsyncConnection> pollConnection(SocketAddress addr0) {
        if (this.strategy != null) return strategy.pollConnection(addr0, this);
        if (addr0 == null && this.transportAddrs.length == 1) addr0 = this.transportAddrs[0].address;
        final SocketAddress addr = addr0;
        final boolean rand = addr == null; //是否随机取地址
        if (rand && this.transportAddrs.length < 1) throw new RuntimeException("Transport (" + this.name + ") have no remoteAddress list");
        try {
            if (!tcp) { // UDP
                SocketAddress udpaddr = rand ? this.transportAddrs[0].address : addr;
                DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(true);
                channel.connect(udpaddr);
                return CompletableFuture.completedFuture(AsyncConnection.create(channel, udpaddr, true, factory.readTimeoutSecond, factory.writeTimeoutSecond));
            }
            if (!rand) { //指定地址
                TransportAddress taddr = findTransportAddress(addr);
                if (taddr == null) {
                    return AsyncConnection.createTCP(group, sslContext, addr, supportTcpNoDelay, factory.readTimeoutSecond, factory.writeTimeoutSecond);
                }
                final BlockingQueue<AsyncConnection> queue = taddr.conns;
                if (!queue.isEmpty()) {
                    AsyncConnection conn;
                    while ((conn = queue.poll()) != null) {
                        if (conn.isOpen()) return CompletableFuture.completedFuture(conn);
                    }
                }
                return AsyncConnection.createTCP(group, sslContext, addr, supportTcpNoDelay, factory.readTimeoutSecond, factory.writeTimeoutSecond);
            }

            //---------------------随机取地址------------------------
            //从连接池里取
            for (final TransportAddress taddr : this.transportAddrs) {
                if (!taddr.enable) continue;
                final BlockingQueue<AsyncConnection> queue = taddr.conns;
                if (!queue.isEmpty()) {
                    AsyncConnection conn;
                    while ((conn = queue.poll()) != null) {
                        if (conn.isOpen()) return CompletableFuture.completedFuture(conn);
                    }
                }
            }
            //从可用/不可用的地址列表中创建连接
            AtomicInteger count = new AtomicInteger(this.transportAddrs.length);
            CompletableFuture future = new CompletableFuture();
            for (final TransportAddress taddr : this.transportAddrs) {
                if (future.isDone()) return future;
                final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(group);
                if (supportTcpNoDelay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                channel.connect(taddr.address, taddr, new CompletionHandler<Void, TransportAddress>() {
                    @Override
                    public void completed(Void result, TransportAddress attachment) {
                        taddr.enable = true;
                        AsyncConnection asyncConn = AsyncConnection.create(channel, attachment.address, factory.readTimeoutSecond, factory.writeTimeoutSecond);
                        if (future.isDone()) {
                            if (!attachment.conns.offer(asyncConn)) asyncConn.dispose();
                        } else {
                            future.complete(asyncConn);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, TransportAddress attachment) {
                        taddr.enable = false;
                        if (count.decrementAndGet() < 1) {
                            future.completeExceptionally(exc);
                        }
                        try {
                            channel.close();
                        } catch (Exception e) {
                        }
                    }
                });
            }
            return future;
        } catch (Exception ex) {
            throw new RuntimeException("transport address = " + addr, ex);
        }
    }

    public void offerConnection(final boolean forceClose, AsyncConnection conn) {
        if (this.strategy != null && strategy.offerConnection(forceClose, conn)) return;
        if (!forceClose && conn.isTCP()) {
            if (conn.isOpen()) {
                TransportAddress taddr = findTransportAddress(conn.getRemoteAddress());
                if (taddr == null || !taddr.conns.offer(conn)) conn.dispose();
            }
        } else {
            conn.dispose();
        }
    }

    public <A> void async(SocketAddress addr, final ByteBuffer buffer, A att, final CompletionHandler<Integer, A> handler) {
        pollConnection(addr).whenComplete((conn, ex) -> {
            if (ex != null) {
                factory.getLogger().log(Level.WARNING, Transport.class.getSimpleName() + " async error", ex);
                return;
            }
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
        });
    }

    public static class TransportAddress {

        protected InetSocketAddress address;

        protected volatile boolean enable;

        protected final BlockingQueue<AsyncConnection> conns = new ArrayBlockingQueue<>(MAX_POOL_LIMIT);

        protected final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

        public TransportAddress(InetSocketAddress address) {
            this.address = address;
            this.enable = true;
        }

        @ConstructorParameters({"address", "enable"})
        public TransportAddress(InetSocketAddress address, boolean enable) {
            this.address = address;
            this.enable = enable;
        }

        public <T> T setAttribute(String name, T value) {
            attributes.put(name, value);
            return value;
        }

        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String name) {
            return (T) attributes.get(name);
        }

        @SuppressWarnings("unchecked")
        public <T> T removeAttribute(String name) {
            return (T) attributes.remove(name);
        }

        public TransportAddress clearAttributes() {
            attributes.clear();
            return this;
        }

        public ConcurrentHashMap<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(ConcurrentHashMap<String, Object> map) {
            attributes.clear();
            if (map != null) attributes.putAll(map);
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public boolean isEnable() {
            return enable;
        }

        @ConvertDisabled
        public BlockingQueue<AsyncConnection> getConns() {
            return conns;
        }

        public void dispose() {
            AsyncConnection conn;
            while ((conn = conns.poll()) != null) {
                conn.dispose();
            }
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

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
