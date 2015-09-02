/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.*;
import com.wentch.redkale.net.icep.stun.*;
import com.wentch.redkale.util.*;
import java.net.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public class IcepRequest extends Request {

    private int requestid;

    private StunPacket stunPacket;

    protected IcepRequest(IcepContext context) {
        super(context);
    }

    //  20.[00,01,00,00,21,12,a4,42,63,6a,30,65,62,43,44,57,67,76,68,58]
    @Override
    protected int readHeader(ByteBuffer buffer) {
        Utility.println(Utility.now() + "-------" + getRemoteAddress() + "   ", buffer);
        if (buffer.remaining() < 20) return -1;
        this.requestid = buffer.getShort();
        short typeid = (short) ((this.requestid >> 16) & 0xffff);
        short actionid = (short) (this.requestid & 0xffff);
        byte[] bytes = new byte[16];
        buffer.get(bytes);
        StunHeader header = new StunHeader(typeid, actionid, bytes);
        this.stunPacket = new StunPacket(header);
        return -1;
    }

    public static void main(String[] args) throws Exception {
        int a = 0xabcd0000;
        System.out.println(Integer.toHexString((a >> 16) & 0xffff));
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void recycle() {

    }

    public int getRequestid() {
        return requestid;
    }

    public StunPacket getStunPacket() {
        return stunPacket;
    }

}
