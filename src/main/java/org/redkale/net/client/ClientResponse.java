/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

/**
 *
 * @author zhangjx
 * @param <P> message
 */
public class ClientResponse<P> {

    protected P message;

    protected Throwable exc;

    public ClientResponse(P result) {
        this.message = result;
    }

    public ClientResponse(Throwable exc) {
        this.exc = exc;
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
