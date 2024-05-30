/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import org.redkale.util.AnyValue;

/**
 * AnyValue的Decoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @since 2.5.0
 */
public class AnyValueDecoder<R extends Reader> implements Decodeable<R, AnyValue> {

    protected final ConvertFactory factory;

    public AnyValueDecoder(final ConvertFactory factory) {
        this.factory = factory;
    }

    @Override
    public AnyValue convertFrom(R in) {
        return null;
    }

    @Override
    public Type getType() {
        return AnyValue.class;
    }
}
