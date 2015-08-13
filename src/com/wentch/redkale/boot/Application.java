/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.http.*;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.source.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import com.wentch.redkale.watch.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * 编译时需要加入: -XDignore.symbol.file=true
 * <p>
 * 进程启动类，程序启动后读取application.xml,进行classpath扫描动态加载Service与Servlet
 * 优先加载所有SNCP协议的服务， 再加载其他协议服务，
 * 最后进行Service、Servlet与其他资源之间的依赖注入。
 * 
 *
 * @author zhangjx
 */
public final class Application {

    //当前进程启动的时间， 类型： long
    public static final String RESNAME_TIME = "APP_TIME";

    //当前进程的根目录， 类型：String
    public static final String RESNAME_HOME = "APP_HOME";

    //当前进程节点的name， 类型：String
    public static final String RESNAME_NODE = "APP_NODE";

    //当前进程节点的IP地址， 类型：InetAddress、String
    public static final String RESNAME_ADDR = "APP_ADDR";

    //application.xml 文件中resources节点的内容， 类型： AnyValue
    public static final String RESNAME_GRES = "APP_GRES";

    //当前SNCP Server所属的组  类型: String
    public static final String RESNAME_SNCP_GROUP = "SNCP_GROUP";

    //当前Service所属的组  类型: Set<String>、String[]
    public static final String RESNAME_SNCP_GROUPS = Sncp.RESNAME_SNCP_GROUPS; //SNCP_GROUPS

    //当前SNCP Server的IP地址+端口 类型: SocketAddress、InetSocketAddress、String
    public static final String RESNAME_SNCP_NODE = "SNCP_NODE";

    //当前SNCP Server的IP地址+端口集合 类型: Map<InetSocketAddress, String>、HashMap<InetSocketAddress, String> 
    public static final String RESNAME_SNCP_NODES = "SNCP_NODES"; 

    protected final ResourceFactory factory = ResourceFactory.root();

    protected final WatchFactory watch = WatchFactory.root();

    protected final Map<InetSocketAddress, String> globalNodes = new HashMap<>();

    private final Map<String, Set<InetSocketAddress>> globalGroups = new HashMap<>();

    protected final List<Transport> transports = new ArrayList<>();

    protected final InetAddress localAddress;

    protected Class<? extends DataCacheListener> dataCacheListenerClass = DataCacheListenerService.class;

    protected Class<? extends WebSocketNode> webSocketNodeClass = WebSocketNodeService.class;

    protected final List<DataSource> sources = new CopyOnWriteArrayList<>();

    protected final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    //--------------------------------------------------------------------------------------------
    private File home;

    private final Logger logger;

    private final AnyValue config;

    private final long startTime = System.currentTimeMillis();

    private final CountDownLatch serversLatch;

    private Application(final AnyValue config) {
        this.config = config;

        final File root = new File(System.getProperty(RESNAME_HOME));
        this.factory.register(RESNAME_TIME, long.class, this.startTime);
        this.factory.register(RESNAME_HOME, Path.class, root.toPath());
        this.factory.register(RESNAME_HOME, File.class, root);
        try {
            this.factory.register(RESNAME_HOME, root.getCanonicalPath());
            this.home = root.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String localaddr = config.getValue("address", "").trim();
        this.localAddress = localaddr.isEmpty() ? Utility.localInetAddress() : new InetSocketAddress(localaddr, 0).getAddress();
        Application.this.factory.register(RESNAME_ADDR, Application.this.localAddress.getHostAddress());
        Application.this.factory.register(RESNAME_ADDR, InetAddress.class, Application.this.localAddress);
        {
            StringBuilder sb = new StringBuilder();
            byte[] bs = this.localAddress.getAddress();
            int v1 = bs[bs.length - 2] & 0xff;
            int v2 = bs[bs.length - 1] & 0xff;
            if (v1 <= 0xf) sb.append('0');
            sb.append(Integer.toHexString(v1));
            if (v2 <= 0xf) sb.append('0');
            sb.append(Integer.toHexString(v2));
            String node = sb.toString();
            Application.this.factory.register(RESNAME_NODE, node);
            System.setProperty(RESNAME_NODE, node);
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
                    String handlers = properties.getProperty("handlers");
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
    }

    public File getHome() {
        return home;
    }

    private void initLogging() {

    }

    public void init() throws Exception {
        System.setProperty("convert.bson.pool.size", "128");
        System.setProperty("convert.json.pool.size", "128");
        System.setProperty("convert.bson.writer.buffer.defsize", "4096");
        System.setProperty("convert.json.writer.buffer.defsize", "4096");

        File persist = new File(this.home, "conf/persistence.xml");
        final String homepath = this.home.getCanonicalPath();
        if (persist.isFile()) System.setProperty(DataDefaultSource.DATASOURCE_CONFPATH, persist.getCanonicalPath());
        logger.log(Level.INFO, RESNAME_HOME + "=" + homepath + "\r\n" + RESNAME_ADDR + "=" + this.localAddress.getHostAddress());
        String lib = config.getValue("lib", "").trim().replace("${APP_HOME}", homepath);
        lib = lib.isEmpty() ? (homepath + "/conf") : (lib + ";" + homepath + "/conf");
        Server.loadLib(logger, lib);
        initLogging();
        if (this.localAddress != null) {
            byte[] bs = this.localAddress.getAddress();
            int v = (0xff & bs[bs.length - 2]) % 10 * 100 + (0xff & bs[bs.length - 1]);
            this.factory.register("property.datasource.nodeid", "" + v);
        }
        //------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            factory.register(RESNAME_GRES, AnyValue.class, resources);
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
                            ps.forEach((x, y) -> factory.register("property." + x, y));
                        }
                    }
                }
                for (AnyValue prop : properties.getAnyValues("property")) {
                    String name = prop.getValue("name");
                    String value = prop.getValue("value");
                    if (name == null || value == null) continue;
                    if (name.startsWith("system.property.")) {
                        System.setProperty(name.substring("system.property.".length()), value);
                    } else {
                        factory.register("property." + name, value);
                    }
                }
            }
        }
        this.factory.register(BsonFactory.root());
        this.factory.register(JsonFactory.root());
        this.factory.register(BsonFactory.root().getConvert());
        this.factory.register(JsonFactory.root().getConvert());
        initResources();
    }

    private void initResources() throws Exception {
        //-------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            //------------------------------------------------------------------------
            AnyValue datacachelistenerConf = resources.getAnyValue("datacachelistener");
            if (datacachelistenerConf != null) {
                String val = datacachelistenerConf.getValue("service", "");
                if (!val.isEmpty()) {
                    if ("none".equalsIgnoreCase(val)) {
                        this.dataCacheListenerClass = null;
                    } else {
                        Class clazz = Class.forName(val);
                        if (!DataCacheListener.class.isAssignableFrom(clazz) || !Service.class.isAssignableFrom(clazz)) {
                            throw new RuntimeException("datacachelistener service (" + val + ") is illegal");
                        }
                        this.dataCacheListenerClass = clazz;
                    }
                }
            }
            //------------------------------------------------------------------------
            AnyValue websocketnodeConf = resources.getAnyValue("websocketnode");
            if (websocketnodeConf != null) {
                String val = websocketnodeConf.getValue("service", "");
                if (!val.isEmpty()) {
                    if ("none".equalsIgnoreCase(val)) {
                        this.webSocketNodeClass = null;
                    } else {
                        Class clazz = Class.forName(val);
                        if (!WebSocketNode.class.isAssignableFrom(clazz) || !Service.class.isAssignableFrom(clazz)) {
                            throw new RuntimeException("websocketnode service (" + val + ") is illegal");
                        }
                        this.webSocketNodeClass = clazz;
                    }
                }
            }
            //------------------------------------------------------------------------

            for (AnyValue conf : resources.getAnyValues("group")) {
                final String group = conf.getValue("name", "");
                String protocol = conf.getValue("protocol", Sncp.DEFAULT_PROTOCOL).toUpperCase();
                if (!"TCP".equalsIgnoreCase(protocol) && "UDP".equalsIgnoreCase(protocol)) {
                    throw new RuntimeException("Not supported Transport Protocol " + conf.getValue("protocol"));
                }
                Set<InetSocketAddress> addrs = globalGroups.get(group);
                if (addrs == null) {
                    addrs = new LinkedHashSet<>();
                    globalGroups.put(group, addrs);
                }
                for (AnyValue node : conf.getAnyValues("node")) {
                    final InetSocketAddress addr = new InetSocketAddress(node.getValue("addr"), node.getIntValue("port"));
                    addrs.add(addr);
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
                    channel.bind(new InetSocketAddress(config.getValue("host", "127.0.0.1"), config.getIntValue("port")));
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
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.INFO, "Control fail", e);
                    System.exit(1);
                }
            }
        }.start();
    }

    private void sendShutDown() throws Exception {
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(config.getValue("host", "127.0.0.1"), config.getIntValue("port")));
        ByteBuffer buffer = ByteBuffer.allocate(128);
        buffer.put("SHUTDOWN".getBytes());
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
        if (!sncps.isEmpty() && globalNodes.isEmpty()) throw new RuntimeException("found SNCP Server node bug not found <group> node info.");

        factory.register(RESNAME_SNCP_NODES, new TypeToken<Map<InetSocketAddress, String>>() {
        }.getType(), globalNodes);
        factory.register(RESNAME_SNCP_NODES, new TypeToken<HashMap<InetSocketAddress, String>>() {
        }.getType(), globalNodes);

        factory.register(RESNAME_SNCP_NODES, new TypeToken<Map<String, Set<InetSocketAddress>>>() {
        }.getType(), globalGroups);
        factory.register(RESNAME_SNCP_NODES, new TypeToken<HashMap<String, Set<InetSocketAddress>>>() {
        }.getType(), globalGroups);

        runServers(timecd, sncps);  //必须确保sncp都启动后再启动其他协议
        runServers(timecd, others);
        timecd.await();
        logger.info(this.getClass().getSimpleName() + " started in " + (System.currentTimeMillis() - startTime) + " ms");
        this.serversLatch.await();
    }

    @SuppressWarnings("unchecked")
    private void runServers(CountDownLatch timecd, final List<AnyValue> serconfs) throws Exception {
        CountDownLatch servicecdl = new CountDownLatch(serconfs.size());
        CountDownLatch sercdl = new CountDownLatch(serconfs.size());
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
                        String protocol = serconf.getValue("protocol", "");
                        String subprotocol = Sncp.DEFAULT_PROTOCOL;
                        int pos = protocol.indexOf('.');
                        if (pos > 0) {
                            subprotocol = protocol.substring(pos + 1);
                            protocol = protocol.substring(0, pos);
                        }
                        NodeServer server = null;
                        if ("SNCP".equalsIgnoreCase(protocol)) {
                            server = new NodeSncpServer(Application.this, servicecdl, new SncpServer(startTime, subprotocol, watch));
                        } else if ("HTTP".equalsIgnoreCase(protocol)) {
                            server = new NodeHttpServer(Application.this, servicecdl, new HttpServer(startTime, watch));
                        }
                        if (server == null) {
                            logger.log(Level.SEVERE, "Not found Server Class for protocol({0})", serconf.getValue("protocol"));
                            System.exit(0);
                        }
                        servers.add(server);
                        server.init(serconf);
                        server.start();
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
        return singleton(serviceClass, false);
    }

    public static <T extends Service> T singleton(Class<T> serviceClass, boolean remote) throws Exception {
        final Application application = Application.create();
        T service = remote ? Sncp.createRemoteService("", serviceClass, null, null) : Sncp.createLocalService("", serviceClass, null, new LinkedHashSet<>(), null, null);
        application.init();
        application.factory.register(service);
        new NodeSncpServer(application, new CountDownLatch(1), null).init(application.config);
        application.factory.inject(service);
        return service;
    }

    private static Application create() throws IOException {
        final String home = new File(System.getProperty(RESNAME_HOME, "")).getCanonicalPath();
        System.setProperty(RESNAME_HOME, home);
        File appfile = new File(home, "conf/application.xml");
        //System.setProperty(DataConnection.PERSIST_FILEPATH, appfile.getCanonicalPath());
        return new Application(load(new FileInputStream(appfile)));
    }

    public static void main(String[] args) throws Exception {
        //运行主程序
        final Application application = Application.create();
        if (System.getProperty("SHUTDOWN") != null) {
            application.sendShutDown();
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

    Set<InetSocketAddress> findGlobalGroup(String group) {
        if (group == null) return null;
        Set<InetSocketAddress> set = globalGroups.get(group);
        return set == null ? null : new LinkedHashSet<>(set);
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
        for (DataSource source : sources) {
            try {
                source.getClass().getMethod("close").invoke(source);
            } catch (Exception e) {
                logger.log(Level.FINER, "close DataSource erroneous", e);
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
        final String home = System.getProperty(RESNAME_HOME);
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
