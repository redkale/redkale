/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.util.concurrent.atomic.LongAdder;
import org.redkale.convert.*;

/**
 * LongAdder 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class LongAdderSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, LongAdder> {

    public static final LongAdderSimpledCoder instance = new LongAdderSimpledCoder();

    @Override
    public void convertTo(W out, LongAdder value) {
        out.writeLong(value == null ? 0L : value.longValue());
    }

    @Override
    public LongAdder convertFrom(R in) {
        LongAdder la = new LongAdder();
        la.add(in.readLong());
        return la;
    }
}
