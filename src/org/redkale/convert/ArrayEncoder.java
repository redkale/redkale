/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;

/**
 * 数组的序列化操作类  <br>
 * 对象数组的序列化，不包含int[]、long[]这样的primitive class数组。  <br>
 * 支持一定程度的泛型。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 序列化的数组元素类型
 */
@SuppressWarnings("unchecked")
public class ArrayEncoder<T> implements Encodeable<Writer, T[]> {

    protected final Type type;

    protected final Type componentType;

    protected final Encodeable anyEncoder;

    protected final Encodeable<Writer, Object> componentEncoder;

    protected volatile boolean inited = false;

    protected final Object lock = new Object();

    public ArrayEncoder(final ConvertFactory factory, final Type type) {
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
            factory.register(type, this);
            this.componentEncoder = factory.loadEncoder(this.componentType);
            this.anyEncoder = factory.getAnyEncoder();
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public void convertTo(Writer out, T[] value) {
        convertTo(out, null, value);
    }

    public void convertTo(Writer out, EnMember member, T[] value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        if (value.length == 0) {
            out.writeArrayB(0, componentEncoder, value);
            out.writeArrayE();
            return;
        }
        if (this.componentEncoder == null) {
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
        if (out.writeArrayB(value.length, componentEncoder, value) < 0) {
            final Type comp = this.componentType;
            boolean first = true;
            for (Object v : value) {
                if (!first) out.writeArrayMark();
                writeMemberValue(out, member, ((v != null && (v.getClass() == comp || out.specify() == comp)) ? componentEncoder : anyEncoder), v, first);
                if (first) first = false;
            }
        }
        out.writeArrayE();
    }

    protected void writeMemberValue(Writer out, EnMember member, Encodeable<Writer, Object> encoder, Object value, boolean first) {
        encoder.convertTo(out, value);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", encoder:" + this.componentEncoder + "}";
    }

    @Override
    public Type getType() {
        return type;
    }

    public Type getComponentType() {
        return componentType;
    }

    public Encodeable<Writer, Object> getComponentEncoder() {
        return componentEncoder;
    }

}
