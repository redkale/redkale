/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.math.BigInteger;
import java.util.Objects;
import org.redkale.convert.*;

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

}
