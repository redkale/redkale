/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.Encodeable;

/**
 * 对不明类型的对象进行序列化； PROTOBUF序列化时将对象的类名写入Writer，JSON则不写入。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 序列化的泛型类型
 */
public final class ProtobufAnyEncoder<T> implements Encodeable<ProtobufWriter, T> {

    final ProtobufFactory factory;

    ProtobufAnyEncoder(ProtobufFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(final ProtobufWriter out, final T value) {
        if (value == null) {
            out.writeNull();
        } else {
            Class clazz = value.getClass();
            if (clazz == Object.class) {
                out.writeObjectB(value);
                out.writeObjectE(value);
                return;
            }
            factory.loadEncoder(clazz).convertTo(out, value);
        }
    }

    @Override
    public Type getType() {
        return Object.class;
    }

    @Override
    public boolean specifyable() {
        return false;
    }
}
