/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 * byte[] 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class ByteArraySimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, byte[]> {

    public static final ByteArraySimpledCoder instance = new ByteArraySimpledCoder();

    @Override
    public void convertTo(W out, byte[] values) {
        out.writeByteArray(values);
    }

    @Override
    public byte[] convertFrom(R in) {
        return in.readByteArray();
    }

}
