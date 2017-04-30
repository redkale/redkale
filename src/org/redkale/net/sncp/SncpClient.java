/*
 * To change this license header, choose License Headers reader Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template reader the editor.
 */
package org.redkale.net.sncp;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.net.*;
import static org.redkale.net.sncp.SncpRequest.*;
import org.redkale.service.*;
import org.redkale.util.*;
import org.redkale.service.RpcCall;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class SncpClient {

    protected static final class SncpAction {

        protected final DLong actionid;

        protected final Method method;

        protected final Type resultTypes;  //void 必须设为 null

        protected final Type[] paramTypes;

        protected final Class[] paramClass;

        protected final Attribute[] paramAttrs; // 为null表示无RpcCall处理，index=0固定为null, 其他为参数标记的RpcCall回调方法

        protected final int handlerFuncParamIndex;

        protected final int handlerAttachParamIndex;

        protected final int addressTargetParamIndex;

        protected final int addressSourceParamIndex;

        protected final boolean boolReturnTypeFuture; // 返回结果类型是否为 CompletableFuture

        protected final Creator<? extends CompletableFuture> futureCreator;

        public SncpAction(final Class clazz, Method method, DLong actionid) {
            this.actionid = actionid == null ? Sncp.hash(method) : actionid;
            Type rt = method.getGenericReturnType();
            if (rt instanceof TypeVariable) {
                TypeVariable tv = (TypeVariable) rt;
                if (tv.getBounds().length == 1) rt = tv.getBounds()[0];
            }
            this.resultTypes = rt == void.class ? null : rt;
            this.boolReturnTypeFuture = CompletableFuture.class.isAssignableFrom(method.getReturnType());
            this.futureCreator = boolReturnTypeFuture ? Creator.create((Class<? extends CompletableFuture>) method.getReturnType()) : null;
            this.paramTypes = method.getGenericParameterTypes();
            this.paramClass = method.getParameterTypes();
            this.method = method;
            Annotation[][] anns = method.getParameterAnnotations();
            int targetAddrIndex = -1;
            int sourceAddrIndex = -1;
            int handlerAttachIndex = -1;
            int handlerFuncIndex = -1;
            boolean hasattr = false;
            Attribute[] atts = new Attribute[paramTypes.length + 1];
            if (anns.length > 0) {
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (AsyncHandler.class.isAssignableFrom(params[i])) {
                        if (boolReturnTypeFuture) {
                            throw new RuntimeException(method + " have both AsyncHandler and CompletableFuture");
                        }
                        if (handlerFuncIndex >= 0) {
                            throw new RuntimeException(method + " have more than one AsyncHandler type parameter");
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
                                    throw new RuntimeException(method + " have more than one @RpcAttachment parameter");
                                }
                                handlerAttachIndex = i;
                            } else if (ann.annotationType() == RpcTargetAddress.class && SocketAddress.class.isAssignableFrom(params[i])) {
                                targetAddrIndex = i;
                            } else if (ann.annotationType() == RpcSourceAddress.class && SocketAddress.class.isAssignableFrom(params[i])) {
                                sourceAddrIndex = i;
                            }
                        }
                        for (Annotation ann : anns[i]) {
                            if (ann.annotationType() == RpcCall.class) {
                                try {
                                    atts[i + 1] = ((RpcCall) ann).value().newInstance();
                                    hasattr = true;
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, RpcCall.class.getSimpleName() + ".attribute cannot a newInstance for" + method, e);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            this.addressTargetParamIndex = targetAddrIndex;
            this.addressSourceParamIndex = sourceAddrIndex;
            this.handlerFuncParamIndex = handlerFuncIndex;
            this.handlerAttachParamIndex = handlerAttachIndex;
            this.paramAttrs = hasattr ? atts : null;
            if (this.handlerFuncParamIndex >= 0 && method.getReturnType() != void.class) {
                throw new RuntimeException(method + " have AsyncHandler type parameter but return type is not void");
            }
        }

        @Override
        public String toString() {
            return "{" + actionid + "," + (method == null ? "null" : method.getName()) + "}";
        }
    }

    protected static final Logger logger = Logger.getLogger(SncpClient.class.getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    protected final JsonConvert convert = JsonFactory.root().getConvert();

    protected final String name;

    protected final boolean remote;

    private final Class serviceClass;

    protected final InetSocketAddress clientAddress;

    private final byte[] addrBytes;

    private final int addrPort;

    protected final DLong serviceid;

    protected final int serviceversion;

    protected final SncpAction[] actions;

    protected final Consumer<Runnable> executor;

    public <T extends Service> SncpClient(final String serviceName, final Class<T> serviceTypeOrImplClass, final T service, final Consumer<Runnable> executor,
        final boolean remote, final Class serviceClass, final InetSocketAddress clientAddress) {
        this.remote = remote;
        this.executor = executor;
        this.serviceClass = serviceClass;
        this.serviceversion = 0;
        this.clientAddress = clientAddress;
        this.name = serviceName;
        Class tn = serviceTypeOrImplClass;
        ResourceType rt = (ResourceType) tn.getAnnotation(ResourceType.class);
        if (rt != null && rt.value().length > 0) tn = rt.value()[0];
        this.serviceid = Sncp.hash(tn.getName() + ':' + serviceName);
        final List<SncpAction> methodens = new ArrayList<>();
        //------------------------------------------------------------------------------
        for (java.lang.reflect.Method method : parseMethod(serviceClass)) {
            methodens.add(new SncpAction(serviceClass, method, Sncp.hash(method)));
        }
        this.actions = methodens.toArray(new SncpAction[methodens.size()]);
        this.addrBytes = clientAddress == null ? new byte[4] : clientAddress.getAddress().getAddress();
        this.addrPort = clientAddress == null ? 0 : clientAddress.getPort();
    }

    static List<SncpAction> getSncpActions(final Class serviceClass) {
        final List<SncpAction> actions = new ArrayList<>();
        //------------------------------------------------------------------------------
        for (java.lang.reflect.Method method : parseMethod(serviceClass)) {
            actions.add(new SncpAction(serviceClass, method, Sncp.hash(method)));
        }
        return actions;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public DLong getServiceid() {
        return serviceid;
    }

    public int getServiceversion() {
        return serviceversion;
    }

    public int getActionCount() {
        return actions.length;
    }

    @Override
    public String toString() {
        String service = serviceClass.getName();
        if (remote) service = service.replace(Sncp.LOCALPREFIX, Sncp.REMOTEPREFIX);
        return this.getClass().getSimpleName() + "(service = " + service + ", serviceid = " + serviceid + ", serviceversion = " + serviceversion + ", name = '" + name
            + "', address = " + (clientAddress == null ? "" : (clientAddress.getHostString() + ":" + clientAddress.getPort()))
            + ", actions.size = " + actions.length + ")";
    }

    public static List<Method> parseMethod(final Class serviceClass) {
        final List<Method> list = new ArrayList<>();
        final List<Method> multis = new ArrayList<>();
        final Map<DLong, Method> actionids = new HashMap<>();

        for (final java.lang.reflect.Method method : serviceClass.getMethods()) {
            if (method.isSynthetic()) continue;
            final int mod = method.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            if (Modifier.isFinal(mod)) continue;
            if (method.getName().equals("getClass") || method.getName().equals("toString")) continue;
            if (method.getName().equals("equals") || method.getName().equals("hashCode")) continue;
            if (method.getName().equals("notify") || method.getName().equals("notifyAll") || method.getName().equals("wait")) continue;
            if (method.getName().equals("init") || method.getName().equals("destroy")) continue;
            //if (method.getName().equals("version") || method.getName().equals("name")) continue;
            //if (onlySncpDyn && method.getAnnotation(SncpDyn.class) == null) continue;
            DLong actionid = Sncp.hash(method);
            Method old = actionids.get(actionid);
            if (old != null) {
                if (old.getDeclaringClass().equals(method.getDeclaringClass()))
                    throw new RuntimeException(serviceClass.getName() + " have one more same action(Method=" + method + ", " + old + ", actionid=" + actionid + ")");
                continue;
            }
            actionids.put(actionid, method);
            if (method.getAnnotation(SncpDyn.class) != null) {
                multis.add(method);
            } else {
                list.add(method);
            }
        }
        multis.sort((m1, m2) -> m1.getAnnotation(SncpDyn.class).index() - m2.getAnnotation(SncpDyn.class).index());
        list.sort((Method o1, Method o2) -> {
            if (!o1.getName().equals(o2.getName())) return o1.getName().compareTo(o2.getName());
            if (o1.getParameterCount() != o2.getParameterCount()) return o1.getParameterCount() - o2.getParameterCount();
            return 0;
        });
        //带SncpDyn必须排在前面
        multis.addAll(list);
        return multis;
    }

    public void remoteSameGroup(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport transport, final int index, final Object... params) {
        if (transport == null) return;
        final SncpAction action = actions[index];
        if (action.handlerFuncParamIndex >= 0) params[action.handlerFuncParamIndex] = null; //不能让远程调用handler，因为之前本地方法已经调用过了
        for (InetSocketAddress addr : transport.getRemoteAddresses()) {
            remote0(null, bsonConvert, jsonConvert, transport, addr, action, params);
        }
    }

    public void asyncRemoteSameGroup(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport transport, final int index, final Object... params) {
        if (transport == null) return;
        if (executor != null) {
            executor.accept(() -> {
                remoteSameGroup(bsonConvert, jsonConvert, transport, index, params);
            });
        } else {
            remoteSameGroup(bsonConvert, jsonConvert, transport, index, params);
        }
    }

    public void remoteDiffGroup(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport[] transports, final int index, final Object... params) {
        if (transports == null || transports.length < 1) return;
        final SncpAction action = actions[index];
        if (action.handlerFuncParamIndex >= 0) params[action.handlerFuncParamIndex] = null; //不能让远程调用handler，因为之前本地方法已经调用过了
        for (Transport transport : transports) {
            remote0(null, bsonConvert, jsonConvert, transport, null, action, params);
        }
    }

    public void asyncRemoteDiffGroup(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport[] transports, final int index, final Object... params) {
        if (transports == null || transports.length < 1) return;
        if (executor != null) {
            executor.accept(() -> {
                remoteDiffGroup(bsonConvert, jsonConvert, transports, index, params);
            });
        } else {
            remoteDiffGroup(bsonConvert, jsonConvert, transports, index, params);
        }
    }

    //只给远程模式调用的
    public <T> T remote(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport transport, final int index, final Object... params) {
        final SncpAction action = actions[index];
        final AsyncHandler handlerFunc = action.handlerFuncParamIndex >= 0 ? (AsyncHandler) params[action.handlerFuncParamIndex] : null;
        if (action.handlerFuncParamIndex >= 0) params[action.handlerFuncParamIndex] = null;
        final BsonReader reader = bsonConvert.pollBsonReader();
        CompletableFuture<byte[]> future = remote0(handlerFunc, bsonConvert, jsonConvert, transport, null, action, params);
        if (action.boolReturnTypeFuture) {
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
                            attr.set(params[i - 1], bsonConvert.convertFrom(attr.type(), reader));
                        }
                        Object rs = bsonConvert.convertFrom(Object.class, reader);

                        result.complete(rs);
                    }
                } finally {
                    bsonConvert.offerBsonReader(reader);
                }
            }); //需要获取  Executor
            return (T) result;
        }
        if (handlerFunc != null) return null;
        try {
            reader.setBytes(future.get(5, TimeUnit.SECONDS));
            byte i;
            while ((i = reader.readByte()) != 0) {
                final Attribute attr = action.paramAttrs[i];
                attr.set(params[i - 1], bsonConvert.convertFrom(attr.type(), reader));
            }
            return bsonConvert.convertFrom(action.handlerFuncParamIndex >= 0 ? Object.class : action.resultTypes, reader);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.log(Level.SEVERE, actions[index].method + " sncp (params: " + jsonConvert.convertTo(params) + ") remote error", e);
            throw new RuntimeException(actions[index].method + " sncp remote error", e);
        } finally {
            bsonConvert.offerBsonReader(reader);
        }
    }

    public <T> void remote(final BsonConvert bsonConvert, final JsonConvert jsonConvert, Transport[] transports, final int index, final Object... params) {
        if (transports == null || transports.length < 1) return;
        remote(bsonConvert, jsonConvert, transports[0], index, params);
        for (int i = 1; i < transports.length; i++) {
            remote0(null, bsonConvert, jsonConvert, transports[i], null, actions[index], params);
        }
    }

    private CompletableFuture<byte[]> remote0(final AsyncHandler handler, final BsonConvert bsonConvert, final JsonConvert jsonConvert, final Transport transport, final SocketAddress addr0, final SncpAction action, final Object... params) {
        if ("rest".equalsIgnoreCase(transport.getSubprotocol())) {
            return remoteRest0(handler, jsonConvert, transport, addr0, action, params);
        }
        return remoteSncp0(handler, bsonConvert, transport, addr0, action, params);
    }

    //尚未实现
    private CompletableFuture<byte[]> remoteRest0(final AsyncHandler handler, final JsonConvert jsonConvert, final Transport transport, final SocketAddress addr0, final SncpAction action, final Object... params) {
        return null;
    }

    private CompletableFuture<byte[]> remoteSncp0(final AsyncHandler handler, final BsonConvert bsonConvert, final Transport transport, final SocketAddress addr0, final SncpAction action, final Object... params) {
        final Type[] myparamtypes = action.paramTypes;
        final Class[] myparamclass = action.paramClass;
        if (action.addressSourceParamIndex >= 0) params[action.addressSourceParamIndex] = this.clientAddress;
        final BsonWriter writer = bsonConvert.pollBsonWriter(transport.getBufferSupplier()); // 将head写入
        writer.writeTo(DEFAULT_HEADER);
        for (int i = 0; i < params.length; i++) {  //params 可能包含: 3 个 boolean
            bsonConvert.convertTo(writer, AsyncHandler.class.isAssignableFrom(myparamclass[i]) ? AsyncHandler.class : myparamtypes[i], params[i]);
        }
        final int reqBodyLength = writer.count() - HEADER_SIZE; //body总长度
        final long seqid = System.nanoTime();
        final DLong actionid = action.actionid;
        final SocketAddress addr = addr0 == null ? (action.addressTargetParamIndex >= 0 ? (SocketAddress) params[action.addressTargetParamIndex] : null) : addr0;
        final AsyncConnection conn = transport.pollConnection(addr);
        if (conn == null || !conn.isOpen()) {
            logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") cannot connect " + (conn == null ? addr : conn.getRemoteAddress()));
            throw new RuntimeException("sncp " + (conn == null ? addr : conn.getRemoteAddress()) + " cannot connect");
        }
        final ByteBuffer[] sendBuffers = writer.toBuffers();
        fillHeader(sendBuffers[0], seqid, actionid, reqBodyLength);

        final ByteBuffer buffer = transport.pollBuffer();
        final CompletableFuture<byte[]> future = new CompletableFuture();
        conn.write(sendBuffers, sendBuffers, new CompletionHandler<Integer, ByteBuffer[]>() {

            @Override
            public void completed(Integer result, ByteBuffer[] attachments) {
                int index = -1;
                for (int i = 0; i < attachments.length; i++) {
                    if (attachments[i].hasRemaining()) {
                        index = i;
                        break;
                    } else {
                        transport.offerBuffer(attachments[i]);
                    }
                }
                if (index == 0) {
                    conn.write(attachments, attachments, this);
                    return;
                } else if (index > 0) {
                    ByteBuffer[] newattachs = new ByteBuffer[attachments.length - index];
                    System.arraycopy(attachments, index, newattachs, 0, newattachs.length);
                    conn.write(newattachs, newattachs, this);
                    return;
                }
                //----------------------- 读取返回结果 -------------------------------------
                buffer.clear();
                conn.read(buffer, null, new CompletionHandler<Integer, Void>() {

                    private byte[] body;

                    private int received;

                    @Override
                    public void completed(Integer count, Void attachment2) {
                        if (count < 1 && buffer.remaining() == buffer.limit()) {   //没有数据可读
                            future.completeExceptionally(new RuntimeException(action.method + " sncp[" + conn.getRemoteAddress() + "] remote no response data"));
                            transport.offerBuffer(buffer);
                            transport.offerConnection(true, conn);
                            return;
                        }
                        if (received < 1 && buffer.limit() < buffer.remaining() + HEADER_SIZE) { //header都没读全
                            conn.read(buffer, attachment2, this);
                            return;
                        }
                        buffer.flip();
                        if (received > 0) {
                            int offset = this.received;
                            this.received += buffer.remaining();
                            buffer.get(body, offset, Math.min(buffer.remaining(), this.body.length - offset));
                            if (this.received < this.body.length) {// 数据仍然不全，需要继续读取          
                                buffer.clear();
                                conn.read(buffer, attachment2, this);
                            } else {
                                success();
                            }
                            return;
                        }
                        checkResult(seqid, action, buffer);

                        final int respBodyLength = buffer.getInt();
                        final int retcode = buffer.getInt();
                        if (retcode != 0) {
                            logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + ")");
                            throw new RuntimeException("remote service(" + action.method + ") deal error (retcode=" + retcode + ", retinfo=" + SncpResponse.getRetCodeInfo(retcode) + ")");
                        }

                        if (respBodyLength > buffer.remaining()) { // 数据不全，需要继续读取
                            this.body = new byte[respBodyLength];
                            this.received = buffer.remaining();
                            buffer.get(body, 0, this.received);
                            buffer.clear();
                            conn.read(buffer, attachment2, this);
                        } else {
                            this.body = new byte[respBodyLength];
                            buffer.get(body, 0, respBodyLength);
                            success();
                        }
                    }

                    public void success() {
                        future.complete(this.body);
                        transport.offerBuffer(buffer);
                        transport.offerConnection(false, conn);
                        if (handler != null) {
                            final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                            final BsonReader reader = bsonConvert.pollBsonReader();
                            try {
                                reader.setBytes(this.body);
                                int i;
                                while ((i = (reader.readByte() & 0xff)) != 0) {
                                    final Attribute attr = action.paramAttrs[i];
                                    attr.set(params[i - 1], bsonConvert.convertFrom(attr.type(), reader));
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
                    public void failed(Throwable exc, Void attachment2) {
                        logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") remote read exec failed", exc);
                        future.completeExceptionally(new RuntimeException(action.method + " sncp remote exec failed"));
                        transport.offerBuffer(buffer);
                        transport.offerConnection(true, conn);
                        if (handler != null) {
                            final Object handlerAttach = action.handlerAttachParamIndex >= 0 ? params[action.handlerAttachParamIndex] : null;
                            handler.failed(exc, handlerAttach);
                        }
                    }
                });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer[] attachment) {
                logger.log(Level.SEVERE, action.method + " sncp (params: " + convert.convertTo(params) + ") remote write exec failed", exc);
                transport.offerBuffer(buffer);
                transport.offerConnection(true, conn);
            }
        });
        return future;
    }

    private void checkResult(long seqid, final SncpAction action, ByteBuffer buffer) {
        long rseqid = buffer.getLong();
        if (rseqid != seqid) throw new RuntimeException("sncp(" + action.method + ") response.seqid = " + seqid + ", but request.seqid =" + rseqid);
        if (buffer.getChar() != HEADER_SIZE) throw new RuntimeException("sncp(" + action.method + ") buffer receive header.length not " + HEADER_SIZE);
        DLong rserviceid = DLong.read(buffer);
        if (!rserviceid.equals(this.serviceid)) throw new RuntimeException("sncp(" + action.method + ") response.serviceid = " + serviceid + ", but request.serviceid =" + rserviceid);
        int version = buffer.getInt();
        if (version != this.serviceversion) throw new RuntimeException("sncp(" + action.method + ") response.serviceversion = " + serviceversion + ", but request.serviceversion =" + version);
        DLong raction = DLong.read(buffer);
        DLong actid = action.actionid;
        if (!actid.equals(raction)) throw new RuntimeException("sncp(" + action.method + ") response.actionid = " + action.actionid + ", but request.actionid =(" + raction + ")");
        buffer.getInt();  //地址
        buffer.getChar(); //端口
    }

    private void fillHeader(ByteBuffer buffer, long seqid, DLong actionid, int bodyLength) {
        //---------------------head----------------------------------
        final int currentpos = buffer.position();
        buffer.position(0);
        buffer.putLong(seqid); //序列号
        buffer.putChar((char) HEADER_SIZE); //header长度
        DLong.write(buffer, this.serviceid);
        buffer.putInt(this.serviceversion);
        DLong.write(buffer, actionid);
        buffer.put(addrBytes);
        buffer.putChar((char) this.addrPort);
        buffer.putInt(bodyLength); //body长度        
        buffer.putInt(0); //结果码， 请求方固定传0
        buffer.position(currentpos);
    }

}
