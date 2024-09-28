/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;
import org.redkale.util.Creator;

/**
 * Map的反序列化操作类 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
@SuppressWarnings("unchecked")
public class MapDecoder<R extends Reader, K, V> implements Decodeable<R, Map<K, V>> {

    protected final Type type;

    protected final Type keyType;

    protected final Type valueType;

    protected Creator<Map<K, V>> creator;

    protected final Decodeable<R, K> keyDecoder;

    protected final Decodeable<R, V> valueDecoder;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    public MapDecoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
            if (type == java.util.Properties.class) {
                this.keyType = String.class;
                this.valueType = String.class;
                this.creator = factory.loadCreator(java.util.Properties.class);
                factory.register(type, this);
                this.keyDecoder = factory.loadDecoder(String.class);
                this.valueDecoder = factory.loadDecoder(String.class);
            } else if (type instanceof ParameterizedType) {
                final ParameterizedType pt = (ParameterizedType) type;
                this.keyType = pt.getActualTypeArguments()[0];
                this.valueType = pt.getActualTypeArguments()[1];
                this.creator = factory.loadCreator((Class) pt.getRawType());
                factory.register(type, this);
                this.keyDecoder = factory.loadDecoder(this.keyType);
                this.valueDecoder = factory.loadDecoder(this.valueType);
            } else if (factory.isReversible()) {
                this.keyType = Object.class;
                this.valueType = Object.class;
                this.creator = factory.loadCreator((Class) type);
                this.keyDecoder = factory.loadDecoder(this.keyType);
                this.valueDecoder = factory.loadDecoder(this.valueType);
            } else {
                throw new ConvertException("mapdecoder not support the type (" + type + ")");
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

    // 仅供类似JsonAnyDecoder这种动态创建使用， 不得调用 factory.register
    public MapDecoder(
            Type type,
            Type keyType,
            Type valueType,
            Creator<Map<K, V>> creator,
            final Decodeable<R, K> keyDecoder,
            Decodeable<R, V> valueDecoder) {
        Objects.requireNonNull(keyDecoder);
        Objects.requireNonNull(valueDecoder);
        this.type = type;
        this.keyType = keyType;
        this.valueType = valueType;
        this.creator = creator;
        this.keyDecoder = keyDecoder;
        this.valueDecoder = valueDecoder;
        this.inited = true;
    }

    protected void checkInited() {
        if (this.keyDecoder == null || this.valueDecoder == null) {
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
    public Map<K, V> convertFrom(R in) {
        this.checkInited();
        Decodeable<R, K> kdecoder = this.keyDecoder;
        Decodeable<R, V> vdecoder = this.valueDecoder;
        int len = in.readMapB(kdecoder, vdecoder);
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        final Map<K, V> result = this.creator.create();
        if (len == Reader.SIGN_VARIABLE) {
            while (in.hasNext()) {
                K key = kdecoder.convertFrom(in);
                in.readBlank();
                V value = vdecoder.convertFrom(in);
                result.put(key, value);
            }
        } else { // 固定长度
            for (int i = 0; i < len; i++) {
                K key = kdecoder.convertFrom(in);
                in.readBlank();
                V value = vdecoder.convertFrom(in);
                result.put(key, value);
            }
        }
        in.readMapE();
        return result;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    public Type getKeyType() {
        return keyType;
    }

    public Type getValueType() {
        return valueType;
    }

    public Decodeable<R, K> getKeyDecoder() {
        return keyDecoder;
    }

    public Decodeable<R, V> getValueDecoder() {
        return valueDecoder;
    }
}
