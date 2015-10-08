/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <W>
 * @param <T>
 */
public abstract class SimpledCoder<R extends Reader, W extends Writer, T> implements Decodeable<R, T>, Encodeable<W, T> {

    private Type type;

    @Override
    public abstract void convertTo(final W out, final T value);

    @Override
    public abstract T convertFrom(final R in);

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> getType() {
        if (type == null) {
            Type[] ts = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();
            type = ts[ts.length - 1];
        }
        return (Class<T>) type;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
