/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;
import java.util.Date;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class DateSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Date> {

    public static final DateSimpledCoder instance = new DateSimpledCoder();

    @Override
    public void convertTo(W out, Date value) {
        out.writeLong(value.getTime());
    }

    @Override
    public Date convertFrom(R in) {
        return new Date(in.readLong());
    }

}
