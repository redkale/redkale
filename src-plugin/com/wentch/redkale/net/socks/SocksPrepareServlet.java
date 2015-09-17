/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;

/**
 *
 * @author zhangjx
 */
public final class SocksPrepareServlet extends PrepareServlet<SocksRequest, SocksResponse> {

    private SocksServlet servlet = new DefaultSocksServlet();

    public SocksPrepareServlet() {
    }

    @Override
    public void init(Context context, AnyValue config) {
        if (servlet != null) servlet.init(context, servlet.conf == null ? config : servlet.conf);
    }

    public void setSocksServlet(SocksServlet servlet, AnyValue conf) {
        servlet.conf = conf;
        if (servlet != null) this.servlet = servlet;
    }

    // 
    @Override
    public void execute(SocksRequest request, SocksResponse response) throws IOException {
        servlet.execute(request, response);
    }

}
