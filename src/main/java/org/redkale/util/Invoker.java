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
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.concurrent.ConcurrentHashMap;

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
        final ClassDesc targetDesc = ClassDesc.ofDescriptor(clazz.descriptorString());
        final ClassDesc returnDesc =
                ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(returnType).descriptorString());
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        StringBuilder sbpts = new StringBuilder();
        for (Class c : method.getParameterTypes()) {
            sbpts.append('_')
                    .append(c.getName()
                            .replace('.', '_')
                            .replace('$', '_')
                            .replace('[', '_')
                            .replace(';', '_'));
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
        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final MethodTypeDesc invokeDesc = MethodTypeDesc.of(returnDesc, targetDesc, CD_Object.arrayType());
        final MethodTypeDesc methodDesc = MethodTypeDesc.ofDescriptor(
                MethodType.methodType(returnType, method.getParameterTypes()).descriptorString());
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(CD_Object)
                    .withInterfaceSymbols(ClassDesc.ofInternalName(supDynName));
            cb.with(SignatureAttribute.of(ClassSignature.parseFrom("Ljava/lang/Object;L" + supDynName + "<"
                    + targetDesc.descriptorString() + returnDesc.descriptorString() + ">;")));
            cb.withMethodBody(
                    "<init>",
                    MethodTypeDesc.of(CD_void),
                    ACC_PUBLIC,
                    code -> code.aload(0)
                            .invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void))
                            .return_());
            cb.withMethodBody("invoke", invokeDesc, ACC_PUBLIC | ACC_VARARGS, code -> {
                Label start = code.newLabel();
                Label end = code.newLabel();
                Label handler = code.newLabel();
                if (throwFlag) {
                    code.exceptionCatch(start, end, handler, CD_Throwable);
                    code.labelBinding(start);
                }
                if (!staticFlag) {
                    code.aload(1);
                }
                Class<?>[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    code.aload(2).loadConstant(i).aaload();
                    Class<?> paramType = paramTypes[i];
                    ClassDesc paramDesc = ClassDesc.ofDescriptor(paramType.descriptorString());
                    if (paramType.isPrimitive()) {
                        ClassDesc wrapperDesc = ClassDesc.ofDescriptor(
                                TypeToken.primitiveToWrapper(paramType).descriptorString());
                        code.checkcast(wrapperDesc)
                                .invokevirtual(
                                        wrapperDesc, paramType.getSimpleName() + "Value", MethodTypeDesc.of(paramDesc));
                    } else {
                        code.checkcast(paramDesc);
                    }
                }
                if (staticFlag) {
                    code.invokestatic(targetDesc, method.getName(), methodDesc, clazz.isInterface());
                } else if (clazz.isInterface()) {
                    code.invokeinterface(targetDesc, method.getName(), methodDesc);
                } else {
                    code.invokevirtual(targetDesc, method.getName(), methodDesc);
                }
                if (returnType == void.class) {
                    code.aconst_null();
                } else if (returnType.isPrimitive()) {
                    code.invokestatic(returnDesc, "valueOf", MethodTypeDesc.of(returnDesc, methodDesc.returnType()));
                }
                if (throwFlag) {
                    code.labelBinding(end);
                }
                code.areturn();
                if (throwFlag) {
                    ClassDesc exceptionDesc = ClassDesc.of("java.lang.RuntimeException");
                    code.labelBinding(handler)
                            .astore(3)
                            .new_(exceptionDesc)
                            .dup()
                            .aload(3)
                            .invokespecial(exceptionDesc, "<init>", MethodTypeDesc.of(CD_void, CD_Throwable))
                            .athrow();
                }
            });
            MethodTypeDesc bridgeDesc = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object.arrayType());
            if (!invokeDesc.equals(bridgeDesc)) {
                cb.withMethodBody(
                        "invoke",
                        bridgeDesc,
                        ACC_PUBLIC | ACC_BRIDGE | ACC_VARARGS | ACC_SYNTHETIC,
                        code -> code.aload(0)
                                .aload(1)
                                .checkcast(targetDesc)
                                .aload(2)
                                .invokevirtual(dynDesc, "invoke", invokeDesc)
                                .areturn());
            }
        });
        try {
            Class<?> resultClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
            RedkaleClassLoader.putReflectionDeclaredConstructors(resultClazz, newDynName.replace('/', '.'));
            return (Invoker<C, T>) resultClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }
}
