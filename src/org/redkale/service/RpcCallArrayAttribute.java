/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.Serializable;
import java.lang.reflect.Array;
import org.redkale.util.Attribute;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 * @param <T> 对象类型
 * @param <F> 字段类型
 */
@SuppressWarnings("unchecked")
public class RpcCallArrayAttribute<T, F> implements Attribute<T[], F> {

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
