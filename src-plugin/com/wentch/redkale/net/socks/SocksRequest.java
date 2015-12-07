/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import com.wentch.redkale.net.http.*;
import java.net.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public class SocksRequest extends HttpRequest {

    private boolean http;

    private short requestid;

    protected SocksRequest(HttpContext context) {
        super(context, null);
    }

    @Override
    protected int readHeader(ByteBuffer buffer) {
        if (buffer.get(0) > 0x05 && buffer.remaining() > 3) {
            this.http = true;
            return super.readHeader(buffer);
        }
        this.http = false;
        if (buffer.get() != 0x05) return -1;
        if (buffer.get() != 0x01) return -1;
        if (buffer.get() != 0x00) return -1;
        return 0;
    }

    protected InetSocketAddress parseSocketAddress() {
        return HttpRequest.parseSocketAddress(getRequestURI());
    }

    @Override
    protected InetSocketAddress getHostSocketAddress() {
        return super.getHostSocketAddress();
    }

    @Override
    protected AsyncConnection getChannel() {
        return super.getChannel();
    }

    @Override
    protected int readBody(ByteBuffer buffer) {
        return buffer.remaining();
    }

    @Override
    protected void prepare() {
        super.prepare();
    }

    @Override
    protected void recycle() {
        this.requestid = 0;
        this.http = false;
        super.recycle();
    }

    public short getRequestid() {
        return requestid;
    }

    public void setRequestid(short requestid) {
        this.requestid = requestid;
    }

    public boolean isHttp() {
        return http;
    }

    public void setHttp(boolean http) {
        this.http = http;
    }

}
