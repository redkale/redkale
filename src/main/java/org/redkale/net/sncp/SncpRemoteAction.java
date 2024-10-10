/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.net.sncp;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.AsmMethodParam;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.MethodDebugVisitor;
import static org.redkale.asm.Opcodes.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.ObjectEncoder;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.service.RpcAttachment;
import org.redkale.service.RpcSourceAddress;
import org.redkale.service.RpcTargetAddress;
import org.redkale.service.RpcTargetTopic;
import org.redkale.util.Creator;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.TypeToken;
import org.redkale.util.Uint128;

/**
 * 每个Service方法的相关信息对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class SncpRemoteAction {

    protected final Uint128 actionid;

    protected final Method method;

    protected final Type returnObjectType; // void必须设为null

    protected final Type[] paramTypes;

    protected final Class[] paramClasses;

    // 参数数量为0: 值为null; 参数数量为1且参数类型为JavaBean: 值为第一个参数的类型; 其他情况: 动态生成的Object类
    protected final Type paramComposeBeanType;

    // 只有paramComposeBeanType为动态生成的组合类时才有值;
    protected final Creator paramComposeBeanCreator;

    protected final int paramHandlerIndex;

    protected final int paramHandlerAttachIndex;

    protected final int paramAddressTargetIndex;

    protected final int paramAddressSourceIndex;

    protected final int paramTopicTargetIndex;

    protected final Class<? extends CompletionHandler> paramHandlerClass; // CompletionHandler参数的类型

    protected final java.lang.reflect.Type paramHandlerType; // CompletionHandler.completed第一个参数的类型

    protected final java.lang.reflect.Type returnFutureType; // 返回结果的CompletableFuture的结果泛型类型

    protected final Class<? extends Future> returnFutureClass; // 返回结果的CompletableFuture类型

    protected final Creator<? extends CompletableFuture> returnFutureCreator; // 返回CompletableFuture类型的构建器

    protected final SncpHeader header;

    @SuppressWarnings("unchecked")
    SncpRemoteAction(
            final Class serviceImplClass,
            Class resourceType,
            Method method,
            Uint128 serviceid,
            Uint128 actionid,
            final SncpClient sncpClient) {
        this.actionid = actionid == null ? Sncp.actionid(method) : actionid;
        Type rt = TypeToken.getGenericType(method.getGenericReturnType(), serviceImplClass);
        this.returnObjectType = rt == void.class || rt == Void.class ? null : rt;
        this.paramTypes = TypeToken.getGenericType(method.getGenericParameterTypes(), serviceImplClass);
        this.paramClasses = method.getParameterTypes();
        Type pt = createParamComposeBeanType(serviceImplClass, method, actionid, paramTypes, paramClasses);
        this.paramComposeBeanType = pt;
        this.paramComposeBeanCreator =
                (pt == null || pt == paramTypes[0]) ? null : Creator.create(TypeToken.typeToClass(pt), 1);
        this.method = method;
        Annotation[][] anns = method.getParameterAnnotations();
        int topicAddrIndex = -1;
        int targetAddrIndex = -1;
        int sourceAddrIndex = -1;
        int handlerAttachIndex = -1;
        int handlerFuncIndex = -1;
        Class handlerFuncClass = null;
        java.lang.reflect.Type handlerResultType = null;
        Class<?>[] params = method.getParameterTypes();
        Type[] genericParams = method.getGenericParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (CompletionHandler.class.isAssignableFrom(params[i])) {
                if (Future.class.isAssignableFrom(method.getReturnType())) {
                    throw new SncpException(method + " have both CompletionHandler and CompletableFuture");
                }
                if (handlerFuncIndex >= 0) {
                    throw new SncpException(method + " have more than one CompletionHandler type parameter");
                }
                Sncp.checkAsyncModifier(params[i], method);
                handlerFuncIndex = i;
                handlerFuncClass = paramClasses[i];
                java.lang.reflect.Type handlerType = TypeToken.getGenericType(genericParams[i], serviceImplClass);
                if (handlerType instanceof Class) {
                    handlerResultType = Object.class;
                } else if (handlerType instanceof ParameterizedType) {
                    handlerResultType = TypeToken.getGenericType(
                            ((ParameterizedType) handlerType).getActualTypeArguments()[0], handlerType);
                } else {
                    throw new SncpException(serviceImplClass + " had unknown genericType in " + method);
                }
                if (method.getReturnType() != void.class) {
                    throw new SncpException(
                            method + " have CompletionHandler type parameter but return type is not void");
                }
                break;
            }
        }
        if (anns.length > 0) {
            for (int i = 0; i < anns.length; i++) {
                if (anns[i].length > 0) {
                    for (Annotation ann : anns[i]) {
                        if (ann.annotationType() == RpcAttachment.class) {
                            if (handlerAttachIndex >= 0) {
                                throw new SncpException(method + " have more than one @RpcAttachment parameter");
                            }
                            handlerAttachIndex = i;
                        } else if (ann.annotationType() == RpcTargetAddress.class) {
                            if (SocketAddress.class.isAssignableFrom(params[i])) {
                                if (sourceAddrIndex >= 0) {
                                    throw new SncpException(method + " have more than one @RpcTargetAddress parameter");
                                } else {
                                    targetAddrIndex = i;
                                }
                            } else {
                                throw new SncpException(
                                        method + " must be SocketAddress Type on @RpcTargetAddress parameter");
                            }
                        } else if (ann.annotationType() == RpcSourceAddress.class) {
                            if (SocketAddress.class.isAssignableFrom(params[i])) {
                                if (sourceAddrIndex >= 0) {
                                    throw new SncpException(method + " have more than one @RpcSourceAddress parameter");
                                } else {
                                    sourceAddrIndex = i;
                                }
                            } else {
                                throw new SncpException(
                                        method + " must be SocketAddress Type on @RpcSourceAddress parameter");
                            }
                        } else if (ann.annotationType() == RpcTargetTopic.class) {
                            if (String.class.isAssignableFrom(params[i])) {
                                if (sourceAddrIndex >= 0) {
                                    throw new SncpException(method + " have more than one @RpcTargetTopic parameter");
                                } else {
                                    topicAddrIndex = i;
                                }
                            } else {
                                throw new SncpException(method + " must be String Type on @RpcTargetTopic parameter");
                            }
                        }
                    }
                }
            }
        }
        this.paramTopicTargetIndex = topicAddrIndex;
        this.paramAddressTargetIndex = targetAddrIndex;
        this.paramAddressSourceIndex = sourceAddrIndex;
        this.paramHandlerIndex = handlerFuncIndex;
        this.paramHandlerClass = handlerFuncClass;
        this.paramHandlerType = handlerResultType;
        this.paramHandlerAttachIndex = handlerAttachIndex;
        this.header = SncpHeader.create(
                sncpClient == null ? null : sncpClient.getClientSncpAddress(),
                serviceid,
                resourceType.getName(),
                actionid,
                method.getName());
        if (this.paramHandlerIndex >= 0 && method.getReturnType() != void.class) {
            throw new SncpException(method + " have CompletionHandler type parameter but return type is not void");
        }
        if (Future.class.isAssignableFrom(method.getReturnType())) {
            java.lang.reflect.Type futureType =
                    TypeToken.getGenericType(method.getGenericReturnType(), serviceImplClass);
            java.lang.reflect.Type returnType = null;
            if (futureType instanceof Class) {
                returnType = Object.class;
            } else if (futureType instanceof ParameterizedType) {
                returnType = TypeToken.getGenericType(
                        ((ParameterizedType) futureType).getActualTypeArguments()[0], futureType);
            } else {
                throw new SncpException(serviceImplClass + " had unknown return genericType in " + method);
            }
            this.returnFutureType = returnType;
            this.returnFutureClass = method.getReturnType().isAssignableFrom(CompletableFuture.class)
                    ? CompletableFuture.class
                    : (Class) method.getReturnType();
            if (method.getReturnType().isAssignableFrom(CompletableFuture.class)
                    || CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                this.returnFutureCreator = (Creator) Creator.create(this.returnFutureClass);
            } else {
                throw new SncpException(serviceImplClass + " return must be CompletableFuture or subclass");
            }
        } else {
            this.returnFutureType = null;
            this.returnFutureClass = null;
            this.returnFutureCreator = null;
        }
    }

    public String actionName() {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    @Override
    public String toString() {
        return "{" + actionid + "," + (method == null ? "null" : method.getName()) + "}";
    }

    public static Type createParamComposeBeanType(
            Class resourceType, Method method, Uint128 actionid, Type[] paramTypes, Class[] paramClasses) {
        if (paramTypes == null || paramTypes.length == 0) {
            return null;
        }
        if (paramTypes.length == 1 && ProtobufFactory.root().createEncoder(paramTypes[0]) instanceof ObjectEncoder) {
            return paramTypes[0];
        }

        // 动态生成组合JavaBean类
        final Class serviceClass = resourceType.getClass();
        final String columnDesc = org.redkale.asm.Type.getDescriptor(ConvertColumn.class);
        final String newDynName = "org/redkaledyn/sncp/servlet/action/_DynSncpActionParamBean_"
                + resourceType.getSimpleName() + "_" + method.getName() + "_" + actionid;
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class<?> newClazz = clz == null
                    ? Thread.currentThread().getContextClassLoader().loadClass(newDynName.replace('/', '.'))
                    : clz;
            return newClazz;
        } catch (Throwable ex) {
            // do nothing
        }

        Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(resourceType);
        AsmMethodBean methodBean = Objects.requireNonNull(methodBeans.get(AsmMethodBoost.getMethodBeanKey(method)));
        // -------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av;

        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, "java/lang/Object", null);
        final List<AsmMethodParam> asmParams = methodBean.getParams();
        for (int i = 1; i <= paramClasses.length; i++) {
            AsmMethodParam param = asmParams.get(i - 1);
            String paramDesc = org.redkale.asm.Type.getDescriptor(paramClasses[i - 1]);
            fv = cw.visitField(ACC_PUBLIC, "arg" + i, paramDesc, param.getSignature(), null);
            av = fv.visitAnnotation(columnDesc, true);
            av.visit("index", i);
            av.visitEnd();
            fv.visitEnd();
        }
        { // 空参数的构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // 一个参数的构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "([Ljava/lang/Object;)V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            for (int i = 1; i <= paramClasses.length; i++) {
                String paramDesc = org.redkale.asm.Type.getDescriptor(paramClasses[i - 1]);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                Asms.visitInsn(mv, i - 1);
                mv.visitInsn(AALOAD);
                Asms.visitCheckCast(mv, paramClasses[i - 1]);
                mv.visitFieldInsn(PUTFIELD, newDynName, "arg" + i, paramDesc);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class newClazz = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        Creator.load(newClazz);
        ProtobufFactory.root().loadDecoder(newClazz);
        ProtobufFactory.root().loadEncoder(newClazz);
        return newClazz;
    }
}
