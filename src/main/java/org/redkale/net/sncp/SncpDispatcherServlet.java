/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.redkale.net.DispatcherServlet;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpDispatcherServlet
        extends DispatcherServlet<Uint128, SncpContext, SncpRequest, SncpResponse, SncpServlet> {

    private final ReentrantLock updateLock = new ReentrantLock();

    protected SncpDispatcherServlet() {
        super();
    }

    @Override
    public void addServlet(SncpServlet servlet, Object attachment, AnyValue conf, Uint128... mappings) {
        updateLock.lock();
        try {
            for (SncpServlet s : getServlets()) {
                if (s.service == servlet.service) {
                    throw new SncpException(s.service + " repeat addSncpServlet");
                }
            }
            setServletConf(servlet, conf);
            putMapping(servlet.getServiceid(), servlet);
            putServlet(servlet);
        } finally {
            updateLock.unlock();
        }
    }

    public <T> SncpServlet removeSncpServlet(Service service) {
        SncpServlet rs = null;
        updateLock.lock();
        try {
            for (SncpServlet servlet : getServlets()) {
                if (servlet.service == service) {
                    rs = servlet;
                    break;
                }
            }
            if (rs != null) {
                removeMapping(rs);
                removeServlet(rs);
            }
        } finally {
            updateLock.unlock();
        }
        return rs;
    }

    @Override
    public void init(SncpContext context, AnyValue config) {
        if (application != null && application.isCompileMode()) {
            return;
        }
        super.init(context, config); // 必须要执行
        getServlets().forEach(s -> s.init(context, getServletConf(s)));
    }

    @Override
    public void destroy(SncpContext context, AnyValue config) {
        super.destroy(context, config); // 必须要执行
        getServlets().forEach(s -> s.destroy(context, getServletConf(s)));
    }

    @Override
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        try {
            if (request.isPing()) {
                ByteArray array = request.getTempByteArray();
                int headerSize = SncpHeader.calcHeaderSize(request);
                array.putPlaceholder(headerSize);
                response.writeHeader(array, 0, 0);
                response.finish(array.getBytes());
                return;
            }
            SncpServlet servlet = mappingServlet(request.getHeader().getServiceid());
            if (servlet == null) {
                response.finish(SncpResponse.RETCODE_ILLSERVICEID, null); // 无效serviceid
            } else {
                servlet.execute(request, response);
            }
        } catch (Throwable e) {
            request.getContext()
                    .getLogger()
                    .log(Level.WARNING, "Dispatch servlet occur exception. request = " + request, e);
            response.finishError(e);
        }
    }
}
