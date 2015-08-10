/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

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
import java.net.InetSocketAddress;
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

    protected AnyValue servconf;

    protected InetSocketAddress servaddr;

    protected String nodeGroup = "";

    protected Consumer<ServiceWrapper> consumer;

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
        this.servconf = config == null ? new AnyValue.DefaultAnyValue() : config;
        //设置root文件夹
        String webroot = config.getValue("root", "root");
        File myroot = new File(webroot);
        if (!webroot.contains(":") && !webroot.startsWith("/")) {
            myroot = new File(System.getProperty(Application.RESNAME_HOME), webroot);
        }
        final String homepath = myroot.getCanonicalPath();
        Server.loadLib(logger, config.getValue("lib", "") + ";" + homepath + "/lib/*;" + homepath + "/classes");
        if (server != null) server.init(config);
        initResource();
        prepare(config);
    }

    private void initResource() {
        final String defgroup = servconf.getValue("group", ""); //Server节点获取group信息
        final List<Transport>[] transportses = parseTransport(defgroup, this.nodeGroup, this.servaddr);
        final List<Transport> sameGroupTransports = transportses[0];
        final List<Transport> diffGroupTransports = transportses[1];
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
                    Service cacheListenerService = Sncp.createLocalService(rs.name(), sc, this.servaddr, sameGroupTransports, diffGroupTransports);
                    regFactory.register(rs.name(), DataCacheListener.class, cacheListenerService);
                    ServiceWrapper wrapper = new ServiceWrapper(sc, cacheListenerService, nodeGroup, rs.name(), null);
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

    protected List<Transport>[] parseTransport(final String group, final String localGroup, final InetSocketAddress addr) {
        final Set<InetSocketAddress> sameGroupAddrs = new LinkedHashSet<>();
        final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
        for (String str : group.split(";")) {
            application.addrGroups.forEach((k, v) -> {
                if (v.equals(str)) {
                    if (v.equals(localGroup)) {
                        sameGroupAddrs.add(k);
                    } else {
                        Set<InetSocketAddress> set = diffGroupAddrs.get(v);
                        if (set == null) {
                            set = new LinkedHashSet<>();
                            diffGroupAddrs.put(v, set);
                        }
                        set.add(k);
                    }
                }
            });
        }
        sameGroupAddrs.remove(addr);
        final List<Transport> sameGroupTransports = new ArrayList<>();
        for (InetSocketAddress iaddr : sameGroupAddrs) {
            Set<InetSocketAddress> tset = new HashSet<>();
            tset.add(iaddr);
            sameGroupTransports.add(loadTransport(localGroup, server.getProtocol(), tset));
        }
        final List<Transport> diffGroupTransports = new ArrayList<>();
        diffGroupAddrs.forEach((k, v) -> diffGroupTransports.add(loadTransport(k, server.getProtocol(), v)));
        return new List[]{sameGroupTransports, diffGroupTransports};
    }

    public abstract InetSocketAddress getSocketAddress();

    public abstract boolean isSNCP();

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
                sb.append("LocalServices(").append(y.getType()).append(':').append(y.getName()).append(") destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
        server.shutdown();
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
        final String defgroup = servconf == null ? "" : servconf.getValue("group", ""); //Server节点获取group信息
        ResourceFactory regFactory = isSNCP() ? application.factory : factory;
        for (FilterEntry<Service> entry : entrys) { //service实现类
            final Class<? extends Service> type = entry.getType();
            if (type.isInterface()) continue;
            if (Modifier.isFinal(type.getModifiers())) continue;
            if (!Modifier.isPublic(type.getModifiers())) continue;
            if (Modifier.isAbstract(type.getModifiers())) continue;
            if (type.getAnnotation(Ignore.class) != null) continue;
            if (!isSNCP() && factory.find(entry.getName(), type) != null) continue;
            String group = entry.getGroup();
            if (group == null || group.isEmpty()) group = defgroup;
            final Set<InetSocketAddress> sameGroupAddrs = new LinkedHashSet<>();
            final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
            for (String str : group.split(";")) {
                application.addrGroups.forEach((k, v) -> {
                    if (v.equals(str)) {
                        if (v.equals(this.nodeGroup)) {
                            sameGroupAddrs.add(k);
                        } else {
                            Set<InetSocketAddress> set = diffGroupAddrs.get(v);
                            if (set == null) {
                                set = new LinkedHashSet<>();
                                diffGroupAddrs.put(v, set);
                            }
                            set.add(k);
                        }
                    }
                });
            }
            final boolean localable = sameGroupAddrs.contains(this.servaddr);
            Service service;

            List<Transport> diffGroupTransports = new ArrayList<>();
            diffGroupAddrs.forEach((k, v) -> diffGroupTransports.add(loadTransport(k, server.getProtocol(), v)));

            if (localable || (sameGroupAddrs.isEmpty() && diffGroupTransports.isEmpty())) {
                sameGroupAddrs.remove(this.servaddr);
                List<Transport> sameGroupTransports = new ArrayList<>();
                for (InetSocketAddress iaddr : sameGroupAddrs) {
                    Set<InetSocketAddress> tset = new HashSet<>();
                    tset.add(iaddr);
                    sameGroupTransports.add(loadTransport(this.nodeGroup, server.getProtocol(), tset));
                }
                service = Sncp.createLocalService(entry.getName(), type, this.servaddr, sameGroupTransports, diffGroupTransports);
            } else {
                StringBuilder g = new StringBuilder(this.nodeGroup);
                diffGroupAddrs.forEach((k, v) -> {
                    if (g.length() > 0) g.append(';');
                    g.append(k);
                    sameGroupAddrs.addAll(v);
                });
                if (sameGroupAddrs.isEmpty()) throw new RuntimeException(type + ":" + group);
                service = Sncp.createRemoteService(entry.getName(), type, this.servaddr, loadTransport(g.toString(), server.getProtocol(), sameGroupAddrs));
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
                throw new RuntimeException(ServiceWrapper.class.getSimpleName() + "(class:" + type.getName() + ", name:" + entry.getName() + ", group:" + group + ") is repeat.");
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
        return createClassFilter(this.nodeGroup, config, null, Service.class, Annotation.class, "services", "service");
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
