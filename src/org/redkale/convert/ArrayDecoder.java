/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;

/**
 * 数组的反序列化操作类  <br>
 * 对象数组的反序列化，不包含int[]、long[]这样的primitive class数组。  <br>
 * 支持一定程度的泛型。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 反解析的数组元素类型
 */
@SuppressWarnings("unchecked")
public class ArrayDecoder<T> implements Decodeable<Reader, T[]> {

    protected final Type type;

    protected final Type componentType;

    protected final Class componentClass;

    protected final Decodeable<Reader, T> decoder;

    protected boolean inited = false;

    protected final Object lock = new Object();

    public ArrayDecoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
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
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public T[] convertFrom(Reader in) {
        return convertFrom(in, null);
    }

    public T[] convertFrom(Reader in, DeMember member) {
        int len = in.readArrayB(member, decoder);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) return null;
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = in.readMemberContentLength(member);
            len = Reader.SIGN_NOLENGTH;
        }
        if (this.decoder == null) {
            if (!this.inited) {
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        final Decodeable<Reader, T> localdecoder = this.decoder;
        final List<T> result = new ArrayList();
        boolean first = true;
        if (len == Reader.SIGN_NOLENGTH) {
            int startPosition = in.position();
            while (in.hasNext(startPosition, contentLength)) {
                result.add(readMemberValue(in, member, first));
                first = false;
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

    protected T readMemberValue(Reader in, DeMember member, boolean first) {
        return this.decoder.convertFrom(in);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", decoder:" + this.decoder + "}";
    }

    @Override
    public Type getType() {
        return type;
    }

    public Type getComponentType() {
        return componentType;
    }

    public Decodeable<Reader, T> getDecoder() {
        return decoder;
    }

}
