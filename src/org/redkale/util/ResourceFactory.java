/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.*;
import java.util.regex.Pattern;
import javax.annotation.Resource;

/**
 *
 * 依赖注入功能主类   <br>
 *
 * 如果&#64;Resource(name = "$") 表示资源name采用所属对象的name  <br>
 * 如果没有&#64;Resource 则会取对象的getResourceName()方法值(若存在)
 * <blockquote><pre>
 * name规则:
 *    1: "$"有特殊含义, 不能表示"$"资源本身
 *    2: 只能是字母、数字、(短横)-、(下划线)_、点(.)的组合
 * </pre></blockquote>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class ResourceFactory {

    public static final String RESOURCE_PARENT_NAME = "$";

    private static final Logger logger = Logger.getLogger(ResourceFactory.class.getSimpleName());

    private final ResourceFactory parent;

    private static final ResourceFactory instance = new ResourceFactory(null);

    private final List<WeakReference<ResourceFactory>> chidren = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Type, ResourceLoader> loadermap = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, ConcurrentHashMap<String, ResourceEntry>> store = new ConcurrentHashMap();

    private ResourceFactory(ResourceFactory parent) {
        this.parent = parent;
    }

    public static ResourceFactory root() {
        return instance;
    }

    public ResourceFactory createChild() {
        ResourceFactory child = new ResourceFactory(this);
        this.chidren.add(new WeakReference<>(child));
        return child;
    }

    public List<ResourceFactory> getChildren() {
        List<ResourceFactory> result = new ArrayList<>();
        for (WeakReference<ResourceFactory> ref : chidren) {
            ResourceFactory rf = ref.get();
            if (rf != null) result.add(rf);
        }
        return result;
    }

    public void release() {
        this.store.clear();
    }

    public void checkName(String name) {
        if (name == null || (!name.isEmpty() && !name.matches("^[a-zA-Z0-9_;\\-\\.\\[\\]\\(\\)]+$"))) {
            throw new IllegalArgumentException("Resource.name(" + name + ") contains illegal character, must be (a-z,A-Z,0-9,_,.,(,),-,[,])");
        }
    }

    public <A> A register(final Class<? extends A> clazz, final A rs) {
        return register(true, clazz, rs);
    }

    public <A> A register(final boolean autoSync, final Class<? extends A> clazz, final A rs) {
        return register(autoSync, "", clazz, rs);
    }

    public <A> A register(final A rs) {
        return register(true, rs);
    }

    public <A> A register(final boolean autoSync, final A rs) {
        if (rs == null) return null;
        return (A) register(autoSync, "", rs);
    }

    public void register(final Type clazz, final ResourceLoader rs) {
        if (clazz == null || rs == null) return;
        loadermap.put(clazz, rs);
    }

    public void register(final ResourceLoader rs, final Type... clazzs) {
        if (clazzs == null || rs == null) return;
        for (Type clazz : clazzs) {
            loadermap.put(clazz, rs);
        }
    }

    public void register(final String name, final boolean value) {
        register(true, name, boolean.class, value);
    }

    public void register(final boolean autoSync, final String name, final boolean value) {
        register(autoSync, name, boolean.class, value);
    }

    public void register(final String name, final byte value) {
        register(true, name, byte.class, value);
    }

    public void register(final boolean autoSync, final String name, final byte value) {
        register(autoSync, name, byte.class, value);
    }

    public void register(final String name, final short value) {
        register(true, name, short.class, value);
    }

    public void register(final boolean autoSync, final String name, final short value) {
        register(autoSync, name, short.class, value);
    }

    public void register(final String name, final int value) {
        register(true, name, int.class, value);
    }

    public void register(final boolean autoSync, final String name, final int value) {
        register(autoSync, name, int.class, value);
    }

    public void register(final String name, final float value) {
        register(true, name, float.class, value);
    }

    public void register(final boolean autoSync, final String name, final float value) {
        register(autoSync, name, float.class, value);
    }

    public void register(final String name, final long value) {
        register(true, name, long.class, value);
    }

    public void register(final boolean autoSync, final String name, final long value) {
        register(autoSync, name, long.class, value);
    }

    public void register(final String name, final double value) {
        register(true, name, double.class, value);
    }

    public void register(final boolean autoSync, final String name, final double value) {
        register(autoSync, name, double.class, value);
    }

    public <A> A register(final String name, final A rs) {
        return register(true, name, rs);
    }

    public <A> A register(final boolean autoSync, final String name, final A rs) {
        checkName(name);
        final Class<?> claz = rs.getClass();
        ResourceType rtype = claz.getAnnotation(ResourceType.class);
        if (rtype == null) {
            return (A) register(autoSync, name, claz, rs);
        } else {
            A old = null;
            for (Class cl : rtype.value()) {
                A t = (A) register(autoSync, name, cl, rs);
                if (t != null) old = t;
            }
            return old;
        }
    }

    public <A> A register(final String name, final Class<? extends A> clazz, final A rs) {
        return register(true, name, clazz, rs);
    }

    public <A> A register(final String name, final Type clazz, final A rs) {
        return register(true, name, clazz, rs);
    }

    public <A> A register(final boolean autoSync, final String name, final Type clazz, final A rs) {
        checkName(name);
        ConcurrentHashMap<String, ResourceEntry> map = this.store.get(clazz);
        if (map == null) {
            synchronized (clazz) {
                map = this.store.get(clazz);
                if (map == null) {
                    map = new ConcurrentHashMap();
                    store.put(clazz, map);
                }
            }
        }
        ResourceEntry re = map.get(name);
        if (re == null) {
            map.put(name, new ResourceEntry(rs));
        } else {
            map.put(name, new ResourceEntry(rs, name, re.elements, autoSync));
        }
        return re == null ? null : (A) re.value;
    }

    public <A> A find(Class<? extends A> clazz) {
        return find("", clazz);
    }

    public <A> A find(String name, Type clazz) {
        ResourceEntry re = findEntry(name, clazz);
        return re == null ? null : (A) re.value;
    }

    private ResourceEntry findEntry(String name, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            ResourceEntry re = map.get(name);
            if (re != null) return re;
        }
        if (parent != null) return parent.findEntry(name, clazz);
        return null;
    }

    public <A> List<A> query(Class<? extends A> clazz) {
        return query(new ArrayList<A>(), clazz);
    }

    public <A> List<A> query(Type clazz) {
        return query(new ArrayList<A>(), clazz);
    }

    private <A> List<A> query(final List<A> list, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            for (ResourceEntry re : map.values()) {
                if (re.value != null) list.add((A) re.value);
            }
        }
        if (parent != null) query(list, clazz);
        return list;
    }

    public <A> A find(String name, Class<? extends A> clazz) {
        ResourceEntry<A> re = findEntry(name, clazz);
        return re == null ? null : re.value;
    }

    private <A> ResourceEntry<A> findEntry(String name, Class<? extends A> clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            ResourceEntry rs = map.get(name);
            if (rs != null) return rs;
        }
        if (parent != null) return parent.findEntry(name, clazz);
        return null;
    }

    public <A> A findChild(final String name, final Class<? extends A> clazz) {
        A rs = find(name, clazz);
        if (rs != null) return rs;
        for (Map.Entry<Type, ConcurrentHashMap<String, ResourceEntry>> en : this.store.entrySet()) {  //不用forEach为兼容JDK 6
            if (!(en.getKey() instanceof Class)) continue;
            if (!clazz.isAssignableFrom((Class) en.getKey())) continue;
            ResourceEntry v = en.getValue().get(name);
            if (v != null) return (A) v.value;
        }
        return null;
    }

    private <A> void load(final Pattern reg, Class<? extends A> clazz, final A exclude, final Map<String, A> result) {
        ConcurrentHashMap<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            for (Map.Entry<String, ResourceEntry> en : map.entrySet()) {  // 不用forEach为兼容JDK 6
                String x = en.getKey();
                ResourceEntry re = en.getValue();
                if (re == null) continue;
                Object y = re.value;
                if (y != exclude && reg.matcher(x).find() && result.get(x) == null) result.put(x, (A) y);
            }
        }
        if (parent != null) parent.load(reg, clazz, exclude, result);
    }

    public <T> boolean inject(final Object src) {
        return inject(src, null);
    }

    public <T> boolean inject(final Object src, final T attachment) {
        return inject(src, attachment, null);
    }

    public <T> boolean inject(final Object src, final BiConsumer<Object, Field> consumer) {
        return inject(src, null, consumer);
    }

    public <T> boolean inject(final Object src, final T attachment, final BiConsumer<Object, Field> consumer) {
        return inject(src, attachment, consumer, new ArrayList());
    }

    private <T> boolean inject(final Object src, final T attachment, final BiConsumer<Object, Field> consumer, final List<Object> list) {
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
                    if (rc == null) {  //深度注入
                        boolean flag = true; //是否没有重复
                        Object ns = field.get(src);
                        for (Object o : list) {
                            if (o == ns) {
                                flag = false;
                                break;
                            }
                        }
                        if (ns == null) continue;
                        if (ns.getClass().isPrimitive() || ns.getClass().isArray() || ns.getClass().getName().startsWith("java")) continue;
                        if (flag) this.inject(ns, attachment, consumer, list);
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    if (consumer != null) consumer.accept(src, field);
                    String tname = rc.name();
                    if (tname.contains(RESOURCE_PARENT_NAME)) {
                        Resource res = src.getClass().getAnnotation(Resource.class);
                        if (res == null) {
                            if (src instanceof Resourcable) {
                                tname = tname.replace(RESOURCE_PARENT_NAME, ((Resourcable) src).resourceName());
                            } else {
                                logger.log(Level.SEVERE, src.getClass().getName() + " not found @Resource on Class or not implements Resourcable");
                            }
                        } else {
                            tname = res.name();
                        }

                    }
                    final String rcname = tname;
                    ResourceEntry re = findEntry(rcname, genctype);
                    if (re == null) {
                        if (rcname.startsWith("property.")) {
                            re = findEntry(rcname, String.class);
                        } else {
                            re = findEntry(rcname, classtype);
                        }
                    }
                    if (re == null) {
                        ResourceLoader it = findLoader(field.getGenericType(), field);
                        if (it != null) {
                            it.load(this, src, rcname, field, attachment);
                            re = findEntry(rcname, genctype);
                        }
                    }
                    if (re == null) {
                        register(rcname, genctype, null); //自动注入null的值
                        re = findEntry(rcname, genctype);
                    }
                    if (re == null) continue;
                    re.elements.add(new ResourceElement<>(src, field));

                    Object rs = re.value;
                    if (rs != null && !rs.getClass().isPrimitive() && classtype.isPrimitive()) {
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
                    if (rs != null) field.set(src, rs);
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "inject " + src + " error", ex);
            return false;
        }
    }

    private ResourceLoader findMatchLoader(Type ft, Field field) {
        ResourceLoader it = this.loadermap.get(ft);
        if (it == null) it = this.loadermap.get(field.getType());
        if (it != null) return it;
        return parent == null ? null : parent.findMatchLoader(ft, field);
    }

    private ResourceLoader findRegxLoader(Type ft, Field field) {
        Class c = field.getType();
        for (Map.Entry<Type, ResourceLoader> en : this.loadermap.entrySet()) {
            Type t = en.getKey();
            if (t == ft) return en.getValue();
            if (t instanceof Class && (((Class) t)).isAssignableFrom(c)) return en.getValue();
        }
        return parent == null ? null : parent.findRegxLoader(ft, field);
    }

    private ResourceLoader findLoader(Type ft, Field field) {
        ResourceLoader it = this.findMatchLoader(ft, field);
        return it == null ? findRegxLoader(ft, field) : it;
    }

    private static class ResourceEntry<T> {

        public final T value;

        public final List<ResourceElement> elements;

        public ResourceEntry(T value) {
            this.value = value;
            this.elements = new CopyOnWriteArrayList<>();
        }

        public ResourceEntry(T value, final String name, final List<ResourceElement> elements, boolean sync) {
            this.value = value;
            this.elements = elements == null ? new CopyOnWriteArrayList<>() : elements;
            if (sync && elements != null && !elements.isEmpty()) {

                for (ResourceElement element : elements) {
                    Object dest = element.dest.get();
                    if (dest == null) continue;
                    Object rs = value;
                    final Class classtype = element.fieldType;
                    if (rs != null && !rs.getClass().isPrimitive() && classtype.isPrimitive()) {
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
                    if (rs == null && classtype.isPrimitive()) rs = Array.get(Array.newInstance(classtype, 1), 0);
                    Object oldVal = null;
                    if (element.listener != null) {
                        try {
                            oldVal = element.field.get(dest);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        element.field.set(dest, rs);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (element.listener != null) {
                        try {
                            element.listener.invoke(dest, name, rs, oldVal);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, dest + " resource change listener error", e);
                        }
                    }
                }
            }
        }
    }

    private static class ResourceElement<T> {

        private static final HashMap<Class, Method> listenerMethods = new HashMap<>(); //不使用ConcurrentHashMap是因为value不能存null

        public final WeakReference<T> dest;

        public final Field field; //Resource 字段

        public final Class fieldType;

        public final Method listener;

        public ResourceElement(T dest, Field field) {
            this.dest = new WeakReference(dest);
            this.field = field;
            this.fieldType = field.getType();
            Class t = dest.getClass();
            String tn = t.getName();
            this.listener = tn.startsWith("java.") || tn.startsWith("javax.") ? null : findListener(t);
        }

        private static synchronized Method findListener(Class clazz) {
            Class loop = clazz;
            Method m = null;
            do {
                for (Method method : loop.getDeclaredMethods()) {
                    if (method.getAnnotation(ResourceListener.class) != null
                        && method.getParameterCount() == 3
                        && String.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        m = method;
                        m.setAccessible(true);
                        break;
                    }
                }
            } while ((loop = loop.getSuperclass()) != Object.class);
            listenerMethods.put(clazz, m);
            return m;
        }
    }

    @FunctionalInterface
    public static interface ResourceLoader {

        public void load(ResourceFactory factory, Object src, String resourceName, Field field, Object attachment);
    }

}
