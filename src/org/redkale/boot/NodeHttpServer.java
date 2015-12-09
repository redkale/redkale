/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import org.redkale.net.http.WebServlet;
import org.redkale.net.http.HttpServer;
import org.redkale.net.http.HttpServlet;
import org.redkale.util.AnyValue;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.util.AnyValue.DefaultAnyValue;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.*;
import javax.annotation.*;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * HTTP Server节点的配置Server
 *
 * @author zhangjx
 */
@NodeProtocol({"HTTP", "HTTPS"})
public final class NodeHttpServer extends NodeServer {

    private final HttpServer httpServer;

    public NodeHttpServer(Application application, AnyValue serconf) {
        super(application, application.getResourceFactory().createChild(), createServer(application, serconf));
        this.httpServer = (HttpServer) server;
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new HttpServer(application.getStartTime(), application.getWatchFactory());
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return httpServer == null ? null : httpServer.getSocketAddress();
    }

    @Override
    protected ClassFilter<Servlet> createServletClassFilter() {
        return createClassFilter(null, WebServlet.class, HttpServlet.class, null, "servlets", "servlet");
    }

    @Override
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception {
        if (httpServer != null) loadHttpServlet(this.nodeConf.getAnyValue("servlets"), servletFilter);
    }

    @Override
    protected void loadService(ClassFilter serviceFilter) throws Exception {
        super.loadService(serviceFilter);
        initWebSocketService();
    }

    private void initWebSocketService() {
        final NodeServer self = this;
        final ResourceFactory regFactory = application.getResourceFactory();
        factory.add(WebSocketNode.class, (ResourceFactory rf, final Object src, Field field, Object attachment) -> {
            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if (!(src instanceof WebSocketServlet)) return;
                String rcname = rs.name();
                if (rcname.equals(ResourceFactory.RESOURCE_PARENT_NAME)) rcname = ((WebSocketServlet) src).name();
                synchronized (regFactory) {
                    Service nodeService = (Service) rf.find(rcname, WebSocketNode.class);
                    if (nodeService == null) {
                        nodeService = Sncp.createLocalService(rcname, getExecutor(), (Class<? extends Service>) WebSocketNodeService.class,
                                getSncpAddress(), sncpDefaultGroups, sncpSameGroupTransports, sncpDiffGroupTransports);
                        regFactory.register(rcname, WebSocketNode.class, nodeService);
                        factory.inject(nodeService, self);
                        logger.fine("[" + Thread.currentThread().getName() + "] Load " + nodeService);
                        if (getSncpAddress() != null) {
                            NodeSncpServer sncpServer = null;
                            for (NodeServer node : application.servers) {
                                if (node.isSNCP() && getSncpAddress().equals(node.getSncpAddress())) {
                                    sncpServer = (NodeSncpServer) node;
                                }
                            }
                            ServiceWrapper wrapper = new ServiceWrapper(WebSocketNodeService.class, nodeService, rcname, getSncpGroup(), sncpDefaultGroups, null);
                            sncpServer.getSncpServer().addService(wrapper);
                        }
                    }
                    field.set(src, nodeService);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "WebSocketNode inject error", e);
            }
        });
    }

    protected void loadHttpServlet(final AnyValue conf, final ClassFilter<? extends Servlet> filter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String prefix = conf == null ? "" : conf.getValue("prefix", "");
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (FilterEntry<? extends Servlet> en : filter.getFilterEntrys()) {
            Class<HttpServlet> clazz = (Class<HttpServlet>) en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) continue;
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws == null || ws.value().length == 0) continue;
            final HttpServlet servlet = clazz.newInstance();
            factory.inject(servlet, this);
            String[] mappings = ws.value();
            if (ws.fillurl() && !prefix.isEmpty()) {
                for (int i = 0; i < mappings.length; i++) {
                    mappings[i] = prefix + mappings[i];
                }
            }
            DefaultAnyValue servletConf = (DefaultAnyValue) en.getProperty();
            WebInitParam[] webparams = ws.initParams();
            if (webparams.length > 0) {
                if (servletConf == null) servletConf = new DefaultAnyValue();
                for (WebInitParam webparam : webparams) {
                    servletConf.addValue(webparam.name(), webparam.value());
                }
            }
            this.httpServer.addHttpServlet(servlet, servletConf, mappings);
            if (sb != null) sb.append(threadName).append(" Loaded ").append(clazz.getName()).append(" --> ").append(Arrays.toString(mappings)).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

}
