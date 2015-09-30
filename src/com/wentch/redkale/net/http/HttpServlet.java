/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.Servlet;
import com.wentch.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class HttpServlet implements Servlet<HttpRequest, HttpResponse<HttpRequest>> {

    AnyValue conf; //当前HttpServlet的配置

    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public final int hashCode() {
        return this.getClass().hashCode();
    }

}
