/*
 *
 */
package org.redkale.net.sncp;

import static java.lang.classfile.ClassFile.*;

import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.bytecode.ByteCodes;
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
                    final String cpDesc = ByteCodes.descriptor(org.redkale.annotation.ConstructorParameters.class);
                    final String realHandlerName =
                            CompletionHandler.class.getName().replace('.', '/');
                    final String realHandlerDesc = ByteCodes.descriptor(CompletionHandler.class);
                    final String newDynName = "org/redkaledyn/sncp/handler/_Dyn" + sncpHandlerClass.getSimpleName()
                            + "__" + handlerClass.getName().replace('.', '/').replace('$', '_');
                    RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
                    try {
                        Class newHandlerClazz = classLoader.loadClass(newDynName.replace('/', '.'));
                        return (Creator<SncpAsyncHandler>) Creator.create(newHandlerClazz);
                    } catch (Throwable ex) {
                        // do nothing
                    }
                    // ------------------------------------------------------------------------------

                    byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
                        cw.withVersion(JAVA_11_VERSION, 0)
                                .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                                .withSuperclass(
                                        ByteCodes.classDesc(handlerInterface ? "java/lang/Object" : handlerClassName));

                        cw.withInterfaceSymbols(Arrays.stream(
                                        handlerInterface && handlerClass != sncpHandlerClass
                                                ? new String[] {handlerClassName, sncpHandlerName}
                                                : new String[] {sncpHandlerName})
                                .map(ByteCodes::classDesc)
                                .toList());

                        { // handler 属性
                            cw.withField("factHandler", ClassDesc.ofDescriptor(realHandlerDesc), ACC_PRIVATE);
                        }
                        { // 构造方法
                            cw.withMethod(
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("(" + realHandlerDesc + ")V"),
                                    ACC_PUBLIC,
                                    mb -> {
                                        List<java.lang.classfile.Annotation> mvAnnotations = new ArrayList<>();
                                        mb.withCode(mv -> {
                                            {
                                                {
                                                    List<AnnotationElement> av0 = new ArrayList<>();
                                                    {
                                                        {
                                                            List<AnnotationValue> av1 = new ArrayList<>();
                                                            av1.add(ByteCodes.annotationValue("factHandler"));
                                                            av0.add(AnnotationElement.of(
                                                                    "value", AnnotationValue.ofArray(av1)));
                                                        }
                                                    }
                                                    mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                            ClassDesc.ofDescriptor(cpDesc), av0));
                                                }
                                            }
                                            mv.aload(0);
                                            mv.invokespecial(
                                                    ByteCodes.classDesc(
                                                            handlerInterface ? "java/lang/Object" : handlerClassName),
                                                    "<init>",
                                                    MethodTypeDesc.ofDescriptor("()V"),
                                                    false);
                                            mv.aload(0);
                                            mv.aload(1);
                                            mv.putfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    "factHandler",
                                                    ClassDesc.ofDescriptor(realHandlerDesc));
                                            mv.return_();
                                        });
                                        if (!mvAnnotations.isEmpty())
                                            mb.with(RuntimeVisibleAnnotationsAttribute.of(mvAnnotations));
                                    });
                        }
                        for (Method method : Sncp.loadNotImplMethods(handlerClass)) { //
                            int mod = method.getModifiers();
                            String methodDesc = ByteCodes.methodDescriptor(method);
                            if (Modifier.isPublic(mod)
                                    && "completed".equals(method.getName())
                                    && method.getParameterCount() == 2) {
                                cw.withMethodBody(
                                        "completed", MethodTypeDesc.ofDescriptor(methodDesc), ACC_PUBLIC, mv -> {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    "factHandler",
                                                    ClassDesc.ofDescriptor(realHandlerDesc));
                                            mv.aload(1);
                                            mv.aload(2);
                                            mv.invokeinterface(
                                                    ByteCodes.classDesc(realHandlerName),
                                                    "completed",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                                            mv.return_();
                                        });

                            } else if (Modifier.isPublic(mod)
                                    && "failed".equals(method.getName())
                                    && method.getParameterCount() == 2) {
                                cw.withMethodBody("failed", MethodTypeDesc.ofDescriptor(methodDesc), ACC_PUBLIC, mv -> {
                                    mv.aload(0);
                                    mv.getfield(
                                            ByteCodes.classDesc(newDynName),
                                            "factHandler",
                                            ClassDesc.ofDescriptor(realHandlerDesc));
                                    mv.aload(1);
                                    mv.aload(2);
                                    mv.invokeinterface(
                                            ByteCodes.classDesc(realHandlerName),
                                            "failed",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Throwable;Ljava/lang/Object;)V"));
                                    mv.return_();
                                });

                            } else if (handlerInterface || Modifier.isAbstract(mod)) {
                                cw.withMethodBody(
                                        method.getName(),
                                        MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method)),
                                        ACC_PUBLIC,
                                        mv -> {
                                            Class returnType = method.getReturnType();
                                            if (returnType == void.class) {
                                                mv.return_();

                                            } else if (returnType.isPrimitive()) {
                                                if (returnType == long.class) {
                                                    mv.lconst_0().lreturn();

                                                } else if (returnType == float.class) {
                                                    mv.fconst_0().freturn();

                                                } else if (returnType == double.class) {
                                                    mv.dconst_0().dreturn();

                                                } else {
                                                    mv.iconst_0().ireturn();
                                                }
                                            } else {
                                                mv.aconst_null();
                                                mv.areturn();
                                            }
                                        });
                            }
                        }
                    });
                    byte[] bytes = classBytes;
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
