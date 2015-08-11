/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.service.Service;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class NodeSncpServer extends NodeServer {

    private final SncpServer server;

    public NodeSncpServer(Application application, CountDownLatch regcdl, SncpServer server) {
        super(application, application.factory.createChild(), regcdl, server);
        this.server = server;
        this.consumer = server == null ? null : x -> server.addService(x);
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return server == null ? null : server.getSocketAddress();
    }

    @Override
    public void prepare(AnyValue config) throws Exception {
        ClassFilter<Service> serviceFilter = createServiceClassFilter(config);
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serviceFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(serviceFilter); //必须在servlet之前
        //-------------------------------------------------------------------
        if (server == null) return; //调试时server才可能为null
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        for (SncpServlet en : server.getSncpServlets()) {
            if (sb != null) sb.append(threadName).append(" Loaded ").append(en).append(LINE_SEPARATOR);
        }
        if (sb != null && sb.length() > 0) logger.log(Level.FINE, sb.toString());
    }

    @Override
    public boolean isSNCP() {
        return true;
    }

    public SncpServer getSncpServer() {
        return server;
    }
}
