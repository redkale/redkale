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
public class ArrayDecoder<R extends Reader, T> implements TagDecodeable<R, T[]> {

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

    @Override
    public T[] convertFrom(R in) {
        return convertFrom(in, null);
    }

    public T[] convertFrom(R in, DeMember member) {
        byte[] typevals = new byte[1];
        int len = in.readArrayB(member, typevals, componentDecoder);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) {
            return null;
        }
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = in.readMemberContentLength(member, componentDecoder);
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
        T[] rs = this.componentArrayFunction.apply(result.size());
        return result.toArray(rs);
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
        if (in == null) {
            return null;
        }
        return decoder.convertFrom(in);
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
