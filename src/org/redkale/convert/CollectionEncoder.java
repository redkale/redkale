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

    protected final Encodeable<Writer, Object> encoder;

    protected boolean inited = false;

    protected final Object lock = new Object();

    public CollectionEncoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
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
            out.writeArrayB(0, encoder, value);
            out.writeArrayE();
            return;
        }
        if (this.encoder == null) {
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
        if (out.writeArrayB(value.size(), encoder, value) < 0) {
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
        encoder.convertTo(out, value);
    }

    @Override
    public Type getType() {
        return type;
    }

    public Encodeable<Writer, Object> getEncoder() {
        return encoder;
    }

}