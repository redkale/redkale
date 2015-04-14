/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import static com.wentch.redkale.convert.ObjectEncoder.TYPEZERO;
import com.wentch.redkale.util.Creator;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class ObjectDecoder<R extends Reader, T> implements Decodeable<R, T> {

    protected final Type type;

    protected final Class typeClass;

    protected Creator<T> creator;

    protected DeMember<R, T, ?>[] members;

    protected Factory factory;

    protected ObjectDecoder(Type type) {
        this.type = ((type instanceof Class) && ((Class) type).isInterface()) ? Object.class : type;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            this.typeClass = (Class) pt.getRawType();
        } else {
            this.typeClass = (Class) type;
        }
        this.members = new DeMember[0];
    }

    public void init(final Factory factory) {
        this.factory = factory;
        if (type == Object.class) return;

        Class clazz = null;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (!(type instanceof Class)) {
            throw new ConvertException("[" + type + "] is no a class");
        } else {
            clazz = (Class) type;
        }
        final Type[] virGenericTypes = this.typeClass.getTypeParameters();
        final Type[] realGenericTypes = (type instanceof ParameterizedType) ? ((ParameterizedType) type).getActualTypeArguments() : TYPEZERO;
        this.creator = factory.loadCreator(clazz);
        final Set<DeMember> list = new HashSet<>();
        try {
            ConvertColumnEntry ref;
            for (final Field field : clazz.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                ref = factory.findRef(field);
                if (ref != null && ref.ignore()) continue;
                Type t = ObjectEncoder.makeGenericType(field.getGenericType(), virGenericTypes, realGenericTypes);
                list.add(new DeMember(ObjectEncoder.createAttribute(factory, clazz, field, null, null), factory.loadDecoder(t)));
            }
            final boolean reversible = factory.isReversible();
            for (final Method method : clazz.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                if (Modifier.isAbstract(method.getModifiers())) continue;
                if (method.isSynthetic()) continue;
                if (method.getName().length() < 4) continue;
                if (!method.getName().startsWith("set")) continue;
                if (method.getParameterTypes().length != 1) continue;
                if (method.getReturnType() != void.class) continue;
                if (reversible) {
                    boolean is = method.getParameterTypes()[0] == boolean.class || method.getParameterTypes()[0] == Boolean.class;
                    try {
                        clazz.getMethod(method.getName().replaceFirst("set", is ? "is" : "get"));
                    } catch (Exception e) {
                        continue;
                    }
                }
                ref = factory.findRef(method);
                if (ref != null && ref.ignore()) continue;
                Type t = ObjectEncoder.makeGenericType(method.getGenericParameterTypes()[0], virGenericTypes, realGenericTypes);
                list.add(new DeMember(ObjectEncoder.createAttribute(factory, clazz, null, null, method), factory.loadDecoder(t)));
            }
            this.members = list.toArray(new DeMember[list.size()]);
            Arrays.sort(this.members);
        } catch (Exception ex) {
            throw new ConvertException(ex);
        }
    }

    /**
     * 对象格式: [0x1][short字段个数][字段名][字段值]...[0x2]
     *
     * @param in
     * @return
     */
    @Override
    public final T convertFrom(final R in) {
        final String clazz = in.readClassName();
        if (clazz != null && !clazz.isEmpty()) {
            try {
                return (T) factory.loadDecoder(Class.forName(clazz)).convertFrom(in);
            } catch (Exception ex) {
                throw new ConvertException(ex);
            }
        }
        if (in.readObjectB() == Reader.SIGN_NULL) return null;
        final T result = this.creator.create();
        final AtomicInteger index = new AtomicInteger();
        while (in.hasNext()) {
            DeMember member = in.readField(index, members);
            in.skipBlank();
            if (member == null) {
                in.skipValue(); //跳过该属性的值
            } else {
                member.read(in, result);
            }
            index.incrementAndGet();
        }
        in.readObjectE();
        return result;
    }

    @Override
    public final Type getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return "ObjectDecoder{" + "type=" + type + ", members=" + Arrays.toString(members) + '}';
    }
}
