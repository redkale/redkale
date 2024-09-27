/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;
import java.util.function.BiFunction;

/**
 * Map的序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
@SuppressWarnings("unchecked")
public class MapEncoder<W extends Writer, K, V> implements Encodeable<W, Map<K, V>> {

    protected final Type type;

    protected final Encodeable<W, K> keyEncoder;

    protected final Encodeable<W, V> valueEncoder;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    protected final Set<String> ignoreMapColumns;

    public MapEncoder(final ConvertFactory factory, final Type type) {
        this.type = type;
        try {
            if (type instanceof ParameterizedType) {
                final Type[] pt = ((ParameterizedType) type).getActualTypeArguments();
                this.keyEncoder = factory.loadEncoder(pt[0]);
                this.valueEncoder = factory.loadEncoder(pt[1]);
            } else {
                this.keyEncoder = factory.getAnyEncoder();
                this.valueEncoder = factory.getAnyEncoder();
            }
            factory.ignoreMapColumnLock.lock();
            try {
                this.ignoreMapColumns =
                        factory.ignoreMapColumns.isEmpty() ? null : new HashSet<>(factory.ignoreMapColumns);
            } finally {
                factory.ignoreMapColumnLock.unlock();
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
        if (this.keyEncoder == null || this.valueEncoder == null) {
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
    public void convertTo(W out, Map<K, V> value) {
        convertTo(out, null, value);
    }

    public void convertTo(W out, EnMember member, Map<K, V> value) {
        this.checkInited();
        final Map<K, V> values = value;
        if (values == null) {
            out.writeNull();
            return;
        }
        Set<String> ignoreColumns = this.ignoreMapColumns;
        BiFunction<K, V, V> mapFieldFunc = (BiFunction) out.mapFieldFunc;
        Encodeable kencoder = this.keyEncoder;
        Encodeable vencoder = this.valueEncoder;
        out.writeMapB(values.size(), kencoder, vencoder, value);
        AtomicBoolean first = new AtomicBoolean(true);
        values.forEach((key, val) -> {
            if (ignoreColumns == null || !ignoreColumns.contains(key)) {
                V v = mapFieldFunc == null ? val : mapFieldFunc.apply(key, val);
                if (!first.get()) {
                    out.writeArrayMark();
                }
                kencoder.convertTo(out, key);
                out.writeMapMark();
                vencoder.convertTo(out, v);
                first.set(false);
            }
        });
        out.writeMapE();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean specifyable() {
        return false;
    }

    public Type getKeyType() {
        return keyEncoder == null ? null : keyEncoder.getType();
    }

    public Type getValueType() {
        return valueEncoder == null ? null : valueEncoder.getType();
    }

    public Encodeable<W, K> getKeyEncoder() {
        return keyEncoder;
    }

    public Encodeable<W, V> getValueEncoder() {
        return valueEncoder;
    }
}
