/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;

/**
 * 对不明类型的对象进行序列化； BSON序列化时将对象的类名写入Writer，JSON则不写入。
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
public final class AnyEncoder<T> implements Encodeable<Writer, T> {

    final Factory factory;

    AnyEncoder(Factory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(final Writer out, final T value) {
        if (value == null) {
            out.writeNull();
        } else {
            out.wirteClassName(factory.getEntity(value.getClass())); 
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
    }

    @Override
    public Type getType() {
        return Object.class;
    }

}
