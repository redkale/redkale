/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.net.sncp;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.NonBlocking;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.Label;
import org.redkale.asm.MethodDebugVisitor;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.Convert;
import org.redkale.convert.Reader;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.service.Service;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.TypeToken;
import org.redkale.util.Uint128;

/**
 * 每个Service方法的SncpServlet对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class SncpActionServlet extends SncpServlet {

    protected final Method method;

    protected final Uint128 actionid;

    protected final boolean nonBlocking;

    @ClassDepends
    protected final java.lang.reflect.Type[] paramTypes; // 第一个元素存放返回类型return type， void的返回参数类型为null, 数组长度为:1+参数个数

    @ClassDepends
    protected final java.lang.reflect.Type paramComposeBeanType;

    protected final int paramHandlerIndex; // >=0表示存在CompletionHandler参数

    protected final Class<? extends CompletionHandler> paramHandlerClass; // CompletionHandler参数的类型

    protected final java.lang.reflect.Type paramHandlerType; // CompletionHandler.completed第一个参数的类型

    @ClassDepends
    protected final java.lang.reflect.Type returnObjectType; // 返回结果类型 void必须设为null

    @ClassDepends
    protected final java.lang.reflect.Type returnFutureType; // 返回结果的CompletableFuture的结果泛型类型

    @ClassDepends
    protected SncpActionServlet(
            String resourceName,
            Class resourceType,
            Service service,
            Uint128 serviceid,
            Uint128 actionid,
            final Method method) {
        super(resourceName, resourceType, service, serviceid);
        Objects.requireNonNull(method);
        this.actionid = actionid;
        this.method = method;
        this.paramComposeBeanType = SncpRemoteAction.createParamComposeBeanType(
                RedkaleClassLoader.getRedkaleClassLoader(),
                Sncp.getServiceType(service),
                method,
                actionid,
                method.getGenericParameterTypes(),
                method.getParameterTypes());

        int handlerFuncIndex = -1;
        Class handlerFuncClass = null;
        java.lang.reflect.Type handlerResultType = null;
        try {
            final Class[] paramClasses = method.getParameterTypes();
            java.lang.reflect.Type[] genericParams = method.getGenericParameterTypes();
            for (int i = 0; i < paramClasses.length; i++) { // 反序列化方法的每个参数
                if (CompletionHandler.class.isAssignableFrom(paramClasses[i])) {
                    handlerFuncIndex = i;
                    handlerFuncClass = paramClasses[i];
                    java.lang.reflect.Type handlerType = TypeToken.getGenericType(genericParams[i], service.getClass());
                    if (handlerType instanceof Class) {
                        handlerResultType = Object.class;
                    } else if (handlerType instanceof ParameterizedType) {
                        handlerResultType = TypeToken.getGenericType(
                                ((ParameterizedType) handlerType).getActualTypeArguments()[0], handlerType);
                    } else {
                        throw new SncpException(service.getClass() + " had unknown genericType in " + method);
                    }
                    if (method.getReturnType() != void.class) {
                        throw new SncpException(
                                method + " have CompletionHandler type parameter but return type is not void");
                    }
                    break;
                }
            }
        } catch (Throwable ex) {
            // do nothing
        }
        java.lang.reflect.Type[] originalParamTypes =
                TypeToken.getGenericType(method.getGenericParameterTypes(), service.getClass());
        java.lang.reflect.Type originalReturnType =
                TypeToken.getGenericType(method.getGenericReturnType(), service.getClass());
        java.lang.reflect.Type[] types = new java.lang.reflect.Type[originalParamTypes.length + 1];
        types[0] = originalReturnType;
        System.arraycopy(originalParamTypes, 0, types, 1, originalParamTypes.length);
        this.paramTypes = types;
        this.paramHandlerIndex = handlerFuncIndex;
        this.paramHandlerClass = handlerFuncClass;
        this.paramHandlerType = handlerResultType;
        this.returnObjectType =
                originalReturnType == void.class || originalReturnType == Void.class ? null : originalReturnType;
        if (Future.class.isAssignableFrom(method.getReturnType())) {
            java.lang.reflect.Type futureType =
                    TypeToken.getGenericType(method.getGenericReturnType(), service.getClass());
            java.lang.reflect.Type returnType = null;
            if (futureType instanceof Class) {
                returnType = Object.class;
            } else if (futureType instanceof ParameterizedType) {
                returnType = TypeToken.getGenericType(
                        ((ParameterizedType) futureType).getActualTypeArguments()[0], futureType);
            } else {
                throw new SncpException(service.getClass() + " had unknown return genericType in " + method);
            }
            this.returnFutureType = returnType;
        } else {
            this.returnFutureType = null;
        }
        NonBlocking non = method.getAnnotation(NonBlocking.class);
        if (non == null) {
            non = service.getClass().getAnnotation(NonBlocking.class);
        }
        // Future代替CompletionStage 不容易判断异步
        this.nonBlocking = non == null
                && (CompletionStage.class.isAssignableFrom(method.getReturnType()) || this.paramHandlerIndex >= 0);
    }

    @Override
    public final void execute(SncpRequest request, SncpResponse response) throws IOException {
        if (paramHandlerIndex > 0) {
            response.paramAsyncHandler(paramHandlerClass, paramHandlerType);
        }
        try {
            action(request, response);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t);
        }
    }

    protected abstract void action(SncpRequest request, SncpResponse response) throws Throwable;

    public <T extends Service> T service() {
        return (T) service;
    }

    @Override
    public Uint128 getServiceid() {
        return serviceid;
    }

    public Uint128 getActionid() {
        return actionid;
    }

    public String actionName() {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     *
     *
     * <blockquote>
     *
     * <pre>
     * public interface TestService extends Service {
     *
     *     public boolean change(TestBean bean, String name, int id);
     *
     *     public void insert(BooleanHandler handler, TestBean bean, String name, int id);
     *
     *     public void update(long show, short v2, CompletionHandler&#60;Boolean, TestBean&#62; handler, TestBean bean, String name, int id);
     *
     *     public CompletableFuture&#60;String&#62; changeName(TestBean bean, String name, int id);
     *
     * }
     *
     * &#064;ResourceType(TestService.class)
     * public class TestServiceImpl implements TestService {
     *
     *     &#064;Override
     *     public boolean change(TestBean bean, String name, int id) {
     *         return false;
     *     }
     *
     *     &#064;Override
     *     public void insert(BooleanHandler handler, TestBean bean, String name, int id) {
     *     }
     *
     *     &#064;Override
     *     public void update(long show, short v2, CompletionHandler&#60;Boolean, TestBean&#62; handler, TestBean bean, String name, int id) {
     *     }
     *
     *     &#064;Override
     *     public CompletableFuture&#60;String&#62; changeName(TestBean bean, String name, int id) {
     *         return null;
     *     }
     * }
     *
     * public class BooleanHandler implements CompletionHandler&#60;Boolean, TestBean&#62; {
     *
     *     &#064;Override
     *     public void completed(Boolean result, TestBean attachment) {
     *     }
     *
     *     &#064;Override
     *     public void failed(Throwable exc, TestBean attachment) {
     *     }
     *
     * }
     *
     * public class DynActionTestService_change extends SncpActionServlet {
     *
     *     public DynActionTestService_change(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
     *         super(resourceName, resourceType, service, serviceid, actionid, method);
     *     }
     *
     *     &#064;Override
     *     public void action(SncpRequest request, SncpResponse response) throws Throwable {
     *         Convert&#60;Reader, Writer&#62; convert = request.getConvert();
     *         Reader in = request.getReader();
     *         TestBean arg1 = convert.convertFrom(paramTypes[1], in);
     *         String arg2 = convert.convertFrom(paramTypes[2], in);
     *         int arg3 = convert.convertFrom(paramTypes[3], in);
     *         TestService serviceObj = (TestService) service();
     *         Object rs = serviceObj.change(arg1, arg2, arg3);
     *         response.finish(boolean.class, rs);
     *     }
     * }
     *
     * public class DynActionTestService_insert extends SncpActionServlet {
     *
     *     public DynActionTestService_insert(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
     *         super(resourceName, resourceType, service, serviceid, actionid, method);
     *     }
     *
     *     &#064;Override
     *     public void action(SncpRequest request, SncpResponse response) throws Throwable {
     *         Convert&#60;Reader, Writer&#62; convert = request.getConvert();
     *         Reader in = request.getReader();
     *         BooleanHandler arg0 = response.getParamAsyncHandler();
     *         convert.convertFrom(CompletionHandler.class, in);
     *         TestBean arg1 = convert.convertFrom(paramTypes[2], in);
     *         String arg2 = convert.convertFrom(paramTypes[3], in);
     *         int arg3 = convert.convertFrom(paramTypes[4], in);
     *         TestService serviceObj = (TestService) service();
     *         serviceObj.insert(arg0, arg1, arg2, arg3);
     *         response.finishVoid();
     *     }
     * }
     *
     * public class DynActionTestService_update extends SncpActionServlet {
     *
     *     public DynActionTestService_update(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
     *         super(resourceName, resourceType, service, serviceid, actionid, method);
     *     }
     *
     *     &#064;Override
     *     public void action(SncpRequest request, SncpResponse response) throws Throwable {
     *         Convert&#60;Reader, Writer&#62; convert = request.getConvert();
     *         Reader in = request.getReader();
     *         long a1 = convert.convertFrom(paramTypes[1], in);
     *         short a2 = convert.convertFrom(paramTypes[2], in);
     *         CompletionHandler a3 = response.getParamAsyncHandler();
     *         convert.convertFrom(CompletionHandler.class, in);
     *         TestBean arg1 = convert.convertFrom(paramTypes[4], in);
     *         String arg2 = convert.convertFrom(paramTypes[5], in);
     *         int arg3 = convert.convertFrom(paramTypes[6], in);
     *         TestService serviceObj = (TestService) service();
     *         serviceObj.update(a1, a2, a3, arg1, arg2, arg3);
     *         response.finishVoid();
     *     }
     * }
     *
     * public class DynActionTestService_changeName extends SncpActionServlet {
     *
     *     public DynActionTestService_changeName(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
     *         super(resourceName, resourceType, service, serviceid, actionid, method);
     *     }
     *
     *     &#064;Override
     *     public void action(SncpRequest request, SncpResponse response) throws Throwable {
     *         Convert&#60;Reader, Writer&#62; convert = request.getConvert();
     * Reader in = request.getReader();
     * TestBean arg1 = convert.convertFrom(paramTypes[1], in);
     * String arg2 = convert.convertFrom(paramTypes[2], in);
     * int arg3 = convert.convertFrom(paramTypes[3], in);
     * TestService serviceObj = (TestService) service();
     * CompletableFuture future = serviceObj.changeName(arg1, arg2, arg3);
     * response.finishFuture(paramHandlerType, future);
     * }
     * }
     *
     * </pre>
     *
     * </blockquote>
     *
     * @param resourceName 资源名
     * @param resourceType 资源类
     * @param serviceImplClass Service实现类
     * @param service Service
     * @param serviceid 类ID
     * @param actionid 操作ID
     * @param method 方法
     * @return SncpActionServlet
     */
    @SuppressWarnings("unchecked")
    public static SncpActionServlet create(
            final String resourceName,
            final Class resourceType,
            final Class serviceImplClass,
            final Service service,
            final Uint128 serviceid,
            final Uint128 actionid,
            final Method method) {

        final Class serviceClass = service.getClass();
        final String supDynName = SncpActionServlet.class.getName().replace('.', '/');
        final String serviceImpTypeName = serviceImplClass.getName().replace('.', '/');
        final String convertName = Convert.class.getName().replace('.', '/');
        final String uint128Desc = Type.getDescriptor(Uint128.class);
        final String convertDesc = Type.getDescriptor(Convert.class);
        final String readerDesc = Type.getDescriptor(Reader.class);
        final String requestName = SncpRequest.class.getName().replace('.', '/');
        final String responseName = SncpResponse.class.getName().replace('.', '/');
        final String requestDesc = Type.getDescriptor(SncpRequest.class);
        final String responseDesc = Type.getDescriptor(SncpResponse.class);
        final String serviceDesc = Type.getDescriptor(Service.class);
        final String handlerDesc = Type.getDescriptor(CompletionHandler.class);
        final String futureDesc = Type.getDescriptor(Future.class);
        final String reflectTypeDesc = Type.getDescriptor(java.lang.reflect.Type.class);
        final boolean boolReturnTypeFuture = Future.class.isAssignableFrom(method.getReturnType());
        final String newDynName = "org/redkaledyn/sncp/servlet/action/_DynSncpActionServlet__"
                + resourceType.getSimpleName() + "_" + method.getName() + "_" + actionid;
        RedkaleClassLoader classLoader = RedkaleClassLoader.getRedkaleClassLoader();
        Class<?> newClazz = null;
        try {
            Class clz = classLoader.findDynClass(newDynName.replace('/', '.'));
            newClazz = clz == null ? classLoader.loadClass(newDynName.replace('/', '.')) : clz;
        } catch (Throwable ex) {
            // do nothing
        }

        final java.lang.reflect.Type[] originalParamTypes =
                TypeToken.getGenericType(method.getGenericParameterTypes(), serviceClass);
        final java.lang.reflect.Type originalReturnType =
                TypeToken.getGenericType(method.getGenericReturnType(), serviceClass);

        final Class[] paramClasses = method.getParameterTypes();
        java.lang.reflect.Type paramComposeBeanType0 = SncpRemoteAction.createParamComposeBeanType(classLoader, serviceImplClass, method, actionid, originalParamTypes, paramClasses);
        if (paramComposeBeanType0 != null && paramComposeBeanType0 == originalParamTypes[0]) {
            paramComposeBeanType0 = null;
        }
        int handlerFuncIndex = -1;
        for (int i = 0; i < paramClasses.length; i++) { // 反序列化方法的每个参数
            if (CompletionHandler.class.isAssignableFrom(paramClasses[i])) {
                if (boolReturnTypeFuture) {
                    throw new SncpException(method + " have both CompletionHandler and CompletableFuture");
                }
                if (handlerFuncIndex >= 0) {
                    throw new SncpException(method + " have more than one CompletionHandler type parameter");
                }
                Sncp.checkAsyncModifier(paramClasses[i], method);
                handlerFuncIndex = i;
            }
        }
        final java.lang.reflect.Type paramComposeBeanType = paramComposeBeanType0;

        if (newClazz == null) {
            // -------------------------------------------------------------
            ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
            MethodDebugVisitor mv;

            cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
            {
                mv = new MethodDebugVisitor(cw.visitMethod(
                        ACC_PUBLIC,
                        "<init>",
                        "(Ljava/lang/String;Ljava/lang/Class;" + serviceDesc + uint128Desc + uint128Desc
                                + "Ljava/lang/reflect/Method;)V",
                        null,
                        null));
                Label label0 = new Label();
                mv.visitLabel(label0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitVarInsn(ALOAD, 4);
                mv.visitVarInsn(ALOAD, 5);
                mv.visitVarInsn(ALOAD, 6);
                mv.visitMethodInsn(
                        INVOKESPECIAL,
                        supDynName,
                        "<init>",
                        "(Ljava/lang/String;Ljava/lang/Class;" + serviceDesc + uint128Desc + uint128Desc
                                + "Ljava/lang/reflect/Method;)V",
                        false);
                mv.visitInsn(RETURN);
                Label label2 = new Label();
                mv.visitLabel(label2);
                mv.visitLocalVariable("this", "L" + newDynName + ";", null, label0, label2, 0);
                mv.visitLocalVariable("resourceName", "Ljava/lang/String;", null, label0, label2, 1);
                mv.visitLocalVariable("resourceType", "Ljava/lang/Class;", null, label0, label2, 2);
                mv.visitLocalVariable("service", serviceDesc, null, label0, label2, 3);
                mv.visitLocalVariable("serviceid", uint128Desc, null, label0, label2, 4);
                mv.visitLocalVariable("actionid", uint128Desc, null, label0, label2, 5);
                mv.visitLocalVariable("method", "Ljava/lang/reflect/Method;", null, label0, label2, 6);
                mv.visitMaxs(7, 7);
                mv.visitEnd();
            }
            String convertFromDesc = "(Ljava/lang/reflect/Type;" + readerDesc + ")Ljava/lang/Object;";
            try {
                convertFromDesc = Type.getMethodDescriptor(
                        Convert.class.getMethod("convertFrom", java.lang.reflect.Type.class, Reader.class));
            } catch (Exception ex) {
                throw new SncpException(ex); // 不可能会发生
            }
            { // action方法
                mv = new MethodDebugVisitor(cw.visitMethod(
                        ACC_PUBLIC, "action", "(" + requestDesc + responseDesc + ")V", null, new String[] {
                            "java/lang/Throwable"
                        }));
                // mv.setDebug(true);
                { // Convert
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, requestName, "getConvert", "()" + convertDesc, false);
                    mv.visitVarInsn(ASTORE, 3);
                }
                { // Reader
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, requestName, "getReader", "()" + readerDesc, false);
                    mv.visitVarInsn(ASTORE, 4);
                }
                if (paramComposeBeanType == null) {
                    int iconst = ICONST_1;
                    int intconst = 1;
                    int store = 5; // action的参数个数+2
                    int[][] codes = new int[paramClasses.length][2];
                    for (int i = 0; i < paramClasses.length; i++) { // 反序列化方法的每个参数
                        if (CompletionHandler.class.isAssignableFrom(paramClasses[i])) {
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "getParamAsyncHandler",
                                    "()Ljava/nio/channels/CompletionHandler;",
                                    false);
                            mv.visitTypeInsn(
                                    CHECKCAST, paramClasses[i].getName().replace('.', '/'));
                            mv.visitVarInsn(ASTORE, store);
                            codes[i] = new int[] {ALOAD, store};
                            store++;
                            iconst++;
                            intconst++;
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitLdcInsn(Type.getType(Type.getDescriptor(CompletionHandler.class)));
                            mv.visitVarInsn(ALOAD, 4);
                            mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                            mv.visitInsn(POP);
                            continue;
                        }
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, "paramTypes", "[Ljava/lang/reflect/Type;");

                        if (intconst < 6) {
                            mv.visitInsn(ICONST_0 + intconst);
                        } else if (iconst <= Byte.MAX_VALUE) {
                            mv.visitIntInsn(BIPUSH, intconst);
                        } else if (iconst <= Short.MAX_VALUE) {
                            mv.visitIntInsn(SIPUSH, intconst);
                        } else {
                            mv.visitLdcInsn(intconst);
                        }
                        mv.visitInsn(AALOAD);
                        mv.visitVarInsn(ALOAD, 4);

                        mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                        int load = ALOAD;
                        int v = 0;
                        if (paramClasses[i].isPrimitive()) {
                            int storecode = ISTORE;
                            load = ILOAD;
                            if (paramClasses[i] == long.class) {
                                storecode = LSTORE;
                                load = LLOAD;
                                v = 1;
                            } else if (paramClasses[i] == float.class) {
                                storecode = FSTORE;
                                load = FLOAD;
                                v = 1;
                            } else if (paramClasses[i] == double.class) {
                                storecode = DSTORE;
                                load = DLOAD;
                                v = 1;
                            }
                            Class bigPrimitiveClass = TypeToken.primitiveToWrapper(paramClasses[i]);
                            String bigPrimitiveName =
                                    bigPrimitiveClass.getName().replace('.', '/');
                            try {
                                Method pm = bigPrimitiveClass.getMethod(paramClasses[i].getSimpleName() + "Value");
                                mv.visitTypeInsn(CHECKCAST, bigPrimitiveName);
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL,
                                        bigPrimitiveName,
                                        pm.getName(),
                                        Type.getMethodDescriptor(pm),
                                        false);
                            } catch (Exception ex) {
                                throw new SncpException(ex); // 不可能会发生
                            }
                            mv.visitVarInsn(storecode, store);
                        } else {
                            mv.visitTypeInsn(
                                    CHECKCAST, paramClasses[i].getName().replace('.', '/'));
                            mv.visitVarInsn(ASTORE, store); //
                        }
                        codes[i] = new int[] {load, store};
                        store += v;
                        iconst++;
                        intconst++;
                        store++;
                    }
                    { // 调用service
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "service", "()" + serviceDesc, false);
                        mv.visitTypeInsn(CHECKCAST, serviceImpTypeName);
                        mv.visitVarInsn(ASTORE, store);

                        mv.visitVarInsn(ALOAD, store);
                        for (int[] j : codes) {
                            mv.visitVarInsn(j[0], j[1]);
                        }
                        mv.visitMethodInsn(
                                serviceImplClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                serviceImpTypeName,
                                method.getName(),
                                Type.getMethodDescriptor(method),
                                serviceImplClass.isInterface());
                        store++;
                    }

                    if (method.getReturnType() != void.class) {
                        final Class returnClass = method.getReturnType();
                        if (returnClass.isPrimitive()) {
                            Class bigClass = TypeToken.primitiveToWrapper(returnClass);
                            try {
                                Method vo = bigClass.getMethod("valueOf", returnClass);
                                mv.visitMethodInsn(
                                        INVOKESTATIC,
                                        bigClass.getName().replace('.', '/'),
                                        vo.getName(),
                                        Type.getMethodDescriptor(vo),
                                        false);
                            } catch (Exception ex) {
                                throw new SncpException(ex); // 不可能会发生
                            }
                        }
                        mv.visitVarInsn(ASTORE, store); // 11

                        if (boolReturnTypeFuture) { // 返回类型为Future
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "returnFutureType", reflectTypeDesc);
                            mv.visitVarInsn(ALOAD, store);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finishFuture",
                                    "(" + reflectTypeDesc + futureDesc + ")V",
                                    false);
                        } else if (handlerFuncIndex >= 0) { // 参数有CompletionHandler
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                        } else { // 普通对象
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "returnObjectType", reflectTypeDesc);
                            mv.visitVarInsn(ALOAD, store);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finish",
                                    "(" + reflectTypeDesc + "Ljava/lang/Object;)V",
                                    false);
                        }
                    } else { // void返回类型
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                    }
                } else { // 动态生成的参数组合类
                    Class paramComposeBeanClass = TypeToken.typeToClass(paramComposeBeanType);
                    String paramComposeBeanName =
                            paramComposeBeanClass.getName().replace('.', '/');
                    mv.visitVarInsn(ALOAD, 3); // convert
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "paramComposeBeanType", reflectTypeDesc);
                    mv.visitVarInsn(ALOAD, 4); // reader
                    mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                    mv.visitTypeInsn(CHECKCAST, paramComposeBeanName);
                    mv.visitVarInsn(ASTORE, 5); // paramBean

                    // 给CompletionHandler参数赋值
                    if (handlerFuncIndex >= 0) {
                        mv.visitVarInsn(ALOAD, 5);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, responseName, "getParamAsyncHandler", "()" + handlerDesc, false);
                        mv.visitFieldInsn(PUTFIELD, paramComposeBeanName, "arg" + (handlerFuncIndex + 1), handlerDesc);
                    }
                    // 调用service()
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "service", "()" + serviceDesc, false);
                    mv.visitTypeInsn(CHECKCAST, serviceImpTypeName);
                    mv.visitVarInsn(ASTORE, 6); // service

                    // 执行service方法
                    mv.visitVarInsn(ALOAD, 6); // service
                    for (int i = 1; i <= paramClasses.length; i++) {
                        mv.visitVarInsn(ALOAD, 5); // paramBean
                        mv.visitFieldInsn(
                                GETFIELD, paramComposeBeanName, "arg" + i, Type.getDescriptor(paramClasses[i - 1]));
                    }
                    mv.visitMethodInsn(
                            serviceImplClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                            serviceImpTypeName,
                            method.getName(),
                            Type.getMethodDescriptor(method),
                            serviceImplClass.isInterface());

                    // 返回
                    if (method.getReturnType() != void.class) {
                        final Class returnClass = method.getReturnType();
                        if (returnClass.isPrimitive()) {
                            Class bigClass = TypeToken.primitiveToWrapper(returnClass);
                            try {
                                Method vo = bigClass.getMethod("valueOf", returnClass);
                                mv.visitMethodInsn(
                                        INVOKESTATIC,
                                        bigClass.getName().replace('.', '/'),
                                        vo.getName(),
                                        Type.getMethodDescriptor(vo),
                                        false);
                            } catch (Exception ex) {
                                throw new SncpException(ex); // 不可能会发生
                            }
                        }
                        mv.visitVarInsn(ASTORE, 7); // returnObject

                        if (boolReturnTypeFuture) { // 返回类型为Future
                            mv.visitVarInsn(ALOAD, 2); // response
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "returnFutureType", reflectTypeDesc);
                            mv.visitVarInsn(ALOAD, 7); // returnObject
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finishFuture",
                                    "(" + reflectTypeDesc + futureDesc + ")V",
                                    false);
                        } else if (handlerFuncIndex >= 0) { // 参数有CompletionHandler
                            mv.visitVarInsn(ALOAD, 2); // response
                            mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                        } else { // 普通对象
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "returnObjectType", reflectTypeDesc);
                            mv.visitVarInsn(ALOAD, 7); // returnObject
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finish",
                                    "(" + reflectTypeDesc + "Ljava/lang/Object;)V",
                                    false);
                        }
                    } else { // void返回类型
                        mv.visitVarInsn(ALOAD, 2); // response
                        mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                    }
                }

                mv.visitInsn(RETURN);
                mv.visitMaxs(8, 8);
                mv.visitEnd();
            }
            cw.visitEnd();

            byte[] bytes = cw.toByteArray();
            newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
            classLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
            RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));

            try {
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), newClazz.getField("service"));
            } catch (Exception e) {
                // do nothing
            }
            for (java.lang.reflect.Type t : originalParamTypes) {
                if (t == java.io.Serializable.class
                        || t == java.io.Serializable[].class
                        || t.toString().startsWith("java.lang.")) {
                    continue;
                }
                ProtobufFactory.root().loadDecoder(t);
            }
            if (originalReturnType != void.class && originalReturnType != Void.class) {
                if (boolReturnTypeFuture && method.getReturnType() != method.getGenericReturnType()) {
                    java.lang.reflect.Type t =
                            ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
                    if (t != Void.class && t != java.lang.reflect.Type.class) {
                        ProtobufFactory.root().loadEncoder(t);
                    }
                } else {
                    try {
                        ProtobufFactory.root().loadEncoder(originalReturnType);
                    } catch (Exception e) {
                        System.err.println(method);
                    }
                }
            }
        }
        try {
            return (SncpActionServlet) newClazz.getConstructors()[0].newInstance(
                    resourceName, resourceType, service, serviceid, actionid, method);
        } catch (Exception ex) {
            throw new SncpException(ex); // 不可能会发生
        }
    }
}
