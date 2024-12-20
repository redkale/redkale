/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.nio.ByteBuffer;
import org.redkale.convert.*;

/**
 * ByteBuffer 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class ByteBufferSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, ByteBuffer> {

    public static final ByteBufferSimpledCoder instance = new ByteBufferSimpledCoder();

    @Override
    public void convertTo(W out, ByteBuffer value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        out.writeArrayB(value.remaining(), ByteSimpledCoder.instance, value);
        boolean flag = false;
        for (byte v : value.array()) {
            if (flag) {
                out.writeArrayMark();
            }
            out.writeByte(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public ByteBuffer convertFrom(R in) {
        if (!in.readArrayB(ByteSimpledCoder.instance)) {
            return null;
        }
        int size = 0;
        byte[] data = new byte[8];
        while (in.hasNext()) {
            if (size >= data.length) {
                byte[] newdata = new byte[data.length + 4];
                System.arraycopy(data, 0, newdata, 0, size);
                data = newdata;
            }
            data[size++] = in.readByte();
        }
        in.readArrayE();
        return ByteBuffer.wrap(data, 0, size);
    }
}
