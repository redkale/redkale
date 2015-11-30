/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.watch;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 *
 * @author zhangjx
 */
public final class WatchFactory {

    private static final WatchFactory instance = new WatchFactory(null);

    private final List<WeakReference<WatchNode>> beans = new CopyOnWriteArrayList<>();

    private final WatchFactory parent;

    private WatchFactory(WatchFactory parent) {
        this.parent = parent;
    }

    public void register(WatchNode bean) {
        if (bean != null) beans.add(new WeakReference<>(bean));
    }

    public static WatchFactory root() {
        return instance;
    }

    public WatchFactory createChild() {
        return new WatchFactory(this);
    }

    public WatchNumber createWatchNumber(String name) {
        return createWatchNumber(name, "", false, 0);
    }

    public WatchNumber createWatchNumber(String name, boolean interval) {
        return createWatchNumber(name, "", interval, 0);
    }

    public WatchNumber createWatchNumber(String name, String description) {
        return createWatchNumber(name, description, false, 0);
    }

    public WatchNumber createWatchNumber(String name, String description, long v) {
        return createWatchNumber(name, description, false, 0);
    }

    public WatchNumber createWatchNumber(String name, String description, boolean interval) {
        return createWatchNumber(name, description, interval, 0);
    }

    public WatchNumber createWatchNumber(String name, String description, boolean interval, long v) {
        WatchNumber bean = new WatchNumber(name, description, interval, v);
        register(bean);
        return bean;
    }

    public void register(String name, LongSupplier supplier) {
        register(name, "", supplier);
    }

    public void register(String name, String description, LongSupplier supplier) {
        register(new WatchSupplier(name, description, supplier));
    }

    public boolean inject(final Object src) {
        return inject(src, new ArrayList<>());
    }

    private boolean inject(final Object src, final List<Object> list) {
        if (src == null) return false;
        try {
            list.add(src);
            Class clazz = src.getClass();
            do {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    field.setAccessible(true);
                    final Class type = field.getType();
                    Watchable wo = field.getAnnotation(Watchable.class);

                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
