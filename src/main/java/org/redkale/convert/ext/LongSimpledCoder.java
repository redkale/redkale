/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * long 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class LongSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Long> {

    public static final LongSimpledCoder instance = new LongSimpledCoder();

    @Override
    public void convertTo(W out, Long value) {
        out.writeLong(value);
    }

    @Override
    public Long convertFrom(R in) {
        return in.readLong();
    }

    /**
     * long 的十六进制JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class LongHexJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, Long> {

        public static final LongHexJsonSimpledCoder instance = new LongHexJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final Long value) {
            if (value == null) {
                out.writeStandardString("0x0");
            } else {
                if (value < 0) {
                    throw new ConvertException("Negative values (" + value + ") are not supported");
                }
                out.writeStandardString("0x" + Long.toHexString(value));
            }
        }

        @Override
        public Long convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return 0L;
            }
            try {
                if (str.length() > 2 && str.charAt(0) == '0' && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {
                    return Long.parseLong(str.substring(2), 16);
                }
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
    }
}
