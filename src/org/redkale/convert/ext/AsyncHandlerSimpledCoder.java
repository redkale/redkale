/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.*;
import org.redkale.util.AsyncHandler;

/**
 * AsyncHandlerSimpledCoder 的SimpledCoder实现, 只输出null
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class AsyncHandlerSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, AsyncHandler> {

    public static final AsyncHandlerSimpledCoder instance = new AsyncHandlerSimpledCoder();

    @Override
    public void convertTo(W out, AsyncHandler value) {
        out.writeObjectNull(AsyncHandler.class);
    }

    @Override
    public AsyncHandler convertFrom(R in) {
        in.readObjectB(AsyncHandler.class);
        return null;
    }

}
