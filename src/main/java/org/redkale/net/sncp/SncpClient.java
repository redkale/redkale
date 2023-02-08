/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.Resource;
import org.redkale.convert.Convert;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.net.*;
import org.redkale.net.client.*;
import org.redkale.net.sncp.SncpServiceInfo.SncpServiceAction;
import org.redkale.util.Traces;

/**
 * SNCP版Client
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

    @Resource
    protected BsonConvert bsonConvert;

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

    protected CompletableFuture<SncpClientConnection> connect(SncpServiceInfo info) {
        return super.connect();
    }

    //只给远程模式调用的
    public <T> T remote(final SncpServiceInfo info, final int index, final Object... params) {
        final String traceid = Traces.currTraceid();
        final Convert convert = info.convert;
        final SncpServiceAction action = info.actions[index];
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
        final CompletableFuture<byte[]> future = remote(info, action, traceid, params);
        if (action.paramHandlerIndex >= 0) { //参数中存在CompletionHandler
            final CompletionHandler handler = callbackHandler;
            final Object attach = callbackHandlerAttach;
            if (handler == null) { //传入的CompletionHandler参数为null
                future.join();
            } else {
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        handler.completed(v == null ? null : convert.convertFrom(action.paramHandlerResultType, v), attach);
                    } else {
                        handler.failed(t, attach);
                    }
                });
            }
        } else if (action.returnFutureClass != null) { //返回类型为CompletableFuture
            if (action.returnFutureClass == CompletableFuture.class) {
                return (T) future.thenApply(v -> v == null ? null : convert.convertFrom(action.paramHandlerResultType, v));
            } else {
                final CompletableFuture stage = action.returnFutureCreator.create();
                future.whenComplete((v, t) -> {
                    if (t == null) {
                        stage.complete(v == null ? null : convert.convertFrom(action.paramHandlerResultType, v));
                    } else {
                        stage.completeExceptionally(t);
                    }
                });
                return (T) stage;
            }
        } else if (action.returnObjectType != null) { //返回类型为JavaBean
            return (T) future.thenApply(v -> v == null ? null : convert.convertFrom(action.paramHandlerResultType, v)).join();
        } else { //返回类型为void
            future.join();
        }
        return null;
    }

    protected CompletableFuture<byte[]> remote(
        final SncpServiceInfo info,
        final SncpServiceAction action,
        final String traceid,
        final Object... params) {

        return null;
    }
}
