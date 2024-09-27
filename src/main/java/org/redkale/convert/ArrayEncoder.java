/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.concurrent.locks.*;

/**
 * 数组的序列化操作类 <br>
 * 对象数组的序列化，不包含int[]、long[]这样的primitive class数组。 <br>
 * 支持一定程度的泛型。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer
 * @param <T> 序列化的数组元素类型
 */
@SuppressWarnings("unchecked")
public class ArrayEncoder<W extends Writer, T> implements Encodeable<W, T[]> {

    protected final Type type;

    protected final Type componentType;

    protected final Encodeable anyEncoder;

    protected final Encodeable<W, Object> componentEncoder;

    protected final boolean subTypeFinal;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    public ArrayEncoder(final ConvertFactory factory, final Type type) {
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
            factory.register(type, this);
            this.componentEncoder = factory.loadEncoder(this.componentType);
            if (componentEncoder == null) {
                throw new ConvertException(
                        "ArrayEncoder init componentEncoder error, componentType = " + this.componentType);
            }
            this.anyEncoder = factory.getAnyEncoder();
            this.subTypeFinal = (this.componentType instanceof Class)
                    && Modifier.isFinal(((Class) this.componentType).getModifiers());
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
    }

    @Override
    public void convertTo(W out, T[] value) {
        convertTo(out, null, value);
    }

    public void convertTo(W out, EnMember member, T[] value) {
        this.checkInited();
        if (value == null) {
            out.writeNull();
            return;
        }
        int iMax = value.length - 1;
        if (iMax == -1) {
            out.writeArrayB(0, componentEncoder, value);
            out.writeArrayE();
            return;
        }
        Encodeable<W, Object> itemEncoder = this.componentEncoder;
        if (subTypeFinal) {
            out.writeArrayB(value.length, itemEncoder, value);
            for (int i = 0; ; i++) {
                writeMemberValue(out, member, itemEncoder, value[i], i);
                if (i == iMax) {
                    break;
                }
                out.writeArrayMark();
            }
        } else {
            out.writeArrayB(value.length, itemEncoder, value);
            final Type comp = this.componentType;
            for (int i = 0; ; i++) {
                Object v = value[i];
                writeMemberValue(
                        out,
                        member,
                        ((v != null && (v.getClass() == comp || out.specificObjectType() == comp))
                                ? itemEncoder
                                : anyEncoder),
                        v,
                        i);
                if (i == iMax) {
                    break;
                }
                out.writeArrayMark();
            }
        }
        out.writeArrayE();
    }

    protected void writeMemberValue(W out, EnMember member, Encodeable<W, Object> encoder, Object value, int index) {
        encoder.convertTo(out, value);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{componentType:" + this.componentType + ", encoder:"
                + this.componentEncoder + "}";
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean specifyable() {
        return false;
    }

    public Type getComponentType() {
        return componentType;
    }

    public Encodeable<W, Object> getComponentEncoder() {
        return componentEncoder;
    }
}
