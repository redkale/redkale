/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.service.Service;
import org.redkale.util.ObjectPool;

/**
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

    public TransportFactory(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, AsynchronousChannelGroup channelGroup) {
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.channelGroup = channelGroup;
    }

    public String findGroupName(InetSocketAddress addr) {
        if (addr == null) return null;
        return groupAddrs.get(addr);
    }

    public TransportGroupInfo findGroupInfo2(String group) {
        if (group == null) return null;
        return groupInfos.get(group);
    }

    public TransportFactory addGroupInfo(String name, InetSocketAddress... addrs) {
        addGroupInfo(new TransportGroupInfo(name, addrs));
        return this;
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
        return new Transport("remotes", info.protocol, info.subprotocol, this.bufferPool, this.channelGroup, sncpAddress, addresses);
    }

    private Transport loadTransport(final String groupName, InetSocketAddress sncpAddress) {
        if (groupName == null) return null;
        TransportGroupInfo info = groupInfos.get(groupName);
        if (info == null) return null;
        return new Transport(groupName, info.protocol, info.subprotocol, this.bufferPool, this.channelGroup, sncpAddress, info.addresses);
    }

    public ExecutorService getExecutor() {
        return executor;
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
