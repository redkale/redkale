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

    private final AtomicInteger maxClassNameLength = new AtomicInteger();

    private final AtomicInteger maxNameLength = new AtomicInteger();

    public SncpServer() {
        this(null, System.currentTimeMillis(), null, ResourceFactory.root());
    }

    public SncpServer(ResourceFactory resourceFactory) {
        this(null, System.currentTimeMillis(), null, resourceFactory);
    }

    public SncpServer(Application application, long serverStartTime, AnyValue serconf, ResourceFactory resourceFactory) {
        super(application, serverStartTime, netprotocol(serconf), resourceFactory, new SncpPrepareServlet());
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
        return this.prepare.getServlets();
    }

    public List<SncpFilter> getSncpFilters() {
        return this.prepare.getFilters();
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
        return (T) this.prepare.removeFilter(filterClass);
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
        this.prepare.addFilter(filter, conf);
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
        return ((SncpPrepareServlet) this.prepare).removeSncpServlet(sncpService);
    }

    public SncpDynServlet addSncpServlet(Service sncpService) {
        if (!Sncp.isSncpDyn(sncpService)) return null;
        SncpDynServlet sds = new SncpDynServlet(BsonFactory.root().getConvert(), Sncp.getResourceName(sncpService),
            Sncp.getResourceType(sncpService), sncpService, maxClassNameLength, maxNameLength);
        this.prepare.addServlet(sds, null, Sncp.getConf(sncpService));
        return sds;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SncpContext createContext(Application application) {
        this.bufferCapacity = Math.max(this.bufferCapacity, 8 * 1024);

        final SncpContextConfig contextConfig = new SncpContextConfig();
        if (application != null) contextConfig.workExecutor = application.getWorkExecutor();
        contextConfig.serverStartTime = this.serverStartTime;
        contextConfig.logger = this.logger;
        contextConfig.sslContext = this.sslContext;
        contextConfig.bufferCapacity = this.bufferCapacity;
        contextConfig.maxconns = this.maxconns;
        contextConfig.maxbody = this.maxbody;
        contextConfig.charset = this.charset;
        contextConfig.address = this.address;
        contextConfig.prepare = this.prepare;
        contextConfig.resourceFactory = this.resourceFactory;
        contextConfig.aliveTimeoutSeconds = this.aliveTimeoutSeconds;
        contextConfig.readTimeoutSeconds = this.readTimeoutSeconds;
        contextConfig.writeTimeoutSeconds = this.writeTimeoutSeconds;

        return new SncpContext(contextConfig);
    }

    @Override
    protected ObjectPool<ByteBuffer> createBufferPool(AtomicLong createCounter, AtomicLong cycleCounter, int bufferPoolSize) {
        if (createCounter == null) createCounter = new AtomicLong();
        if (cycleCounter == null) cycleCounter = new AtomicLong();
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
    protected ObjectPool<Response> createResponsePool(AtomicLong createCounter, AtomicLong cycleCounter, int responsePoolSize) {
        Creator<Response> creator = (Object... params) -> new SncpResponse(this.context, new SncpRequest(this.context));
        ObjectPool<Response> pool = ObjectPool.createSafePool(createCounter, cycleCounter, responsePoolSize, creator, (x) -> ((SncpResponse) x).prepare(), (x) -> ((SncpResponse) x).recycle());
        return pool;
    }

}
