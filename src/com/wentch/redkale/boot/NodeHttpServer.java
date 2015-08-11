/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.net.http.WebServlet;
import com.wentch.redkale.net.http.HttpServer;
import com.wentch.redkale.net.http.HttpServlet;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.boot.ClassFilter.FilterEntry;
import com.wentch.redkale.net.http.*;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;
import javax.annotation.*;

/**
 * HTTP Server节点的配置Server 
 *
 * @author zhangjx
 */
public final class NodeHttpServer extends NodeServer {

    private final HttpServer server;

    public NodeHttpServer(Application application, CountDownLatch servicecdl, HttpServer server) {
        super(application, application.factory.createChild(), servicecdl, server);
        this.server = server;
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return server == null ? null : server.getSocketAddress();
    }

    @Override
    public void prepare(AnyValue config) throws Exception {
        ClassFilter<HttpServlet> httpFilter = createClassFilter(null, config, WebServlet.class, HttpServlet.class, null, "servlets", "servlet");
        ClassFilter<Service> serviceFilter = createServiceClassFilter(config);
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serviceFilter, httpFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter); //必须在servlet之前
        initWebSocketService();
        if (server != null) loadHttpServlet(config.getAnyValue("servlets"), httpFilter);
    }

    private void initWebSocketService() {
        NodeSncpServer sncpServer0 = null;
        for (NodeServer ns : application.servers) {
            if (!ns.isSNCP()) continue;
            if (sncpServer0 == null) sncpServer0 = (NodeSncpServer) ns;
            if (ns.getNodeGroup().equals(getNodeGroup())) {
                sncpServer0 = (NodeSncpServer) ns;
                break;
            }
        }
        final NodeSncpServer sncpServer = sncpServer0;
        final ResourceFactory regFactory = application.factory;
        factory.add(WebSocketNode.class, (ResourceFactory rf, final Object src, Field field) -> {
            try {
                Resource rs = field.getAnnotation(Resource.class);
                if (rs == null) return;
                if (!(src instanceof WebSocketServlet)) return;
                String rcname = rs.name();
                if (rcname.equals(ResourceFactory.RESOURCE_PARENT_NAME)) rcname = ((WebSocketServlet) src).name();
                synchronized (regFactory) {
                    Service nodeService = (Service) rf.find(rcname, WebSocketNode.class);
                    if (nodeService == null) {
                        Class<? extends Service> sc = (Class<? extends Service>) application.webSocketNodeClass;
                        nodeService = Sncp.createLocalService(rcname, (Class<? extends Service>) (sc == null ? WebSocketNodeService.class : sc),
                                getNodeAddress(), (sc == null ? null : nodeSameGroupTransports), (sc == null ? null : nodeDiffGroupTransports));
                        regFactory.register(rcname, WebSocketNode.class, nodeService);
                        WebSocketNode wsn = (WebSocketNode) nodeService;
                        wsn.setLocalSncpAddress(getNodeAddress());
                        final Set<InetSocketAddress> alladdrs = new HashSet<>();
                        application.globalNodes.forEach((k, v) -> alladdrs.add(k));
                        alladdrs.remove(getNodeAddress());
                        WebSocketNode remoteNode = (WebSocketNode) Sncp.createRemoteService(rcname, (Class<? extends Service>) (sc == null ? WebSocketNodeService.class : sc),
                                getNodeAddress(), (sc == null ? null : loadTransport(getNodeGroup(), getNodeProtocol(), alladdrs)));
                        wsn.setRemoteWebSocketNode(remoteNode);
                        factory.inject(nodeService);
                        factory.inject(remoteNode);
                        if (sncpServer != null) {
                            ServiceWrapper wrapper = new ServiceWrapper((Class<? extends Service>) (sc == null ? WebSocketNodeService.class : sc), nodeService, getNodeGroup(), rcname, null);
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

    protected void loadHttpServlet(final AnyValue conf, final ClassFilter<HttpServlet> filter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String prefix = conf == null ? "" : conf.getValue("prefix", "");
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (FilterEntry<HttpServlet> en : filter.getFilterEntrys()) {
            Class<HttpServlet> clazz = en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) continue;
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws == null || ws.value().length == 0) continue;
            final HttpServlet servlet = clazz.newInstance();
            factory.inject(servlet);
            String[] mappings = ws.value();
            if (ws.fillurl() && !prefix.isEmpty()) {
                for (int i = 0; i < mappings.length; i++) {
                    mappings[i] = prefix + mappings[i];
                }
            }
            this.server.addHttpServlet(servlet, en.getProperty(), mappings);
            if (sb != null) sb.append(threadName).append(" Loaded ").append(clazz.getName()).append(" --> ").append(Arrays.toString(mappings)).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

    @Override
    public boolean isSNCP() {
        return false;
    }
}
