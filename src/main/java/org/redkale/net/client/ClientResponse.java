/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;

/**
 *
 * @author zhangjx
 * @param <P> message
 */
public class ClientResponse<P> {

    protected ClientRequest request;

    protected P message;

    protected Throwable exc;

    public ClientResponse() {
    }
    
    public ClientResponse(ClientRequest request, P message) {
        this.request = request;
        this.message = message;
    }

    public ClientResponse(ClientRequest request, Throwable exc) {
        this.request = request;
        this.exc = exc;
    }

    public Serializable getRequestid() {
        return request == null ? null : request.getRequestid();
    }

    public ClientResponse<P> set(ClientRequest request, P message) {
        this.request = request;
        this.message = message;
        return this;
    }

    public ClientResponse<P> set(ClientRequest request, Throwable exc) {
        this.request = request;
        this.exc = exc;
        return this;
    }

    protected void prepare() {
        this.request = null;
        this.message = null;
        this.exc = null;
    }

    protected boolean recycle() {
        this.request = null;
        this.message = null;
        this.exc = null;
        return true;
    }

    public ClientRequest getRequest() {
        return request;
    }

    public void setRequest(ClientRequest request) {
        this.request = request;
    }

    public P getMessage() {
        return message;
    }

    public void setMessage(P message) {
        this.message = message;
    }

    public Throwable getExc() {
        return exc;
    }

    public void setExc(Throwable exc) {
        this.exc = exc;
    }

    @Override
    public String toString() {
        if (exc != null) {
            return "{\"exc\":" + exc + "}";
        }
        return "{\"message\":" + message + "}";
    }

}
