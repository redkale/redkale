/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.net.Server;
import org.redkale.util.AnyValue;
import org.redkale.boot.Application;
import org.redkale.net.Servlet;
import org.redkale.boot.ClassFilter;
import org.redkale.boot.NodeServer;
import org.redkale.boot.NodeProtocol;
import org.redkale.boot.ClassFilter.FilterEntry;
import static org.redkale.boot.NodeServer.LINE_SEPARATOR;
import org.redkale.util.AnyValue.DefaultAnyValue;
import java.lang.reflect.*;
import java.net.*;
import java.util.logging.*;

/**
 * < server protocol="SOCKS" host="0.0.0.0" port="1080" bindaddr="外网IP"> < /server>
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@NodeProtocol({"SOCKS"})
public class NodeSocksServer extends NodeServer {

    private final SocksServer socksServer;

    public NodeSocksServer(Application application, AnyValue serconf) {
        super(application, application.getResourceFactory().createChild(), createServer(application, serconf));
        this.socksServer = (SocksServer) server;
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new SocksServer(application.getStartTime(), application.getWatchFactory());
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return socksServer == null ? null : socksServer.getSocketAddress();
    }

    @Override
    protected ClassFilter<Servlet> createServletClassFilter() {
        return createClassFilter(null, null, SocksServlet.class, null, "servlets", "servlet");
    }

    @Override
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception {
        if (socksServer != null) loadSocksServlet(this.nodeConf.getAnyValue("servlets"), servletFilter);
    }

    protected void loadSocksServlet(final AnyValue conf, ClassFilter<? extends Servlet> filter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (FilterEntry<? extends Servlet> en : filter.getFilterEntrys()) {
            Class<SocksServlet> clazz = (Class<SocksServlet>) en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) continue;
            final SocksServlet servlet = clazz.newInstance();
            factory.inject(servlet);
            DefaultAnyValue servletConf = (DefaultAnyValue) en.getProperty();
            this.socksServer.addSocksServlet(servlet, servletConf);
            if (sb != null) sb.append(threadName).append(" Loaded ").append(clazz.getName()).append(" --> ").append(servletConf).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

}
