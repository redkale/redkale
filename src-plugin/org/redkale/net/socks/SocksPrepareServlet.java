/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.util.AnyValue;
import org.redkale.net.Context;
import org.redkale.net.PrepareServlet;
import java.io.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class SocksPrepareServlet extends PrepareServlet<SocksRequest, SocksResponse> {

    private SocksServlet socksServlet = new SocksConnectServlet();

    private SocksProxyServlet proxyServlet = new SocksProxyServlet();

    public SocksPrepareServlet() {
    }

    @Override
    public void init(Context context, AnyValue config) {
        if (socksServlet != null) socksServlet.init(context, socksServlet.conf == null ? config : socksServlet.conf);
    }

    public void setSocksServlet(SocksServlet servlet, AnyValue conf) {
        servlet.conf = conf;
        if (servlet != null) this.socksServlet = servlet;
    }

    // 
    @Override
    public void execute(SocksRequest request, SocksResponse response) throws IOException {
        if (request.isHttp()) {
            proxyServlet.execute(request, response);
        } else {
            socksServlet.execute(request, response);
        }
    }

}
