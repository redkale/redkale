/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.RedkaleClassLoader;

/**
 * 简单对象的PROTOBUF序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 * @param <T> 序列化的数据类型
 */
public abstract class ProtobufDynEncoder<T> implements Encodeable<ProtobufWriter, T> {

    public abstract void init(final ProtobufFactory factory);

    // 字段全部是primitive或String类型，且没有泛型的类才能动态生成ProtobufDynEncoder， 不支持的返回null
    public static ProtobufDynEncoder createDyncEncoder(final ProtobufFactory factory, final Type type) {
        if (!(type instanceof Class)) {
            return null;
        }
        // 发现有自定义的基础数据类型Encoder就不动态生成ProtobufDynEncoder了
        if (factory.loadEncoder(boolean.class) != BoolSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte.class) != ByteSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short.class) != ShortSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char.class) != CharSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int.class) != IntSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float.class) != FloatSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long.class) != LongSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double.class) != DoubleSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String.class) != StringSimpledCoder.instance) {
            return null;
        }
        // array
        if (factory.loadEncoder(boolean[].class) != BoolArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte[].class) != ByteArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short[].class) != ShortArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char[].class) != CharArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int[].class) != IntArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float[].class) != FloatArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long[].class) != LongArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double[].class) != DoubleArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String[].class) != StringArraySimpledCoder.instance) {
            return null;
        }

        final Class clazz = (Class) type;
        List<AccessibleObject> members = null;
        Set<String> names = new HashSet<>();
        try {
            ConvertColumnEntry ref;
            RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
            for (final Field field : clazz.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (factory.isConvertDisabled(field)) {
                    continue;
                }
                ref = factory.findRef(clazz, field);
                if (ref != null && ref.ignore()) {
                    continue;
                }
                if (factory.findFieldCoder(clazz, field.getName()) != null) {
                    return null;
                }
                if (!factory.isSimpleMemberType(clazz, field.getGenericType(), field.getType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, field);
                if (names.contains(name)) {
                    continue;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(field);
            }
            RedkaleClassLoader.putReflectionPublicMethods(clazz.getName());
            for (final Method method : clazz.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                if (method.isSynthetic()) {
                    continue;
                }
                if (method.getName().length() < 3) {
                    continue;
                }
                if (method.getName().equals("getClass")) {
                    continue;
                }
                if (!(method.getName().startsWith("is") && method.getName().length() > 2)
                        && !(method.getName().startsWith("get")
                                && method.getName().length() > 3)) {
                    continue;
                }
                if (factory.isConvertDisabled(method)) {
                    continue;
                }
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                if (method.getReturnType() == void.class) {
                    continue;
                }
                ref = factory.findRef(clazz, method);
                if (ref != null && ref.ignore()) {
                    continue;
                }
                if (ref != null && ref.fieldFunc() != null) {
                    return null;
                }
                if (!factory.isSimpleMemberType(clazz, method.getGenericReturnType(), method.getReturnType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, method);
                if (names.contains(name)) {
                    continue;
                }
                if (factory.findFieldCoder(clazz, name) != null) {
                    return null;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(method);
            }
            if (members == null) {
                return null;
            }
            factory.sortFieldIndex(clazz, members);
            return generateDyncEncoder(factory, clazz, members);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected static ProtobufDynEncoder generateDyncEncoder(
            final ProtobufFactory factory, final Class clazz, final List<AccessibleObject> members) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        selfObjEncoder.init(factory);
        if (selfObjEncoder.getMembers().length != members.size()) {
            return null; // 存在ignore等定制配置
        }
        return null;
    }
}
