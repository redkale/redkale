/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 * @param <T>
 * @param <F>
 */
public final class EntityCallAttribute<T, F> implements Attribute<T[], F> {

    public static final EntityCallAttribute instance = new EntityCallAttribute();

    private static final ConcurrentHashMap<Class, Attribute> attributes = new ConcurrentHashMap<>();

    private static Attribute load(final Class clazz) {
        Attribute rs = attributes.get(clazz);
        if (rs != null) return rs;
        synchronized (attributes) {
            rs = attributes.get(clazz);
            if (rs == null) {
                Class cltmp = clazz;
                do {
                    for (Field field : cltmp.getDeclaredFields()) {
                        if (field.getAnnotation(javax.persistence.Id.class) == null) continue;
                        try {
                            rs = Attribute.create(cltmp, field);
                            attributes.put(clazz, rs);
                            return rs;
                        } catch (RuntimeException e) {
                        }
                    }
                } while ((cltmp = cltmp.getSuperclass()) != Object.class);
            }
            return rs;
        }
    }

    @Override
    public Class<? extends F> type() {
        return (Class<F>) Object.class;
    }

    @Override
    public Class<T[]> declaringClass() {
        return (Class<T[]>) (Class) Object[].class;
    }

    @Override
    public String field() {
        return "";
    }

    @Override
    public F get(final T[] objs) {
        if (objs == null || objs.length == 0) return null;
        final Attribute<T, Serializable> attr = (Attribute<T, Serializable>) load(objs[0].getClass());
        final Object keys = Array.newInstance(attr.type(), objs.length);
        for (int i = 0; i < objs.length; i++) {
            Array.set(keys, i, attr.get(objs[i]));
        }
        return (F) keys;
    }

    @Override
    public void set(final T[] objs, final F keys) {
        if (objs == null || objs.length == 0) return;
        final Attribute<T, F> attr = (Attribute<T, F>) load(objs[0].getClass());
        for (int i = 0; i < objs.length; i++) {
            attr.set(objs[i], (F) Array.get(keys, i));
        }
    }

}
