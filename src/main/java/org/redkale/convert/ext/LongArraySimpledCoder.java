/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.stream.LongStream;
import org.redkale.convert.*;

/**
 * long[] 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
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
        out.writeArrayB(values.length, LongSimpledCoder.instance, values);
        boolean flag = false;
        for (long v : values) {
            if (flag) {
                out.writeArrayMark();
            }
            out.writeLong(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public long[] convertFrom(R in) {
        if (!in.readArrayB(LongSimpledCoder.instance)) {
            return null;
        }
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
    }

    public static final class LongStreamSimpledCoder<R extends Reader, W extends Writer>
            extends SimpledCoder<R, W, LongStream> {

        public static final LongStreamSimpledCoder instance = new LongStreamSimpledCoder();

        @Override
        @SuppressWarnings("unchecked")
        public void convertTo(W out, LongStream values) {
            if (values == null) {
                out.writeNull();
                return;
            }
            LongArraySimpledCoder.instance.convertTo(out, values.toArray());
        }

        @Override
        @SuppressWarnings("unchecked")
        public LongStream convertFrom(R in) {
            long[] value = LongArraySimpledCoder.instance.convertFrom(in);
            return value == null ? null : LongStream.of(value);
        }
    }
}
