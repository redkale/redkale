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
import org.redkale.annotation.Nonnull;
import org.redkale.annotation.Resource;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cache.support.CacheModuleEngine;
import org.redkale.cluster.*;
import org.redkale.convert.Convert;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.convert.json.*;
import org.redkale.convert.protobuf.ProtobufFactory;
import org.redkale.mq.*;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.schedule.support.ScheduleModuleEngine;
import org.redkale.service.Service;
import org.redkale.source.*;
import org.redkale.util.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.watch.WatchServlet;

/**
 *
 * 进程启动类，全局对象。  <br>
 * <pre>
 * 程序启动执行步骤:
 *     1、读取application.xml
 *     2、进行classpath扫描动态加载Service、WebSocket与Servlet
 *     3、优先加载所有SNCP协议的服务，再加载其他协议服务， 最后加载WATCH协议的服务
 *     4、最后进行Service、Servlet与其他资源之间的依赖注入
 * </pre>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Application {

    /**
     * 当前进程启动的时间， 类型： long
     */
    public static final String RESNAME_APP_TIME = "APP_TIME";

    /**
     * 当前进程服务的名称， 类型：String
     */
    public static final String RESNAME_APP_NAME = "APP_NAME";

    /**
     * 当前进程的根目录， 类型：String、File、Path、URI
     */
    public static final String RESNAME_APP_HOME = "APP_HOME";

    /**
     * 当前进程的配置目录URI，如果不是绝对路径则视为HOME目录下的相对路径 类型：String、URI、File、Path <br>
     * 若配置目录不是本地文件夹， 则File、Path类型的值为null
     */
    public static final String RESNAME_APP_CONF_DIR = "APP_CONF_DIR";

    /**
     * 当前进程的配置文件， 类型：String、URI、File、Path <br>
     * 一般命名为: application.xml、application.properties， 若配置文件不是本地文件， 则File、Path类型的值为null
     */
    public static final String RESNAME_APP_CONF_FILE = "APP_CONF_FILE";

    /**
     * 当前进程节点的nodeid， 类型：int
     */
    public static final String RESNAME_APP_NODEID = "APP_NODEID";

    /**
     * 当前进程节点的IP地址， 类型：InetSocketAddress、InetAddress、String
     */
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
     *
     */
    public static final String RESNAME_APP_CLIENT_ASYNCGROUP = "APP_CLIENT_ASYNCGROUP";

    /**
     * 当前Service所属的SNCP Server的地址 类型: SocketAddress、InetSocketAddress、String <br>
     */
    public static final String RESNAME_SNCP_ADDRESS = "SNCP_ADDRESS";

    /**
     * 当前Service所属的SNCP Server所属的组 类型: String<br>
     */
    public static final String RESNAME_SNCP_GROUP = "SNCP_GROUP";

    /**
     * "SERVER_ROOT" 当前Server的ROOT目录类型：String、File、Path
     */
    public static final String RESNAME_SERVER_ROOT = Server.RESNAME_SERVER_ROOT;

    /**
     * 当前Server的ResourceFactory
     */
    public static final String RESNAME_SERVER_RESFACTORY = "SERVER_RESFACTORY";

    //UDP协议的ByteBuffer Capacity
    private static final int UDP_CAPACITY = 1024;

    //日志
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //本进程节点ID
    final int nodeid;

    //本进程节点ID
    final String name;

    //本地IP地址
    final InetSocketAddress localAddress;

    //日志配置资源
    //@since 2.8.0
    final Properties loggingProperties = new Properties();

    //NodeServer 资源, 顺序必须是sncps, others, watchs
    final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    //配置项里的group信息, 注意： 只给SNCP使用
    private final SncpRpcGroups sncpRpcGroups = new SncpRpcGroups();

    //业务逻辑线程池
    //@since 2.3.0
    @Nonnull
    private ExecutorService workExecutor;

    //给客户端使用，包含SNCP客户端、自定义数据库客户端连接池
    private AsyncIOGroup clientAsyncGroup;

    //配置源管理接口
    //@since 2.7.0
    private PropertiesAgent propertiesAgent;

    //只存放不以system.property.、mimetype.property.、redkale.开头的配置项
    private final Properties envProperties = new Properties();

    //envProperties更新锁
    private final ReentrantLock envPropertiesLock = new ReentrantLock();

    //配置信息，只读版Properties
    private final Environment environment;

    //是否从/META-INF中读取配置
    private final boolean configFromCache;

    //全局根ResourceFactory
    final ResourceFactory resourceFactory = ResourceFactory.create();

    //服务配置项
    final AnyValue config;

    //是否启动了WATCH协议服务
    boolean watching;

    //--------------------------------------------------------------------------------------------    
    //是否用于main方法运行
    private final boolean singletonMode;

    //是否用于编译模式运行
    private final boolean compileMode;

    //进程根目录
    private final File home;

    //进程根目录
    private final String homePath;

    //配置文件目录
    private final URI confPath;

    //监听事件
    private final List<ApplicationListener> listeners = new CopyOnWriteArrayList<>();

    //服务启动时间
    private final long startTime = System.currentTimeMillis();

    //Server启动的计数器，用于确保所有Server都启动完后再进行下一步处理
    private final CountDownLatch shutdownLatch;

    //根ClassLoader
    private final RedkaleClassLoader classLoader;

    //Server根ClassLoader
    private final RedkaleClassLoader serverClassLoader;

    //系统模块组件
    private final List<ModuleEngine> moduleEngines = new ArrayList<>();

    /**
     * 初始化步骤:  <br>
     * 1、基本环境变量设置 <br>
     * 2、ClassLoader初始化 <br>
     * 3、日志配置初始化 <br>
     * 4、本地和远程配置文件读取 <br>
     * 5、ClusterAgent和MessageAgent实例化 <br>
     * 6、Work线程池初始化
     * 7、原生sql解析器初始化 <br>
     *
     * @param singletonMode 是否测试模式
     * @param compileMode   是否编译模式
     * @param config        启动配置
     */
    @SuppressWarnings("UseSpecificCatch") //config: 不带redkale.前缀的配置项
    Application(final AppConfig appConfig) {
        this.singletonMode = appConfig.singletonMode;
        this.compileMode = appConfig.compileMode;
        this.config = appConfig.config;
        this.configFromCache = appConfig.configFromCache;
        this.environment = new Environment(this.envProperties);
        this.name = appConfig.name;
        this.nodeid = appConfig.nodeid;
        this.home = appConfig.home;
        this.homePath = appConfig.homePath;
        this.confPath = appConfig.confPath;
        this.localAddress = appConfig.localAddress;
        this.classLoader = appConfig.classLoader;
        this.serverClassLoader = appConfig.serverClassLoader;

        //设置基础信息资源
        this.resourceFactory.register(RESNAME_APP_NAME, String.class, this.name);
        this.resourceFactory.register(RESNAME_APP_NODEID, int.class, this.nodeid);
        this.resourceFactory.register(RESNAME_APP_NODEID, Integer.class, this.nodeid);
        this.resourceFactory.register(RESNAME_APP_TIME, long.class, this.startTime);
        this.resourceFactory.register(RESNAME_APP_TIME, Long.class, this.startTime);

        this.resourceFactory.register(RESNAME_APP_HOME, String.class, this.home.getPath());
        this.resourceFactory.register(RESNAME_APP_HOME, Path.class, this.home.toPath());
        this.resourceFactory.register(RESNAME_APP_HOME, File.class, this.home);
        this.resourceFactory.register(RESNAME_APP_HOME, URI.class, this.home.toURI());

        this.resourceFactory.register(RESNAME_APP_ADDR, InetSocketAddress.class, this.localAddress);
        this.resourceFactory.register(RESNAME_APP_ADDR, InetAddress.class, this.localAddress.getAddress());
        this.resourceFactory.register(RESNAME_APP_ADDR, String.class, this.localAddress.getAddress().getHostAddress());

        this.resourceFactory.register(RESNAME_APP_CONF_DIR, URI.class, this.confPath);
        this.resourceFactory.register(RESNAME_APP_CONF_DIR, File.class, appConfig.confFile);
        this.resourceFactory.register(RESNAME_APP_CONF_DIR, String.class, this.confPath.toString());

        System.setProperty("redkale.version", Redkale.getDotedVersion());
        System.setProperty("redkale.application.name", this.name);
        System.setProperty("redkale.application.nodeid", String.valueOf(this.nodeid));
        System.setProperty("redkale.application.home", this.home.getPath());
        System.setProperty("redkale.application.confPath", this.confPath.toString());

        //初始化本地配置的System.properties
        appConfig.localSysProperties.forEach((k, v) -> {
            String key = k.toString();
            if (System.getProperty(key) == null) {
                System.setProperty(key, getPropertyValue(v.toString(), appConfig.localSysProperties));
            }
        });

        //需要在加载properties初始化System.properties之后再注册
        this.resourceFactory.register(Environment.class, environment);
        this.resourceFactory.register(BsonFactory.root());
        this.resourceFactory.register(JsonFactory.root());
        this.resourceFactory.register(ProtobufFactory.root());
        this.resourceFactory.register(BsonFactory.root().getConvert());
        this.resourceFactory.register(JsonFactory.root().getConvert());
        this.resourceFactory.register(ProtobufFactory.root().getConvert());
        this.resourceFactory.register("bsonconvert", Convert.class, BsonFactory.root().getConvert());
        this.resourceFactory.register("jsonconvert", Convert.class, JsonFactory.root().getConvert());
        this.resourceFactory.register("protobufconvert", Convert.class, ProtobufFactory.root().getConvert());

        //系统内部模块组件
        moduleEngines.add(new SourceModuleEngine(this));
        moduleEngines.add(new MessageModuleEngine(this));
        moduleEngines.add(new ClusterModuleEngine(this));
        moduleEngines.add(new CacheModuleEngine(this));
        moduleEngines.add(new ScheduleModuleEngine(this));

        //根据本地日志配置文件初始化日志
        reconfigLogging(appConfig.locaLogProperties);

        //打印基础信息日志
        logger.log(Level.INFO, colorMessage(logger, 36, 1, "-------------------------------- Redkale " + Redkale.getDotedVersion() + " --------------------------------"));

        final String confDir = this.confPath.toString();
        logger.log(Level.INFO, "APP_OS       = " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\r\n"
            + "APP_JAVA     = " + System.getProperty("java.runtime.name", System.getProperty("org.graalvm.nativeimage.kind") != null ? "Nativeimage" : "")
            + " " + System.getProperty("java.runtime.version", System.getProperty("java.vendor.version", System.getProperty("java.vm.version"))) + "\r\n" //graalvm.nativeimage 模式下无 java.runtime.xxx 属性
            + "APP_PID      = " + ProcessHandle.current().pid() + "\r\n"
            + RESNAME_APP_NAME + "     = " + this.name + "\r\n"
            + RESNAME_APP_NODEID + "   = " + this.nodeid + "\r\n"
            + "APP_LOADER   = " + this.classLoader.getClass().getSimpleName() + "\r\n"
            + RESNAME_APP_ADDR + "     = " + this.localAddress.getHostString() + ":" + this.localAddress.getPort() + "\r\n"
            + RESNAME_APP_HOME + "     = " + homePath + "\r\n"
            + RESNAME_APP_CONF_DIR + " = " + confDir.substring(confDir.indexOf('!') + 1));

        if (!compileMode && !(classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader)) {
            String lib = getPropertyValue(config.getValue("lib", "${APP_HOME}/libs/*").trim());
            lib = Utility.isEmpty(lib) ? confDir : (lib + ";" + confDir);
            Server.loadLib(classLoader, logger, lib.isEmpty() ? confDir : (lib + ";" + confDir));
        }
        this.shutdownLatch = new CountDownLatch(config.getAnyValues("server").length + 1);
    }

    public void init() throws Exception {
        //注册ResourceType
        this.initResourceTypeLoader();
        this.onAppPreInit();
        //读取远程配置
        this.loadResourceProperties();
        //设置WorkExecutor    
        this.initWorkExecutor();
        initResources();
        //结束时回调
        this.onAppPostInit();
    }

    /**
     * 设置WorkExecutor
     */
    private void initWorkExecutor() {
        int bufferCapacity = 32 * 1024;
        int bufferPoolSize = Utility.cpus() * 8;
        final AnyValue executorConf = config.getAnyValue("executor", true);
        StringBuilder executorLog = new StringBuilder();

        final int workThreads = Math.max(Utility.cpus(), executorConf.getIntValue("threads", Utility.cpus() * 4));
        //指定threads则不使用虚拟线程池
        this.workExecutor = executorConf.getValue("threads") != null
            ? WorkThread.createExecutor(workThreads, "Redkale-WorkThread-%s")
            : WorkThread.createWorkExecutor(workThreads, "Redkale-WorkThread-%s");
        String executorName = this.workExecutor.getClass().getSimpleName();
        executorLog.append("defaultWorkExecutor: {type=" + executorName);
        if (executorName.contains("VirtualExecutor") || executorName.contains("PerTaskExecutor")) {
            executorLog.append(", threads=[virtual]}");
        } else {
            executorLog.append(", threads=" + workThreads + "}");
        }

        ExecutorService clientWorkExecutor = this.workExecutor;
        if (executorName.contains("VirtualExecutor") || executorName.contains("PerTaskExecutor")) {
            executorLog.append(", clientWorkExecutor: [workExecutor]");
        } else {
            //给所有client给一个新的默认ExecutorService
            int clientThreads = executorConf.getIntValue("clients", Utility.cpus() * 4);
            clientWorkExecutor = WorkThread.createWorkExecutor(clientThreads, "Redkale-DefaultClient-WorkThread-%s");
            executorLog.append(", threads=" + clientThreads + "}");
        }
        AsyncIOGroup ioGroup = new AsyncIOGroup("Redkale-DefaultClient-IOThread-%s", clientWorkExecutor, bufferCapacity, bufferPoolSize).skipClose(true);
        this.clientAsyncGroup = ioGroup.start();

        if (executorLog.length() > 0) {
            logger.log(Level.INFO, executorLog.toString());
        }
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, Executor.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, ExecutorService.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_CLIENT_ASYNCGROUP, AsyncGroup.class, this.clientAsyncGroup);
        this.resourceFactory.register(RESNAME_APP_CLIENT_ASYNCGROUP, AsyncIOGroup.class, this.clientAsyncGroup);
    }

    private void initResourceTypeLoader() {
        final Application application = this;
        //只有WatchService才能加载Application、WatchFactory
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) {
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
                    if (resName == null) {
                        return null;
                    }
                    if (srcObj instanceof Service && Sncp.isRemote((Service) srcObj)) {
                        return null; //远程模式不得注入 
                    }
                    Class type = field.getType();
                    if (type == Application.class) {
                        field.set(srcObj, application);
                        return application;
                    } else if (type == ResourceFactory.class) {
                        boolean serv = RESNAME_SERVER_RESFACTORY.equals(resName) || resName.equalsIgnoreCase("server");
                        ResourceFactory rs = serv ? rf : (resName.isEmpty() ? application.resourceFactory : null);
                        field.set(srcObj, rs);
                        return rs;
                    } else if (type == NodeSncpServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeSncpServer.class) {
                                continue;
                            }
                            if (resName.equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(srcObj, server);
                        return server;
                    } else if (type == NodeHttpServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeHttpServer.class) {
                                continue;
                            }
                            if (resName.equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(srcObj, server);
                        return server;
                    } else if (type == NodeWatchServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeWatchServer.class) {
                                continue;
                            }
                            if (resName.equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(srcObj, server);
                        return server;
                    }
//                    if (type == WatchFactory.class) {
//                        field.setex(src, application.watchFactory);
//                    }
                    return null;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                    return null;
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }

        }, Application.class, ResourceFactory.class, NodeSncpServer.class, NodeHttpServer.class, NodeWatchServer.class);

        //------------------------------------ 注册 java.net.http.HttpClient ------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
                if (resourceName.endsWith(".1.1")) {
                    builder.version(HttpClient.Version.HTTP_1_1);
                } else if (resourceName.endsWith(".2")) {
                    builder.version(HttpClient.Version.HTTP_2);
                }
                java.net.http.HttpClient httpClient = builder.build();
                field.set(srcObj, httpClient);
                rf.inject(resourceName, httpClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, java.net.http.HttpClient.class, httpClient);
                return httpClient;
            } catch (Exception e) {
                logger.log(Level.SEVERE, java.net.http.HttpClient.class.getSimpleName() + " inject error", e);
                return null;
            }
        }, java.net.http.HttpClient.class);
        //------------------------------------ 注册 HttpSimpleClient ------------------------------------       
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                HttpSimpleClient httpClient = HttpSimpleClient.create(workExecutor, clientAsyncGroup);
                field.set(srcObj, httpClient);
                rf.inject(resourceName, httpClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpSimpleClient.class, httpClient);
                return httpClient;
            } catch (Exception e) {
                logger.log(Level.SEVERE, HttpSimpleClient.class.getSimpleName() + " inject error", e);
                return null;
            }
        }, HttpSimpleClient.class);
        //------------------------------------ 注册 HttpRpcClient ------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                ClusterAgent clusterAgent = resourceFactory.find("", ClusterAgent.class);
                MessageAgent messageAgent = resourceFactory.find(resourceName, MessageAgent.class);
                if (messageAgent != null) {
                    if (clusterAgent == null || !Objects.equals(clusterAgent.getName(), resourceName)
                        || messageAgent.isRpcFirst()) {
                        HttpRpcClient rpcClient = messageAgent.getHttpRpcClient();
                        field.set(srcObj, rpcClient);
                        rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                        rf.register(resourceName, HttpRpcClient.class, rpcClient);
                        return rpcClient;
                    }
                }
                if (clusterAgent == null) {
                    HttpRpcClient rpcClient = new HttpLocalRpcClient(application, resourceName);
                    field.set(srcObj, rpcClient);
                    rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, HttpRpcClient.class, rpcClient);
                    return rpcClient;
                }
                HttpRpcClient rpcClient = new HttpClusterRpcClient(application, resourceName, clusterAgent);
                field.set(srcObj, rpcClient);
                rf.inject(resourceName, rpcClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpRpcClient.class, rpcClient);
                return rpcClient;
            } catch (Exception e) {
                logger.log(Level.SEVERE, HttpRpcClient.class.getSimpleName() + " inject error", e);
                return null;
            }
        }, HttpRpcClient.class);
    }

    private void initResources() throws Exception {
        //------------------------------------------------------------------------
        for (AnyValue conf : config.getAnyValues("group")) {
            final String group = conf.getValue("name", "");
            if (group.indexOf('$') >= 0) {
                throw new RedkaleException("<group> name cannot contains '$' in " + group);
            }
            final String protocol = conf.getValue("protocol", "TCP").toUpperCase();
            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                throw new RedkaleException("Not supported Transport Protocol " + conf.getValue("protocol"));
            }
            SncpRpcGroup rg = sncpRpcGroups.computeIfAbsent(group, protocol);
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
            ApplicationListener listener = (ApplicationListener) clazz.getDeclaredConstructor().newInstance();
            resourceFactory.inject(listener);
            listener.init(config);
            this.listeners.add(listener);
        }
        //------------------------------------------------------------------------
    }

    private void loadResourceProperties() {
        final Properties dyncProps = new Properties();
        final AtomicInteger propertyIndex = new AtomicInteger();
        Properties logProps = null; //新的日志配置项
        //------------------------------------ 读取本地DataSource、CacheSource配置 ------------------------------------
        if ("file".equals(this.confPath.getScheme())) {
            File sourceFile = new File(new File(confPath), "source.properties");
            if (sourceFile.isFile() && sourceFile.canRead()) {
                Properties props = new Properties();
                try {
                    InputStream in = new FileInputStream(sourceFile);
                    props.load(in);
                    in.close();
                } catch (IOException e) {
                    throw new RedkaleException(e);
                }
                props.forEach((key, val) -> {
                    if (key.toString().startsWith("redkale.datasource.") || key.toString().startsWith("redkale.datasource[")
                        || key.toString().startsWith("redkale.cachesource.") || key.toString().startsWith("redkale.cachesource[")) {
                        dyncProps.put(key, val);
                    } else {
                        logger.log(Level.WARNING, "skip illegal key " + key + " in source.properties");
                    }
                });
            } else {
                //兼容 persistence.xml 【已废弃】
                File persist = new File(new File(confPath), "persistence.xml");
                if (persist.isFile() && persist.canRead()) {
                    logger.log(Level.WARNING, "persistence.xml is deprecated, replaced by source.properties");
                    try {
                        InputStream in = new FileInputStream(persist);
                        dyncProps.putAll(DataSources.loadSourceProperties(in));
                        in.close();
                    } catch (IOException e) {
                        throw new RedkaleException(e);
                    }
                }
            }
        } else { //从url或jar文件中resources读取
            try {
                final URI sourceURI = RedkaleClassLoader.getConfResourceAsURI(configFromCache ? null : this.confPath.toString(), "source.properties");
                InputStream in = sourceURI.toURL().openStream();
                Properties props = new Properties();
                props.load(in);
                in.close();
                props.forEach((key, val) -> {
                    if (key.toString().startsWith("redkale.datasource.") || key.toString().startsWith("redkale.datasource[")
                        || key.toString().startsWith("redkale.cachesource.") || key.toString().startsWith("redkale.cachesource[")) {
                        dyncProps.put(key, val);
                    } else {
                        logger.log(Level.WARNING, "skip illegal key " + key + " in source.properties");
                    }
                });
            } catch (Exception e) { //没有文件 跳过
            }
            //兼容 persistence.xml 【已废弃】
            try {
                final URI xmlURI = RedkaleClassLoader.getConfResourceAsURI(configFromCache ? null : this.confPath.toString(), "persistence.xml");
                InputStream in = xmlURI.toURL().openStream();
                dyncProps.putAll(DataSources.loadSourceProperties(in));
                in.close();
                logger.log(Level.WARNING, "persistence.xml is deprecated, replaced by source.properties");
            } catch (Exception e) { //没有文件 跳过
            }
        }

        //------------------------------------ 读取配置项 ------------------------------------       
        AnyValue propertiesConf = config.getAnyValue("properties");
        if (propertiesConf == null) {
            final AnyValue resources = config.getAnyValue("resources");
            if (resources != null) {
                logger.log(Level.WARNING, "<resources> in application config file is deprecated");
                propertiesConf = resources.getAnyValue("properties");
            }
        }
        if (propertiesConf != null) {
            final Properties agentEnvs = new Properties();
            if (propertiesConf.getValue("load") != null) { //加载本地配置项文件
                for (String dfload : propertiesConf.getValue("load").replace(',', ';').split(";")) {
                    if (dfload.trim().isEmpty()) {
                        continue;
                    }
                    final URI df = RedkaleClassLoader.getConfResourceAsURI(configFromCache ? null : this.confPath.toString(), dfload.trim());
                    if (df != null && (!"file".equals(df.getScheme()) || df.toString().contains("!") || new File(df).isFile())) {
                        Properties ps = new Properties();
                        try {
                            InputStream in = df.toURL().openStream();
                            ps.load(in);
                            in.close();
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "Load properties(" + dfload + ") size = " + ps.size());
                            }
                            ps.forEach((x, y) -> { //load中的配置项除了redkale.cachesource.和redkale.datasource.开头，不应该有其他redkale.开头配置项
                                if (!x.toString().startsWith("redkale.")) {
                                    agentEnvs.put(x, y);
                                } else {
                                    logger.log(Level.WARNING, "skip illegal(startswith 'redkale.') key " + x + " in properties file");
                                }
                            });
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Load properties(" + dfload + ") error", e);
                        }
                    }
                }
            }
            { //可能通过系统环境变量配置信息
                Iterator<PropertiesAgentProvider> it = ServiceLoader.load(PropertiesAgentProvider.class, classLoader).iterator();
                RedkaleClassLoader.putServiceLoader(PropertiesAgentProvider.class);
                List<PropertiesAgentProvider> providers = new ArrayList<>();
                while (it.hasNext()) {
                    PropertiesAgentProvider provider = it.next();
                    if (provider != null && provider.acceptsConf(propertiesConf)) {
                        RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
                        providers.add(provider);
                    }
                }
                for (PropertiesAgentProvider provider : InstanceProvider.sort(providers)) {
                    long s = System.currentTimeMillis();
                    this.propertiesAgent = provider.createInstance();
                    this.resourceFactory.inject(this.propertiesAgent);
                    if (compileMode) {
                        this.propertiesAgent.compile(propertiesConf);
                    } else {
                        Map<String, Properties> propMap = this.propertiesAgent.init(this, propertiesConf);
                        int propCount = 0;
                        if (propMap != null) {
                            for (Map.Entry<String, Properties> en : propMap.entrySet()) {
                                propCount += en.getValue().size();
                                if (en.getKey().startsWith("logging")) {
                                    if (logProps != null) {
                                        logger.log(Level.WARNING, "skip repeat logging config properties(" + en.getKey() + ")");
                                    } else {
                                        logProps = en.getValue();
                                    }
                                } else {
                                    agentEnvs.putAll(en.getValue());
                                }
                            }
                        }
                        logger.info("PropertiesAgent (type = " + this.propertiesAgent.getClass().getSimpleName() + ") load " + propCount + " data in " + (System.currentTimeMillis() - s) + " ms");
                    }
                    break;
                }
            }

            final Properties oldEnvs = new Properties();
            for (AnyValue prop : propertiesConf.getAnyValues("property")) {
                String key = prop.getValue("name");
                String value = prop.getValue("value");
                if (key == null || value == null) {
                    continue;
                }
                oldEnvs.put(key, value);
            }
            agentEnvs.forEach((k, v) -> {
                if (k.toString().startsWith("redkale.")) {
                    dyncProps.put(k, v);
                } else {
                    oldEnvs.put(k, v);  //新配置项会覆盖旧的
                }
            });
            //原有properties节点上的属性同步到dyncEnvs
            propertiesConf.forEach((k, v) -> dyncProps.put("redkale.properties[" + k + "]", v));
            oldEnvs.forEach((k, v) -> { //去重后的配置项
                String prefix = "redkale.properties.property[" + propertyIndex.getAndIncrement() + "]";
                dyncProps.put(prefix + ".name", k);
                dyncProps.put(prefix + ".value", v);
            });
            //移除旧节点
            ((DefaultAnyValue) this.config).removeAnyValues("properties");
        }
        //环境变量的优先级最高
        System.getProperties().forEach((k, v) -> {
            if (k.toString().startsWith("redkale.executor.") //节点全局唯一
                || k.toString().startsWith("redkale.transport.") //节点全局唯一
                || k.toString().startsWith("redkale.cluster.") //节点全局唯一
                || k.toString().startsWith("redkale.mq.")
                || k.toString().startsWith("redkale.mq[")
                || k.toString().startsWith("redkale.group.")
                || k.toString().startsWith("redkale.group[")
                || k.toString().startsWith("redkale.listener.")
                || k.toString().startsWith("redkale.listener[")
                || k.toString().startsWith("redkale.server.")
                || k.toString().startsWith("redkale.server[")) {
                dyncProps.put(k, v);
            } else if (k.toString().startsWith("redkale.properties.")) {
                if (k.toString().startsWith("redkale.properties.property.")
                    || k.toString().startsWith("redkale.properties.property[")) {
                    dyncProps.put(k, v);
                } else {
                    //支持系统变量 -Dredkale.properties.mykey=my-value
                    String prefix = "redkale.properties.property[" + propertyIndex.getAndIncrement() + "]";
                    dyncProps.put(prefix + ".name", k.toString().substring("redkale.properties.".length()));
                    dyncProps.put(prefix + ".value", v);
                }
            }
        });

        if (!dyncProps.isEmpty()) {
            Properties newDyncProps = new Properties();
            dyncProps.forEach((k, v) -> newDyncProps.put(k.toString(), getPropertyValue(v.toString(), dyncProps)));
            //合并配置
            this.config.merge(AnyValue.loadFromProperties(newDyncProps).getAnyValue("redkale"), NodeServer.appConfigmergeFunction);
        }
        //使用合并后的新配置节点
        propertiesConf = this.config.getAnyValue("properties");
        if (propertiesConf != null) {
            //清除property节点数组的下坐标
            ((DefaultAnyValue) propertiesConf).clearParentArrayIndex("property");
            //注入配置项
            for (AnyValue prop : propertiesConf.getAnyValues("property")) {
                String key = prop.getValue("name");
                String value = prop.getValue("value");
                if (key == null) {
                    continue;
                }
                value = value == null ? value : getPropertyValue(value, dyncProps);
                if (key.startsWith("system.property.")) {
                    String propName = key.substring("system.property.".length());
                    if (System.getProperty(propName) == null) { //命令行传参数优先级高
                        System.setProperty(propName, value);
                    }
                } else if (key.startsWith("mimetype.property.")) {
                    MimeType.add(key.substring("mimetype.property.".length()), value);
                } else {
                    this.envProperties.put(key, value);
                    resourceFactory.register(false, key, value);
                }
            }
        }
        //重置日志配置
        if (logProps != null && !logProps.isEmpty()) {
            reconfigLogging(logProps);
        }
    }

    void reconfigLogging(Properties properties0) {
        String searchRawHandler = "java.util.logging.SearchHandler";
        String searchReadHandler = LoggingSearchHandler.class.getName();
        Properties properties = new Properties();
        properties0.entrySet().forEach(x -> {
            properties.put(x.getKey().toString().replace(searchRawHandler, searchReadHandler),
                x.getValue().toString()
                    .replace("%m", "%tY%tm").replace("%d", "%tY%tm%td") //兼容旧时间格式
                    .replace(searchRawHandler, searchReadHandler)
            );
        });
        if (properties.getProperty("java.util.logging.FileHandler.formatter") == null) {
            if (compileMode) {
                properties.setProperty("java.util.logging.FileHandler.formatter", SimpleFormatter.class.getName());
                if (properties.getProperty("java.util.logging.SimpleFormatter.format") == null) {
                    properties.setProperty("java.util.logging.SimpleFormatter.format", LoggingFileHandler.FORMATTER_FORMAT.replaceAll("\r\n", "%n"));
                }
            } else {
                properties.setProperty("java.util.logging.FileHandler.formatter", LoggingFileHandler.LoggingFormater.class.getName());
            }
        }
        if (properties.getProperty("java.util.logging.ConsoleHandler.formatter") == null) {
            if (compileMode) {
                properties.setProperty("java.util.logging.ConsoleHandler.formatter", SimpleFormatter.class.getName());
                if (properties.getProperty("java.util.logging.SimpleFormatter.format") == null) {
                    properties.setProperty("java.util.logging.SimpleFormatter.format", LoggingFileHandler.FORMATTER_FORMAT.replaceAll("\r\n", "%n"));
                }
            } else {
                properties.setProperty("java.util.logging.ConsoleHandler.formatter", LoggingFileHandler.LoggingFormater.class.getName());
            }
        }
        if (!compileMode) { //ConsoleHandler替换成LoggingConsoleHandler
            final String handlers = properties.getProperty("handlers");
            if (handlers != null && handlers.contains("java.util.logging.ConsoleHandler")) {
                final String consoleHandlerClass = LoggingFileHandler.LoggingConsoleHandler.class.getName();
                properties.setProperty("handlers", handlers.replace("java.util.logging.ConsoleHandler", consoleHandlerClass));
                Properties prop = new Properties();
                String prefix = consoleHandlerClass + ".";
                properties.entrySet().forEach(x -> {
                    if (x.getKey().toString().startsWith("java.util.logging.ConsoleHandler.")) {
                        prop.put(x.getKey().toString().replace("java.util.logging.ConsoleHandler.", prefix), x.getValue());
                    }
                });
                prop.entrySet().forEach(x -> {
                    properties.put(x.getKey(), x.getValue());
                });
            }
        }
        String fileHandlerPattern = properties.getProperty("java.util.logging.FileHandler.pattern");
        if (fileHandlerPattern != null && fileHandlerPattern.contains("%")) { //带日期格式
            final String fileHandlerClass = LoggingFileHandler.class.getName();
            Properties prop = new Properties();
            final String handlers = properties.getProperty("handlers");
            if (handlers != null && handlers.contains("java.util.logging.FileHandler")) {
                //singletonrun模式下不输出文件日志
                prop.setProperty("handlers", handlers.replace("java.util.logging.FileHandler", singletonMode || compileMode ? "" : fileHandlerClass));
            }
            if (!prop.isEmpty()) {
                String prefix = fileHandlerClass + ".";
                properties.entrySet().forEach(x -> {
                    if (x.getKey().toString().startsWith("java.util.logging.FileHandler.")) {
                        prop.put(x.getKey().toString().replace("java.util.logging.FileHandler.", prefix), x.getValue());
                    }
                });
                prop.entrySet().forEach(x -> properties.put(x.getKey(), x.getValue()));
            }
            if (!compileMode) {
                properties.put(SncpClient.class.getSimpleName() + ".handlers", LoggingFileHandler.LoggingSncpFileHandler.class.getName());
            }
        }
        if (compileMode) {
            properties.put("handlers", "java.util.logging.ConsoleHandler");
            Map newprop = new HashMap(properties);
            newprop.forEach((k, v) -> {
                if (k.toString().startsWith("java.util.logging.FileHandler.")) {
                    properties.remove(k);
                }
            });
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(out);
        properties.forEach((x, y) -> ps.println(x + "=" + y));
        try {
            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(new ByteArrayInputStream(out.toByteArray()));
            this.loggingProperties.clear();
            this.loggingProperties.putAll(properties);
            Enumeration<String> en = manager.getLoggerNames();
            while (en.hasMoreElements()) {
                for (Handler handler : manager.getLogger(en.nextElement()).getHandlers()) {
                    if (handler instanceof LoggingSearchHandler) {
                        ((LoggingSearchHandler) handler).application = this;
                    }
                }
            }
        } catch (IOException e) { //不会发生
        }
    }

    private static String colorMessage(Logger logger, int color, int type, String msg) {
        final boolean linux = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("linux");
        if (linux) { //Windows PowerShell 也能正常着色
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
            //colour  颜色代号：背景颜色代号(41-46)；前景色代号(31-36)
            //type    样式代号：0无；1加粗；3斜体；4下划线
            //String.format("\033[%d;%dm%s\033[0m", colour, type, content)
            if (supported) {
                msg = "\033[" + color + (type > 0 ? (";" + type) : "") + "m" + msg + "\033[0m";
            }
        }
        return msg;
    }

    private void startSelfServer() throws Exception {
        if (config.getValue("port", "").isEmpty() || "0".equals(config.getValue("port"))) {
            return; //没有配置port则不启动进程自身的监听
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
                    channel.socket().setSoTimeout(6000); //单位:毫秒
                    channel.bind(new InetSocketAddress("127.0.0.1", config.getIntValue("port")));
                    if (!singletonMode) {
                        signalShutdownHandle();
                    }
                    boolean loop = true;
                    final ByteBuffer buffer = ByteBuffer.allocateDirect(UDP_CAPACITY);
                    while (loop) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        SocketAddress address = readUdpData(channel, buffer, out);
                        String[] args = JsonConvert.root().convertFrom(String[].class, out.toString(StandardCharsets.UTF_8));
                        final String cmd = args[0];
                        String[] params = args.length == 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
                        //接收到命令必须要有回应, 无结果输出则回应回车换行符
                        if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                            try {
                                long s = System.currentTimeMillis();
                                logger.info(application.getClass().getSimpleName() + " shutdowning");
                                application.shutdown();
                                sendUdpData(channel, address, buffer, "--- shutdown finish ---".getBytes(StandardCharsets.UTF_8));
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
                                        sb.append(JsonConvert.root().convertTo(o)).append("\r\n");
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

    //数据包前4个字节为数据内容的长度
    private static void sendUdpData(final DatagramChannel channel, SocketAddress dest, ByteBuffer buffer, byte[] bytes) throws IOException {
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

    private static SocketAddress readUdpData(final DatagramChannel channel, ByteBuffer buffer, ByteArrayOutputStream out) throws IOException {
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
        channel.socket().setSoTimeout(6000); //单位:毫秒
        SocketAddress dest = new InetSocketAddress("127.0.0.1", port);
        channel.connect(dest);
        //命令和参数合成一个数组
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
                //if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                //    System.out .println("--- application not running ---");
                //} else {
                (System.err).println("--- application not running ---");
                //}
                return;
            }
            throw e;
        }
    }

    /**
     * 进入Application.init方法时被调用
     */
    private void onAppPreInit() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPreInit();
        }
    }

    /**
     * 结束Application.init方法前被调用
     */
    private void onAppPostInit() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostInit();
        }
    }

    /**
     * 进入Application.start方法被调用
     */
    private void onAppPreStart() {
        for (ApplicationListener listener : this.listeners) {
            listener.preStart(this);
        }
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostInit();
        }
    }

    /**
     * 结束Application.start方法前被调用
     */
    private void onAppPostStart() {
        for (ApplicationListener listener : this.listeners) {
            listener.postStart(this);
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
    private void onEnvironmentLoaded(Properties props) {
        for (ModuleEngine item : moduleEngines) {
            item.onEnvironmentLoaded(props);
        }
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events    变更项
     */
    private void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        for (ModuleEngine item : moduleEngines) {
            item.onEnvironmentChanged(namespace, events);
        }
    }

    /**
     * 服务全部启动前被调用
     */
    private void onServersPreStart() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPreStart();
        }
    }

    /**
     * 服务全部启动后被调用
     */
    private void onServersPostStart() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPostStart();
        }
    }

    /**
     * 执行Service.init方法前被调用
     */
    void onServicePreInit(Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePreInit(service);
        }
    }

    /**
     * 执行Service.init方法后被调用
     */
    void onServicePostInit(Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePostInit(service);
        }
    }

    /**
     * 执行Service.destroy方法前被调用
     */
    void onServicePreDestroy(Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePreDestroy(service);
        }
    }

    /**
     * 执行Service.destroy方法后被调用
     */
    void onServicePostDestroy(Service service) {
        for (ModuleEngine item : moduleEngines) {
            item.onServicePostDestroy(service);
        }
    }

    /**
     * 服务全部停掉前被调用
     */
    private void onServersPreStop() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPreStop();
        }
    }

    /**
     * 服务全部停掉后被调用
     */
    private void onServersPostStop() {
        for (ModuleEngine item : moduleEngines) {
            item.onServersPostStop();
        }
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    private void onAppPreShutdown() {
        for (ApplicationListener listener : this.listeners) {
            try {
                listener.preShutdown(this);
            } catch (Exception e) {
                logger.log(Level.WARNING, listener.getClass() + " preShutdown erroneous", e);
            }
        }
        for (ModuleEngine item : moduleEngines) {
            item.onAppPreShutdown();
        }
    }

    /**
     * 结束Application.shutdown方法前被调用
     */
    private void onAppPostShutdown() {
        for (ModuleEngine item : moduleEngines) {
            item.onAppPostShutdown();
        }
    }

    void onPreCompile() {
        for (ApplicationListener listener : listeners) {
            listener.preCompile(this);
        }
    }

    void onPostCompile() {
        for (ApplicationListener listener : listeners) {
            listener.postCompile(this);
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
        runServers(serverCdl, sncps);  //必须确保SNCP服务都启动后再启动其他服务
        runServers(serverCdl, others);
        runServers(serverCdl, watchs); //必须在所有服务都启动后再启动WATCH服务
        serverCdl.await();
        this.onServersPostStart();

        this.onAppPostStart();
        long intms = System.currentTimeMillis() - startTime;
        String ms = String.valueOf(intms);
        int repeat = ms.length() > 7 ? 0 : (7 - ms.length()) / 2;
        logger.info(colorMessage(logger, 36, 1, "-".repeat(repeat) + "------------------------ Redkale started in " + ms + " ms " + (ms.length() / 2 == 0 ? " " : "") + "-".repeat(repeat) + "------------------------") + "\r\n");
        LoggingBaseHandler.traceEnable = true;

        if (!singletonMode && !compileMode) {
            this.shutdownLatch.await();
        }
    }

    void loadClassesByFilters(final ClassFilter... filters) throws IOException {
        ClassFilter.Loader.load(getHome(), this.serverClassLoader, filters);
    }

    //使用了nohup或使用了后台&，Runtime.getRuntime().addShutdownHook失效
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
                    setName("Redkale-" + serconf.getValue("protocol", "Server").toUpperCase().replaceFirst("\\..+", "") + ":" + serconf.getIntValue("port") + "-Thread");
                    this.setDaemon(true);
                }

                @Override
                public void run() {
                    try {
                        //Thread ctd = Thread.currentThread();
                        //ctd.setContextClassLoader(new URLClassLoader(new URL[0], ctd.getContextClassLoader()));
                        final String protocol = serconf.getValue("protocol", "").replaceFirst("\\..+", "").toUpperCase();
                        NodeServer server = null;
                        if ("SNCP".equals(protocol)) {
                            server = NodeSncpServer.createNodeServer(Application.this, serconf);
                        } else if ("WATCH".equalsIgnoreCase(protocol)) {
                            DefaultAnyValue serconf2 = (DefaultAnyValue) serconf;
                            DefaultAnyValue rest = (DefaultAnyValue) serconf2.getAnyValue("rest");
                            if (rest == null) {
                                rest = new DefaultAnyValue();
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
                                    if (!inited.getAndSet(true)) { //加载自定义的协议，如：SOCKS
                                        ClassFilter profilter = new ClassFilter(classLoader, NodeProtocol.class, NodeServer.class, (Class[]) null);
                                        ClassFilter.Loader.load(home, classLoader, profilter);
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
                                                    + old.getName() + ") but repeat NodeServer-Class(" + type.getName() + ")");
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
                            logger.log(Level.SEVERE, "Not found Server Class for protocol({0})", serconf.getValue("protocol"));
                            Utility.sleep(100);
                            System.exit(1);
                        }
                        server.serviceCdl = serviceCdl;
                        server.init(serconf);
                        if (!singletonMode && !compileMode) {
                            server.start();
                        } else if (compileMode) {
                            server.getServer().getDispatcherServlet().init(server.getServer().getContext(), serconf);
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

    /**
     * 实例化单个Service
     *
     * @param <T>               泛型
     * @param serviceClass      指定的service类
     * @param extServiceClasses 需要排除的service类
     *
     * @return Service对象
     * @throws Exception 异常
     */
    public static <T extends Service> T singleton(Class<T> serviceClass, Class<? extends Service>... extServiceClasses) throws Exception {
        return singleton("", serviceClass, extServiceClasses);
    }

    /**
     * 实例化单个Service
     *
     * @param <T>               泛型
     * @param name              Service的资源名
     * @param serviceClass      指定的service类
     * @param extServiceClasses 需要排除的service类
     *
     * @return Service对象
     * @throws Exception 异常
     */
    public static <T extends Service> T singleton(String name, Class<T> serviceClass, Class<? extends Service>... extServiceClasses) throws Exception {
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
        Times.midnight(); //先初始化一下Utility
        Thread.currentThread().setName("Redkale-Application-Main-Thread");
        //运行主程序
        {
            String cmd = System.getProperty("cmd", System.getProperty("CMD"));
            String[] params = args;
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && args[i].toLowerCase().startsWith("--conf-file=")) {
                        System.setProperty(RESNAME_APP_CONF_FILE, args[i].substring("--conf-file=".length()));
                        String[] newargs = new String[args.length - 1];
                        System.arraycopy(args, 0, newargs, 0, i);
                        System.arraycopy(args, i + 1, newargs, i, args.length - 1 - i);
                        args = newargs;
                        break;
                    }
                }
                if (cmd == null) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] != null && !args[i].startsWith("-")) { //非-开头的第一个视为命令号
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
        }
        //PrepareCompiler.main(args); //测试代码

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
        System.exit(0); //必须要有
    }

    public String getPropertyValue(String value, Properties... envs) {
        if (value == null || value.isBlank()) {
            return value;
        }
        final String val = value;
        //${domain}/${path}/xxx    ${aa${bbb}}
        int pos2 = val.indexOf("}");
        int pos1 = val.lastIndexOf("${", pos2);
        if (pos1 >= 0 && pos2 > 0) {
            String key = val.substring(pos1 + 2, pos2);
            String newVal = null;
            if (RESNAME_APP_NAME.equals(key)) {
                newVal = getName();
            } else if (RESNAME_APP_HOME.equals(key)) {
                newVal = getHome().getPath().replace('\\', '/');
            } else if (RESNAME_APP_NODEID.equals(key)) {
                newVal = String.valueOf(getNodeid());
            } else if (RESNAME_APP_TIME.equals(key)) {
                newVal = String.valueOf(getStartTime());
            } else {
                List<Properties> list = new ArrayList<>();
                list.addAll(Arrays.asList(envs));
                list.add(this.envProperties);
                //list.add(this.sourceProperties);
                //list.add(this.clusterProperties);
                list.add(this.loggingProperties);
                //list.add(this.messageProperties);
                for (Properties prop : list) {
                    if (prop.containsKey(key)) {
                        newVal = getPropertyValue(prop.getProperty(key), envs);
                        break;
                    }
                }
                if (newVal == null) {
                    newVal = this.resourceFactory.find(key, String.class);
                }
            }
            if (newVal == null) {
                throw new RedkaleException("Not found '" + key + "' value");
            }
            return getPropertyValue(val.substring(0, pos1) + newVal + val.substring(pos2 + 1), envs);
        } else if ((pos1 >= 0 && pos2 < 0) || (pos1 < 0 && pos2 >= 0)) {
            throw new RedkaleException(value + " is illegal naming");
        }
        return val;
    }

//    public void schedule(Object service) {
//        this.scheduleManager.schedule(service);
//    }
//
//    public void unschedule(Object service) {
//        this.scheduleManager.unschedule(service);
//    }
    void updateEnvironmentProperties(String namespace, List<ResourceEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        envPropertiesLock.lock();
        try {
            Properties envRegisterProps = new Properties();
            Set<String> envRemovedKeys = new HashSet<>();
            Properties envChangedProps = new Properties();

//            Set<String> sourceRemovedKeys = new HashSet<>();
//            Properties sourceChangedProps = new Properties();
            Set<String> loggingRemovedKeys = new HashSet<>();
            Properties loggingChangedProps = new Properties();

//            Set<String> clusterRemovedKeys = new HashSet<>();
//            Properties clusterChangedProps = new Properties();
//
//            Set<String> messageRemovedKeys = new HashSet<>();
//            Properties messageChangedProps = new Properties();
            for (ResourceEvent<String> event : events) {
                if (namespace != null && namespace.startsWith("logging")) {
                    if (event.newValue() == null) {
                        if (this.loggingProperties.containsKey(event.name())) {
                            loggingRemovedKeys.add(event.name());
                        }
                    } else {
                        loggingChangedProps.put(event.name(), event.newValue());
                    }
                    continue;
                }
                if (event.name().startsWith("redkale.datasource.") || event.name().startsWith("redkale.datasource[")
                    || event.name().startsWith("redkale.cachesource.") || event.name().startsWith("redkale.cachesource[")) {
//                    if (event.name().endsWith(".name")) {
//                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in source config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
//                    } else {
//                        if (!Objects.equals(event.newValue(), this.sourceProperties.getProperty(event.name()))) {
//                            if (event.newValue() == null) {
//                                if (this.sourceProperties.containsKey(event.name())) {
//                                    sourceRemovedKeys.add(event.name());
//                                }
//                            } else {
//                                sourceChangedProps.put(event.name(), event.newValue());
//                            }
//                        }
//                    }
                } else if (event.name().startsWith("redkale.mq.") || event.name().startsWith("redkale.mq[")) {
//                    if (event.name().endsWith(".name")) {
//                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in mq config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
//                    } else {
//                        if (!Objects.equals(event.newValue(), this.messageProperties.getProperty(event.name()))) {
//                            if (event.newValue() == null) {
//                                if (this.messageProperties.containsKey(event.name())) {
//                                    messageRemovedKeys.add(event.name());
//                                }
//                            } else {
//                                messageChangedProps.put(event.name(), event.newValue());
//                            }
//                        }
//                    }
                } else if (event.name().startsWith("redkale.cluster.")) {
//                    if (!Objects.equals(event.newValue(), this.clusterProperties.getProperty(event.name()))) {
//                        if (event.newValue() == null) {
//                            if (this.clusterProperties.containsKey(event.name())) {
//                                clusterRemovedKeys.add(event.name());
//                            }
//                        } else {
//                            clusterChangedProps.put(event.name(), event.newValue());
//                        }
//                    }
                } else if (event.name().startsWith("system.property.")) {
                    String propName = event.name().substring("system.property.".length());
                    if (event.newValue() == null) {
                        System.getProperties().remove(propName);
                    } else {
                        System.setProperty(propName, event.newValue());
                    }
                } else if (event.name().startsWith("mimetype.property.")) {
                    String propName = event.name().substring("system.property.".length());
                    if (event.newValue() != null) {
                        MimeType.add(propName, event.newValue());
                    }
                } else if (event.name().startsWith("redkale.")) {
                    logger.log(Level.WARNING, "not support the environment property key " + event.name() + " on change event");
                } else {
                    if (!Objects.equals(event.newValue(), this.envProperties.getProperty(event.name()))) {
                        envRegisterProps.put(event.name(), event.newValue());
                        if (event.newValue() == null) {
                            if (this.envProperties.containsKey(event.name())) {
                                envRemovedKeys.add(event.name());
                            }
                        } else {
                            envChangedProps.put(event.name(), event.newValue());
                        }
                    }
                }
            }
            //普通配置项的变更
            if (!envRegisterProps.isEmpty()) {
                this.envProperties.putAll(envChangedProps);
                envRemovedKeys.forEach(this.envProperties::remove);
                DefaultAnyValue oldConf = (DefaultAnyValue) this.config.getAnyValue("properties");
                DefaultAnyValue newConf = new DefaultAnyValue();
                oldConf.forEach((k, v) -> newConf.addValue(k, v));
                this.envProperties.forEach((k, v) -> {
                    newConf.addValue("property", new DefaultAnyValue().addValue("name", k.toString()).addValue("value", v.toString()));
                });
                oldConf.replace(newConf);
                resourceFactory.register(envRegisterProps, "", Environment.class);
            }

            //日志配置项的变更
            if (!loggingChangedProps.isEmpty() || !loggingRemovedKeys.isEmpty()) {
                //只是简单变更日志级别则直接操作，无需重新配置日志
                if (loggingRemovedKeys.isEmpty() && loggingChangedProps.size() == 1 && loggingChangedProps.containsKey(".level")) {
                    try {
                        Level logLevel = Level.parse(loggingChangedProps.getProperty(".level"));
                        Logger.getGlobal().setLevel(logLevel);
                        this.loggingProperties.putAll(loggingChangedProps);
                        logger.log(Level.INFO, "Reconfig logging level to " + logLevel);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Reconfig logging level error, new level is " + loggingChangedProps.getProperty(".level"));
                    }
                } else {
                    Properties newLogProps = new Properties();
                    newLogProps.putAll(this.loggingProperties);
                    newLogProps.putAll(loggingChangedProps);
                    loggingRemovedKeys.forEach(newLogProps::remove);
                    reconfigLogging(newLogProps);
                    logger.log(Level.INFO, "Reconfig logging finished ");
                }
            }

        } finally {
            envPropertiesLock.unlock();
        }
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

    public List<Object> command(String cmd, String[] params) {
        List<NodeServer> localServers = new ArrayList<>(servers); //顺序sncps, others, watchs        
        List<Object> results = new ArrayList<>();
        localServers.stream().forEach((server) -> {
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
        if (this.propertiesAgent != null) {
            long s = System.currentTimeMillis();
            this.propertiesAgent.destroy(config.getAnyValue("properties"));
            logger.info(this.propertiesAgent.getClass().getSimpleName() + " destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (this.workExecutor != null) {
            this.workExecutor.shutdownNow();
        }
        if (this.clientAsyncGroup != null) {
            long s = System.currentTimeMillis();
            this.clientAsyncGroup.dispose();
            logger.info("AsyncGroup destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        this.onAppPostShutdown();

        long intms = System.currentTimeMillis() - f;
        String ms = String.valueOf(intms);
        int repeat = ms.length() > 7 ? 0 : (7 - ms.length()) / 2;
        logger.info(colorMessage(logger, 36, 1, "-".repeat(repeat) + "------------------------ Redkale shutdown in " + ms + " ms " + (ms.length() / 2 == 0 ? " " : "") + "-".repeat(repeat) + "------------------------") + "\r\n" + "\r\n");
        LoggingBaseHandler.traceEnable = true;
    }

    private void stopServers() {
        this.onServersPreStop();
        List<NodeServer> localServers = new ArrayList<>(servers); //顺序sncps, others, watchs
        Collections.reverse(localServers); //倒序， 必须让watchs先关闭，watch包含服务发现和注销逻辑
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

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    public boolean isVirtualWorkExecutor() {
        //JDK21+
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

    public int getNodeid() {
        return nodeid;
    }

    public String getName() {
        return name;
    }

    public File getHome() {
        return home;
    }

    public URI getConfPath() {
        return confPath;
    }

    public long getStartTime() {
        return startTime;
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
