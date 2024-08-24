/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.Filter;
import org.redkale.util.AnyValue;

/**
 * HTTP 过滤器 <br>
 * 可通过{@link org.redkale.annotation.Priority}进行顺序设置
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.annotation.Priority
 * @author zhangjx
 */
public abstract class HttpFilter extends Filter<HttpContext, HttpRequest, HttpResponse> {

    // Server执行start后运行此方法
    protected void postStart(HttpContext context, AnyValue config) {}

    protected void setMethod(HttpRequest request, String method) {
        request.setMethod(method);
    }

    protected void setPath(HttpRequest request, String path) {
        request.setRequestPath(path);
    }

    protected void setBody(HttpRequest request, byte[] body) {
        request.updateBody(body);
    }

    protected void setRemoteAddr(HttpRequest request, String remoteAddr) {
        request.setRemoteAddr(remoteAddr);
    }

    protected void setLocale(HttpRequest request, String locale) {
        request.setLocale(locale);
    }

    protected <T> T setProperty(HttpRequest request, String name, T value) {
        return request.setProperty(name, value);
    }

    protected <T> T getProperty(HttpRequest request, String name) {
        return request.getProperty(name);
    }

    protected void removeProperty(HttpRequest request, String name) {
        request.removeProperty(name);
    }

    protected void addHeader(HttpRequest request, String name, String value) {
        request.addHeader(name, value);
    }

    protected void setHeader(HttpRequest request, String name, String value) {
        request.setHeader(name, value);
    }

    protected void removeHeader(HttpRequest request, String name) {
        request.removeHeader(name);
    }

    protected void setParameter(HttpRequest request, String name, String value) {
        request.setParameter(name, value);
    }

    protected void removeParameter(HttpRequest request, String name) {
        request.removeParameter(name);
    }

    protected void setFilter(HttpResponse response, HttpFilter filter) {
        response.setFilter(filter);
    }

    protected void thenEvent(HttpResponse response, HttpFilter filter) {
        response.thenEvent(filter);
    }
}
