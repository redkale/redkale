/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.ext;

import com.wentch.redkale.convert.Reader;
import com.wentch.redkale.convert.Writer;
import com.wentch.redkale.convert.SimpledCoder;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public class TypeSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Class> {

    public static final TypeSimpledCoder instance = new TypeSimpledCoder();

    @Override
    public void convertTo(final W out, final Class value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeSmallString(value.getName());
        }
    }

    @Override
    public Class convertFrom(R in) {
        String str = in.readSmallString();
        if (str == null) return null;
        try {
            return Class.forName(str);
        } catch (Exception e) {
            return null;
        }
    }

}
