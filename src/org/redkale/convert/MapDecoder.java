/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Creator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Map的反序列化操作类 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> Map key的数据类型
 * @param <V> Map value的数据类型
 */
@SuppressWarnings("unchecked")
public class MapDecoder<K, V> implements Decodeable<Reader, Map<K, V>> {

    protected final Type type;

    protected final Type keyType;

    protected final Type valueType;

    protected Creator<Map<K, V>> creator;

    protected final Decodeable<Reader, K> keyDecoder;

    protected final Decodeable<Reader, V> valueDecoder;

    protected boolean inited = false;

    protected final Object lock = new Object();

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
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public Map<K, V> convertFrom(Reader in) {
        return convertFrom(in, null);
    }

    public Map<K, V> convertFrom(Reader in, DeMember member) {
        if (this.keyDecoder == null || this.valueDecoder == null) {
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
        int len = in.readMapB(member, this.keyDecoder);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) return null;
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = in.readMemberContentLength(member, null);
            len = Reader.SIGN_NOLENGTH;
        }
        final Map<K, V> result = this.creator.create();
        boolean first = true;
        if (len == Reader.SIGN_NOLENGTH) {
            int startPosition = in.position();
            while (in.hasNext(this, member, startPosition, contentLength)) {
                Reader entryReader = getMapEntryReader(in, member, first);
                K key = readKeyMember(entryReader, member, first);
                entryReader.readBlank();
                V value = readValueMember(entryReader, member, first);
                result.put(key, value);
                first = false;
            }
        } else {
            for (int i = 0; i < len; i++) {
                K key = readKeyMember(in, member, first);
                in.readBlank();
                V value = readValueMember(in, member, first);
                result.put(key, value);
                first = false;
            }
        }
        in.readMapE();
        return result;
    }

    protected Reader getMapEntryReader(Reader in, DeMember member, boolean first) {
        return in;
    }

    protected K readKeyMember(Reader in, DeMember member, boolean first) {
        return keyDecoder.convertFrom(in);
    }

    protected V readValueMember(Reader in, DeMember member, boolean first) {
        return valueDecoder.convertFrom(in);
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

    public Decodeable<Reader, K> getKeyDecoder() {
        return keyDecoder;
    }

    public Decodeable<Reader, V> getValueDecoder() {
        return valueDecoder;
    }

}
