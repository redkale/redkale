/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.concurrent.locks.*;
import java.util.stream.Stream;

/**
 * Stream的序列化操作类 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer
 * @param <T> 序列化的集合元素类型
 */
@SuppressWarnings("unchecked")
public class StreamEncoder<W extends Writer, T> implements Encodeable<W, Stream<T>> {

    protected final Type type;

    protected final Encodeable<Writer, Object> componentEncoder;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    public StreamEncoder(final ConvertFactory factory, final Type type) {
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
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void convertTo(W out, Stream<T> value) {
        convertTo(out, null, value);
    }

    public void convertTo(W out, EnMember member, Stream<T> value) {
        if (value == null) {
            out.writeNull();
            return;
        }
        Object[] array = value.toArray();
        if (array.length == 0) {
            out.writeArrayB(0, this, componentEncoder, array);
            out.writeArrayE();
            return;
        }
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
        if (out.writeArrayB(array.length, this, componentEncoder, array) < 0) {
            boolean first = true;
            for (Object v : array) {
                if (!first) {
                    out.writeArrayMark();
                }
                writeMemberValue(out, member, v, first);
                if (first) {
                    first = false;
                }
            }
        }
        out.writeArrayE();
    }

    protected void writeMemberValue(W out, EnMember member, Object value, boolean first) {
        componentEncoder.convertTo(out, value);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean specifyable() {
        return false;
    }

    public Encodeable<Writer, Object> getComponentEncoder() {
        return componentEncoder;
    }

    public Type getComponentType() {
        return componentEncoder == null ? null : componentEncoder.getType();
    }
}
