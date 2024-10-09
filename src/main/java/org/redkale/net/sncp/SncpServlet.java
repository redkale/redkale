/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.redkale.annotation.NonBlocking;
import org.redkale.asm.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.*;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.net.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpServlet extends Servlet<SncpContext, SncpRequest, SncpResponse> implements Comparable<SncpServlet> {

    protected final Class resourceType;

    protected final String resourceName;

    protected final Service service;

    protected final Uint128 serviceid;

    private final HashMap<Uint128, SncpActionServlet> actions = new HashMap<>();

    private SncpServlet(String resourceName, Class resourceType, Service service, Uint128 serviceid) {
        Objects.requireNonNull(resourceName);
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(service);
        Objects.requireNonNull(serviceid);
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.service = service;
        this.serviceid = serviceid;
    }

    protected SncpServlet(String resourceName, Class resourceType, Service service) {
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.service = service;
        this._nonBlocking = true;
        this.serviceid = Sncp.serviceid(resourceName, resourceType);

        Class serviceImplClass = Sncp.getServiceType(service);
        for (Map.Entry<Uint128, Method> en :
                Sncp.loadRemoteMethodActions(serviceImplClass).entrySet()) {
            SncpActionServlet action;
            try {
                action = SncpActionServlet.create(
                        resourceName, resourceType, serviceImplClass, service, serviceid, en.getKey(), en.getValue());
            } catch (RuntimeException e) {
                throw new SncpException(
                        en.getValue() + " create " + SncpActionServlet.class.getSimpleName() + " error", e);
            }
            actions.put(en.getKey(), action);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        SncpHeader reqHeader = request.getHeader();
        final SncpActionServlet action = actions.get(reqHeader.getActionid());
        // logger.log(Level.FINEST, "sncpdyn.execute: " + request + ", " + (action == null ? "null" : action.method));
        if (action == null) {
            response.finish(SncpResponse.RETCODE_ILLACTIONID, null); // 无效actionid
        } else {
            try {
                reqHeader.serviceName = action.resourceType.getName();
                reqHeader.methodName = action.method.getName();
                if (response.inNonBlocking()) {
                    if (action.nonBlocking) {
                        action.execute(request, response);
                    } else {
                        response.updateNonBlocking(false);
                        response.getWorkExecutor().execute(() -> {
                            try {
                                Traces.computeIfAbsent(request.getTraceid());
                                action.execute(request, response);
                            } catch (Throwable t) {
                                response.getContext()
                                        .getLogger()
                                        .log(Level.WARNING, "Servlet occur exception. request = " + request, t);
                                response.finishError(t);
                            }
                            Traces.removeTraceid();
                        });
                    }
                } else {
                    action.execute(request, response);
                }
            } catch (Throwable t) {
                response.getContext().getLogger().log(Level.SEVERE, "sncp execute error(" + request + ")", t);
                response.finish(SncpResponse.RETCODE_THROWEXCEPTION, null);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append(" (type=").append(resourceType.getName());
        sb.append(", serviceid=")
                .append(serviceid)
                .append(", name='")
                .append(resourceName)
                .append("'");
        sb.append(", actions.size=")
                .append(actions.size() > 9 ? "" : " ")
                .append(actions.size())
                .append(")");
        return sb.toString();
    }

    public Service getService() {
        return service;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Class getResourceType() {
        return resourceType;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getActionSize() {
        return actions.size();
    }

    @Override
    public int compareTo(SncpServlet other) {
        if (!(other instanceof SncpServlet)) {
            return 1;
        }
        SncpServlet o = other;
        int rs;
        if (this.resourceType == null) {
            rs = o.resourceType == null ? 0 : -1;
        } else if (o.resourceType == null) {
            rs = 1;
        } else {
            rs = this.resourceType.getName().compareTo(o.resourceType.getName());
        }
        if (rs == 0) {
            if (this.resourceName == null) {
                rs = o.resourceName == null ? 0 : -1;
            } else if (o.resourceName == null) {
                rs = 1;
            } else {
                rs = this.resourceName.compareTo(o.resourceName);
            }
        }
        return rs;
    }

    protected ExecutorService getExecutor() {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            return ((WorkThread) thread).getWorkExecutor();
        }
        return ForkJoinPool.commonPool();
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof SncpServlet)) {
            return false;
        }
        return Objects.equals(getServiceid(), ((SncpServlet) obj).getServiceid());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(getServiceid());
    }

    public abstract static class SncpActionServlet extends SncpServlet {

        protected final Method method;

        protected final Uint128 actionid;

        protected final boolean nonBlocking;

        protected final java.lang.reflect.Type[] paramTypes; // 第一个元素存放返回类型return type， void的返回参数类型为null, 数组长度为:1+参数个数

        protected final java.lang.reflect.Type returnObjectType; // 返回结果类型 void必须设为null

        protected final int paramHandlerIndex; // >=0表示存在CompletionHandler参数

        protected final Class<? extends CompletionHandler> paramHandlerClass; // CompletionHandler参数的类型

        protected final java.lang.reflect.Type paramHandlerResultType; // CompletionHandler.completed第一个参数的类型

        protected final java.lang.reflect.Type returnFutureResultType; // 返回结果的CompletableFuture的结果泛型类型

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
                        java.lang.reflect.Type handlerType =
                                TypeToken.getGenericType(genericParams[i], service.getClass());
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
            this.paramHandlerResultType = handlerResultType;
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
                this.returnFutureResultType = returnType;
            } else {
                this.returnFutureResultType = null;
            }
            NonBlocking non = method.getAnnotation(NonBlocking.class);
            if (non == null) {
                non = service.getClass().getAnnotation(NonBlocking.class);
            }
            // Future代替CompletionStage 不容易判断异步
            this.nonBlocking = non == null
                    ? (CompletionStage.class.isAssignableFrom(method.getReturnType()) || this.paramHandlerIndex >= 0)
                    : false;
        }

        @Override
        public final void execute(SncpRequest request, SncpResponse response) throws IOException {
            if (paramHandlerIndex > 0) {
                response.paramAsyncHandler(paramHandlerClass, paramHandlerResultType);
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
         *      public interface TestService extends Service {
         *
         *     public boolean change(TestBean bean, String name, int id);
         *
         *     public void insert(BooleanHandler handler, TestBean bean, String name, int id);
         *
         *     public void update(long show, short v2, CompletionHandler&#60;Boolean, TestBean&#62; handler, TestBean bean, String name, int id);
         *
         *    public CompletableFuture&#60;String&#62; changeName(TestBean bean, String name, int id);
         *
         * }
         *
         * &#064;ResourceType(TestService.class)
         * class TestServiceImpl implements TestService {
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
         * class BooleanHandler implements CompletionHandler&#60;Boolean, TestBean&#62; {
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
         * class DynActionTestService_change extends SncpActionServlet {
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
         * class DynActionTestService_insert extends SncpActionServlet {
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
         * class DynActionTestService_update extends SncpActionServlet {
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
         * class DynActionTestService_changeName extends SncpActionServlet {
         *
         *     public DynActionTestService_changeName(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
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
         *         CompletableFuture future = serviceObj.changeName(arg1, arg2, arg3);
         *         response.finishFuture(paramHandlerResultType, future);
         *     }
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
            final boolean boolReturnTypeFuture = Future.class.isAssignableFrom(method.getReturnType());
            final String newDynName = "org/redkaledyn/sncp/servlet/action/_DynSncpActionServlet__"
                    + resourceType.getSimpleName() + "_" + method.getName() + "_" + actionid;

            Class<?> newClazz = null;
            try {
                Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
                newClazz = clz == null
                        ? Thread.currentThread().getContextClassLoader().loadClass(newDynName.replace('/', '.'))
                        : clz;
            } catch (Throwable ex) {
                // do nothing
            }

            final java.lang.reflect.Type[] originalParamTypes =
                    TypeToken.getGenericType(method.getGenericParameterTypes(), serviceClass);
            final java.lang.reflect.Type originalReturnType =
                    TypeToken.getGenericType(method.getGenericReturnType(), serviceClass);
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
                    int iconst = ICONST_1;
                    int intconst = 1;
                    int store = 5; // action的参数个数+2
                    final Class[] paramClasses = method.getParameterTypes();
                    int[][] codes = new int[paramClasses.length][2];
                    int handlerFuncIndex = -1;
                    for (int i = 0; i < paramClasses.length; i++) { // 反序列化方法的每个参数
                        if (CompletionHandler.class.isAssignableFrom(paramClasses[i])) {
                            if (boolReturnTypeFuture) {
                                throw new SncpException(method + " have both CompletionHandler and CompletableFuture");
                            }
                            if (handlerFuncIndex >= 0) {
                                throw new SncpException(
                                        method + " have more than one CompletionHandler type parameter");
                            }
                            Sncp.checkAsyncModifier(paramClasses[i], method);
                            handlerFuncIndex = i;
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
                            mv.visitFieldInsn(
                                    GETFIELD, newDynName, "returnFutureResultType", "Ljava/lang/reflect/Type;");
                            mv.visitVarInsn(ALOAD, store);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finishFuture",
                                    "(Ljava/lang/reflect/Type;Ljava/util/concurrent/Future;)V",
                                    false);
                        } else if (handlerFuncIndex >= 0) { // 参数有CompletionHandler
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                        } else { // 普通对象
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, "returnObjectType", "Ljava/lang/reflect/Type;");
                            mv.visitVarInsn(ALOAD, store);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    responseName,
                                    "finish",
                                    "(Ljava/lang/reflect/Type;Ljava/lang/Object;)V",
                                    false);
                        }
                    } else { // void返回类型
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(INVOKEVIRTUAL, responseName, "finishVoid", "()V", false);
                    }
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(8, store);
                    mv.visitEnd();
                }
                cw.visitEnd();

                byte[] bytes = cw.toByteArray();
                newClazz = new ClassLoader(serviceClass.getClassLoader()) {
                    public final Class<?> loadClass(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }.loadClass(newDynName.replace('/', '.'), bytes);
                RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
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
}
