/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Method;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.util.RedkaleClassLoader;

/**
 * 枚举 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 * @param <E> Enum的子类
 */
public class ProtobufEnumSimpledCoder<R extends ProtobufReader, W extends ProtobufWriter, E extends Enum>
        extends SimpledCoder<R, W, E> {

    private final Map<Integer, E> values = new HashMap<>();

    private final boolean enumtostring;

    public ProtobufEnumSimpledCoder(Class<E> type, boolean enumtostring) {
        this.type = type;
        this.enumtostring = enumtostring;
        try {
            final Method method = type.getMethod("values");
            RedkaleClassLoader.putReflectionMethod(type.getName(), method);
            for (E item : (E[]) method.invoke(null)) {
                values.put(item.ordinal(), item);
            }
        } catch (Exception e) {
            throw new ConvertException(e);
        }
    }

    @Override
    public void convertTo(final W out, final E value) {
        if (value == null) {
            out.writeNull();
        } else if (enumtostring) {
            out.writeStandardString(value.toString());
        } else {
            out.writeUInt32(value.ordinal());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E convertFrom(final R in) {
        if (enumtostring) {
            String value = in.readStandardString();
            return value == null ? null : (E) Enum.valueOf((Class<E>) type, value);
        } else {
            int value = in.readRawVarint32();
            return values.get(value);
        }
    }

    @Override
    public Class<E> getType() {
        return (Class) type;
    }
}
