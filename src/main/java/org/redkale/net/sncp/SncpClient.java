/*
 *
 */
package org.redkale.net.sncp;

import java.lang.reflect.Type;
import java.net.*;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.*;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.net.*;
import org.redkale.net.client.*;
import org.redkale.net.sncp.SncpRemoteInfo.SncpRemoteAction;
import org.redkale.util.*;

/**
 * SNCP版Client, 一个SncpServer只能对应一个SncpClient
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    private final InetSocketAddress clientSncpAddress;

    public SncpClient(String name, AsyncGroup group, InetSocketAddress clientSncpAddress, ClientAddress address, String netprotocol, int maxConns, int maxPipelines) {
        super(name, group, "TCP".equalsIgnoreCase(netprotocol), address, maxConns, maxPipelines, null, null, null); //maxConns
        this.clientSncpAddress = clientSncpAddress;
    }

    @Override
    public SncpClientConnection createClientConnection(int index, AsyncConnection channel) {
        return new SncpClientConnection(this, index, channel);
    }

    public InetSocketAddress getClientSncpAddress() {
        return clientSncpAddress;
    }

    @Override
    protected CompletableFuture<SncpClientConnection> connect(SocketAddress addr) {
        return super.connect(addr);
    }

    //只给远程模式调用的
    public <T> T remote(final SncpRemoteInfo info, final int index, final Object[] params) {
        final Convert convert = info.convert;
        final SncpRemoteAction action = info.actions[index];
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
        final CompletableFuture<byte[]> future = remote(info, action, convert, Traces.currTraceid(), params);
        if (action.paramHandlerIndex >= 0) { //参数中存在CompletionHandler
            final CompletionHandler handler = callbackHandler;
            final Object attach = callbackHandlerAttach;
            if (handler == null) { //传入的CompletionHandler参数为null
                future.join();
            } else {
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        //v,length-1为了读掉(byte)0
                        handler.completed(v == null ? null : convert.convertFrom(action.paramHandlerResultType, v, 1, v.length - 1), attach);
                    } else {
                        handler.failed(t, attach);
                    }
                });
            }
        } else if (action.returnFutureClass != null) { //返回类型为CompletableFuture
            if (action.returnFutureClass == CompletableFuture.class) {
                //v,length-1为了读掉(byte)0
                return (T) future.thenApply(v -> v == null ? null : convert.convertFrom(action.paramHandlerResultType, v, 1, v.length - 1));
            } else {
                final CompletableFuture returnFuture = action.returnFutureCreator.create();
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        //v,length-1为了读掉(byte)0
                        returnFuture.complete(v == null ? null : convert.convertFrom(action.paramHandlerResultType, v, 1, v.length - 1));
                    } else {
                        returnFuture.completeExceptionally(t);
                    }
                });
                return (T) returnFuture;
            }
        } else if (action.returnObjectType != null) { //返回类型为JavaBean
            //v,length-1为了读掉(byte)0
            return (T) future.thenApply(v -> v == null ? null : convert.convertFrom(action.returnObjectType, v, 1, v.length - 1)).join();
        } else { //返回类型为void
            future.join();
        }
        return null;
    }

    private CompletableFuture<byte[]> remote(
        final SncpRemoteInfo info,
        final SncpRemoteAction action,
        final Convert convert,
        final String traceid,
        final Object[] params) {
        final Type[] myParamTypes = action.paramTypes;
        final Class[] myParamClass = action.paramClasses;
        if (action.paramAddressSourceIndex >= 0) {
            params[action.paramAddressSourceIndex] = this.clientSncpAddress;
        }
        final long seqid = System.nanoTime();
        final SncpClientRequest requet = new SncpClientRequest();
        Writer writer = null;
        if (myParamTypes.length > 0) {
            writer = convert.pollWriter();
            for (int i = 0; i < params.length; i++) { //service方法的参数
                Convert bcc = convert;
                if (params[i] instanceof org.redkale.service.RetResult) {
                    org.redkale.convert.Convert cc = ((org.redkale.service.RetResult) params[i]).convert();
                    if (cc instanceof BsonConvert) {
                        bcc = (BsonConvert) cc;
                    }
                }
                bcc.convertTo(writer, CompletionHandler.class.isAssignableFrom(myParamClass[i]) ? CompletionHandler.class : myParamTypes[i], params[i]);
            }
        }
        requet.prepare(action.header, seqid, traceid, (ByteTuple) writer);
        final SocketAddress addr = action.paramAddressTargetIndex >= 0 ? (SocketAddress) params[action.paramAddressTargetIndex] : info.nextRemoteAddress();
        return super.connect(addr).thenCompose(conn -> writeChannel(conn, requet).thenApply(rs -> rs.getBodyContent()));
    }
}
