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
public class MappedAddressAttribute extends StunAttribute {

    protected InetSocketAddress address;

    public MappedAddressAttribute() {
    }

    public MappedAddressAttribute(InetSocketAddress address) {
        this.address = address;
    }

    /**
     0000   b8 2a 72 c8 7c 7e 08 57 00 60 3b f6 08 00 45 00  .*r.|~.W.`;...E.
     0010   00 78 00 00 40 00 30 11 6f 1a d8 5d f6 12 0a 1c  .x..@.0.o..]....
     0020   02 cf 0d 96 e2 ba 00 64 f0 b3 
     01 01 00 48     21 12 a4 42 50 38 69 55 2b 65 78 66 4e 68 76 32      00 01  .BP8iU+exfNhv2..
     0040   00 08 00 01 e2 ba 71 5c fb ed 00 04 00 08 00 01  ......q\........
     0050   0d 96 d8 5d f6 12 00 05 00 08 00 01 0d 97 d8 5d  ...]...........]
     0060   f6 0f 80 20 00 08 00 01 c3 a8 50 4e 5f af 80 22  ... ......PN_.."
     0070   00 14 56 6f 76 69 64 61 2e 6f 72 67 20 30 2e 39  ..Vovida.org 0.9
     0080   37 2d 43 50 43 00                                7-CPC.
     @param args
     @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        final byte[] transactionid = format("21 12 a4 42 50 38 69 55 2b 65 78 66 4e 68 76 32");
        byte[] attrs = format("80 20 00 08 00 01 c3 a8 50 4e 5f af");
        XorMappedAddressAttribute attr = new XorMappedAddressAttribute().decode(ByteBuffer.wrap(attrs), transactionid);
        System.out.println(attr);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        attr.encode(buffer, transactionid);
        buffer.flip();
        Utility.println(null, buffer);
    }

    protected static byte[] format(String string) {
        String[] strs = string.split("\\s+");
        byte[] bs = new byte[strs.length];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = (byte) Integer.parseInt(strs[i], 16);
        }
        return bs;
    }

    @Override
    public MappedAddressAttribute decode(final ByteBuffer buffer, final byte[] transactionid) {
        final short attrid = (short) (buffer.getShort() & 0x00ff);
        if (attrid != getAttributeid()) throw new IcepException(this.getClass().getSimpleName() + " has illegal attributeid " + attrid);
        final int bodysize = buffer.getShort() & 0xffff;
        final short family = buffer.getShort();
        if (family == 0x0001 && bodysize != 8) throw new IcepException("family = " + family + " but bodysize = " + bodysize);
        if (family == 0x0002 && bodysize != 20) throw new IcepException("family = " + family + " but bodysize = " + bodysize);
        final int port = buffer.getShort() & 0xffff;
        byte[] bytes = new byte[family == 0x0002 ? 16 : 4];
        buffer.get(bytes);
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
        buffer.putShort((short) (ipv6 ? 20 : 8));
        buffer.putShort((short) (ipv6 ? 0x0002 : 0x0001));
        buffer.putShort((short) this.address.getPort());
        final byte[] bytes = this.address.getAddress().getAddress();
        buffer.put(bytes);
        return buffer;
    }

    @Override
    public short getAttributeid() {
        return 0x0001;
    }

    @Override
    public String getName() {
        return "MAPPED-ADDRESS";
    }

    public InetSocketAddress getInetSocketAddress() {
        return address;
    }

    @Override
    public String toString() {
        return getName() + ":" + address;
    }

}
