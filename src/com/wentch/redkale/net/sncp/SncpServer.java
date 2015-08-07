/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Service Node Communicate Protocol
 *
 * @author zhangjx
 */
public final class SncpServer extends Server {

    protected InetSocketAddress nodeAddress;

    public SncpServer(String protocol) {
        this(System.currentTimeMillis(), protocol, null, null);
    }

    public SncpServer(long serverStartTime, String protocol, InetSocketAddress nodeAddress, final WatchFactory watch) {
        super(serverStartTime, protocol, new SncpPrepareServlet(), watch);
        this.nodeAddress = nodeAddress;
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        if (this.nodeAddress == null) {
            if ("0.0.0.0".equals(this.address.getHostString())) {
                this.nodeAddress = new InetSocketAddress(Utility.localInetAddress().getHostAddress(), this.address.getPort());
            } else {
                this.nodeAddress = this.address;
            }
        }
    }

    public void addService(ServiceWrapper entry) {
        ((SncpPrepareServlet) this.prepare).addSncpServlet(new SncpDynServlet(BsonFactory.root().getConvert(), entry.getName(), entry.getService(), entry.getConf()));
    }

    public List<SncpServlet> getSncpServlets() {
        return ((SncpPrepareServlet) this.prepare).getSncpServlets();
    }

    /**
     *
     * 对外的IP地址
     *
     @return 
     */
    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Buffer.cycleCounter");
        int rcapacity = Math.max(this.capacity, 8 * 1024);
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("SNCP_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = SncpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        SncpContext sncpcontext = new SncpContext(this.serverStartTime, this.logger, executor, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, this.prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond);
        responsePool.setCreator((Object... params) -> new SncpResponse(sncpcontext, new SncpRequest(sncpcontext, sncpcontext.bsonFactory)));
        return sncpcontext;
    }

}
