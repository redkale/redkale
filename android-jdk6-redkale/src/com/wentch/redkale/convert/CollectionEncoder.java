/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import java.lang.reflect.*;
import java.util.Collection;

/**
 * 对象集合的序列化.
 * 集合大小不能超过 32767。  在BSON中集合大小设定的是short，对于大于32767长度的集合传输会影响性能，所以没有采用int存储。
 * 支持一定程度的泛型。
 *
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class CollectionEncoder<T> implements Encodeable<Writer, Collection<T>> {

    private final Type type;

    private final Encodeable<Writer, Object> encoder;

    public CollectionEncoder(final Factory factory, final Type type) {
        this.type = type;
        if (type instanceof ParameterizedType) {
            Type t = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (t instanceof TypeVariable) {
                this.encoder = factory.getAnyEncoder();
            } else {
                this.encoder = factory.loadEncoder(t);
            }
        } else {
            this.encoder = factory.getAnyEncoder();
        }
    }

    @Override
    public void convertTo(Writer out, Collection<T> value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        if (value.isEmpty()) {
            out.writeArrayB(0);
            out.writeArrayE();
            return;
        }
        out.writeArrayB(value.size());
        boolean first = true;
        for (Object v : value) {
            if (!first) out.writeArrayMark();
            encoder.convertTo(out, v);
            if (first) first = false;
        }
        out.writeArrayE();
    }

    @Override
    public Type getType() {
        return type;
    }
}
