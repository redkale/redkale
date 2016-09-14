/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.persistence.Transient;
import static org.redkale.boot.Application.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.net.*;
import org.redkale.net.http.WebSocketNode;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;

/**
 * Server节点的初始化配置类
 *
 *
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public abstract class NodeServer {

    //INFO日志的换行符
    public static final String LINE_SEPARATOR = "\r\n";

    //日志输出对象
    protected final Logger logger;

    //日志是否为FINE级别
    protected final boolean fine;

    //日志是否为FINER级别
    protected final boolean finer;

    //日志是否为FINEST级别
    protected final boolean finest;

    //进程主类
    protected final Application application;

    //依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    //当前Server对象
    protected final Server server;

    //当前Server的SNCP协议的组
    private String sncpGroup = null;

    //SNCP服务的地址， 非SNCP为null
    private InetSocketAddress sncpAddress;

    //加载Service时的处理函数
    protected Consumer<ServiceWrapper> consumer;

    //server节点的配置
    protected AnyValue serverConf;

    //加载server节点后的拦截器
    protected NodeInterceptor interceptor;

    //供interceptor使用的Service对象集合
    protected final Set<NodeInterceptor.InterceptorServiceWrapper> interceptorServiceWrappers = new LinkedHashSet<>();

    //本地模式的Service对象集合
    protected final Set<ServiceWrapper> localServiceWrappers = new LinkedHashSet<>();

    //远程模式的Service对象集合
    protected final Set<ServiceWrapper> remoteServiceWrappers = new LinkedHashSet<>();

    public NodeServer(Application application, Server server) {
        this.application = application;
        this.resourceFactory = application.getResourceFactory().createChild();
        this.server = server;
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.fine = logger.isLoggable(Level.FINE);
        this.finer = logger.isLoggable(Level.FINER);
        this.finest = logger.isLoggable(Level.FINEST);
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
                if (context == null) {
                    t.run();
                } else {
                    context.submit(t);
                }
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
        this.serverConf = config == null ? AnyValue.create() : config;
        if (isSNCP()) { // SNCP协议
            String host = this.serverConf.getValue("host", "0.0.0.0").replace("0.0.0.0", "");
            this.sncpAddress = new InetSocketAddress(host.isEmpty() ? application.localAddress.getHostAddress() : host, this.serverConf.getIntValue("port"));
            this.sncpGroup = application.globalNodes.get(this.sncpAddress);
            //单向SNCP服务不需要对等group
            //if (this.sncpGroup == null) throw new RuntimeException("Server (" + String.valueOf(config).replaceAll("\\s+", " ") + ") not found <group> info");
        }
        //单点服务不会有 sncpAddress、sncpGroup
        if (this.sncpAddress != null) this.resourceFactory.register(RESNAME_SERVER_ADDR, this.sncpAddress);
        if (this.sncpGroup != null) this.resourceFactory.register(RESNAME_SERVER_GROUP, this.sncpGroup);
        {
            //设置root文件夹
            String webroot = this.serverConf.getValue("root", "root");
            File myroot = new File(webroot);
            if (!webroot.contains(":") && !webroot.startsWith("/")) {
                myroot = new File(System.getProperty(Application.RESNAME_APP_HOME), webroot);
            }

            resourceFactory.register(Server.RESNAME_SERVER_ROOT, String.class, myroot.getCanonicalPath());
            resourceFactory.register(Server.RESNAME_SERVER_ROOT, File.class, myroot.getCanonicalFile());
            resourceFactory.register(Server.RESNAME_SERVER_ROOT, Path.class, myroot.toPath());

            final String homepath = myroot.getCanonicalPath();
            //加入指定的classpath
            Server.loadLib(logger, this.serverConf.getValue("lib", "").replace("${APP_HOME}", homepath) + ";" + homepath + "/lib/*;" + homepath + "/classes");
        }
        //必须要进行初始化， 构建Service时需要使用Context中的ExecutorService
        server.init(this.serverConf);

        initResource(); //给 DataSource、CacheSource 注册依赖注入时的监听回调事件。
        String interceptorClass = this.serverConf.getValue("interceptor", "");
        if (!interceptorClass.isEmpty()) {
            Class clazz = Class.forName(interceptorClass); 
            this.interceptor = (NodeInterceptor) clazz.newInstance();
        }

        ClassFilter<Servlet> servletFilter = createServletClassFilter();
        ClassFilter<Service> serviceFilter = createServiceClassFilter();
        long s = System.currentTimeMillis();
        if (servletFilter == null) {
            ClassFilter.Loader.load(application.getHome(), serverConf.getValue("excludelibs", "").split(";"), serviceFilter);
        } else {
            ClassFilter.Loader.load(application.getHome(), serverConf.getValue("excludelibs", "").split(";"), serviceFilter, servletFilter);
        }
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter); //必须在servlet之前
        loadServlet(servletFilter);

        if (this.interceptor != null) this.resourceFactory.inject(this.interceptor);
    }

    protected abstract void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception;

    private void initResource() {
        final NodeServer self = this;
        //---------------------------------------------------------------------------------------------
        final ResourceFactory appResFactory = application.getResourceFactory();
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不得注入 DataSource
                DataSource source = new DataDefaultSource(resourceName);
                application.dataSources.add(source);
                appResFactory.register(resourceName, DataSource.class, source);

                SncpClient client = Sncp.getSncpClient((Service) src);
                Transport sameGroupTransport = Sncp.getSameGroupTransport((Service) src);
                List<Transport> diffGroupTransports = Arrays.asList(Sncp.getDiffGroupTransports((Service) src));
                final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                if ((src instanceof DataSource) && sncpAddr != null && resourceFactory.find(resourceName, DataCacheListener.class) == null) { //只有DataSourceService 才能赋值 DataCacheListener
                    Service cacheListenerService = Sncp.createLocalService(resourceName, getExecutor(), appResFactory, DataCacheListenerService.class, sncpAddr, sameGroupTransport, diffGroupTransports);
                    appResFactory.register(resourceName, DataCacheListener.class, cacheListenerService);
                    final NodeSncpServer sncpServer = application.findNodeSncpServer(sncpAddr);
                    Set<String> gs = application.findSncpGroups(sameGroupTransport, diffGroupTransports);
                    ServiceWrapper wrapper = new ServiceWrapper(DataCacheListenerService.class, cacheListenerService, resourceName, sncpServer.getSncpGroup(), gs, null);
                    localServiceWrappers.add(wrapper);
                    sncpServer.consumerAccept(wrapper);
                    rf.inject(cacheListenerService, self);
                    logger.info("[" + Thread.currentThread().getName() + "] Load Service " + wrapper.getService());
                }
                field.set(src, source);
                rf.inject(source, self); // 给 "datasource.nodeid" 赋值;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject error", e);
            }
        }, DataSource.class);
        resourceFactory.register((ResourceFactory rf, final Object src, final String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不得注入 CacheSource   

                SncpClient client = Sncp.getSncpClient((Service) src);
                Transport sameGroupTransport = Sncp.getSameGroupTransport((Service) src);
                Transport[] dts = Sncp.getDiffGroupTransports((Service) src);
                List<Transport> diffGroupTransports = dts == null ? new ArrayList<>() : Arrays.asList(dts);
                final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                final CacheSourceService source = Sncp.createLocalService(resourceName, getExecutor(), appResFactory, CacheSourceService.class, sncpAddr, sameGroupTransport, diffGroupTransports);
                Type genericType = field.getGenericType();
                ParameterizedType pt = (genericType instanceof ParameterizedType) ? (ParameterizedType) genericType : null;
                Type valType = pt == null ? null : pt.getActualTypeArguments()[1];
                source.setStoreType(pt == null ? Serializable.class : (Class) pt.getActualTypeArguments()[0], valType instanceof Class ? (Class) valType : Object.class);
                if (field.getAnnotation(Transient.class) != null) source.setNeedStore(false); //必须在setStoreType之后
                application.cacheSources.add(source);
                appResFactory.register(resourceName, genericType, source);
                appResFactory.register(resourceName, CacheSource.class, source);
                field.set(src, source);
                rf.inject(source, self); //
                ((Service) source).init(null);

                if ((src instanceof WebSocketNodeService) && sncpAddr != null) { //只有WebSocketNodeService的服务才需要给SNCP服务注入CacheSourceService
                    NodeSncpServer sncpServer = application.findNodeSncpServer(sncpAddr);
                    Set<String> gs = application.findSncpGroups(sameGroupTransport, diffGroupTransports);
                    ServiceWrapper wrapper = new ServiceWrapper(CacheSourceService.class, (Service) source, resourceName, sncpServer.getSncpGroup(), gs, null);
                    sncpServer.getSncpServer().addSncpServlet(wrapper);
                    logger.info("[" + Thread.currentThread().getName() + "] Load Service " + wrapper.getService());
                }
                logger.info("[" + Thread.currentThread().getName() + "] Load Source " + source);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject error", e);
            }
        }, CacheSource.class);
    }

    @SuppressWarnings("unchecked")
    protected void loadService(ClassFilter serviceFilter) throws Exception {
        if (serviceFilter == null) return;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        final Set<FilterEntry<Service>> entrys = serviceFilter.getAllFilterEntrys();
        ResourceFactory regFactory = isSNCP() ? application.getResourceFactory() : resourceFactory;

        for (FilterEntry<Service> entry : entrys) { //service实现类
            final Class<? extends Service> type = entry.getType();
            if (Modifier.isFinal(type.getModifiers())) continue; //修饰final的类跳过
            if (!Modifier.isPublic(type.getModifiers())) continue;
            if (entry.isExpect()) {
                if (Modifier.isAbstract(type.getModifiers())) continue; //修饰abstract的类跳过
                if (DataSource.class.isAssignableFrom(type)) continue;
                if (CacheSource.class.isAssignableFrom(type)) continue;
                if (DataSQLListener.class.isAssignableFrom(type)) continue;
                if (DataCacheListener.class.isAssignableFrom(type)) continue;
                if (WebSocketNode.class.isAssignableFrom(type)) continue;
            }
            if (entry.getName().contains("$")) throw new RuntimeException("<name> value cannot contains '$' in " + entry.getProperty());
            if (resourceFactory.find(entry.getName(), type) != null) { //Server加载Service时需要判断是否已经加载过了。
                Service oldother = resourceFactory.find(entry.getName(), type);
                interceptorServiceWrappers.add(new NodeInterceptor.InterceptorServiceWrapper(entry.getName(), type, oldother));
                continue;
            }
            final HashSet<String> groups = entry.getGroups(); //groups.isEmpty()表示<services>没有配置groups属性。
            if (groups.isEmpty() && isSNCP() && this.sncpGroup != null) groups.add(this.sncpGroup);

            final boolean localed = (this.sncpAddress == null && entry.isEmptyGroups() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) //非SNCP的Server，通常是单点服务
                || groups.contains(this.sncpGroup) //本地IP含在内的
                || (this.sncpGroup == null && entry.isEmptyGroups()) //空的SNCP配置
                || type.getAnnotation(LocalService.class) != null;//本地模式
            if (localed && (type.isInterface() || Modifier.isAbstract(type.getModifiers()))) continue; //本地模式不能实例化接口和抽象类的Service类
            final BiConsumer<ResourceFactory, Boolean> runner = (ResourceFactory rf, Boolean needinject) -> {
                try {
                    Service service;
                    if (localed) { //本地模式
                        service = Sncp.createLocalService(entry.getName(), getExecutor(), application.getResourceFactory(), type,
                            NodeServer.this.sncpAddress, loadTransport(NodeServer.this.sncpGroup), loadTransports(groups));
                    } else {
                        service = Sncp.createRemoteService(entry.getName(), getExecutor(), type, NodeServer.this.sncpAddress, loadTransport(groups));
                    }
                    if (SncpClient.parseMethod(type).isEmpty()) return; //class没有可用的方法， 通常为BaseService
                    final ServiceWrapper wrapper = new ServiceWrapper(type, service, entry.getName(), localed ? NodeServer.this.sncpGroup : null, groups, entry.getProperty());
                    for (final Class restype : wrapper.getTypes()) {
                        if (resourceFactory.find(wrapper.getName(), restype) == null) {
                            regFactory.register(wrapper.getName(), restype, wrapper.getService());
                            if (needinject) rf.inject(wrapper.getService()); //动态加载的Service也存在按需加载的注入资源
                        } else if (isSNCP() && !entry.isAutoload()) {
                            throw new RuntimeException(ServiceWrapper.class.getSimpleName() + "(class:" + type.getName() + ", name:" + entry.getName() + ", group:" + groups + ") is repeat.");
                        }
                    }
                    if (wrapper.isRemote()) {
                        remoteServiceWrappers.add(wrapper);
                    } else {
                        localServiceWrappers.add(wrapper);
                        interceptorServiceWrappers.add(new NodeInterceptor.InterceptorServiceWrapper(entry.getName(), type, service));
                        if (consumer != null) consumer.accept(wrapper);
                    }
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            if (entry.isExpect()) {
                ResourceFactory.ResourceLoader resourceLoader = (ResourceFactory rf, final Object src, final String resourceName, Field field, final Object attachment) -> {
                    runner.accept(rf, true);
                };
                for (final Class restype : ServiceWrapper.parseTypes(entry.getType())) {
                    resourceFactory.register(resourceLoader, restype);
                }
            } else {
                runner.accept(resourceFactory, false);
            }

        }

        application.servicecdl.countDown();
        application.servicecdl.await();

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        //---------------- inject ----------------
        new ArrayList<>(localServiceWrappers).forEach(y -> {
            resourceFactory.inject(y.getService(), NodeServer.this);
        });
        new ArrayList<>(remoteServiceWrappers).forEach(y -> {
            resourceFactory.inject(y.getService(), NodeServer.this);
        });
        if (sb != null) {
            remoteServiceWrappers.forEach(y -> {
                sb.append(threadName).append(y.toSimpleString()).append(" loaded and injected").append(LINE_SEPARATOR);
            });
        }
        //----------------- init -----------------
        List<ServiceWrapper> swlist = new ArrayList<>(localServiceWrappers);
        Collections.sort(swlist);
        localServiceWrappers.clear();
        localServiceWrappers.addAll(swlist);
        final List<String> slist = sb == null ? null : new CopyOnWriteArrayList<>();
        CountDownLatch clds = new CountDownLatch(localServiceWrappers.size());
        localServiceWrappers.parallelStream().forEach(y -> {
            try {
                long s = System.currentTimeMillis();
                y.getService().init(y.getConf());
                long e = System.currentTimeMillis() - s;
                if (slist != null) slist.add(new StringBuilder().append(threadName).append(y.toSimpleString()).append(" loaded and inited ").append(e).append(" ms").append(LINE_SEPARATOR).toString());
            } finally {
                clds.countDown();
            }
        });
        clds.await();
        if (slist != null && sb != null) {
            List<String> wlist = new ArrayList<>(slist); //直接使用CopyOnWriteArrayList偶尔会出现莫名的异常(CopyOnWriteArrayList源码1185行)
            Collections.sort(wlist);
            for (String s : wlist) {
                sb.append(s);
            }
        }
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
    }

    protected List<Transport> loadTransports(final HashSet<String> groups) {
        if (groups == null) return null;
        final List<Transport> transports = new ArrayList<>();
        for (String group : groups) {
            if (this.sncpGroup == null || !this.sncpGroup.equals(group)) {
                transports.add(loadTransport(group));
            }
        }
        return transports;
    }

    protected Transport loadTransport(final HashSet<String> groups) {
        if (groups == null || groups.isEmpty()) return null;
        final String groupid = new ArrayList<>(groups).stream().sorted().collect(Collectors.joining(";")); //按字母排列顺序
        Transport transport = application.resourceFactory.find(groupid, Transport.class);
        if (transport != null) return transport;
        final List<Transport> transports = new ArrayList<>();
        for (String group : groups) {
            transports.add(loadTransport(group));
        }
        Set<InetSocketAddress> addrs = new HashSet();
        transports.forEach(t -> addrs.addAll(Arrays.asList(t.getRemoteAddresses())));
        Transport first = transports.get(0);
        GroupInfo ginfo = application.findGroupInfo(first.getName());
        Transport newTransport = new Transport(groupid, ginfo.getProtocol(), application.getWatchFactory(),
            ginfo.getKind(), application.transportBufferPool, application.transportChannelGroup, this.sncpAddress, addrs);
        synchronized (application.resourceFactory) {
            transport = application.resourceFactory.find(groupid, Transport.class);
            if (transport == null) {
                transport = newTransport;
                application.resourceFactory.register(groupid, transport);
            }
        }
        return transport;
    }

    protected Transport loadTransport(final String group) {
        if (group == null) return null;
        Transport transport;
        synchronized (application.resourceFactory) {
            transport = application.resourceFactory.find(group, Transport.class);
            if (transport != null) {
                if (this.sncpAddress != null && !this.sncpAddress.equals(transport.getClientAddress())) {
                    throw new RuntimeException(transport + "repeat create on newClientAddress = " + this.sncpAddress + ", oldClientAddress = " + transport.getClientAddress());
                }
                return transport;
            }
            GroupInfo ginfo = application.findGroupInfo(group);
            Set<InetSocketAddress> addrs = ginfo.copyAddrs();
            if (addrs == null) throw new RuntimeException("Not found <group> = " + group + " on <resources> ");
            transport = new Transport(group, ginfo.getProtocol(), application.getWatchFactory(),
                ginfo.getKind(), application.transportBufferPool, application.transportChannelGroup, this.sncpAddress, addrs);
            application.resourceFactory.register(group, transport);
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
        if (this.serverConf == null) return cf;
        AnyValue[] proplist = this.serverConf.getAnyValues(properties);
        if (proplist == null || proplist.length < 1) return cf;
        cf = null;
        for (AnyValue list : proplist) {
            DefaultAnyValue prop = null;
            String sc = list.getValue("groups");
            if (sc != null) {
                sc = sc.trim();
                if (sc.endsWith(";")) sc = sc.substring(0, sc.length() - 1);
            }
            if (sc == null) sc = localGroup;
            if (sc != null) {
                prop = new AnyValue.DefaultAnyValue();
                prop.addValue("groups", sc);
            }
            ClassFilter filter = new ClassFilter(ref, inter, prop);
            for (AnyValue av : list.getAnyValues(property)) { // <service> 或  <servlet> 节点
                final AnyValue[] items = av.getAnyValues("property");
                if (av instanceof DefaultAnyValue && items.length > 0) { //存在 <property>节点
                    DefaultAnyValue dav = DefaultAnyValue.create();
                    final AnyValue.Entry<String>[] strings = av.getStringEntrys();
                    if (strings != null) {  //将<service>或<servlet>节点的属性值传给dav
                        for (AnyValue.Entry<String> en : strings) {
                            dav.addValue(en.name, en.getValue());
                        }
                    }
                    final AnyValue.Entry<AnyValue>[] anys = av.getAnyEntrys();
                    if (anys != null) {
                        for (AnyValue.Entry<AnyValue> en : anys) { //将<service>或<servlet>节点的非property属性节点传给dav
                            if (!"property".equals(en.name)) dav.addValue(en.name, en.getValue());
                        }
                    }
                    DefaultAnyValue ps = DefaultAnyValue.create();
                    for (AnyValue item : items) {
                        ps.addValue(item.getValue("name"), item.getValue("value"));
                    }
                    dav.addValue("properties", ps);
                    av = dav;
                }
                filter.filter(av, av.getValue("value"), false);
            }
            if (list.getBoolValue("autoload", true)) {
                String includes = list.getValue("includes", "");
                String excludes = list.getValue("excludes", "");
                filter.setIncludePatterns(includes.split(";"));
                filter.setExcludePatterns(excludes.split(";"));
            } else if (ref2 == null || ref2 == Annotation.class) {  //service如果是autoload=false则不需要加载
                filter.setRefused(true);
            } else if (ref2 != Annotation.class) {
                filter.setAnnotationClass(ref2);
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

    public AnyValue getServerConf() {
        return serverConf;
    }

    public Logger getLogger() {
        return logger;
    }

    public String getSncpGroup() {
        return sncpGroup;
    }

    public void start() throws IOException {
        if (interceptor != null) interceptor.preStart(this);
        server.start();
    }

    public void shutdown() throws IOException {
        if (interceptor != null) interceptor.preShutdown(this);
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        localServiceWrappers.forEach(y -> {
            long s = System.currentTimeMillis();
            y.getService().destroy(y.getConf());
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(y.toSimpleString()).append(" destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
        server.shutdown();
    }

    public <T extends Server> T getServer() {
        return (T) server;
    }

    public Set<NodeInterceptor.InterceptorServiceWrapper> getInterceptorServiceWrappers() {
        return new LinkedHashSet<>(interceptorServiceWrappers);
    }

    public Set<ServiceWrapper> getLocalServiceWrappers() {
        return new LinkedHashSet<>(localServiceWrappers);
    }

    public Set<ServiceWrapper> getRemoteServiceWrappers() {
        return new LinkedHashSet<>(remoteServiceWrappers);
    }

}
