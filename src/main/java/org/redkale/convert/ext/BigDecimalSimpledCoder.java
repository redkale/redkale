/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.math.BigDecimal;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.util.Utility;

/**
 * BigDecimal 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class BigDecimalSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, BigDecimal> {

    public static final BigDecimalSimpledCoder instance = new BigDecimalSimpledCoder();

    @Override
    public void convertTo(W out, BigDecimal value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        out.writeStandardString(value.toString());
    }

    @Override
    public BigDecimal convertFrom(R in) {
        String value = in.readStandardString();
        if (value == null) {
            return null;
        }
        return new BigDecimal(Utility.charArray(value));
    }

    /**
     * BigDecimal 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigDecimalJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigDecimal> {

        public static final BigDecimalJsonSimpledCoder instance = new BigDecimalJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigDecimal value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public BigDecimal convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            return new BigDecimal(str);
        }
    }
}
