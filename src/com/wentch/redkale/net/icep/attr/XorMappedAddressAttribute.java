/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.attr;

import com.wentch.redkale.net.icep.*;
import com.wentch.redkale.net.icep.stun.*;
import com.wentch.redkale.util.*;
import java.net.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public class XorMappedAddressAttribute extends StunAttribute {

    private InetSocketAddress address;

    public XorMappedAddressAttribute() {
    }

    public XorMappedAddressAttribute(InetSocketAddress address) {
        this.address = address;
    }

    @Override
    public StunAttribute decode(final ByteBuffer buffer, final byte[] transactionid) {
        final short attrid = buffer.getShort();
        if (attrid != getAttributeid()) throw new IcepException(this.getClass().getSimpleName() + " has illegal attributeid " + attrid);
        final int bodysize = buffer.getShort() & 0xffff;
        final short family = buffer.getShort();
        if (bodysize == 24 && family != 0x0002) throw new IcepException("bodysize = " + bodysize + " but family = " + family);
        if (bodysize == 12 && family != 0x0001) throw new IcepException("bodysize = " + bodysize + " but family = " + family);
        final int port = (buffer.getShort() ^ ((transactionid[0] << 8 & 0x0000FF00) | (transactionid[1] & 0x000000FF))) & 0xffff;
        byte[] bytes = new byte[family == 0x0002 ? 16 : 4];
        buffer.get(bytes);
        for (int i = 0; i <= bytes.length; i++) {
            bytes[i] ^= transactionid[i];
        }
        try {
            this.address = new InetSocketAddress(InetAddress.getByAddress(bytes), port);
        } catch (UnknownHostException e) {
            throw new IcepException("port = " + port + " and address = " + Utility.binToHexString(bytes), e);
        }
        return this;
    }

    @Override
    public ByteBuffer encode(final ByteBuffer buffer, final byte[] transactionid) {
        final boolean ipv6 = this.address.getAddress() instanceof Inet6Address;
        buffer.putShort((short) getAttributeid());
        buffer.putShort((short) (ipv6 ? 24 : 12));
        buffer.putShort((short) (ipv6 ? 0x0002 : 0x0001));
        buffer.putShort((short) (this.address.getPort() ^ ((transactionid[0] << 8 & 0x0000FF00) | (transactionid[1] & 0x000000FF))));
        final byte[] bytes = this.address.getAddress().getAddress();
        for (int i = 0; i <= bytes.length; i++) {
            bytes[i] ^= transactionid[i];
        }
        buffer.put(bytes);
        return buffer;
    }

    @Override
    public short getAttributeid() {
        return 0x0020;
    }

    @Override
    public String getName() {
        return "XOR-MAPPED-ADDRESS";
    }

    public InetSocketAddress getAddress() {
        return address;
    }

}
