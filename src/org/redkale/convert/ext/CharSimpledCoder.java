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
public final class CharSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Character> {

    public static final CharSimpledCoder instance = new CharSimpledCoder();

    @Override
    public void convertTo(W out, Character value) {
        out.writeChar(value);
    }

    @Override
    public Character convertFrom(R in) {
        return in.readChar();
    }

}
