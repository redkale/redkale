/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.util.Attribute;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class RpcCallAttribute implements Attribute<Object, Serializable> {

    public static final RpcCallAttribute instance = new RpcCallAttribute();

    private static final ConcurrentHashMap<Class, Attribute> attributes = new ConcurrentHashMap<>();

    static <T> Attribute<T, Serializable> load(final Class clazz) {
        Attribute rs = attributes.get(clazz);
        if (rs != null) return rs;
        synchronized (attributes) {
            rs = attributes.get(clazz);
            if (rs == null) {
                Class cltmp = clazz;
                do {
                    for (Field field : cltmp.getDeclaredFields()) {
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
    public Class<Serializable> type() {
        return Serializable.class;
    }

    @Override
    public Class<Object> declaringClass() {
        return Object.class;
    }

    @Override
    public String field() {
        return "";
    }

    @Override
    public Serializable get(final Object obj) {
        if (obj == null) return null;
        return load(obj.getClass()).get(obj);
    }

    @Override
    public void set(final Object obj, final Serializable key) {
        if (obj == null) return;
        load(obj.getClass()).set(obj, key);
    }

    @SuppressWarnings("unchecked")
    public static class RpcCallArrayAttribute<T, F> implements Attribute<T[], F> {

        public static final RpcCallArrayAttribute instance = new RpcCallArrayAttribute();

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
            final Attribute<T, Serializable> attr = RpcCallAttribute.load(objs[0].getClass());
            final Object keys = Array.newInstance(attr.type(), objs.length);
            for (int i = 0; i < objs.length; i++) {
                Array.set(keys, i, attr.get(objs[i]));
            }
            return (F) keys;
        }

        @Override
        public void set(final T[] objs, final F keys) {
            if (objs == null || objs.length == 0) return;
            final Attribute<T, Serializable> attr = RpcCallAttribute.load(objs[0].getClass());
            for (int i = 0; i < objs.length; i++) {
                attr.set(objs[i], (Serializable) Array.get(keys, i));
            }
        }

    }

}
