/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import static com.wentch.redkale.boot.Application.*;
import com.wentch.redkale.boot.ClassFilter.FilterEntry;
import com.wentch.redkale.net.sncp.ServiceWrapper;
import com.wentch.redkale.net.Server;
import com.wentch.redkale.net.sncp.Sncp;
import com.wentch.redkale.service.Service;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.http.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.source.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
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

    protected final boolean fine;

    protected final Application application;

    protected final ResourceFactory factory;

    protected final Server server;

    private String sncpGroup = null;  //当前Server的SNCP协议的组

    private String nodeProtocol = Sncp.DEFAULT_PROTOCOL;

    private InetSocketAddress sncpAddress; //HttpServer中的sncpAddress 为所属group对应的SncpServer, 为null表示只是单节点，没有分布式结构

    protected Consumer<ServiceWrapper> consumer;

    protected AnyValue nodeConf;

    protected final HashSet<String> sncpDefaultGroups = new LinkedHashSet<>();

    protected final List<Transport> sncpSameGroupTransports = new ArrayList<>();

    protected final List<Transport> sncpDiffGroupTransports = new ArrayList<>();

    protected final Set<ServiceWrapper> localServiceWrappers = new LinkedHashSet<>();

    protected final Set<ServiceWrapper> remoteServiceWrappers = new LinkedHashSet<>();

    public NodeServer(Application application, ResourceFactory factory, Server server) {
        this.application = application;
        this.factory = factory;
        this.server = server;
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.fine = logger.isLoggable(Level.FINE);
    }

    protected Consumer<Runnable> getExecutor() throws Exception {
        if (server == null) return null;
        final Field field = Server.class.getDeclaredField("context");
        field.setAccessible(true);
        return new Consumer<Runnable>() {

            private Context context;

            @Override
            public void accept(Runnable t) {
                if (context == null && server != null) {
                    try {
                        this.context = (Context) field.get(server);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Server (" + server.getSocketAddress() + ") cannot find Context", e);
                    }
                }
                context.submit(t);
            }

        };
    }

    public static <T extends NodeServer> NodeServer create(Class<T> clazz, Application application, AnyValue serconf) {
        try {
            return clazz.getConstructor(Application.class, AnyValue.class).newInstance(application, serconf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void init(AnyValue config) throws Exception {
        this.nodeConf = config == null ? AnyValue.create() : config;
        if (isSNCP()) { // SNCP协议
            String host = this.nodeConf.getValue("host", "0.0.0.0").replace("0.0.0.0", "");
            this.sncpAddress = new InetSocketAddress(host.isEmpty() ? application.localAddress.getHostAddress() : host, this.nodeConf.getIntValue("port"));
            this.sncpGroup = application.globalNodes.get(this.sncpAddress);
            if (this.sncpGroup == null) throw new RuntimeException("Server (" + String.valueOf(config).replaceAll("\\s+", " ") + ") not found <group> info");
            if (server != null) this.nodeProtocol = server.getProtocol();
        }
        initGroup();
        if (this.sncpAddress != null) this.factory.register(RESNAME_SERVER_ADDR, this.sncpAddress);
        if (this.sncpGroup != null) this.factory.register(RESNAME_SERVER_GROUP, this.sncpGroup);
        {
            //设置root文件夹
            String webroot = config.getValue("root", "root");
            File myroot = new File(webroot);
            if (!webroot.contains(":") && !webroot.startsWith("/")) {
                myroot = new File(System.getProperty(Application.RESNAME_APP_HOME), webroot);
            }
            final String homepath = myroot.getCanonicalPath();
            Server.loadLib(logger, config.getValue("lib", "") + ";" + homepath + "/lib/*;" + homepath + "/classes");
            if (server != null) server.init(config);
        }
        initResource();
        //prepare();
        ClassFilter<Servlet> servletFilter = createServletClassFilter();
        ClassFilter<Service> serviceFilter = createServiceClassFilter();
        long s = System.currentTimeMillis();
        if (servletFilter == null) {
            ClassFilter.Loader.load(application.getHome(), serviceFilter);
        } else {
            ClassFilter.Loader.load(application.getHome(), serviceFilter, servletFilter);
        }
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter); //必须在servlet之前
        loadServlet(servletFilter);
    }

    protected abstract void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception;

    private void initResource() {
        //---------------------------------------------------------------------------------------------
        final ResourceFactory regFactory = application.getResourceFactory();
        factory.add(DataSource.class, (ResourceFactory rf, final Object src, Field field) -> {
            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return;
                DataSource source = DataSourceFactory.create(rs.name());
                application.sources.add(source);
                regFactory.register(rs.name(), DataSource.class, source);
                if (factory.find(rs.name(), DataCacheListener.class) == null) {
                    Service cacheListenerService = Sncp.createLocalService(rs.name(), getExecutor(), DataCacheListenerService.class, this.sncpAddress, sncpDefaultGroups, sncpSameGroupTransports, sncpDiffGroupTransports);
                    regFactory.register(rs.name(), DataCacheListener.class, cacheListenerService);
                    ServiceWrapper wrapper = new ServiceWrapper(DataCacheListenerService.class, cacheListenerService, rs.name(), sncpGroup, sncpDefaultGroups, null);
                    localServiceWrappers.add(wrapper);
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

    private void initGroup() {
        final AnyValue[] services = this.nodeConf.getAnyValues("services");
        final String[] groups = services.length < 1 ? new String[]{""} : services[0].getValue("groups", "").split(";");
        this.sncpDefaultGroups.addAll(Arrays.asList(groups));
        if (!isSNCP()) {
            NodeSncpServer sncpServer = null;
            for (NodeServer node : application.servers) {
                if (!node.isSNCP()) continue;
                if (!this.sncpDefaultGroups.contains(node.sncpGroup)) continue;
                sncpServer = (NodeSncpServer) node;
                break;
            }
            if (sncpServer == null && (groups.length == 1 && groups[0].isEmpty())) {
                for (NodeServer node : application.servers) {
                    if (!node.isSNCP()) continue;
                    sncpServer = (NodeSncpServer) node;
                    break;
                }
            }
            if (sncpServer != null) {
                this.sncpAddress = sncpServer.getSncpAddress();
                this.sncpGroup = sncpServer.getSncpGroup();
                this.sncpDefaultGroups.clear();
                this.sncpDefaultGroups.addAll(sncpServer.sncpDefaultGroups);
                this.sncpSameGroupTransports.addAll(sncpServer.sncpSameGroupTransports);
                this.sncpDiffGroupTransports.addAll(sncpServer.sncpDiffGroupTransports);
                return;
            }
        }
        final Set<InetSocketAddress> sameGroupAddrs = application.findGlobalGroup(this.sncpGroup);
        final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
        for (String groupitem : groups) {
            final Set<InetSocketAddress> addrs = application.findGlobalGroup(groupitem);
            if (addrs == null || groupitem.equals(this.sncpGroup)) continue;
            diffGroupAddrs.put(groupitem, addrs);
        }
        if (sameGroupAddrs != null) {
            sameGroupAddrs.remove(this.sncpAddress);
            for (InetSocketAddress iaddr : sameGroupAddrs) {
                sncpSameGroupTransports.add(loadTransport(this.sncpGroup, getNodeProtocol(), iaddr));
            }
        }
        diffGroupAddrs.forEach((k, v) -> sncpDiffGroupTransports.add(loadTransport(k, getNodeProtocol(), v)));
    }

    @SuppressWarnings("unchecked")
    protected void loadService(ClassFilter serviceFilter) throws Exception {
        if (serviceFilter == null) return;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        final Set<FilterEntry<Service>> entrys = serviceFilter.getFilterEntrys();
        ResourceFactory regFactory = isSNCP() ? application.getResourceFactory() : factory;
        for (FilterEntry<Service> entry : entrys) { //service实现类
            final Class<? extends Service> type = entry.getType();
            if (type.isInterface()) continue;
            if (Modifier.isFinal(type.getModifiers())) continue;
            if (!Modifier.isPublic(type.getModifiers())) continue;
            if (Modifier.isAbstract(type.getModifiers())) continue;
            if (type.getAnnotation(Ignore.class) != null) continue;
            if (!isSNCP() && factory.find(entry.getName(), type) != null) continue;
            final Set<InetSocketAddress> sameGroupAddrs = new LinkedHashSet<>();
            Set<InetSocketAddress> sg = application.findGlobalGroup(this.sncpGroup);
            if (sg != null) sameGroupAddrs.addAll(sg);
            final Map<String, Set<InetSocketAddress>> diffGroupAddrs = new HashMap<>();
            final HashSet<String> groups = entry.getGroups();
            for (String g : groups) {
                if (g.isEmpty()) continue;
                Set<InetSocketAddress> set = application.findGlobalGroup(g);
                if (set == null) throw new RuntimeException(type.getName() + " has illegal group (" + groups + ")");
                if (!g.equals(this.sncpGroup)) {
                    diffGroupAddrs.put(g, set);
                }
            }
            List<Transport> diffGroupTransports = new ArrayList<>();
            diffGroupAddrs.forEach((k, v) -> diffGroupTransports.add(loadTransport(k, server.getProtocol(), v)));

            ServiceWrapper wrapper;
            if ((sameGroupAddrs.isEmpty() && diffGroupAddrs.isEmpty()) || sameGroupAddrs.contains(this.sncpAddress) || type.getAnnotation(LocalService.class) != null) { //本地模式
                sameGroupAddrs.remove(this.sncpAddress);
                List<Transport> sameGroupTransports = new ArrayList<>();
                for (InetSocketAddress iaddr : sameGroupAddrs) {
                    Set<InetSocketAddress> tset = new HashSet<>();
                    tset.add(iaddr);
                    sameGroupTransports.add(loadTransport(this.sncpGroup, server.getProtocol(), tset));
                }
                Service service = Sncp.createLocalService(entry.getName(), getExecutor(), type, this.sncpAddress, groups, sameGroupTransports, diffGroupTransports);
                wrapper = new ServiceWrapper(type, service, this.sncpGroup, entry);
                if (fine) logger.fine("[" + Thread.currentThread().getName() + "] Load " + service);
            } else {
                sameGroupAddrs.remove(this.sncpAddress);
                StringBuilder g = new StringBuilder();
                diffGroupAddrs.forEach((k, v) -> {
                    if (g.length() > 0) g.append(';');
                    g.append(k);
                    sameGroupAddrs.addAll(v);
                });
                if (sameGroupAddrs.isEmpty()) throw new RuntimeException(type.getName() + " has no remote address on group (" + groups + ")");
                Service service = Sncp.createRemoteService(entry.getName(), getExecutor(), type, this.sncpAddress, groups, loadTransport(g.toString(), server.getProtocol(), sameGroupAddrs));
                wrapper = new ServiceWrapper(type, service, "", entry);
                if (fine) logger.fine("[" + Thread.currentThread().getName() + "] Load " + service);
            }
            if (factory.find(wrapper.getName(), wrapper.getType()) == null) {
                regFactory.register(wrapper.getName(), wrapper.getType(), wrapper.getService());
                if (wrapper.getService() instanceof DataSource) {
                    regFactory.register(wrapper.getName(), DataSource.class, wrapper.getService());
                } else if (wrapper.getService() instanceof DataCacheListener) {
                    regFactory.register(wrapper.getName(), DataCacheListener.class, wrapper.getService());
                } else if (wrapper.getService() instanceof DataSQLListener) {
                    regFactory.register(wrapper.getName(), DataSQLListener.class, wrapper.getService());
                } else if (wrapper.getService() instanceof WebSocketNode) {
                    regFactory.register(wrapper.getName(), WebSocketNode.class, wrapper.getService());
                }
                if (wrapper.isRemote()) {
                    remoteServiceWrappers.add(wrapper);
                } else {
                    localServiceWrappers.add(wrapper);
                    if (consumer != null) consumer.accept(wrapper);
                }
            } else if (isSNCP()) {
                throw new RuntimeException(ServiceWrapper.class.getSimpleName() + "(class:" + type.getName() + ", name:" + entry.getName() + ", group:" + groups + ") is repeat.");
            }
        }
        application.servicecdl.countDown();
        application.servicecdl.await();

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        //---------------- inject ----------------
        new HashSet<>(localServiceWrappers).forEach(y -> {
            factory.inject(y.getService());
        });
        remoteServiceWrappers.forEach(y -> {
            factory.inject(y.getService());
        });
        //----------------- init -----------------
        localServiceWrappers.parallelStream().forEach(y -> {
            long s = System.currentTimeMillis();
            y.getService().init(y.getConf());
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(threadName).append("LocalService(").append(y.getType()).append(':').append(y.getName()).append(") init ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
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
                    transport = new Transport(group + "_" + application.transports.size(), protocol, application.getWatchFactory(), 32, addrs);
                    application.transports.add(transport);
                }
            }
        }
        return transport;
    }

    protected abstract ClassFilter<Servlet> createServletClassFilter();

    protected ClassFilter<Service> createServiceClassFilter() {
        return createClassFilter(this.sncpGroup, null, Service.class, Annotation.class, "services", "service");
    }

    protected ClassFilter createClassFilter(final String localGroup, Class<? extends Annotation> ref,
            Class inter, Class<? extends Annotation> ref2, String properties, String property) {
        ClassFilter cf = new ClassFilter(ref, inter, null);
        if (properties == null && properties == null) return cf;
        if (this.nodeConf == null) return cf;
        AnyValue[] proplist = this.nodeConf.getAnyValues(properties);
        if (proplist == null || proplist.length < 1) return cf;
        cf = null;
        for (AnyValue list : proplist) {
            DefaultAnyValue prop = null;
            String sc = list.getValue("groups");
            if (sc == null) sc = localGroup;
            if (sc != null) {
                prop = new AnyValue.DefaultAnyValue();
                prop.addValue("groups", sc);
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

    public abstract InetSocketAddress getSocketAddress();

    public boolean isSNCP() {
        return false;
    }

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
        localServiceWrappers.forEach(y -> {
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

}
