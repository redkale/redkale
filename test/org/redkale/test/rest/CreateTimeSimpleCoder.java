/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import java.text.SimpleDateFormat;
import org.redkale.convert.*;

/**
 *
 * @author zhangjx
 * @param <R> R
 * @param <W> W
 */
public class CreateTimeSimpleCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Long> {

    private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void convertTo(W out, Long value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeString(format.format(new java.util.Date(value)));
        }
    }

    @Override
    public Long convertFrom(R in) {
        String val = in.readString();
        if (val == null) return 0L;
        try {
            return format.parse(val).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }
}
