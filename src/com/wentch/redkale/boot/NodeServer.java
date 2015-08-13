/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import static com.wentch.redkale.boot.Application.*;
import com.wentch.redkale.net.sncp.ServiceWrapper;
import com.wentch.redkale.net.Server;
import com.wentch.redkale.net.sncp.Sncp;
import com.wentch.redkale.service.Service;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.util.Ignore;
import com.wentch.redkale.boot.ClassFilter.FilterEntry;
import com.wentch.redkale.net.*;
import com.wentch.redkale.source.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;
import javax.annotation.*;

/**
 *
 * @author zhangjx
 */
public abstract class NodeServer {

    public static final String LINE_SEPARATOR = "\r\n";

    protected final Logger logger;

    protected final Application application;

    protected final ResourceFactory factory;

    private final CountDownLatch servicecdl;

    private final Server server;

    private InetSocketAddress sncpAddress; //HttpServer中的sncpAddress 为所属group对应的SncpServer, 为null表示只是单节点，没有分布式结构

    private String sncpGroup = "";  //当前Server的SNCP协议的组

    private AnyValue nodeConf;

    private String nodeProtocol = Sncp.DEFAULT_PROTOCOL;

    protected Consumer<ServiceWrapper> consumer;

    protected final List<Transport> nodeSameGroupTransports = new ArrayList<>();

    protected final List<Transport> nodeDiffGroupTransports = new ArrayList<>();

    protected final Set<ServiceWrapper> localServices = new LinkedHashSet<>();

    protected final Set<ServiceWrapper> remoteServices = new LinkedHashSet<>();

    public NodeServer(Application application, ResourceFactory factory, CountDownLatch servicecdl, Server server) {
        this.application = application;
        this.factory = factory;
        this.servicecdl = servicecdl;
        this.server = server;
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
    }

    protected abstract void prepare(final AnyValue config) throws Exception;

    public void init(AnyValue config) throws Exception {
        this.nodeConf = config == null ? AnyValue.create() : config;
        if (isSNCP()) { // SNCP协议
            String host = this.nodeConf.getValue("host", "0.0.0.0").replace("0.0.0.0", "");
            this.sncpAddress = new InetSocketAddress(host.isEmpty() ? application.localAddress.getHostAddress() : host, this.nodeConf.getIntValue("port"));
            this.sncpGroup = application.globalNodes.getOrDefault(this.sncpAddress, "");
            if (server != null) this.nodeProtocol = server.getProtocol();
        } else { // HTTP协议
            this.sncpAddress = null;
            this.sncpGroup = "";
            this.nodeProtocol = Sncp.DEFAULT_PROTOCOL;
            String mbgroup = this.nodeConf.getValue("group", "");
            NodeServer sncpServer = null;  //有匹配的就取匹配的， 没有且SNCP只有一个，则取此SNCP。
            for (NodeServer ns : application.servers) {
                if (!ns.isSNCP()) continue;
                if (sncpServer == null) sncpServer = ns;
                if (ns.getSncpGroup().equals(mbgroup)) {
                    sncpServer = ns;
                    break;
                }
            }
            if (sncpServer != null) {
                this.sncpAddress = sncpServer.getSncpAddress();
                this.sncpGroup = sncpServer.getSncpGroup();
                this.nodeProtocol = sncpServer.getNodeProtocol();
            }
        }
        if (this.sncpAddress != null) { // 无分布式结构下 HTTP协议的sncpAddress 为 null
            this.factory.register(RESNAME_SNCP_NODE, SocketAddress.class, this.sncpAddress);
            this.factory.register(RESNAME_SNCP_NODE, InetSocketAddress.class, this.sncpAddress);
            this.factory.register(RESNAME_SNCP_NODE, String.class, this.sncpAddress.getAddress().getHostAddress());
            this.factory.register(RESNAME_SNCP_GROUP, this.sncpGroup);
        }
        {
            //设置root文件夹
            String webroot = config.getValue("root", "root");
            File myroot = new File(webroot);
            if (!webroot.contains(":") && !webroot.startsWith("/")) {
                myroot = new File(System.getProperty(Application.RESNAME_HOME), webroot);
            }
            final String homepath = myroot.getCanonicalPath();
            Server.loadLib(logger, config.getValue("lib", "") + ";" + homepath + "/lib/*;" + homepath + "/classes");
            if (server != null) server.init(config);
        }
        initResource();
        prepare(config);
    }

    private void initResource() {
        final List<Transport>[] transportses = parseTransport(this.nodeConf.getValue("group", "").split(";"));
        this.nodeSameGroupTransports.addAll(transportses[0]);
        this.nodeDiffGroupTransports.addAll(transportses[1]);

        //---------------------------------------------------------------------------------------------
        final ResourceFactory regFactory = application.factory;
        factory.add(DataSource.class, (ResourceFactory rf, final Object src, Field field) -> {
            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return;
                DataSource source = DataSourceFactory.create(rs.name());
                application.sources.add(source);
                regFactory.register(rs.name(), DataSource.class, source);
                Class<? extends Service> sc = (Class<? extends Service>) application.dataCacheListenerClass;
                if (sc != null) {
                    Service cacheListenerService = Sncp.createLocalService(rs.name(), sc, this.sncpAddress, nodeSameGroupTransports, nodeDiffGroupTransports);
                    regFactory.register(rs.name(), DataCacheListener.class, cacheListenerService);
                    ServiceWrapper wrapper = new ServiceWrapper(sc, cacheListenerService, sncpGroup, rs.name(), null);
                    localServices.add(wrapper);
                    if (consumer != null) consumer.accept(wrapper);
                    rf.inject(cacheListenerService);
                }
                field.set(src, source);
                rf.inject(source); // 给 "datasource.nodeid" 赋值
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject error", e);
            }
        });
    }

    protected List<Transport>[] parseTransport(final String[] groups) {
        final Set<InetSocketAddress> sameGroupAddrs = application.findGlobalGroup(this.sncpGroup);
        final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
        for (String groupitem : groups) {
            final Set<InetSocketAddress> addrs = application.findGlobalGroup(groupitem);
            if (addrs == null || groupitem.equals(this.sncpGroup)) continue;
            diffGroupAddrs.put(groupitem, addrs);
        }
        final List<Transport> sameGroupTransports0 = new ArrayList<>();
        if (sameGroupAddrs != null) {
            sameGroupAddrs.remove(this.sncpAddress);
            for (InetSocketAddress iaddr : sameGroupAddrs) {
                sameGroupTransports0.add(loadTransport(this.sncpGroup, getNodeProtocol(), iaddr));
            }
        }
        final List<Transport> diffGroupTransports0 = new ArrayList<>();
        diffGroupAddrs.forEach((k, v) -> diffGroupTransports0.add(loadTransport(k, getNodeProtocol(), v)));
        return new List[]{sameGroupTransports0, diffGroupTransports0};
    }

    public abstract InetSocketAddress getSocketAddress();

    public abstract boolean isSNCP();

    public InetSocketAddress getSncpAddress() {
        return sncpAddress;
    }

    public String getSncpGroup() {
        return sncpGroup;
    }

    public String getNodeProtocol() {
        return nodeProtocol;
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() throws IOException {
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        localServices.forEach(y -> {
            long s = System.currentTimeMillis();
            y.getService().destroy(y.getConf());
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append("LocalService(").append(y.getType()).append(':').append(y.getName()).append(") destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
        server.shutdown();
    }

    protected Transport loadTransport(String group, String protocol, InetSocketAddress addr) {
        if (addr == null) return null;
        Set<InetSocketAddress> set = new HashSet<>();
        set.add(addr);
        return loadTransport(group, protocol, set);
    }

    protected Transport loadTransport(String group, String protocol, Set<InetSocketAddress> addrs) {
        Transport transport = null;
        if (!addrs.isEmpty()) {
            synchronized (application.transports) {
                for (Transport tran : application.transports) {
                    if (tran.match(addrs)) {
                        transport = tran;
                        break;
                    }
                }
                if (transport == null) {
                    transport = new Transport(group + "_" + application.transports.size(), protocol, application.watch, 32, addrs);
                    application.transports.add(transport);
                }
            }
        }
        return transport;
    }

    @SuppressWarnings("unchecked")
    protected void loadService(ClassFilter serviceFilter) throws Exception {
        if (serviceFilter == null) return;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        final Set<FilterEntry<Service>> entrys = serviceFilter.getFilterEntrys();
        final String defgroups = nodeConf == null ? "" : nodeConf.getValue("group", ""); //Server节点获取group信息
        ResourceFactory regFactory = isSNCP() ? application.factory : factory;
        for (FilterEntry<Service> entry : entrys) { //service实现类
            final Class<? extends Service> type = entry.getType();
            if (type.isInterface()) continue;
            if (Modifier.isFinal(type.getModifiers())) continue;
            if (!Modifier.isPublic(type.getModifiers())) continue;
            if (Modifier.isAbstract(type.getModifiers())) continue;
            if (type.getAnnotation(Ignore.class) != null) continue;
            if (!isSNCP() && factory.find(entry.getName(), type) != null) continue;
            String groups = entry.getGroup();
            if (groups == null || groups.isEmpty()) groups = defgroups;
            final Set<InetSocketAddress> sameGroupAddrs = new LinkedHashSet<>();
            final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
            for (String g : groups.split(";")) {
                if (g.isEmpty()) continue;
                Set<InetSocketAddress> set = application.findGlobalGroup(g);
                if (set == null) throw new RuntimeException(type.getName() + " has illegal group (" + groups + ")");
                if (g.equals(this.sncpGroup)) {
                    sameGroupAddrs.addAll(set);
                } else {
                    diffGroupAddrs.put(g, set);
                }
            }
            final boolean localable = this.sncpAddress == null || sameGroupAddrs.contains(this.sncpAddress);
            Service service;

            List<Transport> diffGroupTransports = new ArrayList<>();
            diffGroupAddrs.forEach((k, v) -> diffGroupTransports.add(loadTransport(k, server.getProtocol(), v)));

            if (localable || (sameGroupAddrs.isEmpty() && diffGroupTransports.isEmpty())) {
                sameGroupAddrs.remove(this.sncpAddress);
                List<Transport> sameGroupTransports = new ArrayList<>();
                for (InetSocketAddress iaddr : sameGroupAddrs) {
                    Set<InetSocketAddress> tset = new HashSet<>();
                    tset.add(iaddr);
                    sameGroupTransports.add(loadTransport(this.sncpGroup, server.getProtocol(), tset));
                }
                service = Sncp.createLocalService(entry.getName(), type, this.sncpAddress, sameGroupTransports, diffGroupTransports);
            } else {
                StringBuilder g = new StringBuilder(this.sncpGroup);
                diffGroupAddrs.forEach((k, v) -> {
                    if (g.length() > 0) g.append(';');
                    g.append(k);
                    sameGroupAddrs.addAll(v);
                });
                if (sameGroupAddrs.isEmpty()) throw new RuntimeException(type.getName() + " has no remote address on group (" + groups + ")");
                service = Sncp.createRemoteService(entry.getName(), type, this.sncpAddress, loadTransport(g.toString(), server.getProtocol(), sameGroupAddrs));
            }
            ServiceWrapper wrapper = new ServiceWrapper(type, service, entry);
            if (factory.find(wrapper.getName(), wrapper.getType()) == null) {
                regFactory.register(wrapper.getName(), wrapper.getType(), wrapper.getService());
                if (wrapper.isRemote()) {
                    remoteServices.add(wrapper);
                } else {
                    localServices.add(wrapper);
                    if (consumer != null) consumer.accept(wrapper);
                }
            } else if (isSNCP()) {
                throw new RuntimeException(ServiceWrapper.class.getSimpleName() + "(class:" + type.getName() + ", name:" + entry.getName() + ", group:" + groups + ") is repeat.");
            }
        }
        servicecdl.countDown();
        servicecdl.await();

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        //---------------- inject ----------------
        new HashSet<>(localServices).forEach(y -> {
            factory.inject(y.getService());
        });
        remoteServices.forEach(y -> {
            factory.inject(y.getService());
        });
        //----------------- init -----------------
        localServices.parallelStream().forEach(y -> {
            long s = System.currentTimeMillis();
            y.getService().init(y.getConf());
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(threadName).append("LocalService(").append(y.getType()).append(':').append(y.getName()).append(") init ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
    }

    protected ClassFilter<Service> createServiceClassFilter(final AnyValue config) {
        return createClassFilter(this.sncpGroup, config, null, Service.class, Annotation.class, "services", "service");
    }

    protected static ClassFilter createClassFilter(final String localGroup, final AnyValue config, Class<? extends Annotation> ref,
            Class inter, Class<? extends Annotation> ref2, String properties, String property) {
        ClassFilter cf = new ClassFilter(ref, inter, null);
        if (properties == null && properties == null) return cf;
        if (config == null) return cf;
        AnyValue[] proplist = config.getAnyValues(properties);
        if (proplist == null || proplist.length < 1) return cf;
        cf = null;
        for (AnyValue list : proplist) {
            DefaultAnyValue prop = null;
            String sc = list.getValue("group", "");
            if (!sc.isEmpty()) {
                prop = new AnyValue.DefaultAnyValue();
                prop.addValue("group", sc);
            }
            ClassFilter filter = new ClassFilter(ref, inter, prop);
            for (AnyValue av : list.getAnyValues(property)) {
                filter.filter(av, av.getValue("value"), false);
            }
            if (list.getBoolValue("autoload", true)) {
                String includes = list.getValue("includes", "");
                String excludes = list.getValue("excludes", "");
                filter.setIncludePatterns(includes.split(";"));
                filter.setExcludePatterns(excludes.split(";"));
            } else {
                if (ref2 == null || ref2 == Annotation.class) {  //service如果是autoload=false则不需要加载
                    filter.setRefused(true);
                } else if (ref2 != Annotation.class) {
                    filter.setAnnotationClass(ref2);
                }
            }
            cf = (cf == null) ? filter : cf.or(filter);
        }
        return cf;
    }

}
