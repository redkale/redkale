/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;

/**
 * Dlong 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class Uint128SimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Uint128> {

    private static final ByteArraySimpledCoder bsSimpledCoder = ByteArraySimpledCoder.instance;

    public static final Uint128SimpledCoder instance = new Uint128SimpledCoder();

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(final W out, final Uint128 value) {
        if (value == null) {
            out.writeNull();
        } else {
            bsSimpledCoder.convertTo(out, value.getBytes());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uint128 convertFrom(R in) {
        byte[] bs = bsSimpledCoder.convertFrom(in);
        if (bs == null) {
            return null;
        }
        return Uint128.create(bs);
    }

    /**
     * Uint128 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class Uint128JsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, Uint128> {

        public static final Uint128JsonSimpledCoder instance = new Uint128JsonSimpledCoder();

        @Override
        public void convertTo(final W out, final Uint128 value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public Uint128 convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return Uint128.create(Utility.hexToBin(str));
        }
    }
}
