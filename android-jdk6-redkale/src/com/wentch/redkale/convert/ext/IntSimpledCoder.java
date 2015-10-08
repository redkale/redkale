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
public final class IntSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Integer> {

    public static final IntSimpledCoder instance = new IntSimpledCoder();

    @Override
    public void convertTo(W out, Integer value) {
        out.writeInt(value);
    }

    @Override
    public Integer convertFrom(R in) {
        return in.readInt();
    }

}
