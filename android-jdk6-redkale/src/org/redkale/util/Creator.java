/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.beans.ConstructorProperties;
import java.lang.reflect.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;

/**
 * 实现一个类的构造方法。 代替低效的反射实现方式。 不支持数组类
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
public interface Creator<T> {

    public T create(Object... params);

    public static abstract class Creators {

        @SuppressWarnings("unchecked")
        public static <T> Creator<T> create(Class<T> clazz) {
            if (clazz.isAssignableFrom(ArrayList.class)) {
                clazz = (Class<T>) ArrayList.class;
            } else if (clazz.isAssignableFrom(HashMap.class)) {
                clazz = (Class<T>) HashMap.class;
            } else if (clazz.isAssignableFrom(HashSet.class)) {
                clazz = (Class<T>) HashSet.class;
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                throw new RuntimeException("[" + clazz + "] is a interface or abstract class, cannot create it's Creator.");
            }
            final String supDynName = Creator.class.getName().replace('.', '/');
            final String interName = clazz.getName().replace('.', '/');
            final String interDesc = Type.getDescriptor(clazz);
            ClassLoader loader = Creator.class.getClassLoader();
            String newDynName = supDynName + "_" + clazz.getSimpleName() + "_" + (System.currentTimeMillis() % 10000);
            if (String.class.getClassLoader() != clazz.getClassLoader()) {
                loader = clazz.getClassLoader();
                newDynName = interName + "_Dyn" + Creator.class.getSimpleName();
            }
            try {
                return (Creator) Class.forName(newDynName.replace('/', '.')).newInstance();
            } catch (Exception ex) {
            }
            Constructor<T> constructor = null;
            for (Constructor c : clazz.getConstructors()) {
                if (c.getParameterCount() == 0) {
                    constructor = c;
                    break;
                }
            }
            if (constructor == null) {
                for (Constructor c : clazz.getConstructors()) {
                    if (c.getAnnotation(ConstructorProperties.class) != null) {
                        constructor = c;
                        break;
                    }
                }
            }
            if (constructor == null) throw new RuntimeException("[" + clazz + "] have no public or java.beans.ConstructorProperties-Annotation constructor.");
            //-------------------------------------------------------------
            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;
            cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + interDesc + ">;", "java/lang/Object", new String[]{supDynName});

            {//构造方法
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                ConstructorProperties cps = constructor.getAnnotation(ConstructorProperties.class);
                if (cps != null) {
                    av0 = mv.visitAnnotation(Type.getDescriptor(ConstructorProperties.class), true);
                    AnnotationVisitor av1 = av0.visitArray("value");
                    for (String n : cps.value()) {
                        av1.visit(null, n);
                    }
                    av1.visitEnd();
                    av0.visitEnd();
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {//create 方法
                mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "create", "([Ljava/lang/Object;)L" + interName + ";", null, null);
                mv.visitTypeInsn(NEW, interName);
                mv.visitInsn(DUP);
                //---------------------------------------
                {
                    Parameter[] params = constructor.getParameters();
                    final int[] iconsts = {ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5};
                    for (int i = 0; i < params.length; i++) {
                        mv.visitVarInsn(ALOAD, 1);
                        if (i < 6) {
                            mv.visitInsn(iconsts[i]);
                        } else {
                            mv.visitIntInsn(BIPUSH, i);
                        }
                        mv.visitInsn(AALOAD);
                        Class ct = params[i].getType();
                        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(ct));
                        if (ct.isPrimitive()) {
                            Class fct = Array.get(Array.newInstance(ct, 1), 0).getClass();
                            try {
                                Method pm = ct.getMethod(ct.getSimpleName() + "Value");
                                mv.visitMethodInsn(INVOKEVIRTUAL, fct.getName().replace('.', '/'), pm.getName(), Type.getMethodDescriptor(pm), false);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex); //不可能会发生
                            }
                        }
                    }
                }
                //---------------------------------------
                mv.visitMethodInsn(INVOKESPECIAL, interName, "<init>", Type.getConstructorDescriptor(constructor), false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs((constructor.getParameterCount() > 0 ? (constructor.getParameterCount() + 3) : 2), 2);
                mv.visitEnd();
            }
            { //虚拟 create 方法
                mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_VARARGS + ACC_SYNTHETIC, "create", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "create", "([Ljava/lang/Object;)" + interDesc, false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            Class<?> creatorClazz = new ClassLoader(loader) {
                public final Class<?> loadClass(String name, byte[] b) {
                    return defineClass(name, b, 0, b.length);
                }
            }.loadClass(newDynName.replace('/', '.'), bytes);
            try {
                return (Creator) creatorClazz.newInstance();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
