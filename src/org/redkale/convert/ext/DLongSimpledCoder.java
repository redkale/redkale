/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.Reader;
import org.redkale.convert.Writer;
import org.redkale.convert.SimpledCoder;
import org.redkale.util.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class DLongSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, DLong> {

    private static final ByteArraySimpledCoder bsSimpledCoder = ByteArraySimpledCoder.instance;

    public static final DLongSimpledCoder instance = new DLongSimpledCoder();

    @Override
    public void convertTo(final W out, final DLong value) {
        if (value == null) {
            out.writeNull();
        } else {
            bsSimpledCoder.convertTo(out, value.directBytes());
        }
    }

    @Override
    public DLong convertFrom(R in) {
        byte[] bs = bsSimpledCoder.convertFrom(in);
        if (bs == null) return null;
        return DLong.create(bs);
    }

}
