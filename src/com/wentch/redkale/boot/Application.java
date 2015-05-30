/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import static com.wentch.redkale.boot.NodeServer.LINE_SEPARATOR;
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
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * 编译时需要加入: -XDignore.symbol.file=true
 * <p>
 * 进程启动类，程序启动后读取application.xml,进行classpath扫描动态加载Service与Servlet， 再进行Service、Servlet与其他资源之间的依赖注入。
 *
 * @author zhangjx
 */
public final class Application {

    //进程启动的时间， 类型： long
    public static final String RESNAME_TIME = "APP_TIME";

    //本地进程的根目录， 类型：String
    public static final String RESNAME_HOME = "APP_HOME";

    //本地节点的名称， 类型：String
    public static final String RESNAME_NODE = "APP_NODE";

    //本地节点的所属组， 类型：String、Map<String, Set<String>>、Map<String, List<SimpleEntry<String, InetSocketAddress[]>>>
    public static final String RESNAME_GROUP = "APP_GROUP";

    //本地节点的所属组所有节点名， 类型：Set<String> 、List<SimpleEntry<String, InetSocketAddress[]>>包含自身节点名
    public static final String RESNAME_INGROUP = "APP_INGROUP";

    //除本地节点的所属组外其他所有组的所有节点名， 类型：Map<String, Set<String>>、Map<String, List<SimpleEntry<String, InetSocketAddress[]>>>
    public static final String RESNAME_OUTGROUP = "APP_OUTGROUP";

    //本地节点的IP地址， 类型：InetAddress、String
    public static final String RESNAME_ADDR = "APP_ADDR";

    //application.xml 文件中resources节点的内容， 类型： AnyValue
    public static final String RESNAME_GRES = "APP_GRES";

    protected final ResourceFactory factory = ResourceFactory.root();

    protected final WatchFactory watch = WatchFactory.root();

    protected final HashMap<Class, ServiceEntry> localServices = new HashMap<>();

    protected final ArrayList<ServiceEntry> remoteServices = new ArrayList<>();

    protected boolean serviceInited = false;

    protected final InetAddress localAddress = Utility.localInetAddress();

    protected String nodeGroup = "";

    protected String nodeName = "";

    //--------------------------------------------------------------------------------------------
    private File home;

    private final Logger logger;

    private final AnyValue config;

    private final List<NodeServer> servers = new CopyOnWriteArrayList<>();

    private final List<DataSource> sources = new CopyOnWriteArrayList<>();

    private final long startTime = System.currentTimeMillis();

    private CountDownLatch serverscdl;

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
        final File logconf = new File(root, "conf/logging.properties");
        this.nodeName = config.getValue("node", "");
        this.nodeGroup = config.getValue("group", "");
        this.factory.register(RESNAME_NODE, this.nodeName);
        this.factory.register(RESNAME_GROUP, this.nodeGroup);
        System.setProperty(RESNAME_NODE, this.nodeName);
        System.setProperty(RESNAME_GROUP, this.nodeGroup);

        this.factory.register(RESNAME_ADDR, this.localAddress.getHostAddress());
        this.factory.register(RESNAME_ADDR, InetAddress.class, this.localAddress);
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
        logger = Logger.getLogger(this.getClass().getSimpleName());
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

        final File root = new File(System.getProperty(RESNAME_HOME));
        this.factory.register(BsonFactory.root());
        this.factory.register(JsonFactory.root());
        this.factory.register(BsonFactory.root().getConvert());
        this.factory.register(JsonFactory.root().getConvert());
        File persist = new File(root, "conf/persistence.xml");
        if (persist.isFile()) System.setProperty(DataJDBCSource.DATASOURCE_CONFPATH, persist.getCanonicalPath());
        logger.log(Level.INFO, RESNAME_HOME + "=" + root.getCanonicalPath() + "\r\n" + RESNAME_ADDR + "=" + this.localAddress.getHostAddress());
        String lib = config.getValue("lib", "").trim().replace("${APP_HOME}", root.getCanonicalPath());
        lib = lib.isEmpty() ? (root.getCanonicalPath() + "/conf") : (lib + ";" + root.getCanonicalPath() + "/conf");
        Server.loadLib(logger, lib);
        initLogging();
        InetAddress addr = Utility.localInetAddress();
        if (addr != null) {
            byte[] bs = addr.getAddress();
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
        initResources();
    }

    public static void singleton(Service service) throws Exception {
        final Application application = Application.create();
        application.init();
        application.factory.register(service);
        new NodeHttpServer(application, new CountDownLatch(1), null).prepare(application.config);
        application.factory.inject(service);
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
        application.start();
        System.exit(0);
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
                                application.serverscdl.countDown();
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
        this.serverscdl = new CountDownLatch(entrys.length + 1);
        CountDownLatch timecd = new CountDownLatch(entrys.length);
        runServers(timecd, entrys);
        timecd.await();
        logger.info(this.getClass().getSimpleName() + " started in " + (System.currentTimeMillis() - startTime) + " ms");
        this.serverscdl.await();
    }

    @SuppressWarnings("unchecked")
    private void runServers(CountDownLatch timecd, final AnyValue[] entrys) throws Exception {
        CountDownLatch servicecdl = new CountDownLatch(entrys.length);
        for (final AnyValue entry : entrys) {
            new Thread() {
                {
                    String host = entry.getValue("host", "").replace("0.0.0.0", "");
                    setName(entry.getValue("protocol", "Server").toUpperCase() + "-" + host + ":" + entry.getIntValue("port", 80) + "-Thread");
                    this.setDaemon(true);
                }

                @Override
                public void run() {
                    try {
                        //Thread ctd = Thread.currentThread();
                        //ctd.setContextClassLoader(new URLClassLoader(new URL[0], ctd.getContextClassLoader()));
                        NodeServer server = null;
                        if ("HTTP".equalsIgnoreCase(entry.getValue("protocol", ""))) {
                            server = new NodeHttpServer(Application.this, servicecdl, new HttpServer(startTime, watch));
                        } else if ("SNCP".equalsIgnoreCase(entry.getValue("protocol", ""))) {
                            server = new NodeSncpServer(Application.this, servicecdl, new SncpServer(startTime, watch));
                        }
                        if (server == null) {
                            logger.log(Level.SEVERE, "Not found Server Class for protocol({0})", entry.getValue("protocol"));
                            System.exit(0);
                        }
                        servers.add(server);

                        server.prepare(entry); //必须在init之前
                        server.init(entry);
                        server.start();
                        timecd.countDown();
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, entry + " runServers error", ex);
                        serverscdl.countDown();
                    }
                }
            }.start();
        }
    }

    private void initResources() throws Exception {

        this.factory.add(DataSource.class, (ResourceFactory rf, final Object src, Field field) -> {
            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if (src.getClass().getAnnotation(RemoteOn.class) != null) return;
                DataSource source = DataSourceFactory.create(rs.name());
                sources.add(source);
                rf.register(rs.name(), DataSource.class, source);
                field.set(src, source);
                rf.inject(source); // 给 "datasource.nodeid" 赋值
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject error", e);
            }
        });

        this.factory.add(DataSQLListener.class, (ResourceFactory rf, Object src, Field field) -> {

            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if (src.getClass().getAnnotation(RemoteOn.class) != null) return;
                DataSQLListener service = rf.findChild("", DataSQLListener.class);
                if (service != null) {
                    field.set(src, service);
                    rf.inject(service);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, DataSQLListener.class.getSimpleName() + " inject error", e);
            }
        }
        );

        this.factory.add(DataCacheListener.class, (ResourceFactory rf, Object src, Field field) -> {

            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if (src.getClass().getAnnotation(RemoteOn.class) != null) return;
                DataCacheListener service = rf.findChild("", DataCacheListener.class);
                if (service != null) {
                    field.set(src, service);
                    rf.inject(service);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, DataCacheListener.class.getSimpleName() + " inject error", e);
            }
        }
        );
        //-------------------------------------------------------------------------
        final AnyValue resources = config.getAnyValue("resources");
        if (resources != null) {
            //------------------------------------------------------------------------
            final String host = this.localAddress.getHostAddress();
            final Map<String, Set<String>> groups = new HashMap<>();
            final Map<String, List<SimpleEntry<String, InetSocketAddress[]>>> groups2 = new HashMap<>();

            for (AnyValue conf : resources.getAnyValues("remote")) {
                final String name = conf.getValue("name");
                final String group = conf.getValue("group", "");
                if (name == null) throw new RuntimeException("remote name is null");
                String protocol = conf.getValue("protocol", "UDP").toUpperCase();
                if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                    throw new RuntimeException("Not supported Transport Protocol " + conf.getValue("protocol"));
                }
                {
                    Set<String> set = groups.get(group);
                    if (set == null) {
                        set = new HashSet<>();
                        groups.put(group, set);
                    }
                    set.add(name);
                }
                AnyValue[] addrs = conf.getAnyValues("address");
                InetSocketAddress[] addresses = new InetSocketAddress[addrs.length];
                int i = -1;
                for (AnyValue addr : addrs) {
                    addresses[++i] = new InetSocketAddress(addr.getValue("addr"), addr.getIntValue("port"));
                }
                if (addresses.length < 1) throw new RuntimeException("Transport(" + name + ") have no address ");
                {
                    List<SimpleEntry<String, InetSocketAddress[]>> list = groups2.get(group);
                    if (list == null) {
                        list = new ArrayList<>();
                        groups2.put(group, list);
                    }
                    list.add(new SimpleEntry<>(name, addresses));
                }
                Transport transport = new Transport(name, protocol, watch, 100, addresses);
                factory.register(name, Transport.class, transport);
                if (this.nodeName.isEmpty() && host.equals(addrs[0].getValue("addr"))) {
                    this.nodeName = name;
                    this.nodeGroup = group;
                    this.factory.register(RESNAME_NODE, this.nodeName);
                    this.factory.register(RESNAME_GROUP, this.nodeGroup);
                    System.setProperty(RESNAME_NODE, this.nodeName);
                    System.setProperty(RESNAME_GROUP, this.nodeGroup);
                }
            }

            this.factory.register(RESNAME_GROUP, new TypeToken<Map<String, Set<String>>>() {
            }.getType(), groups);
            this.factory.register(RESNAME_GROUP, new TypeToken<Map<String, List<SimpleEntry<String, InetSocketAddress[]>>>>() {
            }.getType(), groups2);

            final Map<String, List<SimpleEntry<String, InetSocketAddress[]>>> outgroups2 = new HashMap<>();
            final Map<String, Set<String>> outgroups = new HashMap<>();
            groups.entrySet().stream().filter(x -> !x.getKey().equals(nodeName)).forEach(x -> outgroups.put(x.getKey(), x.getValue()));
            groups2.entrySet().stream().filter(x -> !x.getKey().equals(nodeName)).forEach(x -> outgroups2.put(x.getKey(), x.getValue()));

            this.factory.register(RESNAME_OUTGROUP, new TypeToken<Map<String, Set<String>>>() {
            }.getType(), outgroups);
            this.factory.register(RESNAME_OUTGROUP, new TypeToken<Map<String, List<SimpleEntry<String, InetSocketAddress[]>>>>() {
            }.getType(), outgroups2);

            Set<String> ingroup = groups.get(this.nodeGroup);
            if (ingroup != null) this.factory.register(RESNAME_INGROUP, new TypeToken<Set<String>>() {
            }.getType(), ingroup);
            List<SimpleEntry<String, InetSocketAddress[]>> inengroup = groups2.get(this.nodeGroup);
            if (inengroup != null) this.factory.register(RESNAME_INGROUP, new TypeToken<List<SimpleEntry<String, InetSocketAddress[]>>>() {
            }.getType(), inengroup);
        }

        //------------------------------------------------------------------------
        logger.info(RESNAME_NODE + "=" + this.nodeName + "; " + RESNAME_GROUP + "=" + this.nodeGroup);
        logger.info("datasource.nodeid=" + this.factory.find("property.datasource.nodeid", String.class));

    }

    private void shutdown() throws Exception {
        servers.stream().forEach((server) -> {
            try {
                server.shutdown();
            } catch (Exception t) {
                logger.log(Level.WARNING, " shutdown server(" + server.getSocketAddress() + ") error", t);
            } finally {
                serverscdl.countDown();
            }
        });
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        localServices.entrySet().stream().forEach(k -> {
            Class x = k.getKey();
            ServiceEntry y = k.getValue();
            long s = System.currentTimeMillis();
            y.getService().destroy(y.getServiceConf());
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append("LocalService(").append(y.getNames()).append("|").append(y.getServiceClass()).append(") destroy ").append(e).append("ms").append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
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
