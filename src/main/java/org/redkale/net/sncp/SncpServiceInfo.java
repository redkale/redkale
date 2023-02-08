/*
 *
 */
package org.redkale.net.sncp;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    //MQ模式下此字段才有值
    protected final MessageAgent messageAgent;

    //MQ模式下此字段才有值
    protected final SncpMessageClient messageClient;

    //远程模式, 可能为null
    protected Set<String> remoteGroups;

    //远程模式, 可能为null
    protected Set<InetSocketAddress> remoteAddresses;

    SncpServiceInfo(String resourceName, Class<T> resourceServiceType, final T service, MessageAgent messageAgent, SncpMessageClient messageClient) {
        this.name = resourceName;
        this.serviceType = resourceServiceType;
        this.serviceid = Sncp.serviceid(name, resourceServiceType);
        this.service = service;
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

        protected final Type returnObjectType;  //void 必须设为 null

        protected final Type[] paramTypes;

        protected final Class[] paramClass;

        protected final Attribute[] paramAttrs; // 为null表示无RpcCall处理，index=0固定为null, 其他为参数标记的RpcCall回调方法

        protected final int handlerFuncParamIndex;

        protected final int handlerAttachParamIndex;

        protected final int addressTargetParamIndex;

        protected final int addressSourceParamIndex;

        protected final int topicTargetParamIndex;

        protected final boolean boolReturnTypeFuture; // 返回结果类型是否为 CompletableFuture

        protected final Creator<? extends CompletableFuture> futureCreator;

        protected final SncpHeader header;

        @SuppressWarnings("unchecked")
        SncpServiceAction(final Class serviceImplClass, Method method, Uint128 serviceid, Uint128 actionid) {
            this.actionid = actionid == null ? Sncp.actionid(method) : actionid;
            Type rt = TypeToken.getGenericType(method.getGenericReturnType(), serviceImplClass);
            this.returnObjectType = rt == void.class ? null : rt;
            this.boolReturnTypeFuture = CompletableFuture.class.isAssignableFrom(method.getReturnType());
            this.futureCreator = boolReturnTypeFuture ? Creator.create((Class<? extends CompletableFuture>) method.getReturnType()) : null;
            this.paramTypes = TypeToken.getGenericType(method.getGenericParameterTypes(), serviceImplClass);
            this.paramClass = method.getParameterTypes();
            this.method = method;
            Annotation[][] anns = method.getParameterAnnotations();
            int tpoicAddrIndex = -1;
            int targetAddrIndex = -1;
            int sourceAddrIndex = -1;
            int handlerAttachIndex = -1;
            int handlerFuncIndex = -1;
            boolean hasattr = false;
            Attribute[] atts = new Attribute[paramTypes.length + 1];
            if (anns.length > 0) {
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (CompletionHandler.class.isAssignableFrom(params[i])) {
                        if (boolReturnTypeFuture) {
                            throw new SncpException(method + " have both CompletionHandler and CompletableFuture");
                        }
                        if (handlerFuncIndex >= 0) {
                            throw new SncpException(method + " have more than one CompletionHandler type parameter");
                        }
                        Sncp.checkAsyncModifier(params[i], method);
                        handlerFuncIndex = i;
                        break;
                    }
                }
                for (int i = 0; i < anns.length; i++) {
                    if (anns[i].length > 0) {
                        for (Annotation ann : anns[i]) {
                            if (ann.annotationType() == RpcAttachment.class) {
                                if (handlerAttachIndex >= 0) {
                                    throw new SncpException(method + " have more than one @RpcAttachment parameter");
                                }
                                handlerAttachIndex = i;
                            } else if (ann.annotationType() == RpcTargetAddress.class && SocketAddress.class.isAssignableFrom(params[i])) {
                                targetAddrIndex = i;
                            } else if (ann.annotationType() == RpcSourceAddress.class && SocketAddress.class.isAssignableFrom(params[i])) {
                                sourceAddrIndex = i;
                            } else if (ann.annotationType() == RpcTargetTopic.class && String.class.isAssignableFrom(params[i])) {
                                tpoicAddrIndex = i;
                            }
                        }
                    }
                }
            }
            this.topicTargetParamIndex = tpoicAddrIndex;
            this.addressTargetParamIndex = targetAddrIndex;
            this.addressSourceParamIndex = sourceAddrIndex;
            this.handlerFuncParamIndex = handlerFuncIndex;
            this.handlerAttachParamIndex = handlerAttachIndex;
            this.paramAttrs = hasattr ? atts : null;
            this.header = new SncpHeader(null, serviceid, actionid);
            if (this.handlerFuncParamIndex >= 0 && method.getReturnType() != void.class) {
                throw new SncpException(method + " have CompletionHandler type parameter but return type is not void");
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
