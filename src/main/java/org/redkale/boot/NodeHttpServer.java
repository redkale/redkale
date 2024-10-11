/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.redkale.annotation.*;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.cluster.spi.ClusterAgent;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.*;
import org.redkale.watch.*;

/**
 * HTTP Server节点的配置Server
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@NodeProtocol("HTTP")
public class NodeHttpServer extends NodeServer {

    // 是否加载REST服务， 为true加载rest节点信息并将所有可REST化的Service生成RestServlet
    protected final boolean rest;

    protected final HttpServer httpServer;

    protected ClassFilter<? extends WebSocket> webSocketFilter;

    public NodeHttpServer(Application application, AnyValue serconf) {
        super(application, createServer(application, serconf));
        this.httpServer = (HttpServer) server;
        this.rest = serconf != null && serconf.getAnyValue("rest") != null;
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new HttpServer(
                application,
                application.getStartTime(),
                application.getResourceFactory().createChild());
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return httpServer == null ? null : httpServer.getSocketAddress();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ClassFilter<Service> createServiceClassFilter() {
        return createClassFilter(
                this.sncpGroup,
                null,
                Service.class,
                new Class[] {org.redkale.watch.WatchService.class},
                Annotation.class,
                "services",
                "service");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ClassFilter<Filter> createFilterClassFilter() {
        return createClassFilter(
                null, null, HttpFilter.class, new Class[] {WatchFilter.class}, null, "filters", "filter");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ClassFilter<Servlet> createServletClassFilter() {
        return createClassFilter(
                null,
                WebServlet.class,
                HttpServlet.class,
                new Class[] {WatchServlet.class},
                null,
                "servlets",
                "servlet");
    }

    @Override
    protected List<ClassFilter> createOtherClassFilters() {
        this.webSocketFilter =
                createClassFilter(null, RestWebSocket.class, WebSocket.class, null, null, "rest", "websocket");
        List<ClassFilter> filters = super.createOtherClassFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        }
        filters.add(webSocketFilter);
        return filters;
    }

    @Override
    protected void loadOthers(List<ClassFilter> otherFilters) throws Exception {
        List<ClassFilter> filters = otherFilters;
        if (filters != null) {
            filters.remove(this.webSocketFilter); // webSocketFilter会在loadHttpFilter中处理，先剔除
        }
        super.loadOthers(filters);
    }

    @Override
    protected void loadService(ClassFilter<? extends Service> serviceFilter) throws Exception {
        resourceFactory.register(new NodeWebSocketNodeLoader(this));
        super.loadService(serviceFilter);
    }

    @Override
    protected void loadFilter(ClassFilter<? extends Filter> filterFilter) throws Exception {
        if (httpServer != null) {
            loadHttpFilter(filterFilter);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception {
        if (httpServer != null) {
            loadHttpServlet(servletFilter);
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadHttpFilter(final ClassFilter<? extends Filter> classFilter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        List<FilterEntry<? extends Filter>> list = new ArrayList(classFilter.getFilterEntrys());
        for (FilterEntry<? extends Filter> entry : list) {
            Class<HttpFilter> clazz = (Class<HttpFilter>) entry.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            if (entry.isExpect()) { // 跳过不自动加载的Filter
                continue;
            }
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            final HttpFilter filter = clazz.getDeclaredConstructor().newInstance();
            resourceFactory.inject(filter, this);
            AnyValueWriter filterConf = (AnyValueWriter) entry.getProperty();
            this.httpServer.addHttpFilter(filter, filterConf);
            if (sb != null) {
                sb.append("Load ").append(clazz.getName()).append(LINE_SEPARATOR);
            }
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadHttpServlet(final ClassFilter<? extends Servlet> servletFilter) throws Exception {
        RedkaleClassLoader.putReflectionPublicClasses(HttpServlet.class.getName());
        RedkaleClassLoader.putReflectionPublicClasses(HttpDispatcherServlet.class.getName());
        RedkaleClassLoader.putReflectionDeclaredConstructors(
                HttpResourceServlet.class, HttpResourceServlet.class.getName());
        final AnyValue servletsConf = this.serverConf.getAnyValue("servlets");
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        String prefix0 = servletsConf == null ? "" : servletsConf.getValue("path", "");
        if (!prefix0.isEmpty() && prefix0.charAt(prefix0.length() - 1) == '/') {
            prefix0 = prefix0.substring(0, prefix0.length() - 1);
        }
        if (!prefix0.isEmpty() && prefix0.charAt(0) != '/') {
            prefix0 = '/' + prefix0;
        }
        final String prefix = prefix0;
        List<FilterEntry<? extends Servlet>> list = new ArrayList(servletFilter.getFilterEntrys());
        list.sort(
                (FilterEntry<? extends Servlet> o1,
                        FilterEntry<? extends Servlet>
                                o2) -> { // 必须保证WebSocketServlet优先加载， 因为要确保其他的HttpServlet可以注入本地模式的WebSocketNode
                    boolean ws1 = WebSocketServlet.class.isAssignableFrom(o1.getType());
                    boolean ws2 = WebSocketServlet.class.isAssignableFrom(o2.getType());
                    if (ws1 == ws2) {
                        Priority p1 = o1.getType().getAnnotation(Priority.class);
                        Priority p2 = o2.getType().getAnnotation(Priority.class);
                        int v = (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
                        return v == 0
                                ? o1.getType().getName().compareTo(o2.getType().getName())
                                : 0;
                    }
                    return ws1 ? -1 : 1;
                });
        final long starts = System.currentTimeMillis();
        final List<AbstractMap.SimpleEntry<String, String[]>> ss = sb == null ? null : new ArrayList<>();
        for (FilterEntry<? extends Servlet> entry : list) {
            Class<HttpServlet> clazz = (Class<HttpServlet>) entry.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            if (clazz.getAnnotation(Rest.RestDyn.class) != null) {
                continue; // 动态生成的跳过
            }
            if (entry.isExpect()) { // 跳过不自动加载的Servlet
                continue;
            }
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws == null) {
                continue;
            }
            if (ws.value().length == 0) {
                logger.log(Level.INFO, "Not found @WebServlet.value in " + clazz.getName());
                continue;
            }
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            final HttpServlet servlet = clazz.getDeclaredConstructor().newInstance();
            resourceFactory.inject(servlet, this);
            final String[] mappings = ws.value();
            String pref = ws.repair() ? prefix : "";
            AnyValueWriter servletConf = (AnyValueWriter) entry.getProperty();
            this.httpServer.addHttpServlet(servlet, pref, servletConf, mappings);
            if (ss != null) {
                for (int i = 0; i < mappings.length; i++) {
                    mappings[i] = pref + mappings[i];
                }
                ss.add(new AbstractMap.SimpleEntry<>("HttpServlet (type=" + clazz.getName() + ")", mappings));
            }
        }
        final CopyOnWriteArrayList<AbstractMap.SimpleEntry<String, String[]>> rests =
                sb == null ? null : new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<AbstractMap.SimpleEntry<String, String[]>> webss =
                sb == null ? null : new CopyOnWriteArrayList<>();
        if (rest && serverConf != null) {
            final List<Object> restedObjects = new ArrayList<>();
            final ReentrantLock restedLock = new ReentrantLock();
            for (AnyValue restConf : serverConf.getAnyValues("rest")) {
                loadRestServlet(webSocketFilter, restConf, restedObjects, restedLock, sb, rests, webss);
            }
            this.webSocketFilter = null;
        }
        int max = 0;
        if (ss != null && sb != null) {
            int maxTypeLength = 0;
            int maxNameLength = 0;
            if (rests != null) {
                for (AbstractMap.SimpleEntry<String, String[]> en : rests) {
                    int pos = en.getKey().indexOf(':');
                    if (pos > maxTypeLength) {
                        maxTypeLength = pos;
                    }
                    int len = en.getKey().length() - pos - 1;
                    if (len > maxNameLength) {
                        maxNameLength = len;
                    }
                }
            }
            if (webss != null) {
                for (AbstractMap.SimpleEntry<String, String[]> en : webss) {
                    int pos = en.getKey().indexOf(':');
                    if (pos > maxTypeLength) {
                        maxTypeLength = pos;
                    }
                    int len = en.getKey().length() - pos - 1;
                    if (len > maxNameLength) {
                        maxNameLength = len;
                    }
                }
            }
            if (rests != null) {
                for (AbstractMap.SimpleEntry<String, String[]> en : rests) {
                    StringBuilder sub = new StringBuilder();
                    int pos = en.getKey().indexOf(':');
                    sub.append("RestServlet (type=").append(en.getKey().substring(0, pos));
                    for (int i = 0; i < maxTypeLength - pos; i++) {
                        sub.append(' ');
                    }
                    String n = en.getKey().substring(pos + 1);
                    sub.append(", name='").append(n).append("'");
                    for (int i = 0; i < maxNameLength - n.length(); i++) {
                        sub.append(' ');
                    }
                    sub.append(")");
                    ss.add(new AbstractMap.SimpleEntry<>(sub.toString(), en.getValue()));
                }
            }
            if (webss != null) {
                for (AbstractMap.SimpleEntry<String, String[]> en : webss) {
                    StringBuilder sub = new StringBuilder();
                    int pos = en.getKey().indexOf(':');
                    sub.append("RestWebSocket (type=").append(en.getKey().substring(0, pos));
                    for (int i = 0; i < maxTypeLength - pos; i++) {
                        sub.append(' ');
                    }
                    String n = en.getKey().substring(pos + 1);
                    sub.append(", name='").append(n).append("'");
                    for (int i = 0; i < maxNameLength - n.length(); i++) {
                        sub.append(' ');
                    }
                    sub.append(")");
                    ss.add(new AbstractMap.SimpleEntry<>(sub.toString(), en.getValue()));
                }
            }
            ss.sort((AbstractMap.SimpleEntry<String, String[]> o1, AbstractMap.SimpleEntry<String, String[]> o2) ->
                    o1.getKey().compareTo(o2.getKey()));
            for (AbstractMap.SimpleEntry<String, String[]> as : ss) {
                if (as.getKey().length() > max) {
                    max = as.getKey().length();
                }
            }
            for (AbstractMap.SimpleEntry<String, String[]> as : ss) {
                sb.append("Load ").append(as.getKey());
                for (int i = 0; i < max - as.getKey().length(); i++) {
                    sb.append(' ');
                }
                sb.append("  mapping to  ")
                        .append(Arrays.toString(as.getValue()))
                        .append(LINE_SEPARATOR);
            }
            sb.append("All HttpServlets load in ")
                    .append(System.currentTimeMillis() - starts)
                    .append(" ms")
                    .append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString().trim());
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadRestServlet(
            final ClassFilter<? extends WebSocket> webSocketFilter,
            final AnyValue restConf,
            final List<Object> restedObjects,
            final ReentrantLock restedLock,
            final StringBuilder sb,
            final CopyOnWriteArrayList<AbstractMap.SimpleEntry<String, String[]>> rests,
            final CopyOnWriteArrayList<AbstractMap.SimpleEntry<String, String[]>> webss)
            throws Exception {
        if (!rest) {
            return;
        }
        if (restConf == null) {
            return; // 不存在REST服务
        }
        String prefix0 = restConf.getValue("path", "");
        if (!prefix0.isEmpty() && prefix0.charAt(prefix0.length() - 1) == '/') {
            prefix0 = prefix0.substring(0, prefix0.length() - 1);
        }
        if (!prefix0.isEmpty() && prefix0.charAt(0) != '/') {
            prefix0 = '/' + prefix0;
        }

        String mqname = restConf.getValue("mq");
        MessageAgent agent0 = null;
        if (mqname != null) {
            agent0 = application.getResourceFactory().find(mqname, MessageAgent.class);
            if (agent0 == null) {
                throw new RedkaleException(
                        "not found " + MessageAgent.class.getSimpleName() + " config for (name=" + mqname + ")");
            }
        }
        final MessageAgent messageAgent = agent0;
        if (messageAgent != null) {
            prefix0 = ""; // 开启MQ时,prefix字段失效
        }
        final String prefix = prefix0;
        final boolean autoload = restConf.getBoolValue("autoload", true);
        { // 加载RestService
            String userTypeStr = restConf.getValue("usertype");
            final Class userType = userTypeStr == null ? null : this.serverClassLoader.loadClass(userTypeStr);

            final Class baseServletType =
                    this.serverClassLoader.loadClass(restConf.getValue("base", HttpServlet.class.getName()));
            final Set<String> includeValues = new HashSet<>();
            final Set<String> excludeValues = new HashSet<>();
            for (AnyValue item : restConf.getAnyValues("service")) {
                if (item.getBoolValue("ignore", false)) {
                    excludeValues.add(item.getValue("value", ""));
                } else {
                    includeValues.add(item.getValue("value", ""));
                }
            }

            final ClassFilter restFilter = ClassFilter.create(
                    serverClassLoader,
                    null,
                    application.isCompileMode() ? "" : restConf.getValue("includes", ""),
                    application.isCompileMode() ? "" : restConf.getValue("excludes", ""),
                    includeValues,
                    excludeValues);
            final CountDownLatch scdl = new CountDownLatch(super.servletServices.size());
            Stream<Service> stream = super.servletServices.stream();
            if (!application.isCompileMode()) {
                stream = stream.parallel(); // 不能并行，否则在maven plugin运行环境下ClassLoader不对
            }
            stream.forEach((service) -> {
                try {
                    final Class stype = Sncp.getResourceType(service);
                    final String name = Sncp.getResourceName(service);
                    RestService rs = (RestService) stype.getAnnotation(RestService.class);
                    if (rs == null || rs.ignore()) {
                        return;
                    }

                    final String stypename = stype.getName();
                    if (!autoload && !includeValues.contains(stypename)) {
                        return;
                    }
                    if (!restFilter.accept(stypename)) {
                        return;
                    }
                    restedLock.lock();
                    try {
                        if (restedObjects.contains(service)) {
                            logger.log(Level.WARNING, stype.getName() + " repeat create rest servlet, so ignore");
                            return;
                        }
                        restedObjects.add(service); // 避免重复创建Rest对象
                    } finally {
                        restedLock.unlock();
                    }
                    HttpServlet servlet =
                            httpServer.addRestServlet(serverClassLoader, service, userType, baseServletType, prefix);
                    if (servlet == null) {
                        return; // 没有HttpMapping方法的HttpServlet调用Rest.createRestServlet就会返回null
                    }
                    String prefix2 = prefix;
                    WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
                    if (ws != null && !ws.repair()) {
                        prefix2 = "";
                    }
                    resourceFactory.inject(servlet, NodeHttpServer.this);
                    dynServletMap.put(service, servlet);
                    if (messageAgent != null) {
                        messageAgent.putService(this, service, servlet);
                    }
                    // if (finest) logger.finest("Create RestServlet(resource.name='" + name + "') = " + servlet);
                    if (rests != null) {
                        String[] mappings = servlet.getClass()
                                .getAnnotation(WebServlet.class)
                                .value();
                        for (int i = 0; i < mappings.length; i++) {
                            mappings[i] = prefix2 + mappings[i];
                        }
                        rests.add(new AbstractMap.SimpleEntry<>(
                                Sncp.getResourceType(service).getName() + ":" + name, mappings));
                    }
                } finally {
                    scdl.countDown();
                }
            });
            scdl.await();
        }
        if (webSocketFilter != null) { // 加载RestWebSocket
            final Set<String> includeValues = new HashSet<>();
            final Set<String> excludeValues = new HashSet<>();
            for (AnyValue item : restConf.getAnyValues("websocket")) {
                if (item.getBoolValue("ignore", false)) {
                    excludeValues.add(item.getValue("value", ""));
                } else {
                    includeValues.add(item.getValue("value", ""));
                }
            }
            final ClassFilter restFilter = ClassFilter.create(
                    serverClassLoader,
                    null,
                    application.isCompileMode() ? "" : restConf.getValue("includes", ""),
                    application.isCompileMode() ? "" : restConf.getValue("excludes", ""),
                    includeValues,
                    excludeValues);

            List<FilterEntry<? extends WebSocket>> list = new ArrayList(webSocketFilter.getFilterEntrys());
            for (FilterEntry<? extends WebSocket> en : list) {
                Class<WebSocket> clazz = (Class<WebSocket>) en.getType();
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    logger.log(Level.FINE, clazz.getName() + " cannot abstract on rest websocket, so ignore");
                    continue;
                }
                if (Modifier.isFinal(clazz.getModifiers())) {
                    logger.log(Level.FINE, clazz.getName() + " cannot final on rest websocket, so ignore");
                    continue;
                }
                final Class<? extends WebSocket> stype = en.getType();
                if (stype.getAnnotation(Rest.RestDyn.class) != null) {
                    continue;
                }
                RestWebSocket rs = stype.getAnnotation(RestWebSocket.class);
                if (rs == null || rs.ignore()) {
                    continue;
                }

                final String stypename = stype.getName();
                if (!autoload && !includeValues.contains(stypename)) {
                    continue;
                }
                if (!restFilter.accept(stypename)) {
                    continue;
                }
                if (restedObjects.contains(stype)) {
                    logger.log(Level.WARNING, stype.getName() + " repeat create rest websocket, so ignore");
                    continue;
                }
                restedObjects.add(stype); // 避免重复创建Rest对象
                WebSocketServlet servlet = httpServer.addRestWebSocketServlet(
                        serverClassLoader, stype, messageAgent, prefix, en.getProperty());
                if (servlet == null) {
                    continue; // 没有RestOnMessage方法的HttpServlet调用Rest.createRestWebSocketServlet就会返回null
                }
                String prefix2 = prefix;
                WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
                if (ws != null && !ws.repair()) {
                    prefix2 = "";
                }
                resourceFactory.inject(servlet, NodeHttpServer.this);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(stype.getName() + " create a RestWebSocketServlet");
                }
                if (webss != null) {
                    String[] mappings =
                            servlet.getClass().getAnnotation(WebServlet.class).value();
                    for (int i = 0; i < mappings.length; i++) {
                        mappings[i] = prefix2 + mappings[i];
                    }
                    webss.add(new AbstractMap.SimpleEntry<>(stype.getName() + ":" + rs.name(), mappings));
                }
            }
        }
        if (messageAgent != null) {
            this.messageAgents.put(messageAgent.getName(), messageAgent);
        }
    }

    @Override // loadServlet执行之后调用
    protected void postLoadServlets() {
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
            cluster.register(this, protocol, dynServletMap.keySet(), new HashSet<>(), dynServletMap.keySet());
        }
    }

    @Override
    protected void afterClusterDeregisterOnPreDestroyServices(ClusterAgent cluster, String protocol) {
        cluster.deregister(this, protocol, dynServletMap.keySet(), new HashSet<>(), dynServletMap.keySet());
    }
}
