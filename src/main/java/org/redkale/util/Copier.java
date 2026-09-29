/*
 *
 */
package org.redkale.util;

import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.Opcode.*;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * JavaBean类对象的拷贝，相同的字段名会被拷贝 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <D> 目标对象的数据类型
 * @param <S> 源对象的数据类型
 * @since 2.8.0
 */
public interface Copier<S, D> extends BiFunction<S, D, D> {

    /** 是否跳过值为null的字段 */
    public static final int OPTION_SKIP_NULL_VALUE = 1 << 1; // 2

    /** 是否跳过值为空字符串的字段 */
    public static final int OPTION_SKIP_EMPTY_STRING = 1 << 2; // 4

    /** 同名字段类型强制转换 */
    public static final int OPTION_ALLOW_TYPE_CAST = 1 << 3; // 8

    /**
     * 将源对象字段复制到目标对象
     *
     * @param dest 目标对象
     * @param src 源对象
     * @return 目标对象
     */
    @Override
    public D apply(S src, D dest);

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param dest 目标对象
     * @param src 源对象
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final D dest) {
        return copy(src, dest, 0);
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param dest 目标对象
     * @param src 源对象
     * @param options 可配项
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final D dest, final int options) {
        if (src == null || dest == null) {
            return dest;
        }
        return load((Class<S>) src.getClass(), (Class<D>) dest.getClass(), options)
                .apply(src, dest);
    }

    /**
     * 将源对象复制一份
     *
     * @param <S> 源类泛型
     * @param src 源对象
     * @return 目标对象
     */
    public static <S> S copy(final S src) {
        return src == null ? null : (S) copy(src, src.getClass());
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param src 源对象
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final Class<D> destClass) {
        return copy(src, destClass, 0);
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param src 源对象
     * @param options 可配项
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final Class<D> destClass, final int options) {
        if (src == null) {
            return null;
        }
        Creator<D> creator = Creator.load(destClass);
        return load((Class<S>) src.getClass(), destClass, options).apply(src, creator.create());
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <S> 源类泛型
     * @param src 源对象
     * @param options 可配项
     * @return 目标对象
     */
    public static <S> Map copyToMap(final S src, final int options) {
        if (src == null) {
            return null;
        }
        HashMap dest = new HashMap();
        return load((Class<S>) src.getClass(), HashMap.class, options).apply(src, dest);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @return 复制器
     */
    public static <S, D> Function<S, D> func(final Class<S> srcClass, final Class<D> destClass) {
        return func(srcClass, destClass, 0);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @return 复制器
     */
    public static <S, D> Function<Collection<S>, Set<D>> funcSet(final Class<S> srcClass, final Class<D> destClass) {
        return funcSet(srcClass, destClass, 0);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @return 复制器
     */
    public static <S, D> Function<Collection<S>, List<D>> funcList(final Class<S> srcClass, final Class<D> destClass) {
        return funcList(srcClass, destClass, 0);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param <C> 集合泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param collectionClass 集合类名
     * @return 复制器
     */
    public static <S, D, C extends Collection> Function<Collection<S>, Collection<D>> funcCollection(
            final Class<S> srcClass, final Class<D> destClass, final Class<C> collectionClass) {
        return funcCollection(srcClass, destClass, 0, collectionClass);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @return 复制器
     */
    public static <S, D> Copier<S, D> load(final Class<S> srcClass, final Class<D> destClass) {
        return load(srcClass, destClass, 0);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @return 复制器
     */
    public static <S, D> Function<Collection<S>, Set<D>> funcSet(
            final Class<S> srcClass, final Class<D> destClass, final int options) {
        return (Function) funcCollection(srcClass, destClass, options, LinkedHashSet.class);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @return 复制器
     */
    public static <S, D> Function<Collection<S>, List<D>> funcList(
            final Class<S> srcClass, final Class<D> destClass, final int options) {
        return (Function) funcCollection(srcClass, destClass, options, ArrayList.class);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param <C> 集合泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @param collectionClass 集合类名
     * @return 复制器
     */
    public static <S, D, C extends Collection> Function<Collection<S>, Collection<D>> funcCollection(
            final Class<S> srcClass, final Class<D> destClass, final int options, final Class<C> collectionClass) {
        if (destClass == srcClass) {
            return Inners.CopierInner.copierFuncListOneCaches
                    .computeIfAbsent(collectionClass, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, v -> {
                        Creator<C> creator = Creator.create(collectionClass);
                        Function<S, D> func = func(srcClass, destClass, options);
                        Function<Collection<S>, Collection<D>> funcList = srcs -> {
                            if (srcs == null) {
                                return null;
                            } else if (srcs.isEmpty()) {
                                return creator.create();
                            } else {
                                C list = creator.create();
                                for (S s : srcs) {
                                    list.add(func.apply(s));
                                }
                                return list;
                            }
                        };
                        return funcList;
                    });
        } else {
            return Inners.CopierInner.copierFuncListTwoCaches
                    .computeIfAbsent(collectionClass, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(destClass, v -> {
                        Creator<C> creator = Creator.create(collectionClass);
                        Function<S, D> func = func(srcClass, destClass, options);
                        Function<Collection<S>, Collection<D>> funcList = srcs -> {
                            if (srcs == null) {
                                return null;
                            } else if (srcs.isEmpty()) {
                                return (C) creator.create();
                            } else {
                                C list = creator.create();
                                for (S s : srcs) {
                                    list.add(func.apply(s));
                                }
                                return list;
                            }
                        };
                        return funcList;
                    });
        }
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @return 复制器
     */
    public static <S, D> Function<S, D> func(final Class<S> srcClass, final Class<D> destClass, final int options) {
        if (destClass == srcClass) {
            return Inners.CopierInner.copierFuncOneCaches
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, v -> {
                        Copier<S, D> copier = load(srcClass, destClass, options);
                        Creator<D> creator = Creator.load(destClass);
                        Function<S, D> func = src -> src == null ? null : copier.apply(src, creator.create());
                        return func;
                    });
        } else {
            return Inners.CopierInner.copierFuncTwoCaches
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(destClass, v -> {
                        Copier<S, D> copier = load(srcClass, destClass, options);
                        Creator<D> creator = Creator.load(destClass);
                        Function<S, D> func = src -> src == null ? null : copier.apply(src, creator.create());
                        return func;
                    });
        }
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @return 复制器
     */
    public static <S, D> Copier<S, D> load(final Class<S> srcClass, final Class<D> destClass, final int options) {
        if (destClass == srcClass) {
            return Inners.CopierInner.copierOneCaches
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, v -> create(srcClass, destClass, options));
        } else {
            return Inners.CopierInner.copierTwoCaches
                    .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(srcClass, t -> new ConcurrentHashMap<>())
                    .computeIfAbsent(destClass, v -> create(srcClass, destClass, options));
        }
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @return 复制器
     */
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass) {
        return create(srcClass, destClass, (BiPredicate) null, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param names 源字段名与目标字段名的映射关系
     * @return 复制器
     */
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass, final Class<D> destClass, final Map<String, String> names) {
        return create(srcClass, destClass, (BiPredicate) null, names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param srcColumnPredicate 需复制源类的字段名判断器
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass, final Class<D> destClass, final Predicate<String> srcColumnPredicate) {
        return create(srcClass, destClass, (sc, m) -> srcColumnPredicate.test(m), (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param srcColumnPredicate 需复制源类的字段名判断器
     * @param names 源字段名与目标字段名的映射关系
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass,
            final Class<D> destClass,
            final Predicate<String> srcColumnPredicate,
            final Map<String, String> names) {
        return create(srcClass, destClass, (sc, m) -> srcColumnPredicate.test(m), names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param srcColumnPredicate 需复制源类的字段名判断器
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass,
            final Class<D> destClass,
            final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate) {
        return create(srcClass, destClass, srcColumnPredicate, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param srcColumnPredicate 需复制源类的字段名判断器
     * @param names 源字段名与目标字段名的映射关系
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass,
            final Class<D> destClass,
            final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate,
            final Map<String, String> names) {
        return create(srcClass, destClass, 0, srcColumnPredicate, names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass, final int options) {
        return create(srcClass, destClass, options, (BiPredicate) null, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D> 目标类泛型
     * @param <S> 源类泛型
     * @param destClass 目标类名
     * @param srcClass 源类名
     * @param options 可配项
     * @param srcColumnPredicate 需复制源类的字段名判断器
     * @param nameAlias 源字段名与目标字段名的映射关系
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(
            final Class<S> srcClass,
            final Class<D> destClass,
            final int options,
            final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate,
            final Map<String, String> nameAlias) {
        final boolean skipNullValue =
                (options & OPTION_SKIP_NULL_VALUE) > 0 || ConcurrentHashMap.class.isAssignableFrom(destClass);
        final boolean skipEmptyString = (options & OPTION_SKIP_EMPTY_STRING) > 0;
        final boolean allowTypeCast = (options & OPTION_ALLOW_TYPE_CAST) > 0;
        final Predicate<Object> valPredicate = v -> !(skipNullValue && v == null)
                && !(skipEmptyString && v instanceof CharSequence && ((CharSequence) v).length() == 0);

        if (Map.class.isAssignableFrom(destClass) && Map.class.isAssignableFrom(srcClass)) {
            final Map names0 = nameAlias;
            if (srcColumnPredicate != null) {
                if (nameAlias != null) {
                    return (S src, D dest) -> {
                        if (src == null) {
                            return dest;
                        }
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && valPredicate.test(v)) {
                                d.put(names0.getOrDefault(k, k), v);
                            }
                        });
                        return dest;
                    };
                } else {
                    return (S src, D dest) -> {
                        if (src == null) {
                            return dest;
                        }
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && valPredicate.test(v)) {
                                d.put(k, v);
                            }
                        });
                        return dest;
                    };
                }
            } else if (nameAlias != null) {
                return (S src, D dest) -> {
                    if (src == null) {
                        return dest;
                    }
                    Map d = (Map) dest;
                    ((Map) src).forEach((k, v) -> {
                        if (valPredicate.test(v)) {
                            d.put(names0.getOrDefault(k, k), v);
                        }
                    });
                    return dest;
                };
            }
            return new Copier<S, D>() {
                @Override
                public D apply(S src, D dest) {
                    if (src == null) {
                        return dest;
                    }
                    if (options == 0) {
                        ((Map) dest).putAll((Map) src);
                    } else {
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (valPredicate.test(v)) {
                                d.put(k, v);
                            }
                        });
                    }
                    return dest;
                }
            };
        }
        // ------------------------------------------------------------------------------
        final boolean destIsMap = Map.class.isAssignableFrom(destClass);
        final boolean srcIsMap = Map.class.isAssignableFrom(srcClass);
        final Predicate<Class<?>> throwPredicate = e -> !RuntimeException.class.isAssignableFrom(e);
        final Map<String, AccessibleObject> elements = new TreeMap<>();
        final Map<String, String> destNewNames = new TreeMap<>();
        int ingoreCount = 0;
        if (srcIsMap) { // Map -> JavaBean
            for (java.lang.reflect.Field field : destClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (!Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                final String sfname = field.getName();
                if (srcColumnPredicate != null && !srcColumnPredicate.test(field, sfname)) {
                    ingoreCount++;
                    continue;
                }
                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                if (!Objects.equals(sfname, dfname)) {
                    destNewNames.put(sfname, dfname);
                }
                elements.put(dfname, field);
            }

            for (java.lang.reflect.Method setter : destClass.getMethods()) {
                if (Modifier.isStatic(setter.getModifiers())) {
                    continue;
                }
                if (setter.getParameterTypes().length != 1) {
                    continue;
                }
                if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                    continue; // setter方法带有非RuntimeException异常
                }
                if (!setter.getName().startsWith("set")) {
                    continue;
                }
                String sfname = Utility.readFieldName(setter.getName());
                if (sfname.isEmpty()) {
                    continue;
                }
                if (srcColumnPredicate != null && !srcColumnPredicate.test(setter, sfname)) {
                    ingoreCount++;
                    continue;
                }
                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                if (!Objects.equals(sfname, dfname)) {
                    destNewNames.put(sfname, dfname);
                }
                elements.put(dfname, setter);
            }
        } else { // JavaBean -> Map/JavaBean
            for (java.lang.reflect.Field field : srcClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (!Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                final String sfname = field.getName();
                if (srcColumnPredicate != null && !srcColumnPredicate.test(field, sfname)) {
                    ingoreCount++;
                    continue;
                }
                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                if (!Objects.equals(sfname, dfname)) {
                    destNewNames.put(sfname, dfname);
                }
                elements.put(sfname, field);
            }
            for (java.lang.reflect.Method getter : srcClass.getMethods()) {
                if (Modifier.isStatic(getter.getModifiers())) {
                    continue;
                }
                if (getter.getParameterTypes().length > 0) {
                    continue;
                }
                if ("getClass".equals(getter.getName())) {
                    continue;
                }
                if (Utility.contains(getter.getExceptionTypes(), throwPredicate)) {
                    continue; // setter方法带有非RuntimeException异常
                }
                if (!getter.getName().startsWith("get") && !getter.getName().startsWith("is")) {
                    continue;
                }
                final String sfname = Utility.readFieldName(getter.getName());
                if (sfname.isEmpty()) {
                    continue;
                }
                if (srcColumnPredicate != null && !srcColumnPredicate.test(getter, sfname)) {
                    ingoreCount++;
                    continue;
                }
                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                if (!Objects.equals(sfname, dfname)) {
                    destNewNames.put(sfname, dfname);
                }
                elements.put(sfname, getter);
            }
        }
        StringBuilder extendInfo = new StringBuilder();
        if (ingoreCount > 0 || nameAlias != null) {
            if (ingoreCount > 0) {
                extendInfo.append(elements.keySet().stream().collect(Collectors.joining(",")));
            }
            if (nameAlias != null) {
                if (extendInfo.length() > 0) {
                    extendInfo.append(";");
                }
                destNewNames.forEach((k, v) -> extendInfo.append(k).append(':').append(v));
            }
        }
        // ------------------------------------------------------------------------------
        final String supDynName = Copier.class.getName().replace('.', '/');
        final String destClassName = destClass.getName().replace('.', '/');
        final String srcClassName = srcClass.getName().replace('.', '/');
        final String destDesc = destClass.descriptorString();
        final String srcDesc = srcClass.descriptorString();
        final RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/copier/_Dyn" + Copier.class.getSimpleName() + "_" + options
                + "__" + srcClass.getName().replace('.', '_').replace('$', '_')
                + (srcClass == destClass
                        ? ""
                        : ("__" + destClass.getName().replace('.', '_').replace('$', '_')))
                + (extendInfo.length() == 0 ? "" : Utility.md5Hex(extendInfo.toString()));
        try {
            return (Copier) classLoader
                    .loadClass(newDynName.replace('/', '.'))
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable ex) {
            // do nothing
        }

        // ------------------------------------------------------------------------------
        byte[] bytes = ClassFile.of().build(ClassDesc.ofInternalName(newDynName), cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(CD_Object)
                    .withInterfaceSymbols(ClassDesc.ofInternalName(supDynName));
            cb.with(SignatureAttribute.of(
                    ClassSignature.parseFrom("Ljava/lang/Object;L" + supDynName + "<" + srcDesc + destDesc + ">;")));

            { // 构造函数
                cb.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.invoke(
                            INVOKESPECIAL,
                            ClassDesc.ofInternalName("java/lang/Object"),
                            "<init>",
                            MethodTypeDesc.ofDescriptor("()V"),
                            false);
                    mv.return_();
                });
            }
            if (srcIsMap) { // Map -> JavaBean
                {
                    cb.withMethodBody(
                            "apply",
                            MethodTypeDesc.ofDescriptor("(" + srcDesc + destDesc + ")" + destDesc),
                            ACC_PUBLIC,
                            mv -> {
                                Label label0 = mv.newLabel();
                                mv.labelBinding(label0);
                                {
                                    // if(src == null) return null;
                                    mv.aload(1);
                                    Label ifLabel = mv.newLabel();
                                    mv.branch(IFNONNULL, ifLabel);
                                    mv.aload(2);
                                    mv.areturn();
                                    mv.labelBinding(ifLabel);
                                }

                                mv.aload(1);
                                mv.aload(2);
                                mv.invokedynamic(DynamicCallSiteDesc.of(
                                        MethodHandleDesc.ofMethod(
                                                DirectMethodHandleDesc.Kind.STATIC,
                                                ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
                                                "metafactory",
                                                MethodTypeDesc.of(
                                                        CD_CallSite,
                                                        CD_MethodHandles_Lookup,
                                                        CD_String,
                                                        CD_MethodType,
                                                        CD_MethodType,
                                                        CD_MethodHandle,
                                                        CD_MethodType)),
                                        "accept",
                                        MethodTypeDesc.of(
                                                ClassDesc.of("java.util.function.BiConsumer"),
                                                ClassDesc.ofDescriptor(destDesc)),
                                        MethodTypeDesc.of(CD_void, CD_Object, CD_Object),
                                        MethodHandleDesc.ofMethod(
                                                DirectMethodHandleDesc.Kind.STATIC,
                                                ClassDesc.ofInternalName(newDynName),
                                                "lambda$0",
                                                MethodTypeDesc.of(
                                                        CD_void,
                                                        ClassDesc.ofDescriptor(destDesc),
                                                        CD_Object,
                                                        CD_Object)),
                                        MethodTypeDesc.of(CD_void, CD_Object, CD_Object)));
                                mv.invoke(
                                        srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                        ClassDesc.ofInternalName(srcClassName),
                                        "forEach",
                                        MethodTypeDesc.ofDescriptor("(Ljava/util/function/BiConsumer;)V"),
                                        srcClass.isInterface());
                                mv.aload(2);
                                mv.areturn();
                                Label label2 = mv.newLabel();
                                mv.labelBinding(label2);
                            });
                }
                {
                    cb.withMethodBody(
                            "lambda$0",
                            MethodTypeDesc.ofDescriptor("(" + destDesc + "Ljava/lang/Object;Ljava/lang/Object;)V"),
                            ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC,
                            mv -> {
                                Label goLabel = mv.newLabel();
                                int i = 0;
                                for (Map.Entry<String, AccessibleObject> en : elements.entrySet()) {
                                    final int index = ++i;
                                    final java.lang.reflect.Type fieldType = en.getValue() instanceof Field
                                            ? ((Field) en.getValue()).getGenericType()
                                            : ((Method) en.getValue()).getGenericParameterTypes()[0];
                                    final Class fieldClass = en.getValue() instanceof Field
                                            ? ((Field) en.getValue()).getType()
                                            : ((Method) en.getValue()).getParameterTypes()[0];
                                    final boolean primitive = fieldClass.isPrimitive();
                                    final boolean charstr = CharSequence.class.isAssignableFrom(fieldClass);

                                    mv.ldc(en.getKey());
                                    mv.aload(1);
                                    mv.invoke(
                                            INVOKEVIRTUAL,
                                            ClassDesc.ofInternalName("java/lang/String"),
                                            "equals",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z"),
                                            false);
                                    Label ifeq = index == elements.size() ? goLabel : mv.newLabel();
                                    mv.branch(IFEQ, ifeq);
                                    if (skipNullValue || primitive) {
                                        mv.aload(2);
                                        mv.branch(IFNULL, ifeq);
                                    } else if (skipEmptyString && charstr) {
                                        mv.aload(2);
                                        mv.branch(IFNULL, ifeq);
                                        mv.aload(2);
                                        mv.instanceOf(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                        mv.branch(IFEQ, ifeq);
                                        mv.aload(2);
                                        mv.checkcast(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                        mv.invoke(
                                                INVOKEINTERFACE,
                                                ClassDesc.ofInternalName("java/lang/CharSequence"),
                                                "length",
                                                MethodTypeDesc.ofDescriptor("()I"),
                                                true);
                                        mv.branch(IFLE, ifeq);
                                    }

                                    mv.aload(0);
                                    loadClass(mv, fieldClass);

                                    mv.aload(2);
                                    mv.invoke(
                                            INVOKESTATIC,
                                            ClassDesc.ofInternalName(utilClassName),
                                            "convertValue",
                                            MethodTypeDesc.ofDescriptor(
                                                    "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;"),
                                            false);
                                    cast(mv, fieldClass);

                                    if (en.getValue() instanceof Field) {
                                        mv.putfield(
                                                ClassDesc.ofInternalName(destClassName),
                                                en.getKey(),
                                                ClassDesc.ofDescriptor(fieldClass.descriptorString()));
                                    } else {
                                        Method setter = (Method) en.getValue();
                                        mv.invoke(
                                                destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(destClassName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                setter.getReturnType(), setter.getParameterTypes())
                                                        .descriptorString()),
                                                destClass.isInterface());
                                        if (setter.getReturnType() == long.class
                                                || setter.getReturnType() == double.class) {
                                            mv.pop2();
                                        } else if (setter.getReturnType() != void.class) {
                                            mv.pop();
                                        }
                                    }
                                    if (index == elements.size()) {
                                        mv.labelBinding(goLabel);
                                    } else {
                                        mv.branch(GOTO, goLabel);
                                        mv.labelBinding(ifeq);
                                    }
                                }

                                mv.return_();
                            });
                }
            } else { // JavaBean -> Map/JavaBean
                cb.withMethodBody(
                        "apply",
                        MethodTypeDesc.ofDescriptor("(" + srcDesc + destDesc + ")" + destDesc),
                        ACC_PUBLIC,
                        mv -> {
                            Label label0 = mv.newLabel();
                            mv.labelBinding(label0);
                            {
                                // if(src == null) return null;
                                mv.aload(1);
                                Label ifLabel = mv.newLabel();
                                mv.branch(IFNONNULL, ifLabel);
                                mv.aload(2);
                                mv.areturn();
                                mv.labelBinding(ifLabel);
                            }

                            Predicate<Class> simpler =
                                    t -> t.isPrimitive() || t == String.class || Number.class.isAssignableFrom(t);
                            // 遍历所有字段
                            for (Map.Entry<String, AccessibleObject> en : elements.entrySet()) {
                                if (!(en.getValue() instanceof java.lang.reflect.Field)) {
                                    continue;
                                }
                                java.lang.reflect.Field field = (java.lang.reflect.Field) en.getValue();
                                final String sfname = en.getKey();

                                final String dfname = destNewNames.getOrDefault(sfname, sfname);
                                final Class srcFieldType = field.getType();
                                final boolean charstr = CharSequence.class.isAssignableFrom(srcFieldType);
                                if (destIsMap) { // JavaBean -> Map
                                    String td = srcFieldType.descriptorString();
                                    if ((!skipNullValue && !(skipEmptyString && charstr))
                                            || srcFieldType.isPrimitive()) {
                                        mv.aload(2);
                                        mv.ldc(dfname);
                                        mv.aload(1);
                                        mv.getfield(
                                                ClassDesc.ofInternalName(srcClassName),
                                                sfname,
                                                ClassDesc.ofDescriptor(td));
                                        box(mv, srcFieldType);
                                        mv.invoke(
                                                destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(destClassName),
                                                "put",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                destClass.isInterface());
                                        mv.pop();
                                    } else { // skipNullValue OR (skipEmptyString && charstr)
                                        mv.aload(1);
                                        mv.getfield(
                                                ClassDesc.ofInternalName(srcClassName),
                                                sfname,
                                                ClassDesc.ofDescriptor(td));
                                        mv.astore(3);
                                        mv.aload(3);
                                        Label ifLabel = mv.newLabel();
                                        mv.branch(IFNULL, ifLabel);
                                        if (skipEmptyString && charstr) {
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                            mv.invoke(
                                                    INVOKEINTERFACE,
                                                    ClassDesc.ofInternalName("java/lang/CharSequence"),
                                                    "length",
                                                    MethodTypeDesc.ofDescriptor("()I"),
                                                    true);
                                            mv.branch(IFLE, ifLabel);
                                        }
                                        mv.aload(2);
                                        mv.ldc(dfname);
                                        mv.aload(3);
                                        mv.invoke(
                                                destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(destClassName),
                                                "put",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                destClass.isInterface());
                                        mv.pop();
                                        mv.labelBinding(ifLabel);
                                    }
                                } else { // JavaBean -> JavaBean
                                    boolean needTypeCast = false;
                                    java.lang.reflect.Method setter = null;
                                    java.lang.reflect.Field setField = null;
                                    try {
                                        setField = destClass.getField(dfname);
                                        if (field.getType() == setField.getType()) {
                                            needTypeCast = false;
                                        } else if (simpler.test(field.getType()) && simpler.test(setField.getType())) {
                                            needTypeCast = true;
                                        } else if (!field.getType().equals(setField.getType())) {
                                            if (allowTypeCast) {
                                                needTypeCast = true;
                                            } else {
                                                continue;
                                            }
                                        }
                                    } catch (Exception e) {
                                        String setterMethodName = "set" + Utility.firstCharUpperCase(dfname);
                                        try {
                                            setter = destClass.getMethod(setterMethodName, field.getType());
                                            if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                                                continue; // setter方法带有非RuntimeException异常
                                            }
                                        } catch (Exception e2) {
                                            try {
                                                for (java.lang.reflect.Method m : destClass.getMethods()) {
                                                    if (Modifier.isStatic(m.getModifiers())) {
                                                        continue;
                                                    }
                                                    if (Utility.contains(m.getExceptionTypes(), throwPredicate)) {
                                                        continue; // setter方法带有非RuntimeException异常
                                                    }
                                                    if (m.getParameterTypes().length != 1) {
                                                        continue;
                                                    }
                                                    if (m.getName().equals(setterMethodName)) {
                                                        if (simpler.test(field.getType())
                                                                && simpler.test(setField.getType())) {
                                                            setter = m;
                                                            needTypeCast = true;
                                                        } else if (!allowTypeCast) {
                                                            setter = null;
                                                        }
                                                        break;
                                                    }
                                                }
                                                if (setter == null) {
                                                    continue;
                                                }
                                            } catch (Exception e3) {
                                                continue;
                                            }
                                        }
                                    }
                                    String srcFieldDesc = srcFieldType.descriptorString();
                                    final Class destFieldType =
                                            setter == null ? setField.getType() : setter.getParameterTypes()[0];
                                    boolean localSkipNull = skipNullValue
                                            || (!srcFieldType.isPrimitive() && destFieldType.isPrimitive());
                                    if ((!localSkipNull && !(skipEmptyString && charstr))
                                            || (srcFieldType.isPrimitive() && !allowTypeCast)
                                            || (srcFieldType.isPrimitive() && destFieldType.isPrimitive())) {
                                        if (needTypeCast) {
                                            mv.aload(2);
                                            loadClass(mv, destFieldType);
                                            mv.aload(1);
                                            mv.getfield(
                                                    ClassDesc.ofInternalName(srcClassName),
                                                    sfname,
                                                    ClassDesc.ofDescriptor(srcFieldDesc));
                                            box(mv, srcFieldType);
                                            mv.invoke(
                                                    INVOKESTATIC,
                                                    ClassDesc.ofInternalName(utilClassName),
                                                    "convertValue",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                    false);
                                            cast(mv, destFieldType);
                                            if (setter == null) { // src: field, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: field, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        } else {
                                            mv.aload(2);
                                            mv.aload(1);
                                            mv.getfield(
                                                    ClassDesc.ofInternalName(srcClassName),
                                                    sfname,
                                                    ClassDesc.ofDescriptor(srcFieldDesc));
                                            if (setter == null) { // src: field, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: field, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        }
                                    } else { // skipNullValue OR (skipEmptyString && charstr)
                                        mv.aload(1);
                                        mv.getfield(
                                                ClassDesc.ofInternalName(srcClassName),
                                                sfname,
                                                ClassDesc.ofDescriptor(srcFieldDesc));
                                        mv.astore(3);
                                        mv.aload(3);
                                        Label ifLabel = mv.newLabel();
                                        mv.branch(IFNULL, ifLabel);
                                        if (skipEmptyString && charstr) {
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                            mv.invoke(
                                                    INVOKEINTERFACE,
                                                    ClassDesc.ofInternalName("java/lang/CharSequence"),
                                                    "length",
                                                    MethodTypeDesc.ofDescriptor("()I"),
                                                    true);
                                            mv.branch(IFLE, ifLabel);
                                        }
                                        if (needTypeCast) {
                                            mv.aload(2);
                                            loadClass(mv, destFieldType);
                                            mv.aload(3);
                                            box(mv, srcFieldType);
                                            mv.invoke(
                                                    INVOKESTATIC,
                                                    ClassDesc.ofInternalName(utilClassName),
                                                    "convertValue",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                    false);
                                            cast(mv, destFieldType);
                                            if (setter == null) { // src: field, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: field, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        } else {
                                            mv.aload(2);
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofDescriptor(srcFieldType.descriptorString()));
                                            if (setter == null) { // src: field, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(srcFieldDesc));
                                            } else { // src: field, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        }
                                        mv.labelBinding(ifLabel);
                                    }
                                }
                            }
                            // 遍历所有方法
                            for (Map.Entry<String, AccessibleObject> en : elements.entrySet()) {
                                if (!(en.getValue() instanceof java.lang.reflect.Method)) {
                                    continue;
                                }
                                java.lang.reflect.Method getter = (java.lang.reflect.Method) en.getValue();
                                final String sfname = en.getKey();

                                final String dfname = destNewNames.getOrDefault(sfname, sfname);
                                final Class srcFieldType = getter.getReturnType();
                                final boolean charstr = CharSequence.class.isAssignableFrom(srcFieldType);
                                if (destIsMap) { // srcClass是JavaBean
                                    if ((!skipNullValue && !(skipEmptyString && charstr))
                                            || srcFieldType.isPrimitive()) {
                                        mv.aload(2);
                                        mv.ldc(dfname);
                                        mv.aload(1);
                                        mv.invoke(
                                                srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(srcClassName),
                                                getter.getName(),
                                                MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                getter.getReturnType(), getter.getParameterTypes())
                                                        .descriptorString()),
                                                srcClass.isInterface());
                                        box(mv, srcFieldType);
                                        mv.invoke(
                                                destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(destClassName),
                                                "put",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                destClass.isInterface());
                                        mv.pop();
                                    } else { // skipNullValue OR (skipEmptyString && charstr)
                                        mv.aload(1);
                                        mv.invoke(
                                                srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(srcClassName),
                                                getter.getName(),
                                                MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                getter.getReturnType(), getter.getParameterTypes())
                                                        .descriptorString()),
                                                srcClass.isInterface());
                                        mv.astore(3);
                                        mv.aload(3);
                                        Label ifLabel = mv.newLabel();
                                        mv.branch(IFNULL, ifLabel);
                                        if (skipEmptyString && charstr) {
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                            mv.invoke(
                                                    INVOKEINTERFACE,
                                                    ClassDesc.ofInternalName("java/lang/CharSequence"),
                                                    "length",
                                                    MethodTypeDesc.ofDescriptor("()I"),
                                                    true);
                                            mv.branch(IFLE, ifLabel);
                                        }
                                        mv.aload(2);
                                        mv.ldc(dfname);
                                        mv.aload(3);
                                        mv.invoke(
                                                destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(destClassName),
                                                "put",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                destClass.isInterface());
                                        mv.pop();
                                        mv.labelBinding(ifLabel);
                                    }
                                } else { // srcClass、destClass是JavaBean
                                    boolean needTypeCast = false;
                                    java.lang.reflect.Method setter = null;
                                    java.lang.reflect.Field setField = null;
                                    String setterMethodName = "set" + Utility.firstCharUpperCase(dfname);
                                    try {
                                        setter = destClass.getMethod(setterMethodName, getter.getReturnType());
                                        if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                                            continue; // setter方法带有非RuntimeException异常
                                        }
                                    } catch (Exception e) {
                                        if (allowTypeCast) {
                                            try {
                                                for (java.lang.reflect.Method m : destClass.getMethods()) {
                                                    if (Modifier.isStatic(m.getModifiers())) {
                                                        continue;
                                                    }
                                                    if (Utility.contains(m.getExceptionTypes(), throwPredicate)) {
                                                        continue; // setter方法带有非RuntimeException异常
                                                    }
                                                    if (m.getParameterTypes().length != 1) {
                                                        continue;
                                                    }
                                                    if (m.getName().equals(setterMethodName)) {
                                                        setter = m;
                                                        needTypeCast = true;
                                                        break;
                                                    }
                                                }
                                            } catch (Exception e2) {
                                                // do nothing
                                            }
                                        }
                                        if (setter == null) {
                                            try {
                                                setField = destClass.getField(dfname);
                                                if (!getter.getReturnType().equals(setField.getType())) {
                                                    if (allowTypeCast) {
                                                        needTypeCast = true;
                                                    } else {
                                                        continue;
                                                    }
                                                }
                                            } catch (Exception e3) {
                                                continue;
                                            }
                                        }
                                    }
                                    final Class destFieldType =
                                            setter == null ? setField.getType() : setter.getParameterTypes()[0];
                                    boolean localSkipNull = skipNullValue
                                            || (!srcFieldType.isPrimitive() && destFieldType.isPrimitive());
                                    if ((!localSkipNull && !(skipEmptyString && charstr))
                                            || (srcFieldType.isPrimitive() && !allowTypeCast)
                                            || (srcFieldType.isPrimitive() && destFieldType.isPrimitive())) {
                                        if (needTypeCast) {
                                            mv.aload(2);
                                            loadClass(mv, destFieldType);
                                            mv.aload(1);
                                            mv.invoke(
                                                    srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                    ClassDesc.ofInternalName(srcClassName),
                                                    getter.getName(),
                                                    MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                    getter.getReturnType(), getter.getParameterTypes())
                                                            .descriptorString()),
                                                    srcClass.isInterface());
                                            box(mv, srcFieldType);
                                            mv.invoke(
                                                    INVOKESTATIC,
                                                    ClassDesc.ofInternalName(utilClassName),
                                                    "convertValue",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                    false);
                                            cast(mv, destFieldType);
                                            if (setter == null) { // src: method, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: method, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        } else {
                                            mv.aload(2);
                                            mv.aload(1);
                                            mv.invoke(
                                                    srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                    ClassDesc.ofInternalName(srcClassName),
                                                    getter.getName(),
                                                    MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                    getter.getReturnType(), getter.getParameterTypes())
                                                            .descriptorString()),
                                                    srcClass.isInterface());
                                            if (setter == null) { // src: method, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: method, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        }
                                    } else { // skipNullValue OR (skipEmptyString && charstr)
                                        mv.aload(1);
                                        mv.invoke(
                                                srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                ClassDesc.ofInternalName(srcClassName),
                                                getter.getName(),
                                                MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                getter.getReturnType(), getter.getParameterTypes())
                                                        .descriptorString()),
                                                srcClass.isInterface());
                                        mv.astore(3);
                                        mv.aload(3);
                                        Label ifLabel = mv.newLabel();
                                        mv.branch(IFNULL, ifLabel);
                                        if (skipEmptyString && charstr) {
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofInternalName("java/lang/CharSequence"));
                                            mv.invoke(
                                                    INVOKEINTERFACE,
                                                    ClassDesc.ofInternalName("java/lang/CharSequence"),
                                                    "length",
                                                    MethodTypeDesc.ofDescriptor("()I"),
                                                    true);
                                            mv.branch(IFLE, ifLabel);
                                        }
                                        if (needTypeCast) {
                                            mv.aload(2);
                                            loadClass(mv, destFieldType);
                                            mv.aload(3);
                                            box(mv, srcFieldType);
                                            mv.invoke(
                                                    INVOKESTATIC,
                                                    ClassDesc.ofInternalName(utilClassName),
                                                    "convertValue",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;"),
                                                    false);
                                            cast(mv, destFieldType);
                                            if (setter == null) { // src: method, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(destFieldType.descriptorString()));
                                            } else { // src: method, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        } else {
                                            mv.aload(2);
                                            mv.aload(3);
                                            mv.checkcast(ClassDesc.ofDescriptor(srcFieldType.descriptorString()));
                                            if (setter == null) { // src: method, dest: field
                                                mv.putfield(
                                                        ClassDesc.ofInternalName(destClassName),
                                                        dfname,
                                                        ClassDesc.ofDescriptor(getter.getReturnType()
                                                                .descriptorString()));
                                            } else { // src: method, dest: method
                                                mv.invoke(
                                                        destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                                        ClassDesc.ofInternalName(destClassName),
                                                        setter.getName(),
                                                        MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                                                        setter.getReturnType(),
                                                                        setter.getParameterTypes())
                                                                .descriptorString()),
                                                        destClass.isInterface());
                                                if (setter.getReturnType() == long.class
                                                        || setter.getReturnType() == double.class) {
                                                    mv.pop2();
                                                } else if (setter.getReturnType() != void.class) {
                                                    mv.pop();
                                                }
                                            }
                                        }
                                        mv.labelBinding(ifLabel);
                                    }
                                }
                            }
                            mv.aload(2);
                            mv.areturn();
                            Label label2 = mv.newLabel();
                            mv.labelBinding(label2);
                        });
            }
            {
                cb.withMethodBody(
                        "apply",
                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                        ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                        mv -> {
                            mv.aload(0);
                            mv.aload(1);
                            mv.checkcast(ClassDesc.ofInternalName(srcClassName));
                            mv.aload(2);
                            mv.checkcast(ClassDesc.ofInternalName(destClassName));
                            mv.invoke(
                                    INVOKEVIRTUAL,
                                    ClassDesc.ofInternalName(newDynName),
                                    "apply",
                                    MethodTypeDesc.ofDescriptor("(" + srcDesc + destDesc + ")" + destDesc),
                                    false);
                            mv.areturn();
                        });
            }
        });
        // ------------------------------------------------------------------------------
        Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            return (Copier) newClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    private static void loadClass(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) {
            code.getstatic(
                    ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(type).descriptorString()), "TYPE", CD_Class);
        } else {
            code.ldc(ClassDesc.ofDescriptor(type.descriptorString()));
        }
    }

    private static void box(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) {
            ClassDesc wrapper =
                    ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(type).descriptorString());
            code.invokestatic(
                    wrapper, "valueOf", MethodTypeDesc.of(wrapper, ClassDesc.ofDescriptor(type.descriptorString())));
        }
    }

    private static void cast(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) {
            ClassDesc wrapper =
                    ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(type).descriptorString());
            code.checkcast(wrapper)
                    .invokevirtual(
                            wrapper,
                            type.getSimpleName() + "Value",
                            MethodTypeDesc.of(ClassDesc.ofDescriptor(type.descriptorString())));
        } else {
            code.checkcast(ClassDesc.ofDescriptor(type.descriptorString()));
        }
    }
}
