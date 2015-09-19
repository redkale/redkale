/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.stun;

import java.nio.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class StunPacket {

    private StunHeader header;

    private final List<StunAttribute> attributes = new ArrayList<>();

    public StunPacket(StunHeader header) {
        this.header = header;
    }

    public ByteBuffer encode(final ByteBuffer buffer) {
        int start = buffer.position();
        header.encode(buffer);
        final byte[] transactionid = header.getTransactionid();
        for (StunAttribute attr : attributes) {
            attr.encode(buffer, transactionid);
        }
        int end = buffer.position();
        buffer.putShort(start + 2, (short) (end - start - 20));
        return buffer;
    }

    public void addAttribute(StunAttribute attribute) {
        this.attributes.add(attribute);
    }

    public StunHeader getHeader() {
        return header;
    }

    public List<StunAttribute> getAttributes() {
        return attributes;
    }

}
