/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import org.redkale.annotation.*;

/**
 * 该类实现动态映射一个JavaBean类中成员对应的getter、setter方法； 代替低效的反射实现方式。
 *
 * <blockquote>
 *
 * <pre>
 *  public class Record {
 *
 *      private String name;
 *
 *      public String getName() {
 *          return name;
 *      }
 *
 *      public void setName(String name) {
 *          this.name = name;
 *      }
 *  }
 * </pre>
 *
 * </blockquote>
 *
 * 获取name的 Attribute ：
 *
 * <blockquote>
 *
 * <pre>
 *  Attribute&lt;Record, String&gt; nameAction = Attribute.create(Record.class.getDeclaredField("name"));
 * </pre>
 *
 * </blockquote>
 *
 * 等价于:
 *
 * <blockquote>
 *
 * <pre>
 *  Attribute&lt;Record, String&gt; nameAction = new Attribute&lt;Record, String&gt;() {
 *
 *      private java.lang.reflect.Type _gtype = String.class;
 *
 *      private java.lang.Object _attach;
 *
 *      &#64;Override
 *      public String field() {
 *          return "name";
 *      }
 *
 *      &#64;Override
 *      public String get(Record obj) {
 *          return obj.getName();
 *      }
 *
 *      &#64;Override
 *      public void set(Record obj, String value) {
 *          obj.setName(value);
 *      }
 *
 *      &#64;Override
 *      public Class type() {
 *          return String.class;
 *      }
 *
 *      &#64;Override
 *      public java.lang.reflect.Type genericType() {
 *          return _gtype;
 *      }
 *
 *      &#64;Override
 *      public Object attach() {
 *          return _attach;
 *      }
 *
 *      &#64;Override
 *      public Class declaringClass() {
 *          return Record.class;
 *      }
 *  };
 * </pre>
 *
 * </blockquote>
 *
 * <p>映射Field时，field必须满足以下条件之一： <br>
 * 1、field属性是public且非final <br>
 * 2、至少存在对应的getter、setter方法中的一个 <br>
 * 当不存在getter方法时，get操作固定返回null <br>
 * 当不存在setter方法时，set操作为空方法 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 字段依附的类
 * @param <F> 字段的数据类型
 */
@SuppressWarnings("unchecked")
public interface Attribute<T, F> {

    /**
     * 返回字段的数据类型
     *
     * @return 字段的数据类型
     */
    @Nonnull
    public Class<? extends F> type();

    /**
     * 返回字段的数据泛型
     *
     * @return 字段的数据泛型
     */
    @Nonnull
    default java.lang.reflect.Type genericType() {
        return type();
    }

    /**
     * 返回字段依附的类名
     *
     * @return 依附的类名
     */
    @Nonnull
    public Class<T> declaringClass();

    /**
     * 返回字段名
     *
     * @return 字段名
     */
    @Nonnull
    public String field();

    /**
     * 获取指定对象的该字段的值
     *
     * @param obj 指定对象
     * @return 字段的值
     */
    public F get(T obj);

    /**
     * 给指定对象的该字段赋值
     *
     * @param obj 指定对象
     * @param value 字段新值
     */
    public void set(T obj, F value);

    /**
     * 附加对象
     *
     * @param <E> 泛型
     * @return 附加对象
     */
    @Nullable
    default <E> E attach() {
        return null;
    }

    /**
     * 根据一个Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(final java.lang.reflect.Field field) {
        return create(
                (Class<T>) field.getDeclaringClass(),
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                null);
    }

    /**
     * 根据一个Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param field 字段，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(final java.lang.reflect.Field field, Object attach) {
        return create(
                (Class<T>) field.getDeclaringClass(),
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                attach);
    }

    /**
     * 根据一个Field和field的别名生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param fieldAlias 别名
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(String fieldAlias, final java.lang.reflect.Field field) {
        return create(
                (Class<T>) field.getDeclaringClass(),
                fieldAlias,
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                null);
    }

    /**
     * 根据一个Field和field的别名生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param fieldAlias 别名
     * @param field 字段，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(String fieldAlias, final java.lang.reflect.Field field, Object attach) {
        return create(
                (Class<T>) field.getDeclaringClass(),
                fieldAlias,
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                attach);
    }

    /**
     * 根据一个Class和field真实名称生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldName) {
        if (Map.class.isAssignableFrom(clazz)) {
            return (Attribute) map(fieldName);
        }
        try {
            return create(
                    clazz,
                    fieldName,
                    (Class) null,
                    clazz.getDeclaredField(fieldName),
                    (java.lang.reflect.Method) null,
                    (java.lang.reflect.Method) null,
                    null);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * 根据一个Class和字段名生成 Attribute 的getter对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> createGetter(Class<T> clazz, final String fieldName) {
        if (Map.class.isAssignableFrom(clazz)) {
            return (Attribute) map(fieldName);
        }
        String fieldAlias = fieldName;
        java.lang.reflect.Field field = null;
        java.lang.reflect.Method getter = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException | SecurityException ex) { // 不是标准的getter、setter字段
            try {
                getter = clazz.getMethod(fieldName);
            } catch (NoSuchMethodException | SecurityException ex2) {
                throw new RedkaleException(ex2);
            }
        }
        return create(clazz, fieldAlias, (Class) null, field, getter, (java.lang.reflect.Method) null, null);
    }

    /**
     * 根据一个Class和field真实名称生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldName, Object attach) {
        try {
            return create(
                    clazz,
                    fieldName,
                    (Class) null,
                    clazz.getDeclaredField(fieldName),
                    (java.lang.reflect.Method) null,
                    (java.lang.reflect.Method) null,
                    attach);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * 根据一个Class和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final java.lang.reflect.Field field) {
        return create(
                clazz,
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                null);
    }

    /**
     * 根据一个Class和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param subclass 指定依附的子类
     * @param clazz 指定依附的类
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> subclass, Class<T> clazz, final java.lang.reflect.Field field) {
        return create(
                subclass,
                clazz,
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                null);
    }

    /**
     * 根据一个Class和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param field 字段，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final java.lang.reflect.Field field, Object attach) {
        return create(
                clazz,
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                attach);
    }

    /**
     * 根据一个Class和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param subclass 指定依附的子类
     * @param clazz 指定依附的类
     * @param field 字段，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> subclass, Class<T> clazz, final java.lang.reflect.Field field, Object attach) {
        return create(
                subclass,
                clazz,
                field.getName(),
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                attach);
    }

    /**
     * 根据一个Class、field别名和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz, final String fieldAlias, final java.lang.reflect.Field field) {
        return create(
                clazz,
                fieldAlias,
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                null);
    }

    /**
     * 根据一个Class、field别名和Field生成 Attribute 对象。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param field 字段，如果该字段不存在则抛异常
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz, String fieldAlias, java.lang.reflect.Field field, Object attach) {
        return create(
                clazz,
                fieldAlias,
                (Class) null,
                field,
                (java.lang.reflect.Method) null,
                (java.lang.reflect.Method) null,
                attach);
    }

    /**
     * 根据一个getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(
            final java.lang.reflect.Method getter, final java.lang.reflect.Method setter) {
        return create(
                (Class) (getter == null ? setter.getDeclaringClass() : getter.getDeclaringClass()),
                (String) null,
                (Class) null,
                (java.lang.reflect.Field) null,
                getter,
                setter,
                null);
    }

    /**
     * 根据一个getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(
            final java.lang.reflect.Method getter, final java.lang.reflect.Method setter, Object attach) {
        return create(
                (Class) (getter == null ? setter.getDeclaringClass() : getter.getDeclaringClass()),
                (String) null,
                (Class) null,
                (java.lang.reflect.Field) null,
                getter,
                setter,
                attach);
    }

    /**
     * 根据Class、getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz, java.lang.reflect.Method getter, java.lang.reflect.Method setter) {
        return create(clazz, (String) null, (Class) null, (java.lang.reflect.Field) null, getter, setter, null);
    }

    /**
     * 根据Class、getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz, java.lang.reflect.Method getter, java.lang.reflect.Method setter, Object attach) {
        return create(clazz, (String) null, (Class) null, (java.lang.reflect.Field) null, getter, setter, attach);
    }

    /**
     * 根据Class生成getter、setter方法都存在的字段对应的 Attribute 对象数组。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段的类型
     * @param clazz 指定依附的类
     * @return Attribute对象数组
     */
    public static <T, F> Attribute<T, F>[] create(Class<T> clazz) {
        List<Attribute<T, F>> list = new ArrayList<>();
        RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
        for (java.lang.reflect.Field field : clazz.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            list.add(create(clazz, field));
        }
        RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
        for (java.lang.reflect.Method setter : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(setter.getModifiers())) {
                continue;
            }
            if (!setter.getName().startsWith("set")) {
                continue;
            }
            //            if (setter.getReturnType() != void.class) {
            //                continue;
            //            }
            if (setter.getParameterCount() != 1) {
                continue;
            }
            Class t = setter.getParameterTypes()[0];
            String prefix = t == boolean.class || t == Boolean.class ? "is" : "get";
            java.lang.reflect.Method getter = null;
            try {
                getter = clazz.getMethod(setter.getName().replaceFirst("set", prefix));
            } catch (Exception e) {
                continue;
            }
            list.add(create(clazz, getter, setter));
        }
        return list.toArray(new Attribute[list.size()]);
    }

    /**
     * 根据Class生成getter方法对应的 Attribute 对象数组。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段的类型
     * @param clazz 指定依附的类
     * @return Attribute对象数组
     */
    public static <T, F> Attribute<T, F>[] createGetters(Class<T> clazz) {
        List<Attribute<T, F>> list = new ArrayList<>();
        RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
        for (java.lang.reflect.Field field : clazz.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            list.add(create(clazz, field));
        }
        RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getReturnType() == void.class) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (method.getName().equals("getClass")) {
                continue;
            }
            if ((method.getName().startsWith("get") && method.getName().length() > 3)
                    || (method.getName().startsWith("is") && method.getName().length() > 2)
                    || Utility.isRecordGetter(clazz, method)) {
                list.add(create(clazz, method, null));
            }
        }
        return list.toArray(new Attribute[list.size()]);
    }

    /**
     * 根据Class生成setter方法对应的 Attribute 对象数组。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段的类型
     * @param clazz 指定依附的类
     * @return Attribute对象数组
     */
    public static <T, F> Attribute<T, F>[] createSetters(Class<T> clazz) {
        List<Attribute<T, F>> list = new ArrayList<>();
        RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
        for (java.lang.reflect.Field field : clazz.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            list.add(create(clazz, field));
        }
        RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!method.getName().startsWith("set")) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            list.add(create(clazz, (java.lang.reflect.Method) null, method));
        }
        return list.toArray(new Attribute[list.size()]);
    }

    /**
     * 根据Class、字段别名、getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz,
            final String fieldAlias,
            final java.lang.reflect.Method getter,
            final java.lang.reflect.Method setter) {
        return create(clazz, fieldAlias, (Class) null, (java.lang.reflect.Field) null, getter, setter, null);
    }

    /**
     * 根据Class、字段别名、getter和setter方法生成 Attribute 对象。 tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            Class<T> clazz,
            final String fieldAlias,
            final java.lang.reflect.Method getter,
            final java.lang.reflect.Method setter,
            Object attach) {
        return create(clazz, fieldAlias, (Class) null, (java.lang.reflect.Field) null, getter, setter, attach);
    }

    /**
     * 根据Class、字段别名、Field、getter和setter方法生成 Attribute 对象。 Field、tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param field 字段
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            final Class<T> clazz,
            String fieldAlias,
            final java.lang.reflect.Field field,
            java.lang.reflect.Method getter,
            java.lang.reflect.Method setter) {
        return create(clazz, fieldAlias, (Class) null, field, getter, setter, null);
    }

    /**
     * 根据Class、字段别名、Field、getter和setter方法生成 Attribute 对象。 Field、tgetter、setter不能同时为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param field 字段
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            final Class<T> clazz,
            String fieldAlias,
            final java.lang.reflect.Field field,
            java.lang.reflect.Method getter,
            java.lang.reflect.Method setter,
            Object attach) {
        return create(clazz, fieldAlias, (Class) null, field, getter, setter, attach);
    }

    /**
     * 根据Class、字段别名、字段类型生成虚构的 Attribute 对象,get、set方法为空方法。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param fieldType 字段的类
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(final Class<T> clazz, String fieldAlias, final Class<F> fieldType) {
        return create(
                clazz, fieldAlias, fieldType, (java.lang.reflect.Type) null, (Function) null, (BiConsumer) null, null);
    }

    /**
     * 根据Class、字段别名、字段类型生成虚构的 Attribute 对象,get、set方法为空方法。
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param fieldType 字段的类
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            final Class<T> clazz, String fieldAlias, final Class<F> fieldType, Object attach) {
        return create(
                clazz,
                fieldAlias,
                fieldType,
                (java.lang.reflect.Type) null,
                (Function) null,
                (BiConsumer) null,
                attach);
    }

    /**
     * 根据Class、字段别名、字段类型、Field、getter和setter方法生成 Attribute 对象。 fieldAlias/fieldType、Field、tgetter、setter不能同时为null.
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param fieldType 字段类型
     * @param field 字段
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(
            final Class<T> clazz,
            String fieldAlias,
            final Class<F> fieldType,
            final java.lang.reflect.Field field,
            java.lang.reflect.Method getter,
            java.lang.reflect.Method setter) {
        return create(clazz, fieldAlias, fieldType, field, getter, setter, null);
    }

    /**
     * 根据Class、字段别名、字段类型、Field、getter和setter方法生成 Attribute 对象。 fieldAlias/fieldType、Field、getter、setter不能同时为null.
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param fieldType 字段类型
     * @param field 字段
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(
            final Class<T> clazz,
            String fieldAlias,
            final Class<F> fieldType,
            final java.lang.reflect.Field field,
            java.lang.reflect.Method getter,
            java.lang.reflect.Method setter,
            Object attach) {
        return create(null, clazz, fieldAlias, fieldType, field, getter, setter, attach);
    }

    /**
     * 根据Class、字段别名、字段类型、Field、getter和setter方法生成 Attribute 对象。 fieldAlias/fieldType、Field、tgetter、setter不能同时为null.
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param subclass 指定依附的子类
     * @param clazz 指定依附的类
     * @param fieldAlias 字段别名
     * @param fieldType 字段类型
     * @param field 字段
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(
            java.lang.reflect.Type subclass,
            final Class<T> clazz,
            String fieldAlias,
            final Class<F> fieldType,
            final java.lang.reflect.Field field,
            java.lang.reflect.Method getter,
            java.lang.reflect.Method setter,
            Object attach) {
        if (subclass == null) {
            subclass = clazz;
        }
        if (fieldAlias != null && fieldAlias.isEmpty()) {
            fieldAlias = null;
        }
        int mod = field == null ? java.lang.reflect.Modifier.STATIC : field.getModifiers();
        if (field != null && !java.lang.reflect.Modifier.isStatic(mod) && !java.lang.reflect.Modifier.isPublic(mod)) {
            Class t = field.getType();
            String mn = Utility.firstCharUpperCase(field.getName());
            if (getter == null) {
                String prefix = t == boolean.class || t == Boolean.class ? "is" : "get";
                try {
                    getter = clazz.getMethod(prefix + mn);
                } catch (Exception ex) {
                    try {
                        java.lang.reflect.Method m = clazz.getMethod(field.getName());
                        if (Utility.isRecordGetter(clazz, m) && field.getType() == m.getReturnType()) {
                            getter = m;
                        }
                    } catch (Exception ex2) {
                        // do nothing
                    }
                }
            }
            if (setter == null) {
                try {
                    setter = clazz.getMethod("set" + mn, field.getType());
                } catch (Exception ex) {
                    // do nothing
                }
            }
        }
        final java.lang.reflect.Field tfield =
                field == null ? null : (!Modifier.isPublic(mod) || Modifier.isStatic(mod) ? null : field);
        final java.lang.reflect.Method tgetter = getter;
        final java.lang.reflect.Method tsetter = setter;
        String fieldkey = fieldAlias;
        if (fieldAlias == null) {
            if (field != null) {
                fieldAlias = field.getName();
                fieldkey = fieldAlias;
            } else {
                String s = null;
                if (getter != null) {
                    s = Utility.isRecordGetter(getter)
                            ? getter.getName()
                            : getter.getName().substring(getter.getName().startsWith("is") ? 2 : 3);
                    fieldkey = getter.getName();
                } else if (setter != null) {
                    s = setter.getName().substring(3);
                    fieldkey = setter.getName();
                }
                if (s != null) {
                    char[] d = s.toCharArray();
                    if (d.length < 2 || Character.isLowerCase(d[1])) {
                        d[0] = Character.toLowerCase(d[0]);
                    }
                    fieldAlias = new String(d);
                    fieldkey = fieldAlias;
                }
            }
        }
        if (getter != null) { // 防止fieldName/getter/setter名字相同,所以加上1/2/3
            if (setter == null) {
                fieldkey = (fieldAlias == null ? "" : ("0_" + fieldAlias + "_")) + "1_" + getter.getName();
            } else {
                fieldkey = (fieldAlias == null ? "" : ("0_" + fieldAlias + "_")) + "3_" + getter.getName() + "_"
                        + setter.getName();
            }
        } else if (setter != null) {
            fieldkey = (fieldAlias == null ? "" : ("0_" + fieldAlias + "_")) + "2_" + setter.getName();
        }
        if (fieldAlias == null && fieldType == null && tgetter == null && tsetter == null && tfield == null) {
            throw new RedkaleException("[" + clazz + "]have no public field or setter or getter");
        }
        final String fieldName = fieldAlias;
        Class column = fieldType;
        java.lang.reflect.Type generictype = fieldType;
        if (tfield != null) { // public tfield
            column = tfield.getType();
            generictype = tfield.getGenericType();
        } else if (tgetter != null) {
            column = tgetter.getReturnType();
            generictype = tgetter.getGenericReturnType();
        } else if (tsetter != null) {
            column = tsetter.getParameterTypes()[0];
            generictype = tsetter.getGenericParameterTypes()[0];
        } else if (fieldType == null) {
            throw new RedkaleException("[" + clazz + "]have no public field or setter or getter");
        } else if (column == null) {
            throw new RedkaleException("[" + clazz + "]have no field type");
        }
        boolean checkCast = false;
        if (generictype instanceof java.lang.reflect.TypeVariable) {
            checkCast = true;
            generictype = TypeToken.getGenericType(generictype, subclass);
            if (generictype instanceof Class) {
                column = (Class) generictype;
            }
        }
        StringBuilder newsubname = new StringBuilder();
        for (char ch : subclass.toString()
                .replace("class ", "")
                .toCharArray()) { // RetResult<String>与RetResult<Map<String,String>>是不一样的
            if (ch >= '0' && ch <= '9') {
                newsubname.append(ch);
            } else if (ch >= 'a' && ch <= 'z') {
                newsubname.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                newsubname.append(ch);
            } else {
                newsubname.append('_');
            }
        }
        String tostr = "Dyn" + Attribute.class.getSimpleName() + "_" + fieldName + "_" + column.getSimpleName();
        if (fieldkey.contains("1_")) {
            tostr += "_getter";
        } else if (fieldkey.contains("2_")) {
            tostr += "_setter";
        } else if (fieldkey.contains("3_")) {
            tostr += "_getter_setter";
        }
        final Class pcolumn = column;
        if (column.isPrimitive()) {
            column = TypeToken.primitiveToWrapper(column);
        }
        final Class<?> realclz = TypeToken.typeToClass(subclass);
        final ClassDesc interDesc = ClassDesc.ofDescriptor(realclz.descriptorString());
        final ClassDesc columnDesc = ClassDesc.ofDescriptor(column.descriptorString());
        final ClassDesc primitiveDesc = ClassDesc.ofDescriptor(pcolumn.descriptorString());
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        try {
            classLoader.loadClass(realclz.getName());
        } catch (ClassNotFoundException e) {
            // do nothing
        }
        String pkgname = "";
        String clzname = newsubname.toString();
        if (realclz != null) {
            pkgname = realclz.getName();
            int pos = pkgname.lastIndexOf('.');
            if (pos > 0) {
                pkgname = pkgname.substring(0, pos + 1);
            }
            pkgname = pkgname.replace('.', '/');
        }
        final String newDynName = "org/redkaledyn/attribute/" + pkgname + "_Dyn" + Attribute.class.getSimpleName()
                + "__" + clzname + "__" + fieldkey.substring(fieldkey.indexOf('.') + 1);
        try {
            Attribute rs = (Attribute) classLoader
                    .loadClass(newDynName.replace('/', '.'))
                    .getDeclaredConstructor()
                    .newInstance();
            java.lang.reflect.Field _gtype = rs.getClass().getDeclaredField("_gtype");
            _gtype.setAccessible(true);
            _gtype.set(rs, generictype);
            java.lang.reflect.Field _attach = rs.getClass().getDeclaredField("_attach");
            _attach.setAccessible(true);
            _attach.set(rs, attach);
            return rs;
        } catch (Throwable ex) {
            // do nothing
        }
        // ---------------------------------------------------
        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final ClassDesc typeDesc = ClassDesc.of("java.lang.reflect.Type");
        final MethodTypeDesc getDesc = MethodTypeDesc.of(columnDesc, interDesc);
        final MethodTypeDesc setDesc = MethodTypeDesc.of(CD_void, interDesc, columnDesc);
        final boolean primitive = pcolumn.isPrimitive();
        final boolean cast = checkCast;
        final String description = tostr;
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(CD_Object)
                    .withInterfaceSymbols(ClassDesc.of(Attribute.class.getName()));
            cb.with(SignatureAttribute.of(ClassSignature.parseFrom("Ljava/lang/Object;L"
                    + Attribute.class.getName().replace('.', '/') + "<"
                    + interDesc.descriptorString() + columnDesc.descriptorString() + ">;")));
            cb.withField("_gtype", typeDesc, ACC_PRIVATE);
            cb.withField("_attach", CD_Object, ACC_PRIVATE);
            cb.withMethodBody(
                    "<init>",
                    MethodTypeDesc.of(CD_void),
                    ACC_PUBLIC,
                    code -> code.aload(0)
                            .invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void))
                            .return_());
            cb.withMethodBody(
                    "field",
                    MethodTypeDesc.of(CD_String),
                    ACC_PUBLIC,
                    code -> code.ldc(fieldName).areturn());
            cb.withMethodBody("type", MethodTypeDesc.of(CD_Class), ACC_PUBLIC, code -> {
                if (primitive) {
                    code.getstatic(columnDesc, "TYPE", CD_Class);
                } else {
                    code.ldc(primitiveDesc);
                }
                code.areturn();
            });
            cb.withMethodBody(
                    "genericType",
                    MethodTypeDesc.of(typeDesc),
                    ACC_PUBLIC,
                    code -> code.aload(0).getfield(dynDesc, "_gtype", typeDesc).areturn());
            cb.withMethod("attach", MethodTypeDesc.of(CD_Object), ACC_PUBLIC, mb -> {
                mb.with(SignatureAttribute.of(MethodSignature.parseFrom("<E:Ljava/lang/Object;>()TE;")));
                mb.withCode(code ->
                        code.aload(0).getfield(dynDesc, "_attach", CD_Object).areturn());
            });
            cb.withMethodBody(
                    "declaringClass",
                    MethodTypeDesc.of(CD_Class),
                    ACC_PUBLIC,
                    code -> code.ldc(interDesc).areturn());
            cb.withMethodBody("get", getDesc, ACC_PUBLIC, code -> {
                if (tgetter == null && tfield == null) {
                    code.aconst_null();
                } else {
                    if (tgetter == null) {
                        code.aload(1)
                                .getfield(
                                        interDesc,
                                        tfield.getName(),
                                        ClassDesc.ofDescriptor(tfield.getType().descriptorString()));
                    } else {
                        MethodTypeDesc methodDesc = MethodTypeDesc.of(
                                ClassDesc.ofDescriptor(tgetter.getReturnType().descriptorString()));
                        if (Modifier.isStatic(tgetter.getModifiers())) {
                            code.invokestatic(
                                    ClassDesc.of(tgetter.getDeclaringClass().getName()),
                                    tgetter.getName(),
                                    methodDesc,
                                    tgetter.getDeclaringClass().isInterface());
                        } else {
                            code.aload(1);
                            if (realclz.isInterface()) {
                                code.invokeinterface(interDesc, tgetter.getName(), methodDesc);
                            } else {
                                code.invokevirtual(interDesc, tgetter.getName(), methodDesc);
                            }
                        }
                    }
                    if (primitive) {
                        code.invokestatic(columnDesc, "valueOf", MethodTypeDesc.of(columnDesc, primitiveDesc));
                    } else if (cast) {
                        code.checkcast(columnDesc);
                    }
                }
                code.areturn();
            });
            cb.withMethodBody("set", setDesc, ACC_PUBLIC, code -> {
                if (tsetter != null || (tfield != null && !Modifier.isFinal(tfield.getModifiers()))) {
                    if (tsetter == null || !Modifier.isStatic(tsetter.getModifiers())) {
                        code.aload(1);
                    }
                    code.aload(2);
                    if (primitive) {
                        code.invokevirtual(
                                columnDesc, pcolumn.getSimpleName() + "Value", MethodTypeDesc.of(primitiveDesc));
                    }
                    if (tsetter == null) {
                        code.putfield(
                                interDesc,
                                tfield.getName(),
                                ClassDesc.ofDescriptor(tfield.getType().descriptorString()));
                    } else {
                        MethodTypeDesc methodDesc = MethodTypeDesc.of(
                                ClassDesc.ofDescriptor(tsetter.getReturnType().descriptorString()),
                                Arrays.stream(tsetter.getParameterTypes())
                                        .map(t -> ClassDesc.ofDescriptor(t.descriptorString()))
                                        .toArray(ClassDesc[]::new));
                        if (Modifier.isStatic(tsetter.getModifiers())) {
                            code.invokestatic(
                                    ClassDesc.of(tsetter.getDeclaringClass().getName()),
                                    tsetter.getName(),
                                    methodDesc,
                                    tsetter.getDeclaringClass().isInterface());
                        } else if (realclz.isInterface()) {
                            code.invokeinterface(interDesc, tsetter.getName(), methodDesc);
                        } else {
                            code.invokevirtual(interDesc, tsetter.getName(), methodDesc);
                        }
                        if (tsetter.getReturnType() == long.class || tsetter.getReturnType() == double.class) {
                            code.pop2();
                        } else if (tsetter.getReturnType() != void.class) {
                            code.pop();
                        }
                    }
                }
                code.return_();
            });
            MethodTypeDesc bridgeGetDesc = MethodTypeDesc.of(CD_Object, CD_Object);
            if (!getDesc.equals(bridgeGetDesc)) {
                cb.withMethodBody(
                        "get",
                        bridgeGetDesc,
                        ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                        code -> code.aload(0)
                                .aload(1)
                                .checkcast(interDesc)
                                .invokevirtual(dynDesc, "get", getDesc)
                                .areturn());
            }
            MethodTypeDesc bridgeSetDesc = MethodTypeDesc.of(CD_void, CD_Object, CD_Object);
            if (!setDesc.equals(bridgeSetDesc)) {
                cb.withMethodBody(
                        "set",
                        bridgeSetDesc,
                        ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                        code -> code.aload(0)
                                .aload(1)
                                .checkcast(interDesc)
                                .aload(2)
                                .checkcast(columnDesc)
                                .invokevirtual(dynDesc, "set", setDesc)
                                .return_());
            }
            cb.withMethodBody(
                    "toString",
                    MethodTypeDesc.of(CD_String),
                    ACC_PUBLIC,
                    code -> code.ldc(description).areturn());
        });
        Class<Attribute> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            Attribute rs = newClazz.getDeclaredConstructor().newInstance();
            java.lang.reflect.Field _gtype = rs.getClass().getDeclaredField("_gtype");
            _gtype.setAccessible(true);
            _gtype.set(rs, generictype);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), _gtype);
            java.lang.reflect.Field _attach = rs.getClass().getDeclaredField("_attach");
            _attach.setAccessible(true);
            _attach.set(rs, attach);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), _attach);
            return rs;
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * 根据Class、字段名、字段类型、getter和setter方法生成 Attribute 对象。 clazz、fieldName、fieldType都不能为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            @Nonnull Class<T> clazz,
            @Nonnull String fieldName,
            @Nonnull Class<F> fieldType,
            final Function<T, F> getter,
            final BiConsumer<T, F> setter) {
        return create(clazz, fieldName, fieldType, fieldType, getter, setter);
    }

    /**
     * 根据Class、字段名、字段类型、getter和setter方法生成 Attribute 对象。 clazz、fieldName、fieldType都不能为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            @Nonnull Class<T> clazz,
            @Nonnull String fieldName,
            @Nonnull Class<F> fieldType,
            final Function<T, F> getter,
            final BiConsumer<T, F> setter,
            Object attach) {
        return create(clazz, fieldName, fieldType, fieldType, getter, setter, attach);
    }

    /**
     * 根据Class、字段名、字段类型、getter和setter方法生成 Attribute 对象。 clazz、fieldName、fieldType都不能为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param fieldGenericType 字段泛型
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            @Nonnull Class<T> clazz,
            @Nonnull String fieldName,
            @Nonnull Class<F> fieldType,
            final java.lang.reflect.Type fieldGenericType,
            final Function<T, F> getter,
            final BiConsumer<T, F> setter) {
        return create(clazz, fieldName, fieldType, fieldGenericType, getter, setter, null);
    }

    /**
     * 根据Class、字段名、字段类型、getter和setter方法生成 Attribute 对象。 clazz、fieldName、fieldType都不能为null
     *
     * @param <T> 依附类的类型
     * @param <F> 字段类型
     * @param clazz 指定依附的类
     * @param fieldName 字段名
     * @param fieldType 字段类型
     * @param fieldGenericType 字段泛型
     * @param getter getter方法
     * @param setter setter方法
     * @param attach 附加对象
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(
            @Nonnull final Class<T> clazz,
            @Nonnull final String fieldName,
            @Nonnull final Class<F> fieldType,
            final java.lang.reflect.Type fieldGenericType,
            final Function<T, F> getter,
            final BiConsumer<T, F> setter,
            final Object attach) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(fieldType);
        String str = Attribute.class.getSimpleName() + "_" + fieldName + "_" + fieldType.getSimpleName();
        if (getter != null) {
            str += "_getter";
        }
        if (setter != null) {
            str += "_setter";
        }
        final String tostr = str;
        return new Attribute<T, F>() {
            @Override
            public Class<F> type() {
                return fieldType;
            }

            @Override
            public java.lang.reflect.Type genericType() {
                return fieldGenericType;
            }

            @Override
            public <E> E attach() {
                return (E) attach;
            }

            @Override
            public Class<T> declaringClass() {
                return clazz;
            }

            @Override
            public String field() {
                return fieldName;
            }

            @Override
            public F get(T obj) {
                return getter == null ? null : getter.apply(obj);
            }

            @Override
            public void set(T obj, F value) {
                if (setter != null) {
                    setter.accept(obj, value);
                }
            }

            @Override
            public String toString() {
                return tostr;
            }
        };
    }

    /**
     * 根据Map类生成 Attribute 对象。 fieldName都不能为null
     *
     * @param fieldName 字段名
     * @param <T> 泛型
     * @param <F> 泛型
     * @return Attribute对象
     */
    public static <T extends Map, F> Attribute<T, F> map(final String fieldName) {
        return new Attribute<T, F>() {
            @Override
            public Class<? extends F> type() {
                return (Class) Object.class;
            }

            @Override
            public Class<T> declaringClass() {
                return (Class) Map.class;
            }

            @Override
            public String field() {
                return fieldName;
            }

            @Override
            public F get(T obj) {
                return (F) ((Map) obj).get(fieldName);
            }

            @Override
            public void set(T obj, F value) {
                ((Map) obj).put(fieldName, value);
            }
        };
    }
}
