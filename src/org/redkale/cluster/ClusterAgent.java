/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.redkale.boot.NodeServer;
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
 *
 * @since 2.1.0
 */
public abstract class ClusterAgent {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

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
            ClusterEntry entry = new ClusterEntry(ns, protocol, service);
            updateTransport(entry);
            remoteEntrys.put(entry.serviceid, entry);
        }
        afterRegister(ns, protocol);
    }

    //注销服务
    public void deregister(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices) {
        //注销本地模式
        for (Service service : localServices) {
            deregister(ns, protocol, service);
        }
        int s = intervalCheckSeconds();
        if (s > 0) {  //暂停，弥补其他依赖本进程服务的周期偏差
            try {
                Thread.sleep(s * 1000);
            } catch (InterruptedException ex) {
            }
            logger.info(this.getClass().getSimpleName() + " sleep " + s + " s after deregister");
        }
        //远程模式不注册
    }

    protected void afterRegister(NodeServer ns, String protocol) {
    }

    public int intervalCheckSeconds() {
        return 10;
    }

    //获取远程服务的可用ip列表
    protected abstract Collection<InetSocketAddress> queryAddress(ClusterEntry entry);

    //注册服务
    protected abstract void register(NodeServer ns, String protocol, Service service);

    //注销服务
    protected abstract void deregister(NodeServer ns, String protocol, Service service);

    //格式: protocol:classtype-resourcename
    protected void updateTransport(ClusterEntry entry) {
        Service service = entry.serviceref.get();
        if (service == null) return;
        Collection<InetSocketAddress> addrs = queryAddress(entry);
        Sncp.updateTransport(service, transportFactory, Sncp.getResourceType(service).getName() + "-" + Sncp.getResourceName(service), entry.netprotocol, entry.address, null, addrs);
    }

    //格式: protocol:classtype-resourcename
    protected String generateServiceName(NodeServer ns, String protocol, Service service) {
        if (!Sncp.isSncpDyn(service)) return protocol.toLowerCase() + ":" + service.getClass().getName();
        String resname = Sncp.getResourceName(service);
        return protocol.toLowerCase() + ":" + Sncp.getResourceType(service).getName() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: protocol:classtype-resourcename:nodeid
    protected String generateServiceId(NodeServer ns, String protocol, Service service) {
        return generateServiceName(ns, protocol, service) + ":" + this.nodeid;
    }

    protected String generateCheckName(NodeServer ns, String protocol, Service service) {
        return "check-" + generateServiceName(ns, protocol, service);
    }

    protected String generateCheckId(NodeServer ns, String protocol, Service service) {
        return "check-" + generateServiceId(ns, protocol, service);
    }

    protected ConcurrentHashMap<String, ClusterEntry> getLocalEntrys() {
        return localEntrys;
    }

    protected ConcurrentHashMap<String, ClusterEntry> getRemoteEntrys() {
        return remoteEntrys;
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

        public String servicename;

        public String checkid;

        public String checkname;

        public String protocol;

        public String netprotocol;

        public WeakReference<Service> serviceref;

        public InetSocketAddress address;

        public ClusterEntry(NodeServer ns, String protocol, Service service) {
            this.serviceid = generateServiceId(ns, protocol, service);
            this.servicename = generateServiceName(ns, protocol, service);
            this.checkid = generateCheckId(ns, protocol, service);
            this.checkname = generateCheckName(ns, protocol, service);
            this.protocol = protocol;
            this.address = ns.getSocketAddress();
            this.serviceref = new WeakReference(service);
            Server server = ns.getServer();
            this.netprotocol = server instanceof SncpServer ? ((SncpServer) server).getNetprotocol() : Transport.DEFAULT_PROTOCOL;
        }
    }
}
