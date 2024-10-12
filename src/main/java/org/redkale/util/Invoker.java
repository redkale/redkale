/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;

/**
 * 动态生成指定public方法的调用对象, 替代Method.invoke的反射方式
 *
 * <p>详情见: https://redkale.org
 *
 * @param <C> 泛型
 * @param <R> 泛型
 * @author zhangjx
 * @since 2.5.0
 */
public interface Invoker<C, R> {

    /**
     * 调用方法放回值， 调用静态方法obj=null
     *
     * @param obj 操作对象
     * @param params 方法的参数
     * @return 方法返回的结果
     */
    public R invoke(C obj, Object... params);

    public static <C, T> Invoker<C, T> load(final Class<C> clazz, final String methodName, final Class... paramTypes) {
        java.lang.reflect.Method method = null;
        try {
            method = clazz.getMethod(methodName, paramTypes);
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
        return load(clazz, method);
    }

    public static <C, T> Invoker<C, T> create(
            final Class<C> clazz, final String methodName, final Class... paramTypes) {
        java.lang.reflect.Method method = null;
        try {
            method = clazz.getMethod(methodName, paramTypes);
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
        return create(clazz, method);
    }

    public static <C, T> Invoker<C, T> load(final Class<C> clazz, final Method method) {
        return Inners.InvokerInner.invokerCaches
                .computeIfAbsent(clazz, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(method, v -> create(clazz, method));
    }

    public static <C, T> Invoker<C, T> create(final Class<C> clazz, final Method method) {
        RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
        RedkaleClassLoader.putReflectionMethod(clazz.getName(), method);
        boolean throwFlag = Utility.contains(
                method.getExceptionTypes(),
                e -> !RuntimeException.class.isAssignableFrom(e)); // 方法是否会抛出非RuntimeException异常
        boolean staticFlag = Modifier.isStatic(method.getModifiers());
        final Class<T> returnType = (Class<T>) method.getReturnType();
        final String supDynName = Invoker.class.getName().replace('.', '/');
        final String interName = clazz.getName().replace('.', '/');
        final String interDesc = Type.getDescriptor(clazz);
        final String returnPrimiveDesc = Type.getDescriptor(returnType);
        String returnDesc = Type.getDescriptor(returnType);
        if (returnType == boolean.class) {
            returnDesc = Type.getDescriptor(Boolean.class);
        } else if (returnType == byte.class) {
            returnDesc = Type.getDescriptor(Byte.class);
        } else if (returnType == short.class) {
            returnDesc = Type.getDescriptor(Short.class);
        } else if (returnType == char.class) {
            returnDesc = Type.getDescriptor(Character.class);
        } else if (returnType == int.class) {
            returnDesc = Type.getDescriptor(Integer.class);
        } else if (returnType == float.class) {
            returnDesc = Type.getDescriptor(Float.class);
        } else if (returnType == long.class) {
            returnDesc = Type.getDescriptor(Long.class);
        } else if (returnType == double.class) {
            returnDesc = Type.getDescriptor(Double.class);
        } else if (returnType == void.class) {
            returnDesc = Type.getDescriptor(Void.class);
        }
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        StringBuilder sbpts = new StringBuilder();
        for (Class c : method.getParameterTypes()) {
            sbpts.append('_').append(c.getName().replace('.', '_').replace('$', '_'));
        }
        final String newDynName = "org/redkaledyn/invoker/_Dyn" + Invoker.class.getSimpleName() + "_"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + method.getName() + sbpts;
        try {
            return (Invoker<C, T>) classLoader
                    .loadClass(newDynName.replace('/', '.'))
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable ex) {
            // do nothing
        }
        // -------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "Ljava/lang/Object;L" + supDynName + "<" + interDesc + returnDesc + ">;",
                "java/lang/Object",
                new String[] {supDynName});

        { // Invoker自身的构造方法
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // invoke 方法
            mv = new MethodDebugVisitor(cw.visitMethod(
                    ACC_PUBLIC + ACC_VARARGS,
                    "invoke",
                    "(" + interDesc + "[Ljava/lang/Object;)" + returnDesc,
                    null,
                    null));

            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            if (throwFlag) {
                mv.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
                mv.visitLabel(label0);
            }
            if (!staticFlag) {
                mv.visitVarInsn(ALOAD, 1);
            }

            StringBuilder paramDescs = new StringBuilder();
            int paramIndex = 0;
            for (Class paramType : method.getParameterTypes()) {
                // 参数
                mv.visitVarInsn(ALOAD, 2);
                Asms.visitInsn(mv, paramIndex);
                mv.visitInsn(AALOAD);
                Asms.visitCheckCast(mv, paramType);
                paramDescs.append(Type.getDescriptor(paramType));
                paramIndex++;
            }

            mv.visitMethodInsn(
                    staticFlag ? INVOKESTATIC : (clazz.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL),
                    interName,
                    method.getName(),
                    "(" + paramDescs + ")" + returnPrimiveDesc,
                    !staticFlag && clazz.isInterface());
            if (returnType == void.class) {
                mv.visitInsn(ACONST_NULL);
            } else {
                Asms.visitPrimitiveValueOf(mv, returnType);
            }
            mv.visitLabel(label1);
            mv.visitInsn(ARETURN);
            if (throwFlag) {
                mv.visitLabel(label2);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
                mv.visitVarInsn(ASTORE, 3);
                mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(
                        INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
                mv.visitInsn(ATHROW);
            }
            mv.visitMaxs(3 + method.getParameterCount(), 4);
            mv.visitEnd();
        }

        { // 虚拟 invoke 方法
            mv = new MethodDebugVisitor(cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_VARARGS + ACC_SYNTHETIC,
                    "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                    null,
                    null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, interName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, newDynName, "invoke", "(" + interDesc + "[Ljava/lang/Object;)" + returnDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        try {
            Class<?> resultClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
            RedkaleClassLoader.putReflectionDeclaredConstructors(resultClazz, newDynName.replace('/', '.'));
            return (Invoker<C, T>) resultClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }
}
