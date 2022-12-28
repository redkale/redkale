/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

/**
 *
 * @author zhangjx
 * @param <P> result
 */
public class ClientResult<P> {

    protected P result;

    protected Throwable exc;

    public ClientResult(P result) {
        this.result = result;
    }

    public ClientResult(Throwable exc) {
        this.exc = exc;
    }

    public P getResult() {
        return result;
    }

    public void setResult(P result) {
        this.result = result;
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
        return "{\"result\":" + result + "}";
    }
}
