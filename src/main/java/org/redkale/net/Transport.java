/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 * 被net.client模块代替
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Deprecated(since = "2.8.0")
public final class Transport {

    public static final String DEFAULT_NETPROTOCOL = "TCP";

    protected final AtomicInteger seq = new AtomicInteger(-1);

    protected final ReentrantLock lock = new ReentrantLock();

    protected final TransportFactory factory;

    protected final String name; // 即<group>的name属性

    protected final boolean tcp;

    protected final String netprotocol;

    // 传输端的AsyncGroup
    protected final AsyncGroup asyncGroup;

    protected final InetSocketAddress clientAddress;

    // 不可能为null
    protected TransportNode[] transportNodes = new TransportNode[0];

    protected final SSLContext sslContext;

    // 负载均衡策略
    protected final TransportStrategy strategy;

    // 连接上限， 为null表示无限制
    protected Semaphore semaphore;

    protected Transport(
            String name,
            TransportFactory factory,
            final AsyncGroup asyncGroup,
            final SSLContext sslContext,
            final InetSocketAddress clientAddress,
            final Collection<InetSocketAddress> addresses,
            final TransportStrategy strategy) {
        this(name, DEFAULT_NETPROTOCOL, factory, asyncGroup, sslContext, clientAddress, addresses, strategy);
    }

    protected Transport(
            String name,
            String netprotocol,
            final TransportFactory factory,
            final AsyncGroup asyncGroup,
            final SSLContext sslContext,
            final InetSocketAddress clientAddress,
            final Collection<InetSocketAddress> addresses,
            final TransportStrategy strategy) {
        this.name = name;
        this.netprotocol = netprotocol;
        this.factory = factory;
        factory.transportReferences.add(new WeakReference<>(this));
        this.tcp = "TCP".equalsIgnoreCase(netprotocol);
        this.asyncGroup = asyncGroup;
        this.sslContext = sslContext;
        this.clientAddress = clientAddress;
        this.strategy = strategy;
        updateRemoteAddresses(addresses);
    }

    public Semaphore getSemaphore() {
        return semaphore;
    }

    public void setSemaphore(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    public final InetSocketAddress[] updateRemoteAddresses(final Collection<InetSocketAddress> addresses) {
        final TransportNode[] oldNodes = this.transportNodes;
        lock.lock();
        try {
            boolean same = false;
            if (this.transportNodes != null && addresses != null && this.transportNodes.length == addresses.size()) {
                same = true;
                for (TransportNode node : this.transportNodes) {
                    if (!addresses.contains(node.getAddress())) {
                        same = false;
                        break;
                    }
                }
            }
            if (!same) {
                List<TransportNode> list = new ArrayList<>();
                if (addresses != null) {
                    for (InetSocketAddress addr : addresses) {
                        if (clientAddress != null && clientAddress.equals(addr)) {
                            continue;
                        }
                        boolean hasold = false;
                        for (TransportNode oldAddr : oldNodes) {
                            if (oldAddr.getAddress().equals(addr)) {
                                list.add(oldAddr);
                                hasold = true;
                                break;
                            }
                        }
                        if (hasold) {
                            continue;
                        }
                        list.add(new TransportNode(factory.poolMaxConns, addr));
                    }
                }
                this.transportNodes = list.toArray(new TransportNode[list.size()]);
            }
        } finally {
            lock.unlock();
        }
        InetSocketAddress[] rs = new InetSocketAddress[oldNodes.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = oldNodes[i].getAddress();
        }
        return rs;
    }

    public final boolean addRemoteAddresses(final InetSocketAddress addr) {
        if (addr == null) {
            return false;
        }
        if (clientAddress != null && clientAddress.equals(addr)) {
            return false;
        }
        lock.lock();
        try {
            if (this.transportNodes.length == 0) {
                this.transportNodes = new TransportNode[] {new TransportNode(factory.poolMaxConns, addr)};
            } else {
                for (TransportNode i : this.transportNodes) {
                    if (addr.equals(i.address)) {
                        return false;
                    }
                }
                this.transportNodes = Utility.append(transportNodes, new TransportNode(factory.poolMaxConns, addr));
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public final boolean removeRemoteAddresses(InetSocketAddress addr) {
        if (addr == null) {
            return false;
        }
        lock.lock();
        try {
            this.transportNodes = Utility.remove(transportNodes, new TransportNode(factory.poolMaxConns, addr));
        } finally {
            lock.unlock();
        }
        return true;
    }

    public String getName() {
        return name;
    }

    public void close() {
        TransportNode[] nodes = this.transportNodes;
        if (nodes == null) {
            return;
        }
        for (TransportNode node : nodes) {
            if (node != null) {
                node.dispose();
            }
        }
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public TransportNode[] getTransportNodes() {
        return transportNodes;
    }

    public TransportNode findTransportNode(SocketAddress addr) {
        for (TransportNode node : this.transportNodes) {
            if (node.address.equals(addr)) {
                return node;
            }
        }
        return null;
    }

    public InetSocketAddress[] getRemoteAddresses() {
        InetSocketAddress[] rs = new InetSocketAddress[transportNodes.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = transportNodes[i].getAddress();
        }
        return rs;
    }

    @Override
    public String toString() {
        return Transport.class.getSimpleName() + "{name = " + name + ", protocol = " + netprotocol
                + ", clientAddress = " + clientAddress + ", remoteNodes = " + Arrays.toString(transportNodes) + "}";
    }

    public String getNetprotocol() {
        return netprotocol;
    }

    public boolean isTCP() {
        return tcp;
    }

    protected CompletableFuture<AsyncConnection> pollAsync(
            TransportNode node, SocketAddress addr, Supplier<CompletableFuture<AsyncConnection>> func) {
        final BlockingQueue<AsyncConnection> queue = node.connQueue;
        if (!queue.isEmpty()) {
            AsyncConnection conn;
            while ((conn = queue.poll()) != null) {
                if (conn.isOpen()) {
                    return CompletableFuture.completedFuture(conn);
                } else {
                    conn.dispose();
                }
            }
        }
        if (semaphore != null && !semaphore.tryAcquire()) {
            final CompletableFuture<AsyncConnection> future =
                    Utility.orTimeout(new CompletableFuture<>(), null, 10, TimeUnit.SECONDS);
            future.whenComplete((r, t) -> node.pollQueue.remove(future));
            if (node.pollQueue.offer(future)) {
                return future;
            }
            future.completeExceptionally(new IOException("create transport connection error"));
            return future;
        }
        return func.get().thenApply(conn -> {
            if (conn != null && semaphore != null) {
                conn.beforeCloseListener((c) -> semaphore.release());
            }
            return conn;
        });
    }

    public CompletableFuture<AsyncConnection> pollConnection(SocketAddress addr0) {
        if (this.strategy != null) {
            return strategy.pollConnection(addr0, this);
        }
        final TransportNode[] nodes = this.transportNodes;
        if (addr0 == null && nodes.length == 1) {
            addr0 = nodes[0].address;
        }
        final SocketAddress addr = addr0;
        final boolean rand = addr == null; // 是否随机取地址
        if (rand && nodes.length < 1) {
            throw new RedkaleException("Transport (" + this.name + ") have no remoteAddress list");
        }
        try {
            if (!tcp) { // UDP
                SocketAddress udpaddr = rand ? nodes[0].address : addr;
                return asyncGroup.createUDPClientConnection(udpaddr, 6);
            }
            if (!rand) { // 指定地址
                TransportNode node = findTransportNode(addr);
                if (node == null) {
                    return asyncGroup.createTCPClientConnection(addr, 6);
                }
                return pollAsync(node, addr, () -> asyncGroup.createTCPClientConnection(addr, 6));
            }

            // ---------------------随机取地址------------------------
            int enablecount = 0;
            final TransportNode[] newnodes = new TransportNode[nodes.length];
            for (final TransportNode node : nodes) {
                if (node.disabletime > 0) {
                    continue;
                }
                newnodes[enablecount++] = node;
            }
            final long now = System.currentTimeMillis();
            if (enablecount > 0) { // 存在可用的地址
                final TransportNode one = newnodes[Math.abs(seq.incrementAndGet()) % enablecount];
                final BlockingQueue<AsyncConnection> queue = one.connQueue;
                if (!queue.isEmpty()) {
                    AsyncConnection conn;
                    while ((conn = queue.poll()) != null) {
                        if (conn.isOpen()) {
                            return CompletableFuture.completedFuture(conn);
                        } else {
                            conn.dispose();
                        }
                    }
                }
                return pollAsync(one, one.getAddress(), () -> {
                    return asyncGroup.createTCPClientConnection(one.address, 6).whenComplete((c, t) -> {
                        one.disabletime = t == null ? 0 : System.currentTimeMillis();
                    });
                });
            }
            return pollConnection0(nodes, null, now);
        } catch (Exception ex) {
            throw new RedkaleException("transport address = " + addr, ex);
        }
    }

    private CompletableFuture<AsyncConnection> pollConnection0(TransportNode[] nodes, TransportNode exclude, long now)
            throws IOException {
        // 从可用/不可用的地址列表中创建连接
        CompletableFuture future = new CompletableFuture();
        for (final TransportNode node : nodes) {
            if (node == exclude) {
                continue;
            }
            if (future.isDone()) {
                return future;
            }
            asyncGroup.createTCPClientConnection(node.address, 6).whenComplete((c, t) -> {
                if (c != null && !future.complete(c)) {
                    node.connQueue.offer(c);
                }
                node.disabletime = t == null ? 0 : System.currentTimeMillis();
            });
        }
        return future;
    }

    public void offerConnection(final boolean forceClose, AsyncConnection conn) {
        if (this.strategy != null && strategy.offerConnection(forceClose, conn)) {
            return;
        }
        if (!forceClose && conn.isTCP()) {
            if (conn.isOpen()) {
                TransportNode node = findTransportNode(conn.getRemoteAddress());
                if (node == null || !node.connQueue.offer(conn)) {
                    conn.dispose();
                }
            } else {
                conn.dispose();
            }
        } else {
            conn.dispose();
        }
    }

    public <A> void async(
            SocketAddress addr, final ByteBuffer buffer, A att, final CompletionHandler<Integer, A> handler) {
        pollConnection(addr).whenComplete((conn, ex) -> {
            if (ex != null) {
                factory.getLogger().log(Level.WARNING, Transport.class.getSimpleName() + " async error", ex);
                return;
            }
            conn.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    buffer.clear();
                    conn.setReadBuffer(buffer);
                    conn.read(new CompletionHandler<Integer, ByteBuffer>() {

                        @Override
                        public void completed(Integer result, ByteBuffer attachment) {
                            if (handler != null) {
                                handler.completed(result, att);
                            }
                            conn.offerWriteBuffer(attachment);
                            offerConnection(false, conn);
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer attachment) {
                            conn.offerWriteBuffer(attachment);
                            offerConnection(true, conn);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    conn.offerWriteBuffer(attachment);
                    offerConnection(true, conn);
                }
            });
        });
    }

    public static class TransportNode {

        protected InetSocketAddress address;

        protected volatile long disabletime; // 不可用时的时间, 为0表示可用

        protected final BlockingQueue<AsyncConnection> connQueue;

        protected final ArrayBlockingQueue<CompletableFuture<AsyncConnection>> pollQueue;

        protected final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

        public TransportNode(int poolMaxConns, InetSocketAddress address) {
            this.address = address;
            this.disabletime = 0;
            this.connQueue = new ArrayBlockingQueue<>(poolMaxConns);
            this.pollQueue = new ArrayBlockingQueue(this.connQueue.remainingCapacity() * 100);
        }

        @ConstructorParameters({"poolMaxConns", "address", "disabletime"})
        public TransportNode(int poolMaxConns, InetSocketAddress address, long disabletime) {
            this.address = address;
            this.disabletime = disabletime;
            this.connQueue = new LinkedBlockingQueue<>(poolMaxConns);
            this.pollQueue = new ArrayBlockingQueue(this.connQueue.remainingCapacity() * 100);
        }

        public int getPoolMaxConns() {
            return this.connQueue.remainingCapacity() + this.connQueue.size();
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

        public TransportNode clearAttributes() {
            attributes.clear();
            return this;
        }

        public ConcurrentHashMap<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(ConcurrentHashMap<String, Object> map) {
            attributes.clear();
            if (map != null) {
                attributes.putAll(map);
            }
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public long getDisabletime() {
            return disabletime;
        }

        @ConvertDisabled
        public BlockingQueue<AsyncConnection> getConnQueue() {
            return connQueue;
        }

        public void dispose() {
            AsyncConnection conn;
            while ((conn = connQueue.poll()) != null) {
                conn.dispose();
            }
        }

        @Override
        public int hashCode() {
            return this.address.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TransportNode other = (TransportNode) obj;
            return this.address.equals(other.address);
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
