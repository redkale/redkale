/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.Collection;

/**
 * Collection的序列化操作类  <br>
 * 支持一定程度的泛型。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 序列化的集合元素类型
 */
@SuppressWarnings("unchecked")
public class CollectionEncoder<T> implements Encodeable<Writer, Collection<T>> {

    protected final Type type;

    protected final Encodeable<Writer, Object> componentEncoder;

    protected volatile boolean inited = false;

    protected final Object lock = new Object();

    public CollectionEncoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
            if (type instanceof ParameterizedType) {
                Type t = ((ParameterizedType) type).getActualTypeArguments()[0];
                if (t instanceof TypeVariable) {
                    this.componentEncoder = factory.getAnyEncoder();
                } else {
                    this.componentEncoder = factory.loadEncoder(t);
                }
            } else {
                this.componentEncoder = factory.getAnyEncoder();
            }
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public void convertTo(Writer out, Collection<T> value) {
        convertTo(out, null, value);
    }

    public void convertTo(Writer out, EnMember member, Collection<T> value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        if (value.isEmpty()) {
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
        if (out.writeArrayB(value.size(), componentEncoder, value) < 0) {
            boolean first = true;
            for (Object v : value) {
                if (!first) out.writeArrayMark();
                writeValue(out, member, v);
                if (first) first = false;
            }
        }
        out.writeArrayE();
    }

    protected void writeValue(Writer out, EnMember member, Object value) {
        componentEncoder.convertTo(out, value);
    }

    @Override
    public Type getType() {
        return type;
    }

    public Encodeable<Writer, Object> getComponentEncoder() {
        return componentEncoder;
    }

    public Type getComponentType() {
        return componentEncoder == null ? null : componentEncoder.getType();
    }
}
