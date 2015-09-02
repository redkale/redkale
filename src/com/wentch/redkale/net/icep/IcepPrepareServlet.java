/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class IcepPrepareServlet extends PrepareServlet<IcepRequest, IcepResponse> {

    private final HashMap<Integer, IcepServlet> servletmaps = new HashMap<>();

    public IcepPrepareServlet() {
        this.servletmaps.put(0x0001, new BindingIcepServlet());
    }

    @Override
    public void init(Context context, AnyValue config) {
    }

    // 28.[00,03,00,08,  21,12,a4,42,45,6f,4e,77,4e,47,71,55,32,37,77,39,    00,19,00,04,11,00,00,00]
    @Override
    public void execute(IcepRequest request, IcepResponse response) throws IOException {
        IcepServlet servlet = servletmaps.get(request.getRequestid());
        if (servlet != null) {
            servlet.execute(request, response);
        } else {
            response.finish();
        }
    }

}
