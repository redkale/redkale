/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.LocalTime;
import org.redkale.convert.*;

/**
 * java.time.LocalTime 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class LocalTimeSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, LocalTime> {

    public static final LocalTimeSimpledCoder instance = new LocalTimeSimpledCoder();

    @Override
    public void convertTo(W out, LocalTime value) {
        out.writeLong(value == null ? -1L : value.toNanoOfDay());
    }

    @Override
    public LocalTime convertFrom(R in) {
        long t = in.readLong();
        return t == -1 ? null : LocalTime.ofNanoOfDay(t);
    }

}
