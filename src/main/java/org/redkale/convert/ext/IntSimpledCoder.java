/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * int 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class IntSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Integer> {

    public static final IntSimpledCoder instance = new IntSimpledCoder();

    @Override
    public void convertTo(W out, Integer value) {
        out.writeInt(value);
    }

    @Override
    public Integer convertFrom(R in) {
        return in.readInt();
    }

    /**
     * int 的十六进制JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class IntHexJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, Integer> {

        public static final IntHexJsonSimpledCoder instance = new IntHexJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final Integer value) {
            if (value == null) {
                out.writeStandardString("0x0");
            } else {
                if (value < 0) {
                    throw new ConvertException("Negative values (" + value + ") are not supported");
                }
                out.writeStandardString("0x" + Integer.toHexString(value));
            }
        }

        @Override
        public Integer convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return 0;
            }
            try {
                if (str.length() > 2 && str.charAt(0) == '0' && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {
                    return Integer.parseInt(str.substring(2), 16);
                }
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
