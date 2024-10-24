/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;

/**
 * char[] 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class CharArraySimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, char[]> {

    public static final CharArraySimpledCoder instance = new CharArraySimpledCoder();

    @Override
    public void convertTo(W out, char[] values) {
        if (values == null) {
            out.writeNull();
            return;
        }
        out.writeArrayB(values.length, CharSimpledCoder.instance, values);
        boolean flag = false;
        for (char v : values) {
            if (flag) {
                out.writeArrayMark();
            }
            out.writeChar(v);
            flag = true;
        }
        out.writeArrayE();
    }

    @Override
    public char[] convertFrom(R in) {
        if (!in.readArrayB(CharSimpledCoder.instance)) {
            return null;
        }
        int size = 0;
        char[] data = new char[8];
        while (in.hasNext()) {
            if (size >= data.length) {
                char[] newdata = new char[data.length + 4];
                System.arraycopy(data, 0, newdata, 0, size);
                data = newdata;
            }
            data[size++] = in.readChar();
        }
        in.readArrayE();
        char[] newdata = new char[size];
        System.arraycopy(data, 0, newdata, 0, size);
        return newdata;
    }
}
