/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.annotation.ResourceListener;
import org.redkale.annotation.ResourceType;
import org.redkale.convert.*;

/**
 *
 * 依赖注入功能主类   <br>
 *
 * 如果&#64;Resource(name = "$") 表示资源name采用所属对象的name  <br>
 * 如果没有&#64;Resource且对象实现了Resourcable, 则会取对象的resourceName()方法值
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

    private static final boolean skipCheckRequired = Boolean.getBoolean("redkale.resource.skip.check");

    private static final Logger logger = Logger.getLogger(ResourceFactory.class.getSimpleName());

    private final ReentrantLock lock = new ReentrantLock();

    private final ResourceFactory parent;

    private final List<WeakReference<ResourceFactory>> chidren = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Type, ResourceAnnotationProvider> resAnnotationProviderMap = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, ResourceTypeLoader> resTypeLoaderMap = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, ConcurrentHashMap<String, ResourceEntry>> store = new ConcurrentHashMap();

    private ResourceFactory(ResourceFactory parent) {
        this.parent = parent;
        if (parent == null) {
            ServiceLoader<ResourceAnnotationProvider> loaders = ServiceLoader.load(ResourceAnnotationProvider.class);
            RedkaleClassLoader.putServiceLoader(ResourceAnnotationProvider.class);
            Iterator<ResourceAnnotationProvider> it = loaders.iterator();
            while (it.hasNext()) {
                ResourceAnnotationProvider ril = it.next();
                RedkaleClassLoader.putReflectionPublicConstructors(ril.getClass(), ril.getClass().getName());
                this.resAnnotationProviderMap.put(ril.annotationType(), ril);
            }
        }
    }

    /**
     * 创建一个根ResourceFactory
     *
     * @return ResourceFactory
     */
    public static ResourceFactory create() {
        return new ResourceFactory(null);
    }

    /**
     * 创建ResourceFactory子节点
     *
     * @return ResourceFactory
     */
    public ResourceFactory createChild() {
        ResourceFactory child = new ResourceFactory(this);
        this.chidren.add(new WeakReference<>(child));
        return child;
    }

    /**
     * 获取所有ResourceFactory子节点
     *
     * @return List
     */
    public List<ResourceFactory> getChildren() {
        List<ResourceFactory> result = new ArrayList<>();
        for (WeakReference<ResourceFactory> ref : chidren) {
            ResourceFactory rf = ref.get();
            if (rf != null) {
                result.add(rf);
            }
        }
        return result;
    }

    /**
     * 清空当前ResourceFactory注入资源
     *
     */
    public void release() {
        this.store.clear();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    /**
     * 检查资源名是否合法
     * <blockquote><pre>
     * name规则:
     *    1: "$"有特殊含义, 表示资源本身，"$"不能单独使用
     *    2: 只能是字母、数字、(短横)-、(下划线)_、点(.)、小括号、中括号的组合
     * </pre></blockquote>
     *
     * @param name String
     */
    public static void checkResourceName(String name) {
        if (name == null || (!name.isEmpty() && !name.matches("^[a-zA-Z0-9_;\\-\\.\\[\\]\\(\\)]+$"))) {
            throw new IllegalArgumentException("name(" + name + ") contains illegal character, must be (a-z,A-Z,0-9,_,.,(,),-,[,])");
        }
    }

    public static Class getResourceType(Type type) {
        Class<?> clazz = TypeToken.typeToClass(type);
        ResourceType rt = clazz.getAnnotation(ResourceType.class);
        if (rt != null) {
            return rt.value();
        }
        org.redkale.util.ResourceType rt2 = clazz.getAnnotation(org.redkale.util.ResourceType.class);
        return rt2 == null ? clazz : rt2.value();
    }

    /**
     * 将对象指定类型且name=""注入到资源池中，并同步已被注入的资源
     *
     * @param <A>   泛型
     * @param clazz 资源类型
     * @param rs    资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final Class<? extends A> clazz, final A rs) {
        return register(true, clazz, rs);
    }

    /**
     * 将对象指定类型且name=""注入到资源池中
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param clazz    资源类型
     * @param rs       资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final Class<? extends A> clazz, final A rs) {
        return register(autoSync, "", clazz, rs);
    }

    /**
     * 将对象以name=""注入到资源池中，并同步已被注入的资源
     *
     * @param <A> 泛型
     * @param rs  资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final A rs) {
        return register(true, rs);
    }

    /**
     * 将对象以name=""注入到资源池中，并同步已被注入的资源
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param rs       资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final A rs) {
        if (rs == null) {
            return null;
        }
        return (A) register(autoSync, "", rs);
    }

    /**
     * 将boolean对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final boolean value) {
        register(true, name, boolean.class, value);
    }

    /**
     * 将boolean对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final boolean value) {
        register(autoSync, name, boolean.class, value);
    }

    /**
     * 将byte对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final byte value) {
        register(true, name, byte.class, value);
    }

    /**
     * 将byte对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final byte value) {
        register(autoSync, name, byte.class, value);
    }

    /**
     * 将short对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final short value) {
        register(true, name, short.class, value);
    }

    /**
     * 将short对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final short value) {
        register(autoSync, name, short.class, value);
    }

    /**
     * 将int对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final int value) {
        register(true, name, int.class, value);
    }

    /**
     * 将int对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final int value) {
        register(autoSync, name, int.class, value);
    }

    /**
     * 将float对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final float value) {
        register(true, name, float.class, value);
    }

    /**
     * 将float对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final float value) {
        register(autoSync, name, float.class, value);
    }

    /**
     * 将long对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final long value) {
        register(true, name, long.class, value);
    }

    /**
     * 将long对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final long value) {
        register(autoSync, name, long.class, value);
    }

    /**
     * 将double对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param name  资源名
     * @param value 资源值
     *
     */
    public void register(final String name, final double value) {
        register(true, name, double.class, value);
    }

    /**
     * 将double对象以指定资源名注入到资源池中
     *
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param value    资源值
     *
     */
    public void register(final boolean autoSync, final String name, final double value) {
        register(autoSync, name, double.class, value);
    }

    /**
     * 将对象以指定资源名注入到资源池中，并同步已被注入的资源
     *
     * @param <A>  泛型
     * @param name 资源名
     * @param val  资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final A val) {
        return register(true, name, val);
    }

    /**
     * 将对象以指定资源名注入到资源池中
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param val      资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final String name, final A val) {
        checkResourceName(name);
        final Class<?> claz = val.getClass();

        Class rt = null;
        ResourceType rtype = claz.getAnnotation(ResourceType.class);
        if (rtype != null) {
            rt = rtype.value();
        } else {
            org.redkale.util.ResourceType rtype2 = claz.getAnnotation(org.redkale.util.ResourceType.class);
            if (rtype2 != null) {
                rt = rtype2.value();
            }
        }
        if (rt == null) {
            return (A) register(autoSync, name, claz, val);
        } else {
            A old = null;
            A t = (A) register(autoSync, name, rt, val);
            if (t != null) {
                old = t;
            }
            return old;
        }
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中，并同步已被注入的资源
     *
     * @param <A>   泛型
     * @param name  资源名
     * @param clazz 资源类型
     * @param val   资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final Class<? extends A> clazz, final A val) {
        return register(true, name, clazz, val);
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中，并同步已被注入的资源
     *
     * @param <A>   泛型
     * @param name  资源名
     * @param clazz 资源类型
     * @param val   资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final Type clazz, final A val) {
        return register(true, name, clazz, val);
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param clazz    资源类型
     * @param val      资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final String name, final Type clazz, final A val) {
        return register(autoSync, name, clazz, val, null);
    }

    /**
     * 将多个以指定资源名的String对象注入到资源池中
     *
     * @param properties 资源键值对
     *
     */
    public void register(Properties properties) {
        register(properties, null, null);
    }

    /**
     * 将多个以指定资源名的String对象注入到资源池中
     *
     * @param <A>             泛型
     * @param properties      资源键值对
     * @param environmentName 额外的资源名
     * @param environmentType 额外的类名
     *
     */
    public <A> void register(Properties properties, String environmentName, Class<A> environmentType) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        List<ResourceChangeWrapper> wrappers = new ArrayList<>();
        List<ResourceEvent> environmentEventList = new ArrayList<>();
        properties.forEach((k, v) -> {
            Object old = register(true, k.toString(), String.class, v, wrappers);
            if (!Objects.equals(v, old)) {
                environmentEventList.add(ResourceEvent.create(k.toString(), v, old));
            }
        });
        Map<Object, Method> envListenMap = new LinkedHashMap<>();
        if (!environmentEventList.isEmpty() && environmentName != null && environmentType != null) {
            ResourceEntry<A> entry = findEntry(environmentName, environmentType);
            if (entry != null && entry.elements != null) {
                for (ResourceElement element : entry.elements) {
                    Object dest = element.dest.get();
                    if (dest != null && element.listener != null) {
                        envListenMap.put(dest, element.listener);
                    }
                }
            }
        }
        if (wrappers.isEmpty() && envListenMap.isEmpty()) {
            return;
        }
        Map<Object, List<ResourceChangeWrapper>> map = new LinkedHashMap<>();
        for (ResourceChangeWrapper wrapper : wrappers) {
            map.computeIfAbsent(wrapper.dest, k -> new ArrayList<>()).add(wrapper);
        }
        if (!map.isEmpty()) {
            map.forEach((dest, list) -> {
                if (envListenMap.containsKey(dest)) {
                    return; //跳过含有@Resource Environment字段的对象
                }
                Method listener = list.get(0).listener;
                try {
                    ResourceEvent[] events = new ResourceEvent[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        events[i] = list.get(i).event;
                    }
                    Object[] ps = new Object[]{events};
                    listener.invoke(dest, ps);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, dest + " resource change listener error", e);
                }
            });
        }
        if (!envListenMap.isEmpty()) { //含有@Resource Environment字段的对象进行变更响应
            ResourceEvent[] environmentEvents = environmentEventList.toArray(new ResourceEvent[environmentEventList.size()]);
            envListenMap.forEach((dest, listener) -> {
                try {
                    Object[] ps = new Object[]{environmentEvents};
                    listener.invoke(dest, ps);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, dest + " resource change listener error", e);
                }
            });
        }
    }

    private <A> A register(final boolean autoSync, final String name, final Type clazz, final A val, List<ResourceChangeWrapper> wrappers) {
        checkResourceName(name);
        Class clz = TypeToken.typeToClass(clazz);
        if (clz != null && !clz.isPrimitive() && val != null && !clz.isAssignableFrom(val.getClass())) {
            throw new RedkaleException(clz + "not isAssignableFrom (" + val + ") class " + val.getClass());
        }
        ConcurrentHashMap<String, ResourceEntry> map = this.store.computeIfAbsent(clazz, k -> new ConcurrentHashMap());
        ResourceEntry re = map.get(name);
        if (re == null) {
            map.put(name, new ResourceEntry(name, val));
        } else {
            map.put(name, new ResourceEntry(name, val, re.elements, wrappers, autoSync));
        }
        return re == null ? null : (A) re.value;
    }

    /**
     * 判断是否包含指定资源名和资源类型的资源对象
     *
     * @param <A>       泛型
     * @param recursive 是否遍历父节点
     * @param name      资源名
     * @param clazz     资源类型
     *
     * @return 是否存在
     */
    public <A> boolean contains(boolean recursive, String name, Class<? extends A> clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        return map == null ? ((recursive && parent != null) ? parent.contains(recursive, name, clazz) : false) : map.containsKey(name);
    }

    /**
     * 查找指定资源名和资源类型的资源对象所在的ResourceFactory， 没有则返回null
     *
     * @param name  资源名
     * @param clazz 资源类型
     *
     * @return ResourceFactory
     */
    public ResourceFactory findResourceFactory(String name, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null && map.containsKey(name)) {
            return this;
        }
        if (parent != null) {
            return parent.findResourceFactory(name, clazz);
        }
        return null;
    }

    public <A> A find(Class<? extends A> clazz) {
        return find("", clazz);
    }

    public <A> A find(String name, Type clazz) {
        ResourceEntry re = findEntry(name, clazz);
        return re == null ? null : (A) re.value;
    }

    public <A> A find(String name, Class<? extends A> clazz) {
        ResourceEntry<A> re = findEntry(name, clazz);
        return re == null ? null : re.value;
    }

    public <A> A findChild(final String name, final Class<? extends A> clazz) {
        A rs = find(name, clazz);
        if (rs != null) {
            return rs;
        }
        for (Map.Entry<Type, ConcurrentHashMap<String, ResourceEntry>> en : this.store.entrySet()) {
            if (!(en.getKey() instanceof Class)) {
                continue;
            }
            if (!clazz.isAssignableFrom((Class) en.getKey())) {
                continue;
            }
            ResourceEntry v = en.getValue().get(name);
            if (v != null) {
                return (A) v.value;
            }
        }
        return null;
    }

    private ResourceEntry findEntry(String name, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            ResourceEntry re = map.get(name);
            if (re != null) {
                return re;
            }
        }
        if (parent != null) {
            return parent.findEntry(name, clazz);
        }
        return null;
    }

    public <A> List<A> query(Class<? extends A> clazz) {
        return query(new ArrayList<>(), clazz);
    }

    public <A> List<A> query(Type clazz) {
        return query(new ArrayList<>(), clazz);
    }

    private <A> List<A> query(final List<A> list, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            for (ResourceEntry re : map.values()) {
                if (re.value != null) {
                    list.add((A) re.value);
                }
            }
        }
        if (parent != null) {
            parent.query(list, clazz);
        }
        return list;
    }

    public <A> List<A> query(final BiPredicate<String, Object> predicate) {
        return query(new ArrayList<>(), predicate);
    }

    private <A> List<A> query(final List<A> list, final BiPredicate<String, Object> predicate) {
        if (predicate == null) {
            return list;
        }
        for (ConcurrentHashMap<String, ResourceEntry> map : this.store.values()) {
            for (Map.Entry<String, ResourceEntry> en : map.entrySet()) {
                if (predicate.test(en.getKey(), en.getValue().value)) {
                    list.add((A) en.getValue().value);
                }
            }
        }
        if (parent != null) {
            parent.query(list, predicate);
        }
        return list;
    }

    private <A> ResourceEntry<A> findEntry(String name, Class<? extends A> clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            ResourceEntry rs = map.get(name);
            if (rs != null) {
                return rs;
            }
        }
        if (parent != null) {
            return parent.findEntry(name, clazz);
        }
        return null;
    }

    public <T> boolean inject(final Object srcObj) {
        return inject(srcObj, null);
    }

    public <T> boolean inject(final Object srcObj, final T attachment) {
        return inject(srcObj, attachment, null);
    }

    public <T> boolean inject(final Object srcObj, final BiConsumer<Object, Field> consumer) {
        return inject(srcObj, null, consumer);
    }

    public <T> boolean inject(final Object srcObj, final T attachment, final BiConsumer<Object, Field> consumer) {
        return inject(null, srcObj, attachment, consumer, new ArrayList());
    }

    public <T> boolean inject(final String srcResourceName, final Object srcObj) {
        return inject(srcResourceName, srcObj, null);
    }

    public <T> boolean inject(final String srcResourceName, final Object srcObj, final T attachment) {
        return inject(srcResourceName, srcObj, attachment, null);
    }

    public <T> boolean inject(final String srcResourceName, final Object srcObj, final BiConsumer<Object, Field> consumer) {
        return inject(srcResourceName, srcObj, null, consumer);
    }

    public <T> boolean inject(final String srcResourceName, final Object srcObj, final T attachment, final BiConsumer<Object, Field> consumer) {
        return inject(srcResourceName, srcObj, attachment, consumer, new ArrayList());
    }

    public static String formatResourceName(String name) {
        return formatResourceName(null, name);
    }

    public static String formatResourceName(String parent, String name) {
        if (name == null) {
            return null;
        }
        int pos = name.indexOf("{system.property.");
        if (pos < 0) {
            return (name.contains(RESOURCE_PARENT_NAME) && parent != null) ? name.replace(RESOURCE_PARENT_NAME, parent) : name;
        }
        String prefix = name.substring(0, pos);
        String subName = name.substring(pos + "{system.property.".length());
        pos = subName.lastIndexOf('}');
        if (pos < 0) {
            return (name.contains(RESOURCE_PARENT_NAME) && parent != null) ? name.replace(RESOURCE_PARENT_NAME, parent) : name;
        }
        String postfix = subName.substring(pos + 1);
        String property = subName.substring(0, pos);
        return formatResourceName(parent, prefix + System.getProperty(property, "") + postfix);
    }

    private <T> boolean inject(String srcResourceName, final Object srcObj, final T attachment, final BiConsumer<Object, Field> consumer, final List<Object> list) {
        if (srcObj == null) {
            return false;
        }
        try {
            list.add(srcObj);
            Class clazz = srcObj.getClass();
            final boolean diyloaderflag = !parentRoot().resAnnotationProviderMap.isEmpty();
            do {
                if (java.lang.Enum.class.isAssignableFrom(clazz)) {
                    break;
                }
                final String cname = clazz.getName();
                if (cname.startsWith("java.") || cname.startsWith("javax.")
                    || cname.startsWith("jdk.") || cname.startsWith("sun.")) {
                    break;
                }
                if (cname.indexOf('/') < 0) {//排除内部类， 如:JsonConvert$$Lambda$87/0x0000000100197440-
                    RedkaleClassLoader.putReflectionDeclaredFields(cname);
                }
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    final Class classType = field.getType();
                    Resource rc1 = field.getAnnotation(Resource.class);
                    javax.annotation.Resource rc2 = field.getAnnotation(javax.annotation.Resource.class);
                    if (rc1 == null && rc2 == null) {  //深度注入
                        if (Convert.class.isAssignableFrom(classType)) {
                            continue;
                        }
                        if (ConvertFactory.class.isAssignableFrom(classType)) {
                            continue;
                        }
                        if (ResourceFactory.class.isAssignableFrom(classType)) {
                            continue;
                        }
                        boolean flag = true; //是否没有重复
                        Object ns = field.get(srcObj);
                        for (Object o : list) {
                            if (o == ns) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag && diyloaderflag) {
                            parentRoot().resAnnotationProviderMap.values().stream().forEach(iloader -> {
                                Annotation ann = field.getAnnotation(iloader.annotationType());
                                if (ann == null) {
                                    return;
                                }
                                iloader.load(this, srcResourceName, srcObj, ann, field, attachment);
                            });
                        }
                        if (ns == null) {
                            continue;
                        }
                        final String nsname = ns.getClass().getName();
                        if (ns.getClass().isPrimitive() || ns.getClass().isArray()
                            || nsname.startsWith("java.") || nsname.startsWith("javax.")
                            || nsname.startsWith("jdk.") || nsname.startsWith("sun.")) {
                            continue;
                        }
                        if (flag) {
                            this.inject(null, ns, attachment, consumer, list);
                        }
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    RedkaleClassLoader.putReflectionField(cname, field);
                    final Type gencType = TypeToken.containsUnknownType(field.getGenericType())
                        ? TypeToken.getGenericType(field.getGenericType(), srcObj.getClass()) : field.getGenericType();
                    if (consumer != null) {
                        consumer.accept(srcObj, field);
                    }
                    String tname = rc1 == null ? rc2.name() : rc1.name();
                    if (tname.contains(RESOURCE_PARENT_NAME)) {
                        Resource res1 = srcObj.getClass().getAnnotation(Resource.class);
                        javax.annotation.Resource res2 = srcObj.getClass().getAnnotation(javax.annotation.Resource.class);
                        String presname = res1 == null ? (res2 == null ? srcResourceName : res2.name()) : res1.name();
                        if (presname == null) {
                            if (srcObj instanceof Resourcable) {
                                String oname = ((Resourcable) srcObj).resourceName();
                                if (oname != null) {
                                    tname = tname.replace(RESOURCE_PARENT_NAME, oname);
                                }
                            } else {
                                logger.log(Level.SEVERE, srcObj.getClass().getName() + " not found @Resource on Class or not implements Resourcable");
                            }
                        } else {
                            tname = tname.replace(RESOURCE_PARENT_NAME, presname);
                        }

                    }
                    boolean autoRegNull = true;
                    final String rcname = formatResourceName(srcResourceName, tname);
                    Object rs = null;
                    if (rcname.startsWith("system.property.")) {
                        rs = System.getProperty(rcname.substring("system.property.".length()));
                    } else {
                        ResourceEntry re = findEntry(rcname, gencType);
                        if (re == null) {
                            if (classType.isPrimitive() || classType == Integer.class
                                || classType == Long.class || classType == Short.class
                                || classType == Boolean.class || classType == Byte.class
                                || classType == Float.class || classType == Double.class
                                || classType == BigInteger.class || classType == BigDecimal.class) {
                                re = findEntry(rcname, String.class);
                                if (re == null && rcname.startsWith("property.")) { //兼容2.8.0之前版本自动追加property.开头的配置项
                                    re = findEntry(rcname.substring("property.".length()), String.class);
                                }
                            } else if (classType == String.class && rcname.startsWith("property.")) {//兼容2.8.0之前版本自动追加property.开头的配置项
                                re = findEntry(rcname.substring("property.".length()), String.class);
                            } else {
                                re = findEntry(rcname, classType);
                            }
                        }
                        if (re == null) {
                            ResourceTypeLoader it = findTypeLoader(gencType, field);
                            if (it != null) {
                                rs = it.load(this, srcResourceName, srcObj, rcname, field, attachment);
                                autoRegNull = it.autoNone();
                                if (rs == null) {
                                    re = findEntry(rcname, gencType);
                                }
                            }
                        }
                        if (rs == null && re == null && gencType != classType) {
                            re = findEntry(rcname, classType);
                            if (re == null) {
                                if (classType.isPrimitive() || classType == Integer.class
                                    || classType == Long.class || classType == Short.class
                                    || classType == Boolean.class || classType == Byte.class
                                    || classType == Float.class || classType == Double.class
                                    || classType == BigInteger.class || classType == BigDecimal.class) {
                                    re = findEntry(rcname, String.class);
                                } else {
                                    re = findEntry(rcname, classType);
                                }
                            }
                            if (re == null) {
                                ResourceTypeLoader it = findTypeLoader(classType, field);
                                if (it != null) {
                                    rs = it.load(this, srcResourceName, srcObj, rcname, field, attachment);
                                    autoRegNull = it.autoNone();
                                    if (rs == null) {
                                        re = findEntry(rcname, classType);
                                    }
                                }
                            }
                        }
                        if (rs == null && re == null && autoRegNull && rcname.indexOf('$') < 0) {
                            register(rcname, gencType, null); //自动注入null的值
                            re = findEntry(rcname, gencType);
                        }
                        if (re != null) {
                            re.elements.add(new ResourceElement<>(srcObj, field));
                            rs = re.value;
                        }
                    }
                    if (rs != null && !rs.getClass().isPrimitive() && (classType.isPrimitive()
                        || classType == Integer.class
                        || classType == Long.class || classType == Short.class
                        || classType == Boolean.class || classType == Byte.class
                        || classType == Float.class || classType == Double.class
                        || classType == BigInteger.class || classType == BigDecimal.class)) {
                        if (classType == int.class || classType == Integer.class) {
                            rs = Integer.decode(rs.toString());
                        } else if (classType == long.class || classType == Long.class) {
                            rs = Long.decode(rs.toString());
                        } else if (classType == short.class || classType == Short.class) {
                            rs = Short.decode(rs.toString());
                        } else if (classType == boolean.class || classType == Boolean.class) {
                            rs = "true".equalsIgnoreCase(rs.toString());
                        } else if (classType == byte.class || classType == Byte.class) {
                            rs = Byte.decode(rs.toString());
                        } else if (classType == float.class || classType == Float.class) {
                            rs = Float.parseFloat(rs.toString());
                        } else if (classType == double.class || classType == Double.class) {
                            rs = Double.parseDouble(rs.toString());
                        } else if (classType == BigInteger.class) {
                            rs = new BigInteger(rs.toString());
                        } else if (classType == BigDecimal.class) {
                            rs = new BigDecimal(rs.toString());
                        }
                    }
                    if (rs != null) {
                        field.set(srcObj, rs);
                    }
                    if (rs == null && !skipCheckRequired && rc1 != null && rc1.required()) {
                        String t = srcObj.getClass().getName();
                        if (srcObj.getClass().getSimpleName().startsWith("_Dyn")) {
                            t = srcObj.getClass().getSuperclass().getName();
                        }
                        throw new ResourceInjectException("resource(type=" + field.getType().getSimpleName() + ".class, field=" + field.getName() + ", name='" + rcname + "') must exists in " + t);
                    }
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (ResourceInjectException e) {
            throw e;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "inject " + srcObj + " error", ex);
            return false;
        }
    }

    public <T extends Annotation> void register(final ResourceAnnotationProvider<T> loader) {
        if (loader == null) {
            return;
        }
        parentRoot().resAnnotationProviderMap.put(loader.annotationType(), loader);
    }

    public void register(final ResourceTypeLoader rs, final Type... clazzs) {
        if (clazzs == null || rs == null) {
            return;
        }
        for (Type clazz : clazzs) {
            resTypeLoaderMap.put(clazz, rs);
        }
    }

    public ResourceTypeLoader findResourceTypeLoader(Type clazz) {
        ResourceTypeLoader it = this.resTypeLoaderMap.get(clazz);
        if (it != null) {
            return it;
        }
        return parent == null ? null : parent.findResourceTypeLoader(clazz);
    }

    private ResourceFactory parentRoot() {
        if (parent == null) {
            return this;
        }
        return parent.parentRoot();
    }

    private ResourceTypeLoader findMatchTypeLoader(Type ft, Field field) {
        ResourceTypeLoader it = this.resTypeLoaderMap.get(ft);
        if (it == null && field != null) {
            it = this.resTypeLoaderMap.get(field.getType());
        }
        if (it != null) {
            return it;
        }
        return parent == null ? null : parent.findMatchTypeLoader(ft, field);
    }

    private ResourceTypeLoader findRegxTypeLoader(Type ft, Field field) {
        if (field == null) {
            return null;
        }
        Class c = field.getType();
        for (Map.Entry<Type, ResourceTypeLoader> en : this.resTypeLoaderMap.entrySet()) {
            Type t = en.getKey();
            if (t == ft) {
                return en.getValue();
            }
            if (t instanceof Class && (((Class) t)).isAssignableFrom(c)) {
                return en.getValue();
            }
        }
        return parent == null ? null : parent.findRegxTypeLoader(ft, field);
    }

    public ResourceTypeLoader findTypeLoader(Type ft, Field field) {
        ResourceTypeLoader it = this.findMatchTypeLoader(ft, field);
        return it == null ? findRegxTypeLoader(ft, field) : it;
    }

    private static class ResourceInjectException extends RedkaleException {

        public ResourceInjectException() {
            super();
        }

        public ResourceInjectException(String s) {
            super(s);
        }

        public ResourceInjectException(String message, Throwable cause) {
            super(message, cause);
        }

        public ResourceInjectException(Throwable cause) {
            super(cause);
        }
    }

    private static class ResourceEntry<T> {

        public final String name;

        public final T value;

        public final List<ResourceElement> elements;

        public ResourceEntry(final String name, T value) {
            this.name = name;
            this.value = value;
            this.elements = new CopyOnWriteArrayList<>();
        }

        //wrappers=null时才会触发listener的ResourceEvent事件
        public ResourceEntry(final String name, T value, final List<ResourceElement> elements, Collection<ResourceChangeWrapper> wrappers, boolean sync) {
            this.name = name;
            this.value = value;
            this.elements = elements == null ? new CopyOnWriteArrayList<>() : elements;
            if (sync && elements != null && !elements.isEmpty()) {
                for (ResourceElement element : elements) {
                    Object dest = element.dest.get();
                    if (dest == null) {
                        continue;  //依赖对象可能被销毁了
                    }
                    Object newVal = value;
                    final Class classType = element.fieldType;
                    if (newVal != null && !newVal.getClass().isPrimitive() && (classType.isPrimitive() || Number.class.isAssignableFrom(classType))) {
                        if (classType == int.class || classType == Integer.class) {
                            newVal = Integer.decode(newVal.toString());
                        } else if (classType == long.class || classType == Long.class) {
                            newVal = Long.decode(newVal.toString());
                        } else if (classType == short.class || classType == Short.class) {
                            newVal = Short.decode(newVal.toString());
                        } else if (classType == boolean.class || classType == Boolean.class) {
                            newVal = "true".equalsIgnoreCase(newVal.toString());
                        } else if (classType == byte.class || classType == Byte.class) {
                            newVal = Byte.decode(newVal.toString());
                        } else if (classType == float.class || classType == Float.class) {
                            newVal = Float.parseFloat(newVal.toString());
                        } else if (classType == double.class || classType == Double.class) {
                            newVal = Double.parseDouble(newVal.toString());
                        } else if (classType == BigInteger.class) {
                            newVal = new BigInteger(newVal.toString());
                        } else if (classType == BigDecimal.class) {
                            newVal = new BigDecimal(newVal.toString());
                        }
                    }
                    if (newVal == null && classType.isPrimitive()) {
                        newVal = Array.get(Creator.newArray(classType, 1), 0);
                    }
                    Object oldVal = null;
                    if (element.listener != null) {
                        try {
                            oldVal = element.field.get(dest);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        element.field.set(dest, newVal);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    if (element.listener != null) {
                        try {
                            if (!element.different || !Objects.equals(newVal, oldVal)) {
                                if (wrappers == null) {
                                    Object[] ps = new Object[]{new ResourceEvent[]{ResourceEvent.create(name, newVal, oldVal)}};
                                    element.listener.invoke(dest, ps);
                                } else {
                                    wrappers.add(new ResourceChangeWrapper(dest, element.listener, ResourceEvent.create(name, newVal, oldVal)));
                                }
                            }
                        } catch (Throwable e) {
                            logger.log(Level.SEVERE, dest + " resource change listener error", e);
                        }
                    }
                }
            }
        }
    }

    private static class ResourceElement<T> {

        private static final ReentrantLock syncLock = new ReentrantLock();

        private static final HashMap<String, Method> listenerMethods = new HashMap<>(); //不使用ConcurrentHashMap是因为value不能存null

        public final WeakReference<T> dest;

        public final Field field; //Resource 字段

        public final Class fieldType;

        public final Method listener;

        public final boolean different;

        public ResourceElement(T dest, Field field) {
            this.dest = new WeakReference(dest);
            this.field = field;
            this.fieldType = field.getType();
            Class t = dest.getClass();
            String tn = t.getName();
            AtomicBoolean diff = new AtomicBoolean();
            this.listener = tn.startsWith("java.") || tn.startsWith("javax.") ? null : findListener(t, field.getType(), diff);
            this.different = diff.get();
        }

        private static Method findListener(Class clazz, Class fieldType, AtomicBoolean diff) {
            syncLock.lock();
            try {
                Class loop = clazz;
                Method m = listenerMethods.get(clazz.getName() + "-" + fieldType.getName());
                if (m != null) {
                    return m;
                }
                do {
                    RedkaleClassLoader.putReflectionDeclaredMethods(loop.getName());
                    for (Method method : loop.getDeclaredMethods()) {
                        ResourceListener rl = method.getAnnotation(ResourceListener.class);
                        org.redkale.util.ResourceListener rl2 = method.getAnnotation(org.redkale.util.ResourceListener.class);
                        if (rl == null && rl2 == null) {
                            continue;
                        }
                        if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == ResourceEvent[].class) {
                            m = method;
                            m.setAccessible(true);
                            diff.set(rl != null ? rl.different() : rl2.different());
                            RedkaleClassLoader.putReflectionMethod(loop.getName(), method);
                            break;
                        } else {
                            logger.log(Level.SEVERE, "@" + ResourceListener.class.getSimpleName() + " must on method with " + ResourceEvent.class.getSimpleName() + "[] parameter type");
                        }
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
                listenerMethods.put(clazz.getName() + "-" + fieldType.getName(), m);
                return m;
            } finally {
                syncLock.unlock();
            }
        }
    }

    private static class ResourceChangeWrapper {

        public Object dest;

        public Method listener;

        public ResourceEvent event;

        public ResourceChangeWrapper(Object dest, Method listener, ResourceEvent event) {
            this.dest = dest;
            this.listener = listener;
            this.event = event;
        }

        public Object getDest() {
            return dest;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.dest);
            hash = 97 * hash + Objects.hashCode(this.listener);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ResourceChangeWrapper other = (ResourceChangeWrapper) obj;
            if (!Objects.equals(this.dest, other.dest)) {
                return false;
            }
            return Objects.equals(this.listener, other.listener);
        }

    }

//    public static class SimpleResourceTypeLoader implements ResourceTypeLoader {
//
//        protected Class<?> type;
//
//        protected Creator creator;
//
//        protected ResourceFactory factory;
//
//        public SimpleResourceTypeLoader(Class type) {
//            this(null, type, Creator.create(type));
//        }
//
//        public SimpleResourceTypeLoader(Class type, Creator creator) {
//            this(null, type, Creator.create(type));
//        }
//
//        public SimpleResourceTypeLoader(ResourceFactory factory, Class type) {
//            this(factory, type, Creator.create(type));
//        }
//
//        public SimpleResourceTypeLoader(ResourceFactory factory, Class type, Creator creator) {
//            this.factory = factory;
//            this.type = type;
//            this.creator = creator == null ? Creator.create(type) : creator;
//        }
//
//        @Override
//        public void load(ResourceFactory resFactory, String srcResourceName, Object srcObj, String resourceName, Field field, Object attachment) {
//            try {
//                if (field.getAnnotation(Resource.class) == null) return;
//                Object bean = creator.create();
//                field.set(srcObj, bean);
//                ResourceFactory rf = factory == null ? resFactory : factory;
//                ResourceType rtype = bean.getClass().getAnnotation(ResourceType.class);
//                Class resType = rtype == null ? type : rtype.value();
//                rf.register(resourceName, resType, bean);
//                resFactory.inject(resourceName, bean, srcObj);
//            } catch (RuntimeException ex) {
//                throw ex;
//            } catch (Exception e) {
//                throw new RedkaleException(e);
//            }
//        }
//    }
}
