/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.watch;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class WatchFactory {

    private static final WatchFactory instance = new WatchFactory("", null);

    private final List<WeakReference<WatchFactory>> chidren = new CopyOnWriteArrayList<>();

    private final List<WeakReference<WatchNode>> beans = new CopyOnWriteArrayList<>();

    private final String name;

    private final WatchFactory parent;

    private WatchFactory(String name, WatchFactory parent) {
        this.name = name;
        this.parent = parent;
    }

    public void register(WatchNode bean) {
        if (bean == null) return;
        checkName(bean.getName());
        beans.add(new WeakReference<>(bean));
    }

    public static WatchFactory root() {
        return instance;
    }

    public WatchFactory createChild(final String name) {
        WatchFactory child = new WatchFactory(name, this);
        this.chidren.add(new WeakReference<>(child));
        return child;
    }

    public List<WatchFactory> getChildren() {
        List<WatchFactory> result = new ArrayList<>();
        for (WeakReference<WatchFactory> ref : chidren) {
            WatchFactory rf = ref.get();
            if (rf != null) result.add(rf);
        }
        return result;
    }

    public List<WatchNode> getWatchNodes() {
        List<WatchNode> result = new ArrayList<>();
        for (WeakReference<WatchNode> ref : beans) {
            WatchNode rf = ref.get();
            if (rf != null) result.add(rf);
        }
        return result;
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
        return new WatchNumber(name, description, interval, v);
    }

    public void register(String name, LongSupplier supplier) {
        register(name, "", supplier);
    }

    public void register(String name, String description, LongSupplier supplier) {
        register(new WatchSupplier(name, description, supplier));
    }

    public void checkName(String name) {
        if (name == null || (!name.isEmpty() && !name.matches("^[a-zA-Z0-9_;/\\-\\.\\[\\]\\(\\)]+$")) || name.contains("//")) {
            throw new IllegalArgumentException("Watch.name(" + name + ") contains illegal character, must be (a-z,A-Z,0-9,/,_,.,(,),-,[,]) and cannot contains //");
        }
    }

    protected <T> boolean inject(final Object src) {
        return inject(src, null);
    }

    protected <T> boolean inject(final Object src, final T attachment) {
        return inject(src, attachment, new ArrayList<>());
    }

    private <T> boolean inject(final Object src, final T attachment, final List<Object> list) {
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
                    if (wo == null && !WatchNode.class.isAssignableFrom(type)) continue;
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
