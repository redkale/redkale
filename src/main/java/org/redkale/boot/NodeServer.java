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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.annotation.Command;
import static org.redkale.boot.Application.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cluster.spi.ClusterAgent;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.Filter;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * Server节点的初始化配置类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public abstract class NodeServer {

    // INFO日志的换行符
    public static final String LINE_SEPARATOR = "\r\n";

    // 日志输出对象
    protected final Logger logger;

    // 进程主类
    protected final Application application;

    // 依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    // 当前Server对象
    protected final Server server;

    // ClassLoader
    protected RedkaleClassLoader serverClassLoader;

    protected final Thread serverThread;

    // server节点的配置
    protected AnyValue serverConf;

    protected final String threadName;

    // 加载server节点后的拦截器
    protected NodeInterceptor interceptor;

    // 本地模式的Service对象集合
    protected final Set<Service> localServices = new LinkedHashSet<>();

    // 远程模式的Service对象集合
    protected final Set<Service> remoteServices = new LinkedHashSet<>();

    // 需要转换成Servlet的Service对象集合, Component的Service不在其内
    protected final Set<Service> servletServices = new LinkedHashSet<>();

    // 存在SncpServlet、RestServlet
    protected final Map<Service, Servlet> dynServletMap = new LinkedHashMap<>();

    // MessageAgent对象集合
    protected final Map<String, MessageAgent> messageAgents = new HashMap<>();

    // 需要远程模式Service的MessageAgent对象集合
    protected final Map<String, MessageAgent> sncpRemoteAgents = new HashMap<>();

    // 当前Server的SNCP协议的组
    protected String sncpGroup = null;

    // 当前Server的SNCP服务Client
    protected SncpClient sncpClient;

    // SncpClient的AsyncGroup
    private AsyncIOGroup sncpAsyncGroup;

    // SNCP服务的地址， 非SNCP为null
    private InetSocketAddress sncpAddress;

    private volatile int maxTypeLength = 0;

    private volatile int maxNameLength = 0;

    CountDownLatch serviceCdl;

    public NodeServer(Application application, Server server) {
        this.threadName = Thread.currentThread().getName();
        this.application = application;
        this.server = server;
        this.resourceFactory = server.getResourceFactory();
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
        if (application.isCompileMode()
                || application.getServerClassLoader() instanceof RedkaleClassLoader.RedkaleCacheClassLoader) {
            this.serverClassLoader = application.getServerClassLoader();
        } else {
            this.serverClassLoader = RedkaleClassLoader.getRedkaleClassLoader(application.getServerClassLoader());
        }
        Thread.currentThread().setContextClassLoader(this.serverClassLoader);
        this.serverThread = Thread.currentThread();
        this.server.setServerClassLoader(serverClassLoader);
    }

    public static <T extends NodeServer> NodeServer create(Class<T> clazz, Application application, AnyValue serconf) {
        try {
            RedkaleClassLoader.putReflectionDeclaredConstructors(
                    clazz, clazz.getName(), Application.class, AnyValue.class);
            return clazz.getDeclaredConstructor(Application.class, AnyValue.class)
                    .newInstance(application, serconf);
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
    }

    public void init(AnyValue config) throws Exception {
        this.serverConf = config == null ? AnyValue.create() : config;
        if (isSNCP()) { // SNCP协议
            String host = this.serverConf
                    .getValue("host", isWATCH() ? "127.0.0.1" : "0.0.0.0")
                    .replace("0.0.0.0", "");
            if (host.isEmpty()) {
                host = application.localAddress.getAddress().getHostAddress();
            }
            this.sncpAddress = new InetSocketAddress(host, this.serverConf.getIntValue("port"));
            this.sncpGroup = application.getSncpRpcGroups().getGroup(this.sncpAddress);
            // 单向SNCP服务不需要对等group
            // if (this.sncpGroup == null) {
            //    throw new RedkaleException("Server (" + String.valueOf(config).replaceAll("\\s+", " ") + ") not found
            // <group> info");
            // }
        }
        // 单点服务不会有 sncpAddress、sncpGroup
        if (this.sncpAddress != null) {
            this.resourceFactory.register(RESNAME_SNCP_ADDRESS, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDRESS, SocketAddress.class, this.sncpAddress);
            this.resourceFactory.register(RESNAME_SNCP_ADDRESS, InetSocketAddress.class, this.sncpAddress);
            this.resourceFactory.register(
                    RESNAME_SNCP_ADDRESS,
                    String.class,
                    this.sncpAddress.getHostString() + ":" + this.sncpAddress.getPort());
        }
        if (this.sncpGroup != null) {
            this.resourceFactory.register(RESNAME_SNCP_GROUP, this.sncpGroup);
        }
        {
            // 设置root文件夹
            String webroot = this.serverConf.getValue("root", "root");
            File myroot = new File(webroot);
            if (!webroot.contains(":") && !webroot.startsWith("/")) {
                myroot = new File(System.getProperty(Application.RESNAME_APP_HOME), webroot);
            }

            resourceFactory.register(Server.RESNAME_SERVER_ROOT, String.class, myroot.getCanonicalPath());
            resourceFactory.register(Server.RESNAME_SERVER_ROOT, File.class, myroot.getCanonicalFile());
            resourceFactory.register(Server.RESNAME_SERVER_ROOT, Path.class, myroot.toPath());

            // 加入指定的classpath
            Server.loadLib(serverClassLoader, logger, this.serverConf.getValue("lib", ""));
            this.serverThread.setContextClassLoader(this.serverClassLoader);
        }
        // 必须要进行初始化， 构建Service时需要使用Context中的ExecutorService
        server.init(this.serverConf);
        if (this.sncpAddress != null) { // 初始化SncpClient
            this.sncpAsyncGroup = new AsyncIOGroup(
                            "Redkale-SncpClient-IOThread-%s",
                            application.getWorkExecutor(), server.getBufferCapacity(), server.getBufferPoolSize())
                    .skipClose(true);
            this.sncpClient = new SncpClient(
                    server.getName(),
                    this.sncpAsyncGroup,
                    application.getNodeid(),
                    this.sncpAddress,
                    new ClientAddress(this.sncpAddress),
                    server.getNetprotocol(),
                    Utility.cpus(),
                    1000);
        }

        // --------------------- 注册 Local AutoLoad(false) Service ---------------------
        resourceFactory.register(new NodeAutoServiceLoader(this));
        // ----------------------------- 注册 WebSocketNode -----------------------------
        resourceFactory.register(new NodeWebSocketNodeLoader(this));

        // 过滤类
        String interceptorClass = this.serverConf.getValue("interceptor", "");
        if (!interceptorClass.isEmpty()) {
            Class clazz = serverClassLoader.loadClass(interceptorClass);
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            this.interceptor = (NodeInterceptor) clazz.getDeclaredConstructor().newInstance();
        }

        ClassFilter<Service> serviceFilter = createServiceClassFilter();
        if (application.isSingletonMode()) { // singleton模式下只加载指定的Service
            final String ssc = System.getProperty("redkale.singleton.serviceclass");
            final String extssc = System.getProperty("redkale.singleton.extserviceclasses");
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
        List<ClassFilter> otherFilters = createOtherClassFilters();
        List<ClassFilter> filters = new ArrayList<>();
        if (serviceFilter != null) {
            filters.add(serviceFilter);
        }
        if (filterFilter != null) {
            filters.add(filterFilter);
        }
        if (servletFilter != null) {
            filters.add(servletFilter);
        }
        if (otherFilters != null) {
            filters.addAll(otherFilters);
        }
        long s = System.currentTimeMillis();
        application.loadClassByFilters(filters.toArray(new ClassFilter[filters.size()]));
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter); // 必须在servlet之前
        loadOthers(otherFilters);
        if (!application.isSingletonMode()) { // 非singleton模式下才加载Filter、Servlet
            loadFilter(filterFilter);
            loadServlet(servletFilter);
            postLoadServlets();
        }
        if (this.interceptor != null) {
            this.resourceFactory.inject(this.interceptor);
        }
    }

    protected void loadOthers(List<ClassFilter> otherFilters) throws Exception {}

    protected abstract void loadFilter(ClassFilter<? extends Filter> filterFilter) throws Exception;

    protected abstract void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception;

    @SuppressWarnings("unchecked")
    protected void loadService(ClassFilter<? extends Service> serviceFilter) throws Exception {
        Objects.requireNonNull(serviceFilter);
        final long starts = System.currentTimeMillis();
        final Set<FilterEntry<? extends Service>> entrys = (Set) serviceFilter.getAllFilterEntrys();
        final SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
        final AtomicInteger serviceCount = new AtomicInteger();
        for (FilterEntry<? extends Service> entry : entrys) { // service实现类
            final Class<? extends Service> serviceImplClass = entry.getType();
            if (Modifier.isFinal(serviceImplClass.getModifiers())) {
                continue; // 修饰final的类跳过
            }
            if (!Modifier.isPublic(serviceImplClass.getModifiers())) {
                continue;
            }
            if (Sncp.isSncpDyn(serviceImplClass)) {
                continue; // 动态生成的跳过
            }
            if (entry.isExpect()) {
                if (Modifier.isAbstract(serviceImplClass.getModifiers())) {
                    continue; // 修饰abstract的类跳过
                }
                if (DataSource.class.isAssignableFrom(serviceImplClass)) {
                    continue;
                }
                if (CacheSource.class.isAssignableFrom(serviceImplClass)) {
                    continue;
                }
            }
            if (entry.getName().contains(Resource.PARENT_NAME)) {
                throw new RedkaleException(
                        "<name> value cannot contains '" + Resource.PARENT_NAME + "' in " + entry.getProperty());
            }
            if (!entry.isEmptyGroup() && !entry.isRemote() && !rpcGroups.containsGroup(entry.getGroup())) {
                throw new RedkaleException("Not found group(" + entry.getGroup() + ")");
            }
            Service oldOther = resourceFactory.find(entry.getName(), serviceImplClass);
            if (oldOther != null) { // Server加载Service时需要判断是否已在其他协议服务中加载
                if (!Sncp.isRemote(oldOther)) {
                    if (!Sncp.isComponent(oldOther)) {
                        servletServices.add(oldOther);
                    }
                }
                continue;
            }
            boolean isLocalGroup0 = rpcGroups.isLocalGroup(this.sncpGroup, this.sncpAddress, entry);
            final String group = isLocalGroup0 ? null : entry.getGroup();
            final boolean localMode = serviceImplClass.getAnnotation(Local.class) != null || isLocalGroup0; // 本地模式
            if ((localMode || Sncp.isComponent(serviceImplClass)) && Utility.isAbstractOrInterface(serviceImplClass)) {
                continue; // 本地模式或Component不能实例化接口和抽象类的Service类
            }
            if (entry.isExpect()) {
                Class t = ResourceFactory.getResourceType(entry.getType());
                if (resourceFactory.findResourceTypeLoader(t) == null) {
                    resourceFactory.register(new NodeExpectServiceLoader(
                            this, t, serviceImplClass, serviceCount, rpcGroups, entry, group, localMode));
                }
            } else {
                NodeExpectServiceLoader resourceLoader = new NodeExpectServiceLoader(
                        this, serviceImplClass, serviceImplClass, serviceCount, rpcGroups, entry, group, localMode);
                resourceLoader.load(resourceFactory, null, null, entry.getName(), null, false);
            }
        }
        long et = System.currentTimeMillis();
        if (serviceCdl != null) {
            serviceCdl.countDown();
            serviceCdl.await();
        }
        logger.info(this.getClass().getSimpleName() + " construct services in " + (et - starts) + " ms and await "
                + (System.currentTimeMillis() - et) + " ms");

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        // ---------------- inject ----------------
        new ArrayList<>(localServices).forEach(y -> {
            resourceFactory.inject(Sncp.getResourceName(y), y, NodeServer.this);
        });
        // 远程模式不可inject， 里面存在@Resource.required=true依赖
        //        new ArrayList<>(remoteServices).forEach(y -> {
        //            resourceFactory.inject(Sncp.getResourceName(y), y, NodeServer.this);
        //            calcMaxLength(y);
        //        });

        if (sb != null) {
            remoteServices.forEach(y -> {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength))
                        .append(" load and inject")
                        .append(LINE_SEPARATOR);
            });
        }
        if (isSNCP() && !sncpRemoteAgents.isEmpty()) {
            sncpRemoteAgents.values().forEach(agent -> {
                // mqAgent.putSncpResp((NodeSncpServer) this);
                //  mqAgent.startSncpRespConsumer();
            });
        }
        // ----------------- init -----------------
        List<Service> swlist = new ArrayList<>(localServices);
        swlist.forEach(y -> calcMaxLength(y));
        swlist.sort((o1, o2) -> {
            Priority p1 = o1.getClass().getAnnotation(Priority.class);
            Priority p2 = o2.getClass().getAnnotation(Priority.class);
            int v = (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
            if (v != 0) {
                return v;
            }
            int v1 = o1.getClass().getAnnotation(Component.class) == null ? -1 : 1;
            int v2 = o2.getClass().getAnnotation(Component.class) == null ? -1 : 1;
            if (v1 != v2) {
                return v1 - v2;
            }
            int rs = Sncp.getResourceType(o1)
                    .getName()
                    .compareTo(Sncp.getResourceType(o2).getName());
            if (rs == 0) {
                rs = Sncp.getResourceName(o1).compareTo(Sncp.getResourceName(o2));
            }
            return rs;
        });
        localServices.clear();
        localServices.addAll(swlist);
        // this.loadPersistData();
        long preinits = System.currentTimeMillis();
        preInitServices(localServices, remoteServices, servletServices);
        long preinite = System.currentTimeMillis() - preinits;
        final List<String> slist = sb == null ? null : new CopyOnWriteArrayList<>();
        if (application.isCompileMode()) {
            localServices.stream().forEach(y -> {
                String serstr = Sncp.toSimpleString(y, maxNameLength, maxTypeLength);
                if (slist != null) {
                    slist.add(new StringBuilder()
                            .append(serstr)
                            .append(" load")
                            .append(LINE_SEPARATOR)
                            .toString());
                }
            });
        } else {
            localServices.stream().forEach(y -> {
                long s = System.currentTimeMillis();
                application.onServicePreInit(this, y);
                y.init(Sncp.getResourceConf(y));
                application.onServicePostInit(this, y);
                long e = System.currentTimeMillis() - s;
                if (slist != null) {
                    String serstr = Sncp.toSimpleString(y, maxNameLength, maxTypeLength);
                    slist.add(new StringBuilder()
                            .append(serstr)
                            .append(" load and init in ")
                            .append(e < 10 ? "  " : (e < 100 ? " " : ""))
                            .append(e)
                            .append(" ms")
                            .append(LINE_SEPARATOR)
                            .toString());
                }
            });
            localServices.stream().forEach(y -> {
                if (Sncp.isComponent(y)) {
                    long s = System.currentTimeMillis();
                    boolean rs = interceptComponent(y);
                    long e = System.currentTimeMillis() - s;
                    if (rs && slist != null) {
                        String serstr = Sncp.toSimpleString(y, maxNameLength, maxTypeLength);
                        slist.add(new StringBuilder()
                                .append(serstr)
                                .append(" component-start in ")
                                .append(e < 10 ? "  " : (e < 100 ? " " : ""))
                                .append(e)
                                .append(" ms")
                                .append(LINE_SEPARATOR)
                                .toString());
                    }
                }
            });
        }
        if (slist != null && sb != null) {
            List<String> wlist =
                    new ArrayList<>(slist); // 直接使用CopyOnWriteArrayList偶尔会出现莫名的异常(CopyOnWriteArrayList源码1185行)
            for (String s : wlist) {
                sb.append(s);
            }
            sb.append("All ")
                    .append(localServices.size())
                    .append(" Services load in ")
                    .append(System.currentTimeMillis() - starts)
                    .append(" ms");
        }
        if (sb != null && preinite > 10) {
            sb.append(ClusterAgent.class.getSimpleName())
                    .append(" register in ")
                    .append(preinite)
                    .append(" ms" + LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    private void calcMaxLength(Service y) { // 计算toString中的长度
        String n = Sncp.getResourceName(y);
        maxNameLength = Math.max(maxNameLength, n == null ? 0 : n.length());
        maxTypeLength =
                Math.max(maxTypeLength, Sncp.getResourceType(y).getName().length() + 1);
    }

    protected boolean acceptsComponent(Class<? extends Service> serviceImplClass) {
        if (Modifier.isAbstract(serviceImplClass.getModifiers())
                || Modifier.isInterface(serviceImplClass.getModifiers())) {
            return false;
        }
        return true;
    }

    protected boolean interceptComponent(Service service) {
        return false;
    }

    protected MessageAgent getMessageAgent(AnyValue serviceConf) {
        MessageAgent agent = null;
        if (serviceConf != null && serviceConf.getValue("mq") != null) {
            agent = application.getResourceFactory().find(serviceConf.getValue("mq"), MessageAgent.class);
            if (agent != null) {
                messageAgents.put(agent.getName(), agent);
            }
        }
        return agent;
    }

    // Service.init执行之前调用
    protected void preInitServices(
            Set<Service> localServices, Set<Service> remoteServices, Set<Service> servletServices) {
        final ClusterAgent cluster = application.getResourceFactory().find("", ClusterAgent.class);
        if (!application.isCompileMode() && cluster != null) {
            NodeProtocol pros = getClass().getAnnotation(NodeProtocol.class);
            String protocol = pros.value().toUpperCase();
            if (!cluster.containsProtocol(protocol)) {
                return;
            }
            if (!cluster.containsPort(server.getSocketAddress().getPort())) {
                return;
            }
            cluster.register(this, protocol, localServices, remoteServices, servletServices);
        }
    }

    // loadServlet执行之后调用
    protected void postLoadServlets() {}

    // Service.destroy执行之前调用
    protected void preDestroyServices(
            Set<Service> localServices, Set<Service> remoteServices, Set<Service> servletServices) {
        final ClusterAgent cluster = application.getResourceFactory().find("", ClusterAgent.class);
        if (!application.isCompileMode() && cluster != null) { // 服务注销
            NodeProtocol pros = getClass().getAnnotation(NodeProtocol.class);
            String protocol = pros.value().toUpperCase();
            if (cluster.containsProtocol(protocol)
                    && cluster.containsPort(server.getSocketAddress().getPort())) {
                cluster.deregister(this, protocol, localServices, remoteServices, servletServices);
                afterClusterDeregisterOnPreDestroyServices(cluster, protocol);
            }
        }
        if (!application.isCompileMode() && !this.messageAgents.isEmpty()) { // MQ
        }
    }

    protected void afterClusterDeregisterOnPreDestroyServices(ClusterAgent cluster, String protocol) {}

    // Server.start执行之后调用
    protected void postStartServer(
            Set<Service> localServices, Set<Service> remoteServices, Set<Service> servletServices) {}

    protected abstract ClassFilter<Filter> createFilterClassFilter();

    protected abstract ClassFilter<Servlet> createServletClassFilter();

    protected List<ClassFilter> createOtherClassFilters() {
        return null;
    }

    protected ClassFilter<Service> createServiceClassFilter() {
        return createClassFilter(
                this.sncpGroup,
                null,
                Service.class,
                (!isSNCP() && application.watching) ? null : new Class[] {org.redkale.watch.WatchService.class},
                Annotation.class,
                "services",
                "service");
    }

    protected ClassFilter createClassFilter(
            final String localGroup,
            Class<? extends Annotation> ref,
            Class inter,
            Class[] excludeSuperClasses,
            Class<? extends Annotation> ref2,
            String properties,
            String property) {
        ClassFilter cf = new ClassFilter(this.serverClassLoader, ref, inter, excludeSuperClasses, null);
        if (properties == null) {
            cf.setRefused(true);
            return cf;
        }
        if (this.serverConf == null) {
            cf.setRefused(true);
            return cf;
        }
        AnyValue[] proplist = this.serverConf.getAnyValues(properties);
        if (Utility.isEmpty(proplist)) {
            cf.setRefused(true);
            return cf;
        }
        cf = null;
        for (AnyValue list : proplist) {
            AnyValueWriter prop = null;
            String sc = list.getValue("group");
            String mq = list.getValue("mq");
            if (sc != null) {
                sc = sc.trim();
            }
            if (sc == null) {
                sc = localGroup;
            }
            if (sc != null || mq != null) {
                prop = new AnyValueWriter();
                if (sc != null) {
                    prop.addValue("group", sc);
                }
                if (mq != null) {
                    prop.addValue("mq", mq);
                }
            }
            ClassFilter filter = new ClassFilter(this.serverClassLoader, ref, inter, excludeSuperClasses, prop);
            for (AnyValue av : list.getAnyValues(property)) { // <service>、<filter>、<servlet> 节点
                final AnyValue[] items = av.getAnyValues("property");
                if (items.length > 0) { // 存在 <property>节点
                    AnyValueWriter dav = AnyValueWriter.create();
                    final AnyValue.Entry<String>[] strings = av.getStringEntrys();
                    if (strings != null) { // 将<service>、<filter>、<servlet>节点的属性值传给dav
                        for (AnyValue.Entry<String> en : strings) {
                            dav.addValue(en.name, en.getValue());
                        }
                    }
                    final AnyValue.Entry<AnyValue>[] anys = av.getAnyEntrys();
                    if (anys != null) {
                        for (AnyValue.Entry<AnyValue> en : anys) { // 将<service>、<filter>、<servlet>节点的非property属性节点传给dav
                            if (!"property".equals(en.name)) {
                                dav.addValue(en.name, en.getValue());
                            }
                        }
                    }
                    AnyValueWriter ps = AnyValueWriter.create();
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
                filter.setIncludePatterns(includes.replace(',', ';').split(";"));
                filter.setExcludePatterns(excludes.replace(',', ';').split(";"));
            } else if (ref2 == null || ref2 == Annotation.class) { // service如果是autoload=false则不需要加载
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
        if (this.sncpAsyncGroup != null) {
            this.sncpAsyncGroup.start();
        }
        server.start();
        postStartServer(localServices, remoteServices, servletServices);
    }

    public void shutdown() throws IOException {
        if (interceptor != null) {
            interceptor.preShutdown(this);
        }
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        final boolean finest = logger.isLoggable(Level.FINEST);
        preDestroyServices(localServices, remoteServices, servletServices);
        localServices.forEach(y -> {
            long s = System.currentTimeMillis();
            if (finest) {
                logger.finest(Sncp.getResourceType(y) + " is destroying");
            }
            application.onServicePreDestroy(this, y);
            y.destroy(Sncp.getResourceConf(y));
            if (finest) {
                logger.finest(Sncp.getResourceType(y) + " was destroyed");
            }
            application.onServicePostDestroy(this, y);
            long e = System.currentTimeMillis() - s;
            if (e > 2 && sb != null) {
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength))
                        .append(" destroy ")
                        .append(e)
                        .append("ms")
                        .append(LINE_SEPARATOR);
            }
        });
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
        if (this.sncpAsyncGroup != null) {
            long s = System.currentTimeMillis();
            this.sncpAsyncGroup.dispose();
            logger.info("SncpAsyncGroup destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
        if (this.sncpClient != null) {
            long s = System.currentTimeMillis();
            this.sncpClient.close();
            logger.info("SncpClient close in " + (System.currentTimeMillis() - s) + " ms");
        }
        server.shutdown();
    }

    public List<Object> command(String cmd, String[] params) throws IOException {
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        List<Object> results = new ArrayList<>();
        localServices.forEach(y -> {
            Set<Method> methods = new HashSet<>();
            Class loop = y.getClass();
            // do {   public方法不用递归
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
            // } while ((loop = loop.getSuperclass()) != Object.class);
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
                sb.append(Sncp.toSimpleString(y, maxNameLength, maxTypeLength))
                        .append(" command (")
                        .append(cmd)
                        .append(") ")
                        .append(e)
                        .append("ms")
                        .append(LINE_SEPARATOR);
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

    public Set<Service> getServletServices() {
        return new LinkedHashSet<>(servletServices);
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
}
