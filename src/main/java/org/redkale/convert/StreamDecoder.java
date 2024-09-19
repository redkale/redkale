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

    @Override
    public Stream<T> convertFrom(R in) {
        return convertFrom(in, null);
    }

    public Stream<T> convertFrom(R in, DeMember member) {
        byte[] typevals = new byte[1];
        int len = in.readArrayB(member, typevals, this.componentDecoder);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = in.readMemberContentLength(member, this.componentDecoder);
            len = Reader.SIGN_NOLENGTH;
        }
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
        final Decodeable<R, T> localdecoder = getComponentDecoder(this.componentDecoder, typevals);
        final List<T> result = new ArrayList();
        boolean first = true;
        if (len == Reader.SIGN_NOLENGTH) {
            int startPosition = in.position();
            while (hasNext(in, member, startPosition, contentLength, first)) {
                R itemReader = getItemReader(in, member, first);
                if (itemReader == null) {
                    break;
                }
                result.add(readMemberValue(itemReader, member, localdecoder, first));
                first = false;
            }
        } else {
            for (int i = 0; i < len; i++) {
                result.add(localdecoder.convertFrom(in));
            }
        }
        in.readArrayE();
        return result.stream();
    }

    protected boolean hasNext(R in, DeMember member, int startPosition, int contentLength, boolean first) {
        return in.hasNext(startPosition, contentLength);
    }

    protected Decodeable<R, T> getComponentDecoder(Decodeable<R, T> decoder, byte[] typevals) {
        return decoder;
    }

    protected R getItemReader(R in, DeMember member, boolean first) {
        return in;
    }

    protected T readMemberValue(R in, DeMember member, Decodeable<R, T> decoder, boolean first) {
        return decoder.convertFrom(in);
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
