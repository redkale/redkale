/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.Servlet;

/**
 *
 * @author zhangjx
 */
public abstract class HttpServlet implements Servlet<HttpRequest, HttpResponse> {

    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public final int hashCode() {
        return this.getClass().hashCode();
    }

}
