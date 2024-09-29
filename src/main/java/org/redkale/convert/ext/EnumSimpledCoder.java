/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.lang.reflect.*;
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
public final class EnumSimpledCoder<R extends Reader, W extends Writer, E extends Enum> extends SimpledCoder<R, W, E> {

    private final Encodeable valueEncoder;

    private final Map<E, Object> enumToValues;

    private final Map<String, E> valueToEnums;

    public EnumSimpledCoder(final ConvertFactory factory, Class<E> type) {
        this.type = type;
        ConvertEnumValue cev = type.getAnnotation(ConvertEnumValue.class);
        if (cev == null) {
            this.valueEncoder = null;
            this.enumToValues = null;
            this.valueToEnums = null;
        } else {
            try {
                String fieldName = cev.value();
                Field field = type.getDeclaredField(fieldName);
                RedkaleClassLoader.putReflectionField(type.getName(), field);
                char[] chs = fieldName.toCharArray();
                chs[0] = Character.toUpperCase(chs[0]);
                String methodName = "get" + new String(chs);
                Method method = null;
                try {
                    method = type.getMethod(methodName);
                } catch (NoSuchMethodException | SecurityException me) {
                    method = type.getMethod(fieldName);
                    methodName = fieldName;
                }
                RedkaleClassLoader.putReflectionMethod(methodName, method);
                Map<E, Object> map1 = new HashMap<>();
                Map<String, E> map2 = new HashMap<>();
                for (E e : type.getEnumConstants()) {
                    map1.put(e, method.invoke(e));
                    map2.put(method.invoke(e).toString(), e);
                }
                this.valueEncoder = factory.loadEncoder(field.getType());
                this.enumToValues = map1;
                this.valueToEnums = map2;
            } catch (Exception e) {
                throw new ConvertException(e);
            }
        }
    }

    @Override
    public void convertTo(final W out, final E value) {
        if (value == null) {
            out.writeNull();
        } else if (valueEncoder != null) {
            valueEncoder.convertTo(out, enumToValues.get(value));
        } else {
            out.writeStandardString(value.toString());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E convertFrom(final R in) {
        String value = in.readStandardString();
        if (value == null) {
            return null;
        }
        if (valueToEnums != null) {
            return valueToEnums.get(value);
        } else {
            return (E) Enum.valueOf((Class<E>) type, value);
        }
    }

    @Override
    public Class<E> getType() {
        return (Class<E>) type;
    }
}
