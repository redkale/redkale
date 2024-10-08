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
 * String 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class StringSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, String> {

    public static final StringSimpledCoder instance = new StringSimpledCoder();

    @Override
    public void convertTo(W out, String value) {
        out.writeString(value);
    }

    @Override
    public String convertFrom(R in) {
        return in.readString();
    }

    public static final class StandardStringSimpledCoder<R extends Reader, W extends Writer>
            extends SimpledCoder<R, W, String> {

        public static final StandardStringSimpledCoder instance = new StandardStringSimpledCoder();

        @Override
        public void convertTo(W out, String value) {
            out.writeStandardString(value);
        }

        @Override
        public String convertFrom(R in) {
            return in.readStandardString();
        }
    }
}
