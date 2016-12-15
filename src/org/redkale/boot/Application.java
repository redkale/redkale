/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.xml.parsers.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.convert.json.JsonFactory;
import org.redkale.net.*;
import org.redkale.net.http.MimeType;
import org.redkale.net.sncp.SncpClient;
import org.redkale.service.Service;
import org.redkale.source.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;
import org.redkale.watch.WatchFactory;
import org.w3c.dom.*;

/**
 * 编译时需要加入: -XDignore.symbol.file=true
 * <p>
 * 进程启动类，程序启动后读取application.xml,进行classpath扫描动态加载Service与Servlet 优先加载所有SNCP协议的服务， 再加载其他协议服务， 最后进行Service、Servlet与其他资源之间的依赖注入。
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Application {

    //当前进程启动的时间， 类型： long
    public static final String RESNAME_APP_TIME = "APP_TIME";

    //当前进程的根目录， 类型：String、File、Path
    public static final String RESNAME_APP_HOME = "APP_HOME";

    //application.xml 文件中resources节点的内容， 类型： AnyValue
    public static final String RESNAME_APP_GRES = "APP_GRES";

    //当前进程节点的name， 类型：String
    public static final String RESNAME_APP_NODE = "APP_NODE";

    //当前进程节点的IP地址， 类型：InetAddress、String
    public static final String RESNAME_APP_ADDR = "APP_ADDR";

    //当前Service的IP地址+端口 类型: SocketAddress、InetSocketAddress、String
    public static final String RESNAME_SERVER_ADDR = "SERVER_ADDR";

    //当前SNCP Server所属的组  类型: String
    public static final String RESNAME_SERVER_GROUP = "SERVER_GROUP";

    //当前Server的ROOT目录 类型：String、File、Path
    public static final String RESNAME_SERVER_ROOT = Server.RESNAME_SERVER_ROOT;

    final Map<InetSocketAddress, String> globalNodes = new HashMap<>();

    final Map<String, GroupInfo> globalGroups = new HashMap<>();

    final InetAddress localAddress;

    final List<CacheSource> cacheSources = new CopyOnWriteArrayList<>();

    final List<DataSource> dataSources = new CopyOnWriteArrayList<>();

    final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    final ObjectPool<ByteBuffer> transportBufferPool;

    final ExecutorService transportExecutor;

    final AsynchronousChannelGroup transportChannelGroup;

    final ResourceFactory resourceFactory = ResourceFactory.root();

    CountDownLatch servicecdl;  //会出现两次赋值

    //--------------------------------------------------------------------------------------------    
    private final boolean singletonrun;

    private final WatchFactory watchFactory = WatchFactory.root();

    private final File home;

    private final Logger logger;

    private final AnyValue config;

    private final long startTime = System.currentTimeMillis();

    private final CountDownLatch serversLatch;

    private Application(final AnyValue config) {
        this(false, config);
    }

    private Application(final boolean singletonrun, final AnyValue config) {
        this.singletonrun = singletonrun;
        this.config = config;

        final File root = new File(System.getProperty(RESNAME_APP_HOME));
        this.resourceFactory.register(RESNAME_APP_TIME, long.class, this.startTime);
        this.resourceFactory.register(RESNAME_APP_HOME, Path.class, root.toPath());
        this.resourceFactory.register(RESNAME_APP_HOME, File.class, root);
        try {
            this.resourceFactory.register(RESNAME_APP_HOME, root.getCanonicalPath());
            this.home = root.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String localaddr = config.getValue("address", "").trim();
        this.localAddress = localaddr.isEmpty() ? Utility.localInetAddress() : new InetSocketAddress(localaddr, config.getIntValue("port")).getAddress();
        this.resourceFactory.register(RESNAME_APP_ADDR, this.localAddress.getHostAddress());
        this.resourceFactory.register(RESNAME_APP_ADDR, InetAddress.class, this.localAddress);
        {
            String node = config.getValue("node", "").trim();
            if (node.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                byte[] bs = this.localAddress.getAddress();
                int v1 = bs[bs.length - 2] & 0xff;
                int v2 = bs[bs.length - 1] & 0xff;
                if (v1 <= 0xf) sb.append('0');
                sb.append(Integer.toHexString(v1));
                if (v2 <= 0xf) sb.append('0');
                sb.append(Integer.toHexString(v2));
                node = sb.toString();
            }
            this.resourceFactory.register(RESNAME_APP_NODE, node);
            System.setProperty(RESNAME_APP_NODE, node);
        }
        //以下是初始化日志配置
        final File logconf = new File(root, "conf/logging.properties");
        if (logconf.isFile() && logconf.canRead()) {
            try {
                final String rootpath = root.getCanonicalPath().replace('\\', '/');
                FileInputStream fin = new FileInputStream(logconf);
                Properties properties = new Properties();
                properties.load(fin);
                fin.close();
                properties.entrySet().stream().forEach(x -> {
                    x.setValue(x.getValue().toString().replace("${APP_HOME}", rootpath));
                });

                if (properties.getProperty("java.util.logging.FileHandler.formatter") == null) {
                    properties.setProperty("java.util.logging.FileHandler.formatter", LogFileHandler.LoggingFormater.class.getName());
                }
                if (properties.getProperty("java.util.logging.ConsoleHandler.formatter") == null) {
                    properties.setProperty("java.util.logging.ConsoleHandler.formatter", LogFileHandler.LoggingFormater.class.getName());
                }
                String fileHandlerPattern = properties.getProperty("java.util.logging.FileHandler.pattern");
                if (fileHandlerPattern != null && fileHandlerPattern.contains("%d")) {
                    final String fileHandlerClass = LogFileHandler.class.getName();
                    Properties prop = new Properties();
                    final String handlers = properties.getProperty("handlers");
                    if (handlers != null && handlers.contains("java.util.logging.FileHandler")) {
                        prop.setProperty("handlers", handlers.replace("java.util.logging.FileHandler", fileHandlerClass));
                    }
                    if (!prop.isEmpty()) {
                        String prefix = fileHandlerClass + ".";
                        properties.entrySet().stream().forEach(x -> {
                            if (x.getKey().toString().startsWith("java.util.logging.FileHandler.")) {
                                prop.put(x.getKey().toString().replace("java.util.logging.FileHandler.", prefix), x.getValue());
                            }
                        });
                        prop.entrySet().stream().forEach(x -> {
                            properties.put(x.getKey(), x.getValue());
                        });
                    }
                    properties.put(SncpClient.class.getSimpleName() + ".handlers", LogFileHandler.SncpLogFileHandler.class.getName());
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                final PrintStream ps = new PrintStream(out);
                properties.forEach((x, y) -> ps.println(x + "=" + y));
                LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
            } catch (Exception e) {
                Logger.getLogger(this.getClass().getSimpleName()).log(Level.WARNING, "init logger configuration error", e);
            }
        }
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        this.serversLatch = new CountDownLatch(config.getAnyValues("server").length + 1);
        //------------------配置 <transport> 节点 ------------------
        ObjectPool<ByteBuffer> transportPool = null;
        ExecutorService transportExec = null;
        AsynchronousChannelGroup transportGroup = null;
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            AnyValue transportConf = resources.getAnyValue("transport");
            int groupsize = resources.getAnyValues("group").length;
            if (groupsize > 0 && transportConf == null) transportConf = new DefaultAnyValue();
            if (transportConf != null) {
                //--------------transportBufferPool-----------
                AtomicLong createBufferCounter = watchFactory == null ? new AtomicLong() : watchFactory.createWatchNumber(Transport.class.getSimpleName() + ".Buffer.creatCounter");
                AtomicLong cycleBufferCounter = watchFactory == null ? new AtomicLong() : watchFactory.createWatchNumber(Transport.class.getSimpleName() + ".Buffer.cycleCounter");
                final int bufferCapacity = transportConf.getIntValue("bufferCapacity", 8 * 1024);
                final int bufferPoolSize = transportConf.getIntValue("bufferPoolSize", groupsize * Runtime.getRuntime().availableProcessors() * 8);
                final int threads = transportConf.getIntValue("threads", groupsize * Runtime.getRuntime().availableProcessors() * 8);
                transportPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, bufferPoolSize,
                    (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                        if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) return false;
                        e.clear();
                        return true;
                    });
                //-----------transportChannelGroup--------------
                try {
                    final AtomicInteger counter = new AtomicInteger();
                    transportExec = Executors.newFixedThreadPool(threads, (Runnable r) -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        t.setName("Transport-Thread-" + counter.incrementAndGet());
                        return t;
                    });
                    transportGroup = AsynchronousChannelGroup.withCachedThreadPool(transportExec, 1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                logger.log(Level.INFO, Transport.class.getSimpleName() + " configure bufferCapacity = " + bufferCapacity + "; bufferPoolSize = " + bufferPoolSize + "; threads = " + threads + ";");
            }
        }
        this.transportBufferPool = transportPool;
        this.transportExecutor = transportExec;
        this.transportChannelGroup = transportGroup;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public WatchFactory getWatchFactory() {
        return watchFactory;
    }

    public File getHome() {
        return home;
    }

    public long getStartTime() {
        return startTime;
    }

    private void initLogging() {

    }

    public void init() throws Exception {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "" + Runtime.getRuntime().availableProcessors() * 4);
        System.setProperty("convert.bson.tiny", "true");
        System.setProperty("convert.json.tiny", "true");
        System.setProperty("convert.bson.pool.size", "128");
        System.setProperty("convert.json.pool.size", "128");
        System.setProperty("convert.bson.writer.buffer.defsize", "4096");
        System.setProperty("convert.json.writer.buffer.defsize", "4096");

        File persist = new File(this.home, "conf/persistence.xml");
        final String homepath = this.home.getCanonicalPath();
        if (persist.isFile()) System.setProperty(DataDefaultSource.DATASOURCE_CONFPATH, persist.getCanonicalPath());
        logger.log(Level.INFO, "--------------------------- Redkale --------------------------"  + "\r\n"
            + RESNAME_APP_HOME + "= " + homepath + "\r\n" + RESNAME_APP_ADDR + "= " + this.localAddress.getHostAddress());
        String lib = config.getValue("lib", "").trim().replace("${APP_HOME}", homepath);
        lib = lib.isEmpty() ? (homepath + "/conf") : (lib + ";" + homepath + "/conf");
        Server.loadLib(logger, lib);
        initLogging();
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
                        dfload = dfload.trim().replace("${APP_HOME}", home.getCanonicalPath()).replace('\\', '/');
                        final File df = (dfload.indexOf('/') < 0) ? new File(home, "conf/" + dfload) : new File(dfload);
                        if (df.isFile()) {
                            Properties ps = new Properties();
                            InputStream in = new FileInputStream(df);
                            ps.load(in);
                            in.close();
                            ps.forEach((x, y) -> resourceFactory.register("property." + x, y));
                        }
                    }
                }
                for (AnyValue prop : properties.getAnyValues("property")) {
                    String name = prop.getValue("name");
                    String value = prop.getValue("value");
                    if (name == null || value == null) continue;
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
        if (this.localAddress != null && this.resourceFactory.find("property.datasource.nodeid", String.class) == null) {
            byte[] bs = this.localAddress.getAddress();
            int v = (0xff & bs[bs.length - 2]) % 10 * 100 + (0xff & bs[bs.length - 1]);
            this.resourceFactory.register("property.datasource.nodeid", "" + v);
        }
        this.resourceFactory.register(BsonFactory.root());
        this.resourceFactory.register(JsonFactory.root());
        this.resourceFactory.register(BsonFactory.root().getConvert());
        this.resourceFactory.register(JsonFactory.root().getConvert());
        initResources();
    }

    private void initResources() throws Exception {
        //-------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            //------------------------------------------------------------------------

            for (AnyValue conf : resources.getAnyValues("group")) {
                final String group = conf.getValue("name", "");
                final String protocol = conf.getValue("protocol", Transport.DEFAULT_PROTOCOL).toUpperCase();
                if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                    throw new RuntimeException("Not supported Transport Protocol " + conf.getValue("protocol"));
                }
                GroupInfo ginfo = globalGroups.get(group);
                if (ginfo == null) {
                    ginfo = new GroupInfo(group, protocol, conf.getValue("kind", ""), new LinkedHashSet<>());
                    globalGroups.put(group, ginfo);
                }
                for (AnyValue node : conf.getAnyValues("node")) {
                    final InetSocketAddress addr = new InetSocketAddress(node.getValue("addr"), node.getIntValue("port"));
                    ginfo.addrs.add(addr);
                    String oldgroup = globalNodes.get(addr);
                    if (oldgroup != null) throw new RuntimeException(addr + " had one more group " + (globalNodes.get(addr)));
                    globalNodes.put(addr, group);
                }
            }
        }
        //------------------------------------------------------------------------
    }

    private void startSelfServer() throws Exception {
        final Application application = this;
        new Thread() {
            {
                setName("Application-Control-Thread");
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
                        if ("SHUTDOWN".equalsIgnoreCase(new String(bytes))) {
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
                        } else if ("APIDOC".equalsIgnoreCase(new String(bytes))) {
                            try {
                                new ApiDocs(application).run();
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
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.INFO, "Control fail", e);
                    System.exit(1);
                }
            }
        }.start();
    }

    private void sendCommand(String command) throws Exception {
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress("127.0.0.1", config.getIntValue("port")));
        ByteBuffer buffer = ByteBuffer.allocate(128);
        buffer.put(command.getBytes());
        buffer.flip();
        channel.write(buffer);
        buffer.clear();
        channel.configureBlocking(false);
        channel.read(buffer);
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        channel.close();
        logger.info(new String(bytes));
        Thread.sleep(500);
    }

    public void start() throws Exception {
        final AnyValue[] entrys = config.getAnyValues("server");
        CountDownLatch timecd = new CountDownLatch(entrys.length);
        final List<AnyValue> sncps = new ArrayList<>();
        final List<AnyValue> others = new ArrayList<>();
        for (final AnyValue entry : entrys) {
            if (entry.getValue("protocol", "").toUpperCase().startsWith("SNCP")) {
                sncps.add(entry);
            } else {
                others.add(entry);
            }
        }
        //单向SNCP服务不需要对等group
        //if (!sncps.isEmpty() && globalNodes.isEmpty()) throw new RuntimeException("found SNCP Server node but not found <group> node info.");

        runServers(timecd, sncps);  //必须确保sncp都启动后再启动其他协议
        runServers(timecd, others);
        timecd.await();
        logger.info(this.getClass().getSimpleName() + " started in " + (System.currentTimeMillis() - startTime) + " ms");
        if (!singletonrun) this.serversLatch.await();
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
                    String host = serconf.getValue("host", "").replace("0.0.0.0", "[0]");
                    setName(serconf.getValue("protocol", "Server").toUpperCase() + "-" + host + ":" + serconf.getIntValue("port") + "-Thread");
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
                        } else if ("HTTP".equalsIgnoreCase(protocol)) {
                            server = new NodeHttpServer(Application.this, serconf);
                        } else {
                            if (!inited.get()) {
                                synchronized (nodeClasses) {
                                    if (!inited.getAndSet(true)) { //加载自定义的协议，如：SOCKS
                                        ClassFilter profilter = new ClassFilter(NodeProtocol.class, NodeServer.class);
                                        ClassFilter.Loader.load(home, serconf.getValue("excludelibs", "").split(";"), profilter);
                                        final Set<FilterEntry<NodeServer>> entrys = profilter.getFilterEntrys();
                                        for (FilterEntry<NodeServer> entry : entrys) {
                                            final Class<? extends NodeServer> type = entry.getType();
                                            NodeProtocol pros = type.getAnnotation(NodeProtocol.class);
                                            for (String p : pros.value()) {
                                                p = p.toUpperCase();
                                                if ("SNCP".equals(p) || "HTTP".equals(p)) continue;
                                                final Class<? extends NodeServer> old = nodeClasses.get(p);
                                                if (old != null && old != type) throw new RuntimeException("Protocol(" + p + ") had NodeServer-Class(" + old.getName() + ") but repeat NodeServer-Class(" + type.getName() + ")");
                                                nodeClasses.put(p, type);
                                            }
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
                        if (!singletonrun) server.start();
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

    public static <T extends Service> T singleton(Class<T> serviceClass) throws Exception {
        return singleton("", serviceClass);
    }

    public static <T extends Service> T singleton(String name, Class<T> serviceClass) throws Exception {
        if (serviceClass == null) throw new IllegalArgumentException("serviceClass is null");
        final Application application = Application.create(true);
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
        final String home = new File(System.getProperty(RESNAME_APP_HOME, "")).getCanonicalPath();
        System.setProperty(RESNAME_APP_HOME, home);
        File appfile = new File(home, "conf/application.xml");
        return new Application(singleton, load(new FileInputStream(appfile)));
    }

    public static void main(String[] args) throws Exception {
        Utility.midnight(); //先初始化一下Utility
        //运行主程序
        final Application application = Application.create(false);
        if (System.getProperty("CMD") != null) {
            application.sendCommand(System.getProperty("CMD"));
            return;
        } else if (System.getProperty("SHUTDOWN") != null) { //兼容旧接口
            application.sendCommand("SHUTDOWN");
            return;
        }
        application.init();
        application.startSelfServer();
        try {
            application.start();
        } catch (Exception e) {
            application.logger.log(Level.SEVERE, "Application start error", e);
            System.exit(0);
        }
        System.exit(0);
    }

    Set<String> findSncpGroups(Transport sameGroupTransport, Collection<Transport> diffGroupTransports) {
        Set<String> gs = new HashSet<>();
        if (sameGroupTransport != null) gs.add(sameGroupTransport.getName());
        if (diffGroupTransports != null) {
            for (Transport t : diffGroupTransports) {
                gs.add(t.getName());
            }
        }
        return gs;
    }

    NodeSncpServer findNodeSncpServer(final InetSocketAddress sncpAddr) {
        for (NodeServer node : servers) {
            if (node.isSNCP() && sncpAddr.equals(node.getSncpAddress())) {
                return (NodeSncpServer) node;
            }
        }
        return null;
    }

    GroupInfo findGroupInfo(String group) {
        if (group == null) return null;
        return globalGroups.get(group);
    }

    private void shutdown() throws Exception {
        servers.stream().forEach((server) -> {
            try {
                server.shutdown();
            } catch (Exception t) {
                logger.log(Level.WARNING, " shutdown server(" + server.getSocketAddress() + ") error", t);
            } finally {
                serversLatch.countDown();
            }
        });

        for (DataSource source : dataSources) {
            try {
                source.getClass().getMethod("close").invoke(source);
            } catch (Exception e) {
                logger.log(Level.FINER, "close DataSource erroneous", e);
            }
        }
        for (CacheSource source : cacheSources) {
            try {
                source.getClass().getMethod("close").invoke(source);
            } catch (Exception e) {
                logger.log(Level.FINER, "close CacheSource erroneous", e);
            }
        }
        if (this.transportChannelGroup != null) {
            try {
                this.transportChannelGroup.shutdownNow();
            } catch (Exception e) {
                logger.log(Level.FINER, "close transportChannelGroup erroneous", e);
            }
        }
    }

    private static AnyValue load(final InputStream in0) {
        final DefaultAnyValue any = new DefaultAnyValue();
        try (final InputStream in = in0) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            Element root = doc.getDocumentElement();
            load(any, root);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return any;
    }

    private static void load(final DefaultAnyValue any, final Node root) {
        final String home = System.getProperty(RESNAME_APP_HOME);
        NamedNodeMap nodes = root.getAttributes();
        if (nodes == null) return;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            any.addValue(node.getNodeName(), node.getNodeValue().replace("${APP_HOME}", home));
        }
        NodeList children = root.getChildNodes();
        if (children == null) return;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            DefaultAnyValue sub = new DefaultAnyValue();
            load(sub, node);
            any.addValue(node.getNodeName(), sub);
        }

    }
}
