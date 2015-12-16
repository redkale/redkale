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
