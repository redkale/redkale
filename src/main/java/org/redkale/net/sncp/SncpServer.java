/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import org.redkale.boot.Application;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.Server;
import org.redkale.net.sncp.SncpContext.SncpContextConfig;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * Service Node Communicate Protocol
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class SncpServer extends Server<Uint128, SncpContext, SncpRequest, SncpResponse, SncpServlet> {

    public SncpServer() {
        this(null, System.currentTimeMillis(), null, ResourceFactory.create());
    }

    public SncpServer(ResourceFactory resourceFactory) {
        this(null, System.currentTimeMillis(), null, resourceFactory);
    }

    public SncpServer(
            Application application, long serverStartTime, AnyValue serconf, ResourceFactory resourceFactory) {
        super(application, serverStartTime, getConfNetprotocol(serconf), resourceFactory, new SncpDispatcherServlet());
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
     * @param <T> 泛型
     * @param filterClass SncpFilter类
     * @return SncpFilter
     */
    public <T extends SncpFilter> T removeSncpFilter(Class<T> filterClass) {
        return (T) this.dispatcher.removeFilter(filterClass);
    }

    /**
     * 添加SncpFilter
     *
     * @param filter SncpFilter
     * @param conf AnyValue
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
     * @return SncpServlet
     */
    public SncpServlet removeSncpServlet(Service sncpService) {
        if (!Sncp.isSncpDyn(sncpService)) {
            throw new SncpException(sncpService + " is not sncp dynamic-gen service");
        }
        return ((SncpDispatcherServlet) this.dispatcher).removeSncpServlet(sncpService);
    }

    public SncpServlet addSncpServlet(Service sncpService) {
        if (!Sncp.isSncpDyn(sncpService)) {
            throw new SncpException(sncpService + " is not sncp dynamic-gen service");
        }
        SncpServlet sds =
                new SncpServlet(Sncp.getResourceName(sncpService), Sncp.getResourceType(sncpService), sncpService);
        this.dispatcher.addServlet(sds, null, Sncp.getResourceConf(sncpService));
        return sds;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SncpContext createContext() {
        if (!"UDP".equalsIgnoreCase(netprotocol)) {
            this.bufferCapacity = Math.max(this.bufferCapacity, 8 * 1024);
        }
        final SncpContextConfig contextConfig = new SncpContextConfig();
        initContextConfig(contextConfig);

        return new SncpContext(contextConfig);
    }

    @Override
    protected ByteBufferPool createSafeBufferPool(LongAdder createCounter, LongAdder cycleCounter, int bufferPoolSize) {
        return ByteBufferPool.createSafePool(createCounter, cycleCounter, bufferPoolSize, this.bufferCapacity);
    }

    @Override
    protected ObjectPool<SncpResponse> createSafeResponsePool(
            LongAdder createCounter, LongAdder cycleCounter, int responsePoolSize) {
        Creator<SncpResponse> creator =
                (Object... params) -> new SncpResponse(this.context, new SncpRequest(this.context));
        return ObjectPool.createSafePool(
                createCounter, cycleCounter, responsePoolSize, creator, SncpResponse::prepare, SncpResponse::recycle);
    }
}
