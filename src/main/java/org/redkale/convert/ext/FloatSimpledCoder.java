/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 * float 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class FloatSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Float> {

    public static final FloatSimpledCoder instance = new FloatSimpledCoder();

    @Override
    public void convertTo(W out, Float value) {
        out.writeFloat(value);
    }

    @Override
    public Float convertFrom(R in) {
        return in.readFloat();
    }
}
