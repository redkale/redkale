/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;

/**
 * 对不明类型的对象进行序列化； PROTOBUF序列化时将对象的类名写入Writer，JSON则不写入。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer
 * @param <T> 序列化的泛型类型
 */
public final class AnyEncoder<W extends Writer, T> implements Encodeable<W, T> {

    final ConvertFactory factory;

    AnyEncoder(ConvertFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(final W out, final T value) {
        if (value == null) {
            out.writeClassName(null);
            out.writeNull();
        } else {
            Class clazz = value.getClass();
            if (clazz == Object.class) {
                out.writeObjectB(value);
                out.writeObjectE(value);
                return;
            }
            if (out.needWriteClassName()) {
                out.writeClassName(factory.getEntityAlias(clazz));
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
