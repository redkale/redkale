/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Map的序列化操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
@SuppressWarnings("unchecked")
public class MapEncoder<K, V> implements Encodeable<Writer, Map<K, V>> {

    protected final Type type;

    protected final Encodeable<Writer, K> keyEncoder;

    protected final Encodeable<Writer, V> valueEncoder;

    protected volatile boolean inited = false;

    protected final Object lock = new Object();

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
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public void convertTo(Writer out, Map<K, V> value) {
        convertTo(out, null, value);
    }

    public void convertTo(Writer out, EnMember member, Map<K, V> value) {
        final Map<K, V> values = value;
        if (values == null) {
            out.writeNull();
            return;
        }

        if (this.keyEncoder == null || this.valueEncoder == null) {
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
        if (out.writeMapB(values.size(), (Encodeable) keyEncoder, (Encodeable) valueEncoder, value) < 0) {
            boolean first = true;
            for (Map.Entry<K, V> en : values.entrySet()) {
                if (!first) out.writeArrayMark();
                writeMemberValue(out, member, en.getKey(), en.getValue(), first);
                if (first) first = false;
            }
        }
        out.writeMapE();
    }

    protected void writeMemberValue(Writer out, EnMember member, K key, V value, boolean first) {
        keyEncoder.convertTo(out, key);
        out.writeMapMark();
        valueEncoder.convertTo(out, value);
    }

    @Override
    public Type getType() {
        return type;
    }

    public Type getKeyType() {
        return keyEncoder == null ? null : keyEncoder.getType();
    }

    public Type getValueType() {
        return valueEncoder == null ? null : valueEncoder.getType();
    }

    public Encodeable<Writer, K> getKeyEncoder() {
        return keyEncoder;
    }

    public Encodeable<Writer, V> getValueEncoder() {
        return valueEncoder;
    }

}
