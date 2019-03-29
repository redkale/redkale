/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 * String 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class StringConvertWrapperSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, StringConvertWrapper> {

    public static final StringConvertWrapperSimpledCoder instance = new StringConvertWrapperSimpledCoder();

    @Override
    public void convertTo(W out, StringConvertWrapper value) {
        out.writeWrapper(value);
    }

    @Override
    public StringConvertWrapper convertFrom(R in) {
        return new StringConvertWrapper(in.readString());
    }

}
