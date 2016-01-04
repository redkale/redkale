/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.net.PrepareServlet;
import org.redkale.net.Context;
import org.redkale.util.AnyValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.redkale.util.*;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
public class SncpPrepareServlet extends PrepareServlet<SncpRequest, SncpResponse> {

    private static final ByteBuffer pongBuffer = ByteBuffer.wrap("PONG".getBytes()).asReadOnlyBuffer();

    private final Map<DLong, Map<DLong, SncpServlet>> maps = new HashMap<>();

    private final Map<DLong, SncpServlet> singlemaps = new HashMap<>();

    public void addSncpServlet(SncpServlet servlet) {
        if (servlet.getNameid() == DLong.ZERO) {
            synchronized (singlemaps) {
                singlemaps.put(servlet.getServiceid(), servlet);
            }
        } else {
            synchronized (maps) {
                Map<DLong, SncpServlet> m = maps.get(servlet.getServiceid());
                if (m == null) {
                    m = new HashMap<>();
                    maps.put(servlet.getServiceid(), m);
                }
                m.put(servlet.getNameid(), servlet);
            }
        }
    }

    public List<SncpServlet> getSncpServlets() {
        ArrayList<SncpServlet> list = new ArrayList<>(singlemaps.values());
        maps.values().forEach(x -> list.addAll(x.values()));
        return list;
    }

    @Override
    public void init(Context context, AnyValue config) {
        Collection<Map<DLong, SncpServlet>> values = this.maps.values();
        values.stream().forEach((en) -> {
            en.values().stream().forEach(s -> s.init(context, s.conf));
        });
    }

    @Override
    public void destroy(Context context, AnyValue config) {
        Collection<Map<DLong, SncpServlet>> values = this.maps.values();
        values.stream().forEach((en) -> {
            en.values().stream().forEach(s -> s.destroy(context, s.conf));
        });
    }

    @Override
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        if (request.isPing()) {
            response.finish(pongBuffer.duplicate());
            return;
        }
        SncpServlet servlet;
        if (request.getNameid() == DLong.ZERO) {
            servlet = singlemaps.get(request.getServiceid());
            if (servlet == null) {
                response.finish(SncpResponse.RETCODE_ILLSERVICEID, null);  //无效serviceid
                return;
            }
        } else {
            Map<DLong, SncpServlet> m = maps.get(request.getServiceid());
            if (m == null) {
                response.finish(SncpResponse.RETCODE_ILLSERVICEID, null);  //无效serviceid
                return;
            }
            servlet = m.get(request.getNameid());
        }
        if (servlet == null) {
            response.finish(SncpResponse.RETCODE_ILLNAMEID, null);  //无效nameid
        } else {
            servlet.execute(request, response);
        }
    }

}
