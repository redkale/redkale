/*
 *
 */
package org.redkale.net.sncp;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufWriter;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.mq.spi.MessageClient;
import org.redkale.mq.spi.MessageRecord;
import static org.redkale.net.sncp.Sncp.loadRemoteMethodActions;
import static org.redkale.net.sncp.SncpHeader.HEADER_SUBSIZE;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * 每个Service的client相关信息对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <S> Service泛型
 * @since 2.8.0
 */
public class SncpRemoteInfo<S extends Service> {

    protected static final Logger logger = Logger.getLogger(SncpRemoteInfo.class.getSimpleName());

    protected final String name;

    protected final Class<S> serviceType;

    protected final Uint128 serviceid;

    protected final String resourceid;

    protected final int serviceVersion;

    // key: actionid.Uint128.toString()
    protected final Map<String, SncpRemoteAction> actions = new HashMap<>();

    // 非MQ模式下此字段才有值
    protected final SncpRpcGroups sncpRpcGroups;

    // 非MQ模式下此字段才有值
    protected final SncpClient sncpClient;

    // 非MQ模式下此字段才有值, 可能为null
    protected String remoteGroup;

    // 非MQ模式下此字段才有值, 可能为null
    protected Set<InetSocketAddress> remoteAddresses;

    // 默认值: ProtobufConvert.root()
    protected final ProtobufConvert convert;

    // MQ模式下此字段才有值
    protected final String topic;

    // MQ模式下此字段才有值
    protected final MessageAgent messageAgent;

    // MQ模式下此字段才有值
    protected final MessageClient messageClient;

    SncpRemoteInfo(
            String resourceName,
            Class<S> resourceType,
            Class<S> serviceImplClass,
            ProtobufConvert convert,
            SncpRpcGroups sncpRpcGroups,
            SncpClient sncpClient,
            MessageAgent messageAgent,
            String remoteGroup) {
        Objects.requireNonNull(sncpRpcGroups);
        this.name = resourceName;
        this.serviceType = resourceType;
        this.resourceid = Sncp.resourceid(resourceName, resourceType);
        this.serviceid = Sncp.serviceid(resourceName, resourceType);
        this.convert = convert;
        this.serviceVersion = 0;
        this.sncpRpcGroups = sncpRpcGroups;
        this.sncpClient = sncpClient;
        this.messageAgent = messageAgent;
        this.remoteGroup = remoteGroup;
        this.messageClient = messageAgent == null ? null : messageAgent.getSncpMessageClient();
        this.topic = messageAgent == null
                ? null
                : Sncp.generateSncpReqTopic(resourceName, resourceType, messageAgent.getNodeid());

        for (Map.Entry<Uint128, Method> en :
                loadRemoteMethodActions(Sncp.getServiceType(serviceImplClass)).entrySet()) {
            this.actions.put(
                    en.getKey().toString(),
                    new SncpRemoteAction(
                            serviceImplClass, resourceType, en.getValue(), serviceid, en.getKey(), sncpClient));
        }
    }

    // 由远程模式的DyncRemoveService调用
    public <T> T remote(final String actionid, final Object... params) {
        final SncpRemoteAction action = this.actions.get(actionid);
        CompletionHandler callbackHandler = null;
        Object callbackHandlerAttach = null;
        if (action.paramHandlerIndex >= 0) {
            callbackHandler = (CompletionHandler) params[action.paramHandlerIndex];
            params[action.paramHandlerIndex] = null;
            if (action.paramHandlerAttachIndex >= 0) {
                callbackHandlerAttach = params[action.paramHandlerAttachIndex];
                params[action.paramHandlerAttachIndex] = null;
            }
        }
        final CompletableFuture<byte[]> future = remote(action, Traces.currentTraceid(), params);
        if (action.paramHandlerIndex >= 0) { // 参数中存在CompletionHandler
            final CompletionHandler handler = callbackHandler;
            final Object attach = callbackHandlerAttach;
            if (handler == null) { // 传入的CompletionHandler参数为null
                future.join();
            } else {
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        // v,length-1为了读掉(byte)0
                        handler.completed(
                                v == null
                                        ? null
                                        : convert.convertFrom(action.paramHandlerResultType, v, 1, v.length - 1),
                                attach);
                    } else {
                        handler.failed(t, attach);
                    }
                });
            }
        } else if (action.returnFutureClass != null) { // 返回类型为CompletableFuture
            if (action.returnFutureClass == CompletableFuture.class) {
                // v,length-1为了读掉(byte)0
                return (T) future.thenApply(
                        v -> v == null ? null : convert.convertFrom(action.returnFutureResultType, v, 1, v.length - 1));
            } else {
                final CompletableFuture returnFuture = action.returnFutureCreator.create();
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        // v,length-1为了读掉(byte)0
                        returnFuture.complete(
                                v == null
                                        ? null
                                        : convert.convertFrom(action.returnFutureResultType, v, 1, v.length - 1));
                    } else {
                        returnFuture.completeExceptionally(t);
                    }
                });
                return (T) returnFuture;
            }
        } else if (action.returnObjectType != null) { // 返回类型为JavaBean
            // v,length-1为了读掉(byte)0
            return (T) future.thenApply(
                            v -> v == null ? null : convert.convertFrom(action.returnObjectType, v, 1, v.length - 1))
                    .join();
        } else { // 返回类型为void
            future.join();
        }
        return null;
    }

    private CompletableFuture<byte[]> remote(
            final SncpRemoteAction action, final String traceid, final Object[] params) {
        if (messageAgent != null) {
            return remoteMessage(action, traceid, params);
        } else {
            return remoteClient(action, traceid, params);
        }
    }

    // MQ模式RPC
    private CompletableFuture<byte[]> remoteMessage(
            final SncpRemoteAction action, final String traceid, final Object[] params) {
        final SncpClientRequest request =
                createSncpClientRequest(action, this.sncpClient.clientSncpAddress, traceid, params);
        String targetTopic =
                action.paramTopicTargetIndex >= 0 ? (String) params[action.paramTopicTargetIndex] : this.topic;
        if (targetTopic == null) {
            targetTopic = this.topic;
        }
        ByteArray array = new ByteArray();
        request.writeTo(null, array);
        MessageRecord message = messageAgent
                .getSncpMessageClient()
                .createMessageRecord(MessageRecord.CTYPE_PROTOBUF, targetTopic, null, array.getBytes());
        final String tt = targetTopic;
        message.localActionName(action.actionName());
        message.localParams(params);
        return messageClient.sendMessage(message).thenApply(msg -> {
            if (msg == null || msg.getContent() == null) {
                logger.log(
                        Level.SEVERE,
                        action.method + " sncp mq(params: " + JsonConvert.root().convertTo(params) + ", message: "
                                + message + ") deal error, this.topic = " + this.topic + ", targetTopic = " + tt
                                + ", result = " + msg);
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(msg.getContent());
            int headerSize = buffer.getChar();
            if (headerSize <= HEADER_SUBSIZE) {
                throw new SncpException("sncp header length must more " + HEADER_SUBSIZE + ", but is " + headerSize);
            }
            SncpHeader header = SncpHeader.read(buffer, headerSize);
            if (!header.checkValid(action.header)) {
                throw new SncpException(
                        "sncp header error, response-header:" + action.header + "+, response-header:" + header);
            }
            final int retcode = header.getRetcode();
            if (retcode != 0) {
                logger.log(
                        Level.SEVERE,
                        action.method + " sncp (params: " + JsonConvert.root().convertTo(params)
                                + ") deal error (retcode=" + retcode + ", retinfo="
                                + SncpResponse.getRetCodeInfo(retcode)
                                + "), params=" + JsonConvert.root().convertTo(params));
                throw new SncpException("remote service(" + action.method + ") deal error (retcode=" + retcode
                        + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + ")");
            }
            final int respBodyLength = header.getBodyLength();
            byte[] body = new byte[respBodyLength];
            buffer.get(body, 0, respBodyLength);
            return body;
        });
    }

    // Client模式RPC
    protected CompletableFuture<byte[]> remoteClient(
            final SncpRemoteAction action, final String traceid, final Object[] params) {
        final SncpClient client = this.sncpClient;
        final SncpClientRequest request = createSncpClientRequest(action, client.clientSncpAddress, traceid, params);
        final SocketAddress addr = action.paramAddressTargetIndex >= 0
                ? (SocketAddress) params[action.paramAddressTargetIndex]
                : nextRemoteAddress();
        return client.connect(addr)
                .thenCompose(conn -> client.writeChannel(conn, request).thenApply(rs -> rs.getBodyContent()));
    }

    protected SncpClientRequest createSncpClientRequest(
            SncpRemoteAction action, InetSocketAddress clientSncpAddress, String traceid, Object[] params) {
        final Type[] myParamTypes = action.paramTypes;
        final Class[] myParamClass = action.paramClasses;
        if (action.paramAddressSourceIndex >= 0) {
            params[action.paramAddressSourceIndex] = clientSncpAddress;
        }
        byte[] body = null;
        if (myParamTypes.length > 0) {
            ProtobufWriter writer = convert.pollWriter();
            for (int i = 0; i < params.length; i++) { // service方法的参数
                convert.convertTo(
                        writer,
                        CompletionHandler.class.isAssignableFrom(myParamClass[i])
                                ? CompletionHandler.class
                                : myParamTypes[i],
                        params[i]);
            }
            body = writer.toByteArray().content();
            convert.offerWriter(writer);
        }
        final SncpClientRequest request = new SncpClientRequest();
        request.prepare(action.header, this.sncpClient.nextSeqno(), traceid, body);
        return request;
    }

    protected InetSocketAddress nextRemoteAddress() {
        InetSocketAddress addr = sncpRpcGroups.nextRemoteAddress(resourceid);
        if (addr != null) {
            return addr;
        }
        SncpRpcGroup srg = sncpRpcGroups.getSncpRpcGroup(remoteGroup);
        if (srg != null) {
            Set<InetSocketAddress> addrs = srg.getAddresses();
            if (!addrs.isEmpty()) {
                Iterator<InetSocketAddress> it = addrs.iterator();
                if (it.hasNext()) {
                    return it.next();
                }
            }
        }
        throw new SncpException(
                "Not found SocketAddress by remoteGroup = " + remoteGroup + ", resourceid = " + resourceid);
    }

    @Override
    public String toString() {
        InetSocketAddress clientSncpAddress = sncpClient == null ? null : sncpClient.getClientSncpAddress();
        return this.getClass().getSimpleName() + "(service=" + serviceType.getSimpleName() + ", serviceid="
                + serviceid
                + ", serviceVersion=" + serviceVersion + ", name='" + name
                + "', address="
                + (clientSncpAddress == null
                        ? ""
                        : (clientSncpAddress.getHostString() + ":" + clientSncpAddress.getPort()))
                + ", actions.size=" + actions.size() + ")";
    }

    public String toSimpleString() { // 给Sncp产生的Service用
        InetSocketAddress clientSncpAddress = sncpClient == null ? null : sncpClient.getClientSncpAddress();
        return serviceType.getSimpleName() + "(name='" + name + "', serviceid=" + serviceid + ", serviceVersion="
                + serviceVersion
                + ", clientaddr="
                + (clientSncpAddress == null
                        ? ""
                        : (clientSncpAddress.getHostString() + ":" + clientSncpAddress.getPort()))
                + ((remoteGroup == null || remoteGroup.isEmpty()) ? "" : ", remoteGroup=" + remoteGroup)
                + ", actions.size=" + actions.size() + ")";
    }

    public void updateRemoteAddress(String remoteGroup, Set<InetSocketAddress> remoteAddresses) {
        this.remoteGroup = remoteGroup;
        this.remoteAddresses = remoteAddresses;
    }

    public String getName() {
        return name;
    }

    public Class getServiceClass() {
        return serviceType;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public SncpRemoteAction[] getActions() {
        return actions.values().toArray(new SncpRemoteAction[actions.size()]);
    }

    public String getTopic() {
        return topic;
    }

    public String getRemoteGroup() {
        return remoteGroup;
    }

    public Set<InetSocketAddress> getRemoteAddresses() {
        return remoteAddresses;
    }

    public static final class SncpRemoteAction {

        protected final Uint128 actionid;

        protected final Method method;

        protected final Type returnObjectType; // void必须设为null

        protected final Type[] paramTypes;

        protected final Class[] paramClasses;

        protected final int paramHandlerIndex;

        protected final int paramHandlerAttachIndex;

        protected final int paramAddressTargetIndex;

        protected final int paramAddressSourceIndex;

        protected final int paramTopicTargetIndex;

        protected final Class<? extends CompletionHandler> paramHandlerClass; // CompletionHandler参数的类型

        protected final java.lang.reflect.Type paramHandlerResultType; // CompletionHandler.completed第一个参数的类型

        protected final java.lang.reflect.Type returnFutureResultType; // 返回结果的CompletableFuture的结果泛型类型

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
                                        throw new SncpException(
                                                method + " have more than one @RpcTargetAddress parameter");
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
                                        throw new SncpException(
                                                method + " have more than one @RpcSourceAddress parameter");
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
                                        throw new SncpException(
                                                method + " have more than one @RpcTargetTopic parameter");
                                    } else {
                                        tpoicAddrIndex = i;
                                    }
                                } else {
                                    throw new SncpException(
                                            method + " must be String Type on @RpcTargetTopic parameter");
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
                this.returnFutureResultType = returnType;
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
