/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.rtp;

import com.wentch.redkale.net.icep.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public class RtpPacket implements IcepCoder<RtpPacket> {

    private RtpHeader header;

    private byte[] payload;

    @Override
    public RtpPacket decode(final ByteBuffer buffer) {
        if (header == null) this.header = new RtpHeader();
        this.header.decode(buffer);
        this.payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return this;
    }

    @Override
    public ByteBuffer encode(final ByteBuffer buffer) {
        this.header.encode(buffer).put(payload);
        return buffer;
    }

}
