/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import java.lang.reflect.*;
import java.util.*;

/**
 * 对象数组的序列化，不包含int[]、long[]这样的primitive class数组.
 * 数组长度不能超过 32767。  在BSON中数组长度设定的是short，对于大于32767长度的数组传输会影响性能，所以没有采用int存储。
 * 支持一定程度的泛型。
 * 
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class ArrayDecoder<T> implements Decodeable<Reader, T[]> {

    private final Type type;

    private final Type componentType;

    private final Class componentClass;

    private final Decodeable<Reader, T> decoder;

    public ArrayDecoder(final Factory factory, final Type type) {
        this.type = type;
        if (type instanceof GenericArrayType) {
            Type t = ((GenericArrayType) type).getGenericComponentType();
            this.componentType = t instanceof TypeVariable ? Object.class : t;
        } else if ((type instanceof Class) && ((Class) type).isArray()) {
            this.componentType = ((Class) type).getComponentType();
        } else {
            throw new ConvertException("(" + type + ") is not a array type");
        }
        if (this.componentType instanceof ParameterizedType) {
            this.componentClass = (Class) ((ParameterizedType) this.componentType).getRawType();
        } else {
            this.componentClass = (Class) this.componentType;
        }
        factory.register(type, this);
        this.decoder = factory.loadDecoder(this.componentType);
    }

    @Override
    public T[] convertFrom(Reader in) {
        final int len = in.readArrayB();
        if (len == Reader.SIGN_NULL) return null;
        final Decodeable<Reader, T> localdecoder = this.decoder;
        final List<T> result = new ArrayList();
        if (len == Reader.SIGN_NOLENGTH) {
            while (in.hasNext()) {
                result.add(localdecoder.convertFrom(in));
            }
        } else {
            for (int i = 0; i < len; i++) {
                result.add(localdecoder.convertFrom(in));
            }
        }
        in.readArrayE();
        T[] rs = (T[]) Array.newInstance((Class) this.componentClass, result.size());
        return result.toArray(rs);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", decoder:" + this.decoder + "}";
    }

    @Override
    public Type getType() {
        return type;
    }

}
