/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import org.redkale.service.Service;
import org.redkale.util.ObjectPool;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TransportFactory {

    protected static final Logger logger = Logger.getLogger(TransportFactory.class.getSimpleName());

    //传输端的线程池
    protected final ExecutorService executor;

    //传输端的ByteBuffer对象池
    protected final ObjectPool<ByteBuffer> bufferPool;

    //传输端的ChannelGroup
    protected final AsynchronousChannelGroup channelGroup;

    //每个地址对应的Group名
    protected final Map<InetSocketAddress, String> groupAddrs = new HashMap<>();

    //协议地址的Group集合
    protected final Map<String, TransportGroupInfo> groupInfos = new HashMap<>();

    protected final List<WeakReference<Service>> services = new CopyOnWriteArrayList<>();

    //负载均衡策略
    protected final TransportStrategy strategy;

    protected TransportFactory(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup,
        final TransportStrategy strategy) {
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.channelGroup = channelGroup;
        this.strategy = strategy;
    }

    protected TransportFactory(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup) {
        this(executor, bufferPool, channelGroup, null);
    }

    public static TransportFactory create(int threads) {
        return create(threads, threads * 2, 8 * 1024);
    }

    public static TransportFactory create(int threads, int bufferPoolSize, int bufferCapacity) {
        final ObjectPool<ByteBuffer> transportPool = new ObjectPool<>(new AtomicLong(), new AtomicLong(), bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) return false;
                e.clear();
                return true;
            });
        final AtomicInteger counter = new AtomicInteger();
        ExecutorService transportExec = Executors.newFixedThreadPool(threads, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Transport-Thread-" + counter.incrementAndGet());
            return t;
        });
        AsynchronousChannelGroup transportGroup = null;
        try {
            transportGroup = AsynchronousChannelGroup.withCachedThreadPool(transportExec, 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return create(transportExec, transportPool, transportGroup);
    }

    public static TransportFactory create(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup) {
        return new TransportFactory(executor, bufferPool, channelGroup, null);
    }

    public static TransportFactory create(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup,
        final TransportStrategy strategy) {
        return new TransportFactory(executor, bufferPool, channelGroup, strategy);
    }

    public Transport createTransportTCP(String name, final InetSocketAddress clientAddress, final Collection<InetSocketAddress> addresses) {
        return new Transport(name, "TCP", "", this, this.bufferPool, this.channelGroup, clientAddress, addresses, strategy);
    }

    public Transport createTransport(String name, String protocol, final InetSocketAddress clientAddress, final Collection<InetSocketAddress> addresses) {
        return new Transport(name, protocol, "", this, this.bufferPool, this.channelGroup, clientAddress, addresses, strategy);
    }

    public Transport createTransport(String name, String protocol, String subprotocol,
        final InetSocketAddress clientAddress, final Collection<InetSocketAddress> addresses) {
        return new Transport(name, protocol, subprotocol, this, this.bufferPool, this.channelGroup, clientAddress, addresses, strategy);
    }

    public String findGroupName(InetSocketAddress addr) {
        if (addr == null) return null;
        return groupAddrs.get(addr);
    }

    public TransportGroupInfo findGroupInfo(String group) {
        if (group == null) return null;
        return groupInfos.get(group);
    }

    public boolean addGroupInfo(String groupName, InetSocketAddress... addrs) {
        addGroupInfo(new TransportGroupInfo(groupName, addrs));
        return true;
    }

    public boolean removeGroupInfo(String groupName, InetSocketAddress addr) {
        if (groupName == null || groupName.isEmpty() || addr == null) return false;
        if (!groupName.equals(groupAddrs.get(addr))) return false;
        TransportGroupInfo group = groupInfos.get(groupName);
        if (group == null) return false;
        group.removeAddress(addr);
        groupAddrs.remove(addr);
        return true;
    }

    public TransportFactory addGroupInfo(String name, Set<InetSocketAddress> addrs) {
        addGroupInfo(new TransportGroupInfo(name, addrs));
        return this;
    }

    public boolean addGroupInfo(TransportGroupInfo info) {
        if (info == null) throw new RuntimeException("TransportGroupInfo can not null");
        if (info.addresses == null) throw new RuntimeException("TransportGroupInfo.addresses can not null");
        if (!checkName(info.name)) throw new RuntimeException("Transport.group.name only 0-9 a-z A-Z _ cannot begin 0-9");
        TransportGroupInfo old = groupInfos.get(info.name);
        if (old != null && !old.protocol.equals(info.protocol)) throw new RuntimeException("Transport.group.name repeat but protocol is different");
        if (old != null && !old.subprotocol.equals(info.subprotocol)) throw new RuntimeException("Transport.group.name repeat but subprotocol is different");
        for (InetSocketAddress addr : info.addresses) {
            if (!groupAddrs.getOrDefault(addr, info.name).equals(info.name)) throw new RuntimeException(addr + " repeat but different group.name");
        }
        if (old == null) {
            groupInfos.put(info.name, info);
        } else {
            old.putAddress(info.addresses);
        }
        for (InetSocketAddress addr : info.addresses) {
            groupAddrs.put(addr, info.name);
        }
        return true;
    }

    public Transport loadSameGroupTransport(InetSocketAddress sncpAddress) {
        return loadTransport(groupAddrs.get(sncpAddress), sncpAddress);
    }

    public Transport[] loadDiffGroupTransports(InetSocketAddress sncpAddress, final Set<String> diffGroups) {
        if (diffGroups == null) return null;
        final String sncpGroup = groupAddrs.get(sncpAddress);
        final List<Transport> transports = new ArrayList<>();
        for (String group : diffGroups) {
            if (sncpGroup == null || !sncpGroup.equals(group)) {
                transports.add(loadTransport(group, sncpAddress));
            }
        }
        return transports.toArray(new Transport[transports.size()]);
    }

    public Transport loadRemoteTransport(InetSocketAddress sncpAddress, final Set<String> groups) {
        if (groups == null) return null;
        Set<InetSocketAddress> addresses = new HashSet<>();
        TransportGroupInfo info = null;
        for (String group : groups) {
            info = groupInfos.get(group);
            if (info == null) continue;
            addresses.addAll(info.addresses);
        }
        if (info == null) return null;
        if (sncpAddress != null) addresses.remove(sncpAddress);
        return new Transport(groups.stream().sorted().collect(Collectors.joining(";")), info.protocol, info.subprotocol, this, this.bufferPool, this.channelGroup, sncpAddress, addresses, this.strategy);
    }

    private Transport loadTransport(final String groupName, InetSocketAddress sncpAddress) {
        if (groupName == null) return null;
        TransportGroupInfo info = groupInfos.get(groupName);
        if (info == null) return null;
        return new Transport(groupName, info.protocol, info.subprotocol, this, this.bufferPool, this.channelGroup, sncpAddress, info.addresses, this.strategy);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public List<TransportGroupInfo> getGroupInfos() {
        return new ArrayList<>(this.groupInfos.values());
    }

    public void addSncpService(Service service) {
        if (service == null) return;
        services.add(new WeakReference<>(service));
    }

    public List<Service> getServices() {
        List<Service> rs = new ArrayList<>();
        for (WeakReference<Service> ref : services) {
            Service service = ref.get();
            if (service != null) rs.add(service);
        }
        return rs;
    }

    public void shutdownNow() {
        try {
            this.channelGroup.shutdownNow();
        } catch (Exception e) {
            logger.log(Level.FINER, "close transportChannelGroup erroneous", e);
        }
    }

    private static boolean checkName(String name) {  //不能含特殊字符
        if (name.isEmpty()) return false;
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') return false;
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                return false;
            }
        }
        return true;
    }
}
