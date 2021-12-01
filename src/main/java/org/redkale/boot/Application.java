/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import org.redkale.cluster.ClusterAgent;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.net.TransportGroupInfo;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.net.ssl.SSLContext;
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
import static org.redkale.source.DataSources.DATASOURCE_CONFPATH;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;
import org.redkale.watch.*;

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
     * 当前进程的名称， 类型：String
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
    public static final String RESNAME_APP_CONF = "APP_CONF";

    /**
     * application.xml 文件中resources节点的内容， 类型： AnyValue
     */
    public static final String RESNAME_APP_GRES = "APP_GRES";

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
    public static final String RESNAME_APP_ASYNCGROUP = "APP_ASYNCGROUP";

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
    public static final String RESNAME_SERVER_RESFACTORY = Server.RESNAME_SERVER_RESFACTORY;

    //本进程节点ID
    final int nodeid;

    //本进程节点ID
    final String name;

    //本地IP地址
    final InetSocketAddress localAddress;

    //业务逻辑线程池
    //@since 2.3.0
    final ExecutorService workExecutor;

    //CacheSource 资源
    final List<CacheSource> cacheSources = new CopyOnWriteArrayList<>();

    //DataSource 资源
    final List<DataSource> dataSources = new CopyOnWriteArrayList<>();

    //NodeServer 资源, 顺序必须是sncps, others, watchs
    final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    //SNCP传输端的TransportFactory, 注意： 只给SNCP使用
    final TransportFactory sncpTransportFactory;

    //给客户端使用，包含SNCP客户端、自定义数据库客户端连接池
    final AsyncGroup asyncGroup;

    //第三方服务发现管理接口
    //@since 2.1.0
    final ClusterAgent clusterAgent;

    //MQ管理接口
    //@since 2.1.0
    final MessageAgent[] messageAgents;

    //全局根ResourceFactory
    final ResourceFactory resourceFactory = ResourceFactory.create();

    //服务配置项
    final AnyValue config;

    //是否从/META-INF中读取配置
    final boolean configFromCache;

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

    //配置文件目录
    private final URI confPath;

    //日志
    private final Logger logger;

    //监听事件
    final List<ApplicationListener> listeners = new CopyOnWriteArrayList<>();

    //服务启动时间
    private final long startTime = System.currentTimeMillis();

    //Server启动的计数器，用于确保所有Server都启动完后再进行下一步处理
    private final CountDownLatch serversLatch;

    //根ClassLoader
    private final RedkaleClassLoader classLoader;

    //Server根ClassLoader
    private final RedkaleClassLoader serverClassLoader;

    Application(final AnyValue config) {
        this(false, false, config);
    }

    @SuppressWarnings("UseSpecificCatch")
    Application(final boolean singletonMode, boolean compileMode, final AnyValue config) {
        this.singletonMode = singletonMode;
        this.compileMode = compileMode;
        this.config = config;
        this.configFromCache = "true".equals(config.getValue("[config-from-cache]"));
        System.setProperty("redkale.version", Redkale.getDotedVersion());

        final File root = new File(System.getProperty(RESNAME_APP_HOME));
        this.resourceFactory.register(RESNAME_APP_TIME, long.class, this.startTime);
        this.resourceFactory.register(RESNAME_APP_HOME, Path.class, root.toPath());
        this.resourceFactory.register(RESNAME_APP_HOME, File.class, root);
        this.resourceFactory.register(RESNAME_APP_HOME, URI.class, root.toURI());
        File confFile = null;
        try {
            this.resourceFactory.register(RESNAME_APP_HOME, root.getCanonicalPath());
            if (System.getProperty(RESNAME_APP_HOME) == null) {
                System.setProperty(RESNAME_APP_HOME, root.getCanonicalPath());
            }
            this.home = root.getCanonicalFile();
            String confDir = System.getProperty(RESNAME_APP_CONF, "conf");
            if (confDir.contains("://") || confDir.startsWith("file:") || confDir.startsWith("resource:") || confDir.contains("!")) { //graalvm native-image startwith resource:META-INF
                this.confPath = new URI(confDir);
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String localaddr = config.getValue("address", "").trim();
        InetAddress addr = localaddr.isEmpty() ? Utility.localInetAddress() : new InetSocketAddress(localaddr, config.getIntValue("port")).getAddress();
        this.localAddress = new InetSocketAddress(addr, config.getIntValue("port"));
        this.resourceFactory.register(RESNAME_APP_ADDR, addr.getHostAddress());
        this.resourceFactory.register(RESNAME_APP_ADDR, InetAddress.class, addr);
        this.resourceFactory.register(RESNAME_APP_ADDR, InetSocketAddress.class, this.localAddress);

        this.resourceFactory.register(RESNAME_APP_CONF, URI.class, this.confPath);
        this.resourceFactory.register(RESNAME_APP_CONF, String.class, this.confPath.toString());
        if (confFile != null) {
            this.resourceFactory.register(RESNAME_APP_CONF, File.class, confFile);
            this.resourceFactory.register(RESNAME_APP_CONF, Path.class, confFile.toPath());
        }

        {
            int nid = config.getIntValue("nodeid", 0);
            this.nodeid = nid;
            this.resourceFactory.register(RESNAME_APP_NODEID, nid);
            System.setProperty(RESNAME_APP_NODEID, "" + nid);
        }
        {
            this.name = checkName(config.getValue("name", ""));
            this.resourceFactory.register(RESNAME_APP_NAME, name);
            System.setProperty(RESNAME_APP_NAME, name);
        }
        {
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
                            if (!list.isEmpty()) cacheClasses = new LinkedHashSet<>(list);
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
        //以下是初始化日志配置
        {
            URI logConfURI;
            File logConfFile = null;
            if (configFromCache) {
                logConfURI = RedkaleClassLoader.getConfResourceAsURI(null, "logging.properties");
            } else if ("file".equals(confPath.getScheme())) {
                logConfFile = new File(confPath.getPath(), "logging.properties");
                logConfURI = logConfFile.toURI();
                if (!logConfFile.isFile() || !logConfFile.canRead()) logConfFile = null;
            } else {
                logConfURI = URI.create(confPath + (confPath.toString().endsWith("/") ? "" : "/") + "logging.properties");
            }
            if (!"file".equals(confPath.getScheme()) || logConfFile != null) {
                try {
                    final String rootpath = root.getCanonicalPath().replace('\\', '/');
                    InputStream fin = logConfURI.toURL().openStream();
                    Properties properties = new Properties();
                    properties.load(fin);
                    fin.close();
                    properties.entrySet().forEach(x -> x.setValue(x.getValue().toString().replace("${APP_HOME}", rootpath)));

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
                    String fileHandlerPattern = properties.getProperty("java.util.logging.FileHandler.pattern");
                    if (fileHandlerPattern != null && fileHandlerPattern.contains("%d")) {
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
                    LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
                } catch (Exception e) {
                    Logger.getLogger(this.getClass().getSimpleName()).log(Level.WARNING, "init logger configuration error", e);
                }
            }
        }
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.serversLatch = new CountDownLatch(config.getAnyValues("server").length + 1);
        logger.log(Level.INFO, "------------------------- Redkale " + Redkale.getDotedVersion() + " -------------------------");
        //------------------配置 <transport> 节点 ------------------
        final AnyValue resources = config.getAnyValue("resources");
        TransportStrategy strategy = null;
        String excludelib0 = null;
        ClusterAgent cluster = null;
        MessageAgent[] mqs = null;
        int bufferCapacity = 32 * 1024;
        int bufferPoolSize = Utility.cpus() * 8;
        int readTimeoutSeconds = TransportFactory.DEFAULT_READTIMEOUTSECONDS;
        int writeTimeoutSeconds = TransportFactory.DEFAULT_WRITETIMEOUTSECONDS;
        AnyValue executorConf = null;
        if (resources != null) {
            executorConf = resources.getAnyValue("executor");
            AnyValue excludelibConf = resources.getAnyValue("excludelibs");
            if (excludelibConf != null) excludelib0 = excludelibConf.getValue("value");
            AnyValue transportConf = resources.getAnyValue("transport");
            int groupsize = resources.getAnyValues("group").length;
            if (groupsize > 0 && transportConf == null) transportConf = new DefaultAnyValue();
            if (transportConf != null) {
                //--------------transportBufferPool-----------
                bufferCapacity = Math.max(parseLenth(transportConf.getValue("bufferCapacity"), bufferCapacity), 32 * 1024);
                readTimeoutSeconds = transportConf.getIntValue("readTimeoutSeconds", readTimeoutSeconds);
                writeTimeoutSeconds = transportConf.getIntValue("writeTimeoutSeconds", writeTimeoutSeconds);
                final int threads = parseLenth(transportConf.getValue("threads"), groupsize * Utility.cpus() * 2);
                bufferPoolSize = parseLenth(transportConf.getValue("bufferPoolSize"), threads * 4);
            }

            AnyValue clusterConf = resources.getAnyValue("cluster");
            if (clusterConf != null) {
                try {
                    String classval = clusterConf.getValue("value");
                    if (classval == null || classval.isEmpty()) {
                        Iterator<ClusterAgentProvider> it = ServiceLoader.load(ClusterAgentProvider.class, classLoader).iterator();
                        RedkaleClassLoader.putServiceLoader(ClusterAgentProvider.class);
                        while (it.hasNext()) {
                            ClusterAgentProvider agent = it.next();
                            if (agent != null) RedkaleClassLoader.putReflectionPublicConstructors(agent.getClass(), agent.getClass().getName()); //loader class
                            if (agent != null && agent.acceptsConf(clusterConf)) {
                                RedkaleClassLoader.putReflectionPublicConstructors(agent.getClass(), agent.agentClass().getName()); //agent class
                                cluster = agent.agentClass().getConstructor().newInstance();
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
                        if (cluster == null) logger.log(Level.SEVERE, "load application cluster resource, but not found name='value' value error: " + clusterConf);
                    } else {
                        Class type = classLoader.loadClass(clusterConf.getValue("value"));
                        if (!ClusterAgent.class.isAssignableFrom(type)) {
                            logger.log(Level.SEVERE, "load application cluster resource, but not found " + ClusterAgent.class.getSimpleName() + " implements class error: " + clusterConf);
                        } else {
                            RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                            cluster = (ClusterAgent) type.getDeclaredConstructor().newInstance();
                            cluster.setConfig(clusterConf);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "load application cluster resource error: " + clusterConf, e);
                }
            }

            AnyValue[] mqConfs = resources.getAnyValues("mq");
            if (mqConfs != null && mqConfs.length > 0) {
                mqs = new MessageAgent[mqConfs.length];
                Set<String> mqnames = new HashSet<>();
                for (int i = 0; i < mqConfs.length; i++) {
                    AnyValue mqConf = mqConfs[i];
                    String mqname = mqConf.getValue("name", "");
                    if (mqnames.contains(mqname)) throw new RuntimeException("mq.name(" + mqname + ") is repeat");
                    mqnames.add(mqname);
                    String namex = mqConf.getValue("names");
                    if (namex != null && !namex.isEmpty()) {
                        for (String n : namex.split(";")) {
                            if (n.trim().isEmpty()) continue;
                            if (mqnames.contains(n.trim())) throw new RuntimeException("mq.name(" + n.trim() + ") is repeat");
                            mqnames.add(n.trim());
                        }
                    }
                    try {
                        String classval = mqConf.getValue("value");
                        if (classval == null || classval.isEmpty()) {
                            Iterator<MessageAgentProvider> it = ServiceLoader.load(MessageAgentProvider.class, classLoader).iterator();
                            RedkaleClassLoader.putServiceLoader(MessageAgentProvider.class);
                            while (it.hasNext()) {
                                MessageAgentProvider messageAgent = it.next();
                                if (messageAgent != null) RedkaleClassLoader.putReflectionPublicConstructors(messageAgent.getClass(), messageAgent.getClass().getName()); //loader class
                                if (messageAgent != null && messageAgent.acceptsConf(mqConf)) {
                                    RedkaleClassLoader.putReflectionPublicConstructors(messageAgent.getClass(), messageAgent.agentClass().getName()); //agent class
                                    mqs[i] = messageAgent.agentClass().getConstructor().newInstance();
                                    mqs[i].setConfig(mqConf);
                                    break;
                                }
                            }
                            if (mqs[i] == null) logger.log(Level.SEVERE, "load application mq resource, but not found name='value' value error: " + mqConf);
                        } else {
                            Class type = classLoader.loadClass(classval);
                            if (!MessageAgent.class.isAssignableFrom(type)) {
                                logger.log(Level.SEVERE, "load application mq resource, but not found " + MessageAgent.class.getSimpleName() + " implements class error: " + mqConf);
                            } else {
                                RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                                mqs[i] = (MessageAgent) type.getDeclaredConstructor().newInstance();
                                mqs[i].setConfig(mqConf);
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "load application mq resource error: " + mqs[i], e);
                    }
                }
            }
        }

        ExecutorService workExecutor0 = null;
        {
            if (executorConf == null) executorConf = DefaultAnyValue.create();
            final AtomicReference<ExecutorService> workref = new AtomicReference<>();
            final int executorThreads = executorConf.getIntValue("threads", Math.max(2, Utility.cpus()));
            boolean executorHash = executorConf.getBoolValue("hash");
            if (executorThreads > 0) {
                final AtomicInteger workcounter = new AtomicInteger();
                if (executorHash) {
                    workExecutor0 = new ThreadHashExecutor(executorThreads, (Runnable r) -> {
                        int i = workcounter.get();
                        int c = workcounter.incrementAndGet();
                        String threadname = "Redkale-HashWorkThread-" + (c > 9 ? c : ("0" + c));
                        Thread t = new WorkThread(threadname, i, executorThreads, workref.get(), r);
                        return t;
                    });
                } else {
                    workExecutor0 = Executors.newFixedThreadPool(executorThreads, (Runnable r) -> {
                        int i = workcounter.get();
                        int c = workcounter.incrementAndGet();
                        String threadname = "Redkale-WorkThread-" + (c > 9 ? c : ("0" + c));
                        Thread t = new WorkThread(threadname, i, executorThreads, workref.get(), r);
                        return t;
                    });
                }
                workref.set(workExecutor0);
            }
        }
        this.workExecutor = workExecutor0;
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, Executor.class, this.workExecutor);
        this.resourceFactory.register(RESNAME_APP_EXECUTOR, ExecutorService.class, this.workExecutor);

        this.asyncGroup = new AsyncIOGroup(true, null, this.workExecutor, bufferCapacity, bufferPoolSize);
        this.resourceFactory.register(RESNAME_APP_ASYNCGROUP, AsyncGroup.class, this.asyncGroup);

        this.excludelibs = excludelib0;
        this.sncpTransportFactory = TransportFactory.create(this.asyncGroup, (SSLContext) null, Transport.DEFAULT_NETPROTOCOL, readTimeoutSeconds, writeTimeoutSeconds, strategy);
        DefaultAnyValue tarnsportConf = DefaultAnyValue.create(TransportFactory.NAME_POOLMAXCONNS, System.getProperty("redkale.net.transport.pool.maxconns", "100"))
            .addValue(TransportFactory.NAME_PINGINTERVAL, System.getProperty("redkale.net.transport.ping.interval", "30"))
            .addValue(TransportFactory.NAME_CHECKINTERVAL, System.getProperty("redkale.net.transport.check.interval", "30"));
        this.sncpTransportFactory.init(tarnsportConf, Sncp.PING_BUFFER, Sncp.PONG_BUFFER.remaining());
        this.clusterAgent = cluster;
        this.messageAgents = mqs;
        if (compileMode || this.classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader) {
            this.serverClassLoader = this.classLoader;
        } else {
            this.serverClassLoader = new RedkaleClassLoader(this.classLoader);
        }
    }

    private String checkName(String name) {  //不能含特殊字符
        if (name == null || name.isEmpty()) return name;
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
            }
        }
        return name;
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    public AsyncGroup getAsyncGroup() {
        return asyncGroup;
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
        if (messageAgents == null) return null;
        for (MessageAgent agent : messageAgents) {
            if (agent.getName().equals(name)) return agent;
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

    public void init() throws Exception {
        System.setProperty("redkale.net.transport.poolmaxconns", "100");
        System.setProperty("redkale.net.transport.pinginterval", "30");
        System.setProperty("redkale.net.transport.checkinterval", "30");
        System.setProperty("redkale.convert.tiny", "true");
        System.setProperty("redkale.convert.pool.size", "128");
        System.setProperty("redkale.convert.writer.buffer.defsize", "4096");

        final String confDir = this.confPath.toString();
        final String homepath = this.home.getCanonicalPath();
        if ("file".equals(this.confPath.getScheme())) {
            File persist = new File(new File(confPath), "persistence.xml");
            if (persist.isFile()) System.setProperty(DataSources.DATASOURCE_CONFPATH, persist.getCanonicalPath());
        } else {
            System.setProperty(DataSources.DATASOURCE_CONFPATH, confDir + (confDir.endsWith("/") ? "" : "/") + "persistence.xml");
        }
//        String pidstr = "";
//        try { //JDK 9+
//            Class phclass = Thread.currentThread().getContextClassLoader().loadClass("java.lang.ProcessHandle");
//            Object phobj = phclass.getMethod("current").invoke(null);
//            Object pid = phclass.getMethod("pid").invoke(phobj);
//            pidstr = "APP_PID  = " + pid + "\r\n";
//        } catch (Throwable t) {
//        }

        logger.log(Level.INFO, "APP_OSNAME = " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\r\n"
            + "APP_JAVA   = " + System.getProperty("java.runtime.name", System.getProperty("org.graalvm.nativeimage.kind") != null ? "Nativeimage" : "")
            + " " + System.getProperty("java.runtime.version", System.getProperty("java.vendor.version", System.getProperty("java.vm.version"))) + "\r\n" //graalvm.nativeimage 模式下无 java.runtime.xxx 属性
            + "APP_PID    = " + ProcessHandle.current().pid() + "\r\n"
            + RESNAME_APP_NODEID + " = " + this.nodeid + "\r\n"
            + "APP_LOADER = " + this.classLoader.getClass().getSimpleName() + "\r\n"
            + RESNAME_APP_ADDR + "   = " + this.localAddress.getHostString() + ":" + this.localAddress.getPort() + "\r\n"
            + RESNAME_APP_HOME + "   = " + homepath + "\r\n"
            + RESNAME_APP_CONF + "   = " + confDir.substring(confDir.indexOf('!') + 1));

        if (!compileMode && !(classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader)) {
            String lib = config.getValue("lib", "${APP_HOME}/libs/*").trim().replace("${APP_HOME}", homepath);
            lib = lib.isEmpty() ? confDir : (lib + ";" + confDir);
            Server.loadLib(classLoader, logger, lib);
        }

        //------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            resourceFactory.register(RESNAME_APP_GRES, AnyValue.class, resources);
            final AnyValue properties = resources.getAnyValue("properties");
            if (properties != null) {
                String dfloads = properties.getValue("load");
                if (dfloads != null) {
                    for (String dfload : dfloads.split(";")) {
                        if (dfload.trim().isEmpty()) continue;
                        final URI df = RedkaleClassLoader.getConfResourceAsURI(configFromCache ? null : confDir, dfload.trim());
                        if (df != null && (!"file".equals(df.getScheme()) || df.toString().contains("!") || new File(df).isFile())) {
                            Properties ps = new Properties();
                            try {
                                InputStream in = df.toURL().openStream();
                                ps.load(in);
                                in.close();
                                ps.forEach((x, y) -> resourceFactory.register("property." + x, y.toString().replace("${APP_HOME}", homepath)));
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "load properties(" + dfload + ") error", e);
                            }
                        }
                    }
                }
                for (AnyValue prop : properties.getAnyValues("property")) {
                    String name = prop.getValue("name");
                    String value = prop.getValue("value");
                    if (name == null || value == null) continue;
                    value = value.replace("${APP_HOME}", homepath);
                    if (name.startsWith("system.property.")) {
                        System.setProperty(name.substring("system.property.".length()), value);
                    } else if (name.startsWith("mimetype.property.")) {
                        MimeType.add(name.substring("mimetype.property.".length()), value);
                    } else if (name.startsWith("property.")) {
                        resourceFactory.register(name, value);
                    } else {
                        resourceFactory.register("property." + name, value);
                    }
                }
            }
        }
        this.resourceFactory.register(BsonFactory.root());
        this.resourceFactory.register(JsonFactory.root());
        this.resourceFactory.register(BsonFactory.root().getConvert());
        this.resourceFactory.register(JsonFactory.root().getConvert());
        this.resourceFactory.register("bsonconvert", Convert.class, BsonFactory.root().getConvert());
        this.resourceFactory.register("jsonconvert", Convert.class, JsonFactory.root().getConvert());
        //只有WatchService才能加载Application、WatchFactory
        final Application application = this;
        this.resourceFactory.register(new ResourceFactory.ResourceLoader() {

            @Override
            public void load(ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) {
                try {
                    Resource res = field.getAnnotation(Resource.class);
                    if (res == null) return;
                    if (src instanceof Service && Sncp.isRemote((Service) src)) return; //远程模式不得注入 
                    Class type = field.getType();
                    if (type == Application.class) {
                        field.set(src, application);
                    } else if (type == ResourceFactory.class) {
                        boolean serv = RESNAME_SERVER_RESFACTORY.equals(res.name()) || res.name().equalsIgnoreCase("server");
                        field.set(src, serv ? rf : (res.name().isEmpty() ? application.resourceFactory : null));
                    } else if (type == TransportFactory.class) {
                        field.set(src, application.sncpTransportFactory);
                    } else if (type == NodeSncpServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeSncpServer.class) continue;
                            if (res.name().equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(src, server);
                    } else if (type == NodeHttpServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeHttpServer.class) continue;
                            if (res.name().equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(src, server);
                    } else if (type == NodeWatchServer.class) {
                        NodeServer server = null;
                        for (NodeServer ns : application.getNodeServers()) {
                            if (ns.getClass() != NodeWatchServer.class) continue;
                            if (res.name().equals(ns.server.getName())) {
                                server = ns;
                                break;
                            }
                        }
                        field.set(src, server);
                    }
//                    if (type == WatchFactory.class) {
//                        field.set(src, application.watchFactory);
//                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Resource inject error", e);
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }

        }, Application.class, ResourceFactory.class, TransportFactory.class, NodeSncpServer.class, NodeHttpServer.class, NodeWatchServer.class);

        //------------------------------------- 注册 java.net.http.HttpClient --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder();
                if (resourceName.endsWith(".1.1")) {
                    builder.version(HttpClient.Version.HTTP_1_1);
                } else if (resourceName.endsWith(".2")) {
                    builder.version(HttpClient.Version.HTTP_2);
                }
                java.net.http.HttpClient httpClient = builder.build();
                field.set(src, httpClient);
                rf.inject(httpClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, java.net.http.HttpClient.class, httpClient);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[" + Thread.currentThread().getName() + "] java.net.http.HttpClient inject error", e);
            }
        }, java.net.http.HttpClient.class);
        //------------------------------------- 注册 HttpSimpleClient --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                HttpSimpleClient httpClient = HttpSimpleClient.create(asyncGroup);
                field.set(src, httpClient);
                rf.inject(httpClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpSimpleClient.class, httpClient);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[" + Thread.currentThread().getName() + "] HttpClient inject error", e);
            }
        }, HttpSimpleClient.class);
        //--------------------------------------------------------------------------
        if (this.asyncGroup != null) {
            ((AsyncIOGroup) this.asyncGroup).start();
        }
        if (this.clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "ClusterAgent initing");
            long s = System.currentTimeMillis();
            if (this.clusterAgent instanceof CacheClusterAgent) {
                String sourceName = ((CacheClusterAgent) clusterAgent).getSourceName(); //必须在inject前调用，需要赋值Resourcable.name
                loadCacheSource(sourceName);
            }
            clusterAgent.setTransportFactory(this.sncpTransportFactory);
            this.resourceFactory.inject(clusterAgent);
            clusterAgent.init(clusterAgent.getConfig());
            this.resourceFactory.register(ClusterAgent.class, clusterAgent);
            logger.info("ClusterAgent init in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (this.messageAgents != null) {
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "MessageAgent initing");
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                this.resourceFactory.inject(agent);
                agent.init(agent.getConfig());
                this.resourceFactory.register(agent.getName(), MessageAgent.class, agent);
                this.resourceFactory.register(agent.getName(), HttpMessageClient.class, agent.getHttpMessageClient());
                //this.resourceFactory.register(agent.getName(), SncpMessageClient.class, agent.getSncpMessageClient()); //不需要给开发者使用
            }
            logger.info("MessageAgent init in " + (System.currentTimeMillis() - s) + " ms");

        }
        //------------------------------------- 注册 HttpMessageClient --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, final Object src, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null) return;
                if (clusterAgent == null) {
                    HttpMessageClient messageClient = new HttpMessageLocalClient(application, resourceName);
                    field.set(src, messageClient);
                    rf.inject(messageClient, null); // 给其可能包含@Resource的字段赋值;
                    rf.register(resourceName, HttpMessageClient.class, messageClient);
                    return;
                }
                HttpMessageClient messageClient = new HttpMessageClusterClient(application, resourceName, clusterAgent);
                field.set(src, messageClient);
                rf.inject(messageClient, null); // 给其可能包含@Resource的字段赋值;
                rf.register(resourceName, HttpMessageClient.class, messageClient);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[" + Thread.currentThread().getName() + "] HttpMessageClient inject error", e);
            }
        }, HttpMessageClient.class);
        initResources();
    }

    private void loadCacheSource(final String sourceName) {
        final AnyValue resources = config.getAnyValue("resources");
        for (AnyValue sourceConf : resources.getAnyValues("source")) {
            if (!sourceName.equals(sourceConf.getValue("name"))) continue;
            String classval = sourceConf.getValue("value");
            try {
                Class sourceType = CacheMemorySource.class;
                if (classval == null || classval.isEmpty()) {
                    RedkaleClassLoader.putServiceLoader(CacheSourceProvider.class);
                    List<CacheSourceProvider> providers = new ArrayList<>();
                    Iterator<CacheSourceProvider> it = ServiceLoader.load(CacheSourceProvider.class, serverClassLoader).iterator();
                    while (it.hasNext()) {
                        CacheSourceProvider s = it.next();
                        if (s != null) RedkaleClassLoader.putReflectionPublicConstructors(s.getClass(), s.getClass().getName());
                        if (s != null && s.acceptsConf(sourceConf)) {
                            providers.add(s);
                        }
                    }
                    Collections.sort(providers, (a, b) -> {
                        Priority p1 = a == null ? null : a.getClass().getAnnotation(Priority.class);
                        Priority p2 = b == null ? null : b.getClass().getAnnotation(Priority.class);
                        return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
                    });
                    for (CacheSourceProvider provider : providers) {
                        sourceType = provider.sourceClass();
                        if (sourceType != null) break;
                    }
                } else {
                    sourceType = serverClassLoader.loadClass(classval);
                }
                RedkaleClassLoader.putReflectionPublicConstructors(sourceType, sourceType.getName());
                CacheSource source = sourceType == CacheMemorySource.class ? new CacheMemorySource(sourceName)
                    : Modifier.isFinal(sourceType.getModifiers()) ? (CacheSource) sourceType.getConstructor().newInstance()
                    : (CacheSource) Sncp.createLocalService(serverClassLoader, sourceName, sourceType, null, resourceFactory, sncpTransportFactory, null, null, sourceConf);
                cacheSources.add((CacheSource) source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                resourceFactory.inject(source);
                if (!compileMode && source instanceof Service) ((Service) source).init(sourceConf);
                logger.info("[" + Thread.currentThread().getName() + "] Load Source resourceName = " + sourceName + ", source = " + source);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application source resource error: " + sourceConf, e);
            }
            return;
        }
    }

    private void initResources() throws Exception {
        //-------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (!compileMode && !singletonMode && configFromCache) {
            System.setProperty(DATASOURCE_CONFPATH, ""); //必须要清空
        }
        if (resources != null) {
            //------------------------------------------------------------------------
            for (AnyValue conf : resources.getAnyValues("group")) {
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
            for (AnyValue conf : resources.getAnyValues("listener")) {
                final String listenClass = conf.getValue("value", "");
                if (listenClass.isEmpty()) continue;
                Class clazz = classLoader.loadClass(listenClass);
                if (!ApplicationListener.class.isAssignableFrom(clazz)) continue;
                RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
                @SuppressWarnings("unchecked")
                ApplicationListener listener = (ApplicationListener) clazz.getDeclaredConstructor().newInstance();
                resourceFactory.inject(listener);
                listener.init(config);
                this.listeners.add(listener);
            }
        }
        //------------------------------------------------------------------------
    }

    private void startSelfServer() throws Exception {
        final Application application = this;
        new Thread() {
            {
                setName("Redkale-Application-SelfServer-Thread");
            }

            @Override
            public void run() {
                try {
                    final DatagramChannel channel = DatagramChannel.open();
                    channel.configureBlocking(true);
                    channel.socket().setSoTimeout(3000);
                    channel.bind(new InetSocketAddress("127.0.0.1", config.getIntValue("port")));
                    boolean loop = true;
                    ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
                    while (loop) {
                        buffer.clear();
                        SocketAddress address = channel.receive(buffer);
                        buffer.flip();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        String[] param = JsonConvert.root().convertFrom(String[].class, bytes);
                        final String cmd = param[0];
                        if ("SHUTDOWN".equalsIgnoreCase(cmd)) {
                            try {
                                long s = System.currentTimeMillis();
                                logger.info(application.getClass().getSimpleName() + " shutdowning");
                                application.shutdown();
                                buffer.clear();
                                buffer.put("SHUTDOWN OK".getBytes());
                                buffer.flip();
                                channel.send(buffer, address);
                                long e = System.currentTimeMillis() - s;
                                logger.info(application.getClass().getSimpleName() + " shutdown in " + e + " ms");
                                application.serversLatch.countDown();
                                System.exit(0);
                            } catch (Exception ex) {
                                logger.log(Level.INFO, "SHUTDOWN FAIL", ex);
                                buffer.clear();
                                buffer.put("SHUTDOWN FAIL".getBytes());
                                buffer.flip();
                                channel.send(buffer, address);
                            }
                        } else if ("APIDOC".equalsIgnoreCase(cmd)) {
                            try {
                                String[] args = new String[param.length - 1];
                                if (param.length > 1) System.arraycopy(param, 1, args, 0, args.length);
                                new ApiDocsService(application).run(args);
                                buffer.clear();
                                buffer.put("APIDOC OK".getBytes());
                                buffer.flip();
                                channel.send(buffer, address);
                            } catch (Exception ex) {
                                buffer.clear();
                                buffer.put("APIDOC FAIL".getBytes());
                                buffer.flip();
                                channel.send(buffer, address);
                            }
                        } else {
                            long s = System.currentTimeMillis();
                            logger.info(application.getClass().getSimpleName() + " command " + cmd);
                            application.command(cmd);
                            buffer.clear();
                            buffer.put("COMMAND OK".getBytes());
                            buffer.flip();
                            channel.send(buffer, address);
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

    private static void sendCommand(Logger logger, int port, String command, String[] args) throws Exception {
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress("127.0.0.1", port));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        String[] param = new String[1 + (args == null ? 0 : args.length)];
        param[0] = command;
        if (args != null) System.arraycopy(args, 0, param, 1, args.length);
        buffer.put(JsonConvert.root().convertToBytes(param));
        buffer.flip();
        channel.write(buffer);
        buffer.clear();
        channel.configureBlocking(true);
        try {
            channel.read(buffer);
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            channel.close();
            if (logger != null) logger.info("Send: " + command + ", Reply: " + new String(bytes));
            Thread.sleep(1000);
        } catch (Exception e) {
            if (e instanceof PortUnreachableException) {
                if ("APIDOC".equalsIgnoreCase(command)) {
                    final Application application = Application.create(true);
                    application.init();
                    application.start();
                    new ApiDocsService(application).run(args);
                    if (logger != null) logger.info("APIDOC OK");
                    return;
                }
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
        if (watchs.size() > 1) throw new RuntimeException("Found one more WATCH Server");
        this.watching = !watchs.isEmpty();

        runServers(timecd, sncps);  //必须确保SNCP服务都启动后再启动其他服务
        runServers(timecd, others);
        runServers(timecd, watchs); //必须在所有服务都启动后再启动WATCH服务
        timecd.await();
        if (this.clusterAgent != null) this.clusterAgent.start();
        if (this.messageAgents != null) {
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "MessageAgent starting");
            long s = System.currentTimeMillis();
            final StringBuffer sb = new StringBuffer();
            Set<String> names = new HashSet<>();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                Map<String, Long> map = agent.start().join();
                AtomicInteger maxlen = new AtomicInteger();
                map.keySet().forEach(str -> {
                    if (str.length() > maxlen.get()) maxlen.set(str.length());
                });
                new TreeMap<>(map).forEach((topic, ms) -> sb.append("MessageConsumer(topic=").append(alignString(topic, maxlen.get())).append(") init and start in ").append(ms).append(" ms\r\n")
                );
            }
            if (sb.length() > 0) logger.info(sb.toString().trim());
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") start in " + (System.currentTimeMillis() - s) + " ms");
        }
        //if (!singletonrun) signalHandle();
        //if (!singletonrun) clearPersistData();
        logger.info(this.getClass().getSimpleName() + " started in " + (System.currentTimeMillis() - startTime) + " ms\r\n");
        for (ApplicationListener listener : this.listeners) {
            listener.postStart(this);
        }
        if (!singletonMode && !compileMode) this.serversLatch.await();
    }

    private static String alignString(String value, int maxlen) {
        StringBuilder sb = new StringBuilder(maxlen);
        sb.append(value);
        for (int i = 0; i < maxlen - value.length(); i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

//    private void clearPersistData() {
//        File cachedir = new File(home, "cache");
//        if (!cachedir.isDirectory()) return;
//        File[] lfs = cachedir.listFiles();
//        if (lfs != null) {
//            for (File file : lfs) {
//                if (file.getName().startsWith("persist-")) file.delete();
//            }
//        }
//    }
//    private void signalHandle() {
//        //http://www.comptechdoc.org/os/linux/programming/linux_pgsignals.html
//        String[] sigs = new String[]{"HUP", "TERM", "INT", "QUIT", "KILL", "TSTP", "USR1", "USR2", "STOP"};
//        List<sun.misc.Signal> list = new ArrayList<>();
//        for (String sig : sigs) {
//            try {
//                list.add(new sun.misc.Signal(sig));
//            } catch (Exception e) {
//            }
//        }
//        sun.misc.SignalHandler handler = new sun.misc.SignalHandler() {
//
//            private volatile boolean runed;
//
//            @Override
//            public void handle(Signal sig) {
//                if (runed) return;
//                runed = true;
//                logger.info(Application.this.getClass().getSimpleName() + " stoped\r\n");
//                System.exit(0);
//            }
//        };
//        for (Signal sig : list) {
//            try {
//                Signal.handle(sig, handler);
//            } catch (Exception e) {
//            }
//        }
//    }
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
                                            if ("SNCP".equals(p) || "HTTP".equals(p)) continue;
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
                            if (nodeClass != null) server = NodeServer.create(nodeClass, Application.this, serconf);
                        }
                        if (server == null) {
                            logger.log(Level.SEVERE, "Not found Server Class for protocol({0})", serconf.getValue("protocol"));
                            System.exit(0);
                        }
                        servers.add(server);
                        server.init(serconf);
                        if (!singletonMode && !compileMode) {
                            server.start();
                        } else if (compileMode) {
                            server.getServer().getPrepareServlet().init(server.getServer().getContext(), serconf);
                        }
                        timecd.countDown();
                        sercdl.countDown();
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, serconf + " runServers error", ex);
                        Application.this.serversLatch.countDown();
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
        if (serviceClass == null) throw new IllegalArgumentException("serviceClass is null");
        final Application application = Application.create(true);
        System.setProperty("red" + "kale.singleton.serviceclass", serviceClass.getName());
        if (extServiceClasses != null && extServiceClasses.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Class clazz : extServiceClasses) {
                if (sb.length() > 0) sb.append(',');
                sb.append(clazz.getName());
            }
            System.setProperty("red" + "kale.singleton.extserviceclasses", sb.toString());
        }
        application.init();
        application.start();
        for (NodeServer server : application.servers) {
            T service = server.resourceFactory.find(name, serviceClass);
            if (service != null) return service;
        }
        if (Modifier.isAbstract(serviceClass.getModifiers())) throw new IllegalArgumentException("abstract class not allowed");
        if (serviceClass.isInterface()) throw new IllegalArgumentException("interface class not allowed");
        throw new IllegalArgumentException(serviceClass.getName() + " maybe have zero not-final public method");
    }

    public static Application create(final boolean singleton) throws IOException {
        return new Application(singleton, false, loadAppConfig());
    }

    /**
     * 重新加载配置信息
     *
     * @throws IOException 异常
     */
    public void reloadConfig() throws IOException {
        AnyValue newconfig = loadAppConfig();
        final String confpath = this.confPath.toString();
        final String homepath = this.home.getCanonicalPath();
        final AnyValue resources = newconfig.getAnyValue("resources");
        if (resources != null) {
            resourceFactory.register(RESNAME_APP_GRES, AnyValue.class, resources);
            final AnyValue properties = resources.getAnyValue("properties");
            if (properties != null) {
                String dfloads = properties.getValue("load");
                if (dfloads != null) {
                    for (String dfload : dfloads.split(";")) {
                        if (dfload.trim().isEmpty()) continue;
                        final URI df = (dfload.indexOf('/') < 0) ? URI.create(confpath + (confpath.endsWith("/") ? "" : "/") + dfload) : new File(dfload).toURI();
                        if (!"file".equals(df.getScheme()) || new File(df).isFile()) {
                            Properties ps = new Properties();
                            try {
                                InputStream in = df.toURL().openStream();
                                ps.load(in);
                                in.close();
                                ps.forEach((x, y) -> resourceFactory.register("property." + x, y.toString().replace("${APP_HOME}", homepath)));
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "load properties(" + dfload + ") error", e);
                            }
                        }
                    }
                }
                for (AnyValue prop : properties.getAnyValues("property")) {
                    String name = prop.getValue("name");
                    String value = prop.getValue("value");
                    if (name == null || value == null) continue;
                    value = value.replace("${APP_HOME}", homepath);
                    if (name.startsWith("system.property.")) {
                        System.setProperty(name.substring("system.property.".length()), value);
                    } else if (name.startsWith("mimetype.property.")) {
                        MimeType.add(name.substring("mimetype.property.".length()), value);
                    } else if (name.startsWith("property.")) {
                        resourceFactory.register(name, value);
                    } else {
                        resourceFactory.register("property." + name, value);
                    }
                }
            }
        }
    }

    static AnyValue loadAppConfig() throws IOException {
        final String home = new File(System.getProperty(RESNAME_APP_HOME, "")).getCanonicalPath().replace('\\', '/');
        System.setProperty(RESNAME_APP_HOME, home);
        String confDir = System.getProperty(RESNAME_APP_CONF, "conf");
        URI appConfFile;
        boolean fromcache = false;
        if (confDir.contains("://")) {
            appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.xml");
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > 0) {
            File f = new File(confDir, "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                confDir = appConfFile.toString().replace("/application.xml", "");
                fromcache = true;
            }
        } else {
            File f = new File(new File(home, confDir), "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                confDir = appConfFile.toString().replace("/application.xml", "");
                fromcache = true;
            }
        }
        System.setProperty(RESNAME_APP_CONF, confDir);
        AnyValue conf = AnyValue.loadFromXml(appConfFile.toURL().openStream(), StandardCharsets.UTF_8, (k, v) -> v.replace("${APP_HOME}", home)).getAnyValue("application");
        if (fromcache) ((DefaultAnyValue) conf).addValue("[config-from-cache]", "true");
        return conf;
    }

    public static void main(String[] args) throws Exception {
        Utility.midnight(); //先初始化一下Utility
        Thread.currentThread().setName("Redkale-Application-Main-Thread");
        //运行主程序
        {
            String cmd = System.getProperty("cmd", System.getProperty("CMD"));
            String[] params = args;
            if (cmd == null && args != null && args.length > 0
                && args[0] != null && !args[0].trim().isEmpty() && !"start".equalsIgnoreCase(args[0])) {
                cmd = args[0];
                params = Arrays.copyOfRange(args, 1, args.length);
            }
            if (cmd != null) {
                AnyValue config = loadAppConfig();
                Application.sendCommand(null, config.getIntValue("port"), cmd, params);
                return;
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
            System.exit(0);
        }
        System.exit(0);
    }

    NodeSncpServer findNodeSncpServer(final InetSocketAddress sncpAddr) {
        for (NodeServer node : servers) {
            if (node.isSNCP() && sncpAddr.equals(node.getSncpAddress())) {
                return (NodeSncpServer) node;
            }
        }
        return null;
    }

    public void command(String cmd) {
        List<NodeServer> localServers = new ArrayList<>(servers); //顺序sncps, others, watchs        
        localServers.stream().forEach((server) -> {
            try {
                server.command(cmd);
            } catch (Exception t) {
                logger.log(Level.WARNING, " command server(" + server.getSocketAddress() + ") error", t);
            }
        });
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
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "MessageAgent stopping");
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                agent.stop().join();
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") stop in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (!isCompileMode() && clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "ClusterAgent destroying");
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
                serversLatch.countDown();
            }
        });
        if (this.messageAgents != null) {
            Set<String> names = new HashSet<>();
            if (logger.isLoggable(Level.FINER)) logger.log(Level.FINER, "MessageAgent destroying");
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                agent.destroy(agent.getConfig());
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        for (DataSource source : dataSources) {
            if (source == null) continue;
            try {
                if (source instanceof Service) {
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getConf((Service) source) : null);
//                } else {
//                    source.getClass().getMethod("close").invoke(source);
//                    RedkaleClassLoader.putReflectionMethod(source.getClass().getName(), source.getClass().getMethod("close"));
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close DataSource erroneous", e);
            }
        }
        for (CacheSource source : cacheSources) {
            if (source == null) continue;
            try {
                if (source instanceof Service) {
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getConf((Service) source) : null);
//                } else {
//                    source.getClass().getMethod("close").invoke(source);
//                    RedkaleClassLoader.putReflectionMethod(source.getClass().getName(), source.getClass().getMethod("close"));
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close CacheSource erroneous", e);
            }
        }
        if (this.asyncGroup != null) {
            ((AsyncIOGroup) this.asyncGroup).close();
        }
        this.sncpTransportFactory.shutdownNow();
    }

    private static int parseLenth(String value, int defValue) {
        if (value == null) return defValue;
        value = value.toUpperCase().replace("B", "");
        if (value.endsWith("G")) return Integer.decode(value.replace("G", "")) * 1024 * 1024 * 1024;
        if (value.endsWith("M")) return Integer.decode(value.replace("M", "")) * 1024 * 1024;
        if (value.endsWith("K")) return Integer.decode(value.replace("K", "")) * 1024;
        return Integer.decode(value);
    }

}
