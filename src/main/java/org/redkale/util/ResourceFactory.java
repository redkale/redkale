/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
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

    private static final Logger logger = Logger.getLogger(ResourceFactory.class.getSimpleName());

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
            if (rf != null) result.add(rf);
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
        return rt == null ? clazz : rt.value();
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
        if (rs == null) return null;
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
     * @param rs   资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final A rs) {
        return register(true, name, rs);
    }

    /**
     * 将对象以指定资源名注入到资源池中
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param rs       资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final String name, final A rs) {
        checkResourceName(name);
        final Class<?> claz = rs.getClass();
        ResourceType rtype = claz.getAnnotation(ResourceType.class);
        if (rtype == null) {
            return (A) register(autoSync, name, claz, rs);
        } else {
            A old = null;
            A t = (A) register(autoSync, name, rtype.value(), rs);
            if (t != null) old = t;
            return old;
        }
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中，并同步已被注入的资源
     *
     * @param <A>   泛型
     * @param name  资源名
     * @param clazz 资源类型
     * @param rs    资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final Class<? extends A> clazz, final A rs) {
        return register(true, name, clazz, rs);
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中，并同步已被注入的资源
     *
     * @param <A>   泛型
     * @param name  资源名
     * @param clazz 资源类型
     * @param rs    资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final String name, final Type clazz, final A rs) {
        return register(true, name, clazz, rs);
    }

    /**
     * 将对象以指定资源名和类型注入到资源池中
     *
     * @param <A>      泛型
     * @param autoSync 是否同步已被注入的资源
     * @param name     资源名
     * @param clazz    资源类型
     * @param rs       资源对象
     *
     * @return 旧资源对象
     */
    public <A> A register(final boolean autoSync, final String name, final Type clazz, final A rs) {
        checkResourceName(name);
        Class clz = TypeToken.typeToClass(clazz);
        if (clz != null && !clz.isPrimitive() && rs != null && !clz.isAssignableFrom(rs.getClass())) {
            throw new RuntimeException(clz + "not isAssignableFrom (" + rs + ") class " + rs.getClass());
        }
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
            map.put(name, new ResourceEntry(name, rs));
        } else {
            map.put(name, new ResourceEntry(name, rs, re.elements, autoSync));
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
        if (map != null && map.containsKey(name)) return this;
        if (parent != null) return parent.findResourceFactory(name, clazz);
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
        if (rs != null) return rs;
        for (Map.Entry<Type, ConcurrentHashMap<String, ResourceEntry>> en : this.store.entrySet()) {
            if (!(en.getKey() instanceof Class)) continue;
            if (!clazz.isAssignableFrom((Class) en.getKey())) continue;
            ResourceEntry v = en.getValue().get(name);
            if (v != null) return (A) v.value;
        }
        return null;
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
        return query(new ArrayList<>(), clazz);
    }

    public <A> List<A> query(Type clazz) {
        return query(new ArrayList<>(), clazz);
    }

    private <A> List<A> query(final List<A> list, Type clazz) {
        Map<String, ResourceEntry> map = this.store.get(clazz);
        if (map != null) {
            for (ResourceEntry re : map.values()) {
                if (re.value != null) list.add((A) re.value);
            }
        }
        if (parent != null) parent.query(list, clazz);
        return list;
    }

    public <A> List<A> query(final BiPredicate<String, Object> predicate) {
        return query(new ArrayList<>(), predicate);
    }

    private <A> List<A> query(final List<A> list, final BiPredicate<String, Object> predicate) {
        if (predicate == null) return list;
        for (ConcurrentHashMap<String, ResourceEntry> map : this.store.values()) {
            for (Map.Entry<String, ResourceEntry> en : map.entrySet()) {
                if (predicate.test(en.getKey(), en.getValue().value)) {
                    list.add((A) en.getValue().value);
                }
            }
        }
        if (parent != null) parent.query(list, predicate);
        return list;
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
        if (name == null) return null;
        int pos = name.indexOf("{system.property.");
        if (pos < 0) return (name.contains(RESOURCE_PARENT_NAME) && parent != null) ? name.replace(RESOURCE_PARENT_NAME, parent) : name;
        String prefix = name.substring(0, pos);
        String subname = name.substring(pos + "{system.property.".length());
        pos = subname.lastIndexOf('}');
        if (pos < 0) return (name.contains(RESOURCE_PARENT_NAME) && parent != null) ? name.replace(RESOURCE_PARENT_NAME, parent) : name;
        String postfix = subname.substring(pos + 1);
        String property = subname.substring(0, pos);
        return formatResourceName(parent, prefix + System.getProperty(property, "") + postfix);
    }

    private <T> boolean inject(String srcResourceName, final Object srcObj, final T attachment, final BiConsumer<Object, Field> consumer, final List<Object> list) {
        if (srcObj == null) return false;
        try {
            list.add(srcObj);
            Class clazz = srcObj.getClass();
            final boolean diyloaderflag = !parentRoot().resAnnotationProviderMap.isEmpty();
            do {
                if (java.lang.Enum.class.isAssignableFrom(clazz)) break;
                final String cname = clazz.getName();
                if (cname.startsWith("java.") || cname.startsWith("javax.")
                    || cname.startsWith("jdk.") || cname.startsWith("sun.")) break;
                if (cname.indexOf('/') < 0) {//排除内部类， 如:JsonConvert$$Lambda$87/0x0000000100197440-
                    RedkaleClassLoader.putReflectionDeclaredFields(cname);
                }
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    final Class classtype = field.getType();
                    Resource rc = field.getAnnotation(Resource.class);
                    if (rc == null) {  //深度注入
                        if (Convert.class.isAssignableFrom(classtype)) continue;
                        if (ConvertFactory.class.isAssignableFrom(classtype)) continue;
                        if (ResourceFactory.class.isAssignableFrom(classtype)) continue;
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
                                if (ann == null) return;
                                iloader.load(this, srcResourceName, srcObj, ann, field, attachment);
                            });
                        }
                        if (ns == null) continue;
                        final String nsname = ns.getClass().getName();
                        if (ns.getClass().isPrimitive() || ns.getClass().isArray()
                            || nsname.startsWith("java.") || nsname.startsWith("javax.")
                            || nsname.startsWith("jdk.") || nsname.startsWith("sun.")) continue;
                        if (flag) this.inject(null, ns, attachment, consumer, list);
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    RedkaleClassLoader.putReflectionField(cname, field);
                    final Type genctype = TypeToken.containsUnknownType(field.getGenericType())
                        ? TypeToken.getGenericType(field.getGenericType(), srcObj.getClass()) : field.getGenericType();
                    if (consumer != null) consumer.accept(srcObj, field);
                    String tname = rc.name();
                    if (tname.contains(RESOURCE_PARENT_NAME)) {
                        Resource res = srcObj.getClass().getAnnotation(Resource.class);
                        String presname = res == null ? srcResourceName : res.name();
                        if (presname == null) {
                            if (srcObj instanceof Resourcable) {
                                tname = tname.replace(RESOURCE_PARENT_NAME, ((Resourcable) srcObj).resourceName());
                            } else {
                                logger.log(Level.SEVERE, srcObj.getClass().getName() + " not found @Resource on Class or not implements Resourcable");
                            }
                        } else {
                            tname = tname.replace(RESOURCE_PARENT_NAME, presname);
                        }

                    }
                    boolean autoregnull = true;
                    final String rcname = formatResourceName(srcResourceName, tname);
                    Object rs;
                    if (rcname.startsWith("system.property.")) {
                        rs = System.getProperty(rcname.substring("system.property.".length()));
                    } else {
                        ResourceEntry re = findEntry(rcname, genctype);
                        if (re == null) {
                            if (rcname.startsWith("property.")) {
                                re = findEntry(rcname, String.class);
                            } else {
                                re = findEntry(rcname, classtype);
                            }
                        }
                        if (re == null) {
                            ResourceTypeLoader it = findTypeLoader(genctype, field);
                            if (it != null) {
                                it.load(this, srcResourceName, srcObj, rcname, field, attachment);
                                autoregnull = it.autoNone();
                                re = findEntry(rcname, genctype);
                            }
                        }
                        if (re == null && genctype != classtype) {
                            re = findEntry(rcname, classtype);
                            if (re == null) {
                                if (rcname.startsWith("property.")) {
                                    re = findEntry(rcname, String.class);
                                } else {
                                    re = findEntry(rcname, classtype);
                                }
                            }
                            if (re == null) {
                                ResourceTypeLoader it = findTypeLoader(classtype, field);
                                if (it != null) {
                                    it.load(this, srcResourceName, srcObj, rcname, field, attachment);
                                    autoregnull = it.autoNone();
                                    re = findEntry(rcname, classtype);
                                }
                            }
                        }
                        if (re == null && autoregnull) {
                            register(rcname, genctype, null); //自动注入null的值
                            re = findEntry(rcname, genctype);
                        }
                        if (re == null) continue;
                        re.elements.add(new ResourceElement<>(srcObj, field));
                        rs = re.value;
                    }
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
                    if (rs != null) field.set(srcObj, rs);
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            return true;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "inject " + srcObj + " error", ex);
            return false;
        }
    }

    public <T extends Annotation> void register(final ResourceAnnotationProvider<T> loader) {
        if (loader == null) return;
        parentRoot().resAnnotationProviderMap.put(loader.annotationType(), loader);
    }

    public void register(final ResourceTypeLoader rs, final Type... clazzs) {
        if (clazzs == null || rs == null) return;
        for (Type clazz : clazzs) {
            resTypeLoaderMap.put(clazz, rs);
        }
    }

    public ResourceTypeLoader findResourceTypeLoader(Type clazz) {
        ResourceTypeLoader it = this.resTypeLoaderMap.get(clazz);
        if (it != null) return it;
        return parent == null ? null : parent.findResourceTypeLoader(clazz);
    }

    private ResourceFactory parentRoot() {
        if (parent == null) return this;
        return parent.parentRoot();
    }

    private ResourceTypeLoader findMatchTypeLoader(Type ft, Field field) {
        ResourceTypeLoader it = this.resTypeLoaderMap.get(ft);
        if (it == null && field != null) it = this.resTypeLoaderMap.get(field.getType());
        if (it != null) return it;
        return parent == null ? null : parent.findMatchTypeLoader(ft, field);
    }

    private ResourceTypeLoader findRegxTypeLoader(Type ft, Field field) {
        if (field == null) return null;
        Class c = field.getType();
        for (Map.Entry<Type, ResourceTypeLoader> en : this.resTypeLoaderMap.entrySet()) {
            Type t = en.getKey();
            if (t == ft) return en.getValue();
            if (t instanceof Class && (((Class) t)).isAssignableFrom(c)) return en.getValue();
        }
        return parent == null ? null : parent.findRegxTypeLoader(ft, field);
    }

    public ResourceTypeLoader findTypeLoader(Type ft, Field field) {
        ResourceTypeLoader it = this.findMatchTypeLoader(ft, field);
        return it == null ? findRegxTypeLoader(ft, field) : it;
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

        public ResourceEntry(final String name, T value, final List<ResourceElement> elements, boolean sync) {
            this.name = name;
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

        private static final HashMap<String, Method> listenerMethods = new HashMap<>(); //不使用ConcurrentHashMap是因为value不能存null

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
            this.listener = tn.startsWith("java.") || tn.startsWith("javax.") ? null : findListener(t, field.getType());
        }

        private static Method findListener(Class clazz, Class fieldType) {
            synchronized (listenerMethods) {
                Class loop = clazz;
                Method m = listenerMethods.get(clazz.getName() + "-" + fieldType.getName());
                if (m != null) return m;
                do {
                    RedkaleClassLoader.putReflectionDeclaredMethods(loop.getName());
                    for (Method method : loop.getDeclaredMethods()) {
                        if (method.getAnnotation(ResourceListener.class) != null
                            && method.getParameterCount() == 3
                            && String.class.isAssignableFrom(method.getParameterTypes()[0])
                            && method.getParameterTypes()[1] == method.getParameterTypes()[2]
                            && method.getParameterTypes()[1].isAssignableFrom(fieldType)) {
                            m = method;
                            m.setAccessible(true);
                            RedkaleClassLoader.putReflectionMethod(loop.getName(), method);
                            break;
                        }
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
                listenerMethods.put(clazz.getName() + "-" + fieldType.getName(), m);
                return m;
            }
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
//                throw new RuntimeException(e);
//            }
//        }
//    }
}