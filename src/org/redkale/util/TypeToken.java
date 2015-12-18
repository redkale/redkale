/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 *
 * 获取泛型的Type类
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
public abstract class TypeToken<T> {

    private final Type type;

    public TypeToken() {
        type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public final Type getType() {
        return type;
    }

    public static Type createParameterizedType(final Class rawType, final Class... actualTypeArguments) {
        ClassLoader loader = TypeToken.class.getClassLoader();
        String newDynName = TypeToken.class.getName().replace('.', '/') + "_Dyn" + System.currentTimeMillis();
        for (;;) {
            try {
                Class.forName(newDynName.replace('/', '.'));
                newDynName = TypeToken.class.getName().replace('.', '/') + "_Dyn" + Math.abs(System.nanoTime());
            } catch (Exception ex) {  //异常说明类不存在
                break;
            }
        }
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, "java/lang/Object", null);
        String rawTypeDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(rawType);
        StringBuilder sb = new StringBuilder();
        sb.append(rawTypeDesc.substring(0, rawTypeDesc.length() - 1)).append('<');
        for (Class c : actualTypeArguments) {
            sb.append(jdk.internal.org.objectweb.asm.Type.getDescriptor(c));
        }
        sb.append(">;");
        {
            fv = cw.visitField(ACC_PUBLIC, "field", rawTypeDesc, sb.toString(), null);
            fv.visitEnd();
        }
        {//构造方法
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            return newClazz.getField("field").getGenericType();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
