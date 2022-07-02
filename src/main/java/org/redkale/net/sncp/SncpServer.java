/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.*;
import org.redkale.boot.Application;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.net.*;
import org.redkale.net.sncp.SncpContext.SncpContextConfig;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * Service Node Communicate Protocol
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class SncpServer extends Server<DLong, SncpContext, SncpRequest, SncpResponse, SncpServlet> {

    private final AtomicInteger maxTypeLength = new AtomicInteger();

    private final AtomicInteger maxNameLength = new AtomicInteger();

    public SncpServer() {
        this(null, System.currentTimeMillis(), null, ResourceFactory.create());
    }

    public SncpServer(ResourceFactory resourceFactory) {
        this(null, System.currentTimeMillis(), null, resourceFactory);
    }

    public SncpServer(Application application, long serverStartTime, AnyValue serconf, ResourceFactory resourceFactory) {
        super(application, serverStartTime, netprotocol(serconf), resourceFactory, new SncpDispatcherServlet());
    }

    private static String netprotocol(AnyValue serconf) {
        if (serconf == null) return "TCP";
        String protocol = serconf.getValue("protocol", "").toUpperCase();
        if (protocol.endsWith(".UDP")) return "UDP";
        return "TCP";
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
    }

    public List<SncpServlet> getSncpServlets() {
        return this.dispatcher.getServlets();
    }

    public List<SncpFilter> getSncpFilters() {
        return this.dispatcher.getFilters();
    }

    /**
     * 删除SncpFilter
     *
     * @param <T>         泛型
     * @param filterClass SncpFilter类
     *
     * @return SncpFilter
     */
    public <T extends SncpFilter> T removeSncpFilter(Class<T> filterClass) {
        return (T) this.dispatcher.removeFilter(filterClass);
    }

    /**
     * 添加SncpFilter
     *
     * @param filter SncpFilter
     * @param conf   AnyValue
     *
     * @return SncpServer
     */
    public SncpServer addSncpFilter(SncpFilter filter, AnyValue conf) {
        this.dispatcher.addFilter(filter, conf);
        return this;
    }

    /**
     * 删除SncpServlet
     *
     * @param sncpService Service
     *
     * @return SncpServlet
     */
    public SncpServlet removeSncpServlet(Service sncpService) {
        return ((SncpDispatcherServlet) this.dispatcher).removeSncpServlet(sncpService);
    }

    public SncpDynServlet addSncpServlet(Service sncpService) {
        if (!Sncp.isSncpDyn(sncpService)) return null;
        SncpDynServlet sds = new SncpDynServlet(BsonFactory.root().getConvert(), Sncp.getResourceName(sncpService),
            Sncp.getResourceType(sncpService), sncpService, maxTypeLength, maxNameLength);
        this.dispatcher.addServlet(sds, null, Sncp.getConf(sncpService));
        return sds;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SncpContext createContext() {
        this.bufferCapacity = Math.max(this.bufferCapacity, 8 * 1024);

        final SncpContextConfig contextConfig = new SncpContextConfig();
        initContextConfig(contextConfig);

        return new SncpContext(contextConfig);
    }

    @Override
    protected ObjectPool<ByteBuffer> createBufferPool(LongAdder createCounter, LongAdder cycleCounter, int bufferPoolSize) {
        final int rcapacity = this.bufferCapacity;
        ObjectPool<ByteBuffer> bufferPool = ObjectPool.createSafePool(createCounter, cycleCounter, bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                e.clear();
                return true;
            });
        return bufferPool;
    }

    @Override
    protected ObjectPool<Response> createResponsePool(LongAdder createCounter, LongAdder cycleCounter, int responsePoolSize) {
        Creator<Response> creator = (Object... params) -> new SncpResponse(this.context, new SncpRequest(this.context));
        ObjectPool<Response> pool = ObjectPool.createSafePool(createCounter, cycleCounter, responsePoolSize, creator, (x) -> ((SncpResponse) x).prepare(), (x) -> ((SncpResponse) x).recycle());
        return pool;
    }

}
