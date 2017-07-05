/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import static org.redkale.net.http.WebSocket.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.redkale.net.http.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@AutoLoad(false)
@ResourceType(WebSocketNode.class)
public class WebSocketNodeService extends WebSocketNode implements Service {

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
    }

    @Override
    public void destroy(AnyValue conf) {
        super.destroy(conf);
    }

    @Override
    public CompletableFuture<List<String>> getWebSocketAddresses(final @RpcTargetAddress InetSocketAddress targetAddress, final Serializable groupid) {
        if (localSncpAddress == null || !localSncpAddress.equals(targetAddress)) return remoteWebSocketAddresses(targetAddress, groupid);
        if (this.localEngine == null) return CompletableFuture.completedFuture(new ArrayList<>());
        return CompletableFuture.supplyAsync(() -> {
            final List<String> rs = new ArrayList<>();
            this.localEngine.getLocalWebSockets(groupid).forEach(x -> rs.add(x.getRemoteAddr()));
            return rs;
        });
    }

    @Override
    public CompletableFuture<Integer> sendMessage(@RpcTargetAddress InetSocketAddress addr, Object message, boolean last, Serializable userid) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.sendMessage(message, last, userid);
    }

    @Override
    public CompletableFuture<Integer> broadcastMessage(@RpcTargetAddress InetSocketAddress addr, Object message, boolean last) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.broadcastMessage(message, last);
    }

    /**
     * 当用户连接到节点，需要更新到CacheSource
     *
     * @param userid   String
     * @param sncpAddr InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> connect(Serializable userid, InetSocketAddress sncpAddr) {
        CompletableFuture<Void> future = sncpNodeAddresses.appendSetItemAsync(userid, sncpAddr);
        future = future.thenAccept((a) -> sncpNodeAddresses.appendSetItemAsync(SOURCE_SNCP_NODES_KEY, sncpAddr));
        if (finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " connect from " + sncpAddr);
        return future;
    }

    /**
     * 当用户从一个节点断掉了所有的连接，需要从CacheSource中删除
     *
     * @param userid   String
     * @param sncpAddr InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> disconnect(Serializable userid, InetSocketAddress sncpAddr) {
        CompletableFuture<Void> future = sncpNodeAddresses.removeSetItemAsync(userid, sncpAddr);
        if (finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " disconnect from " + sncpAddr);
        return future;
    }
}
