/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public class SocksRequest extends Request {

    private short requestid;

    protected SocksRequest(SocksContext context) {
        super(context);
    }

    @Override
    protected int readHeader(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void prepare() {
    }

    @Override
    protected void recycle() {
        super.recycle();
    }

    public short getRequestid() {
        return requestid;
    }

    public void setRequestid(short requestid) {
        this.requestid = requestid;
    }

}
