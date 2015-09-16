/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.nio.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class IcepServer extends Server {

    public IcepServer() {
        this(System.currentTimeMillis(), null);
    }

    public IcepServer(long serverStartTime, final WatchFactory watch) {
        super(serverStartTime, "UDP", new IcepPrepareServlet(), watch);
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
    }

    public void addIcepServlet(IcepServlet servlet, AnyValue conf) {
        ((IcepPrepareServlet) this.prepare).addIcepServlet(servlet, conf);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("ICEP_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("ICEP_" + port + ".Buffer.cycleCounter");
        int rcapacity = Math.max(this.capacity, 16 * 1024 + 8); //兼容 HTTP 2.0
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("ICEP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("ICEP_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = IcepResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        IcepContext icepcontext = new IcepContext(this.serverStartTime, this.logger, executor, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, this.prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond);
        responsePool.setCreator((Object... params) -> new IcepResponse(icepcontext, new IcepRequest(icepcontext)));
        return icepcontext;
    }
}
