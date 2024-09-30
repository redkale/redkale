/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.Date;
import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 * java.util.Date 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class DateSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Date> {

    public static final DateSimpledCoder instance = new DateSimpledCoder();

    @Override
    public void convertTo(W out, Date value) {
        out.writeLong(value == null ? 0L : value.getTime());
    }

    @Override
    public Date convertFrom(R in) {
        long t = in.readLong();
        return t == 0 ? null : new Date(t);
    }
}
