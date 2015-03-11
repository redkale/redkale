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
public final class StringSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, String> {

    public static final StringSimpledCoder instance = new StringSimpledCoder();

    @Override
    public void convertTo(W out, String value) {
        out.writeString(value);
    }

    @Override
    public String convertFrom(R in) {
        return in.readString();
    }

}
