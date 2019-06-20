/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import org.redkale.util.RedkaleClassLoader;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.persistence.Transient;
import static org.redkale.boot.Application.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.net.Filter;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;
import org.redkale.util.AnyValue.DefaultAnyValue;

/**
 * Server节点的初始化配置类
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public abstract class NodeServer {

    //INFO日志的换行符
    public static final String LINE_SEPARATOR = "\r\n";

    //日志输出对象
    protected final Logger logger;

    //进程主类
    protected final Application application;

    //依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    //当前Server对象
    protected final Server server;

    //ClassLoader
    protected RedkaleClassLoader serverClassLoader;

    protected final Thread serverThread;

    //当前Server的SNCP协议的组
    protected String sncpGroup = null;

    //SNCP服务的地址， 非SNCP为null
    private InetSocketAddress sncpAddress;

    //加载Service时的处理函数
    protected Consumer<Service> consumer;

    //server节点的配置
    protected AnyValue serverConf;

    //加载server节点后的拦截器
    protected NodeInterceptor interceptor;

    //供interceptor使用的Service对象集合
    protected final Set<Service> interceptorServices = new LinkedHashSet<>();

    //本地模式的Service对象集合
    protected final Set<Service> localServices = new LinkedHashSet<>();

    //远程模式的Service对象集合
    protected final Set<Service> remoteServices = new LinkedHashSet<>();

    private volatile int maxClassNameLength = 0;

    private volatile int maxNameLength = 0;

    public NodeServer(Application application, Server server) {
        this.application = application;
        this.server = server;
        this.resourceFactory = server.getResourceFactory();
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.serverClassLoader = new RedkaleClassLoader(application.getServerClassLoader());
        Thread.currentThread().setContextClassLoader(this.serverClassLoader);
        this.serverThread = Thread.currentThread();
        this.server.setServerClassLoader(serverClassLoader);
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
            String host = this.serverConf.getValue("host", isWATCH() ? "127.0.0.1" : "0.0.0.0").replace("0.0.0.0", "");
            this.sncpAddress = new InetSocketAddress(host.isEmpty() ? application.localAddress.getHostAddress() : host, this.serverConf.getIntValue("port"));
            this.sncpGroup = application.sncpTransportFactory.findGroupName(this.sncpAddress);
            //单向SNCP服务不需要对等group
            //if (this.sncpGroup == null) throw new RuntimeException("Server (" + String.valueOf(config).replaceAll("\\s+", " ") + ") not found <group> info");
        }
        //单点服务不会有 sncpAddress、sncpGroup
        if (this.sncpAddress != null) {
            this.resourceFactory.register(RESNAME_SNCP_ADDR, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDR, SocketAddress.class, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDR, String.class, this.sncpAddress.getHostString() + ":" + this.sncpAddress.getPort());
        }
        if (this.sncpGroup != null) this.resourceFactory.register(RESNAME_SNCP_GROUP, this.sncpGroup);
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

            //加入指定的classpath
            Server.loadLib(serverClassLoader, logger, this.serverConf.getValue("lib", ""));
            this.serverThread.setContextClassLoader(this.serverClassLoader);
        }
        //必须要进行初始化， 构建Service时需要使用Context中的ExecutorService
        server.init(this.serverConf);
        //init之后才有Executor
        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, Executor.class, server.getExecutor());
        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, ExecutorService.class, server.getExecutor());
        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, ThreadPoolExecutor.class, server.getExecutor());

        initResource(); //给 DataSource、CacheSource 注册依赖注入时的监听回调事件。
        String interceptorClass = this.serverConf.getValue("interceptor", "");
        if (!interceptorClass.isEmpty()) {
            Class clazz = serverClassLoader.loadClass(interceptorClass);
            this.interceptor = (NodeInterceptor) clazz.getDeclaredConstructor().newInstance();
        }

        ClassFilter<Service> serviceFilter = createServiceClassFilter();
        if (application.singletonrun) { //singleton模式下只加载指定的Service
            final String ssc = System.getProperty("red" + "kale-singleton-serviceclass");
            final String extssc = System.getProperty("red" + "kale-singleton-extserviceclasses");
            if (ssc != null) {
                final List<String> sscList = new ArrayList<>();
                sscList.add(ssc);
                if (extssc != null && !extssc.isEmpty()) {
                    for (String s : extssc.split(",")) {
                        if (!s.isEmpty()) sscList.add(s);
                    }
                }
                serviceFilter.setExpectPredicate(c -> !sscList.contains(c));
            }
        }
        ClassFilter<Filter> filterFilter = createFilterClassFilter();
        ClassFilter<Servlet> servletFilter = createServletClassFilter();
        ClassFilter otherFilter = createOtherClassFilter();
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serverConf.getValue("excludelibs", "").split(";"), serviceFilter, filterFilter, servletFilter, otherFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter, otherFilter); //必须在servlet之前
        if (!application.singletonrun) { //非singleton模式下才加载Filter、Servlet
            loadFilter(filterFilter, otherFilter);
            loadServlet(servletFilter, otherFilter);
        }
        if (this.interceptor != null) this.resourceFactory.inject(this.interceptor);
    }

    protected abstract void loadFilter(ClassFilter<? extends Filter> filterFilter, ClassFilter otherFilter) throws Exception;

    protected abstract void loadServlet(ClassFilter<? extends Servlet> servletFilter, ClassFilter otherFilter) throws Exception;

    private void initResource() {
        final NodeServer self = this;
        //---------------------------------------------------------------------------------------------
        final ResourceFactory appResFactory = application.getResourceFactory();
        final TransportFactory appSncpTranFactory = application.getSncpTransportFactory();
        final AnyValue resources = application.config.getAnyValue("resources");
        final Map<String, SimpleEntry<Class, AnyValue>> cacheResource = new HashMap<>();
        final Map<String, SimpleEntry<Class, AnyValue>> dataResources = new HashMap<>();
        if (resources != null) {
            for (AnyValue sourceConf : resources.getAnyValues("source")) {
                try {
                    Class type = serverClassLoader.loadClass(sourceConf.getValue("value"));
                    if (type == DataSource.class) {
                        type = DataMemorySource.class;
                        for (AnyValue itemConf : sourceConf.getAnyValues("property")) {
                            if (itemConf.getValue("name", "").contains(DataSources.JDBC_URL)) {
                                type = DataJdbcSource.class;
                                break;
                            }
                        }
                    }
                    if (!Service.class.isAssignableFrom(type)) {
                        logger.log(Level.SEVERE, "load application source resource, but not Service error: " + sourceConf);
                    } else if (CacheSource.class.isAssignableFrom(type)) {
                        cacheResource.put(sourceConf.getValue("name", ""), new SimpleEntry(type, sourceConf));
                    } else if (DataSource.class.isAssignableFrom(type)) {
                        dataResources.put(sourceConf.getValue("name", ""), new SimpleEntry(type, sourceConf));
                    } else {
                        logger.log(Level.SEVERE, "load application source resource, but not CacheSource error: " + sourceConf);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "load application source resource error: " + sourceConf, e);
                }
            }
        }
        //------------------------------------- 注册 Resource --------------------------------------------------------
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                Resource res = field.getAnnotation(Resource.class);
                if (res == null || !res.name().startsWith("properties.")) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不得注入 DataSource
                Class type = field.getType();
                if (type != AnyValue.class && type != AnyValue[].class) return;
                Object resource = null;
                final AnyValue properties = resources == null ? null : resources.getAnyValue("properties");
                if (properties != null && type == AnyValue.class) {
                    resource = properties.getAnyValue(res.name().substring("properties.".length()));
                    appResFactory.register(resourceName, AnyValue.class, resource);
                } else if (properties != null && type == AnyValue[].class) {
                    resource = properties.getAnyValues(res.name().substring("properties.".length()));
                    appResFactory.register(resourceName, AnyValue[].class, resource);
                }
                field.set(src, resource);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Resource inject error", e);
            }
        }, AnyValue.class, AnyValue[].class);

        //------------------------------------- 注册 DataSource --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不得注入 DataSource
                SimpleEntry<Class, AnyValue> resEntry = dataResources.get(resourceName);
                AnyValue sourceConf = resEntry == null ? null : resEntry.getValue();
                DataSource source = null;
                boolean needinit = true;
                if (sourceConf != null) {
                    final Class sourceType = resEntry.getKey();
                    if (sourceType == DataJdbcSource.class) {
                        source = DataSources.createDataSource(resourceName, sourceConf);
                    } else {
                        boolean can = false;
                        for (Constructor cr : sourceType.getConstructors()) {
                            if (cr.getParameterCount() == 0) {
                                can = true;
                                break;
                            }
                        }
                        if (DataSource.class.isAssignableFrom(sourceType) && can) { // 必须有空构造函数
                            final Service srcService = (Service) src;
                            SncpClient client = Sncp.getSncpClient(srcService);
                            final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                            final Set<String> groups = new HashSet<>();
                            if (client != null && client.getSameGroup() != null) groups.add(client.getSameGroup());
                            if (client != null && client.getDiffGroups() != null) groups.addAll(client.getDiffGroups());
                            source = (DataSource) Sncp.createLocalService(serverClassLoader, resourceName, sourceType, appResFactory, appSncpTranFactory, sncpAddr, groups, Sncp.getConf(srcService));
                        }
                    }
                }
                if (source == null) {
                    source = DataSources.createDataSource(resourceName); //从persistence.xml配置中创建
                    needinit = false;
                }
                application.dataSources.add(source);
                appResFactory.register(resourceName, DataSource.class, source);

                SncpClient client = Sncp.getSncpClient((Service) src);
                final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                if ((src instanceof DataSource) && sncpAddr != null && resourceFactory.find(resourceName, DataCacheListener.class) == null) { //只有DataSourceService 才能赋值 DataCacheListener   
                    final NodeSncpServer sncpServer = application.findNodeSncpServer(sncpAddr);
                    final Set<String> groups = new HashSet<>();
                    if (client != null && client.getSameGroup() != null) groups.add(client.getSameGroup());
                    if (client != null && client.getDiffGroups() != null) groups.addAll(client.getDiffGroups());
                    Service cacheListenerService = Sncp.createLocalService(serverClassLoader, resourceName, DataCacheListenerService.class, appResFactory, appSncpTranFactory, sncpAddr, groups, Sncp.getConf((Service) src));
                    appResFactory.register(resourceName, DataCacheListener.class, cacheListenerService);
                    localServices.add(cacheListenerService);
                    sncpServer.consumerAccept(cacheListenerService);
                    rf.inject(cacheListenerService, self);
                    logger.info("[" + Thread.currentThread().getName() + "] Load Service " + cacheListenerService);
                }
                field.set(src, source);
                rf.inject(source, self); // 给其可能包含@Resource的字段赋值;
                //NodeServer.this.watchFactory.inject(src);
                if (source instanceof Service && needinit) ((Service) source).init(sourceConf);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject error", e);
            }
        }, DataSource.class);

        //------------------------------------- 注册 CacheSource --------------------------------------------------------
        resourceFactory.register(new ResourceFactory.ResourceLoader() {
            @Override
            public void load(ResourceFactory rf, final Object src, final String resourceName, Field field, final Object attachment) {
                try {
                    if (field.getAnnotation(Resource.class) == null) return;
                    if (!(src instanceof Service)) throw new RuntimeException("CacheSource must be inject in Service, cannot " + src);
                    if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不需要注入 CacheSource 
                    final Service srcService = (Service) src;
                    SncpClient client = Sncp.getSncpClient(srcService);
                    final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                    final Set<String> groups = new HashSet<>();
                    if (client != null && client.getSameGroup() != null) groups.add(client.getSameGroup());
                    if (client != null && client.getDiffGroups() != null) groups.addAll(client.getDiffGroups());

                    SimpleEntry<Class, AnyValue> resEntry = cacheResource.get(resourceName);
                    AnyValue sourceConf = resEntry == null ? null : resEntry.getValue();
                    if (sourceConf == null) {
                        SimpleEntry<Class, AnyValue> resEntry2 = dataResources.get(resourceName);
                        sourceConf = resEntry2 == null ? null : resEntry2.getValue();
                    }
                    final Class sourceType = sourceConf == null ? CacheMemorySource.class : serverClassLoader.loadClass(sourceConf.getValue("value"));
                    Object source = null;
                    if (CacheSource.class.isAssignableFrom(sourceType)) { // CacheSource
                        source = (CacheSource) Sncp.createLocalService(serverClassLoader, resourceName, sourceType, appResFactory, appSncpTranFactory, sncpAddr, groups, Sncp.getConf(srcService));
                        Type genericType = field.getGenericType();
                        ParameterizedType pt = (genericType instanceof ParameterizedType) ? (ParameterizedType) genericType : null;
                        Type valType = pt == null ? null : pt.getActualTypeArguments()[0];
                        if (CacheSource.class.isAssignableFrom(sourceType)) {
                            CacheSource cacheSource = (CacheSource) source;
                            cacheSource.initValueType(valType instanceof Class ? (Class) valType : Object.class);
                            cacheSource.initTransient(field.getAnnotation(Transient.class) != null); //必须在initValueType之后
                        }
                        application.cacheSources.add((CacheSource) source);
                        appResFactory.register(resourceName, genericType, source);
                        appResFactory.register(resourceName, CacheSource.class, source);
                    }
                    field.set(src, source);
                    rf.inject(source, self); //
                    if (source instanceof Service) ((Service) source).init(sourceConf);

                    if ((src instanceof WebSocketNodeService) && sncpAddr != null) { //只有WebSocketNodeService的服务才需要给SNCP服务注入CacheMemorySource
                        NodeSncpServer sncpServer = application.findNodeSncpServer(sncpAddr);
                        sncpServer.getSncpServer().addSncpServlet((Service) source);
                        //logger.info("[" + Thread.currentThread().getName() + "] Load Service " + source);
                    }
                    logger.info("[" + Thread.currentThread().getName() + "] Load Source " + source);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "DataSource inject error", e);
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        }, CacheSource.class);

        //------------------------------------- 注册 WebSocketNode --------------------------------------------------------
        resourceFactory.register(new ResourceFactory.ResourceLoader() {
            @Override
            public void load(ResourceFactory rf, final Object src, final String resourceName, Field field, final Object attachment) {
                try {
                    if (field.getAnnotation(Resource.class) == null) return;
                    if ((src instanceof Service) && Sncp.isRemote((Service) src)) return; //远程模式不需要注入 WebSocketNode 
                    Service nodeService = (Service) rf.find(resourceName, WebSocketNode.class);
                    if (nodeService == null) {
                        final HashSet<String> groups = new HashSet<>();
                        if (groups.isEmpty() && isSNCP() && NodeServer.this.sncpGroup != null) groups.add(NodeServer.this.sncpGroup);
                        nodeService = Sncp.createLocalService(serverClassLoader, resourceName, WebSocketNodeService.class, application.getResourceFactory(), application.getSncpTransportFactory(), NodeServer.this.sncpAddress, groups, (AnyValue) null);
                        (isSNCP() ? appResFactory : resourceFactory).register(resourceName, WebSocketNode.class, nodeService);
                    }
                    resourceFactory.inject(nodeService, self);
                    field.set(src, nodeService);
                    if (Sncp.isRemote(nodeService)) {
                        remoteServices.add(nodeService);
                    } else {
                        if (field != null) rf.inject(nodeService); //动态加载的Service也存在按需加载的注入资源
                        localServices.add(nodeService);
                        interceptorServices.add(nodeService);
                        if (consumer != null) consumer.accept(nodeService);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "WebSocketNode inject error", e);
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        }, WebSocketNode.class);
    }

    @SuppressWarnings("unchecked")
    protected void loadService(ClassFilter<? extends Service> serviceFilter, ClassFilter otherFilter) throws Exception {
        if (serviceFilter == null) return;
        final long starts = System.currentTimeMillis();
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        final Set<FilterEntry<? extends Service>> entrys = (Set) serviceFilter.getAllFilterEntrys();
        ResourceFactory regFactory = isSNCP() ? application.getResourceFactory() : resourceFactory;
        final ResourceFactory appResourceFactory = application.getResourceFactory();
        final TransportFactory appSncpTransFactory = application.getSncpTransportFactory();
        for (FilterEntry<? extends Service> entry : entrys) { //service实现类
            final Class<? extends Service> serviceImplClass = entry.getType();
            if (Modifier.isFinal(serviceImplClass.getModifiers())) continue; //修饰final的类跳过
            if (!Modifier.isPublic(serviceImplClass.getModifiers())) continue;
            if (entry.isExpect()) {
                if (Modifier.isAbstract(serviceImplClass.getModifiers())) continue; //修饰abstract的类跳过
                if (DataSource.class.isAssignableFrom(serviceImplClass)) continue;
                if (CacheSource.class.isAssignableFrom(serviceImplClass)) continue;
                if (DataCacheListener.class.isAssignableFrom(serviceImplClass)) continue;
            }
            if (entry.getName().contains("$")) throw new RuntimeException("<name> value cannot contains '$' in " + entry.getProperty());
            Service oldother = resourceFactory.find(entry.getName(), serviceImplClass);
            if (oldother != null) { //Server加载Service时需要判断是否已经加载过了。
                interceptorServices.add(oldother);
                continue;
            }
            final HashSet<String> groups = entry.getGroups(); //groups.isEmpty()表示<services>没有配置groups属性。
            if (groups.isEmpty() && isSNCP() && this.sncpGroup != null) groups.add(this.sncpGroup);

            final boolean localed = (this.sncpAddress == null && entry.isEmptyGroups() && !serviceImplClass.isInterface() && !Modifier.isAbstract(serviceImplClass.getModifiers())) //非SNCP的Server，通常是单点服务
                || groups.contains(this.sncpGroup) //本地IP含在内的
                || (this.sncpGroup == null && entry.isEmptyGroups()) //空的SNCP配置
                || serviceImplClass.getAnnotation(Local.class) != null;//本地模式
            if (localed && (serviceImplClass.isInterface() || Modifier.isAbstract(serviceImplClass.getModifiers()))) continue; //本地模式不能实例化接口和抽象类的Service类
            final ResourceFactory.ResourceLoader resourceLoader = (ResourceFactory rf, final Object src, final String resourceName, Field field, final Object attachment) -> {
                try {
                    if (SncpClient.parseMethod(serviceImplClass).isEmpty() && serviceImplClass.getAnnotation(Priority.class) == null) {  //class没有可用的方法且没有标记启动优先级的， 通常为BaseService
                        if (!serviceImplClass.getName().startsWith("org.redkale.")) logger.log(Level.FINE, serviceImplClass + " cannot load because not found less one public non-final method");
                        return;
                    }

                    Service service;
                    boolean ws = src instanceof WebSocketServlet;
                    if (ws || localed) { //本地模式
                        service = Sncp.createLocalService(serverClassLoader, resourceName, serviceImplClass, appResourceFactory, appSncpTransFactory, NodeServer.this.sncpAddress, groups, entry.getProperty());
                    } else {
                        service = Sncp.createRemoteService(serverClassLoader, resourceName, serviceImplClass, appSncpTransFactory, NodeServer.this.sncpAddress, groups, entry.getProperty());
                    }
                    final Class restype = Sncp.getResourceType(service);
                    if (rf.find(resourceName, restype) == null) {
                        regFactory.register(resourceName, restype, service);
                    } else if (isSNCP() && !entry.isAutoload()) {
                        throw new RuntimeException(restype.getSimpleName() + "(class:" + serviceImplClass.getName() + ", name:" + resourceName + ", group:" + groups + ") is repeat.");
                    }
                    if (Sncp.isRemote(service)) {
                        remoteServices.add(service);
                    } else {
                        if (field != null) rf.inject(service); //动态加载的Service也存在按需加载的注入资源
                        localServices.add(service);
                        interceptorServices.add(service);
                        if (consumer != null) consumer.accept(service);
                    }
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            if (entry.isExpect()) {
                ResourceType rty = entry.getType().getAnnotation(ResourceType.class);
                resourceFactory.register(resourceLoader, rty == null ? entry.getType() : rty.value());
            } else {
                resourceLoader.load(resourceFactory, null, entry.getName(), null, false);
            }

        }

        application.servicecdl.countDown();
        application.servicecdl.await();

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        //---------------- inject ----------------
        new ArrayList<>(localServices).forEach(y -> {
            resourceFactory.inject(y, NodeServer.this);
            calcMaxLength(y);
        });
        new ArrayList<>(remoteServices).forEach(y -> {
            resourceFactory.inject(y, NodeServer.this);
            calcMaxLength(y);
        });

        if (sb != null) {
            remoteServices.forEach(y -> {
                sb.append(threadName).append(Sncp.toSimpleString(y, maxNameLength, maxClassNameLength)).append(" load and inject").append(LINE_SEPARATOR);
            });
        }
        //----------------- init -----------------
        List<Service> swlist = new ArrayList<>(localServices);
        swlist.sort((o1, o2) -> {
            Priority p1 = o1.getClass().getAnnotation(Priority.class);
            Priority p2 = o2.getClass().getAnnotation(Priority.class);
            int v = (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
            if (v != 0) return v;
            int rs = Sncp.getResourceType(o1).getName().compareTo(Sncp.getResourceType(o2).getName());
            if (rs == 0) rs = Sncp.getResourceName(o1).compareTo(Sncp.getResourceName(o2));
            return rs;
        });
        localServices.clear();
        localServices.addAll(swlist);
        //this.loadPersistData();
        final List<String> slist = sb == null ? null : new CopyOnWriteArrayList<>();
        localServices.stream().forEach(y -> {
            long s = System.currentTimeMillis();
            y.init(Sncp.getConf(y));
            long e = System.currentTimeMillis() - s;
            String serstr = Sncp.toSimpleString(y, maxNameLength, maxClassNameLength);
            if (slist != null) slist.add(new StringBuilder().append(threadName).append(serstr).append(" load and init in ").append(e).append(" ms").append(LINE_SEPARATOR).toString());
        });
        if (slist != null && sb != null) {
            List<String> wlist = new ArrayList<>(slist); //直接使用CopyOnWriteArrayList偶尔会出现莫名的异常(CopyOnWriteArrayList源码1185行)
            for (String s : wlist) {
                sb.append(s);
            }
            sb.append(threadName).append("All Services load cost " + (System.currentTimeMillis() - starts) + " ms" + LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
    }

    private void calcMaxLength(Service y) { //计算toString中的长度
        maxNameLength = Math.max(maxNameLength, Sncp.getResourceName(y).length());
        maxClassNameLength = Math.max(maxClassNameLength, Sncp.getResourceType(y).getName().length() + 1);
    }

    protected abstract ClassFilter<Filter> createFilterClassFilter();

    protected abstract ClassFilter<Servlet> createServletClassFilter();

    protected ClassFilter createOtherClassFilter() {
        return null;
    }

    protected ClassFilter<Service> createServiceClassFilter() {
        return createClassFilter(this.sncpGroup, null, Service.class, (!isSNCP() && application.watching) ? null : new Class[]{org.redkale.watch.WatchService.class}, Annotation.class, "services", "service");
    }

    protected ClassFilter createClassFilter(final String localGroup, Class<? extends Annotation> ref,
        Class inter, Class[] excludeSuperClasses, Class<? extends Annotation> ref2, String properties, String property) {
        ClassFilter cf = new ClassFilter(this.serverClassLoader, ref, inter, excludeSuperClasses, null);
        if (properties == null && properties == null) {
            cf.setRefused(true);
            return cf;
        }
        if (this.serverConf == null) {
            cf.setRefused(true);
            return cf;
        }
        AnyValue[] proplist = this.serverConf.getAnyValues(properties);
        if (proplist == null || proplist.length < 1) {
            cf.setRefused(true);
            return cf;
        }
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
            ClassFilter filter = new ClassFilter(this.serverClassLoader, ref, inter, excludeSuperClasses, prop);
            for (AnyValue av : list.getAnyValues(property)) { // <service>、<filter>、<servlet> 节点
                final AnyValue[] items = av.getAnyValues("property");
                if (av instanceof DefaultAnyValue && items.length > 0) { //存在 <property>节点
                    DefaultAnyValue dav = DefaultAnyValue.create();
                    final AnyValue.Entry<String>[] strings = av.getStringEntrys();
                    if (strings != null) {  //将<service>、<filter>、<servlet>节点的属性值传给dav
                        for (AnyValue.Entry<String> en : strings) {
                            dav.addValue(en.name, en.getValue());
                        }
                    }
                    final AnyValue.Entry<AnyValue>[] anys = av.getAnyEntrys();
                    if (anys != null) {
                        for (AnyValue.Entry<AnyValue> en : anys) { //将<service>、<filter>、<servlet>节点的非property属性节点传给dav
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
                if (!av.getBoolValue("ignore", false)) {
                    filter.filter(av, av.getValue("value"), false);
                }
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

    public boolean isWATCH() {
        return false;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public RedkaleClassLoader getServerClassLoader() {
        return serverClassLoader;
    }

    public void setServerClassLoader(RedkaleClassLoader serverClassLoader) {
        Objects.requireNonNull(this.serverClassLoader);
        this.serverClassLoader = serverClassLoader;
        this.serverThread.setContextClassLoader(serverClassLoader);
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
        final boolean finest = logger.isLoggable(Level.FINEST);
        localServices.forEach(y -> {
            long s = System.currentTimeMillis();
            if (finest) logger.finest(y + " is destroying");
            y.destroy(Sncp.getConf(y));
            if (finest) logger.finest(y + " was destroyed");
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxClassNameLength)).append(" destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
        server.shutdown();
    }

    public <T extends Server> T getServer() {
        return (T) server;
    }

    public Set<Service> getInterceptorServices() {
        return new LinkedHashSet<>(interceptorServices);
    }

    public Set<Service> getLocalServices() {
        return new LinkedHashSet<>(localServices);
    }

    public Set<Service> getRemoteServices() {
        return new LinkedHashSet<>(remoteServices);
    }

}
