/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.regex.*;
import org.redkale.convert.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public class PatternSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Pattern> {

    public static final PatternSimpledCoder instance = new PatternSimpledCoder();

    @Override
    public void convertTo(W out, Pattern value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeString(value.flags() + "," + value.pattern());
        }
    }

    @Override
    public Pattern convertFrom(R in) {
        String value = in.readString();
        if (value == null) return null;
        int pos = value.indexOf(',');
        return Pattern.compile(value.substring(pos + 1), Integer.parseInt(value.substring(0, pos)));
    }

}
