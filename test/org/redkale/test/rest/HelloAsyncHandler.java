/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import org.redkale.util.AsyncHandler;

/**
 *
 * @author zhangjx
 */
public class HelloAsyncHandler implements AsyncHandler {

    @Override
    public void completed(Object result, Object attachment) {
        System.out.println("-----HelloAsyncHandler--------result : " + result + ", attachment: " + attachment);
    }

    @Override
    public void failed(Throwable exc, Object attachment) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
