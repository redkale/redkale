/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 * 文件 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class ThrowableSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Throwable> {

    public static final ThrowableSimpledCoder instance = new ThrowableSimpledCoder();

    @Override
    public void convertTo(W out, Throwable value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeString(value.toString());
        }
    }

    @Override
    public Throwable convertFrom(R in) {
        String value = in.readString();
        if (value == null) return null;
        return new Exception(value);
    }

}
