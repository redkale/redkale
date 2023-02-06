/*
 * To change this license header, choose License Headers reader Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template reader the editor.
 */
package org.redkale.net.sncp;

import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.annotation.Resource;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.mq.*;
import org.redkale.net.*;
import org.redkale.net.sncp.Sncp.SncpDyn;
import static org.redkale.net.sncp.SncpHeader.HEADER_SIZE;
import static org.redkale.net.sncp.SncpRequest.*;
import org.redkale.net.sncp.SncpServiceInfo.SncpServiceAction;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class SncpOldClient {

    protected static final Logger logger = Logger.getLogger(SncpOldClient.class.getSimpleName());

    protected final JsonConvert convert = JsonFactory.root().getConvert();

    protected final String name;

    protected final boolean remote;

    private final Class serviceClass;

    protected final InetSocketAddress clientSncpAddress;

    private final byte[] addrBytes;

    private final int addrPort;

    protected final Uint128 serviceid;

    protected final int serviceVersion;

    protected final SncpServiceAction[] actions;

    protected final MessageAgent messageAgent;

    protected final SncpMessageClient messageClient;

    protected final String topic;

    @Resource
    protected BsonConvert bsonConvert;

    //远程模式, 可能为null
    protected Set<String> remoteGroups;

    //远程模式, 可能为null
    protected Transport remoteGroupTransport;

    public <T extends Service> SncpOldClient(final String serviceResourceName, final Class<T> serviceTypeOrImplClass, final T service, MessageAgent messageAgent, final TransportFactory factory,
        final boolean remote, final Class serviceClass, final InetSocketAddress clientSncpAddress) {
        this.remote = remote;
        this.messageAgent = messageAgent;
        this.messageClient = messageAgent == null ? null : messageAgent.getSncpMessageClient();
        this.topic = messageAgent == null ? null : messageAgent.generateSncpReqTopic(service);
        this.serviceClass = serviceClass;
        this.serviceVersion = 0; //暂不实现Version
        this.clientSncpAddress = clientSncpAddress;
        this.name = serviceResourceName;
        Class<?> serviceResourceType = ResourceFactory.getResourceType(serviceTypeOrImplClass); //serviceResourceType
        this.serviceid = Sncp.serviceid(serviceResourceName, serviceResourceType);
        final List<SncpServiceAction> methodens = new ArrayList<>();
        //------------------------------------------------------------------------------
        for (Map.Entry<Uint128, Method> en : Sncp.loadMethodActions(serviceClass).entrySet()) {
            methodens.add(new SncpServiceAction(serviceClass, en.getValue(), serviceid, en.getKey()));
        }
        this.actions = methodens.toArray(new SncpServiceAction[methodens.size()]);
        this.addrBytes = clientSncpAddress == null ? new byte[4] : clientSncpAddress.getAddress().getAddress();
        this.addrPort = clientSncpAddress == null ? 0 : clientSncpAddress.getPort();
        if (this.addrBytes.length != 4) {
            throw new SncpException("SNCP clientAddress only support IPv4");
        }
    }

    public MessageAgent getMessageAgent() {
        return messageAgent;
    }

    public InetSocketAddress getClientAddress() {
        return clientSncpAddress;
    }

    public Uint128 getServiceid() {
        return serviceid;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public int getActionCount() {
        return actions.length;
    }

    public Set<String> getRemoteGroups() {
        return remoteGroups;
    }

    public void setRemoteGroups(Set<String> remoteGroups) {
        this.remoteGroups = remoteGroups;
    }

    public Transport getRemoteGroupTransport() {
        return remoteGroupTransport;
    }

    public void setRemoteGroupTransport(Transport remoteGroupTransport) {
        this.remoteGroupTransport = remoteGroupTransport;
    }

    @Override
    public String toString() {
        String service = serviceClass.getName();
        if (remote) {
            service = service.replace("DynLocalService", "DynRemoteService");
        }
        return this.getClass().getSimpleName() + "(service = " + service + ", serviceid = " + serviceid + ", serviceVersion = " + serviceVersion + ", name = '" + name
            + "', address = " + (clientSncpAddress == null ? "" : (clientSncpAddress.getHostString() + ":" + clientSncpAddress.getPort()))
            + ", actions.size = " + actions.length + ")";
    }

    public String toSimpleString() { //给Sncp产生的Service用
        if (DataSource.class.isAssignableFrom(serviceClass) || CacheSource.class.isAssignableFrom(serviceClass)) {
            String service = serviceClass.getAnnotation(SncpDyn.class) == null ? serviceClass.getName() : serviceClass.getSuperclass().getSimpleName();
            return service + "(serviceid=" + serviceid + ", name='" + name + "', actions.size=" + actions.length + ")";
        }
        String service = serviceClass.getAnnotation(SncpDyn.class) == null ? serviceClass.getName() : serviceClass.getSuperclass().getSimpleName();
        if (remote) {
            service = service.replace("DynLocalService", "DynRemoteService");
        }
        return service + "(name = '" + name + "', serviceid = " + serviceid + ", serviceVersion = " + serviceVersion
            + ", clientaddr = " + (clientSncpAddress == null ? "" : (clientSncpAddress.getHostString() + ":" + clientSncpAddress.getPort()))
            + ((remoteGroups == null || remoteGroups.isEmpty()) ? "" : ", remoteGroups = " + remoteGroups)
            + (remoteGroupTransport == null ? "" : ", remoteGroupTransport = " + Arrays.toString(remoteGroupTransport.getRemoteAddresses()))
            + ", actions.size = " + actions.length + ")";
    }

    //只给远程模式调用的
    public <T> T remote(final int index, final Object... params) {
        final SncpServiceAction action = actions[index];
        final CompletionHandler handlerFunc = action.handlerFuncParamIndex >= 0 ? (CompletionHandler) params[action.handlerFuncParamIndex] : null;
        if (action.handlerFuncParamIndex >= 0) {
            params[action.handlerFuncParamIndex] = null;
        }
        final BsonReader reader = bsonConvert.pollBsonReader();
        CompletableFuture<byte[]> future = remote0(handlerFunc, remoteGroupTransport, null, action, params);
        if (action.boolReturnTypeFuture) { //与handlerFuncIndex互斥
            CompletableFuture result = action.futureCreator.create();
            future.whenComplete((v, e) -> {
                try {
                    if (e != null) {
                        result.completeExceptionally(e);
                    } else {
                        reader.setBytes(v);
                        byte i;
                        while ((i = reader.readByte()) != 0) {
                            final Attribute attr = action.paramAttrs[i];
                            attr.set(params[i - 1], bsonConvert.convertFrom(attr.genericType(), reader));
                        }
                        Object rs = bsonConvert.convertFrom(Object.class, reader);

                        result.complete(rs);
                    }
                } catch (Exception exp) {
                    result.completeExceptionally(exp);
                } finally {
                    bsonConvert.offerBsonReader(reader);
                }
            }); //需要获取  Executor
            return (T) result;
        }
        if (handlerFunc != null) {
            return null;
        }
        try {
            reader.setBytes(future.get(5, TimeUnit.SECONDS));
            byte i;
            while ((i = reader.readByte()) != 0) {
                final Attribute attr = action.paramAttrs[i];
                attr.set(params[i - 1], bsonConvert.convertFrom(attr.genericType(), reader));
            }
            return bsonConvert.convertFrom(action.handlerFuncParamIndex >= 0 ? Object.class : action.resultTypes, reader);
        } catch (RpcRemoteException re) {
            throw re;
        } catch (TimeoutException e) {
            throw new RpcRemoteException(actions[index].method + " sncp remote timeout, params=" + JsonConvert.root().convertTo(params));
        } catch (InterruptedException | ExecutionException e) {
            throw new RpcRemoteException(actions[index].method + " sncp remote error, params=" + JsonConvert.root().convertTo(params), e);
        } finally {
            bsonConvert.offerBsonReader(reader);
        }
    }

    private CompletableFuture<byte[]> remote0(final CompletionHandler handler, final Transport transport, final SocketAddress addr0, final SncpServiceAction action, final Object... params) {
        final String traceid = Traces.currTraceid();
        final Type[] myparamtypes = action.paramTypes;
        final Class[] myparamclass = action.paramClass;
        if (action.addressSourceParamIndex >= 0) {
            params[action.addressSourceParamIndex] = this.clientSncpAddress;
        }
        if (bsonConvert == null) {
            bsonConvert = BsonConvert.root();
        }
        final BsonWriter writer = bsonConvert.pollBsonWriter(); // 将head写入
        writer.writeTo(DEFAULT_HEADER);
        for (int i = 0; i < params.length; i++) {  //params 可能包含: 3 个 boolean
            BsonConvert bcc = bsonConvert;
            if (params[i] instanceof org.redkale.service.RetResult) {
                org.redkale.convert.Convert cc = ((org.redkale.service.RetResult) params[i]).convert();
                if (cc instanceof BsonConvert) {
                    bcc = (BsonConvert) cc;
                }
            }
            bcc.convertTo(writer, CompletionHandler.class.isAssignableFrom(myparamclass[i]) ? CompletionHandler.class : myparamtypes[i], params[i]);
        }
        final int reqBodyLength = writer.count() - HEADER_SIZE; //body总长度
        final long seqid = System.nanoTime();
        final Uint128 actionid = action.actionid;
        if (messageAgent != null) { //MQ模式
            final ByteArray reqbytes = writer.toByteArray();
            fillHeader(reqbytes, action, seqid, traceid, reqBodyLength);
            String targetTopic = action.topicTargetParamIndex >= 0 ? (String) params[action.topicTargetParamIndex] : this.topic;
            if (targetTopic == null) {
                targetTopic = this.topic;
            }
            MessageRecord message = messageClient.createMessageRecord(targetTopic, null, reqbytes.getBytes());
            final String tt = targetTopic;
            if (logger.isLoggable(Level.FINER)) {
                message.attach(Utility.append(new Object[]{action.actionName()}, params));
            } else {
                message.attach(params);
            }
            return messageClient.sendMessage(message).thenApply(msg -> {
                if (msg == null || msg.getContent() == null) {
                    logger.log(Level.SEVERE, action.method + " sncp mq(params: " + convert.convertTo(params) + ", message: " + message + ") deal error, this.topic = " + this.topic + ", targetTopic = " + tt + ", result = " + msg);
                    return null;
                }
                ByteBuffer buffer = ByteBuffer.wrap(msg.getContent());
                checkResult(seqid, action, buffer);

                final int respBodyLength = buffer.getInt();
                final int retcode = buffer.getInt();
                if (retcode != 0) {
                    logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + "), params=" + JsonConvert.root().convertTo(params));
                    throw new SncpException("remote service(" + action.method + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + ")");
                }
                byte[] body = new byte[respBodyLength];
                buffer.get(body, 0, respBodyLength);
                return body;
            });
        }
        final SocketAddress addr = addr0 == null ? (action.addressTargetParamIndex >= 0 ? (SocketAddress) params[action.addressTargetParamIndex] : null) : addr0;
        CompletableFuture<AsyncConnection> connFuture = transport.pollConnection(addr);
        return connFuture.thenCompose(conn0 -> {
            final CompletableFuture<byte[]> future = new CompletableFuture();
            if (conn0 == null) {
                future.completeExceptionally(new RpcRemoteException("sncp " + (conn0 == null ? addr : conn0.getRemoteAddress()) + " cannot connect, params=" + JsonConvert.root().convertTo(params)));
                return future;
            }
            if (!conn0.isOpen()) {
                conn0.dispose();
                future.completeExceptionally(new RpcRemoteException("sncp " + conn0.getRemoteAddress() + " cannot connect, params=" + JsonConvert.root().convertTo(params)));
                return future;
            }
            final AsyncConnection conn = conn0;
            final ByteArray array = writer.toByteArray();
            fillHeader(array, action, seqid, traceid, reqBodyLength);
            conn.write(array, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachments) {
                    //----------------------- 读取返回结果 -------------------------------------     
                    conn.read(new CompletionHandler<Integer, ByteBuffer>() {

                        private byte[] body;

                        private int received;

                        @Override
                        public void completed(Integer count, ByteBuffer buffer) {
                            try {
                                if (count < 1 && buffer.remaining() == buffer.limit()) {   //没有数据可读
                                    future.completeExceptionally(new RpcRemoteException(action.method + " sncp[" + conn.getRemoteAddress() + "] remote no response data, params=" + JsonConvert.root().convertTo(params)));
                                    conn.offerReadBuffer(buffer);
                                    transport.offerConnection(true, conn);
                                    return;
                                }
                                if (received < 1 && buffer.limit() < buffer.remaining() + HEADER_SIZE) { //header都没读全
                                    conn.setReadBuffer(buffer);
                                    conn.read(this);
                                    return;
                                }
                                buffer.flip();
                                if (received > 0) {
                                    int offset = this.received;
                                    this.received += buffer.remaining();
                                    buffer.get(body, offset, Math.min(buffer.remaining(), this.body.length - offset));
                                    if (this.received < this.body.length) {// 数据仍然不全，需要继续读取          
                                        buffer.clear();
                                        conn.setReadBuffer(buffer);
                                        conn.read(this);
                                    } else {
                                        conn.offerReadBuffer(buffer);
                                        success();
                                    }
                                    return;
                                }
                                checkResult(seqid, action, buffer);

                                final int respBodyLength = buffer.getInt();
                                final int retcode = buffer.getInt();
                                if (retcode != 0) {
                                    logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + "), params=" + JsonConvert.root().convertTo(params));
                                    throw new SncpException("remote service(" + action.method + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + ")");
                                }

                                if (respBodyLength > buffer.remaining()) { // 数据不全，需要继续读取
                                    this.body = new byte[respBodyLength];
                                    this.received = buffer.remaining();
                                    buffer.get(body, 0, this.received);
                                    buffer.clear();
                                    conn.setReadBuffer(buffer);
                                    conn.read(this);
                                } else {
                                    this.body = new byte[respBodyLength];
                                    buffer.get(body, 0, respBodyLength);
                                    conn.offerReadBuffer(buffer);
                                    success();
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                                future.completeExceptionally(new RpcRemoteException(action.method + " sncp[" + conn.getRemoteAddress() + "] remote response error, params=" + JsonConvert.root().convertTo(params)));
                                transport.offerConnection(true, conn);
                                if (handler != null) {
                                    final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                                    handler.failed(e, handlerAttach);
                                }
                                logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") deal error", e);
                            }
                        }

                        @SuppressWarnings("unchecked")
                        public void success() {
                            future.complete(this.body);
                            transport.offerConnection(false, conn);
                            if (handler != null) {
                                final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                                final BsonReader reader = bsonConvert.pollBsonReader();
                                try {
                                    reader.setBytes(this.body);
                                    int i;
                                    while ((i = (reader.readByte() & 0xff)) != 0) {
                                        final Attribute attr = action.paramAttrs[i];
                                        attr.set(params[i - 1], bsonConvert.convertFrom(attr.genericType(), reader));
                                    }
                                    Object rs = bsonConvert.convertFrom(action.handlerFuncParamIndex >= 0 ? Object.class : action.resultTypes, reader);
                                    handler.completed(rs, handlerAttach);
                                } catch (Exception e) {
                                    handler.failed(e, handlerAttach);
                                } finally {
                                    bsonConvert.offerBsonReader(reader);
                                }
                            }
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer attachment2) {
                            future.completeExceptionally(new RpcRemoteException(action.method + " sncp remote exec failed, params=" + JsonConvert.root().convertTo(params)));
                            conn.offerReadBuffer(attachment2);
                            transport.offerConnection(true, conn);
                            if (handler != null) {
                                final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                                handler.failed(exc, handlerAttach);
                            }
                            logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") remote read exec failed, params=" + JsonConvert.root().convertTo(params), exc);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    future.completeExceptionally(new RpcRemoteException(action.method + " sncp remote exec failed, params=" + JsonConvert.root().convertTo(params)));
                    transport.offerConnection(true, conn);
                    if (handler != null) {
                        final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                        handler.failed(exc, handlerAttach);
                    }
                    logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") remote write exec failed, params=" + JsonConvert.root().convertTo(params), exc);
                }
            });
            return future;
        });
    }

    private void checkResult(long seqid, final SncpServiceAction action, ByteBuffer buffer) {
        long rseqid = buffer.getLong();
        if (rseqid != seqid) {
            throw new SncpException("sncp(" + action.method + ") response.seqid = " + seqid + ", but request.seqid =" + rseqid);
        }
        if (buffer.getChar() != HEADER_SIZE) {
            throw new SncpException("sncp(" + action.method + ") buffer receive header.length not " + HEADER_SIZE);
        }
        Uint128 rserviceid = Uint128.read(buffer);
        if (!rserviceid.equals(this.serviceid)) {
            throw new SncpException("sncp(" + action.method + ") response.serviceid = " + serviceid + ", but request.serviceid =" + rserviceid);
        }
        int version = buffer.getInt();
        if (version != this.serviceVersion) {
            throw new SncpException("sncp(" + action.method + ") response.serviceVersion = " + serviceVersion + ", but request.serviceVersion =" + version);
        }
        Uint128 raction = Uint128.read(buffer);
        Uint128 actid = action.actionid;
        if (!actid.equals(raction)) {
            throw new SncpException("sncp(" + action.method + ") response.actionid = " + action.actionid + ", but request.actionid =(" + raction + ")");
        }
        buffer.getInt();  //地址
        buffer.getChar(); //端口
    }

    private void fillHeader(ByteArray buffer, SncpServiceAction action, long seqid, String traceid, int bodyLength) {
        action.header.writeTo(buffer, addrBytes, addrPort, seqid, bodyLength, 0); //结果码， 请求方固定传0  
    }

}
