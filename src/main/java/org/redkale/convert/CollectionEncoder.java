/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.concurrent.locks.*;

/**
 * Collection的序列化操作类 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer
 * @param <T> 序列化的集合元素类型
 */
@SuppressWarnings("unchecked")
public class CollectionEncoder<W extends Writer, T> implements Encodeable<W, Collection<T>> {

    protected final Type type;

    protected final Encodeable<W, Object> componentEncoder;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    public CollectionEncoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
            if (type instanceof ParameterizedType) {
                Type t = ((ParameterizedType) type).getActualTypeArguments()[0];
                if (t instanceof TypeVariable) {
                    this.componentEncoder = factory.loadEncoder(Object.class);
                } else {
                    this.componentEncoder = factory.loadEncoder(t);
                }
            } else {
                this.componentEncoder = factory.loadEncoder(Object.class);
            }
        } finally {
            inited = true;
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    protected void checkInited() {
        if (this.componentEncoder == null) {
            if (!this.inited) {
                lock.lock();
                try {
                    condition.await();
                } catch (Exception e) {
                    // do nothing
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public void convertTo(W out, Collection<T> value) {
        convertTo(out, null, value);
    }

    public void convertTo(W out, EnMember member, Collection<T> value) {
        this.checkInited();
        if (value == null) {
            out.writeNull();
            return;
        }
        Encodeable itemEncoder = this.componentEncoder;
        if (value.isEmpty()) {
            out.writeArrayB(0, itemEncoder, value);
            out.writeArrayE();
            return;
        }
        out.writeArrayB(value.size(), itemEncoder, value);
        boolean first = true;
        for (Object v : value) {
            if (!first) {
                out.writeArrayMark();
            }
            itemEncoder.convertTo(out, v);
            if (first) {
                first = false;
            }
        }
        out.writeArrayE();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean specifyable() {
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.type + ", encoder:" + this.componentEncoder
                + "}";
    }

    public Encodeable<W, Object> getComponentEncoder() {
        return componentEncoder;
    }

    public Type getComponentType() {
        return componentEncoder == null ? null : componentEncoder.getType();
    }
}
