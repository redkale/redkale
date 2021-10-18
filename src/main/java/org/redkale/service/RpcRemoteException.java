/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

/**
 * 供RPC协议使用
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class RpcRemoteException extends RuntimeException {

    public RpcRemoteException() {
        super();
    }

    public RpcRemoteException(String s) {
        super(s);
    }

    public RpcRemoteException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcRemoteException(Throwable cause) {
        super(cause);
    }
}
