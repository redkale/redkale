/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import java.nio.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
public interface IcepCoder<T> {

    public T decode(final ByteBuffer buffer);

    public ByteBuffer encode(final ByteBuffer buffer);
}
