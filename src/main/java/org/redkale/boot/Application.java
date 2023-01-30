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
import java.util.function.Consumer;
import java.util.logging.*;
import javax.net.ssl.SSLContext;
import org.redkale.annotation.Resource;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cluster.*;
import org.redkale.convert.Convert;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.convert.json.*;
import org.redkale.mq.*;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.source.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;
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
     * 当前进程的客户端组， 类型：AsyncGroup
     *
     * @since 2.3.0
     */
    public static final String RESNAME_APP_CLIENT_ASYNCGROUP = "APP_CLIENT_ASYNCGROUP";

    /**
     * 当前Service所属的SNCP Server的地址 类型: SocketAddress、InetSocketAddress、String <br>
     */
    public static final String RESNAME_SNCP_ADDR = "SNCP_ADDR";

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

    private static final int UDP_CAPACITY = 1024;

    //本进程节点ID
    final int nodeid;

    //本进程节点ID
    final String name;

    //本地IP地址
    final InetSocketAddress localAddress;

    //业务逻辑线程池
    //@since 2.3.0
    final ExecutorService workExecutor;

    //日志配置资源
    //@since 2.8.0
    final Properties loggingProperties = new Properties();

    //Source 原始的配置资源, 只会存在redkale.datasource(.|[) redkale.cachesource(.|[)开头的配置项
    final Properties sourceProperties = new Properties();

    //CacheSource 资源
    final List<CacheSource> cacheSources = new CopyOnWriteArrayList<>();

    //DataSource 资源
    final List<DataSource> dataSources = new CopyOnWriteArrayList<>();

    //NodeServer 资源, 顺序必须是sncps, others, watchs
    final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    //SNCP传输端的TransportFactory, 注意： 只给SNCP使用
    private final TransportFactory sncpTransportFactory;

    //给客户端使用，包含SNCP客户端、自定义数据库客户端连接池
    private final AsyncIOGroup clientAsyncGroup;

    //配置源管理接口
    //@since 2.7.0
    private PropertiesAgent propertiesAgent;

    //只存放不以system.property.、mimetype.property.、redkale.开头的配置项
    private final Properties envProperties = new Properties();

    //配置信息，只读版Properties
    private final Environment environment;

    //第三方服务配置资源
    //@since 2.8.0
    final Properties clusterProperties = new Properties();

    //第三方服务发现管理接口
    //@since 2.1.0
    private final ClusterAgent clusterAgent;

    //MQ管理配置资源
    //@since 2.8.0
    final Properties messageProperties = new Properties();

    //MQ管理接口
    //@since 2.1.0
    private final MessageAgent[] messageAgents;

    //是否从/META-INF中读取配置
    private final boolean configFromCache;

    //全局根ResourceFactory
    final ResourceFactory resourceFactory = ResourceFactory.create();

    //服务配置项
    final AnyValue config;

    //排除的jar路径
    final String excludelibs;

    //临时计数器
    CountDownLatch servicecdl;  //会出现两次赋值

    //是否启动了WATCH协议服务
    boolean watching;

    //--------------------------------------------------------------------------------------------    
    //是否用于main方法运行
    private final boolean singletonMode;

    //是否用于编译模式运行
    private final boolean compileMode;

    //根WatchFactory
    //private final WatchFactory watchFactory = WatchFactory.root();
    //进程根目录
    private final File home;

    private final String homePath;

    //配置文件目录
    private final URI confPath;

    //日志
    private final Logger logger;

    //监听事件
    final List<ApplicationListener> listeners = new CopyOnWriteArrayList<>();

    //服务启动时间
    private final long startTime = System.currentTimeMillis();

    //Server启动的计数器，用于确保所有Server都启动完后再进行下一步处理
    private final CountDownLatch shutdownLatch;

    //根ClassLoader
    private final RedkaleClassLoader classLoader;

    //Server根ClassLoader
    private final RedkaleClassLoader serverClassLoader;

    //config: 不带redkale的配置项
    Application(final AnyValue config) {
        this(false, false, config);
    }

    @SuppressWarnings("UseSpecificCatch") //config: 不带redkale的配置项
    Application(final boolean singletonMode, boolean compileMode, final AnyValue config) {
        this.singletonMode = singletonMode;
        this.compileMode = compileMode;
        this.config = config;
        this.configFromCache = "true".equals(config.getValue("[config-from-cache]"));
        this.environment = new Environment(this.envProperties);
        //设置APP_HOME、APP_NAME
        this.name = checkName(config.getValue("name", ""));
        this.resourceFactory.register(RESNAME_APP_NAME, name);
        System.setProperty(RESNAME_APP_NAME, name);

        final File root = new File(System.getProperty(RESNAME_APP_HOME));
        this.resourceFactory.register(RESNAME_APP_TIME, long.class, this.startTime);
        this.resourceFactory.register(RESNAME_APP_HOME, Path.class, root.toPath());
        this.resourceFactory.register(RESNAME_APP_HOME, File.class, root);
        this.resourceFactory.register(RESNAME_APP_HOME, URI.class, root.toURI());
        File confFile = null;
        try { //设置APP_HOME
            this.resourceFactory.register(RESNAME_APP_HOME, root.getCanonicalPath());
            if (System.getProperty(RESNAME_APP_HOME) == null) {
                System.setProperty(RESNAME_APP_HOME, root.getCanonicalPath());
            }
            this.home = root.getCanonicalFile();
            this.homePath = this.home.getPath();
            String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
            if (confDir.contains("://") || confDir.startsWith("file:") || confDir.startsWith("resource:") || confDir.contains("!")) { //graalvm native-image startwith resource:META-INF
                this.confPath = URI.create(confDir);
                if (confDir.startsWith("file:")) {
                    confFile = new File(this.confPath.getPath()).getCanonicalFile();
                }
            } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > 0) {
                confFile = new File(confDir).getCanonicalFile();
                this.confPath = confFile.toURI();
            } else {
                confFile = new File(this.home, confDir).getCanonicalFile();
                this.confPath = confFile.toURI();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        { //设置系统变量
            AnyValue propertiesConf = this.config.getAnyValue("properties");
            if (propertiesConf != null) { //设置配置文件中的系统变量
                for (AnyValue prop : propertiesConf.getAnyValues("property")) {
                    String key = prop.getValue("name", "");
                    if (key.startsWith("system.property.")) {
                        String propName = key.substring("system.property.".length());
                        if (System.getProperty(propName) == null) { //命令行传参数优先级高
                            String value = prop.getValue("value");
                            //replaceValue调用前必须给home、name赋值
                            System.setProperty(propName, value == null ? value : replaceValue(value));
                        }
                    }
                }
            }
            //设置默认系统变量
            if (System.getProperty("redkale.net.transport.poolmaxconns") == null) {
                System.setProperty("redkale.net.transport.poolmaxconns", "100");
            }
            if (System.getProperty("redkale.net.transport.pinginterval") == null) {
                System.setProperty("redkale.net.transport.pinginterval", "30");
            }
            if (System.getProperty("redkale.net.transport.checkinterval") == null) {
                System.setProperty("redkale.net.transport.checkinterval", "30");
            }
            if (System.getProperty("redkale.convert.tiny") == null) {
                System.setProperty("redkale.convert.tiny", "true");
            }
            if (System.getProperty("redkale.convert.pool.size") == null) {
                System.setProperty("redkale.convert.pool.size", "128");
            }
            if (System.getProperty("redkale.convert.writer.buffer.defsize") == null) {
                System.setProperty("redkale.convert.writer.buffer.defsize", "4096");
            }
            if (System.getProperty("redkale.trace.enable") == null) {
                System.setProperty("redkale.trace.enable", "false");
            }

            System.setProperty("redkale.version", Redkale.getDotedVersion());
            int nid = config.getIntValue("nodeid", 0);
            this.nodeid = nid;
            this.resourceFactory.register(RESNAME_APP_NODEID, nid);
            System.setProperty(RESNAME_APP_NODEID, "" + nid);
        }

        String localaddr = config.getValue("address", "").trim();
        InetAddress addr = localaddr.isEmpty() ? Utility.localInetAddress() : new InetSocketAddress(localaddr, config.getIntValue("port")).getAddress();
        this.localAddress = new InetSocketAddress(addr, config.getIntValue("port"));
        this.resourceFactory.register(RESNAME_APP_ADDR, addr.getHostAddress());
        this.resourceFactory.register(RESNAME_APP_ADDR, InetAddress.class, addr);
        this.resourceFactory.register(RESNAME_APP_ADDR, InetSocketAddress.class, this.localAddress);

        this.resourceFactory.register(RESNAME_APP_CONF_DIR, URI.class, this.confPath);
        this.resourceFactory.register(RESNAME_APP_CONF_DIR, String.class, this.confPath.toString());
        if (confFile != null) {
            this.resourceFactory.register(RESNAME_APP_CONF_DIR, File.class, confFile);
            this.resourceFactory.register(RESNAME_APP_CONF_DIR, Path.class, confFile.toPath());
        }
        this.resourceFactory.register(Environment.class, environment);

        { //初始化ClassLoader
            ClassLoader currClassLoader = Thread.currentThread().getContextClassLoader();
            if (currClassLoader instanceof RedkaleClassLoader) {
                this.classLoader = (RedkaleClassLoader) currClassLoader;
            } else {
                Set<String> cacheClasses = null;
                if (!singletonMode && !compileMode) {
                    try {
                        InputStream in = Application.class.getResourceAsStream(RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH);
                        if (in != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1024);
                            List<String> list = new ArrayList<>();
                            reader.lines().forEach(v -> list.add(v));
                            Collections.sort(list);
                            if (!list.isEmpty()) {
                                cacheClasses = new LinkedHashSet<>(list);
                            }
                            in.close();
                        }
                    } catch (Exception e) {
                    }
                }
                if (cacheClasses == null) {
                    this.classLoader = new RedkaleClassLoader(currClassLoader);
                } else {
                    this.classLoader = new RedkaleClassLoader.RedkaleCacheClassLoader(currClassLoader, cacheClasses);
                }
                Thread.currentThread().setContextClassLoader(this.classLoader);
            }
        }
        { //以下是初始化日志配置
            URI logConfURI;
            File logConfFile = null;
            if (configFromCache) {
                logConfURI = RedkaleClassLoader.getConfResourceAsURI(null, "logging.properties");
            } else if ("file".equals(confPath.getScheme())) {
                logConfFile = new File(confPath.getPath(), "logging.properties");
                logConfURI = logConfFile.toURI();
                if (!logConfFile.isFile() || !logConfFile.canRead()) {
                    logConfFile = null;
                }
            } else {
                logConfURI = URI.create(confPath + (confPath.toString().endsWith("/") ? "" : "/") + "logging.properties");
            }
            if (!"file".equals(confPath.getScheme()) || logConfFile != null) {
                try {
                    InputStream fin = logConfURI.toURL().openStream();
                    Properties properties0 = new Properties();
                    properties0.load(fin);
                    fin.close();
                    reconfigLogging(properties0);
                } catch (IOException e) {
                    Logger.getLogger(this.getClass().getSimpleName()).log(Level.WARNING, "init logger configuration error", e);
                }
            }

            if (compileMode) {
                RedkaleClassLoader.putReflectionClass(java.lang.Class.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(SimpleFormatter.class, SimpleFormatter.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(LoggingSearchHandler.class, LoggingSearchHandler.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.class, LoggingFileHandler.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingFormater.class, LoggingFileHandler.LoggingFormater.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingConsoleHandler.class, LoggingFileHandler.LoggingConsoleHandler.class.getName());
                RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingSncpFileHandler.class, LoggingFileHandler.LoggingSncpFileHandler.class.getName());
            }
        }
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.shutdownLatch = new CountDownLatch(config.getAnyValues("server").length + 1);
        logger.log(Level.INFO, colorMessage(logger, 36, 1, "-------------------------------- Redkale " + Redkale.getDotedVersion() + " --------------------------------"));

        //------------------------------------ 基本设置 ------------------------------------   
        {
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
                String lib = replaceValue(config.getValue("lib", "${APP_HOME}/libs/*").trim());
                lib = lib.isEmpty() ? confDir : (lib + ";" + confDir);
                Server.loadLib(classLoader, logger, lib);
            }

            this.resourceFactory.register(BsonFactory.root());
            this.resourceFactory.register(JsonFactory.root());
            this.resourceFactory.register(BsonFactory.root().getConvert());
            this.resourceFactory.register(JsonFactory.root().getConvert());
            this.resourceFactory.register("bsonconvert", Convert.class, BsonFactory.root().getConvert());
            this.resourceFactory.register("jsonconvert", Convert.class, JsonFactory.root().getConvert());
        }
        //------------------------------------ 读取配置 ------------------------------------
        try {
            loadResourceProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //------------------------------------ 配置 <transport> 节点 ------------------------------------
        TransportStrategy strategy = null;
        String excludelib0 = null;
        ClusterAgent cluster = null;
        MessageAgent[] mqs = null;
        int bufferCapacity = 32 * 1024;
        int bufferPoolSize = Utility.cpus() * 8;
        int readTimeoutSeconds = TransportFactory.DEFAULT_READTIMEOUTSECONDS;
        int writeTimeoutSeconds = TransportFactory.DEFAULT_WRITETIMEOUTSECONDS;
        AnyValue executorConf = null;
        executorConf = config.getAnyValue("executor");
        AnyValue excludelibConf = config.getAnyValue("excludelibs");
        if (excludelibConf != null) {
            excludelib0 = excludelibConf.getValue("value");
        }
        AnyValue transportConf = config.getAnyValue("transport");
        int groupsize = config.getAnyValues("group").length;
        if (groupsize > 0 && transportConf == null) {
            transportConf = new DefaultAnyValue();
        }
        if (transportConf != null) {
            //--------------transportBufferPool-----------
            bufferCapacity = Math.max(parseLenth(transportConf.getValue("bufferCapacity"), bufferCapacity), 32 * 1024);
            readTimeoutSeconds = transportConf.getIntValue("readTimeoutSeconds", readTimeoutSeconds);
            writeTimeoutSeconds = transportConf.getIntValue("writeTimeoutSeconds", writeTimeoutSeconds);
            final int threads = parseLenth(transportConf.getValue("threads"), groupsize * Utility.cpus() * 2);
            bufferPoolSize = parseLenth(transportConf.getValue("bufferPoolSize"), threads * 4);
        }

        AnyValue clusterConf = config.getAnyValue("cluster");
        if (clusterConf != null) {
            try {
                String classVal = clusterConf.getValue("type", clusterConf.getValue("value")); //兼容value字段
                if (classVal == null || classVal.isEmpty() || classVal.indexOf('.') < 0) { //不包含.表示非类名，比如值: consul, nacos
                    Iterator<ClusterAgentProvider> it = ServiceLoader.load(ClusterAgentProvider.class, classLoader).iterator();
                    RedkaleClassLoader.putServiceLoader(ClusterAgentProvider.class);
                    while (it.hasNext()) {
                        ClusterAgentProvider provider = it.next();
                        if (provider != null) {
                            RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName()); //loader class
                        }
                        if (provider != null && provider.acceptsConf(clusterConf)) {
                            cluster = provider.createInstance();
                            cluster.setConfig(clusterConf);
                            break;
                        }
                    }
                    if (cluster == null) {
                        ClusterAgent cacheClusterAgent = new CacheClusterAgent();
                        if (cacheClusterAgent.acceptsConf(clusterConf)) {
                            cluster = cacheClusterAgent;
                            cluster.setConfig(clusterConf);
                        }
                    }
                    if (cluster == null) {
                        logger.log(Level.SEVERE, "load application cluster resource, but not found name='type' value error: " + clusterConf);
                    }
                } else {
                    Class type = classLoader.loadClass(classVal);
                    if (!ClusterAgent.class.isAssignableFrom(type)) {
                        logger.log(Level.SEVERE, "load application cluster resource, but not found " + ClusterAgent.class.getSimpleName() + " implements class error: " + clusterConf);
                    } else {
                        RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                        cluster = (ClusterAgent) type.getDeclaredConstructor().newInstance();
                        cluster.setConfig(clusterConf);
                    }
                }
                //此时不能执行cluster.init，因内置的对象可能依赖config.properties配置项
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application cluster resource error: " + clusterConf, e);
            }
        }

        AnyValue[] mqConfs = config.getAnyValues("mq");
        if (mqConfs != null && mqConfs.length > 0) {
            mqs = new MessageAgent[mqConfs.length];
            Set<String> mqnames = new HashSet<>();
            for (int i = 0; i < mqConfs.length; i++) {
                AnyValue mqConf = mqConfs[i];
                String mqname = mqConf.getValue("name", "");
                if (mqnames.contains(mqname)) {
                    throw new RuntimeException("mq.name(" + mqname + ") is repeat");
                }
                mqnames.add(mqname);
                String namex = mqConf.getValue("names");
                if (namex != null && !namex.isEmpty()) {
                    for (String n : namex.split(";")) {
                        if (n.trim().isEmpty()) {
                            continue;
                        }
                        if (mqnames.contains(n.trim())) {
                            throw new RuntimeException("mq.name(" + n.trim() + ") is repeat");
                        }
                        mqnames.add(n.trim());
                    }
                }
                try {
                    String classVal = mqConf.getValue("type", mqConf.getValue("value")); //兼容value字段
                    if (classVal == null || classVal.isEmpty() || classVal.indexOf('.') < 0) { //不包含.表示非类名，比如值: kafka, pulsar
                        Iterator<MessageAgentProvider> it = ServiceLoader.load(MessageAgentProvider.class, classLoader).iterator();
                        RedkaleClassLoader.putServiceLoader(MessageAgentProvider.class);
                        while (it.hasNext()) {
                            MessageAgentProvider provider = it.next();
                            if (provider != null) {
                                RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName()); //loader class
                            }
                            if (provider != null && provider.acceptsConf(mqConf)) {
                                mqs[i] = provider.createInstance();
                                mqs[i].setConfig(mqConf);
                                break;
                            }
                        }
                        if (mqs[i] == null) {
                            logger.log(Level.SEVERE, "load application mq resource, but not found name='value' value error: " + mqConf);
                        }
                    } else {
                        Class type = classLoader.loadClass(classVal);
                        if (!MessageAgent.class.isAssignableFrom(type)) {
                            logger.log(Level.SEVERE, "load application mq resource, but not found " + MessageAgent.class.getSimpleName() + " implements class error: " + mqConf);
                        } else {
                            RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                            mqs[i] = (MessageAgent) type.getDeclaredConstructor().newInstance();
                            mqs[i].setConfig(mqConf);
                        }
                    }
                    //此时不能执行mq.init，因内置的对象可能依赖config.properties配置项
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "load application mq resource error: " + mqs[i], e);
                }
            }
        }

        ExecutorService workExecutor0 = null;
        if (executorConf == null) {
            executorConf = DefaultAnyValue.create();
        }
        final int workThreads = executorConf.getIntValue("threads", Utility.cpus() * 4);
        boolean workHash = executorConf.getBoolValue("hash", false);
        if (workThreads > 0) {
            if (workHash) {
                workExecutor0 = WorkThread.createHashExecutor(workThreads, "Redkale-HashWorkThread-%s");
            } else {
                workExecutor0 = WorkThread.createExecutor(workThreads, "Redkale-WorkThread-%s");
            }
        }
        ExecutorService clientExecutor = workExecutor0;
        if (clientExecutor == null) {
            //给所有client给一个默认的ExecutorService
            final int clientThreads = executorConf.getIntValue("clients", Utility.cpus());
            clientExecutor = WorkThread.createExecutor(clientThreads, "Redkale-DefaultClient-WorkThread-%s");
        }

        this.workExecutor = workExecutor0;
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, Executor.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, ExecutorService.class, this.workExecutor);

        this.clientAsyncGroup = new AsyncIOGroup(true, "Redkale-DefaultClient-IOThread-%s", clientExecutor, bufferCapacity, bufferPoolSize).skipClose(true);
        this.resourceFactory.register(RESNAME_APP_CLIENT_ASYNCGROUP, AsyncGroup.class, this.clientAsyncGroup);

        this.excludelibs = excludelib0;
        this.sncpTransportFactory = TransportFactory.create(this.clientAsyncGroup, (SSLContext) null, Transport.DEFAULT_NETPROTOCOL, readTimeoutSeconds, writeTimeoutSeconds, strategy);
        DefaultAnyValue tarnsportConf = DefaultAnyValue.create(TransportFactory.NAME_POOLMAXCONNS, System.getProperty("redkale.net.transport.pool.maxconns", "100"))
            .addValue(TransportFactory.NAME_PINGINTERVAL, System.getProperty("redkale.net.transport.ping.interval", "30"))
            .addValue(TransportFactory.NAME_CHECKINTERVAL, System.getProperty("redkale.net.transport.check.interval", "30"));
        this.sncpTransportFactory.init(tarnsportConf, ByteBuffer.wrap(Sncp.getPingBytes()).asReadOnlyBuffer(), Sncp.getPongBytes().length);
        this.clusterAgent = cluster;
        this.messageAgents = mqs;
        if (compileMode || this.classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader) {
            this.serverClassLoader = this.classLoader;
        } else {
            this.serverClassLoader = new RedkaleClassLoader(this.classLoader);
        }
    }

    private void loadResourceProperties() throws IOException {
        final Properties dyncProps = new Properties();
        final AtomicInteger propertyIndex = new AtomicInteger();
        Properties logProps = null; //新的日志配置项
        //------------------------------------ 读取本地DataSource、CacheSource配置 ------------------------------------
        if ("file".equals(this.confPath.getScheme())) {
            File sourceFile = new File(new File(confPath), "source.properties");
            if (sourceFile.isFile() && sourceFile.canRead()) {
                InputStream in = new FileInputStream(sourceFile);
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
            } else {
                //兼容 persistence.xml 【已废弃】
                File persist = new File(new File(confPath), "persistence.xml");
                if (persist.isFile() && persist.canRead()) {
                    logger.log(Level.WARNING, "persistence.xml is deprecated, replaced by source.properties");
                    InputStream in = new FileInputStream(persist);
                    dyncProps.putAll(DataSources.loadSourceProperties(in));
                    in.close();
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
            if (propertiesConf.getValue("load") != null) { //本地配置项文件加载
                for (String dfload : propertiesConf.getValue("load").split(";")) {
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
                || k.toString().startsWith("redkale.excludelibs.") //节点全局唯一
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
            //合并配置
            this.config.merge(AnyValue.loadFromProperties(dyncProps).getAnyValue("redkale"), NodeServer.appConfigmergeFunction);
            dyncProps.forEach((key, val) -> {
                if (key.toString().startsWith("redkale.datasource.") || key.toString().startsWith("redkale.datasource[")
                    || key.toString().startsWith("redkale.cachesource.") || key.toString().startsWith("redkale.cachesource[")) {
                    if (key.toString().endsWith(".name")) {
                        logger.log(Level.WARNING, "skip illegal key " + key + " in source config, key cannot endsWith '.name'");
                    } else {
                        this.sourceProperties.put(key, val);
                    }
                } else if (key.toString().startsWith("redkale.mq.") || key.toString().startsWith("redkale.mq[")) {
                    if (key.toString().endsWith(".name")) {
                        logger.log(Level.WARNING, "skip illegal key " + key + " in mq config, key cannot endsWith '.name'");
                    } else {
                        this.messageProperties.put(key, val);
                    }
                } else if (key.toString().startsWith("redkale.cluster.")) {
                    this.clusterProperties.put(key, val);
                }
            });
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
                value = value == null ? value : replaceValue(value);
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
                    .replace("${" + RESNAME_APP_NAME + "}", getName())
                    .replace("${" + RESNAME_APP_HOME + "}", getHome().getPath().replace('\\', '/'))
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
                prop.entrySet().forEach(x -> {
                    properties.put(x.getKey(), x.getValue());
                });
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

    private String checkName(String name) {  //不能含特殊字符
        if (name == null || name.isEmpty()) {
            return name;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || ch == '.' || ch == '-' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException("name only 0-9 a-z A-Z _ - . cannot begin 0-9");
            }
        }
        return name;
    }

    public void init() throws Exception {
        //只有WatchService才能加载Application、WatchFactory
        final Application application = this;
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
                    } else if (type == TransportFactory.class) {
                        field.set(srcObj, application.sncpTransportFactory);
                        return application.sncpTransportFactory;
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

        }, Application.class, ResourceFactory.class, TransportFactory.class, NodeSncpServer.class, NodeHttpServer.class, NodeWatchServer.class);

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
                logger.log(Level.SEVERE, "java.net.http.HttpClient inject error", e);
                return null;
            }
        }, java.net.http.HttpClient.class);
        //------------------------------------ 注册 HttpSimpleClient ------------------------------------       
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                HttpSimpleClient httpClient = HttpSimpleClient.create(clientAsyncGroup);
                field.set(srcObj, httpClient);
                rf.inject(resourceName, httpClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpSimpleClient.class, httpClient);
                return httpClient;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "HttpClient inject error", e);
                return null;
            }
        }, HttpSimpleClient.class);
        //--------------------------------------------------------------------------
        if (this.clientAsyncGroup != null) {
            ((AsyncIOGroup) this.clientAsyncGroup).start();
        }
        if (this.clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "ClusterAgent (type = " + this.clusterAgent.getClass().getSimpleName() + ") initing");
            }
            long s = System.currentTimeMillis();
            if (this.clusterAgent instanceof CacheClusterAgent) {
                String sourceName = ((CacheClusterAgent) clusterAgent).getSourceName(); //必须在inject前调用，需要赋值Resourcable.name
                loadCacheSource(sourceName, false);
            }
            clusterAgent.setTransportFactory(this.sncpTransportFactory);
            this.resourceFactory.inject(clusterAgent);
            clusterAgent.init(this.resourceFactory, clusterAgent.getConfig());
            this.resourceFactory.register(ClusterAgent.class, clusterAgent);
            logger.info("ClusterAgent (type = " + this.clusterAgent.getClass().getSimpleName() + ") init in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (this.messageAgents != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent initing");
            }
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                this.resourceFactory.inject(agent);
                agent.init(this.resourceFactory, agent.getConfig());
                this.resourceFactory.register(agent.getName(), MessageAgent.class, agent);
                this.resourceFactory.register(agent.getName(), HttpMessageClient.class, agent.getHttpMessageClient());
                //this.resourceFactory.register(agent.getName(), SncpMessageClient.class, agent.getSncpMessageClient()); //不需要给开发者使用
            }
            logger.info("MessageAgent init in " + (System.currentTimeMillis() - s) + " ms");
        }
        //------------------------------------ 注册 HttpMessageClient ------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                if (clusterAgent == null) {
                    HttpMessageClient messageClient = new HttpMessageLocalClient(application, resourceName);
                    field.set(srcObj, messageClient);
                    rf.inject(resourceName, messageClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, HttpMessageClient.class, messageClient);
                    return messageClient;
                }
                HttpMessageClient messageClient = new HttpMessageClusterClient(application, resourceName, clusterAgent);
                field.set(srcObj, messageClient);
                rf.inject(resourceName, messageClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpMessageClient.class, messageClient);
                return messageClient;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "HttpMessageClient inject error", e);
                return null;
            }
        }, HttpMessageClient.class);
        initResources();
    }

    private AnyValue findSourceConfig(String sourceName, String sourceType) {
        AnyValue sourceNode = config.getAnyValue(sourceType);
        if (sourceNode != null) {
            AnyValue confNode = sourceNode.getAnyValue(sourceName);
            if (confNode != null) { //必须要设置name属性
                ((DefaultAnyValue) confNode).setValue("name", sourceName);
            }
            return confNode;
        }
        return null;
    }

    private AnyValue findMQConfig(String mqName) {
        AnyValue mqsNode = config.getAnyValue("mq");
        if (mqsNode != null) {
            AnyValue confNode = mqsNode.getAnyValue(mqName);
            if (confNode != null) { //必须要设置name属性
                ((DefaultAnyValue) confNode).setValue("name", mqName);
            }
            return confNode;
        }
        return null;
    }

    CacheSource loadCacheSource(final String sourceName, boolean autoMemory) {
        synchronized (cacheSources) {
            long st = System.currentTimeMillis();
            CacheSource old = resourceFactory.find(sourceName, CacheSource.class);
            if (old != null) {
                return old;
            }
            final AnyValue sourceConf = findSourceConfig(sourceName, "cachesource");
            if (sourceConf == null) {
                if (!autoMemory) {
                    return null;
                }
                CacheSource source = new CacheMemorySource(sourceName);
                cacheSources.add(source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                if (!compileMode && source instanceof Service) {
                    ((Service) source).init(sourceConf);
                }
                logger.info("Load CacheSource resourceName = " + sourceName + ", source = " + source + " in " + (System.currentTimeMillis() - st) + " ms");
                return source;
            }
            if (!sourceConf.getValue(AbstractCacheSource.CACHE_SOURCE_SOURCE, "").isEmpty()) {
                CacheSource source = loadCacheSource(sourceConf.getValue(AbstractCacheSource.CACHE_SOURCE_SOURCE), autoMemory);
                if (source != null) {
                    resourceFactory.register(sourceName, CacheSource.class, source);
                }
                return source;
            }
            try {
                CacheSource source = AbstractCacheSource.createCacheSource(serverClassLoader, resourceFactory, sourceConf, sourceName, compileMode);
                cacheSources.add(source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                logger.info("Load CacheSource resourceName = " + sourceName + ", source = " + source + " in " + (System.currentTimeMillis() - st) + " ms");
                return source;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application CaheSource error: " + sourceConf, e);
            }
            return null;
        }
    }

    DataSource loadDataSource(final String sourceName, boolean autoMemory) {
        synchronized (dataSources) {
            DataSource old = resourceFactory.find(sourceName, DataSource.class);
            if (old != null) {
                return old;
            }
            final AnyValue sourceConf = findSourceConfig(sourceName, "datasource");
            if (sourceConf == null) {
                if (!autoMemory) {
                    return null;
                }
                DataSource source = new DataMemorySource(sourceName);
                if (!compileMode && source instanceof Service) {
                    resourceFactory.inject(sourceName, source);
                    ((Service) source).init(sourceConf);
                }
                dataSources.add(source);
                resourceFactory.register(sourceName, DataSource.class, source);
                logger.info("Load DataSource resourceName = " + sourceName + ", source = " + source);
                return source;
            }
            if (!sourceConf.getValue(AbstractDataSource.DATA_SOURCE_SOURCE, "").isEmpty()) {
                DataSource source = loadDataSource(sourceConf.getValue(AbstractDataSource.DATA_SOURCE_SOURCE), autoMemory);
                if (source != null) {
                    if (source instanceof DataMemorySource && DataMemorySource.isSearchType(sourceConf)) {
                        resourceFactory.register(sourceName, SearchSource.class, source);
                    } else {
                        resourceFactory.register(sourceName, DataSource.class, source);
                    }
                }
                return source;
            }
            try {
                DataSource source = AbstractDataSource.createDataSource(serverClassLoader, resourceFactory, sourceConf, sourceName, compileMode);
                dataSources.add(source);
                if (source instanceof DataMemorySource && DataMemorySource.isSearchType(sourceConf)) {
                    resourceFactory.register(sourceName, SearchSource.class, source);
                } else {
                    resourceFactory.register(sourceName, DataSource.class, source);
                }
                logger.info("Load DataSource resourceName = " + sourceName + ", source = " + source);
                return source;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application DataSource error: " + sourceConf, e);
            }
            return null;
        }
    }

    private void initResources() throws Exception {
        //------------------------------------------------------------------------
        for (AnyValue conf : config.getAnyValues("group")) {
            final String group = conf.getValue("name", "");
            final String protocol = conf.getValue("protocol", Transport.DEFAULT_NETPROTOCOL).toUpperCase();
            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                throw new RuntimeException("Not supported Transport Protocol " + conf.getValue("protocol"));
            }
            TransportGroupInfo ginfo = new TransportGroupInfo(group, protocol, new LinkedHashSet<>());
            for (AnyValue node : conf.getAnyValues("node")) {
                final InetSocketAddress addr = new InetSocketAddress(node.getValue("addr"), node.getIntValue("port"));
                ginfo.putAddress(addr);
            }
            sncpTransportFactory.addGroupInfo(ginfo);
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
                    channel.socket().setSoTimeout(3000);
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
        channel.socket().setSoTimeout(3000);
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
            System.out.println(rs);
        } catch (Exception e) {
            if (e instanceof PortUnreachableException) {
                if ("APIDOC".equalsIgnoreCase(cmd)) {
                    final Application application = new Application(false, true, Application.loadAppConfig());
                    application.init();
                    application.start();
                    String rs = new ApiDocCommand(application).command(cmd, params);
                    application.shutdown();
                    if (logger != null) {
                        logger.info(rs);
                    }
                    System.out.println(rs);
                    return;
                }
                //if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                //    System.out.println("--- application not running ---");
                //} else {
                System.err.println("--- application not running ---");
                //}
                return;
            }
            throw e;
        }
    }

    /**
     * 启动
     *
     * @throws Exception 异常
     */
    public void start() throws Exception {
        if (!singletonMode && !compileMode && this.clusterAgent != null) {
            this.clusterAgent.register(this);
        }
        final AnyValue[] entrys = config.getAnyValues("server");
        CountDownLatch timecd = new CountDownLatch(entrys.length);
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
            throw new RuntimeException("Found one more WATCH Server");
        }
        this.watching = !watchs.isEmpty();

        runServers(timecd, sncps);  //必须确保SNCP服务都启动后再启动其他服务
        runServers(timecd, others);
        runServers(timecd, watchs); //必须在所有服务都启动后再启动WATCH服务
        timecd.await();
        if (this.clusterAgent != null) {
            this.clusterAgent.start();
        }
        if (this.messageAgents != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent starting");
            }
            long s = System.currentTimeMillis();
            final StringBuffer sb = new StringBuffer();
            Set<String> names = new HashSet<>();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                Map<String, Long> map = agent.start().join();
                AtomicInteger maxlen = new AtomicInteger();
                map.keySet().forEach(str -> {
                    if (str.length() > maxlen.get()) {
                        maxlen.set(str.length());
                    }
                });
                new TreeMap<>(map).forEach((topic, ms) -> sb.append("MessageConsumer(topic=").append(alignString(topic, maxlen.get())).append(") init and start in ").append(ms).append(" ms\r\n")
                );
            }
            if (sb.length() > 0) {
                logger.info(sb.toString().trim());
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") start in " + (System.currentTimeMillis() - s) + " ms");
        }
        long intms = System.currentTimeMillis() - startTime;
        String ms = String.valueOf(intms);
        int repeat = ms.length() > 7 ? 0 : (7 - ms.length()) / 2;
        logger.info(colorMessage(logger, 36, 1, "-".repeat(repeat) + "------------------------ Redkale started in " + ms + " ms " + (ms.length() / 2 == 0 ? " " : "") + "-".repeat(repeat) + "------------------------") + "\r\n");
        LoggingBaseHandler.traceFlag = true;

        for (ApplicationListener listener : this.listeners) {
            listener.postStart(this);
        }
        if (!singletonMode && !compileMode) {
            this.shutdownLatch.await();
        }
    }

    private static String alignString(String value, int maxlen) {
        StringBuilder sb = new StringBuilder(maxlen);
        sb.append(value);
        for (int i = 0; i < maxlen - value.length(); i++) {
            sb.append(' ');
        }
        return sb.toString();
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
    private void runServers(CountDownLatch timecd, final List<AnyValue> serconfs) throws Exception {
        this.servicecdl = new CountDownLatch(serconfs.size());
        CountDownLatch sercdl = new CountDownLatch(serconfs.size());
        final AtomicBoolean inited = new AtomicBoolean(false);
        final Map<String, Class<? extends NodeServer>> nodeClasses = new HashMap<>();
        for (final AnyValue serconf : serconfs) {
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
                                synchronized (nodeClasses) {
                                    if (!inited.getAndSet(true)) { //加载自定义的协议，如：SOCKS
                                        ClassFilter profilter = new ClassFilter(classLoader, NodeProtocol.class, NodeServer.class, (Class[]) null);
                                        ClassFilter.Loader.load(home, classLoader, ((excludelibs != null ? (excludelibs + ";") : "") + serconf.getValue("excludelibs", "")).split(";"), profilter);
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
                                                throw new RuntimeException("Protocol(" + p + ") had NodeServer-Class(" + old.getName() + ") but repeat NodeServer-Class(" + type.getName() + ")");
                                            }
                                            nodeClasses.put(p, type);
                                        }
                                    }
                                }
                            }
                            Class<? extends NodeServer> nodeClass = nodeClasses.get(protocol);
                            if (nodeClass != null) {
                                server = NodeServer.create(nodeClass, Application.this, serconf);
                            }
                        }
                        if (server == null) {
                            logger.log(Level.SEVERE, "Not found Server Class for protocol({0})", serconf.getValue("protocol"));
                            System.exit(1);
                        }
                        server.init(serconf);
                        if (!singletonMode && !compileMode) {
                            server.start();
                        } else if (compileMode) {
                            server.getServer().getDispatcherServlet().init(server.getServer().getContext(), serconf);
                        }
                        servers.add(server);
                        timecd.countDown();
                        sercdl.countDown();
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, serconf + " runServers error", ex);
                        Application.this.shutdownLatch.countDown();
                        System.exit(1);
                    }
                }
            };
            thread.start();
        }
        sercdl.await();
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
        System.setProperty("red" + "kale.singleton.serviceclass", serviceClass.getName());
        if (extServiceClasses != null && extServiceClasses.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Class clazz : extServiceClasses) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(clazz.getName());
            }
            System.setProperty("red" + "kale.singleton.extserviceclasses", sb.toString());
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
        return new Application(singleton, false, loadAppConfig());
    }

    static AnyValue loadAppConfig() throws IOException {
        final String home = new File(System.getProperty(RESNAME_APP_HOME, "")).getCanonicalPath().replace('\\', '/');
        System.setProperty(RESNAME_APP_HOME, home);
        String sysConfFile = System.getProperty(RESNAME_APP_CONF_FILE);
        if (sysConfFile != null) {
            String text;
            if (sysConfFile.contains("://")) {
                text = Utility.readThenClose(URI.create(sysConfFile).toURL().openStream());
            } else {
                File f = new File(sysConfFile);
                if (f.isFile() && f.canRead()) {
                    text = Utility.readThenClose(new FileInputStream(f));
                } else {
                    throw new IOException("Read application conf file (" + sysConfFile + ") error ");
                }
            }
            return text.trim().startsWith("<") ? AnyValue.loadFromXml(text, (k, v) -> v.replace("${APP_HOME}", home)).getAnyValue("application") : AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
        URI appConfFile;
        boolean fromCache = false;
        if (confDir.contains("://")) { //jar内部资源
            appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.xml");
            try {
                appConfFile.toURL().openStream().close();
            } catch (IOException e) { //没有application.xml就尝试读application.properties
                appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.properties");
            }
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > 0) { //绝对路径
            File f = new File(confDir, "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(confDir, "application.properties");
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                } else {
                    appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                    try {
                        appConfFile.toURL().openStream().close();
                    } catch (IOException e) { //没有application.xml就尝试读application.properties
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                    }
                    confDir = appConfFile.toString().replace("/application.xml", "").replace("/application.properties", "");
                    fromCache = true;
                }
            }
        } else { //相对路径
            File f = new File(new File(home, confDir), "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(new File(home, confDir), "application.properties");
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                } else {
                    appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                    try {
                        appConfFile.toURL().openStream().close();
                    } catch (IOException e) { //没有application.xml就尝试读application.properties
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                    }
                    confDir = appConfFile.toString().replace("/application.xml", "").replace("/application.properties", "");
                    fromCache = true;
                }
            }
        }
        System.setProperty(RESNAME_APP_CONF_DIR, confDir);
        String text = Utility.readThenClose(appConfFile.toURL().openStream());
        AnyValue conf;
        if (text.trim().startsWith("<")) {
            conf = AnyValue.loadFromXml(text, (k, v) -> v.replace("${APP_HOME}", home)).getAnyValue("application");
        } else {
            conf = AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        if (fromCache) {
            ((DefaultAnyValue) conf).addValue("[config-from-cache]", "true");
        }

        return conf;
    }

    public static void main(String[] args) throws Exception {
        Utility.midnight(); //先初始化一下Utility
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
                    System.out.println(generateHelp());
                    return;
                }
                boolean restart = "restart".equalsIgnoreCase(cmd);
                AnyValue config = loadAppConfig();
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
            for (ApplicationListener listener : application.listeners) {
                listener.preStart(application);
            }
            application.start();
        } catch (Exception e) {
            application.logger.log(Level.SEVERE, "Application start error", e);
            System.exit(1);
        }
        System.exit(0); //必须要有
    }

    private String replaceValue(String value) {
        return value == null ? value : value.replace("${APP_HOME}", homePath).replace("${APP_NAME}", name);
    }

    void updateEnvironmentProperties(String namespace, List<ResourceEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        synchronized (envProperties) {
            Properties envRegisterProps = new Properties();
            Set<String> envRemovedKeys = new HashSet<>();
            Properties envChangedProps = new Properties();

            Set<String> sourceRemovedKeys = new HashSet<>();
            Properties sourceChangedProps = new Properties();

            Set<String> loggingRemovedKeys = new HashSet<>();
            Properties loggingChangedProps = new Properties();

            Set<String> clusterRemovedKeys = new HashSet<>();
            Properties clusterChangedProps = new Properties();

            Set<String> messageRemovedKeys = new HashSet<>();
            Properties messageChangedProps = new Properties();

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
                    if (event.name().endsWith(".name")) {
                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in source config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
                    } else {
                        if (!Objects.equals(event.newValue(), this.sourceProperties.getProperty(event.name()))) {
                            if (event.newValue() == null) {
                                if (this.sourceProperties.containsKey(event.name())) {
                                    sourceRemovedKeys.add(event.name());
                                }
                            } else {
                                sourceChangedProps.put(event.name(), event.newValue());
                            }
                        }
                    }
                } else if (event.name().startsWith("redkale.mq.") || event.name().startsWith("redkale.mq[")) {
                    if (event.name().endsWith(".name")) {
                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in mq config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
                    } else {
                        if (!Objects.equals(event.newValue(), this.messageProperties.getProperty(event.name()))) {
                            if (event.newValue() == null) {
                                if (this.messageProperties.containsKey(event.name())) {
                                    messageRemovedKeys.add(event.name());
                                }
                            } else {
                                messageChangedProps.put(event.name(), event.newValue());
                            }
                        }
                    }
                } else if (event.name().startsWith("redkale.cluster.")) {
                    if (!Objects.equals(event.newValue(), this.clusterProperties.getProperty(event.name()))) {
                        if (event.newValue() == null) {
                            if (this.clusterProperties.containsKey(event.name())) {
                                clusterRemovedKeys.add(event.name());
                            }
                        } else {
                            clusterChangedProps.put(event.name(), event.newValue());
                        }
                    }
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
                envRemovedKeys.forEach(k -> this.envProperties.remove(k));
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
                    loggingRemovedKeys.forEach(k -> newLogProps.remove(k));
                    reconfigLogging(newLogProps);
                    logger.log(Level.INFO, "Reconfig logging finished ");
                }
            }

            //数据源配置项的变更
            if (!sourceChangedProps.isEmpty() || !sourceRemovedKeys.isEmpty()) {
                Set<String> cacheSourceNames = new LinkedHashSet<>();
                Set<String> dataSourceNames = new LinkedHashSet<>();
                List<String> keys = new ArrayList<>();
                keys.addAll(sourceRemovedKeys);
                keys.addAll((Set) sourceChangedProps.keySet());
                for (final String key : keys) {
                    if (key.startsWith("redkale.cachesource[")) {
                        cacheSourceNames.add(key.substring("redkale.cachesource[".length(), key.indexOf(']')));
                    } else if (key.startsWith("redkale.cachesource.")) {
                        cacheSourceNames.add(key.substring("redkale.cachesource.".length(), key.indexOf('.', "redkale.cachesource.".length())));
                    } else if (key.startsWith("redkale.datasource[")) {
                        dataSourceNames.add(key.substring("redkale.datasource[".length(), key.indexOf(']')));
                    } else if (key.startsWith("redkale.datasource.")) {
                        dataSourceNames.add(key.substring("redkale.datasource.".length(), key.indexOf('.', "redkale.datasource.".length())));
                    }
                }
                //更新缓存
                for (String sourceName : cacheSourceNames) {
                    CacheSource source = Utility.find(cacheSources, s -> Objects.equals(s.resourceName(), sourceName));
                    if (source == null) {
                        continue;  //多余的数据源
                    }
                    final DefaultAnyValue old = (DefaultAnyValue) findSourceConfig(sourceName, "cachesource");
                    Properties newProps = new Properties();
                    this.sourceProperties.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.cachesource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.cachesource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                    });
                    List<ResourceEvent> changeEvents = new ArrayList<>();
                    sourceChangedProps.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.cachesource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.cachesource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), v, this.sourceProperties.getProperty(key)));
                    });
                    sourceRemovedKeys.forEach(k -> {
                        final String key = k;
                        String prefix = "redkale.cachesource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.cachesource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return;
                        }
                        newProps.remove(k); //不是同一name数据源配置项
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), null, this.sourceProperties.getProperty(key)));
                    });
                    if (!changeEvents.isEmpty()) {
                        DefaultAnyValue back = old.copy();
                        try {
                            old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("cachesource").getAnyValue(sourceName));
                            ((AbstractCacheSource) source).onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                        } catch (RuntimeException e) {
                            old.replace(back);  //还原配置
                            throw e;
                        }
                    }
                }
                //更新数据库                
                for (String sourceName : dataSourceNames) {
                    DataSource source = Utility.find(dataSources, s -> Objects.equals(s.resourceName(), sourceName));
                    if (source == null) {
                        continue;  //多余的数据源
                    }
                    DefaultAnyValue old = (DefaultAnyValue) findSourceConfig(sourceName, "datasource");
                    Properties newProps = new Properties();
                    this.sourceProperties.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.datasource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.datasource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                    });
                    List<ResourceEvent> changeEvents = new ArrayList<>();
                    sourceChangedProps.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.datasource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.datasource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), v, this.sourceProperties.getProperty(key)));
                    });
                    sourceRemovedKeys.forEach(k -> {
                        final String key = k;
                        String prefix = "redkale.datasource[" + sourceName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.datasource." + sourceName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return;
                        }
                        newProps.remove(k); //不是同一name数据源配置项
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), null, this.sourceProperties.getProperty(key)));
                    });
                    if (!changeEvents.isEmpty()) {
                        DefaultAnyValue back = old.copy();
                        try {
                            old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("datasource").getAnyValue(sourceName));
                            ((AbstractDataSource) source).onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                        } catch (RuntimeException e) {
                            old.replace(back);  //还原配置
                            throw e;
                        }
                    }
                }
                sourceRemovedKeys.forEach(k -> this.sourceProperties.remove(k));
                this.sourceProperties.putAll(sourceChangedProps);
            }

            //MQ配置项的变更
            if (!messageChangedProps.isEmpty() || !messageRemovedKeys.isEmpty()) {
                Set<String> messageNames = new LinkedHashSet<>();
                List<String> keys = new ArrayList<>();
                keys.addAll(messageRemovedKeys);
                keys.addAll((Set) messageChangedProps.keySet());
                for (final String key : keys) {
                    if (key.startsWith("redkale.mq[")) {
                        messageNames.add(key.substring("redkale.mq[".length(), key.indexOf(']')));
                    } else if (key.startsWith("redkale.mq.")) {
                        messageNames.add(key.substring("redkale.mq.".length(), key.indexOf('.', "redkale.mq.".length())));
                    }
                }
                //更新MQ
                for (String mqName : messageNames) {
                    MessageAgent agent = Utility.find(messageAgents, s -> Objects.equals(s.resourceName(), mqName));
                    if (agent == null) {
                        continue;  //多余的数据源
                    }
                    final DefaultAnyValue old = (DefaultAnyValue) findMQConfig(mqName);
                    Properties newProps = new Properties();
                    this.messageProperties.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.mq[" + mqName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.mq." + mqName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                    });
                    List<ResourceEvent> changeEvents = new ArrayList<>();
                    messageChangedProps.forEach((k, v) -> {
                        final String key = k.toString();
                        String prefix = "redkale.mq[" + mqName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.mq." + mqName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return; //不是同一name数据源配置项
                        }
                        newProps.put(k, v);
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), v, this.messageProperties.getProperty(key)));
                    });
                    messageRemovedKeys.forEach(k -> {
                        final String key = k;
                        String prefix = "redkale.mq[" + mqName + "].";
                        int pos = key.indexOf(prefix);
                        if (pos < 0) {
                            prefix = "redkale.mq." + mqName + ".";
                            pos = key.indexOf(prefix);
                        }
                        if (pos < 0) {
                            return;
                        }
                        newProps.remove(k); //不是同一name数据源配置项
                        changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), null, this.messageProperties.getProperty(key)));
                    });
                    if (!changeEvents.isEmpty()) {
                        DefaultAnyValue back = old.copy();
                        try {
                            old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("mq").getAnyValue(mqName));
                            agent.onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                        } catch (RuntimeException e) {
                            old.replace(back);  //还原配置
                            throw e;
                        }
                    }
                }
                messageRemovedKeys.forEach(k -> this.messageProperties.remove(k));
                this.messageProperties.putAll(messageChangedProps);
            }

            //第三方服务注册配置项的变更
            if (!clusterChangedProps.isEmpty() || !clusterRemovedKeys.isEmpty()) {
                if (this.clusterAgent != null) {
                    final DefaultAnyValue old = (DefaultAnyValue) config.getAnyValue("cluster");
                    Properties newProps = new Properties();
                    newProps.putAll(clusterProperties);
                    List<ResourceEvent> changeEvents = new ArrayList<>();
                    clusterChangedProps.forEach((k, v) -> {
                        final String key = k.toString();
                        newProps.put(k, v);
                        changeEvents.add(ResourceEvent.create(key.substring("redkale.cluster.".length()), v, this.clusterProperties.getProperty(key)));
                    });
                    clusterRemovedKeys.forEach(k -> {
                        final String key = k;
                        newProps.remove(k);
                        changeEvents.add(ResourceEvent.create(key.substring("redkale.cluster.".length()), null, this.clusterProperties.getProperty(key)));
                    });
                    if (!changeEvents.isEmpty()) {
                        DefaultAnyValue back = old.copy();
                        try {
                            old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("cluster"));
                            clusterAgent.onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                        } catch (RuntimeException e) {
                            old.replace(back);  //还原配置
                            throw e;
                        }
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    clusterChangedProps.forEach((k, v) -> {
                        sb.append(ClusterAgent.class.getSimpleName()).append(" skip change '").append(k).append("'\r\n");
                    });
                    clusterRemovedKeys.forEach(k -> {
                        sb.append(ClusterAgent.class.getSimpleName()).append(" skip change '").append(k).append("'\r\n");
                    });
                    if (sb.length() > 0) {
                        logger.log(Level.INFO, sb.toString());
                    }
                }
                clusterRemovedKeys.forEach(k -> this.clusterProperties.remove(k));
                this.clusterProperties.putAll(clusterChangedProps);
            }
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
            + "       --api-skiprpc=[true|false]            skip @RestService(rpconly=true) service or @RestMapping(rpconly=true) method, default is true\r\n"
            + "       --api-host=[url]                      api root url, default is http://localhost\r\n"
            + "   help, -h, --help                          show this help\r\n";
    }

    NodeSncpServer findNodeSncpServer(final InetSocketAddress sncpAddr) {
        for (NodeServer node : servers) {
            if (node.isSNCP() && sncpAddr.equals(node.getSncpAddress())) {
                return (NodeSncpServer) node;
            }
        }
        return null;
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
        for (ApplicationListener listener : this.listeners) {
            try {
                listener.preShutdown(this);
            } catch (Exception e) {
                logger.log(Level.WARNING, listener.getClass() + " preShutdown erroneous", e);
            }
        }
        List<NodeServer> localServers = new ArrayList<>(servers); //顺序sncps, others, watchs
        Collections.reverse(localServers); //倒序， 必须让watchs先关闭，watch包含服务发现和注销逻辑
        if (isCompileMode() && this.messageAgents != null) {
            Set<String> names = new HashSet<>();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent stopping");
            }
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                agent.stop().join();
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") stop in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (!isCompileMode() && clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "ClusterAgent destroying");
            }
            long s = System.currentTimeMillis();
            clusterAgent.deregister(this);
            clusterAgent.destroy(clusterAgent.getConfig());
            logger.info("ClusterAgent destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        localServers.stream().forEach((server) -> {
            try {
                server.shutdown();
            } catch (Exception t) {
                logger.log(Level.WARNING, " shutdown server(" + server.getSocketAddress() + ") error", t);
            } finally {
                shutdownLatch.countDown();
            }
        });
        if (this.messageAgents != null) {
            Set<String> names = new HashSet<>();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent destroying");
            }
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                agent.destroy(agent.getConfig());
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        for (DataSource source : dataSources) {
            if (source == null) {
                continue;
            }
            try {
                if (source instanceof Service) {
                    long s = System.currentTimeMillis();
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
                    logger.info(source + " destroy in " + (System.currentTimeMillis() - s) + " ms");
//                } else {
//                    source.getClass().getMethod("close").invoke(source);
//                    RedkaleClassLoader.putReflectionMethod(source.getClass().getName(), source.getClass().getMethod("close"));
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close DataSource erroneous", e);
            }
        }
        for (CacheSource source : cacheSources) {
            if (source == null) {
                continue;
            }
            try {
                if (source instanceof Service) {
                    long s = System.currentTimeMillis();
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
                    logger.info(source + " destroy in " + (System.currentTimeMillis() - s) + " ms");
//                } else {
//                    source.getClass().getMethod("close").invoke(source);
//                    RedkaleClassLoader.putReflectionMethod(source.getClass().getName(), source.getClass().getMethod("close"));
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close CacheSource erroneous", e);
            }
        }
        if (this.propertiesAgent != null) {
            long s = System.currentTimeMillis();
            this.propertiesAgent.destroy(config.getAnyValue("properties"));
            logger.info(this.propertiesAgent.getClass().getSimpleName() + " destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (this.clientAsyncGroup != null) {
            long s = System.currentTimeMillis();
            ((AsyncIOGroup) this.clientAsyncGroup).dispose();
            logger.info("AsyncGroup destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        this.sncpTransportFactory.shutdownNow();
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    public AsyncGroup getClientAsyncGroup() {
        return clientAsyncGroup;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public TransportFactory getSncpTransportFactory() {
        return sncpTransportFactory;
    }

    public ClusterAgent getClusterAgent() {
        return clusterAgent;
    }

    public MessageAgent getMessageAgent(String name) {
        if (messageAgents == null) {
            return null;
        }
        for (MessageAgent agent : messageAgents) {
            if (agent.getName().equals(name)) {
                return agent;
            }
        }
        return null;
    }

    public MessageAgent[] getMessageAgents() {
        return messageAgents;
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

    public List<DataSource> getDataSources() {
        return new ArrayList<>(dataSources);
    }

    public List<CacheSource> getCacheSources() {
        return new ArrayList<>(cacheSources);
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

    private static int parseLenth(String value, int defValue) {
        if (value == null) {
            return defValue;
        }
        value = value.toUpperCase().replace("B", "");
        if (value.endsWith("G")) {
            return Integer.decode(value.replace("G", "")) * 1024 * 1024 * 1024;
        }
        if (value.endsWith("M")) {
            return Integer.decode(value.replace("M", "")) * 1024 * 1024;
        }
        if (value.endsWith("K")) {
            return Integer.decode(value.replace("K", "")) * 1024;
        }
        return Integer.decode(value);
    }

}
