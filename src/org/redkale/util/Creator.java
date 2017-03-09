/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.beans.*;
import java.io.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * <p>
 * 实现一个类的构造方法。 代替低效的反射实现方式。 不支持数组类。
 * 常见的无参数的构造函数类都可以自动生成Creator， 对应自定义的类可以提供一个静态构建Creator方法。
 * 例如:
 * <blockquote><pre>
 * public class Record {
 *
 *    private final int id;
 *
 *    private String name;
 *
 *    Record(int id, String name) {
 *        this.id = id;
 *        this.name = name;
 *    }
 *
 *    private static Creator createCreator() {
 *        return new Creator&lt;Record&gt;() {
 *            &#64;Override
 *            &#64;ConstructorParameters({"id", "name"})
 *            public Record create(Object... params) {
 *                if(params[0] == null) params[0] = 0;
 *                return new Record((Integer) params[0], (String) params[1]);
 *            }
 *         };
 *    }
 * }
 * </pre></blockquote>
 *
 * 或者:
 * <blockquote><pre>
 * public class Record {
 *
 *    private final int id;
 *
 *    private String name;
 *
 *    &#64;java.beans.ConstructorProperties({"id", "name"})
 *    public Record(int id, String name) {
 *        this.id = id;
 *        this.name = name;
 *    }
 * }
 * </pre></blockquote>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 构建对象的数据类型
 */
public interface Creator<T> {

    /**
     * 该注解只用于Creator.create方法上， 与 java.beans.ConstructorProperties 类似。
     *
     */
    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    public static @interface ConstructorParameters {

        String[] value();
    }

    /**
     * 创建对象
     *
     * @param params 构造函数的参数
     *
     * @return 构建的对象
     */
    public T create(Object... params);

    /**
     * 根据指定的class采用ASM技术生产Creator。
     *
     * @param <T>   构建类的数据类型
     * @param clazz 构建类
     *
     * @return Creator对象
     */
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
        for (final Method method : clazz.getDeclaredMethods()) { //查找类中是否存在提供创建Creator实例的静态方法
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterTypes().length != 0) continue;
            if (method.getReturnType() != Creator.class) continue;
            try {
                method.setAccessible(true);
                return (Creator<T>) method.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
        Constructor<T> constructor0 = null;
        for (Constructor c : clazz.getConstructors()) {  //优先找public 的构造函数
            if (c.getParameterCount() == 0) {
                constructor0 = c;
                break;
            }
        }
        if (constructor0 == null) {//其次找非private带ConstructorProperties的构造函数
            for (Constructor c : clazz.getDeclaredConstructors()) {
                if (Modifier.isPrivate(c.getModifiers())) continue;
                if (c.getAnnotation(ConstructorProperties.class) != null) {
                    constructor0 = c;
                    break;
                }
            }
        }
        if (constructor0 == null) {//再次找非private且带-parameters编译项的构造函数 java 8以上才支持
            for (Constructor c : clazz.getDeclaredConstructors()) {
                if (Modifier.isPrivate(c.getModifiers())) continue;
                Parameter[] params = c.getParameters();
                if (params.length == 0) continue;
                boolean flag = true;
                for (Parameter param : params) {
                    try {
                        clazz.getDeclaredField(param.getName());
                    } catch (Exception e) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    constructor0 = c;
                    break;
                }
            }
        }
        if (constructor0 == null) {//最后找非private的空构造函数
            for (Constructor c : clazz.getDeclaredConstructors()) {
                if (Modifier.isPrivate(c.getModifiers())) continue;
                if (c.getParameterCount() == 0) {
                    constructor0 = c;
                    break;
                }
            }
        }
        final Constructor<T> constructor = constructor0;
        if (constructor == null) throw new RuntimeException("[" + clazz + "] have no public or java.beans.ConstructorProperties-Annotation constructor.");
        //-------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + interDesc + ">;", "java/lang/Object", new String[]{supDynName});

        {//构造方法
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {//create 方法
            mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "create", "([Ljava/lang/Object;)L" + interName + ";", null, null);
            ConstructorProperties cps = constructor.getAnnotation(ConstructorProperties.class);
            String[] cparams = cps == null ? null : cps.value();
            if (cparams == null && constructor.getParameterCount() > 0) { // java 8 以上版本才支持的 -parameters 编译项
                Parameter[] params = constructor.getParameters();
                String[] ss = new String[params.length];
                for (int i = 0; i < ss.length; i++) {
                    try {
                        clazz.getDeclaredField(params[i].getName());
                    } catch (Exception e) { //没有该字段，直接退出
                        ss = null;
                        break;
                    }
                    ss[i] = params[i].getName();
                }
                cparams = ss;
            }
            if (cparams != null) {
                av0 = mv.visitAnnotation(Type.getDescriptor(ConstructorParameters.class), true);
                AnnotationVisitor av1 = av0.visitArray("value");
                for (String n : cparams) {
                    av1.visit(null, n);
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            final Class[] paramTypes = constructor.getParameterTypes();
            final int[] iconsts = {ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5};
            {  //有Primitive数据类型且值为null的参数需要赋默认值
                for (int i = 0; i < paramTypes.length; i++) {
                    final Class pt = paramTypes[i];
                    if (!pt.isPrimitive()) continue;
                    mv.visitVarInsn(ALOAD, 1);
                    if (i < 6) {
                        mv.visitInsn(iconsts[i]);
                    } else {
                        mv.visitIntInsn(BIPUSH, i);
                    }
                    mv.visitInsn(AALOAD);
                    Label lab = new Label();
                    mv.visitJumpInsn(IFNONNULL, lab);
                    mv.visitVarInsn(ALOAD, 1);
                    if (i < 6) {
                        mv.visitInsn(iconsts[i]);
                    } else {
                        mv.visitIntInsn(BIPUSH, i);
                    }
                    if (pt == int.class) {
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    } else if (pt == long.class) {
                        mv.visitInsn(LCONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    } else if (pt == boolean.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                    } else if (pt == short.class) {
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    } else if (pt == float.class) {
                        mv.visitInsn(FCONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    } else if (pt == byte.class) {
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    } else if (pt == double.class) {
                        mv.visitInsn(DCONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    } else if (pt == char.class) {
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    }
                    mv.visitInsn(AASTORE);
                    mv.visitLabel(lab);
                }
            }
            mv.visitTypeInsn(NEW, interName);
            mv.visitInsn(DUP);
            //---------------------------------------
            {
                for (int i = 0; i < paramTypes.length; i++) {
                    mv.visitVarInsn(ALOAD, 1);
                    if (i < 6) {
                        mv.visitInsn(iconsts[i]);
                    } else {
                        mv.visitIntInsn(BIPUSH, i);
                    }
                    mv.visitInsn(AALOAD);
                    final Class ct = paramTypes[i];
                    if (ct.isPrimitive()) {
                        final Class bigct = Array.get(Array.newInstance(ct, 1), 0).getClass();
                        mv.visitTypeInsn(CHECKCAST, bigct.getName().replace('.', '/'));
                        try {
                            Method pm = bigct.getMethod(ct.getSimpleName() + "Value");
                            mv.visitMethodInsn(INVOKEVIRTUAL, bigct.getName().replace('.', '/'), pm.getName(), Type.getMethodDescriptor(pm), false);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex); //不可能会发生
                        }
                    } else {
                        mv.visitTypeInsn(CHECKCAST, ct.getName().replace('.', '/'));
                    }
                }
            }
            //---------------------------------------
            mv.visitMethodInsn(INVOKESPECIAL, interName, "<init>", Type.getConstructorDescriptor(constructor), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs((paramTypes.length > 0 ? (paramTypes.length + 3) : 2), 2);
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
        final byte[] bytes = cw.toByteArray();
        final boolean ispub = Modifier.isPublic(constructor.getModifiers());
        Class<?> resultClazz = null;
        if (loader instanceof URLClassLoader && !ispub) {
            try {
                final URLClassLoader urlLoader = (URLClassLoader) loader;
                final URL url = new URL("memclass", "localhost", -1, "/" + newDynName.replace('/', '.') + "/", new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        return new URLConnection(u) {
                            @Override
                            public void connect() throws IOException {
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(bytes);
                            }
                        };
                    }
                });
                Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURLMethod.setAccessible(true);
                addURLMethod.invoke(urlLoader, url);
                resultClazz = urlLoader.loadClass(newDynName.replace('/', '.'));
            } catch (Throwable t) { //异常无需理会， 使用下一种loader方式
                t.printStackTrace();
            }
        }
        if (!ispub && resultClazz == null) throw new RuntimeException("[" + clazz + "] have no public or java.beans.ConstructorProperties-Annotation constructor.");
        try {
            if (resultClazz == null) resultClazz = new ClassLoader(loader) {
                    public final Class<?> loadClass(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }.loadClass(newDynName.replace('/', '.'), bytes);
            return (Creator) resultClazz.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
