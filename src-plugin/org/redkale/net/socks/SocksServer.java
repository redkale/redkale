/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.util.AnyValue;
import org.redkale.net.Server;
import org.redkale.util.ObjectPool;
import org.redkale.net.Context;
import org.redkale.watch.WatchFactory;
import org.redkale.net.Response;
import java.nio.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class SocksServer extends Server {

    public SocksServer() {
        this(System.currentTimeMillis(), null);
    }

    public SocksServer(long serverStartTime, final WatchFactory watch) {
        super(serverStartTime, "TCP", new SocksPrepareServlet(), watch);
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
    }

    public void addSocksServlet(SocksServlet servlet, AnyValue conf) {
        ((SocksPrepareServlet) this.prepare).setSocksServlet(servlet, conf);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        if (this.readTimeoutSecond < 1) this.readTimeoutSecond = 6;
        if (this.writeTimeoutSecond < 1) this.writeTimeoutSecond = 6;
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SOCKS_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SOCKS_" + port + ".Buffer.cycleCounter");
        int rcapacity = Math.max(this.capacity, 8 * 1024);
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SOCKS_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SOCKS_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = SocksResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        SocksContext localcontext = new SocksContext(this.serverStartTime, this.logger, executor, rcapacity, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, this.prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond, "");
        responsePool.setCreator((Object... params) -> new SocksResponse(localcontext, new SocksRequest(localcontext)));
        return localcontext;
    }
}
