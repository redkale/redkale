/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 第三方服务发现管理接口cluster
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class ClusterAgent {

    protected int nodeid;

    protected String name;

    protected String[] protocols; //必须全大写

    protected int[] ports;

    protected AnyValue config;

    protected TransportFactory transportFactory;

    protected final ConcurrentHashMap<String, ClusterEntry> localEntrys = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, ClusterEntry> remoteEntrys = new ConcurrentHashMap<>();

    public void init(AnyValue config) {
        this.config = config;
        this.name = config.getValue("name", "");
        {
            String ps = config.getValue("protocols", "").toUpperCase();
            if (ps == null || ps.isEmpty()) ps = "SNCP";
            this.protocols = ps.split(";");
        }
        String ts = config.getValue("ports", "");
        if (ts != null && !ts.isEmpty()) {
            String[] its = ts.split(";");
            List<Integer> list = new ArrayList<>();
            for (String str : its) {
                if (str.trim().isEmpty()) continue;
                list.add(Integer.parseInt(str.trim()));
            }
            if (!list.isEmpty()) this.ports = list.stream().mapToInt(x -> x).toArray();
        }
    }

    public void destroy(AnyValue config) {
    }

    public boolean containsProtocol(String protocol) {
        if (protocol == null || protocol.isEmpty()) return false;
        return protocols == null || Utility.contains(protocols, protocol.toUpperCase());
    }

    public boolean containsPort(int port) {
        if (ports == null || ports.length == 0) return true;
        return Utility.contains(ports, port);
    }

    //注册服务
    public void register(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices) {
        if (localServices.isEmpty()) return;
        //注册本地模式
        for (Service service : localServices) {
            register(ns, protocol, service);
            ClusterEntry entry = new ClusterEntry(ns, protocol, service);
            localEntrys.put(entry.serviceid, entry);
        }
        //远程模式加载IP列表, 只能是SNCP协议        
        for (Service service : remoteServices) {
            updateTransport(ns, protocol, service);
            ClusterEntry entry = new ClusterEntry(ns, protocol, service);
            remoteEntrys.put(entry.serviceid, entry);
        }
    }

    //注销服务
    public void deregister(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices) {
        //注销本地模式
        for (Service service : localServices) {
            deregister(ns, protocol, service);
        }
        //远程模式不注册
    }

    //获取远程服务的可用ip列表
    public abstract List<InetSocketAddress> queryAddress(NodeServer ns, String protocol, Service service);

    //注册服务
    public abstract void register(NodeServer ns, String protocol, Service service);

    //注销服务
    public abstract void deregister(NodeServer ns, String protocol, Service service);

    //格式: protocol:classtype-resourcename
    public void updateTransport(NodeServer ns, String protocol, Service service) {
        Server server = ns.getServer();
        String netprotocol = server instanceof SncpServer ? ((SncpServer) server).getNetprotocol() : Transport.DEFAULT_PROTOCOL;
        if (!Sncp.isSncpDyn(service)) return;
        List<InetSocketAddress> addrs = queryAddress(ns, protocol, service);
        if (addrs != null && !addrs.isEmpty()) {
            Sncp.updateTransport(service, transportFactory, Sncp.getResourceType(service).getName() + "-" + Sncp.getResourceName(service), netprotocol, ns.getSncpAddress(), null, addrs);
        }
    }

    //格式: protocol:classtype-resourcename
    public String generateServiceType(NodeServer ns, String protocol, Service service) {
        if (!Sncp.isSncpDyn(service)) return protocol.toLowerCase() + ":" + service.getClass().getName();
        return protocol.toLowerCase() + ":" + Sncp.getResourceType(service).getName() + "-" + Sncp.getResourceName(service);
    }

    //格式: protocol:classtype-resourcename:nodeid
    public String generateServiceId(NodeServer ns, String protocol, Service service) {
        return generateServiceType(ns, protocol, service) + ":" + this.nodeid;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public int getNodeid() {
        return nodeid;
    }

    public void setNodeid(int nodeid) {
        this.nodeid = nodeid;
    }

    public TransportFactory getTransportFactory() {
        return transportFactory;
    }

    public void setTransportFactory(TransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public AnyValue getConfig() {
        return config;
    }

    public void setConfig(AnyValue config) {
        this.config = config;
    }

    public class ClusterEntry {

        public String serviceid;

        public String servicetype;

        public WeakReference<Service> serviceref;

        public InetSocketAddress address;

        public ClusterEntry(NodeServer ns, String protocol, Service service) {
            this.serviceid = generateServiceId(ns, protocol, service);
            this.servicetype = generateServiceType(ns, protocol, service);
            this.address = ns.getSocketAddress();
            this.serviceref = new WeakReference(service);
        }
    }
}
