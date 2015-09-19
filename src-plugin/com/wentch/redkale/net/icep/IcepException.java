/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

/**
 *
 * @author zhangjx
 */
public class IcepException extends RuntimeException {

    public IcepException() {
        super();
    }

    public IcepException(String s) {
        super(s);
    }

    public IcepException(String message, Throwable cause) {
        super(message, cause);
    }

    public IcepException(Throwable cause) {
        super(cause);
    }
}
