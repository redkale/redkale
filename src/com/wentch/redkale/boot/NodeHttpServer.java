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
import com.wentch.redkale.service.Service;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class NodeHttpServer extends NodeServer {

    private final HttpServer server;

    public NodeHttpServer(Application application, CountDownLatch servicecdl, HttpServer server) {
        super(application, servicecdl, server);
        this.server = server;
    }

    @Override
    public void init(AnyValue config) throws Exception {
        server.init(config);
        super.init(config);
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return server == null ? null : server.getSocketAddress();
    }

    @Override
    public void prepare(AnyValue config) throws Exception {
        super.prepare(config);
        ClassFilter<HttpServlet> httpFilter = createHttpServletClassFilter(application.nodeName, config);
        ClassFilter<Service> serviceFilter = createServiceClassFilter(application.nodeName, config);
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serviceFilter, httpFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(config.getAnyValue("services"), serviceFilter); //必须在servlet之前
        if (server != null) initHttpServlet(config.getAnyValue("servlets"), httpFilter);
    }

    protected static ClassFilter<HttpServlet> createHttpServletClassFilter(final String node, final AnyValue config) {
        return createClassFilter(node, config, WebServlet.class, HttpServlet.class, null, "servlets", "servlet");
    }

    protected void initHttpServlet(final AnyValue conf, final ClassFilter<HttpServlet> filter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String prefix = conf == null ? "" : conf.getValue("prefix", "");
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (FilterEntry<HttpServlet> en : filter.getFilterEntrys()) {
            Class<HttpServlet> clazz = en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) continue;
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws == null || ws.value().length == 0) continue;
            final HttpServlet servlet = clazz.newInstance();
            application.factory.inject(servlet);
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
}
