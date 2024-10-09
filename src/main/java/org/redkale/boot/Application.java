/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Configuration;
import org.redkale.annotation.Nonnull;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cached.spi.CachedModuleEngine;
import org.redkale.cluster.*;
import org.redkale.cluster.spi.ClusterAgent;
import org.redkale.cluster.spi.ClusterModuleEngine;
import org.redkale.cluster.spi.HttpClusterRpcClient;
import org.redkale.cluster.spi.HttpLocalRpcClient;
import org.redkale.convert.Convert;
import org.redkale.convert.json.*;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.inject.ResourceAnnotationLoader;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.locked.spi.LockedModuleEngine;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.mq.spi.MessageModuleEngine;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.props.spi.PropertiesModule;
import org.redkale.scheduled.spi.ScheduledModuleEngine;
import org.redkale.service.RetCodes;
import org.redkale.service.Service;
import org.redkale.source.*;
import org.redkale.source.spi.SourceModuleEngine;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.ByteBufferPool;
import org.redkale.util.Creator;
import org.redkale.util.Environment;
import org.redkale.util.Redkale;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Times;
import org.redkale.util.Utility;
import org.redkale.watch.WatchServlet;

/**
 * 进程启动类，全局对象。 <br>
 *
 * <pre>
 * 程序启动执行步骤:
 *     1、读取application.xml
 *     2、进行classpath扫描动态加载Service、WebSocket与Servlet
 *     3、优先加载所有SNCP协议的服务，再加载其他协议服务， 最后加载WATCH协议的服务
 *     4、最后进行Service、Servlet与其他资源之间的依赖注入
 * </pre>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Application {

    /** 当前进程启动的时间， 类型： long */
    public static final String RESNAME_APP_TIME = "APP_TIME";

    /** 当前进程服务的名称， 类型：String */
    public static final String RESNAME_APP_NAME = "APP_NAME";

    /** 当前进程的根目录， 类型：String、File、Path、URI */
    public static final String RESNAME_APP_HOME = "APP_HOME";

    /**
     * 当前进程的配置目录URI，如果不是绝对路径则视为HOME目录下的相对路径 类型：String、URI、File、Path <br>
     * 若配置目录不是本地文件夹， 则File、Path类型的值为null
     */
    public static final String RESNAME_APP_CONF_DIR = "APP_CONF_DIR";

    /** 当前进程节点的nodeid， 类型：String */
    public static final String RESNAME_APP_NODEID = "APP_NODEID";

    /** 当前进程节点的IP地址， 类型：InetSocketAddress、InetAddress、String */
    public static final String RESNAME_APP_ADDR = "APP_ADDR";

    /**
     * 当前进程的work线程池， 类型：Executor、ExecutorService
     *
     * @since 2.3.0
     */
    public static final String RESNAME_APP_EXECUTOR = "APP_EXECUTOR";

    /**
     * 使用RESNAME_APP_CLIENT_IOGROUP代替
     *
     * @since 2.3.0
     */
    public static final String RESNAME_APP_CLIENT_ASYNCGROUP = "APP_CLIENT_ASYNCGROUP";

    /** 当前Service所属的SNCP Server的地址 类型: SocketAddress、InetSocketAddress、String <br> */
    public static final String RESNAME_SNCP_ADDRESS = "SNCP_ADDRESS";

    /** 当前Service所属的SNCP Server所属的组 类型: String<br> */
    public static final String RESNAME_SNCP_GROUP = "SNCP_GROUP";

    /** "SERVER_ROOT" 当前Server的ROOT目录类型：String、File、Path */
    public static final String RESNAME_SERVER_ROOT = Server.RESNAME_SERVER_ROOT;

    /** 当前Server的ResourceFactory */
    public static final String RESNAME_SERVER_RESFACTORY = "SERVER_RESFACTORY";

    public static final String SYSNAME_APP_NAME = "redkale.application.name";

    public static final String SYSNAME_APP_NODEID = "redkale.application.nodeid";

    public static final String SYSNAME_APP_HOME = "redkale.application.home";

    public static final String SYSNAME_APP_CONF_DIR = "redkale.application.confdir";

    public static final Set<String> REDKALE_RESNAMES = Collections.unmodifiableSet(Set.of(
            RESNAME_APP_NAME,
            RESNAME_APP_NODEID,
            RESNAME_APP_TIME,
            RESNAME_APP_HOME,
            RESNAME_APP_ADDR,
            RESNAME_APP_CONF_DIR));

    // UDP协议的ByteBuffer Capacity
    private static final int UDP_CAPACITY = 1024;

    // 日志
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    // 本进程节点ID
    final String nodeid;

    // 本进程节点ID
    final String name;

    // 本地IP地址
    final InetSocketAddress localAddress;

    // 配置信息，只读版Properties
    private final Environment environment;

    // 全局根ResourceFactory
    final ResourceFactory resourceFactory = ResourceFactory.create();

    // NodeServer 资源, 顺序必须是sncps, others, watchs
    final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    // 配置项里的group信息, 注意： 只给SNCP使用
    private final SncpRpcGroups sncpRpcGroups = new SncpRpcGroups();

    // 除logging配置之外的所有配置项，包含本地和远程配置项
    final Properties envProperties = new Properties();

    // 业务逻辑线程池
    // @since 2.3.0
    @Nonnull
    private ExecutorService workExecutor;

    // 给客户端使用，包含SNCP客户端、自定义数据库客户端连接池
    private AsyncIOGroup clientAsyncGroup;

    // 服务配置项
    final AnyValue config;

    // 是否启动了WATCH协议服务
    boolean watching;

    // ------------- 模块组件(必须靠后放,否则new Module时resourceFactory会为null) -------------
    // 日志组件
    // @since 2.8.0
    final LoggingModule loggingModule;

    // 配置组件
    final PropertiesModule propertiesModule;

    // 数据源组件
    private final SourceModuleEngine sourceModule;

    // -----------------------------------------------------------------------------------
    // 是否用于main方法运行
    private final boolean singletonMode;

    // 是否用于编译模式运行
    private final boolean compileMode;

    // 进程根目录
    private final File home;

    // 配置文件目录
    private final URI confDir;

    // 监听事件
    private final List<ApplicationListener> listeners = new CopyOnWriteArrayList<>();

    // 服务启动时间
    private final long startTime = System.currentTimeMillis();

    // Server启动的计数器，用于确保所有Server都启动完后再进行下一步处理
    private final CountDownLatch shutdownLatch;

    // 根ClassLoader
    private final RedkaleClassLoader classLoader;

    // Server根ClassLoader
    private final RedkaleClassLoader serverClassLoader;

    // 系统模块组件
    private final List<ModuleEngine> moduleEngines = new ArrayList<>();

    /**
     * 初始化步骤: <br>
     * 1、基本环境变量设置 <br>
     * 2、ClassLoader初始化 <br>
     * 3、日志配置初始化 <br>
     * 4、本地和远程配置文件读取 <br>
     * 5、ClusterAgent和MessageAgent实例化 <br>
     * 6、Work线程池初始化 7、原生sql解析器初始化 <br>
     *
     * @param singletonMode 是否测试模式
     * @param compileMode 是否编译模式
     * @param config 启动配置
     */
    @SuppressWarnings("UseSpecificCatch") // config: 不带redkale.前缀的配置项
    Application(final AppConfig appConfig) {
        this.singletonMode = appConfig.singletonMode;
        this.compileMode = appConfig.compileMode;
        this.config = appConfig.config;
        this.envProperties.putAll(appConfig.localEnvProperties);
        this.environment = new Environment(this.envProperties);
        this.name = appConfig.name;
        this.nodeid = appConfig.nodeid;
        this.home = appConfig.home;
        this.confDir = appConfig.confDir;
        this.localAddress = appConfig.localAddress;
        this.classLoader = appConfig.classLoader;
        this.serverClassLoader = appConfig.serverClassLoader;

        this.loggingModule = new LoggingModule(this);
        this.propertiesModule = new PropertiesModule(this);
        this.sourceModule = new SourceModuleEngine(this);

        // 设置基础信息资源
        this.resourceFactory.register(RESNAME_APP_NAME, String.class, this.name);

        this.resourceFactory.register(RESNAME_APP_NODEID, String.class, this.nodeid);
        if (Utility.isNumeric(this.nodeid)) { // 兼容旧类型
            this.resourceFactory.register(
                    RESNAME_APP_NODEID, int.class, Math.abs(((Long) Long.parseLong(this.nodeid)).intValue()));
        }

        this.resourceFactory.register(RESNAME_APP_TIME, long.class, this.startTime);
        this.resourceFactory.register(RESNAME_APP_TIME, Long.class, this.startTime);

        this.resourceFactory.register(RESNAME_APP_HOME, String.class, this.home.getPath());
        this.resourceFactory.register(RESNAME_APP_HOME, Path.class, this.home.toPath());
        this.resourceFactory.register(RESNAME_APP_HOME, File.class, this.home);
        this.resourceFactory.register(RESNAME_APP_HOME, URI.class, this.home.toURI());

        this.resourceFactory.register(RESNAME_APP_ADDR, InetSocketAddress.class, this.localAddress);
        this.resourceFactory.register(RESNAME_APP_ADDR, InetAddress.class, this.localAddress.getAddress());
        this.resourceFactory.register(
                RESNAME_APP_ADDR, String.class, this.localAddress.getAddress().getHostAddress());

        this.resourceFactory.register(RESNAME_APP_CONF_DIR, URI.class, this.confDir);
        this.resourceFactory.register(RESNAME_APP_CONF_DIR, File.class, appConfig.confFile);
        this.resourceFactory.register(RESNAME_APP_CONF_DIR, String.class, this.confDir.toString());

        System.setProperty("redkale.version", Redkale.getDotedVersion());
        System.setProperty(SYSNAME_APP_NAME, this.name);
        System.setProperty(SYSNAME_APP_NODEID, String.valueOf(this.nodeid));
        System.setProperty(SYSNAME_APP_HOME, this.home.getPath());
        System.setProperty(SYSNAME_APP_CONF_DIR, this.confDir.toString());

        this.envProperties.put(RESNAME_APP_NAME, this.name);
        this.envProperties.put(RESNAME_APP_NODEID, String.valueOf(this.nodeid));
        this.envProperties.put(RESNAME_APP_TIME, String.valueOf(this.startTime));
        this.envProperties.put(RESNAME_APP_HOME, this.home.getPath());
        this.envProperties.put(RESNAME_APP_ADDR, this.localAddress.getAddress().getHostAddress());
        this.envProperties.put(RESNAME_APP_CONF_DIR, this.confDir.toString());

        // 初始化本地配置的System.properties、mimetypes
        this.registerResourceEnvs(true, appConfig.localEnvProperties);

        // 需要在加载properties初始化System.properties之后再注册
        this.resourceFactory.register(Environment.class, environment);
        this.resourceFactory.register(JsonFactory.root());
        this.resourceFactory.register(ProtobufFactory.root());
        this.resourceFactory.register(JsonFactory.root().getConvert());
        this.resourceFactory.register(ProtobufFactory.root().getConvert());
        this.resourceFactory.register(
                "jsonconvert", Convert.class, JsonFactory.root().getConvert());
        this.resourceFactory.register(
                "protobufconvert", Convert.class, ProtobufFactory.root().getConvert());
        JsonFactory.root().registerFieldFuncConsumer(resourceFactory::inject);
        ProtobufFactory.root().registerFieldFuncConsumer(resourceFactory::inject);

        // 系统内部模块组件
        moduleEngines.add(this.sourceModule); // 放第一，很多module依赖于source
        moduleEngines.add(new MessageModuleEngine(this));
        moduleEngines.add(new ClusterModuleEngine(this));
        moduleEngines.add(new ScheduledModuleEngine(this));
        moduleEngines.add(new CachedModuleEngine(this));
        moduleEngines.add(new LockedModuleEngine(this));

        // 根据本地日志配置文件初始化日志
        loggingModule.reconfigLogging(true, appConfig.locaLogProperties);

        // 打印基础信息日志
        logger.log(
                Level.INFO,
                colorMessage(
                        logger,
                        36,
                        1,
                        "-------------------------------- Redkale " + Redkale.getDotedVersion()
                                + " --------------------------------"));

        final String confDirStr = this.confDir.toString();
        logger.log(
                Level.INFO,
                "APP_OS       = " + System.getProperty("os.name") + " "
                        + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\r\n"
                        + "APP_JAVA     = "
                        + System.getProperty(
                                "java.runtime.name",
                                System.getProperty("org.graalvm.nativeimage.kind") != null ? "Nativeimage" : "")
                        + " "
                        + System.getProperty(
                                "java.runtime.version",
                                System.getProperty("java.vendor.version", System.getProperty("java.vm.version")))
                        + "\r\n" // graalvm.nativeimage 模式下无 java.runtime.xxx 属性
                        + "APP_PID      = " + ProcessHandle.current().pid() + "\r\n"
                        + RESNAME_APP_NAME + "     = " + this.name + "\r\n"
                        + RESNAME_APP_NODEID + "   = " + this.nodeid + "\r\n"
                        + "APP_LOADER   = " + this.classLoader.getClass().getSimpleName() + "\r\n"
                        + RESNAME_APP_ADDR + "     = " + this.localAddress.getHostString() + ":"
                        + this.localAddress.getPort() + "\r\n"
                        + RESNAME_APP_HOME + "     = " + this.home.getPath().replace('\\', '/') + "\r\n"
                        + RESNAME_APP_CONF_DIR + " = " + confDirStr.substring(confDirStr.indexOf('!') + 1));

        if (!compileMode && !(classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader)) {
            String lib = environment.getPropertyValue(
                    config.getValue("lib", "${APP_HOME}/libs/*").trim());
            lib = Utility.isEmpty(lib) ? confDirStr : (lib + ";" + confDirStr);
            Server.loadLib(classLoader, logger, lib.isEmpty() ? confDirStr : (lib + ";" + confDirStr));
        }
        this.shutdownLatch = new CountDownLatch(config.getAnyValues("server").length + 1);
    }

    public void init() throws Exception {
        // 注册Resource加载器
        this.initInjectResource();
        // 读取远程配置，并合并app.config
        this.propertiesModule.initRemoteProperties();
        // 解析配置
        this.onEnvironmentLoaded();
        // init起始回调
        this.onAppPreInit();
        // 加载错误码
        this.initRetCodes();
        // 设置WorkExecutor
        this.initWorkExecutor();
        // 回调Listener
        initAppListeners();
        // init结束回调
        this.onAppPostInit();
    }

    private void initInjectResource() {
        final Application application = this;
        // 只有WatchService才能加载Application、WatchFactory
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    if (field != null) {
                        field.set(srcObj, application);
                    }
                    return application;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return Application.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    boolean serv =
                            RESNAME_SERVER_RESFACTORY.equals(resourceName) || resourceName.equalsIgnoreCase("server");
                    ResourceFactory rs = serv ? rf : (resourceName.isEmpty() ? application.resourceFactory : null);
                    if (field != null) {
                        field.set(srcObj, rs);
                    }
                    return rs;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return ResourceFactory.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    NodeServer server = null;
                    for (NodeServer ns : application.getNodeServers()) {
                        if (ns.getClass() != NodeSncpServer.class) {
                            continue;
                        }
                        if (resourceName.equals(ns.server.getName())) {
                            server = ns;
                            break;
                        }
                    }
                    if (field != null) {
                        field.set(srcObj, server);
                    }
                    return server;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return NodeSncpServer.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    NodeServer server = null;
                    for (NodeServer ns : application.getNodeServers()) {
                        if (ns.getClass() != NodeHttpServer.class) {
                            continue;
                        }
                        if (resourceName.equals(ns.server.getName())) {
                            server = ns;
                            break;
                        }
                    }
                    if (field != null) {
                        field.set(srcObj, server);
                    }
                    return server;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return NodeHttpServer.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    NodeServer server = null;
                    for (NodeServer ns : application.getNodeServers()) {
                        if (ns.getClass() != NodeWatchServer.class) {
                            continue;
                        }
                        if (resourceName.equals(ns.server.getName())) {
                            server = ns;
                            break;
                        }
                    }
                    if (field != null) {
                        field.set(srcObj, server);
                    }
                    return server;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return NodeWatchServer.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });

        // ------------------------------------ 注册 java.net.http.HttpClient ------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
                    if (resourceName.endsWith(".1.1")) {
                        builder.version(HttpClient.Version.HTTP_1_1);
                    } else if (resourceName.endsWith(".2")) {
                        builder.version(HttpClient.Version.HTTP_2);
                    }
                    java.net.http.HttpClient httpClient = builder.build();
                    if (field != null) {
                        field.set(srcObj, httpClient);
                    }
                    rf.inject(resourceName, httpClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, java.net.http.HttpClient.class, httpClient);
                    return httpClient;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, java.net.http.HttpClient.class.getSimpleName() + " inject error", e);
                    return null;
                }
            }

            @Override
            public Type resourceType() {
                return java.net.http.HttpClient.class;
            }
        });
        // ------------------------------------ 注册 WebClient ------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    WebClient webClient = WebClient.create(workExecutor, clientAsyncGroup);
                    if (field != null) {
                        field.set(srcObj, webClient);
                    }
                    rf.inject(resourceName, webClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, WebClient.class, webClient);
                    return webClient;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, WebClient.class.getSimpleName() + " inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return WebClient.class;
            }
        });
        // ------------------------------------ 注册 HttpRpcClient ------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    ClusterAgent clusterAgent = resourceFactory.find("", ClusterAgent.class);
                    MessageAgent messageAgent = resourceFactory.find(resourceName, MessageAgent.class);
                    if (messageAgent != null) {
                        if (clusterAgent == null
                                || !Objects.equals(clusterAgent.getName(), resourceName)
                                || messageAgent.isRpc()) {
                            HttpRpcClient rpcClient = messageAgent.getHttpRpcClient();
                            if (field != null) {
                                field.set(srcObj, rpcClient);
                            }
                            rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                            rf.register(resourceName, HttpRpcClient.class, rpcClient);
                            return rpcClient;
                        }
                    }
                    if (clusterAgent == null) {
                        HttpRpcClient rpcClient = new HttpLocalRpcClient(application, resourceName);
                        if (field != null) {
                            field.set(srcObj, rpcClient);
                        }
                        rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                        rf.register(resourceName, HttpRpcClient.class, rpcClient);
                        return rpcClient;
                    }
                    HttpRpcClient rpcClient = new HttpClusterRpcClient(application, resourceName, clusterAgent);
                    if (field != null) {
                        field.set(srcObj, rpcClient);
                    }
                    rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, HttpRpcClient.class, rpcClient);
                    return rpcClient;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, HttpRpcClient.class.getSimpleName() + " inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return HttpRpcClient.class;
            }
        });
        // 加载Configuration
        ClassFilter<?> resConfigFilter = new ClassFilter(this.getClassLoader(), Configuration.class, Object.class);
        ClassFilter<ResourceAnnotationLoader> resAnnFilter =
                new ClassFilter(this.getClassLoader(), ResourceAnnotationLoader.class);
        ClassFilter<ResourceTypeLoader> resTypeFilter =
                new ClassFilter(this.getClassLoader(), ResourceTypeLoader.class);

        loadClassByFilters(resConfigFilter, resAnnFilter, resTypeFilter);
        { // Configuration
            StringBuilder sb = new StringBuilder();
            resConfigFilter.getFilterEntrys().forEach(en -> {
                AutoLoad auto = en.getType().getAnnotation(AutoLoad.class);
                if (Modifier.isPublic(en.getType().getModifiers()) && (auto == null || auto.value())) {
                    int c = resourceFactory.registerConfiguration(en.getType());
                    sb.append("Load Configuration (type=")
                            .append(en.getType().getName())
                            .append(") ")
                            .append(c)
                            .append(" resources\r\n");
                }
            });
            if (sb.length() > 0) {
                logger.log(Level.INFO, sb.toString().trim());
            }
        }
        { // ResourceAnnotationLoader
            StringBuilder sb = new StringBuilder();
            resAnnFilter.getFilterEntrys().forEach(en -> {
                AutoLoad auto = en.getType().getAnnotation(AutoLoad.class);
                if (Modifier.isPublic(en.getType().getModifiers()) && (auto == null || auto.value())) {
                    ResourceAnnotationLoader loader =
                            Creator.create(en.getType()).create();
                    resourceFactory.register(loader);
                    sb.append("Load ResourceAnnotationLoader (type=")
                            .append(en.getType().getName())
                            .append(", annotation=")
                            .append(loader.annotationType().getName())
                            .append(")\r\n");
                }
            });
            if (sb.length() > 0) {
                logger.log(Level.INFO, sb.toString().trim());
            }
        }
        { // ResourceTypeLoader
            StringBuilder sb = new StringBuilder();
            resTypeFilter.getFilterEntrys().forEach(en -> {
                AutoLoad auto = en.getType().getAnnotation(AutoLoad.class);
                if (Modifier.isPublic(en.getType().getModifiers()) && (auto == null || auto.value())) {
                    ResourceTypeLoader loader = Creator.create(en.getType()).create();
                    resourceFactory.register(loader);
                    sb.append("Load ResourceTypeLoader (type=")
                            .append(en.getType().getName())
                            .append(")\r\n");
                }
            });
            if (sb.length() > 0) {
                logger.log(Level.INFO, sb.toString().trim());
            }
        }
    }

    private void registerResourceEnvs(boolean first, Properties... envs) {
        for (Properties props : envs) {
            props.forEach((k, v) -> {
                String val = environment.getPropertyValue(v.toString(), envs);
                if (k.toString().startsWith("system.property.")) {
                    String key = k.toString().substring("system.property.".length());
                    if (System.getProperty(key) == null || !first) {
                        System.setProperty(key, val);
                    }
                    resourceFactory.register(!first, k.toString(), val);
                } else if (k.toString().startsWith("mimetype.property.")) {
                    MimeType.add(k.toString().substring("mimetype.property.".length()), val);
                } else {
                    resourceFactory.register(!first, k.toString(), val);
                }
            });
        }
    }

    /** 加载错误码 */
    private void initRetCodes() throws IOException {
        ClassFilter<RetCodes> filter = new ClassFilter(this.getClassLoader(), RetCodes.class);
        loadClassByFilters(filter);
        StringBuilder sb = new StringBuilder();
        filter.getFilterEntrys().forEach(en -> {
            if (en.getType() != RetCodes.class) {
                AutoLoad auto = en.getType().getAnnotation(AutoLoad.class);
                if (auto == null || auto.value()) {
                    int c = RetCodes.load(en.getType());
                    sb.append("Load RetCodes (type=")
                            .append(en.getType().getName())
                            .append(") ")
                            .append(c)
                            .append(" records\r\n");
                }
            }
        });
        if (sb.length() > 0) {
            logger.log(Level.INFO, sb.toString().trim());
        }
    }

    /** 设置WorkExecutor */
    private void initWorkExecutor() {
        int bufferCapacity = 32 * 1024;
        int bufferPoolSize = ByteBufferPool.DEFAULT_BUFFER_POOL_SIZE;
        final AnyValue executorConf = config.getAnyValue("executor", true);
        StringBuilder executorLog = new StringBuilder();

        int confThreads = executorConf.getIntValue("threads", WorkThread.DEFAULT_WORK_POOL_SIZE);
        final int workThreads = Math.max(Utility.cpus(), confThreads);
        // 指定threads则不使用虚拟线程池
        this.workExecutor = executorConf.getValue("threads") != null
                ? WorkThread.createExecutor(workThreads, "Redkale-WorkThread-%s")
                : WorkThread.createWorkExecutor(workThreads, "Redkale-WorkThread-%s");
        String executorName = this.workExecutor.getClass().getSimpleName();
        executorLog.append("defaultWorkExecutor: {type=").append(executorName);
        if (executorName.contains("VirtualExecutor") || executorName.contains("PerTaskExecutor")) {
            executorLog.append(", threads=[virtual]}");
        } else {
            executorLog.append(", threads=").append(workThreads).append("}");
        }

        ExecutorService clientWorkExecutor = this.workExecutor;
        if (executorName.contains("VirtualExecutor") || executorName.contains("PerTaskExecutor")) {
            executorLog.append(", clientWorkExecutor: [workExecutor]");
        } else {
            // 给所有client给一个新的默认ExecutorService
            int clientThreads = executorConf.getIntValue("clients", WorkThread.DEFAULT_WORK_POOL_SIZE);
            clientWorkExecutor = WorkThread.createWorkExecutor(clientThreads, "Redkale-DefaultClient-WorkThread-%s");
            executorLog.append(", threads=").append(clientThreads).append("}");
        }
        {
            AsyncIOGroup ioGroup = new AsyncIOGroup(
                            "Redkale-DefaultClient-IOThread-%s", clientWorkExecutor, bufferCapacity, bufferPoolSize)
                    .skipClose(true);
            this.clientAsyncGroup = ioGroup.start();
        }
        if (executorLog.length() > 0) {
            logger.log(Level.INFO, executorLog.toString());
        }
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, Executor.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, ExecutorService.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_CLIENT_ASYNCGROUP, AsyncGroup.class, this.clientAsyncGroup);
        this.resourceFactory.register(RESNAME_APP_CLIENT_ASYNCGROUP, AsyncIOGroup.class, this.clientAsyncGroup);
    }

    private void initAppListeners() throws Exception {
        // ------------------------------------------------------------------------
        for (AnyValue conf : config.getAnyValues("group")) {
            final String group = conf.getValue("name", "");
            if (group.indexOf('$') >= 0 || group.indexOf('.') >= 0) {
                throw new RedkaleException("<group> name cannot contains '$', '.' in " + group);
            }
            final String protocol = conf.getValue("protocol", "TCP").toUpperCase();
            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                throw new RedkaleException("Not supported Transport Protocol " + conf.getValue("protocol"));
            }
            SncpRpcGroup rg = sncpRpcGroups.computeIfAbsent(group, protocol);
            String nodes = conf.getValue("nodes");
            if (Utility.isNotEmpty(nodes)) {
                for (String node : nodes.replace(',', ';').split(";")) {
                    if (Utility.isNotBlank(node)) {
                        int pos = node.indexOf(':');
                        String addr = node.substring(0, pos).trim();
                        int port = Integer.parseInt(node.substring(pos + 1).trim());
                        rg.putAddress(new InetSocketAddress(addr, port));
                    }
                }
            }
            for (AnyValue node : conf.getAnyValues("node")) {
                rg.putAddress(new InetSocketAddress(node.getValue("addr"), node.getIntValue("port")));
            }
        }
        for (AnyValue conf : config.getAnyValues("listener")) {
            final String listenClass = conf.getValue("value", "");
            if (listenClass.isEmpty()) {
                continue;
            }
            Class clazz = classLoader.loadClass(listenClass);
            if (!ApplicationListener.class.isAssignableFrom(clazz)) {
                continue;
            }
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            @SuppressWarnings("unchecked")
            ApplicationListener listener =
                    (ApplicationListener) clazz.getDeclaredConstructor().newInstance();
            resourceFactory.inject(listener);
            listener.init(config);
            this.listeners.add(listener);
        }
        // ------------------------------------------------------------------------
    }

    private static String colorMessage(Logger logger, int color, int type, String msg) {
        final boolean linux =
                System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("linux");
        if (linux) { // Windows PowerShell 也能正常着色
            boolean supported = true;
            Logger l = logger;
            do {
                if (l.getHandlers() == null) {
                    break;
                }
                for (Handler handler : l.getHandlers()) {
                    if (!(handler instanceof ConsoleHandler)) {
                        supported = false;
                        break;
                    }
                }
                if (!supported) {
                    break;
                }
            } while ((l = l.getParent()) != null);
            // colour  颜色代号：背景颜色代号(41-46)；前景色代号(31-36)
            // type    样式代号：0无；1加粗；3斜体；4下划线
            // String.format("\033[%d;%dm%s\033[0m", colour, type, content)
            if (supported) {
                msg = "\033[" + color + (type > 0 ? (";" + type) : "") + "m" + msg + "\033[0m";
            }
        }
        return msg;
    }

    private void startSelfServer() throws Exception {
        if (config.getValue("port", "").isEmpty() || "0".equals(config.getValue("port"))) {
            return; // 没有配置port则不启动进程自身的监听
        }
        final Application application = this;
        new Thread() {
            {
                setName("Redkale-Application-SelfServer-Thread");
            }

            @Override
            public void run() {
                try {
                    DatagramChannel channel = DatagramChannel.open();
                    channel.configureBlocking(true);
                    channel.socket().setSoTimeout(6000); // 单位:毫秒
                    channel.bind(new InetSocketAddress("127.0.0.1", config.getIntValue("port")));
                    if (!singletonMode) {
                        signalShutdownHandle();
                    }
                    boolean loop = true;
                    final ByteBuffer buffer = ByteBuffer.allocateDirect(UDP_CAPACITY);
                    while (loop) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        SocketAddress address = readUdpData(channel, buffer, out);
                        String[] args =
                                JsonConvert.root().convertFrom(String[].class, out.toString(StandardCharsets.UTF_8));
                        final String cmd = args[0];
                        String[] params = args.length == 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
                        // 接收到命令必须要有回应, 无结果输出则回应回车换行符
                        if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                            try {
                                long s = System.currentTimeMillis();
                                logger.info(application.getClass().getSimpleName() + " shutdowning");
                                application.shutdown();
                                sendUdpData(
                                        channel,
                                        address,
                                        buffer,
                                        "--- shutdown finish ---".getBytes(StandardCharsets.UTF_8));
                                long e = System.currentTimeMillis() - s;
                                logger.info(application.getClass().getSimpleName() + " shutdown in " + e + " ms");
                                channel.close();
                            } catch (Exception ex) {
                                logger.log(Level.INFO, "Shutdown fail", ex);
                                sendUdpData(channel, address, buffer, "shutdown fail".getBytes(StandardCharsets.UTF_8));
                            } finally {
                                loop = false;
                                application.shutdownLatch.countDown();
                            }
                        } else if ("APIDOC".equalsIgnoreCase(cmd)) {
                            try {
                                String rs = new ApiDocCommand(application).command(cmd, params);
                                if (rs == null || rs.isEmpty()) {
                                    rs = "\r\n";
                                }
                                sendUdpData(channel, address, buffer, rs.getBytes(StandardCharsets.UTF_8));
                            } catch (Exception ex) {
                                sendUdpData(channel, address, buffer, "apidoc fail".getBytes(StandardCharsets.UTF_8));
                            }
                        } else {
                            long s = System.currentTimeMillis();
                            logger.info(application.getClass().getSimpleName() + " command " + cmd);
                            List<Object> rs = application.command(cmd, params);
                            StringBuilder sb = new StringBuilder();
                            if (rs != null) {
                                for (Object o : rs) {
                                    if (o instanceof CharSequence) {
                                        sb.append(o).append("\r\n");
                                    } else {
                                        sb.append(JsonConvert.root().convertTo(o))
                                                .append("\r\n");
                                    }
                                }
                            }
                            if (sb.length() == 0) {
                                sb.append("\r\n");
                            }
                            sendUdpData(channel, address, buffer, sb.toString().getBytes(StandardCharsets.UTF_8));
                            long e = System.currentTimeMillis() - s;
                            logger.info(application.getClass().getSimpleName() + " command in " + e + " ms");
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.INFO, "Control fail", e);
                    Utility.sleep(100);
                    System.exit(1);
                }
            }
        }.start();
    }

    // 数据包前4个字节为数据内容的长度
    private static void sendUdpData(final DatagramChannel channel, SocketAddress dest, ByteBuffer buffer, byte[] bytes)
            throws IOException {
        buffer.clear();
        int count = (bytes.length + 4) / UDP_CAPACITY + ((bytes.length + 4) % UDP_CAPACITY > 0 ? 1 : 0);
        int start = 0;
        for (int i = 0; i < count; i++) {
            if (start == 0) {
                buffer.putInt(bytes.length);
            }
            int len = Math.min(buffer.remaining(), bytes.length - start);
            buffer.put(bytes, start, len);
            buffer.flip();
            boolean first = true;
            while (buffer.hasRemaining()) {
                if (!first) {
                    Utility.sleep(10);
                }
                if (dest == null) {
                    channel.write(buffer);
                } else {
                    channel.send(buffer, dest);
                }
                first = false;
            }
            start += len;
            buffer.clear();
        }
    }

    private static SocketAddress readUdpData(
            final DatagramChannel channel, ByteBuffer buffer, ByteArrayOutputStream out) throws IOException {
        out.reset();
        buffer.clear();
        SocketAddress src = null;
        if (channel.isConnected()) {
            channel.read(buffer);
            while (buffer.position() < 4) channel.read(buffer);
        } else {
            src = channel.receive(buffer);
            while (buffer.position() < 4) src = channel.receive(buffer);
        }
        buffer.flip();
        final int dataSize = buffer.getInt();
        while (buffer.hasRemaining()) {
            out.write(buffer.get());
        }
        while (out.size() < dataSize) {
            buffer.clear();
            channel.receive(buffer);
            buffer.flip();
            while (buffer.hasRemaining()) {
                out.write(buffer.get());
            }
        }
        return src;
    }

    private static void sendCommand(Logger logger, int port, String cmd, String[] params) throws Exception {
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.socket().setSoTimeout(6000); // 单位:毫秒
        SocketAddress dest = new InetSocketAddress("127.0.0.1", port);
        channel.connect(dest);
        // 命令和参数合成一个数组
        String[] args = new String[1 + (params == null ? 0 : params.length)];
        args[0] = cmd;
        if (params != null) {
            System.arraycopy(params, 0, args, 1, params.length);
        }
        final ByteBuffer buffer = ByteBuffer.allocateDirect(UDP_CAPACITY);
        try {
            sendUdpData(channel, dest, buffer, JsonConvert.root().convertToBytes(args));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            readUdpData(channel, buffer, out);
            channel.close();
            String rs = out.toString(StandardCharsets.UTF_8).trim();
            if (logger != null) {
                logger.info("Send: " + cmd + ", Reply: " + rs);
            }
            (System.out).println(rs);
        } catch (Exception e) {
            if (e instanceof PortUnreachableException) {
                if ("APIDOC".equalsIgnoreCase(cmd)) {
                    final Application application = new Application(AppConfig.create(false, true));
                    application.init();
                    application.start();
                    String rs = new ApiDocCommand(application).command(cmd, params);
                    application.shutdown();
                    if (logger != null) {
                        logger.info(rs);
                    }
                    (System.out).println(rs);
                    return;
                }
                // if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                //    System.out .println("--- application not running ---");
                // } else {
                (System.err).println("--- application not running ---");
                // }
                return;
            }
            throw e;
        }
    }

    AsmMethodBoost createAsmMethodBoost(boolean remote, Class serviceClass) {
        List<AsmMethodBoost> list = null;
        for (ModuleEngine item : moduleEngines) {
            AsmMethodBoost boost = item.createAsmMethodBoost(remote, serviceClass);
            if (boost != null) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(boost);
            }
        }
        if (list == null) {
            return null;
        }
        Utility.sortPriority(list);
        return list.size() == 1 ? list.get(0) : AsmMethodBoost.create(remote, list);
    }

    /** 进入Application.init方法时被调用 */
    private void onAppPreInit() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPreInit();
        }
    }

    /** 结束Application.init方法前被调用 */
    private void onAppPostInit() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostInit();
        }
    }

    /** 进入Application.start方法被调用 */
    private void onAppPreStart() {
        for (ApplicationListener listener : this.listeners) {
            listener.onPreStart(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onAppPreStart();
        }
    }

    /** 结束Application.start方法前被调用 */
    private void onAppPostStart() {
        for (ApplicationListener listener : this.listeners) {
            listener.onPostStart(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostStart();
        }
    }

    /**
     * 配置项加载后被调用
     *
     * @param props 配置项全量
     */
    private void onEnvironmentLoaded() {
        this.registerResourceEnvs(true, this.envProperties);
        for (ModuleEngine item : moduleEngines) {
            item.onEnvironmentLoaded(this.envProperties);
        }
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events 变更项
     */
    void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        for (ModuleEngine item : moduleEngines) {
            item.onEnvironmentChanged(namespace, events);
        }
    }

    /** 服务全部启动前被调用 */
    private void onServersPreStart() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPreStart();
        }
    }

    /** 服务全部启动后被调用 */
    private void onServersPostStart() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPostStart();
        }
    }

    /** 执行Service.init方法前被调用 */
    void onServicePreInit(NodeServer server, Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePreInit(server, service);
        }
    }

    /** 执行Service.init方法后被调用 */
    void onServicePostInit(NodeServer server, Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePostInit(server, service);
        }
    }

    /** 执行Service.destroy方法前被调用 */
    void onServicePreDestroy(NodeServer server, Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePreDestroy(server, service);
        }
    }

    /** 执行Service.destroy方法后被调用 */
    void onServicePostDestroy(NodeServer server, Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePostDestroy(server, service);
        }
    }

    /** 服务全部停掉前被调用 */
    private void onServersPreStop() {
        for (ApplicationListener listener : listeners) {
            listener.onServersPreStop(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onServersPreStop();
        }
    }

    /** 服务全部停掉后被调用 */
    private void onServersPostStop() {
        for (ApplicationListener listener : listeners) {
            listener.onServersPostStop(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onServersPostStop();
        }
    }

    /** 进入Application.shutdown方法被调用 */
    private void onAppPreShutdown() {
        for (ApplicationListener listener : this.listeners) {
            try {
                listener.onPreShutdown(this);
            } catch (Exception e) {
                logger.log(Level.WARNING, listener.getClass() + " preShutdown erroneous", e);
            }
        }
        for (ModuleEngine item : moduleEngines) {
            item.onAppPreShutdown();
        }
    }

    /** 结束Application.shutdown方法前被调用 */
    private void onAppPostShutdown() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostShutdown();
        }
    }

    void onPreCompile() {
        for (ApplicationListener listener : listeners) {
            listener.onPreCompile(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onPreCompile();
        }
    }

    void onPostCompile() {
        for (ApplicationListener listener : listeners) {
            listener.onPostCompile(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onPostCompile();
        }
    }

    /**
     * 启动
     *
     * @throws Exception 异常
     */
    public void start() throws Exception {
        this.onAppPreStart();
        final AnyValue[] entrys = config.getAnyValues("server");
        CountDownLatch serverCdl = new CountDownLatch(entrys.length);
        final List<AnyValue> sncps = new ArrayList<>();
        final List<AnyValue> others = new ArrayList<>();
        final List<AnyValue> watchs = new ArrayList<>();
        for (final AnyValue entry : entrys) {
            if (entry.getValue("protocol", "").toUpperCase().startsWith("SNCP")) {
                sncps.add(entry);
            } else if (entry.getValue("protocol", "").toUpperCase().startsWith("WATCH")) {
                watchs.add(entry);
            } else {
                others.add(entry);
            }
        }
        if (watchs.size() > 1) {
            throw new RedkaleException("Found one more WATCH Server");
        }
        this.watching = !watchs.isEmpty();

        this.onServersPreStart();
        runServers(serverCdl, sncps); // 必须确保SNCP服务都启动后再启动其他服务
        runServers(serverCdl, others);
        runServers(serverCdl, watchs); // 必须在所有服务都启动后再启动WATCH服务
        serverCdl.await();
        this.onServersPostStart();

        this.onAppPostStart();
        long intms = System.currentTimeMillis() - startTime;
        String ms = String.valueOf(intms);
        int repeat = ms.length() > 7 ? 0 : (7 - ms.length()) / 2;
        logger.info(colorMessage(
                        logger,
                        36,
                        1,
                        "-".repeat(repeat) + "------------------------ Redkale started in " + ms + " ms "
                                + (ms.length() / 2 == 0 ? " " : "") + "-".repeat(repeat) + "------------------------")
                + "\r\n");
        LoggingBaseHandler.traceEnable = true;

        if (!singletonMode && !compileMode) {
            this.shutdownLatch.await();
        }
    }

    /**
     * 实例化单个Service
     *
     * @param <T> 泛型
     * @param serviceClass 指定的service类
     * @param extServiceClasses 需要排除的service类
     * @return Service对象
     * @throws Exception 异常
     */
    public static <T extends Service> T singleton(Class<T> serviceClass, Class<? extends Service>... extServiceClasses)
            throws Exception {
        return singleton("", serviceClass, extServiceClasses);
    }

    /**
     * 实例化单个Service
     *
     * @param <T> 泛型
     * @param name Service的资源名
     * @param serviceClass 指定的service类
     * @param extServiceClasses 需要排除的service类
     * @return Service对象
     * @throws Exception 异常
     */
    public static <T extends Service> T singleton(
            String name, Class<T> serviceClass, Class<? extends Service>... extServiceClasses) throws Exception {
        if (serviceClass == null) {
            throw new IllegalArgumentException("serviceClass is null");
        }
        final Application application = Application.create(true);
        System.setProperty("redkale.singleton.serviceclass", serviceClass.getName());
        if (extServiceClasses != null && extServiceClasses.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Class clazz : extServiceClasses) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(clazz.getName());
            }
            System.setProperty("redkale.singleton.extserviceclasses", sb.toString());
        }
        application.init();
        application.start();
        for (NodeServer server : application.servers) {
            T service = server.resourceFactory.find(name, serviceClass);
            if (service != null) {
                return service;
            }
        }
        if (Modifier.isAbstract(serviceClass.getModifiers())) {
            throw new IllegalArgumentException("abstract class not allowed");
        }
        if (serviceClass.isInterface()) {
            throw new IllegalArgumentException("interface class not allowed");
        }
        throw new IllegalArgumentException(serviceClass.getName() + " maybe have zero not-final public method");
    }

    public static Application create(final boolean singleton) throws IOException {
        return new Application(AppConfig.create(singleton, false));
    }

    public static void main(String[] args) throws Exception {
        Times.midnight(); // 先初始化一下Utility
        Thread.currentThread().setName("Redkale-Application-Main-Thread");
        // 运行主程序
        String cmd = System.getProperty("cmd", System.getProperty("CMD"));
        String[] params = args;
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && args[i].toLowerCase().startsWith("--conf-file=")) {
                    System.setProperty(AppConfig.PARAM_APP_CONF_FILE, args[i].substring("--conf-file=".length()));
                    String[] newargs = new String[args.length - 1];
                    System.arraycopy(args, 0, newargs, 0, i);
                    System.arraycopy(args, i + 1, newargs, i, args.length - 1 - i);
                    args = newargs;
                    break;
                }
            }
            if (cmd == null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && !args[i].startsWith("-")) { // 非-开头的第一个视为命令号
                        cmd = args[i];
                        if ("start".equalsIgnoreCase(cmd) || "startup".equalsIgnoreCase(cmd)) {
                            cmd = null;
                        }
                        params = new String[args.length - 1];
                        System.arraycopy(args, 0, params, 0, i);
                        System.arraycopy(args, i + 1, params, i, args.length - 1 - i);
                        break;
                    }
                }
            }
            if (cmd == null) {
                if (args.length == 1 && ("--help".equalsIgnoreCase(args[0]) || "-h".equalsIgnoreCase(args[0]))) {
                    cmd = args[0];
                }
            }
        }
        if (cmd != null) {
            if ("stop".equalsIgnoreCase(cmd)) {
                cmd = "shutdown";
            }
            if ("help".equalsIgnoreCase(cmd) || "--help".equalsIgnoreCase(cmd) || "-h".equalsIgnoreCase(cmd)) {
                (System.out).println(generateHelp());
                return;
            }
            boolean restart = "restart".equalsIgnoreCase(cmd);
            AnyValue config = AppConfig.loadAppConfig();
            Application.sendCommand(null, config.getIntValue("port"), restart ? "SHUTDOWN" : cmd, params);
            if (!restart) {
                return;
            }
        }

        // PrepareCompiler.main(args); //测试代码
        final Application application = Application.create(false);
        application.init();
        application.startSelfServer();
        try {
            application.start();
        } catch (Exception e) {
            application.logger.log(Level.SEVERE, "Application start error", e);
            Utility.sleep(100);
            System.exit(1);
        }
        System.exit(0); // 必须要有
    }

    public List<Object> command(String cmd, String[] params) {
        List<NodeServer> localServers = new ArrayList<>(servers); // 顺序sncps, others, watchs
        List<Object> results = new ArrayList<>();
        localServers.stream().forEach(server -> {
            try {
                List<Object> rs = server.command(cmd, params);
                if (rs != null) {
                    results.addAll(rs);
                }
            } catch (Exception t) {
                logger.log(Level.WARNING, " command server(" + server.getSocketAddress() + ") error", t);
            }
        });
        return results;
    }

    public void shutdown() throws Exception {
        long f = System.currentTimeMillis();
        this.onAppPreShutdown();
        stopServers();
        this.propertiesModule.destroy();
        this.workExecutor.shutdownNow();
        if (this.clientAsyncGroup != null) {
            long s = System.currentTimeMillis();
            this.clientAsyncGroup.dispose();
            logger.info("default.client.AsyncGroup destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        this.onAppPostShutdown();

        long intms = System.currentTimeMillis() - f;
        String ms = String.valueOf(intms);
        int repeat = ms.length() > 7 ? 0 : (7 - ms.length()) / 2;
        logger.info(colorMessage(
                        logger,
                        36,
                        1,
                        "-".repeat(repeat) + "------------------------ Redkale shutdown in " + ms + " ms "
                                + (ms.length() / 2 == 0 ? " " : "") + "-".repeat(repeat) + "------------------------")
                + "\r\n" + "\r\n");
        LoggingBaseHandler.traceEnable = true;
    }

    private static String generateHelp() {
        return ""
                + "Usage: redkale [command] [arguments]\r\n"
                + "Command: \r\n"
                + "   start, startup                            start one process\r\n"
                + "       --conf-file=[file]                    application config file, eg. application.xml、application.properties\r\n"
                + "   shutdown, stop                            shutdown one process\r\n"
                + "       --conf-file=[file]                    application config file, eg. application.xml、application.properties\r\n"
                + "   restart                                   restart one process\r\n"
                + "       --conf-file=[file]                    application config file, eg. application.xml、application.properties\r\n"
                + "   apidoc                                    generate apidoc\r\n"
                + "       --api-skiprpc=[true|false]            skip @RestService(rpcOnly=true) service or @RestMapping(rpcOnly=true) method, default is true\r\n"
                + "       --api-host=[url]                      api root url, default is http://localhost\r\n"
                + "   help, -h, --help                          show this help\r\n";
    }

    @SuppressWarnings("unchecked")
    private void runServers(CountDownLatch serverCdl, final List<AnyValue> serverConfs) throws Exception {
        CountDownLatch serviceCdl = new CountDownLatch(serverConfs.size());
        CountDownLatch startCdl = new CountDownLatch(serverConfs.size());
        final AtomicBoolean inited = new AtomicBoolean(false);
        final ReentrantLock nodeLock = new ReentrantLock();
        final Map<String, Class<? extends NodeServer>> nodeClasses = new HashMap<>();
        for (final AnyValue serconf : serverConfs) {
            Thread thread = new Thread() {
                {
                    setName("Redkale-"
                            + serconf.getValue("protocol", "Server")
                                    .toUpperCase()
                                    .replaceFirst("\\..+", "") + ":" + serconf.getIntValue("port") + "-Thread");
                    this.setDaemon(true);
                }

                @Override
                public void run() {
                    try {
                        // Thread ctd = Thread.currentThread();
                        // ctd.setContextClassLoader(new URLClassLoader(new URL[0], ctd.getContextClassLoader()));
                        final String protocol = serconf.getValue("protocol", "")
                                .replaceFirst("\\..+", "")
                                .toUpperCase();
                        NodeServer server = null;
                        if ("SNCP".equals(protocol)) {
                            server = NodeSncpServer.createNodeServer(Application.this, serconf);
                        } else if ("WATCH".equalsIgnoreCase(protocol)) {
                            AnyValueWriter serconf2 = (AnyValueWriter) serconf;
                            AnyValueWriter rest = (AnyValueWriter) serconf2.getAnyValue("rest");
                            if (rest == null) {
                                rest = new AnyValueWriter();
                                serconf2.addValue("rest", rest);
                            }
                            rest.setValue("base", WatchServlet.class.getName());
                            server = new NodeWatchServer(Application.this, serconf);
                        } else if ("HTTP".equalsIgnoreCase(protocol)) {
                            server = new NodeHttpServer(Application.this, serconf);
                        } else {
                            if (!inited.get()) {
                                nodeLock.lock();
                                try {
                                    if (!inited.getAndSet(true)) { // 加载自定义的协议，如：SOCKS
                                        ClassFilter profilter =
                                                new ClassFilter(classLoader, NodeProtocol.class, NodeServer.class);
                                        loadClassByFilters(profilter);
                                        final Set<FilterEntry<NodeServer>> entrys = profilter.getFilterEntrys();
                                        for (FilterEntry<NodeServer> entry : entrys) {
                                            final Class<? extends NodeServer> type = entry.getType();
                                            NodeProtocol pros = type.getAnnotation(NodeProtocol.class);
                                            String p = pros.value().toUpperCase();
                                            if ("SNCP".equals(p) || "HTTP".equals(p)) {
                                                continue;
                                            }
                                            final Class<? extends NodeServer> old = nodeClasses.get(p);
                                            if (old != null && old != type) {
                                                throw new RedkaleException("Protocol(" + p + ") had NodeServer-Class("
                                                        + old.getName() + ") but repeat NodeServer-Class("
                                                        + type.getName() + ")");
                                            }
                                            nodeClasses.put(p, type);
                                        }
                                    }
                                } finally {
                                    nodeLock.unlock();
                                }
                            }
                            Class<? extends NodeServer> nodeClass = nodeClasses.get(protocol);
                            if (nodeClass != null) {
                                server = NodeServer.create(nodeClass, Application.this, serconf);
                            }
                        }
                        if (server == null) {
                            logger.log(
                                    Level.SEVERE,
                                    "Not found Server Class for protocol({0})",
                                    serconf.getValue("protocol"));
                            Utility.sleep(100);
                            System.exit(1);
                        }
                        server.serviceCdl = serviceCdl;
                        server.init(serconf);
                        if (!singletonMode && !compileMode) {
                            server.start();
                        } else if (compileMode) {
                            server.getServer()
                                    .getDispatcherServlet()
                                    .init(server.getServer().getContext(), serconf);
                        }
                        servers.add(server);
                        serverCdl.countDown();
                        startCdl.countDown();
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, serconf + " runServers error", ex);
                        Application.this.shutdownLatch.countDown();
                        Utility.sleep(100);
                        System.exit(1);
                    }
                }
            };
            thread.start();
        }
        startCdl.await();
    }

    private void stopServers() {
        this.onServersPreStop();
        List<NodeServer> localServers = new ArrayList<>(servers); // 顺序sncps, others, watchs
        Collections.reverse(localServers); // 倒序， 必须让watchs先关闭，watch包含服务发现和注销逻辑
        localServers.stream().forEach(server -> {
            try {
                server.shutdown();
            } catch (Exception t) {
                logger.log(Level.WARNING, " shutdown server(" + server.getSocketAddress() + ") error", t);
            } finally {
                shutdownLatch.countDown();
            }
        });
        this.onServersPostStop();
    }

    // 使用了nohup或使用了后台&，Runtime.getRuntime().addShutdownHook失效
    private void signalShutdownHandle() {
        Consumer<Consumer<String>> signalShutdownConsumer = Utility.signalShutdownConsumer();
        if (signalShutdownConsumer == null) {
            return;
        }
        signalShutdownConsumer.accept(sig -> {
            try {
                long s = System.currentTimeMillis();
                logger.info(Application.this.getClass().getSimpleName() + " shutdowning " + sig);
                shutdown();
                long e = System.currentTimeMillis() - s;
                logger.info(Application.this.getClass().getSimpleName() + " shutdown in " + e + " ms");
            } catch (Exception ex) {
                logger.log(Level.INFO, "Shutdown fail", ex);
            } finally {
                shutdownLatch.countDown();
            }
        });
    }

    List<ModuleEngine> getModuleEngines() {
        return moduleEngines;
    }

    public void loadClassByFilters(final ClassFilter... filters) {
        try {
            ClassFilter.Loader.load(getHome(), getClassLoader(), filters);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    public void loadServerClassFilters(final ClassFilter... filters) {
        try {
            ClassFilter.Loader.load(getHome(), getServerClassLoader(), filters);
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    public DataSource loadDataSource(final String sourceName, boolean autoMemory) {
        return sourceModule.loadDataSource(sourceName, autoMemory);
    }

    public CacheSource loadCacheSource(final String sourceName, boolean autoMemory) {
        return sourceModule.loadCacheSource(sourceName, autoMemory);
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    public boolean isVirtualWorkExecutor() {
        // JDK21+
        return workExecutor != null && workExecutor.getClass().getSimpleName().contains("ThreadPerTaskExecutor");
    }

    public AsyncGroup getClientAsyncGroup() {
        return clientAsyncGroup;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public RedkaleClassLoader getClassLoader() {
        return classLoader;
    }

    public RedkaleClassLoader getServerClassLoader() {
        return serverClassLoader;
    }

    public List<NodeServer> getNodeServers() {
        return new ArrayList<>(servers);
    }

    public SncpRpcGroups getSncpRpcGroups() {
        return sncpRpcGroups;
    }

    public String getNodeid() {
        return nodeid;
    }

    public String getName() {
        return name;
    }

    public File getHome() {
        return home;
    }

    public URI getConfDir() {
        return confDir;
    }

    public long getStartTime() {
        return startTime;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public AnyValue getAppConfig() {
        return config;
    }

    public boolean isCompileMode() {
        return compileMode;
    }

    public boolean isSingletonMode() {
        return singletonMode;
    }
}
