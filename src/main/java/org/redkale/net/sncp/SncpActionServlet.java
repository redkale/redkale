/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.net.sncp;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.constant.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.NonBlocking;
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
                RedkaleClassLoader.currentClassLoader(),
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
        final ClassDesc superDesc = ClassDesc.ofDescriptor(SncpActionServlet.class.descriptorString());
        final ClassDesc serviceImplDesc = ClassDesc.ofDescriptor(serviceImplClass.descriptorString());
        final ClassDesc uint128Desc = ClassDesc.ofDescriptor(Uint128.class.descriptorString());
        final ClassDesc convertDesc = ClassDesc.ofDescriptor(Convert.class.descriptorString());
        final ClassDesc readerDesc = ClassDesc.ofDescriptor(Reader.class.descriptorString());
        final ClassDesc requestDesc = ClassDesc.ofDescriptor(SncpRequest.class.descriptorString());
        final ClassDesc responseDesc = ClassDesc.ofDescriptor(SncpResponse.class.descriptorString());
        final ClassDesc serviceDesc = ClassDesc.ofDescriptor(Service.class.descriptorString());
        final ClassDesc handlerDesc = ClassDesc.ofDescriptor(CompletionHandler.class.descriptorString());
        final ClassDesc futureDesc = ClassDesc.ofDescriptor(Future.class.descriptorString());
        final ClassDesc reflectTypeDesc = ClassDesc.ofDescriptor(java.lang.reflect.Type.class.descriptorString());
        final boolean boolReturnTypeFuture = Future.class.isAssignableFrom(method.getReturnType());
        final String newDynName = "org/redkaledyn/sncp/servlet/action/_DynSncpActionServlet__"
                + resourceType.getSimpleName() + "_" + method.getName() + "_" + actionid;
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        Class<?> newClazz = null;
        try {
            newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
        } catch (Throwable ex) {
            // do nothing
        }

        final java.lang.reflect.Type[] originalParamTypes =
                TypeToken.getGenericType(method.getGenericParameterTypes(), serviceClass);
        final java.lang.reflect.Type originalReturnType =
                TypeToken.getGenericType(method.getGenericReturnType(), serviceClass);

        final Class[] paramClasses = method.getParameterTypes();
        java.lang.reflect.Type paramComposeBeanType0 = SncpRemoteAction.createParamComposeBeanType(
                classLoader, serviceImplClass, method, actionid, originalParamTypes, paramClasses);
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
            final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
            final ClassDesc methodClassDesc = ClassDesc.ofDescriptor(Method.class.descriptorString());
            final MethodTypeDesc constructorDesc = MethodTypeDesc.of(
                    CD_void, CD_String, CD_Class, serviceDesc, uint128Desc, uint128Desc, methodClassDesc);
            final MethodTypeDesc serviceMethodDesc = MethodTypeDesc.ofDescriptor(
                    MethodType.methodType(method.getReturnType(), paramClasses).descriptorString());
            final MethodTypeDesc convertFromDesc = MethodTypeDesc.of(CD_Object, reflectTypeDesc, readerDesc);
            final int handlerIndex = handlerFuncIndex;
            byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
                cb.withVersion(JAVA_11_VERSION, 0)
                        .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                        .withSuperclass(superDesc);
                cb.withMethodBody("<init>", constructorDesc, ACC_PUBLIC, code -> {
                    code.aload(0)
                            .aload(1)
                            .aload(2)
                            .aload(3)
                            .aload(4)
                            .aload(5)
                            .aload(6)
                            .invokespecial(superDesc, "<init>", constructorDesc)
                            .return_();
                    code.localVariable(0, "this", dynDesc, code.startLabel(), code.endLabel());
                    code.localVariable(1, "resourceName", CD_String, code.startLabel(), code.endLabel());
                    code.localVariable(2, "resourceType", CD_Class, code.startLabel(), code.endLabel());
                    code.localVariable(3, "service", serviceDesc, code.startLabel(), code.endLabel());
                    code.localVariable(4, "serviceid", uint128Desc, code.startLabel(), code.endLabel());
                    code.localVariable(5, "actionid", uint128Desc, code.startLabel(), code.endLabel());
                    code.localVariable(6, "method", methodClassDesc, code.startLabel(), code.endLabel());
                });
                cb.withMethod("action", MethodTypeDesc.of(CD_void, requestDesc, responseDesc), ACC_PUBLIC, mb -> {
                    mb.with(ExceptionsAttribute.ofSymbols(CD_Throwable));
                    mb.withCode(code -> {
                        int convertSlot = code.allocateLocal(TypeKind.REFERENCE);
                        int readerSlot = code.allocateLocal(TypeKind.REFERENCE);
                        code.aload(1)
                                .invokevirtual(requestDesc, "getConvert", MethodTypeDesc.of(convertDesc))
                                .astore(convertSlot);
                        code.aload(1)
                                .invokevirtual(requestDesc, "getReader", MethodTypeDesc.of(readerDesc))
                                .astore(readerSlot);
                        if (paramComposeBeanType == null) {
                            int[] paramSlots = new int[paramClasses.length];
                            for (int i = 0; i < paramClasses.length; i++) {
                                Class paramClass = paramClasses[i];
                                ClassDesc paramDesc = ClassDesc.ofDescriptor(paramClass.descriptorString());
                                TypeKind kind = TypeKind.from(paramDesc);
                                paramSlots[i] = code.allocateLocal(kind);
                                if (CompletionHandler.class.isAssignableFrom(paramClass)) {
                                    code.aload(2)
                                            .invokevirtual(
                                                    responseDesc,
                                                    "getParamAsyncHandler",
                                                    MethodTypeDesc.of(handlerDesc))
                                            .checkcast(paramDesc)
                                            .astore(paramSlots[i]);
                                    // 消费请求中的CompletionHandler占位参数
                                    code.aload(convertSlot)
                                            .loadConstant(handlerDesc)
                                            .aload(readerSlot)
                                            .invokevirtual(convertDesc, "convertFrom", convertFromDesc)
                                            .pop();
                                } else {
                                    code.aload(convertSlot)
                                            .aload(0)
                                            .getfield(dynDesc, "paramTypes", reflectTypeDesc.arrayType())
                                            .loadConstant(i + 1)
                                            .aaload()
                                            .aload(readerSlot)
                                            .invokevirtual(convertDesc, "convertFrom", convertFromDesc);
                                    if (paramClass.isPrimitive()) {
                                        ClassDesc wrapperDesc =
                                                ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(paramClass)
                                                        .descriptorString());
                                        code.checkcast(wrapperDesc)
                                                .invokevirtual(
                                                        wrapperDesc,
                                                        paramClass.getSimpleName() + "Value",
                                                        MethodTypeDesc.of(paramDesc));
                                    } else {
                                        code.checkcast(paramDesc);
                                    }
                                    code.storeLocal(kind, paramSlots[i]);
                                }
                            }
                            code.aload(0)
                                    .invokevirtual(dynDesc, "service", MethodTypeDesc.of(serviceDesc))
                                    .checkcast(serviceImplDesc);
                            for (int i = 0; i < paramClasses.length; i++) {
                                code.loadLocal(TypeKind.from(paramClasses[i]), paramSlots[i]);
                            }
                        } else {
                            // 动态生成的参数组合类
                            ClassDesc beanDesc = ClassDesc.ofDescriptor(
                                    TypeToken.typeToClass(paramComposeBeanType).descriptorString());
                            int beanSlot = code.allocateLocal(TypeKind.REFERENCE);
                            code.aload(convertSlot)
                                    .aload(0)
                                    .getfield(dynDesc, "paramComposeBeanType", reflectTypeDesc)
                                    .aload(readerSlot)
                                    .invokevirtual(convertDesc, "convertFrom", convertFromDesc)
                                    .checkcast(beanDesc)
                                    .astore(beanSlot);
                            if (handlerIndex >= 0) {
                                code.aload(beanSlot)
                                        .aload(2)
                                        .invokevirtual(
                                                responseDesc, "getParamAsyncHandler", MethodTypeDesc.of(handlerDesc))
                                        .putfield(beanDesc, "arg" + (handlerIndex + 1), handlerDesc);
                            }
                            code.aload(0)
                                    .invokevirtual(dynDesc, "service", MethodTypeDesc.of(serviceDesc))
                                    .checkcast(serviceImplDesc);
                            for (int i = 0; i < paramClasses.length; i++) {
                                code.aload(beanSlot)
                                        .getfield(
                                                beanDesc,
                                                "arg" + (i + 1),
                                                ClassDesc.ofDescriptor(paramClasses[i].descriptorString()));
                            }
                        }
                        if (serviceImplClass.isInterface()) {
                            code.invokeinterface(serviceImplDesc, method.getName(), serviceMethodDesc);
                        } else {
                            code.invokevirtual(serviceImplDesc, method.getName(), serviceMethodDesc);
                        }
                        Class returnClass = method.getReturnType();
                        if (returnClass == void.class) {
                            code.aload(2).invokevirtual(responseDesc, "finishVoid", MethodTypeDesc.of(CD_void));
                        } else {
                            if (returnClass.isPrimitive()) {
                                ClassDesc wrapperDesc = ClassDesc.ofDescriptor(TypeToken.primitiveToWrapper(returnClass)
                                        .descriptorString());
                                code.invokestatic(
                                        wrapperDesc,
                                        "valueOf",
                                        MethodTypeDesc.of(wrapperDesc, serviceMethodDesc.returnType()));
                            }
                            int resultSlot = code.allocateLocal(TypeKind.REFERENCE);
                            code.astore(resultSlot);
                            if (boolReturnTypeFuture) {
                                code.aload(2)
                                        .aload(0)
                                        .getfield(dynDesc, "returnFutureType", reflectTypeDesc)
                                        .aload(resultSlot)
                                        .invokevirtual(
                                                responseDesc,
                                                "finishFuture",
                                                MethodTypeDesc.of(CD_void, reflectTypeDesc, futureDesc));
                            } else if (handlerIndex >= 0) {
                                code.aload(2).invokevirtual(responseDesc, "finishVoid", MethodTypeDesc.of(CD_void));
                            } else {
                                code.aload(2)
                                        .aload(0)
                                        .getfield(dynDesc, "returnObjectType", reflectTypeDesc)
                                        .aload(resultSlot)
                                        .invokevirtual(
                                                responseDesc,
                                                "finish",
                                                MethodTypeDesc.of(CD_void, reflectTypeDesc, CD_Object));
                            }
                        }
                        code.return_();
                    });
                });
            });
            newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
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
