/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Type;

/**
 * 该类功能是动态映射一个Data类中成员对应的getter、setter方法； 代替低效的反射实现方式。
 * 映射Field时，field必须满足以下条件之一：
 * 1、field属性是public且非final
 * 2、至少存在对应的getter、setter方法中的一个
 * 当不存在getter方法时，get操作规定返回null
 * 当不存在setter方法时，set操作为空方法
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 * @param <F>
 */
public interface Attribute<T, F> {

    public Class<? extends F> type();

    public Class<T> declaringClass();

    public String field();

    public F get(T obj);

    public void set(T obj, F value);

    public static abstract class Attributes {

        /**
         * 根据一个Field生成 Attribute 对象。
         *
         * @param <T>
         * @param <F>
         * @param field
         * @return
         */
        public static <T, F> Attribute<T, F> create(final Field field) {
            return create((Class<T>) field.getDeclaringClass(), field.getName(), field, null, null);
        }

        /**
         * 根据一个Field和field的别名生成 Attribute 对象。
         *
         * @param <T>
         * @param <F>
         * @param fieldname 别名
         * @param field
         * @return
         */
        public static <T, F> Attribute<T, F> create(String fieldname, final Field field) {
            return create((Class<T>) field.getDeclaringClass(), fieldname, field, null, null);
        }

        /**
         * 根据一个Class和field名生成 Attribute 对象。
         *
         * @param <T>
         * @param <F>
         * @param clazz
         * @param fieldname 字段名， 如果该字段不存在则抛异常
         * @return
         */
        public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldname) {
            try {
                return create(clazz, fieldname, clazz.getDeclaredField(fieldname), null, null);
            } catch (NoSuchFieldException ex) {
                throw new RuntimeException(ex);
            } catch (SecurityException ex) {
                throw new RuntimeException(ex);
            }
        }

        public static <T, F> Attribute<T, F> create(Class<T> clazz, final java.lang.reflect.Field field) {
            return create(clazz, field.getName(), field);
        }

        public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldname, final java.lang.reflect.Field field) {
            return create(clazz, fieldname, field, null, null);
        }

        /**
         * getter、setter不能全为null
         *
         * @param <T>
         * @param <F>
         * @param getter
         * @param setter
         * @return
         */
        public static <T, F> Attribute<T, F> create(final Method getter, final Method setter) {
            return create((Class) (getter == null ? setter.getDeclaringClass() : getter.getDeclaringClass()), null, null, getter, setter);
        }

        /**
         * getter、setter不能全为null
         *
         * @param <T>
         * @param <F>
         * @param clazz
         * @param getter
         * @param setter
         * @return
         */
        public static <T, F> Attribute<T, F> create(Class<T> clazz, final Method getter, final Method setter) {
            return create(clazz, null, null, getter, setter);
        }

        /**
         * getter、setter不能全为null
         *
         * @param <T>
         * @param <F>
         * @param clazz
         * @param fieldalias
         * @param getter
         * @param setter
         * @return
         */
        public static <T, F> Attribute<T, F> create(Class<T> clazz, final String fieldalias, final Method getter, final Method setter) {
            return create(clazz, fieldalias, null, getter, setter);
        }

        /**
         * field、getter、setter不能全为null
         *
         * @param <T>
         * @param <F>
         * @param clazz
         * @param fieldalias0
         * @param field0
         * @param getter0
         * @param setter0
         * @return
         */
        @SuppressWarnings("unchecked")
        public static <T, F> Attribute<T, F> create(final Class<T> clazz, String fieldalias0, final Field field0, Method getter0, Method setter0) {
            if (fieldalias0 != null && fieldalias0.isEmpty()) fieldalias0 = null;
            int mod = field0 == null ? Modifier.STATIC : field0.getModifiers();
            if (field0 != null && !Modifier.isStatic(mod) && !Modifier.isPublic(mod)) {
                Class t = field0.getType();
                char[] fs = field0.getName().toCharArray();
                fs[0] = Character.toUpperCase(fs[0]);
                String mn = new String(fs);
                if (getter0 == null) {
                    String prefix = t == boolean.class || t == Boolean.class ? "is" : "get";
                    try {
                        getter0 = clazz.getMethod(prefix + mn);
                    } catch (Exception ex) {
                    }
                }
                if (setter0 == null) {
                    try {
                        setter0 = clazz.getMethod("set" + mn, field0.getType());
                    } catch (Exception ex) {
                    }
                }
            }
            final Field field = field0 == null ? null : (!Modifier.isPublic(mod) || Modifier.isStatic(mod) ? null : field0);
            final java.lang.reflect.Method getter = getter0;
            final java.lang.reflect.Method setter = setter0;
            if (fieldalias0 == null) {
                if (field0 != null) {
                    fieldalias0 = field0.getName();
                } else {
                    String s;
                    if (getter0 != null) {
                        s = getter0.getName().substring(getter0.getName().startsWith("is") ? 2 : 3);
                    } else {
                        s = setter0.getName().substring(3);
                    }
                    char[] d = s.toCharArray();
                    if (d.length < 2 || Character.isLowerCase(d[1])) {
                        d[0] = Character.toLowerCase(d[0]);
                    }
                    fieldalias0 = new String(d);
                }
            }
            if (getter == null && setter == null && field == null) {
                throw new RuntimeException("[" + clazz + "]have no public field or setter or getter");
            }
            final String fieldname = fieldalias0;
            Class column;
            if (field != null) { // public field
                column = field.getType();
            } else if (getter != null) {
                column = getter.getReturnType();
            } else { // setter != null
                column = setter.getParameterTypes()[0];
            }
            final Class pcolumn = column;
            if (column.isPrimitive()) column = Array.get(Array.newInstance(column, 1), 0).getClass();
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
            final org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
            org.objectweb.asm.MethodVisitor mv;

            cw.visit(V1_7, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + interDesc + columnDesc + ">;", "java/lang/Object", new String[]{supDynName});

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
                if (getter == null) {
                    if (field == null) {
                        mv.visitInsn(ACONST_NULL);
                    } else {  //public field
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(GETFIELD, interName, field.getName(), Type.getDescriptor(pcolumn));
                        if (pcolumn != column) {
                            mv.visitMethodInsn(INVOKESTATIC, columnName, "valueOf", "(" + Type.getDescriptor(pcolumn) + ")" + columnDesc, false);
                            m = 2;
                        }
                    }
                } else {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, interName, getter.getName(), Type.getMethodDescriptor(getter), false);
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
                if (setter == null) {
                    if (field == null || Modifier.isFinal(field.getModifiers())) {
                        m = 0;
                    } else { //public field
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
                        mv.visitFieldInsn(PUTFIELD, interName, field.getName(), Type.getDescriptor(pcolumn));
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
                    mv.visitMethodInsn(INVOKEVIRTUAL, interName, setter.getName(), Type.getMethodDescriptor(setter), false);
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
}
