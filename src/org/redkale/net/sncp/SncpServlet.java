/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.util.Objects;
import java.util.concurrent.*;
import org.redkale.net.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class SncpServlet extends Servlet<SncpContext, SncpRequest, SncpResponse> implements Comparable<SncpServlet> {

    protected final Class<? extends Service> type;

    protected final String serviceName;

    protected final Service service;

    protected SncpServlet(String serviceName, Class<? extends Service> type, Service service) {
        this.type = type;
        this.service = service;
        this.serviceName = serviceName;
    }

    public Service getService() {
        return service;
    }

    public String getServiceName() {
        return serviceName;
    }

    public abstract DLong getServiceid();

    protected ExecutorService getExecutor() {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            return ((WorkThread) thread).getExecutor();
        }
        return ForkJoinPool.commonPool();
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof SncpServlet)) return false;
        return Objects.equals(getServiceid(), ((SncpServlet) obj).getServiceid());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(getServiceid());
    }

    @Override
    public int compareTo(SncpServlet o) {
        return 0;
    }
}
