/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;
import org.redkale.convert.Reader;
import java.math.BigInteger;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class BigIntegerSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, BigInteger> {

    public static final BigIntegerSimpledCoder instance = new BigIntegerSimpledCoder();

    @Override
    public void convertTo(W out, BigInteger value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        ByteArraySimpledCoder.instance.convertTo(out, value.toByteArray());
    }

    @Override
    public BigInteger convertFrom(R in) {
        byte[] bytes = ByteArraySimpledCoder.instance.convertFrom(in);
        return bytes == null ? null : new BigInteger(bytes);
    }

}
