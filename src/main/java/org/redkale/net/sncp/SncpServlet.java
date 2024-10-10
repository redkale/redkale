/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.redkale.net.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpServlet extends Servlet<SncpContext, SncpRequest, SncpResponse> implements Comparable<SncpServlet> {

    protected final Class resourceType;

    protected final String resourceName;

    protected final Service service;

    protected final Uint128 serviceid;

    private final HashMap<Uint128, SncpActionServlet> actions = new HashMap<>();

    protected SncpServlet(String resourceName, Class resourceType, Service service, Uint128 serviceid) {
        Objects.requireNonNull(resourceName);
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(service);
        Objects.requireNonNull(serviceid);
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.service = service;
        this.serviceid = serviceid;
    }

    protected SncpServlet(String resourceName, Class resourceType, Service service) {
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.service = service;
        this._nonBlocking = true;
        this.serviceid = Sncp.serviceid(resourceName, resourceType);

        Class serviceImplClass = Sncp.getServiceType(service);
        for (Map.Entry<Uint128, Method> en :
                Sncp.loadRemoteMethodActions(serviceImplClass).entrySet()) {
            SncpActionServlet action;
            try {
                action = SncpActionServlet.create(
                        resourceName, resourceType, serviceImplClass, service, serviceid, en.getKey(), en.getValue());
            } catch (RuntimeException e) {
                throw new SncpException(
                        en.getValue() + " create " + SncpActionServlet.class.getSimpleName() + " error", e);
            }
            actions.put(en.getKey(), action);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        SncpHeader reqHeader = request.getHeader();
        final SncpActionServlet action = actions.get(reqHeader.getActionid());
        // logger.log(Level.FINEST, "sncpdyn.execute: " + request + ", " + (action == null ? "null" : action.method));
        if (action == null) {
            response.finish(SncpResponse.RETCODE_ILLACTIONID, null); // 无效actionid
        } else {
            try {
                reqHeader.serviceName = action.resourceType.getName();
                reqHeader.methodName = action.method.getName();
                if (response.inNonBlocking()) {
                    if (action.nonBlocking) {
                        action.execute(request, response);
                    } else {
                        response.updateNonBlocking(false);
                        response.getWorkExecutor().execute(() -> {
                            try {
                                Traces.computeIfAbsent(request.getTraceid());
                                action.execute(request, response);
                            } catch (Throwable t) {
                                response.getContext()
                                        .getLogger()
                                        .log(Level.WARNING, "Servlet occur exception. request = " + request, t);
                                response.finishError(t);
                            }
                            Traces.removeTraceid();
                        });
                    }
                } else {
                    action.execute(request, response);
                }
            } catch (Throwable t) {
                response.getContext().getLogger().log(Level.SEVERE, "sncp execute error(" + request + ")", t);
                response.finish(SncpResponse.RETCODE_THROWEXCEPTION, null);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append(" (type=").append(resourceType.getName());
        sb.append(", serviceid=")
                .append(serviceid)
                .append(", name='")
                .append(resourceName)
                .append("'");
        sb.append(", actions.size=")
                .append(actions.size() > 9 ? "" : " ")
                .append(actions.size())
                .append(")");
        return sb.toString();
    }

    public Service getService() {
        return service;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Class getResourceType() {
        return resourceType;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getActionSize() {
        return actions.size();
    }

    @Override
    public int compareTo(SncpServlet other) {
        if (!(other instanceof SncpServlet)) {
            return 1;
        }
        SncpServlet o = other;
        int rs;
        if (this.resourceType == null) {
            rs = o.resourceType == null ? 0 : -1;
        } else if (o.resourceType == null) {
            rs = 1;
        } else {
            rs = this.resourceType.getName().compareTo(o.resourceType.getName());
        }
        if (rs == 0) {
            if (this.resourceName == null) {
                rs = o.resourceName == null ? 0 : -1;
            } else if (o.resourceName == null) {
                rs = 1;
            } else {
                rs = this.resourceName.compareTo(o.resourceName);
            }
        }
        return rs;
    }

    protected ExecutorService getExecutor() {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            return ((WorkThread) thread).getWorkExecutor();
        }
        return ForkJoinPool.commonPool();
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof SncpServlet)) {
            return false;
        }
        return Objects.equals(getServiceid(), ((SncpServlet) obj).getServiceid());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(getServiceid());
    }
}
