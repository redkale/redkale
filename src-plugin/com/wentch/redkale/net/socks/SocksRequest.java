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
        if (buffer.get() != 0x05) return -1;
        if (buffer.get() != 0x01) return -1;
        if (buffer.get() != 0x00) return -1;
        return 0;
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
    }

    @Override
    protected void prepare() {
    }

    @Override
    protected void recycle() {
        this.requestid = 0;
        super.recycle();
    }

    public short getRequestid() {
        return requestid;
    }

    public void setRequestid(short requestid) {
        this.requestid = requestid;
    }

}
