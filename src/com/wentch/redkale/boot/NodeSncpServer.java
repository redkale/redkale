/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.net.sncp.SncpServer;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.service.Service;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author zhangjx
 */
public final class NodeSncpServer extends NodeServer {

    private final SncpServer server;

    public NodeSncpServer(Application application, CountDownLatch regcdl, SncpServer server) {
        super(application, regcdl, server);
        this.server = server;
        this.consumer = x -> server.addService(x);
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
    public void load(AnyValue config) throws Exception {
        super.load(config);
        ClassFilter<Service> serviceFilter = createServiceClassFilter(application.nodeName, config);
        long s = System.currentTimeMillis();
        ClassFilter.Loader.load(application.getHome(), serviceFilter);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " load filter class in " + e + " ms");
        loadService(config.getAnyValue("services"), serviceFilter); //必须在servlet之前
    }

}
