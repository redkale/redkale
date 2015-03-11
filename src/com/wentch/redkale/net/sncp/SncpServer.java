/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.BsonConvert;
import com.wentch.redkale.convert.bson.BsonFactory;
import com.wentch.redkale.net.ResponsePool;
import com.wentch.redkale.net.BufferPool;
import com.wentch.redkale.net.Server;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.watch.WatchFactory;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Service Node Communicate Protocol
 *
 * @author zhangjx
 */
public final class SncpServer extends Server {

    private final List<ServiceEntry> services = new ArrayList<>();

    public SncpServer() {
        this(System.currentTimeMillis(), null);
    }

    public SncpServer(long serverStartTime, final WatchFactory watch) {
        super(serverStartTime, "UDP", watch);
    }

    public void addService(ServiceEntry entry) {
        this.services.add(entry);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Buffer.cycleCounter");
        BufferPool bufferPool = new BufferPool(createBufferCounter, cycleBufferCounter, Math.max(this.capacity, 8 * 1024), this.bufferPoolSize);
        SncpPrepareServlet prepare = new SncpPrepareServlet();
        final BsonConvert convert = BsonFactory.root().getConvert();
        this.services.stream().forEach(x -> x.getNames().forEach(y -> prepare.addSncpServlet(new SncpDynServlet(convert, y, x.getService(), x.getServiceConf()))));
        this.services.clear();
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Response.cycleCounter");
        SncpContext sncpcontext = new SncpContext(this.serverStartTime, this.logger, executor, bufferPool,
                new ResponsePool(createResponseCounter, cycleResponseCounter, this.responsePoolSize),
                this.maxbody, this.charset, this.address, prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond);
        sncpcontext.getResponsePool().setResponseFactory(() -> new SncpResponse(sncpcontext, new SncpRequest(sncpcontext, sncpcontext.bsonFactory)));
        return sncpcontext;
    }

}
