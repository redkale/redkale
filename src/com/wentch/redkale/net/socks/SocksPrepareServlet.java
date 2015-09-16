/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class SocksPrepareServlet  extends PrepareServlet<SocksRequest, SocksResponse> {

    private final HashMap<Short, SocksServlet> servletmaps = new HashMap<>();

    public SocksPrepareServlet() {
    }

    @Override
    public void init(Context context, AnyValue config) {
    }

    public void addSocksServlet(SocksServlet servlet, AnyValue conf) {
        servlet.conf = conf;
        this.servletmaps.put(servlet.getRequestid(), servlet);
    }

    // 28.[00,03,00,08,  21,12,a4,42,45,6f,4e,77,4e,47,71,55,32,37,77,39,    00,19,00,04,11,00,00,00]
    @Override
    public void execute(SocksRequest request, SocksResponse response) throws IOException {
        SocksServlet servlet = servletmaps.get(request.getRequestid());
        if (servlet != null) {
            servlet.execute(request, response);
        } else {
            response.finish();
        }
    }

}

