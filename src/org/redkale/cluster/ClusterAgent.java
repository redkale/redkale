/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.annotation.Resource;
import org.redkale.boot.*;
import static org.redkale.boot.Application.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.mq.MessageMultiConsumer;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
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

    @Resource(name = RESNAME_APP_NODEID)
    protected int nodeid;

    @Resource(name = RESNAME_APP_NAME)
    protected String appName = "";

    @Resource(name = RESNAME_APP_ADDR)
    protected InetSocketAddress appAddress;

    protected String name;

    protected boolean waits;

    protected String[] protocols; //必须全大写

    protected int[] ports;

    protected AnyValue config;

    protected TransportFactory transportFactory;

    protected final ConcurrentHashMap<String, ClusterEntry> localEntrys = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, ClusterEntry> remoteEntrys = new ConcurrentHashMap<>();

    public void init(AnyValue config) {
        this.config = config;
        this.name = config.getValue("name", "");
        this.waits = config.getBoolValue("waits", false);
        {
            String ps = config.getValue("protocols", "").toUpperCase();
            if (ps == null || ps.isEmpty()) ps = "SNCP;HTTP";
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

    //ServiceLoader时判断配置是否符合当前实现类
    public abstract boolean match(AnyValue config);

    public boolean containsProtocol(String protocol) {
        if (protocol == null || protocol.isEmpty()) return false;
        return protocols == null || Utility.contains(protocols, protocol.toUpperCase());
    }

    public boolean containsPort(int port) {
        if (ports == null || ports.length == 0) return true;
        return Utility.contains(ports, port);
    }

    public abstract void register(Application application);

    public abstract void deregister(Application application);

    //注册服务, 在NodeService调用Service.init方法之前调用
    public void register(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices) {
        if (localServices.isEmpty()) return;
        //注册本地模式
        for (Service service : localServices) {
            if (!canRegister(protocol, service)) continue;
            register(ns, protocol, service);
            ClusterEntry htentry = new ClusterEntry(ns, protocol, service);
            localEntrys.put(htentry.serviceid, htentry);
            if (protocol.toLowerCase().startsWith("http")) {
                MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
                if (mmc != null) {
                    register(ns, "mqtp", service);
                    ClusterEntry mqentry = new ClusterEntry(ns, "mqtp", service);
                    localEntrys.put(mqentry.serviceid, mqentry);
                }
            }
        }
        //远程模式加载IP列表, 只支持SNCP协议    
        if (ns.isSNCP()) {
            for (Service service : remoteServices) {
                ClusterEntry entry = new ClusterEntry(ns, protocol, service);
                updateSncpTransport(entry);
                remoteEntrys.put(entry.serviceid, entry);
            }
        }
    }

    //注销服务, 在NodeService调用Service.destroy 方法之前调用
    public void deregister(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices) {
        //注销本地模式  远程模式不注册
        for (Service service : localServices) {
            if (!canRegister(protocol, service)) continue;
            deregister(ns, protocol, service);
        }
        afterDeregister(ns, protocol);
    }

    protected boolean canRegister(String protocol, Service service) {
        if ("SNCP".equalsIgnoreCase(protocol) && service.getClass().getAnnotation(Local.class) != null) return false;
        if (service instanceof WebSocketNode) {
            if (((WebSocketNode) service).getLocalWebSocketEngine() == null) return false;
        }
        return true;
    }

    public void start() {
    }

    protected void afterDeregister(NodeServer ns, String protocol) {
        if (!this.waits) return;
        int s = intervalCheckSeconds();
        if (s > 0) {  //暂停，弥补其他依赖本进程服务的周期偏差
            try {
                Thread.sleep(s * 1000);
            } catch (InterruptedException ex) {
            }
            logger.info(this.getClass().getSimpleName() + " wait for " + s * 1000 + "ms after deregister");
        }
    }

    public int intervalCheckSeconds() {
        return 10;
    }

    //获取MQTP的HTTP远程服务的可用ip列表, key = servicename的后半段
    public abstract CompletableFuture<Map<String, Collection<InetSocketAddress>>> queryMqtpAddress(String protocol, String module, String resname);

    //获取HTTP远程服务的可用ip列表
    public abstract CompletableFuture<Collection<InetSocketAddress>> queryHttpAddress(String protocol, String module, String resname);

    //获取远程服务的可用ip列表
    protected abstract CompletableFuture<Collection<InetSocketAddress>> queryAddress(ClusterEntry entry);

    //注册服务
    protected abstract void register(NodeServer ns, String protocol, Service service);

    //注销服务
    protected abstract void deregister(NodeServer ns, String protocol, Service service);

    //格式: protocol:classtype-resourcename
    protected void updateSncpTransport(ClusterEntry entry) {
        Service service = entry.serviceref.get();
        if (service == null) return;
        Collection<InetSocketAddress> addrs = ClusterAgent.this.queryAddress(entry).join();
        Sncp.updateTransport(service, transportFactory, Sncp.getResourceType(service).getName() + "-" + Sncp.getResourceName(service), entry.netprotocol, entry.address, null, addrs);
    }

    protected String generateApplicationServiceName() {
        return "application" + (appName == null || appName.isEmpty() ? "" : ("." + appName)) + ".node" + this.nodeid;
    }

    protected String generateApplicationServiceId() { //与servicename相同
        return generateApplicationServiceName();
    }

    protected String generateApplicationCheckName() {
        return "check-" + generateApplicationServiceName();
    }

    protected String generateApplicationCheckId() {
        return "check-" + generateApplicationServiceId();
    }

    //也会提供给HttpMessageClusterAgent适用
    public String generateHttpServiceName(String protocol, String module, String resname) {
        return protocol.toLowerCase() + ":" + module + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: protocol:classtype-resourcename
    protected String generateServiceName(NodeServer ns, String protocol, Service service) {
        if (protocol.toLowerCase().startsWith("http")) {  //HTTP使用RestService.name方式是为了与MessageClient中的module保持一致, 因为HTTP依靠的url中的module，无法知道Service类名
            String resname = Sncp.getResourceName(service);
            String module = Rest.getRestModule(service).toLowerCase();
            return protocol.toLowerCase() + ":" + module + (resname.isEmpty() ? "" : ("-" + resname));
        }
        if ("mqtp".equalsIgnoreCase(protocol)) {
            MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
            String selfmodule = Rest.getRestModule(service).toLowerCase();
            return protocol.toLowerCase() + ":" + mmc.module() + ":" + selfmodule;
        }
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

        public boolean canceled;

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

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
