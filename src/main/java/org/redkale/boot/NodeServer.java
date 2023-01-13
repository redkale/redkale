/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Command;
import org.redkale.annotation.*;
import static org.redkale.boot.Application.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cluster.ClusterAgent;
import org.redkale.mq.MessageAgent;
import org.redkale.net.Filter;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;

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
    protected BiConsumer<MessageAgent, Service> consumer;

    //server节点的配置
    protected AnyValue serverConf;

    protected final String threadName;

    //加载server节点后的拦截器
    protected NodeInterceptor interceptor;

    //供interceptor使用的Service对象集合
    protected final Set<Service> interceptorServices = new LinkedHashSet<>();

    //本地模式的Service对象集合
    protected final Set<Service> localServices = new LinkedHashSet<>();

    //远程模式的Service对象集合
    protected final Set<Service> remoteServices = new LinkedHashSet<>();

    protected final Map<Service, Servlet> dynServletMap = new LinkedHashMap<>();

    //MessageAgent对象集合
    protected final Map<String, MessageAgent> messageAgents = new HashMap<>();

    //需要远程模式Service的MessageAgent对象集合
    protected final Map<String, MessageAgent> sncpRemoteAgents = new HashMap<>();

    private volatile int maxTypeLength = 0;

    private volatile int maxNameLength = 0;

    public NodeServer(Application application, Server server) {
        this.threadName = Thread.currentThread().getName();
        this.application = application;
        this.server = server;
        this.resourceFactory = server.getResourceFactory();
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        if (application.isCompileMode() || application.getServerClassLoader() instanceof RedkaleClassLoader.RedkaleCacheClassLoader) {
            this.serverClassLoader = application.getServerClassLoader();
        } else {
            this.serverClassLoader = new RedkaleClassLoader(application.getServerClassLoader());
        }
        Thread.currentThread().setContextClassLoader(this.serverClassLoader);
        this.serverThread = Thread.currentThread();
        this.server.setServerClassLoader(serverClassLoader);
    }

    public static <T extends NodeServer> NodeServer create(Class<T> clazz, Application application, AnyValue serconf) {
        try {
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName(), Application.class, AnyValue.class);
            return clazz.getDeclaredConstructor(Application.class, AnyValue.class).newInstance(application, serconf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void init(AnyValue config) throws Exception {
        this.serverConf = config == null ? AnyValue.create() : config;
        if (isSNCP()) { // SNCP协议
            String host = this.serverConf.getValue("host", isWATCH() ? "127.0.0.1" : "0.0.0.0").replace("0.0.0.0", "");
            this.sncpAddress = new InetSocketAddress(host.isEmpty() ? application.localAddress.getAddress().getHostAddress() : host, this.serverConf.getIntValue("port"));
            this.sncpGroup = application.getSncpTransportFactory().findGroupName(this.sncpAddress);
            //单向SNCP服务不需要对等group
            //if (this.sncpGroup == null) throw new RuntimeException("Server (" + String.valueOf(config).replaceAll("\\s+", " ") + ") not found <group> info");
        }
        //单点服务不会有 sncpAddress、sncpGroup
        if (this.sncpAddress != null) {
            this.resourceFactory.register(RESNAME_SNCP_ADDR, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDR, SocketAddress.class, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDR, String.class, this.sncpAddress.getHostString() + ":" + this.sncpAddress.getPort());
        }
        if (this.sncpGroup != null) {
            this.resourceFactory.register(RESNAME_SNCP_GROUP, this.sncpGroup);
        }
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
        //废弃 @since 2.3.0 
//        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, Executor.class, server.getWorkExecutor());
//        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, ExecutorService.class, server.getWorkExecutor());
//        resourceFactory.register(Server.RESNAME_SERVER_EXECUTOR, ThreadPoolExecutor.class, server.getWorkExecutor());

        initResource(); //给 DataSource、CacheSource 注册依赖注入时的监听回调事件。
        String interceptorClass = this.serverConf.getValue("interceptor", "");
        if (!interceptorClass.isEmpty()) {
            Class clazz = serverClassLoader.loadClass(interceptorClass);
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            this.interceptor = (NodeInterceptor) clazz.getDeclaredConstructor().newInstance();
        }

        ClassFilter<Service> serviceFilter = createServiceClassFilter();
        if (application.isSingletonMode()) { //singleton模式下只加载指定的Service
            final String ssc = System.getProperty("red" + "kale.singleton.serviceclass");
            final String extssc = System.getProperty("red" + "kale.singleton.extserviceclasses");
            if (ssc != null) {
                final List<String> sscList = new ArrayList<>();
                sscList.add(ssc);
                if (extssc != null && !extssc.isEmpty()) {
                    for (String s : extssc.split(",")) {
                        if (!s.isEmpty()) {
                            sscList.add(s);
                        }
                    }
                }
                serviceFilter.setExpectPredicate(c -> !sscList.contains(c));
            }
        }
        ClassFilter<Filter> filterFilter = createFilterClassFilter();
        ClassFilter<Servlet> servletFilter = createServletClassFilter();
        ClassFilter otherFilter = createOtherClassFilter();
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serverClassLoader, ((application.excludelibs != null ? (application.excludelibs + ";") : "") + serverConf.getValue("excludelibs", "")).split(";"), serviceFilter, filterFilter, servletFilter, otherFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter, otherFilter); //必须在servlet之前
        if (!application.isSingletonMode()) { //非singleton模式下才加载Filter、Servlet
            loadFilter(filterFilter, otherFilter);
            loadServlet(servletFilter, otherFilter);
            postLoadServlets();
        }
        if (this.interceptor != null) {
            this.resourceFactory.inject(this.interceptor);
        }
    }

    protected abstract void loadFilter(ClassFilter<? extends Filter> filterFilter, ClassFilter otherFilter) throws Exception;

    protected abstract void loadServlet(ClassFilter<? extends Servlet> servletFilter, ClassFilter otherFilter) throws Exception;

    private void initResource() {
        final NodeServer self = this;
        //---------------------------------------------------------------------------------------------
        final ResourceFactory appResFactory = application.getResourceFactory();
        final TransportFactory appSncpTranFactory = application.getSncpTransportFactory();
        final String confURI = appResFactory.find(RESNAME_APP_CONF_DIR, String.class);
        //------------------------------------- 注册 Resource --------------------------------------------------------
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                String resName = null;
                Resource res = field.getAnnotation(Resource.class);
                if (res != null) {
                    resName = res.name();
                } else {
                    javax.annotation.Resource res2 = field.getAnnotation(javax.annotation.Resource.class);
                    if (res2 != null) {
                        resName = res2.name();
                    }
                }
                if (resName == null || !resName.startsWith("properties.")) {
                    return null;
                }
                if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                    return null; //远程模式不得注入 DataSource
                }
                Class type = field.getType();
                if (type != AnyValue.class && type != AnyValue[].class) {
                    return null;
                }
                Object resource = null;
                final AnyValue properties = application.getAppConfig().getAnyValue("properties");
                if (properties != null && type == AnyValue.class) {
                    resource = properties.getAnyValue(resName.substring("properties.".length()));
                    appResFactory.register(resourceName, AnyValue.class, resource);
                } else if (properties != null && type == AnyValue[].class) {
                    resource = properties.getAnyValues(resName.substring("properties.".length()));
                    appResFactory.register(resourceName, AnyValue[].class, resource);
                }
                field.set(srcObj, resource);
                return resource;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Resource inject error", e);
                return null;
            }
        }, AnyValue.class, AnyValue[].class);

        //------------------------------------- 注册 Local AutoLoad(false) Service --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            Class<Service> resServiceType = Service.class;
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                    return null; //远程模式不得注入 AutoLoad Service
                }
                if (!Service.class.isAssignableFrom(field.getType())) {
                    return null;
                }
                resServiceType = (Class) field.getType();
                if (resServiceType.getAnnotation(Local.class) == null) {
                    return null;
                }
                boolean auto = true;
                AutoLoad al = resServiceType.getAnnotation(AutoLoad.class);
                if (al != null) {
                    auto = al.value();
                }
                org.redkale.util.AutoLoad al2 = resServiceType.getAnnotation(org.redkale.util.AutoLoad.class);
                if (al2 != null) {
                    auto = al2.value();
                }
                if (auto) {
                    return null;
                }

                //ResourceFactory resfactory = (isSNCP() ? appResFactory : resourceFactory);
                SncpClient client = srcObj instanceof Service ? Sncp.getSncpClient((Service) srcObj) : null;
                final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                final Set<String> groups = new HashSet<>();
                Service service = Modifier.isFinal(resServiceType.getModifiers()) ? (Service) resServiceType.getConstructor().newInstance() : Sncp.createLocalService(serverClassLoader, resourceName, resServiceType, null, appResFactory, appSncpTranFactory, sncpAddr, groups, null);
                appResFactory.register(resourceName, resServiceType, service);

                field.set(srcObj, service);
                rf.inject(resourceName, service, self); // 给其可能包含@Resource的字段赋值;
                if (!application.isCompileMode()) {
                    service.init(null);
                }
                logger.info("Load Service(@Local @AutoLoad service = " + resServiceType.getSimpleName() + ", resourceName = '" + resourceName + "')");
                return service;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Load @Local @AutoLoad(false) Service inject " + resServiceType + " to " + srcObj + " error", e);
                return null;
            }
        }, Service.class);

        //------------------------------------- 注册 DataSource --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                    return null; //远程模式不得注入 DataSource
                }
                DataSource source = application.loadDataSource(resourceName, false);
                field.set(srcObj, source);
                return source;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject to " + srcObj + " error", e);
                return null;
            }
        }, DataSource.class);

        //------------------------------------- 注册 CacheSource --------------------------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {
            @Override
            public Object load(ResourceFactory rf, String srcResourceName, final Object srcObj, final String resourceName, Field field, final Object attachment) {
                try {
                    if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                        return null;
                    }
                    if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                        return null; //远程模式不需要注入 CacheSource 
                    }
                    if (!(srcObj instanceof Service)) {
                        throw new RuntimeException("CacheSource must be inject in Service, cannot in " + srcObj);
                    }
                    final Service srcService = (Service) srcObj;
                    SncpClient client = Sncp.getSncpClient(srcService);
                    final InetSocketAddress sncpAddr = client == null ? null : client.getClientAddress();
                    //final boolean ws = (srcObj instanceof org.redkale.net.http.WebSocketNodeService) && sncpAddr != null; //不配置SNCP服务会导致ws=false时没有注入CacheMemorySource
                    final boolean ws = (srcObj instanceof org.redkale.net.http.WebSocketNodeService);
                    CacheSource source = application.loadCacheSource(resourceName, ws);
                    field.set(srcObj, source);

                    if (ws && sncpAddr != null) { //只有WebSocketNodeService的服务才需要给SNCP服务注入CacheMemorySource
                        NodeSncpServer sncpServer = application.findNodeSncpServer(sncpAddr);
                        if (sncpServer != null && source != null && source.getClass().getAnnotation(Local.class) == null) { //本地模式的Service不生成SncpServlet
                            sncpServer.getSncpServer().addSncpServlet((Service) source);
                        }
                    }
                    logger.info("Load CacheSource (type = " + (source == null ? null : source.getClass().getSimpleName()) + ", resourceName = '" + resourceName + "')");
                    return source;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "DataSource inject error", e);
                    return null;
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        }, CacheSource.class);

        //------------------------------------- 注册 WebSocketNode --------------------------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {
            @Override
            public Object load(ResourceFactory rf, String srcResourceName, final Object srcObj, final String resourceName, Field field, final Object attachment) {
                try {
                    if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                        return null;
                    }
                    if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                        return null; //远程模式不需要注入 WebSocketNode 
                    }
                    Service nodeService = (Service) rf.find(resourceName, WebSocketNode.class);
                    MessageAgent messageAgent = null;
                    if (srcObj instanceof Service) {
                        messageAgent = Sncp.getMessageAgent((Service) srcObj);
                    } else if (srcObj instanceof WebSocketServlet) {
                        try {
                            Field c = WebSocketServlet.class.getDeclaredField("messageAgent");
                            RedkaleClassLoader.putReflectionField("messageAgent", c);
                            c.setAccessible(true);
                            messageAgent = (MessageAgent) c.get(srcObj);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "WebSocketServlet getMessageAgent error", ex);
                        }
                    }
                    if (nodeService == null) {
                        final HashSet<String> groups = new HashSet<>();
                        if (groups.isEmpty() && isSNCP() && NodeServer.this.sncpGroup != null) {
                            groups.add(NodeServer.this.sncpGroup);
                        }
                        nodeService = Sncp.createLocalService(serverClassLoader, resourceName, org.redkale.net.http.WebSocketNodeService.class, messageAgent, application.getResourceFactory(), application.getSncpTransportFactory(), NodeServer.this.sncpAddress, groups, (AnyValue) null);
                        (isSNCP() ? appResFactory : resourceFactory).register(resourceName, WebSocketNode.class, nodeService);
                        ((org.redkale.net.http.WebSocketNodeService) nodeService).setName(resourceName);
                    }
                    resourceFactory.inject(resourceName, nodeService, self);
                    if (messageAgent != null && Sncp.getMessageAgent(nodeService) == null) {
                        Sncp.setMessageAgent(nodeService, messageAgent);
                    }
                    field.set(srcObj, nodeService);
                    if (Sncp.isRemote(nodeService)) {
                        remoteServices.add(nodeService);
                    } else {
                        rf.inject(resourceName, nodeService); //动态加载的Service也存在按需加载的注入资源
                        localServices.add(nodeService);
                        interceptorServices.add(nodeService);
                        if (consumer != null) {
                            consumer.accept(null, nodeService);
                        }
                    }
                    return nodeService;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "WebSocketNode inject error", e);
                    return null;
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
        if (serviceFilter == null) {
            return;
        }
        final long starts = System.currentTimeMillis();
        final Set<FilterEntry<? extends Service>> entrys = (Set) serviceFilter.getAllFilterEntrys();
        ResourceFactory regFactory = isSNCP() ? application.getResourceFactory() : resourceFactory;
        final ResourceFactory appResourceFactory = application.getResourceFactory();
        final TransportFactory appSncpTransFactory = application.getSncpTransportFactory();
        final AtomicInteger serviceCount = new AtomicInteger();
        for (FilterEntry<? extends Service> entry : entrys) { //service实现类
            final Class<? extends Service> serviceImplClass = entry.getType();
            if (Modifier.isFinal(serviceImplClass.getModifiers())) {
                continue; //修饰final的类跳过
            }
            if (!Modifier.isPublic(serviceImplClass.getModifiers())) {
                continue;
            }
            if (Sncp.isSncpDyn(serviceImplClass)) {
                continue; //动态生成的跳过
            }
            if (entry.isExpect()) {
                if (Modifier.isAbstract(serviceImplClass.getModifiers())) {
                    continue; //修饰abstract的类跳过
                }
                if (DataSource.class.isAssignableFrom(serviceImplClass)) {
                    continue;
                }
                if (CacheSource.class.isAssignableFrom(serviceImplClass)) {
                    continue;
                }
            }
            if (entry.getName().contains("$")) {
                throw new RuntimeException("<name> value cannot contains '$' in " + entry.getProperty());
            }
            Service oldother = resourceFactory.find(entry.getName(), serviceImplClass);
            if (oldother != null) { //Server加载Service时需要判断是否已经加载过了。
                if (!Sncp.isRemote(oldother)) {
                    interceptorServices.add(oldother);
                }
                continue;
            }
            final HashSet<String> groups = entry.getGroups(); //groups.isEmpty()表示<services>没有配置groups属性。
            if (groups.isEmpty() && isSNCP() && this.sncpGroup != null) {
                groups.add(this.sncpGroup);
            }

            final boolean localed = (this.sncpAddress == null && entry.isEmptyGroups() && !serviceImplClass.isInterface() && !Modifier.isAbstract(serviceImplClass.getModifiers())) //非SNCP的Server，通常是单点服务
                || groups.contains(this.sncpGroup) //本地IP含在内的
                || (this.sncpGroup == null && entry.isEmptyGroups()) //空的SNCP配置
                || serviceImplClass.getAnnotation(Local.class) != null;//本地模式
            if (localed && (serviceImplClass.isInterface() || Modifier.isAbstract(serviceImplClass.getModifiers()))) {
                continue; //本地模式不能实例化接口和抽象类的Service类
            }
            final ResourceTypeLoader resourceLoader = (ResourceFactory rf, String srcResourceName, final Object srcObj, final String resourceName, Field field, final Object attachment) -> {
                try {
                    if (SncpClient.parseMethodActions(serviceImplClass).isEmpty()
                        && (serviceImplClass.getAnnotation(Priority.class) == null && serviceImplClass.getAnnotation(javax.annotation.Priority.class) == null)) {  //class没有可用的方法且没有标记启动优先级的， 通常为BaseService
                        if (!serviceImplClass.getName().startsWith("org.redkale.") && !serviceImplClass.getSimpleName().contains("Base")) {
                            logger.log(Level.FINE, serviceImplClass + " cannot load because not found less one public non-final method");
                        }
                        return null;
                    }
                    RedkaleClassLoader.putReflectionPublicMethods(serviceImplClass.getName());
                    MessageAgent agent = null;
                    if (entry.getProperty() != null && entry.getProperty().getValue("mq") != null) {
                        agent = application.getMessageAgent(entry.getProperty().getValue("mq"));
                        if (agent != null) {
                            messageAgents.put(agent.getName(), agent);
                        }
                    }

                    Service service;
                    final boolean ws = srcObj instanceof WebSocketServlet;
                    if (ws || localed) { //本地模式
                        service = Sncp.createLocalService(serverClassLoader, resourceName, serviceImplClass, agent, appResourceFactory, appSncpTransFactory, NodeServer.this.sncpAddress, groups, entry.getProperty());
                    } else {
                        service = Sncp.createRemoteService(serverClassLoader, resourceName, serviceImplClass, agent, appSncpTransFactory, NodeServer.this.sncpAddress, groups, entry.getProperty());
                    }
                    if (service instanceof org.redkale.net.http.WebSocketNodeService) {
                        ((org.redkale.net.http.WebSocketNodeService) service).setName(resourceName);
                        if (agent != null) {
                            Sncp.setMessageAgent(service, agent);
                        }
                    }
                    final Class restype = Sncp.getResourceType(service);
                    if (rf.find(resourceName, restype) == null) {
                        regFactory.register(resourceName, restype, service);
                    } else if (isSNCP() && !entry.isAutoload()) {
                        throw new RuntimeException(restype.getSimpleName() + "(class:" + serviceImplClass.getName() + ", name:" + resourceName + ", group:" + groups + ") is repeat.");
                    }
                    if (Sncp.isRemote(service)) {
                        remoteServices.add(service);
                        if (agent != null) {
                            sncpRemoteAgents.put(agent.getName(), agent);
                        }
                    } else {
                        if (field != null) {
                            rf.inject(resourceName, service); //动态加载的Service也存在按需加载的注入资源
                        }
                        localServices.add(service);
                        interceptorServices.add(service);
                        if (consumer != null) {
                            consumer.accept(agent, service);
                        }
                    }
                    serviceCount.incrementAndGet();
                    return service;
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            if (entry.isExpect()) {
                Class t = ResourceFactory.getResourceType(entry.getType());
                if (resourceFactory.findResourceTypeLoader(t) == null) {
                    resourceFactory.register(resourceLoader, t);
                }
            } else {
                resourceLoader.load(resourceFactory, null, null, entry.getName(), null, false);
            }

        }
        long et = System.currentTimeMillis();
        application.servicecdl.countDown();
        application.servicecdl.await();
        logger.info(this.getClass().getSimpleName() + " construct services in " + (et - starts) + " ms and await " + (System.currentTimeMillis() - et) + " ms");

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        //---------------- inject ----------------
        new ArrayList<>(localServices).forEach(y -> {
            resourceFactory.inject(Sncp.getResourceName(y), y, NodeServer.this);
        });
        new ArrayList<>(remoteServices).forEach(y -> {
            resourceFactory.inject(Sncp.getResourceName(y), y, NodeServer.this);
            calcMaxLength(y);
        });

        if (sb != null) {
            remoteServices.forEach(y -> {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength)).append(" load and inject").append(LINE_SEPARATOR);
            });
        }
        if (isSNCP() && !sncpRemoteAgents.isEmpty()) {
            sncpRemoteAgents.values().forEach(agent -> {
                // agent.putSncpResp((NodeSncpServer) this);
                //  agent.startSncpRespConsumer();
            });
        }
        //----------------- init -----------------
        List<Service> swlist = new ArrayList<>(localServices);
        swlist.forEach(y -> calcMaxLength(y));
        swlist.sort((o1, o2) -> {
            Priority p1 = o1.getClass().getAnnotation(Priority.class);
            Priority p2 = o2.getClass().getAnnotation(Priority.class);
            int v = (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
            if (v != 0) {
                return v;
            }
            int rs = Sncp.getResourceType(o1).getName().compareTo(Sncp.getResourceType(o2).getName());
            if (rs == 0) {
                rs = Sncp.getResourceName(o1).compareTo(Sncp.getResourceName(o2));
            }
            return rs;
        });
        localServices.clear();
        localServices.addAll(swlist);
        //this.loadPersistData();
        long preinits = System.currentTimeMillis();
        preInitServices(localServices, remoteServices);
        long preinite = System.currentTimeMillis() - preinits;
        final List<String> slist = sb == null ? null : new CopyOnWriteArrayList<>();
        if (application.isCompileMode()) {
            localServices.stream().forEach(y -> {
                String serstr = Sncp.toSimpleString(y, maxNameLength, maxTypeLength);
                if (slist != null) {
                    slist.add(new StringBuilder().append(serstr).append(" load").append(LINE_SEPARATOR).toString());
                }
            });
        } else {
            localServices.stream().forEach(y -> {
                long s = System.currentTimeMillis();
                y.init(Sncp.getConf(y));
                long e = System.currentTimeMillis() - s;
                String serstr = Sncp.toSimpleString(y, maxNameLength, maxTypeLength);
                if (slist != null) {
                    slist.add(new StringBuilder().append(serstr).append(" load and init in ").append(e < 10 ? "  " : (e < 100 ? " " : "")).append(e).append(" ms").append(LINE_SEPARATOR).toString());
                }
            });
        }
        if (slist != null && sb != null) {
            List<String> wlist = new ArrayList<>(slist); //直接使用CopyOnWriteArrayList偶尔会出现莫名的异常(CopyOnWriteArrayList源码1185行)
            for (String s : wlist) {
                sb.append(s);
            }
            sb.append("All " + localServices.size() + " Services load in ").append(System.currentTimeMillis() - starts).append(" ms");
        }
        if (sb != null && preinite > 10) {
            sb.append(ClusterAgent.class.getSimpleName()).append(" register in ").append(preinite).append(" ms" + LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    private void calcMaxLength(Service y) { //计算toString中的长度
        maxNameLength = Math.max(maxNameLength, Sncp.getResourceName(y).length());
        maxTypeLength = Math.max(maxTypeLength, Sncp.getResourceType(y).getName().length() + 1);
    }

    //Service.init执行之前调用
    protected void preInitServices(Set<Service> localServices, Set<Service> remoteServices) {
        final ClusterAgent cluster = application.getClusterAgent();
        if (!application.isCompileMode() && cluster != null) {
            NodeProtocol pros = getClass().getAnnotation(NodeProtocol.class);
            String protocol = pros.value().toUpperCase();
            if (!cluster.containsProtocol(protocol)) {
                return;
            }
            if (!cluster.containsPort(server.getSocketAddress().getPort())) {
                return;
            }
            cluster.register(this, protocol, localServices, remoteServices);
        }
    }

    //loadServlet执行之后调用
    protected void postLoadServlets() {

    }

    //Service.destroy执行之前调用
    protected void preDestroyServices(Set<Service> localServices, Set<Service> remoteServices) {
        if (!application.isCompileMode() && application.getClusterAgent() != null) { //服务注销
            final ClusterAgent cluster = application.getClusterAgent();
            NodeProtocol pros = getClass().getAnnotation(NodeProtocol.class);
            String protocol = pros.value().toUpperCase();
            if (cluster.containsProtocol(protocol) && cluster.containsPort(server.getSocketAddress().getPort())) {
                cluster.deregister(this, protocol, localServices, remoteServices);
                afterClusterDeregisterOnPreDestroyServices(cluster, protocol);
            }
        }
        if (!application.isCompileMode() && !this.messageAgents.isEmpty()) { //MQ
        }
    }

    protected void afterClusterDeregisterOnPreDestroyServices(ClusterAgent cluster, String protocol) {
    }

    //Server.start执行之后调用
    protected void postStartServer(Set<Service> localServices, Set<Service> remoteServices) {
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
            AnyValue.DefaultAnyValue prop = null;
            String sc = list.getValue("groups");
            String mq = list.getValue("mq");
            if (sc != null) {
                sc = sc.trim();
                if (sc.endsWith(";")) {
                    sc = sc.substring(0, sc.length() - 1);
                }
            }
            if (sc == null) {
                sc = localGroup;
            }
            if (sc != null || mq != null) {
                prop = new AnyValue.DefaultAnyValue();
                if (sc != null) {
                    prop.addValue("groups", sc);
                }
                if (mq != null) {
                    prop.addValue("mq", mq);
                }
            }
            ClassFilter filter = new ClassFilter(this.serverClassLoader, ref, inter, excludeSuperClasses, prop);
            for (AnyValue av : list.getAnyValues(property)) { // <service>、<filter>、<servlet> 节点
                final AnyValue[] items = av.getAnyValues("property");
                if (av instanceof AnyValue.DefaultAnyValue && items.length > 0) { //存在 <property>节点
                    AnyValue.DefaultAnyValue dav = AnyValue.DefaultAnyValue.create();
                    final AnyValue.Entry<String>[] strings = av.getStringEntrys();
                    if (strings != null) {  //将<service>、<filter>、<servlet>节点的属性值传给dav
                        for (AnyValue.Entry<String> en : strings) {
                            dav.addValue(en.name, en.getValue());
                        }
                    }
                    final AnyValue.Entry<AnyValue>[] anys = av.getAnyEntrys();
                    if (anys != null) {
                        for (AnyValue.Entry<AnyValue> en : anys) { //将<service>、<filter>、<servlet>节点的非property属性节点传给dav
                            if (!"property".equals(en.name)) {
                                dav.addValue(en.name, en.getValue());
                            }
                        }
                    }
                    AnyValue.DefaultAnyValue ps = AnyValue.DefaultAnyValue.create();
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

    public Application getApplication() {
        return application;
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
        if (interceptor != null) {
            interceptor.preStart(this);
        }
        server.start();
        postStartServer(localServices, remoteServices);
    }

    public void shutdown() throws IOException {
        if (interceptor != null) {
            interceptor.preShutdown(this);
        }
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        final boolean finest = logger.isLoggable(Level.FINEST);
        preDestroyServices(localServices, remoteServices);
        localServices.forEach(y -> {
            long s = System.currentTimeMillis();
            if (finest) {
                logger.finest(y + " is destroying");
            }
            y.destroy(Sncp.getConf(y));
            if (finest) {
                logger.finest(y + " was destroyed");
            }
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength)).append(" destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
        server.shutdown();
    }

    public List<Object> command(String cmd, String[] params) throws IOException {
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        List<Object> results = new ArrayList<>();
        localServices.forEach(y -> {
            Set<Method> methods = new HashSet<>();
            Class loop = y.getClass();
            //do {   public方法不用递归
            for (Method m : loop.getMethods()) {
                Command c = m.getAnnotation(Command.class);
                org.redkale.util.Command c2 = m.getAnnotation(org.redkale.util.Command.class);
                if (c == null && c2 == null) {
                    continue;
                }
                if (Modifier.isStatic(m.getModifiers())) {
                    logger.log(Level.WARNING, m + " is static on @Command");
                    continue;
                }
                if (m.getParameterCount() != 1 && m.getParameterCount() != 2) {
                    logger.log(Level.WARNING, m + " parameter count = " + m.getParameterCount() + " on @Command");
                    continue;
                }
                if (m.getParameterTypes()[0] != String.class) {
                    logger.log(Level.WARNING, m + " parameters[0] type is not String.class on @Command");
                    continue;
                }
                if (m.getParameterCount() == 2 && m.getParameterTypes()[1] != String[].class) {
                    logger.log(Level.WARNING, m + " parameters[1] type is not String[].class on @Command");
                    continue;
                }
                if (!c.value().isEmpty() && !c.value().equalsIgnoreCase(cmd)) {
                    continue;
                }
                methods.add(m);
            }
            //} while ((loop = loop.getSuperclass()) != Object.class);
            if (methods.isEmpty()) {
                return;
            }
            long s = System.currentTimeMillis();
            Method one = null;
            try {
                for (Method method : methods) {
                    one = method;
                    Object r = method.getParameterCount() == 2 ? method.invoke(y, cmd, params) : method.invoke(y, cmd);
                    if (r != null) {
                        results.add(r);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, one + " run error, cmd = " + cmd, ex);
            }
            long e = System.currentTimeMillis() - s;
            if (e > 10 && sb != null) {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength)).append(" command (").append(cmd).append(") ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
        return results;
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

    public String getThreadName() {
        return this.threadName;
    }

    static final AnyValue.MergeFunction appConfigmergeFunction = (path, key, val1, val2) -> {
        if ("".equals(path)) {
            if ("executor".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("transport".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("excludelibs".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("cluster".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("listener".equals(key)) {
                if (Objects.equals(val1.getValue("value"), val2.getValue("value"))) {
                    return AnyValue.MergeFunction.SKIP;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
            if ("mq".equals(key)) {
                if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
            if ("group".equals(key)) {
                if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
            if ("server".equals(key)) {
                if (Objects.equals(val1.getValue("name", val1.getValue("protocol") + "_" + val1.getValue("port")),
                    val2.getValue("name", val2.getValue("protocol") + "_" + val2.getValue("port")))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
        }
        if ("cachesource".equals(path)) {
            return AnyValue.MergeFunction.REPLACE;
        }
        if ("datasource".equals(path)) {
            return AnyValue.MergeFunction.REPLACE;
        }
        if ("properties".equals(path)) {
            if ("property".equals(key)) {
                if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
        }
        if ("server".equals(path)) {
            if ("ssl".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("render".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("resource-servlet".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
        }
        if ("server.request".equals(path)) {
            if ("remoteaddr".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("rpc".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("locale".equals(key)) {
                if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
        }
        if ("server.response".equals(path)) {
            if ("content-type".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("defcookie".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("options".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("date".equals(key)) {
                return AnyValue.MergeFunction.REPLACE;
            }
            if ("addheader".equals(key) || "setheader".equals(key)) {
                if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                    return AnyValue.MergeFunction.REPLACE;
                } else {
                    return AnyValue.MergeFunction.NONE;
                }
            }
        }
        return AnyValue.MergeFunction.MERGE;
    };

}
