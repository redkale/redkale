/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.stream.IntStream;
import org.redkale.convert.*;

/**
 * int[] 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class IntArraySimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, int[]> {

    public static final IntArraySimpledCoder instance = new IntArraySimpledCoder();

    @Override
    public void convertTo(W out, int[] values) {
        if (values == null) {
            out.writeNull();
            return;
        }
        out.writeArrayB(values.length, IntSimpledCoder.instance, values);
        boolean flag = false;
        for (int v : values) {
            if (flag) {
                out.writeArrayMark();
            }
            out.writeInt(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public int[] convertFrom(R in) {
        if (!in.readArrayB(IntSimpledCoder.instance)) {
            return null;
        }
        int size = 0;
        int[] data = new int[8];
        while (in.hasNext()) {
            if (size >= data.length) {
                int[] newdata = new int[data.length + 4];
                System.arraycopy(data, 0, newdata, 0, size);
                data = newdata;
            }
            data[size++] = in.readInt();
        }
        in.readArrayE();
        int[] newdata = new int[size];
        System.arraycopy(data, 0, newdata, 0, size);
        return newdata;
    }

    public static final class IntStreamSimpledCoder<R extends Reader, W extends Writer>
            extends SimpledCoder<R, W, IntStream> {

        public static final IntStreamSimpledCoder instance = new IntStreamSimpledCoder();

        @Override
        @SuppressWarnings("unchecked")
        public void convertTo(W out, IntStream values) {
            if (values == null) {
                out.writeNull();
                return;
            }
            IntArraySimpledCoder.instance.convertTo(out, values.toArray());
        }

        @Override
        @SuppressWarnings("unchecked")
        public IntStream convertFrom(R in) {
            int[] value = IntArraySimpledCoder.instance.convertFrom(in);
            return value == null ? null : IntStream.of(value);
        }
    }
}
