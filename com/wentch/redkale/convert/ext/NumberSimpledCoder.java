/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.ext;

import com.wentch.redkale.convert.Reader;
import com.wentch.redkale.convert.SimpledCoder;
import com.wentch.redkale.convert.Writer;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class NumberSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Number> {

    public static final NumberSimpledCoder instance = new NumberSimpledCoder();

    @Override
    public void convertTo(W out, Number value) {
        out.writeLong(value == null ? 0L : value.longValue());
    }

    @Override
    public Number convertFrom(R in) {
        return in.readLong();
    }

}
