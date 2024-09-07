/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import org.redkale.annotation.Nullable;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <R> 请求对象
 * @param <P> message
 */
public class ClientResponse<R extends ClientRequest, P extends ClientResult> {

    // 服务端返回一个不存在的requestid，可能为null
    @Nullable
    protected R request;

    protected P message;

    protected Throwable cause;

    public ClientResponse() {}

    public ClientResponse(R request, P message) {
        this.request = request;
        this.message = message;
    }

    public ClientResponse(R request, Throwable exc) {
        this.request = request;
        this.cause = exc;
    }

    public Serializable getRequestid() {
        return request == null ? null : request.getRequestid();
    }

    public ClientResponse<R, P> success(R request, P message) {
        this.request = request;
        this.message = message;
        return this;
    }

    public ClientResponse<R, P> fail(R request, Throwable exc) {
        this.request = request;
        this.cause = exc;
        return this;
    }

    protected void prepare() {
        this.request = null;
        this.message = null;
        this.cause = null;
    }

    protected boolean recycle() {
        this.request = null;
        this.message = null;
        this.cause = null;
        return true;
    }

    public R getRequest() {
        return request;
    }

    public void setRequest(R request) {
        this.request = request;
    }

    public P getMessage() {
        return message;
    }

    public void setMessage(P message) {
        this.message = message;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public String toString() {
        if (cause != null) {
            return "{\"request\":" + request + ",\"exc\":" + cause + "}";
        }
        return "{\"request\":" + request + ",\"message\":" + message + "}";
    }

    boolean isError() {
        return false;
    }

    static class ClientErrorResponse<R extends ClientRequest, P extends ClientResult> extends ClientResponse<R, P> {

        public ClientErrorResponse(R request, Throwable exc) {
            super(request, exc);
        }

        @Override
        boolean isError() {
            return true;
        }
    }
}
