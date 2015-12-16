/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class ShortSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Short> {

    public static final ShortSimpledCoder instance = new ShortSimpledCoder();

    @Override
    public void convertTo(W out, Short value) {
        out.writeShort(value);
    }

    @Override
    public Short convertFrom(R in) {
        return in.readShort();
    }

}
