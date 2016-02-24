/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.*;

/**
 * 该类实现动态映射一个JavaBean类中成员对应的getter、setter方法； 代替低效的反射实现方式。
 * <blockquote><pre>
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
 * </pre></blockquote>
 * 获取name的 Attribute ：
 * <blockquote><pre>
 *  Attribute&lt;Record, String&gt; nameAction = Attribute.create(Record.class.getDeclaredField("name"));
 * </pre></blockquote>
 * 等价于:
 * <blockquote><pre>
 *  Attribute&lt;Record, String&gt; nameAction = new Attribute&lt;Record, String&gt;() {
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
 *      public Class declaringClass() {
 *          return Record.class;
 *      }
 *  };
 * </pre></blockquote>
 * <p>
 * 映射Field时，field必须满足以下条件之一： <br>
 * 1、field属性是public且非final <br>
 * 2、至少存在对应的getter、setter方法中的一个 <br>
 * 当不存在getter方法时，get操作固定返回null <br>
 * 当不存在setter方法时，set操作为空方法  <br>
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 * @param <T> 字段依附的类
 * @param <F> 字段的数据类型
 */
public interface Attribute<T, F> {

    /**
     * 返回字段的数据类型
     *
     * @return 字段的数据类型
     */
    public Class<? extends F> type();

    /**
     * 返回字段依附的类名
     *
     * @return 依附的类名
     */
    public Class<T> declaringClass();

    /**
     * 返回字段名
     *
     * @return 字段名
     */
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
     * @param obj   指定对象
     * @param value 字段新值
     */
    public void set(T obj, F value);

    /**
     * 根据一个Field生成 Attribute 对象。
     *
     * @param <T>   依附类的类型
     * @param <F>   字段类型
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(final java.lang.reflect.Field field) {
        return create((Class<T>) field.getDeclaringClass(), field.getName(), field, null, null);
    }

    /**
     * 根据一个Field和field的别名生成 Attribute 对象。
     *
     * @param <T>        依附类的类型
     * @param <F>        字段类型
     * @param fieldalias 别名
     * @param field      字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(String fieldalias, final java.lang.reflect.Field field) {
        return create((Class<T>) field.getDeclaringClass(), fieldalias, field, null, null);
    }

    /**
     * 根据一个Class和field真实名称生成 Attribute 对象。
     *
     * @param <T>       依附类的类型
     * @param <F>       字段类型
     * @param clazz     指定依附的类
     * @param fieldname 字段名，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldname) {
        try {
            return create(clazz, fieldname, clazz.getDeclaredField(fieldname), null, null);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 根据一个Class和Field生成 Attribute 对象。
     *
     * @param <T>   依附类的类型
     * @param <F>   字段类型
     * @param clazz 指定依附的类
     * @param field 字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final java.lang.reflect.Field field) {
        return create(clazz, field.getName(), field);
    }

    /**
     * 根据一个Class、field别名和Field生成 Attribute 对象。
     *
     * @param <T>        依附类的类型
     * @param <F>        字段类型
     * @param clazz      指定依附的类
     * @param fieldalias 字段别名
     * @param field      字段，如果该字段不存在则抛异常
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldalias, final java.lang.reflect.Field field) {
        return create(clazz, fieldalias, field, null, null);
    }

    /**
     * 根据一个getter和setter方法生成 Attribute 对象。
     * tgetter、setter不能同时为null
     *
     * @param <T>    依附类的类型
     * @param <F>    字段类型
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(final java.lang.reflect.Method getter, final java.lang.reflect.Method setter) {
        return create((Class) (getter == null ? setter.getDeclaringClass() : getter.getDeclaringClass()), null, null, getter, setter);
    }

    /**
     * 根据Class、getter和setter方法生成 Attribute 对象。
     * tgetter、setter不能同时为null
     *
     * @param <T>    依附类的类型
     * @param <F>    字段类型
     * @param clazz  指定依附的类
     * @param getter getter方法
     * @param setter setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final java.lang.reflect.Method getter, final java.lang.reflect.Method setter) {
        return create(clazz, null, null, getter, setter);
    }

    /**
     * 根据Class、字段别名、getter和setter方法生成 Attribute 对象。
     * tgetter、setter不能同时为null
     *
     * @param <T>        依附类的类型
     * @param <F>        字段类型
     * @param clazz      指定依附的类
     * @param fieldalias 字段别名
     * @param getter     getter方法
     * @param setter     setter方法
     * @return Attribute对象
     */
    public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldalias, final java.lang.reflect.Method getter, final java.lang.reflect.Method setter) {
        return create(clazz, fieldalias, null, getter, setter);
    }

    /**
     * 根据Class、字段别名、Field、getter和setter方法生成 Attribute 对象。
     * Field、tgetter、setter不能同时为null
     *
     * @param <T>        依附类的类型
     * @param <F>        字段类型
     * @param clazz      指定依附的类
     * @param fieldalias 字段别名
     * @param field      字段
     * @param getter     getter方法
     * @param setter     setter方法
     * @return Attribute对象
     */
    @SuppressWarnings("unchecked")
    public static <T, F> Attribute<T, F> create(final Class<T> clazz, String fieldalias, final java.lang.reflect.Field field, java.lang.reflect.Method getter, java.lang.reflect.Method setter) {
        if (fieldalias != null && fieldalias.isEmpty()) fieldalias = null;
        int mod = field == null ? java.lang.reflect.Modifier.STATIC : field.getModifiers();
        if (field != null && !java.lang.reflect.Modifier.isStatic(mod) && !java.lang.reflect.Modifier.isPublic(mod)) {
            Class t = field.getType();
            char[] fs = field.getName().toCharArray();
            fs[0] = Character.toUpperCase(fs[0]);
            String mn = new String(fs);
            if (getter == null) {
                String prefix = t == boolean.class || t == Boolean.class ? "is" : "get";
                try {
                    getter = clazz.getMethod(prefix + mn);
                } catch (Exception ex) {
                }
            }
            if (setter == null) {
                try {
                    setter = clazz.getMethod("set" + mn, field.getType());
                } catch (Exception ex) {
                }
            }
        }
        final java.lang.reflect.Field tfield = field == null ? null : (!java.lang.reflect.Modifier.isPublic(mod) || java.lang.reflect.Modifier.isStatic(mod) ? null : field);
        final java.lang.reflect.Method tgetter = getter;
        final java.lang.reflect.Method tsetter = setter;
        if (fieldalias == null) {
            if (field != null) {
                fieldalias = field.getName();
            } else {
                String s;
                if (getter != null) {
                    s = getter.getName().substring(getter.getName().startsWith("is") ? 2 : 3);
                } else {
                    s = setter.getName().substring(3);
                }
                char[] d = s.toCharArray();
                if (d.length < 2 || Character.isLowerCase(d[1])) {
                    d[0] = Character.toLowerCase(d[0]);
                }
                fieldalias = new String(d);
            }
        }
        if (tgetter == null && tsetter == null && tfield == null) {
            throw new RuntimeException("[" + clazz + "]have no public field or setter or getter");
        }
        final String fieldname = fieldalias;
        Class column;
        if (tfield != null) { // public tfield
            column = tfield.getType();
        } else if (tgetter != null) {
            column = tgetter.getReturnType();
        } else { // tsetter != null
            column = tsetter.getParameterTypes()[0];
        }
        final Class pcolumn = column;
        if (column.isPrimitive()) column = java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(column, 1), 0).getClass();
        final String supDynName = Attribute.class.getName().replace('.', '/');
        final String interName = clazz.getName().replace('.', '/');
        final String columnName = column.getName().replace('.', '/');
        final String interDesc = Type.getDescriptor(clazz);
        final String columnDesc = Type.getDescriptor(column);

        ClassLoader loader = Attribute.class.getClassLoader();
        String newDynName = supDynName + "_Dyn_" + clazz.getSimpleName() + "_"
                + fieldname.substring(fieldname.indexOf('.') + 1) + "_" + pcolumn.getSimpleName().replace("[]", "Array");
        if (String.class.getClassLoader() != clazz.getClassLoader()) {
            loader = clazz.getClassLoader();
            newDynName = interName + "_Dyn" + Attribute.class.getSimpleName() + "_"
                    + fieldname.substring(fieldname.indexOf('.') + 1) + "_" + pcolumn.getSimpleName().replace("[]", "Array");
        }
        try {
            return (Attribute) Class.forName(newDynName.replace('/', '.')).newInstance();
        } catch (Throwable ex) {
        }
        //---------------------------------------------------
        final ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + interDesc + columnDesc + ">;", "java/lang/Object", new String[]{supDynName});

        { //构造方法
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        { //field 方法
            mv = cw.visitMethod(ACC_PUBLIC, "field", "()Ljava/lang/String;", null, null);
            mv.visitLdcInsn(fieldname);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //type 方法
            mv = cw.visitMethod(ACC_PUBLIC, "type", "()Ljava/lang/Class;", null, null);
            if (pcolumn == boolean.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == byte.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == char.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == short.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == int.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == float.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == long.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
            } else if (pcolumn == double.class) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
            } else {
                mv.visitLdcInsn(Type.getType(pcolumn));
            }
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //declaringClass 方法
            mv = cw.visitMethod(ACC_PUBLIC, "declaringClass", "()Ljava/lang/Class;", null, null);
            mv.visitLdcInsn(Type.getType(clazz));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //get 方法
            mv = cw.visitMethod(ACC_PUBLIC, "get", "(" + interDesc + ")" + columnDesc, null, null);
            int m = 1;
            if (tgetter == null) {
                if (tfield == null) {
                    mv.visitInsn(ACONST_NULL);
                } else {  //public tfield
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(GETFIELD, interName, tfield.getName(), Type.getDescriptor(pcolumn));
                    if (pcolumn != column) {
                        mv.visitMethodInsn(INVOKESTATIC, columnName, "valueOf", "(" + Type.getDescriptor(pcolumn) + ")" + columnDesc, false);
                        m = 2;
                    }
                }
            } else {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, interName, tgetter.getName(), Type.getMethodDescriptor(tgetter), false);
                if (pcolumn != column) {
                    mv.visitMethodInsn(INVOKESTATIC, columnName, "valueOf", "(" + Type.getDescriptor(pcolumn) + ")" + columnDesc, false);
                    m = 2;
                }
            }
            mv.visitInsn(ARETURN);
            mv.visitMaxs(m, 2);
            mv.visitEnd();
        }
        { //set 方法
            mv = cw.visitMethod(ACC_PUBLIC, "set", "(" + interDesc + columnDesc + ")V", null, null);
            int m = 2;
            if (tsetter == null) {
                if (tfield == null || java.lang.reflect.Modifier.isFinal(tfield.getModifiers())) {
                    m = 0;
                } else { //public tfield
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    if (pcolumn != column) {
                        try {
                            java.lang.reflect.Method pm = column.getMethod(pcolumn.getSimpleName() + "Value");
                            mv.visitMethodInsn(INVOKEVIRTUAL, columnName, pm.getName(), Type.getMethodDescriptor(pm), false);
                            m = 3;
                        } catch (Exception ex) {
                            throw new RuntimeException(ex); //不可能会发生
                        }
                    }
                    mv.visitFieldInsn(PUTFIELD, interName, tfield.getName(), Type.getDescriptor(pcolumn));
                }
            } else {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                if (pcolumn != column) {
                    try {
                        java.lang.reflect.Method pm = column.getMethod(pcolumn.getSimpleName() + "Value");
                        mv.visitMethodInsn(INVOKEVIRTUAL, columnName, pm.getName(), Type.getMethodDescriptor(pm), false);
                        m = 3;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex); //不可能会发生
                    }
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, interName, tsetter.getName(), Type.getMethodDescriptor(tsetter), false);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(m, 3);
            mv.visitEnd();
        }
        { //虚拟get
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, interName);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "get", "(" + interDesc + ")" + columnDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        {//虚拟set
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, interName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, columnName);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "set", "(" + interDesc + columnDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<Attribute> creatorClazz = (Class<Attribute>) new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            return creatorClazz.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
