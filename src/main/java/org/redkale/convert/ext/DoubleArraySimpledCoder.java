/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.stream.DoubleStream;
import org.redkale.convert.*;

/**
 * double[] 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class DoubleArraySimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, double[]> {

    public static final DoubleArraySimpledCoder instance = new DoubleArraySimpledCoder();

    @Override
    public void convertTo(W out, double[] values) {
        if (values == null) {
            out.writeNull();
            return;
        }
        out.writeArrayB(values.length, DoubleSimpledCoder.instance, values);
        boolean flag = false;
        for (double v : values) {
            if (flag) {
                out.writeArrayMark();
            }
            out.writeDouble(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public double[] convertFrom(R in) {
        if (!in.readArrayB(DoubleSimpledCoder.instance)) {
            return null;
        }
        int size = 0;
        double[] data = new double[8];
        while (in.hasNext()) {
            if (size >= data.length) {
                double[] newdata = new double[data.length + 4];
                System.arraycopy(data, 0, newdata, 0, size);
                data = newdata;
            }
            data[size++] = in.readDouble();
        }
        in.readArrayE();
        double[] newdata = new double[size];
        System.arraycopy(data, 0, newdata, 0, size);
        return newdata;
    }

    public static final class DoubleStreamSimpledCoder<R extends Reader, W extends Writer>
            extends SimpledCoder<R, W, DoubleStream> {

        public static final DoubleStreamSimpledCoder instance = new DoubleStreamSimpledCoder();

        @Override
        @SuppressWarnings("unchecked")
        public void convertTo(W out, DoubleStream values) {
            if (values == null) {
                out.writeNull();
                return;
            }
            DoubleArraySimpledCoder.instance.convertTo(out, values.toArray());
        }

        @Override
        @SuppressWarnings("unchecked")
        public DoubleStream convertFrom(R in) {
            double[] value = DoubleArraySimpledCoder.instance.convertFrom(in);
            return value == null ? null : DoubleStream.of(value);
        }
    }
}
