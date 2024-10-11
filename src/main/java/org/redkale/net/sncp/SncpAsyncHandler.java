/*
 *
 */
package org.redkale.net.sncp;

import java.lang.reflect.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.util.*;

/**
 * 异步回调函数
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <V> 结果对象的泛型
 * @param <A> 附件对象的泛型
 * @since 2.8.0
 */
public interface SncpAsyncHandler<V, A> extends CompletionHandler<V, A> {

    public static SncpAsyncHandler createHandler(
            Class<? extends CompletionHandler> handlerClazz, CompletionHandler factHandler) {
        Objects.requireNonNull(handlerClazz);
        Objects.requireNonNull(factHandler);
        if (handlerClazz == CompletionHandler.class) {
            return new SncpAsyncHandler() {
                @Override
                public void completed(Object result, Object attachment) {
                    factHandler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    factHandler.failed(exc, attachment);
                }
            };
        }
        return HandlerInner.creatorMap
                .computeIfAbsent(handlerClazz, handlerClass -> {
                    // -------------------------------------------------------------
                    final boolean handlerInterface = handlerClass.isInterface();
                    final Class sncpHandlerClass = SncpAsyncHandler.class;
                    final String handlerClassName = handlerClass.getName().replace('.', '/');
                    final String sncpHandlerName = sncpHandlerClass.getName().replace('.', '/');
                    final String cpDesc = Type.getDescriptor(org.redkale.annotation.ConstructorParameters.class);
                    final String realHandlerName =
                            CompletionHandler.class.getName().replace('.', '/');
                    final String realHandlerDesc = Type.getDescriptor(CompletionHandler.class);
                    final String newDynName = "org/redkaledyn/sncp/handler/_Dyn" + sncpHandlerClass.getSimpleName()
                            + "__" + handlerClass.getName().replace('.', '/').replace('$', '_');
                    RedkaleClassLoader classLoader = RedkaleClassLoader.getRedkaleClassLoader();
                    try {
                        Class newHandlerClazz = classLoader.loadClass(newDynName.replace('/', '.'));
                        return (Creator<SncpAsyncHandler>) Creator.create(newHandlerClazz);
                    } catch (Throwable ex) {
                        // do nothing
                    }
                    // ------------------------------------------------------------------------------
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    FieldVisitor fv;
                    MethodDebugVisitor mv;
                    AnnotationVisitor av0;
                    cw.visit(
                            V11,
                            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                            newDynName,
                            null,
                            handlerInterface ? "java/lang/Object" : handlerClassName,
                            handlerInterface && handlerClass != sncpHandlerClass
                                    ? new String[] {handlerClassName, sncpHandlerName}
                                    : new String[] {sncpHandlerName});

                    { // handler 属性
                        fv = cw.visitField(ACC_PRIVATE, "factHandler", realHandlerDesc, null, null);
                        fv.visitEnd();
                    }
                    { // 构造方法
                        mv = new MethodDebugVisitor(
                                cw.visitMethod(ACC_PUBLIC, "<init>", "(" + realHandlerDesc + ")V", null, null));
                        // mv.setDebug(true);
                        {
                            av0 = mv.visitAnnotation(cpDesc, true);
                            {
                                AnnotationVisitor av1 = av0.visitArray("value");
                                av1.visit(null, "factHandler");
                                av1.visitEnd();
                            }
                            av0.visitEnd();
                        }
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitMethodInsn(
                                INVOKESPECIAL,
                                handlerInterface ? "java/lang/Object" : handlerClassName,
                                "<init>",
                                "()V",
                                false);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(PUTFIELD, newDynName, "factHandler", realHandlerDesc);
                        mv.visitInsn(RETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                    }
                    for (Method method : Sncp.loadNotImplMethods(handlerClass)) { //
                        int mod = method.getModifiers();
                        String methodDesc = Type.getMethodDescriptor(method);
                        if (Modifier.isPublic(mod)
                                && "completed".equals(method.getName())
                                && method.getParameterCount() == 2) {
                            mv = new MethodDebugVisitor(
                                    cw.visitMethod(ACC_PUBLIC, "completed", methodDesc, null, null));
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "factHandler", realHandlerDesc);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitMethodInsn(
                                    INVOKEINTERFACE,
                                    realHandlerName,
                                    "completed",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    true);
                            mv.visitInsn(RETURN);
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                            //                    if (!"(Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodDesc)) {
                            //                        mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE
                            // + ACC_SYNTHETIC, "completed", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null));
                            //                        mv.visitVarInsn(ALOAD, 0);
                            //                        mv.visitVarInsn(ALOAD, 1);
                            //                        mv.visitTypeInsn(CHECKCAST, "java/lang/Object");
                            //                        mv.visitVarInsn(ALOAD, 2);
                            //                        mv.visitTypeInsn(CHECKCAST, "java/lang/Object");
                            //                        mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "completed",
                            // methodDesc, false);
                            //                        mv.visitInsn(RETURN);
                            //                        mv.visitMaxs(3, 3);
                            //                        mv.visitEnd();
                            //                    }
                        } else if (Modifier.isPublic(mod)
                                && "failed".equals(method.getName())
                                && method.getParameterCount() == 2) {
                            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "failed", methodDesc, null, null));
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "factHandler", realHandlerDesc);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitMethodInsn(
                                    INVOKEINTERFACE,
                                    realHandlerName,
                                    "failed",
                                    "(Ljava/lang/Throwable;Ljava/lang/Object;)V",
                                    true);
                            mv.visitInsn(RETURN);
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                            //                    if (!"(Ljava/lang/Throwable;Ljava/lang/Object;)V".equals(methodDesc))
                            // {
                            //                        mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE
                            // + ACC_SYNTHETIC, "failed", "(Ljava/lang/Throwable;Ljava/lang/Object;)V", null, null));
                            //                        mv.visitVarInsn(ALOAD, 0);
                            //                        mv.visitVarInsn(ALOAD, 1);
                            //                        mv.visitVarInsn(ALOAD, 2);
                            //                        mv.visitTypeInsn(CHECKCAST, "java/lang/Object");
                            //                        mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "failed",
                            // methodDesc, false);
                            //                        mv.visitInsn(RETURN);
                            //                        mv.visitMaxs(3, 3);
                            //                        mv.visitEnd();
                            //                    }
                        } else if (handlerInterface || Modifier.isAbstract(mod)) {
                            mv = new MethodDebugVisitor(cw.visitMethod(
                                    ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
                            Class returnType = method.getReturnType();
                            if (returnType == void.class) {
                                mv.visitInsn(RETURN);
                                mv.visitMaxs(0, 1);
                            } else if (returnType.isPrimitive()) {
                                mv.visitInsn(ICONST_0);
                                if (returnType == long.class) {
                                    mv.visitInsn(LRETURN);
                                    mv.visitMaxs(2, 1);
                                } else if (returnType == float.class) {
                                    mv.visitInsn(FRETURN);
                                    mv.visitMaxs(2, 1);
                                } else if (returnType == double.class) {
                                    mv.visitInsn(DRETURN);
                                    mv.visitMaxs(2, 1);
                                } else {
                                    mv.visitInsn(IRETURN);
                                    mv.visitMaxs(1, 1);
                                }
                            } else {
                                mv.visitInsn(ACONST_NULL);
                                mv.visitInsn(ARETURN);
                                mv.visitMaxs(1, 1);
                            }
                            mv.visitEnd();
                        }
                    }
                    cw.visitEnd();
                    byte[] bytes = cw.toByteArray();
                    Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
                    return (Creator<SncpAsyncHandler>) Creator.create(newClazz);
                })
                .create(factHandler);
    }

    static class HandlerInner {

        static final Map<Class, Creator<SncpAsyncHandler>> creatorMap = new ConcurrentHashMap<>();

        private HandlerInner() {
            // do nothing
        }
    }
}
