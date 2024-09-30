/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.ext;

import java.util.concurrent.atomic.AtomicBoolean;
import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 * AtomicAtomicBoolean 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class AtomicBooleanSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, AtomicBoolean> {

    public static final AtomicBooleanSimpledCoder instance = new AtomicBooleanSimpledCoder();

    @Override
    public void convertTo(W out, AtomicBoolean value) {
        out.writeBoolean(value != null && value.get());
    }

    @Override
    public AtomicBoolean convertFrom(R in) {
        return new AtomicBoolean(in.readBoolean());
    }
}
