/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.boot.*;
import com.wentch.redkale.boot.ClassFilter.FilterEntry;
import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import java.lang.reflect.*;
import java.net.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
@NodeProtocol({"ICEP"})
public class NodeIcepServer extends NodeServer {

    private final IcepServer icepServer;

    public NodeIcepServer(Application application, AnyValue serconf) {
        super(application, application.getResourceFactory().createChild(), createServer(application, serconf));
        this.icepServer = (IcepServer) server;
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new IcepServer(application.getStartTime(), application.getWatchFactory());
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return icepServer == null ? null : icepServer.getSocketAddress();
    }

    @Override
    protected ClassFilter<Servlet> createServletClassFilter() {
        return createClassFilter(null, null, IcepServlet.class, null, "servlets", "servlet");
    }

    @Override
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception {
        if (icepServer != null) loadIcepServlet(this.nodeConf.getAnyValue("servlets"), servletFilter);
    }

    protected void loadIcepServlet(final AnyValue conf, ClassFilter<? extends Servlet> filter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (FilterEntry<? extends Servlet> en : filter.getFilterEntrys()) {
            Class<IcepServlet> clazz = (Class<IcepServlet>) en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) continue;
            final IcepServlet servlet = clazz.newInstance();
            factory.inject(servlet);
            DefaultAnyValue servletConf = (DefaultAnyValue) en.getProperty();
            this.icepServer.addIcepServlet(servlet, servletConf);
            if (sb != null) sb.append(threadName).append(" Loaded ").append(clazz.getName()).append(" --> ").append(format(servlet.getRequestid())).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

    private static String format(short value) {
        String str = Integer.toHexString(value);
        if (str.length() == 1) return "0x000" + str;
        if (str.length() == 2) return "0x00" + str;
        if (str.length() == 3) return "0x0" + str;
        return "0x" + str;
    }
}
