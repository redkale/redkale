/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.math.BigInteger;
import java.util.Objects;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * BigInteger 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class BigIntegerSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, BigInteger> {

    public static final BigIntegerSimpledCoder instance = new BigIntegerSimpledCoder();

    protected final SimpledCoder<R, W, byte[]> bsSimpledCoder;

    protected BigIntegerSimpledCoder() {
        this.bsSimpledCoder = ByteArraySimpledCoder.instance;
    }

    public BigIntegerSimpledCoder(SimpledCoder<R, W, byte[]> bSimpledCoder) {
        this.bsSimpledCoder = Objects.requireNonNull(bSimpledCoder);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(W out, BigInteger value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        bsSimpledCoder.convertTo(out, value.toByteArray());
    }

    @Override
    @SuppressWarnings("unchecked")
    public BigInteger convertFrom(R in) {
        byte[] bytes = bsSimpledCoder.convertFrom(in);
        return bytes == null ? null : new BigInteger(bytes);
    }

    /**
     * BigInteger 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigIntegerJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigInteger> {

        public static final BigIntegerJsonSimpledCoder instance = new BigIntegerJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigInteger value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public BigInteger convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            return new BigInteger(str);
        }
    }

    /**
     * BigInteger 的十六进制JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigIntegerHexJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigInteger> {

        public static final BigIntegerHexJsonSimpledCoder instance = new BigIntegerHexJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigInteger value) {
            if (value == null) {
                out.writeNull();
            } else {
                String s = value.toString(16);
                out.writeStandardString(s.charAt(0) == '-' ? ("-0x" + s.substring(1)) : ("0x" + s));
            }
        }

        @Override
        public BigInteger convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            if (str.length() > 2) {
                if (str.charAt(0) == '0' && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {
                    return new BigInteger(str.substring(2), 16);
                } else if (str.charAt(0) == '-'
                        && str.length() > 3
                        && str.charAt(1) == '0'
                        && (str.charAt(2) == 'x' || str.charAt(2) == 'X')) {
                    return new BigInteger("-" + str.substring(3), 16);
                }
            }
            return new BigInteger(str);
        }
    }
}
