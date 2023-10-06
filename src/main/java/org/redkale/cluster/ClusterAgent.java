/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceListener;
import org.redkale.boot.*;
import static org.redkale.boot.Application.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.Server;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * 服务注册中心管理类cluster
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

    @Resource(required = false)
    protected Application application;

    protected String name;

    protected boolean waits;

    protected String[] protocols; //必须全大写

    protected int[] ports;

    protected AnyValue config;

    protected Set<String> tags;

    protected final ConcurrentHashMap<String, ClusterEntry> localEntrys = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, ClusterEntry> remoteEntrys = new ConcurrentHashMap<>();

    public void init(AnyValue config) {
        this.config = config;
        this.name = config.getValue("name", "");
        this.waits = config.getBoolValue("waits", false);
        {
            String ps = config.getValue("protocols", "").toUpperCase();
            if (ps == null || ps.isEmpty()) {
                ps = "SNCP;HTTP";
            }
            this.protocols = ps.split(";");
        }
        String ts = config.getValue("ports", "");
        if (ts != null && !ts.isEmpty()) {
            String[] its = ts.split(";");
            List<Integer> list = new ArrayList<>();
            for (String str : its) {
                if (str.trim().isEmpty()) {
                    continue;
                }
                list.add(Integer.parseInt(str.trim()));
            }
            if (!list.isEmpty()) {
                this.ports = list.stream().mapToInt(x -> x).toArray();
            }
        }
        Set<String> tags0 = new HashSet<>();
        for (String str : config.getValue("tags", "").split(";|,")) {
            if (!str.trim().isEmpty()) {
                tags0.add(str.trim());
            }
        }
        if (!tags0.isEmpty()) {
            this.tags = tags0;
        }
    }

    @ResourceListener
    public abstract void onResourceChange(ResourceEvent[] events);

    public void destroy(AnyValue config) {
    }

    /**
     * ServiceLoader时判断配置是否符合当前实现类
     *
     * @param config 节点配置
     *
     * @return boolean
     */
    public abstract boolean acceptsConf(AnyValue config);

    public boolean containsProtocol(String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            return false;
        }
        return protocols == null || Utility.contains(protocols, protocol.toUpperCase());
    }

    public boolean containsPort(int port) {
        if (ports == null || ports.length == 0) {
            return true;
        }
        return Utility.contains(ports, port);
    }

    public abstract void register(Application application);

    public abstract void deregister(Application application);

    //注册服务, 在NodeService调用Service.init方法之前调用
    public void register(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices, Set<Service> servletServices) {
        if (servletServices.isEmpty()) {
            return;
        }
        //注册本地模式
        for (Service service : servletServices) {
            if (!canRegister(ns, protocol, service)) {
                continue;
            }
            ClusterEntry htentry = register(ns, protocol, service);
            localEntrys.put(htentry.serviceid, htentry);
        }
        //远程模式加载IP列表, 只支持SNCP协议    
        if (ns.isSNCP()) {
            for (Service service : remoteServices) {
                ClusterEntry entry = new ClusterEntry(ns, protocol, service);
                updateSncpAddress(entry);
                remoteEntrys.put(entry.serviceid, entry);
            }
        }
    }

    //注销服务, 在NodeService调用Service.destroy 方法之前调用
    public void deregister(NodeServer ns, String protocol, Set<Service> localServices, Set<Service> remoteServices, Set<Service> servletServices) {
        //注销本地模式  远程模式不注册
        for (Service service : servletServices) {
            if (!canRegister(ns, protocol, service)) {
                continue;
            }
            deregister(ns, protocol, service);
        }
        afterDeregister(ns, protocol);
    }

    protected boolean canRegister(NodeServer ns, String protocol, Service service) {
        if (service.getClass().getAnnotation(Component.class) != null) {
            return false;
        }
        if ("SNCP".equalsIgnoreCase(protocol) && service.getClass().getAnnotation(Local.class) != null) {
            return false;
        }
        AutoLoad al = service.getClass().getAnnotation(AutoLoad.class);
        if (al != null && !al.value() && service.getClass().getAnnotation(Local.class) != null) {
            return false;
        }
        org.redkale.util.AutoLoad al2 = service.getClass().getAnnotation(org.redkale.util.AutoLoad.class);
        if (al2 != null && !al2.value() && service.getClass().getAnnotation(Local.class) != null) {
            return false;
        }
        if (service instanceof WebSocketNode) {
            if (((WebSocketNode) service).getLocalWebSocketEngine() == null) {
                return false;
            }
        }
        ClusterEntry entry = new ClusterEntry(ns, protocol, service);
        if (entry.serviceName.trim().endsWith(serviceSeparator())) {
            return false;
        }
        return true;
    }

    public void start() {
    }

    protected void afterDeregister(NodeServer ns, String protocol) {
        if (!this.waits) {
            return;
        }
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

    //获取HTTP远程服务的可用ip列表
    public abstract CompletableFuture<Set<InetSocketAddress>> queryHttpAddress(String protocol, String module, String resname);

    //获取SNCP远程服务的可用ip列表 restype: resourceType.getName()
    public abstract CompletableFuture<Set<InetSocketAddress>> querySncpAddress(String protocol, String restype, String resname);

    //获取远程服务的可用ip列表
    protected abstract CompletableFuture<Set<InetSocketAddress>> queryAddress(ClusterEntry entry);

    //注册服务
    protected abstract ClusterEntry register(NodeServer ns, String protocol, Service service);

    //注销服务
    protected abstract void deregister(NodeServer ns, String protocol, Service service);

    //格式: protocol:classtype-resourcename
    protected void updateSncpAddress(ClusterEntry entry) {
        if (application == null) {
            return;
        }
        Service service = entry.serviceRef.get();
        if (service == null) {
            return;
        }
        try {
            Set<InetSocketAddress> addrs = ClusterAgent.this.queryAddress(entry).join();
            SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
            rpcGroups.putClusterAddress(entry.resourceid, addrs);
        } catch (Exception e) {
            logger.log(Level.SEVERE, entry + " updateSncpAddress error", e);
        }
    }

    protected String urlEncode(String value) {
        return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    protected String generateApplicationServiceName() {
        return "node" + (appName == null || appName.isEmpty() ? "" : ("." + appName));
    }

    protected String generateApplicationServiceType() {
        return "nodes";
    }

    protected String generateApplicationServiceId() {
        return generateApplicationServiceName() + "." + this.nodeid;
    }

    protected String generateApplicationCheckName() {
        return "check-" + generateApplicationServiceName();
    }

    protected String generateApplicationCheckId() {
        return "check-" + generateApplicationServiceId();
    }

    protected String generateApplicationHost() {
        return this.appAddress.getHostString();
    }

    protected int generateApplicationPort() {
        return this.appAddress.getPort();
    }

    protected String serviceSeparator() {
        return ".";
    }

    public String generateSncpServiceName(String protocol, String restype, String resname) {
        return protocol.toLowerCase() + serviceSeparator() + restype + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    //也会提供给HttpMessageClusterAgent适用
    public String generateHttpServiceName(String protocol, String module, String resname) {
        return protocol.toLowerCase() + serviceSeparator() + module + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: protocol:classtype-resourcename
    protected String generateServiceName(NodeServer ns, String protocol, Service service) {
        if (protocol.toLowerCase().startsWith("http")) {  //HTTP使用RestService.name方式是为了与MessageClient中的module保持一致, 因为HTTP依靠的url中的module，无法知道Service类名
            String resname = Sncp.getResourceName(service);
            String module = Rest.getRestModule(service).toLowerCase();
            return protocol.toLowerCase() + serviceSeparator() + module + (resname.isEmpty() ? "" : ("-" + resname));
        }
        if (!Sncp.isSncpDyn(service)) {
            return protocol.toLowerCase() + serviceSeparator() + service.getClass().getName();
        }
        String resname = Sncp.getResourceName(service);
        return protocol.toLowerCase() + serviceSeparator() + Sncp.getResourceType(service).getName() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: protocol:classtype-resourcename:nodeid
    protected String generateServiceId(NodeServer ns, String protocol, Service service) {
        return generateServiceName(ns, protocol, service) + serviceSeparator() + this.nodeid;
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

        //serviceName+nodeid为主  服务的单个实例
        public String serviceid;

        //以协议+Rest资源名为主  服务类名
        public String serviceName;

        public final String resourceType;

        public final String resourceName;

        public final String resourceid;

        public String checkid;

        public String checkName;

        //http or sncp
        public String protocol;

        //TCP or UDP
        public String netProtocol;

        @ConvertDisabled
        public WeakReference<Service> serviceRef;

        public InetSocketAddress address;

        public boolean canceled;

        public ClusterEntry(NodeServer ns, String protocol, Service service) {
            this.serviceid = generateServiceId(ns, protocol, service);
            this.serviceName = generateServiceName(ns, protocol, service);
            this.checkid = generateCheckId(ns, protocol, service);
            this.checkName = generateCheckName(ns, protocol, service);
            Class restype = Sncp.getResourceType(service);
            this.resourceType = restype.getName();
            this.resourceName = Sncp.getResourceName(service);
            this.resourceid = Sncp.resourceid(resourceName, restype);
            this.protocol = protocol;
            InetSocketAddress addr = ns.getSocketAddress();
            String host = addr.getHostString();
            if ("0.0.0.0".equals(host)) {
                host = appAddress.getHostString();
                addr = new InetSocketAddress(host, addr.getPort());
            }
            this.address = addr;
            this.serviceRef = new WeakReference(service);
            Server server = ns.getServer();
            this.netProtocol = server instanceof SncpServer ? ((SncpServer) server).getNetprotocol() : "TCP";
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
