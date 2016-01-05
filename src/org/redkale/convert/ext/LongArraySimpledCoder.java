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
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class LongArraySimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, long[]> {

    public static final LongArraySimpledCoder instance = new LongArraySimpledCoder();

    @Override
    public void convertTo(W out, long[] values) {
        if (values == null) {
            out.writeNull();
            return;
        }
        out.writeArrayB(values.length);
        boolean flag = false;
        for (long v : values) {
            if (flag) out.writeArrayMark();
            out.writeLong(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public long[] convertFrom(R in) {
        int len = in.readArrayB();
        if (len == Reader.SIGN_NULL) return null;
        if (len == Reader.SIGN_NOLENGTH) {
            int size = 0;
            long[] data = new long[8];
            while (in.hasNext()) {
                if (size >= data.length) {
                    long[] newdata = new long[data.length + 4];
                    System.arraycopy(data, 0, newdata, 0, size);
                    data = newdata;
                }
                data[size++] = in.readLong();
            }
            in.readArrayE();
            long[] newdata = new long[size];
            System.arraycopy(data, 0, newdata, 0, size);
            return newdata;
        } else {
            long[] values = new long[len];
            for (int i = 0; i < values.length; i++) {
                values[i] = in.readLong();
            }
            in.readArrayE();
            return values;
        }
    }

}
