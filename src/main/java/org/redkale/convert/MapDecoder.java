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

    @Override
    public Map<K, V> convertFrom(R in) {
        return convertFrom(in, null);
    }

    public Map<K, V> convertFrom(R in, DeMember member) {
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
        byte[] typevals = new byte[2];
        int len = in.readMapB(member, typevals, this.keyDecoder, this.valueDecoder);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = in.readMemberContentLength(member, null);
            len = Reader.SIGN_NOLENGTH;
        }
        final Map<K, V> result = this.creator.create();
        boolean first = true;
        Decodeable<R, K> kdecoder = getKeyDecoder(this.keyDecoder, typevals);
        Decodeable<R, V> vdecoder = getValueDecoder(this.valueDecoder, typevals);
        if (len == Reader.SIGN_NOLENGTH) {
            int startPosition = in.position();
            while (hasNext(in, member, startPosition, contentLength, first)) {
                R entryReader = getEntryReader(in, member, first);
                if (entryReader == null) {
                    break;
                }
                K key = readKeyMember(entryReader, member, kdecoder, first);
                entryReader.readBlank();
                V value = readValueMember(entryReader, member, vdecoder, first);
                result.put(key, value);
                first = false;
            }
        } else {
            for (int i = 0; i < len; i++) {
                K key = readKeyMember(in, member, kdecoder, first);
                in.readBlank();
                V value = readValueMember(in, member, vdecoder, first);
                result.put(key, value);
                first = false;
            }
        }
        in.readMapE();
        return result;
    }

    protected boolean hasNext(R in, DeMember member, int startPosition, int contentLength, boolean first) {
        return in.hasNext(startPosition, contentLength);
    }

    protected Decodeable<R, K> getKeyDecoder(Decodeable<R, K> decoder, byte[] typevals) {
        return decoder;
    }

    protected Decodeable<R, V> getValueDecoder(Decodeable<R, V> decoder, byte[] typevals) {
        return decoder;
    }

    protected R getEntryReader(R in, DeMember member, boolean first) {
        return in;
    }

    protected K readKeyMember(R in, DeMember member, Decodeable<R, K> decoder, boolean first) {
        return decoder.convertFrom(in);
    }

    protected V readValueMember(R in, DeMember member, Decodeable<R, V> decoder, boolean first) {
        return decoder.convertFrom(in);
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
