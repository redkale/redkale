/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

/**
 *
 * @author zhangjx
 */
public class NioWorkerThread extends NioEventLoop {

    public NioWorkerThread(String name) {
        super(name);
    }

    @Override
    protected void doLoopProcessing() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
