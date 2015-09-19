/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.stun;

import java.nio.*;

/**
 *
 * @author zhangjx
 */
public abstract class StunAttribute {

    public abstract StunAttribute decode(final ByteBuffer buffer, final byte[] transactionid);

    public abstract ByteBuffer encode(final ByteBuffer buffer, final byte[] transactionid);

    public abstract short getAttributeid();

    public abstract String getName();
}
