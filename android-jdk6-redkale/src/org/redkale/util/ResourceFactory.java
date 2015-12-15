/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;
import javax.annotation.*;

/**
 * 如果Resource(name = "$") 表示资源name采用所属对象的name
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class ResourceFactory {

    public static final String RESOURCE_PARENT_NAME = "$";

    private static final Logger logger = Logger.getLogger(ResourceFactory.class.getSimpleName());

    private final ResourceFactory parent;

    private static final ResourceFactory instance = new ResourceFactory(null);

    private final ConcurrentHashMap<Type, Intercepter> interceptmap = new ConcurrentHashMap();

    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, ?>> store = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, ConcurrentHashMap<String, ?>> gencstore = new ConcurrentHashMap();

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
            ConcurrentHashMap<String, A> sub = new ConcurrentHashMap();
            sub.put(name, rs);
            store.put(clazz, sub);
        } else {
            map.put(name, rs);
        }
    }

    public <A> void register(final String name, final Type clazz, final A rs) {
        if (clazz instanceof Class) {
            register(name, (Class) clazz, rs);
            return;
        }
        ConcurrentHashMap map = this.gencstore.get(clazz);
        if (map == null) {
            ConcurrentHashMap<String, A> sub = new ConcurrentHashMap();
            sub.put(name, rs);
            gencstore.put(clazz, sub);
        } else {
            map.put(name, rs);
        }
    }

    public <A> A find(Class<? extends A> clazz) {
        return find("", clazz);
    }

    public <A> A find(String name, Type clazz) {
        Map<String, ?> map = this.gencstore.get(clazz);
        if (map != null) {
            A rs = (A) map.get(name);
            if (rs != null) return rs;
        }
        if (parent != null) return parent.find(name, clazz);
        return null;
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

    public <A> A findChild(final String name, final Class<? extends A> clazz) {
        A rs = find(name, clazz);
        if (rs != null) return rs;
        for (Map.Entry<Class<?>, ConcurrentHashMap<String, ?>> en : this.store.entrySet()) {  //不用forEach为兼容JDK 6
            if (!clazz.isAssignableFrom(en.getKey())) continue;
            Object v = en.getValue().get(name);
            if (v != null) return (A) v;
        }
        return null;
    }

    public <A> Map<String, A> find(final Pattern reg, Class<? extends A> clazz, A exclude) {
        Map<String, A> result = new LinkedHashMap();
        load(reg, clazz, exclude, result);
        return result;
    }

    private <A> void load(final Pattern reg, Class<? extends A> clazz, final A exclude, final Map<String, A> result) {
        ConcurrentHashMap<String, ?> map = this.store.get(clazz);
        if (map != null) {
            for (Map.Entry<String, ?> en : map.entrySet()) {  // 不用forEach为兼容JDK 6
                String x = en.getKey();
                Object y = en.getValue();
                if (y != exclude && reg.matcher(x).find() && result.get(x) == null) result.put(x, (A) y);
            }
        }
        if (parent != null) parent.load(reg, clazz, exclude, result);
    }

    public <T> boolean inject(final Object src) {
        return inject(src, null);
    }

    public <T> boolean inject(final Object src, final T attachment) {
        return inject(src, attachment, new ArrayList());
    }

    private <T> boolean inject(final Object src, final T attachment, final List<Object> list) {
        if (src == null) return false;
        try {
            list.add(src);
            Class clazz = src.getClass();
            do {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    final Class classtype = field.getType();
                    final Type genctype = field.getGenericType();
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
                        if (flag) this.inject(ns, attachment, list);
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    final String rcname = (rc.name().contains(RESOURCE_PARENT_NAME) && src instanceof Nameable) ? rc.name().replace(RESOURCE_PARENT_NAME, ((Nameable) src).name()) : rc.name();
                    Object rs = genctype == classtype ? null : find(rcname, genctype);
                    if (rs == null) {
                        if (Map.class.isAssignableFrom(classtype)) {
                            rs = find(Pattern.compile(rcname.isEmpty() ? ".*" : rcname), (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1], src);
                        } else if (rcname.startsWith("property.")) {
                            rs = find(rcname, String.class);
                        } else {
                            rs = find(rcname, classtype);
                        }
                    }
                    if (rs == null) {
                        Intercepter it = findIntercepter(field.getGenericType(), field);
                        if (it != null) it.invoke(this, src, field, attachment);
                        continue;
                    }
                    if (!rs.getClass().isPrimitive() && classtype.isPrimitive()) {
                        if (classtype == int.class) {
                            rs = Integer.decode(rs.toString());
                        } else if (classtype == long.class) {
                            rs = Long.decode(rs.toString());
                        } else if (classtype == short.class) {
                            rs = Short.decode(rs.toString());
                        } else if (classtype == boolean.class) {
                            rs = "true".equalsIgnoreCase(rs.toString());
                        } else if (classtype == byte.class) {
                            rs = Byte.decode(rs.toString());
                        } else if (classtype == float.class) {
                            rs = Float.parseFloat(rs.toString());
                        } else if (classtype == double.class) {
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

        public void invoke(ResourceFactory factory, Object src, Field field, Object attachment);
    }

}
