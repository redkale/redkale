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
import java.util.concurrent.*;
import java.util.logging.Level;
import org.redkale.net.WorkThread;
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

        ExecutorService executor = null;
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            executor = ((WorkThread) thread).getExecutor();
        }
        if (executor == null) executor = ForkJoinPool.commonPool();

        return CompletableFuture.supplyAsync(() -> {
            final List<String> rs = new ArrayList<>();
            this.localEngine.getLocalWebSockets(groupid).forEach(x -> rs.add(x.getRemoteAddr()));
            return rs;
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> sendMessage(@RpcTargetAddress InetSocketAddress targetAddress, Object message, boolean last, Serializable... userids) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.sendLocalMessage(message, last, userids);
    }

    @Override
    public CompletableFuture<Integer> broadcastMessage(@RpcTargetAddress InetSocketAddress targetAddress, final WebSocketRange wsrange, Object message, boolean last) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.broadcastLocalMessage(wsrange, message, last);
    }

    @Override
    public CompletableFuture<Integer> sendAction(@RpcTargetAddress InetSocketAddress targetAddress, final WebSocketAction action, Serializable... userids) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.sendLocalAction(action, userids);
    }

    @Override
    public CompletableFuture<Integer> broadcastAction(@RpcTargetAddress InetSocketAddress targetAddress, final WebSocketAction action) {
        if (this.localEngine == null) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        return this.localEngine.broadcastLocalAction(action);
    }

    /**
     * 当用户连接到节点，需要更新到CacheSource
     *
     * @param userid   Serializable
     * @param sncpAddr InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> connect(Serializable userid, InetSocketAddress sncpAddr) {
        tryAcquireSemaphore();
        CompletableFuture<Void> future = sncpNodeAddresses.appendSetItemAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class, sncpAddr);
        future = future.thenAccept((a) -> sncpNodeAddresses.appendSetItemAsync(SOURCE_SNCP_ADDRS_KEY, InetSocketAddress.class, sncpAddr));
        if (semaphore != null) future.whenComplete((r, e) -> releaseSemaphore());
        if (logger.isLoggable(Level.FINEST)) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " connect from " + sncpAddr);
        return future;
    }

    /**
     * 当用户从一个节点断掉了所有的连接，需要从CacheSource中删除
     *
     * @param userid   Serializable
     * @param sncpAddr InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> disconnect(Serializable userid, InetSocketAddress sncpAddr) {
        tryAcquireSemaphore();
        CompletableFuture<Void> future = sncpNodeAddresses.removeSetItemAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class, sncpAddr);
        if (semaphore != null) future.whenComplete((r, e) -> releaseSemaphore());
        if (logger.isLoggable(Level.FINEST)) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " disconnect from " + sncpAddr);
        return future;
    }

    /**
     * 更改用户ID，需要更新到CacheSource
     *
     * @param olduserid Serializable
     * @param newuserid Serializable
     * @param sncpAddr  InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> changeUserid(Serializable olduserid, Serializable newuserid, InetSocketAddress sncpAddr) {
        tryAcquireSemaphore();
        CompletableFuture<Void> future = sncpNodeAddresses.appendSetItemAsync(SOURCE_SNCP_USERID_PREFIX + newuserid, InetSocketAddress.class, sncpAddr);
        future = future.thenAccept((a) -> sncpNodeAddresses.removeSetItemAsync(SOURCE_SNCP_USERID_PREFIX + olduserid, InetSocketAddress.class, sncpAddr));
        if (semaphore != null) future.whenComplete((r, e) -> releaseSemaphore());
        if (logger.isLoggable(Level.FINEST)) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + olduserid + " changeUserid to " + newuserid + " from " + sncpAddr);
        return future;
    }

    /**
     * 判断用户是否有WebSocket
     *
     * @param userid        Serializable
     * @param targetAddress InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Boolean> existsWebSocket(Serializable userid, @RpcTargetAddress InetSocketAddress targetAddress) {
        if (logger.isLoggable(Level.FINEST)) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " existsWebSocket from " + targetAddress);
        if (localEngine == null) return CompletableFuture.completedFuture(false);
        return CompletableFuture.completedFuture(localEngine.existsLocalWebSocket(userid));
    }

    /**
     * 强制关闭用户的WebSocket
     *
     * @param userid        Serializable
     * @param targetAddress InetSocketAddress
     *
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Integer> forceCloseWebSocket(Serializable userid, @RpcTargetAddress InetSocketAddress targetAddress) {
        //不能从sncpNodeAddresses中移除，因为engine.forceCloseWebSocket 会调用到disconnect
        if (logger.isLoggable(Level.FINEST)) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " forceCloseWebSocket from " + targetAddress);
        if (localEngine == null) return CompletableFuture.completedFuture(0);
        return CompletableFuture.completedFuture(localEngine.forceCloseLocalWebSocket(userid));
    }
}
