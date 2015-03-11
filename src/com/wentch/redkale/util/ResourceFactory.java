/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;
import java.util.regex.Pattern;
import javax.annotation.Resource;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class ResourceFactory {

    private static final Logger logger = Logger.getLogger(ResourceFactory.class.getSimpleName());

    private final ResourceFactory parent;

    private static final ResourceFactory instance = new ResourceFactory(null);

    private final ConcurrentHashMap<Type, Intercepter> interceptmap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, ?>> store = new ConcurrentHashMap<>();

    private ResourceFactory(ResourceFactory parent) {
        this.parent = parent;
    }

    public static ResourceFactory root() {
        return instance;
    }

    public ResourceFactory createChild() {
        return new ResourceFactory(this);
    }

    public void release() {
        this.store.clear();
    }

    public void register(final Class clazz, final Object rs) {
        register("", clazz, rs);
    }

    public void register(final Object rs) {
        if (rs != null) register("", rs.getClass(), rs);
    }

    public void add(final Type clazz, final Intercepter rs) {
        if (clazz == null || rs == null) return;
        interceptmap.put(clazz, rs);
    }

    public void register(final String name, final Object rs) {
        register(name, rs.getClass(), rs);
    }

    public <A> void register(final String name, final Class<? extends A> clazz, final A rs) {
        ConcurrentHashMap map = this.store.get(clazz);
        if (map == null) {
            ConcurrentHashMap<String, A> sub = new ConcurrentHashMap<>();
            sub.put(name, rs);
            store.put(clazz, sub);
        } else {
            map.put(name, rs);
        }
    }

    public <A> A find(Class<? extends A> clazz) {
        return find("", clazz);
    }

    public <A> A find(String name, Class<? extends A> clazz) {
        Map<String, ?> map = this.store.get(clazz);
        if (map != null) {
            A rs = (A) map.get(name);
            if (rs != null) return rs;
        }
        if (parent != null) return parent.find(name, clazz);
        return null;
    }

    public <A> A findChild(String name, Class<? extends A> clazz) {
        A rs = find(name, clazz);
        if (rs != null) return rs;
        Optional<Map.Entry<Class<?>, ConcurrentHashMap<String, ?>>> opt = this.store.entrySet().stream()
                .filter(x -> clazz.isAssignableFrom(x.getKey()) && x.getValue().containsKey(name))
                .findFirst();
        return opt.isPresent() ? (A) opt.get().getValue().get(name) : null;
    }

    public <A> Map<String, A> find(final Pattern reg, Class<? extends A> clazz, A exclude) {
        Map<String, A> result = new LinkedHashMap<>();
        load(reg, clazz, exclude, result);
        return result;
    }

    private <A> void load(final Pattern reg, Class<? extends A> clazz, final A exclude, final Map<String, A> result) {
        ConcurrentHashMap<String, ?> map = this.store.get(clazz);
        if (map != null) {
            map.forEach((x, y) -> {
                if (y != exclude && reg.matcher(x).find() && result.get(x) == null) result.put(x, (A) y);
            });
        }
        if (parent != null) parent.load(reg, clazz, exclude, result);
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
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    final Class type = field.getType();
                    Resource rc = field.getAnnotation(Resource.class);
                    if (rc == null) {
                        boolean flag = true;
                        Object ns = field.get(src);
                        for (Object o : list) {
                            if (o == ns) {
                                flag = false;
                                break;
                            }
                        }
                        if (ns == null) continue;
                        if (ns.getClass().isPrimitive() || ns.getClass().isArray() || ns.getClass().getName().startsWith("java")) continue;
                        if (flag) this.inject(ns, list);
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    Object rs;
                    if (Map.class.isAssignableFrom(type)) {
                        rs = find(Pattern.compile(rc.name().isEmpty() ? ".+" : rc.name()), (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1], src);
                    } else {
                        if (rc.name().startsWith("property.")) {
                            rs = find(rc.name(), String.class);
                        } else {
                            rs = find(rc.name(), type);
                        }
                    }
                    if (rs == null) {
                        Intercepter it = findIntercepter(field.getGenericType(), field);
                        if (it != null) it.invoke(this, src, field);
                        continue;
                    }
                    if (!rs.getClass().isPrimitive() && type.isPrimitive()) {
                        if (type == int.class) {
                            rs = Integer.decode(rs.toString());
                        } else if (type == long.class) {
                            rs = Long.decode(rs.toString());
                        } else if (type == short.class) {
                            rs = Short.decode(rs.toString());
                        } else if (type == boolean.class) {
                            rs = "true".equalsIgnoreCase(rs.toString());
                        } else if (type == byte.class) {
                            rs = Byte.decode(rs.toString());
                        } else if (type == float.class) {
                            rs = Float.parseFloat(rs.toString());
                        } else if (type == double.class) {
                            rs = Double.parseDouble(rs.toString());
                        }
                    }
                    field.set(src, rs);
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (Exception ex) {
            logger.log(Level.FINER, "inject " + src + " error", ex);
            return false;
        }
    }

    private Intercepter findIntercepter(Type ft, Field field) {
        Intercepter it = this.interceptmap.get(ft);
        if (it != null) return it;
        Class c = field.getType();
        for (Map.Entry<Type, Intercepter> en : this.interceptmap.entrySet()) {
            Type t = en.getKey();
            if (t == ft) return en.getValue();
            if (t instanceof Class && (((Class) t)).isAssignableFrom(c)) return en.getValue();
        }
        return parent == null ? null : parent.findIntercepter(ft, field);
    }

    public static interface Intercepter {

        public void invoke(ResourceFactory factory, Object src, Field field);
    }

}
