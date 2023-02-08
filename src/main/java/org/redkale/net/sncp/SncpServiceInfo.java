/*
 *
 */
package org.redkale.net.sncp;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.convert.Convert;
import org.redkale.mq.*;
import static org.redkale.net.sncp.Sncp.loadMethodActions;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * 每个Service的client相关信息对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Service泛型
 *
 * @since 2.8.0
 */
public final class SncpServiceInfo<T extends Service> {

    protected final String name;

    protected final Class<T> serviceType;

    protected final T service;

    protected final Uint128 serviceid;

    protected final int serviceVersion;

    protected final SncpServiceAction[] actions;

    protected final String topic;

    protected final Convert convert;

    //非MQ模式下此字段才有值
    protected final SncpClient sncpClient;

    //MQ模式下此字段才有值
    protected final MessageAgent messageAgent;

    //MQ模式下此字段才有值
    protected final SncpMessageClient messageClient;

    //远程模式, 可能为null
    protected Set<String> remoteGroups;

    //远程模式, 可能为null
    protected Set<InetSocketAddress> remoteAddresses;

    SncpServiceInfo(String resourceName, Class<T> resourceServiceType, final T service, Convert convert,
        SncpClient sncpClient, MessageAgent messageAgent, SncpMessageClient messageClient) {
        this.sncpClient = sncpClient;
        this.name = resourceName;
        this.serviceType = resourceServiceType;
        this.serviceid = Sncp.serviceid(resourceName, resourceServiceType);
        this.service = service;
        this.convert = convert;
        this.serviceVersion = 0;
        this.messageAgent = messageAgent;
        this.messageClient = messageAgent == null ? null : messageAgent.getSncpMessageClient();
        this.topic = messageAgent == null ? null : messageAgent.generateSncpReqTopic(service);

        final List<SncpServiceAction> serviceActions = new ArrayList<>();
        final Class serviceImplClass = service.getClass();
        for (Map.Entry<Uint128, Method> en : loadMethodActions(resourceServiceType).entrySet()) {
            serviceActions.add(new SncpServiceAction(serviceImplClass, en.getValue(), serviceid, en.getKey()));
        }
        this.actions = serviceActions.toArray(new SncpServiceAction[serviceActions.size()]);
    }

    //只给远程模式调用的
    public <T> T remote(final int index, final Object... params) {
        return sncpClient.remote(this, index, params);
    }

    public void updateRemoteAddress(Set<String> remoteGroups, Set<InetSocketAddress> remoteAddresses) {
        this.remoteGroups = remoteGroups;
        this.remoteAddresses = remoteAddresses;
    }

    public String getName() {
        return name;
    }

    public Class getServiceClass() {
        return serviceType;
    }

    public T getService() {
        return service;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public SncpServiceAction[] getActions() {
        return actions;
    }

    public String getTopic() {
        return topic;
    }

    public Set<String> getRemoteGroups() {
        return remoteGroups;
    }

    public Set<InetSocketAddress> getRemoteAddresses() {
        return remoteAddresses;
    }

    public static final class SncpServiceAction {

        protected final Uint128 actionid;

        protected final Method method;

        protected final Type returnObjectType;  //void必须设为null

        protected final Type[] paramTypes;

        protected final Class[] paramClasses;

        protected final int paramHandlerIndex;

        protected final int paramHandlerAttachIndex;

        protected final int paramAddressTargetIndex;

        protected final int paramAddressSourceIndex;

        protected final int paramTopicTargetIndex;

        protected final Class<? extends CompletionHandler> paramHandlerClass; //CompletionHandler参数的类型

        protected final java.lang.reflect.Type paramHandlerResultType; //CompletionHandler.completed第一个参数的类型

        protected final java.lang.reflect.Type returnFutureResultType; //返回结果的CompletableFuture的结果泛型类型

        protected final Class<? extends Future> returnFutureClass; //返回结果的CompletableFuture类型

        protected final Creator<? extends CompletableFuture> returnFutureCreator; //返回CompletableFuture类型的构建器

        protected final SncpHeader header;

        @SuppressWarnings("unchecked")
        SncpServiceAction(final Class serviceImplClass, Method method, Uint128 serviceid, Uint128 actionid) {
            this.actionid = actionid == null ? Sncp.actionid(method) : actionid;
            Type rt = TypeToken.getGenericType(method.getGenericReturnType(), serviceImplClass);
            this.returnObjectType = rt == void.class || rt == Void.class ? null : rt;
            this.paramTypes = TypeToken.getGenericType(method.getGenericParameterTypes(), serviceImplClass);
            this.paramClasses = method.getParameterTypes();
            this.method = method;
            Annotation[][] anns = method.getParameterAnnotations();
            int tpoicAddrIndex = -1;
            int targetAddrIndex = -1;
            int sourceAddrIndex = -1;
            int handlerAttachIndex = -1;
            int handlerFuncIndex = -1;
            Class handlerFuncClass = null;
            java.lang.reflect.Type handlerResultType = null;
            Class<?>[] params = method.getParameterTypes();
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
                    java.lang.reflect.Type handlerType = TypeToken.getGenericType(method.getTypeParameters()[i], serviceImplClass);
                    if (handlerType instanceof Class) {
                        handlerResultType = Object.class;
                    } else if (handlerType instanceof ParameterizedType) {
                        handlerResultType = TypeToken.getGenericType(((ParameterizedType) handlerType).getActualTypeArguments()[0], handlerType);
                    } else {
                        throw new SncpException(serviceImplClass + " had unknown genericType in " + method);
                    }
                    if (method.getReturnType() != void.class) {
                        throw new SncpException(method + " have CompletionHandler type parameter but return type is not void");
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
                                    throw new SncpException(method + " must be SocketAddress Type on @RpcTargetAddress parameter");
                                }
                            } else if (ann.annotationType() == RpcSourceAddress.class) {
                                if (SocketAddress.class.isAssignableFrom(params[i])) {
                                    if (sourceAddrIndex >= 0) {
                                        throw new SncpException(method + " have more than one @RpcSourceAddress parameter");
                                    } else {
                                        sourceAddrIndex = i;
                                    }
                                } else {
                                    throw new SncpException(method + " must be SocketAddress Type on @RpcSourceAddress parameter");
                                }
                            } else if (ann.annotationType() == RpcTargetTopic.class) {
                                if (String.class.isAssignableFrom(params[i])) {
                                    if (sourceAddrIndex >= 0) {
                                        throw new SncpException(method + " have more than one @RpcTargetTopic parameter");
                                    } else {
                                        tpoicAddrIndex = i;
                                    }
                                } else {
                                    throw new SncpException(method + " must be String Type on @RpcTargetTopic parameter");
                                }
                            }
                        }
                    }
                }
            }
            this.paramTopicTargetIndex = tpoicAddrIndex;
            this.paramAddressTargetIndex = targetAddrIndex;
            this.paramAddressSourceIndex = sourceAddrIndex;
            this.paramHandlerIndex = handlerFuncIndex;
            this.paramHandlerClass = handlerFuncClass;
            this.paramHandlerResultType = handlerResultType;
            this.paramHandlerAttachIndex = handlerAttachIndex;
            this.header = new SncpHeader(null, serviceid, actionid);
            if (this.paramHandlerIndex >= 0 && method.getReturnType() != void.class) {
                throw new SncpException(method + " have CompletionHandler type parameter but return type is not void");
            }
            if (Future.class.isAssignableFrom(method.getReturnType())) {
                java.lang.reflect.Type futureType = TypeToken.getGenericType(method.getGenericReturnType(), serviceImplClass);
                java.lang.reflect.Type returnType = null;
                if (futureType instanceof Class) {
                    returnType = Object.class;
                } else if (futureType instanceof ParameterizedType) {
                    returnType = TypeToken.getGenericType(((ParameterizedType) futureType).getActualTypeArguments()[0], futureType);
                } else {
                    throw new SncpException(serviceImplClass + " had unknown return genericType in " + method);
                }
                this.returnFutureResultType = returnType;
                this.returnFutureClass = method.getReturnType().isAssignableFrom(CompletableFuture.class) ? CompletableFuture.class : (Class) method.getReturnType();
                if (method.getReturnType().isAssignableFrom(CompletableFuture.class) || CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                    this.returnFutureCreator = (Creator) Creator.create(this.returnFutureClass);
                } else {
                    throw new SncpException(serviceImplClass + " return must be CompletableFuture or subclass");
                }
            } else {
                this.returnFutureResultType = null;
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
    }
}
