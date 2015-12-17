/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public class CharSequenceSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, CharSequence> {

    public static final CharSequenceSimpledCoder instance = new CharSequenceSimpledCoder();

    @Override
    public void convertTo(W out, CharSequence value) {
        out.writeString(value == null ? null : value.toString());
    }

    @Override
    public CharSequence convertFrom(R in) {
        return in.readString();
    }
}
