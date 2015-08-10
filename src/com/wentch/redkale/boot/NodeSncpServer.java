/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import static com.wentch.redkale.boot.Application.RESNAME_SNCP_ADDRESS;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.service.Service;
import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class NodeSncpServer extends NodeServer {

    private final SncpServer server;

    private final File home;

    public NodeSncpServer(Application application, InetSocketAddress addr, CountDownLatch regcdl, SncpServer server) {
        super(application, application.factory.createChild(), regcdl, server);
        this.server = server;
        this.home = application.getHome();
        this.servaddr = addr;
        this.nodeGroup = application.addrGroups.getOrDefault(addr, "");
        this.consumer = server == null ? null : x -> server.addService(x);
        this.factory.register(RESNAME_SNCP_ADDRESS, SocketAddress.class, this.servaddr);
        this.factory.register(RESNAME_SNCP_ADDRESS, InetSocketAddress.class, this.servaddr);
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return server == null ? null : server.getSocketAddress();
    }

    @Override
    public void prepare(AnyValue config) throws Exception {
        ClassFilter<Service> serviceFilter = createServiceClassFilter(config);
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(home, serviceFilter);
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
