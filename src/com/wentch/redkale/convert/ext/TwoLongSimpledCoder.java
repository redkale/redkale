/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.ext;

import com.wentch.redkale.convert.Reader;
import com.wentch.redkale.convert.Writer;
import com.wentch.redkale.convert.SimpledCoder;
import com.wentch.redkale.util.TwoLong;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class TwoLongSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, TwoLong> {

    public static final TwoLongSimpledCoder instance = new TwoLongSimpledCoder();

    @Override
    public void convertTo(final W out, final TwoLong value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeSmallString(value.getFirst() + "_" + value.getSecond());
        }
    }

    @Override
    public TwoLong convertFrom(R in) {
        String str = in.readString();
        if (str == null) return null;
        int pos = str.indexOf('_');
        return new TwoLong(Long.parseLong(str.substring(0, pos)), Long.parseLong(str.substring(pos + 1)));
    }

}
