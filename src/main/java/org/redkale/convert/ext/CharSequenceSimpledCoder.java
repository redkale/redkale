/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 * CharSequence 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class CharSequenceSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, CharSequence> {

    public static final CharSequenceSimpledCoder instance = new CharSequenceSimpledCoder();

    @Override
    public void convertTo(W out, CharSequence value) {
        out.writeString(value == null ? null : value.toString());
    }

    @Override
    public CharSequence convertFrom(R in) {
        return in.readString();
    }

    public static class StringBuilderSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, StringBuilder> {

        public static final StringBuilderSimpledCoder instance = new StringBuilderSimpledCoder();

        @Override
        public void convertTo(W out, StringBuilder value) {
            out.writeString(value == null ? null : value.toString());
        }

        @Override
        public StringBuilder convertFrom(R in) {
            String rs = in.readString();
            return rs == null ? null : new StringBuilder(rs);
        }
    }
}
