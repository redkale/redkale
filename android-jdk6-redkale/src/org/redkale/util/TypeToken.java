/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.GenericArrayType;
import java.util.*;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.*;

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

    /**
     * 判断Type是否能确定最终的class， 是则返回true，存在通配符或者不确定类型则返回false。
     * 例如: Map&#60; String, String &#62; 返回 ture; Map&#60; ? extends Serializable, String &#62; 返回false;
     *
     * @param type
     * @return
     */
    public final static boolean isClassType(final Type type) {
        if (type instanceof Class) return true;
        if (type instanceof WildcardType) return false;
        if (type instanceof TypeVariable) return false;
        if (type instanceof GenericArrayType) return isClassType(((GenericArrayType) type).getGenericComponentType());
        if (!(type instanceof ParameterizedType)) return false; //只能是null了
        final ParameterizedType ptype = (ParameterizedType) type;
        if (ptype.getOwnerType() != null && !isClassType(ptype.getOwnerType())) return false;
        if (!isClassType(ptype.getRawType())) return false;
        for (Type t : ptype.getActualTypeArguments()) {
            if (!isClassType(t)) return false;
        }
        return true;
    }

    /**
     * 动态创建 ParameterizedType
     *
     * @param ownerType0
     * @param rawType0
     * @param actualTypeArguments0
     * @return
     */
    public static Type createParameterizedType(final Type ownerType0, final Type rawType0, final Type... actualTypeArguments0) {
        if (ownerType0 == null && rawType0 instanceof Class) {
            int count = 0;
            for (Type t : actualTypeArguments0) {
                if (isClassType(t)) count++;
            }
            if (count == actualTypeArguments0.length) return createParameterizedType((Class) rawType0, actualTypeArguments0);
        }
        return new ParameterizedType() {
            private final Class<?> rawType = (Class<?>) rawType0;

            private final Type ownerType = ownerType0;

            private final Type[] actualTypeArguments = actualTypeArguments0;

            @Override
            public Type[] getActualTypeArguments() {
                return actualTypeArguments.clone();
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return ownerType;
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(rawType) ^ Objects.hashCode(ownerType);
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ParameterizedType)) return false;
                final ParameterizedType that = (ParameterizedType) o;
                if (this == that) return true;
                return Objects.equals(ownerType, that.getOwnerType())
                        && Objects.equals(rawType, that.getRawType())
                        && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                if (ownerType != null) sb.append((ownerType instanceof Class) ? (((Class) ownerType).getName()) : ownerType.toString()).append(".");
                sb.append(rawType.getName());

                if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                    sb.append("<");
                    boolean first = true;
                    for (Type t : actualTypeArguments) {
                        if (!first) sb.append(", ");
                        sb.append(t);
                        first = false;
                    }
                    sb.append(">");
                }
                return sb.toString();
            }
        };
    }

    private static Type createParameterizedType(final Class rawType, final Type... actualTypeArguments) {
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
        cw.visit(V1_7, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, "java/lang/Object", null);
        String rawTypeDesc = org.objectweb.asm.Type.getDescriptor(rawType);
        StringBuilder sb = new StringBuilder();
        sb.append(rawTypeDesc.substring(0, rawTypeDesc.length() - 1)).append('<');
        for (Type c : actualTypeArguments) {
            sb.append(getClassTypeDescriptor(c));
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

    private static CharSequence getClassTypeDescriptor(Type type) {
        if (!isClassType(type)) throw new IllegalArgumentException(type + " not a class type");
        if (type instanceof Class) return org.objectweb.asm.Type.getDescriptor((Class) type);
        final ParameterizedType pt = (ParameterizedType) type;
        CharSequence rawTypeDesc = getClassTypeDescriptor(pt.getRawType());
        StringBuilder sb = new StringBuilder();
        sb.append(rawTypeDesc.subSequence(0, rawTypeDesc.length() - 1)).append('<');
        for (Type c : pt.getActualTypeArguments()) {
            sb.append(getClassTypeDescriptor(c));
        }
        sb.append(">;");
        return sb;
    }
}
