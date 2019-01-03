/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.Duration;
import org.redkale.convert.*;

/**
 * Duration 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class DurationSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Duration> {

    public static final DurationSimpledCoder instance = new DurationSimpledCoder();

    @Override
    public void convertTo(W out, Duration value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.writeLong(value.toNanos());
        }
    }

    @Override
    public Duration convertFrom(R in) {
        String value = in.readSmallString();
        if (value == null) return null;
        return Duration.ofNanos(Long.parseLong(value));
    }

}
