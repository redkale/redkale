/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.stream.Stream;

/**
 * Stream的反序列化操作类 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader
 * @param <T> 反解析的集合元素类型
 */
@SuppressWarnings("unchecked")
public class StreamDecoder<R extends Reader, T> implements Decodeable<R, Stream<T>> {

    protected final Type type;

    protected final Type componentType;

    protected final Decodeable<R, T> componentDecoder;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    public StreamDecoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
            if (type instanceof ParameterizedType) {
                final ParameterizedType pt = (ParameterizedType) type;
                this.componentType = pt.getActualTypeArguments()[0];
                factory.register(type, this);
                this.componentDecoder = factory.loadDecoder(this.componentType);
            } else if (factory.isReversible() && type == Stream.class) {
                this.componentType = Object.class;
                factory.register(type, this);
                this.componentDecoder = factory.loadDecoder(this.componentType);
            } else {
                throw new ConvertException("StreamDecoder not support the type (" + type + ")");
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
        if (this.componentDecoder == null) {
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
    public Stream<T> convertFrom(R in) {
        this.checkInited();
        final Decodeable<R, T> itemDecoder = this.componentDecoder;
        if (!in.readArrayB(itemDecoder)) {
            return null;
        }
        final List<T> result = new ArrayList();
        while (in.hasNext()) {
            result.add(itemDecoder.convertFrom(in));
        }
        in.readArrayE();
        return result.stream();
    }

    @Override
    public Type getType() {
        return type;
    }

    public Type getComponentType() {
        return componentType;
    }

    public Decodeable<R, T> getComponentDecoder() {
        return componentDecoder;
    }
}
