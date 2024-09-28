/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.IntFunction;
import org.redkale.util.Creator;

/**
 * 数组的反序列化操作类 <br>
 * 对象数组的反序列化，不包含int[]、long[]这样的primitive class数组。 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader
 * @param <T> 反解析的数组元素类型
 */
@SuppressWarnings("unchecked")
public class ArrayDecoder<R extends Reader, T> implements Decodeable<R, T[]> {

    protected final Type type;

    protected final Type componentType;

    protected final Class componentClass;

    protected final Decodeable<R, T> componentDecoder;

    protected final IntFunction<T[]> componentArrayFunction;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

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
            this.componentDecoder = factory.loadDecoder(this.componentType);
            this.componentArrayFunction = Creator.funcArray(this.componentClass);
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
    public T[] convertFrom(R in) {
        this.checkInited();
        final Decodeable<R, T> itemDecoder = this.componentDecoder;
        int len = in.readArrayB(itemDecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        final List<T> result = new ArrayList();
        if (len == Reader.SIGN_VARIABLE) {
            while (in.hasNext()) {
                result.add(itemDecoder.convertFrom(in));
            }
        } else { // 固定长度
            for (int i = 0; i < len; i++) {
                result.add(itemDecoder.convertFrom(in));
            }
        }
        in.readArrayE();
        T[] rs = this.componentArrayFunction.apply(result.size());
        return result.toArray(rs);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", decoder:"
                + this.componentDecoder + "}";
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
