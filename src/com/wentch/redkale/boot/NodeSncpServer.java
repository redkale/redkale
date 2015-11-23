/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.net.*;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.util.*;
import java.net.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
@NodeProtocol({"SNCP"})
public final class NodeSncpServer extends NodeServer {

    private final SncpServer sncpServer;

    public NodeSncpServer(Application application, AnyValue serconf) {
        super(application, application.getResourceFactory().createChild(), createServer(application, serconf));
        this.sncpServer = (SncpServer) this.server;
        this.consumer = sncpServer == null ? null : x -> sncpServer.addService(x);
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new SncpServer(application.getStartTime(), application.getWatchFactory());
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return sncpServer == null ? null : sncpServer.getSocketAddress();
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        //-------------------------------------------------------------------
        if (sncpServer == null) return; //调试时server才可能为null
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (SncpServlet en : sncpServer.getSncpServlets()) {
            if (sb != null) sb.append(threadName).append(" Loaded ").append(en).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

    @Override
    public boolean isSNCP() {
        return true;
    }

    public SncpServer getSncpServer() {
        return sncpServer;
    }

    @Override
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter) throws Exception {
    }

    @Override
    protected ClassFilter<Servlet> createServletClassFilter() {
        return null;
    }

}
