/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;

/**
 * 对象数组的反序列化，不包含int[]、long[]这样的primitive class数组.
 * 数组长度不能超过 32767。 在BSON中数组长度设定的是short，对于大于32767长度的数组传输会影响性能，所以没有必要采用int存储。
 * 支持一定程度的泛型。
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class ArrayEncoder<T> implements Encodeable<Writer, T[]> {

    private final Type type;

    private final Type componentType;

    private final Encodeable anyEncoder;

    private final Encodeable<Writer, Object> encoder;

    public ArrayEncoder(final Factory factory, final Type type) {
        this.type = type;
        if (type instanceof GenericArrayType) {
            Type t = ((GenericArrayType) type).getGenericComponentType();
            this.componentType = t instanceof TypeVariable ? Object.class : t;
        } else if ((type instanceof Class) && ((Class) type).isArray()) {
            this.componentType = ((Class) type).getComponentType();
        } else {
            throw new ConvertException("(" + type + ") is not a array type");
        }
        factory.register(type, this);
        this.encoder = factory.loadEncoder(this.componentType);
        this.anyEncoder = factory.getAnyEncoder();
    }

    @Override
    public void convertTo(Writer out, T[] value) {
        if (value == null) {
            out.wirteClassName(null);
            out.writeNull();
            return;
        }
        if (value.length == 0) {
            out.writeArrayB(0);
            out.writeArrayE();
            return;
        }
        out.writeArrayB(value.length);
        final Type comp = this.componentType;
        boolean first = true;
        for (Object v : value) {
            if (!first) out.writeArrayMark();
            ((v != null && v.getClass() == comp) ? encoder : anyEncoder).convertTo(out, v);
            if (first) first = false;
        }
        out.writeArrayE();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", encoder:" + this.encoder + "}";
    }

    @Override
    public Type getType() {
        return type;
    }
}
