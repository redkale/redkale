/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;

/**
 * 动态生成指定方法的调用对象, 替代Method.invoke的反射方式
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @param <OBJECT_TYPE> 泛型
 * @param <RETURN_TYPE> 泛型
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface Invoker<OBJECT_TYPE, RETURN_TYPE> {

    /**
     * 调用方法放回值， 调用静态方法obj=null
     *
     * @param obj    操作对象
     * @param params 方法的参数
     *
     * @return 方法返回的结果
     */
    public RETURN_TYPE invoke(OBJECT_TYPE obj, Object... params);

    public static <OBJECT_TYPE, RETURN_TYPE> Invoker<OBJECT_TYPE, RETURN_TYPE> create(final Class<OBJECT_TYPE> clazz, final String methodName, final Class... paramTypes) {
        java.lang.reflect.Method method = null;
        try {
            method = clazz.getMethod(methodName, paramTypes);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return create(clazz, method);
    }

    public static <C, T> Invoker<C, T> create(final Class<C> clazz, final Method method) {
        RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
        RedkaleClassLoader.putReflectionMethod(clazz.getName(), method);
        boolean throwflag = Utility.contains(method.getExceptionTypes(), e -> !RuntimeException.class.isAssignableFrom(e)); //方法是否会抛出非RuntimeException异常
        boolean staticflag = Modifier.isStatic(method.getModifiers());
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
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        StringBuilder sbpts = new StringBuilder();
        for (Class c : method.getParameterTypes()) {
            sbpts.append('_').append(c.getName().replace('.', '_').replace('$', '_'));
        }
        final String newDynName = "org/redkaledyn/invoker/_Dyn" + Invoker.class.getSimpleName() + "_" + clazz.getName().replace('.', '_').replace('$', '_')
            + "_" + method.getName() + sbpts;
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            return (Invoker<C, T>) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz).getDeclaredConstructor().newInstance();
        } catch (Throwable ex) {
        }
        //-------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;
        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + interDesc + returnDesc + ">;", "java/lang/Object", new String[]{supDynName});

        {//Invoker自身的构造方法
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //invoke 方法
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke", "(" + interDesc + "[Ljava/lang/Object;)" + returnDesc, null, null));

            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            if (throwflag) {
                mv.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
                mv.visitLabel(label0);
            }
            if (!staticflag) {
                mv.visitVarInsn(ALOAD, 1);
            }

            StringBuilder paramDescs = new StringBuilder();
            int paramIndex = 0;
            for (Class paramType : method.getParameterTypes()) {
                //参数
                mv.visitVarInsn(ALOAD, 2);
                MethodDebugVisitor.pushInt(mv, paramIndex);
                mv.visitInsn(AALOAD);
                if (paramType == boolean.class) {
                    paramDescs.append("Z");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                } else if (paramType == byte.class) {
                    paramDescs.append("B");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                } else if (paramType == short.class) {
                    paramDescs.append("S");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                } else if (paramType == char.class) {
                    paramDescs.append("C");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                } else if (paramType == int.class) {
                    paramDescs.append("I");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                } else if (paramType == float.class) {
                    paramDescs.append("F");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                } else if (paramType == long.class) {
                    paramDescs.append("J");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                } else if (paramType == double.class) {
                    paramDescs.append("D");
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                } else {
                    paramDescs.append(Type.getDescriptor(paramType));
                    if (paramType != Object.class) {
                        mv.visitTypeInsn(CHECKCAST, paramType.getName().replace('.', '/'));
                    }
                }
                paramIndex++;
            }

            mv.visitMethodInsn(staticflag ? INVOKESTATIC : (clazz.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL), interName, method.getName(), "(" + paramDescs + ")" + returnPrimiveDesc, !staticflag && clazz.isInterface());
            if (returnType == boolean.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            } else if (returnType == byte.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            } else if (returnType == short.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            } else if (returnType == char.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            } else if (returnType == int.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            } else if (returnType == float.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            } else if (returnType == long.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            } else if (returnType == double.class) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            }
            mv.visitLabel(label1);
            mv.visitInsn(ARETURN);
            if (throwflag) {
                mv.visitLabel(label2);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
                mv.visitVarInsn(ASTORE, 3);
                mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
                mv.visitInsn(ATHROW);
            }
            mv.visitMaxs(3 + method.getParameterCount(), 4);
            mv.visitEnd();
        }

        { //虚拟 invoke 方法
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_VARARGS + ACC_SYNTHETIC, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, interName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "invoke", "(" + interDesc + "[Ljava/lang/Object;)" + returnDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();
        final byte[] bytes = cw.toByteArray();
        Class<?> resultClazz = null;
        try {
            if (resultClazz == null) {
                resultClazz = new ClassLoader(loader) {
                    public final Class<?> loadClass(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }.loadClass(newDynName.replace('/', '.'), bytes);
            }
            RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, resultClazz);
            RedkaleClassLoader.putReflectionDeclaredConstructors(resultClazz, newDynName.replace('/', '.'));
            return (Invoker<C, T>) resultClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
