/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.Filter;
import org.redkale.util.AnyValue;

/**
 * HTTP 过滤器  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class HttpFilter extends Filter<HttpContext, HttpRequest, HttpResponse> {

    //Server执行start后运行此方法
    public void postStart(HttpContext context, AnyValue config) {
    }

    protected void setMethod(HttpRequest request, String method) {
        request.setMethod(method);
    }

    protected void setRequestURI(HttpRequest request, String requestURI) {
        request.setRequestURI(requestURI);
    }

    protected void setRemoteAddr(HttpRequest request, String remoteAddr) {
        request.setRemoteAddr(remoteAddr);
    }

    protected void setLocale(HttpRequest request, String locale) {
        request.setLocale(locale);
    }

    protected void setParameter(HttpRequest request, String name, String value) {
        request.setParameter(name, value);
    }

    protected void setHeader(HttpRequest request, String name, String value) {
        request.setHeader(name, value);
    }

    protected void removeParameter(HttpRequest request, String name) {
        request.removeParameter(name);
    }

    protected void removeHeader(HttpRequest request, String name) {
        request.removeHeader(name);
    }

    protected void removeAttribute(HttpRequest request, String name) {
        request.removeAttribute(name);
    }

    protected void removeProperty(HttpRequest request, String name) {
        request.removeProperty(name);
    }
}
