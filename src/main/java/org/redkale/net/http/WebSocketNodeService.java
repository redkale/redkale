package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.redkale.annotation.*;
import static org.redkale.net.http.WebSocket.RETCODE_GROUP_EMPTY;
import org.redkale.service.RpcTargetAddress;
import org.redkale.service.RpcTargetTopic;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;

/**
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

    public final void setName(String name) {
        this.name = name;
    }

    @Override
    public CompletableFuture<List<String>> getWebSocketAddresses(
            @RpcTargetTopic String topic,
            final @RpcTargetAddress InetSocketAddress targetAddress,
            final Serializable groupid) {
        if ((topic == null || !topic.equals(this.wsNodeAddress.getTopic()))
                && (localSncpAddress == null || !localSncpAddress.equals(targetAddress))) {
            return remoteWebSocketAddresses(topic, targetAddress, groupid);
        }
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        final List<String> rs = new ArrayList<>();
        this.localEngine.getLocalWebSockets(groupid).forEach(x -> rs.add(x.getRemoteAddr()));
        return CompletableFuture.completedFuture(rs);
    }

    @Override
    public CompletableFuture<Integer> sendMessage(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            WebSocketPacket message,
            boolean last,
            Serializable... userids) {
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return this.localEngine.sendLocalMessage(message, last, userids);
    }

    @Override
    public CompletableFuture<Integer> broadcastMessage(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            final WebSocketRange wsrange,
            WebSocketPacket message,
            boolean last) {
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return this.localEngine.broadcastLocalMessage(wsrange, message, last);
    }

    @Override
    public CompletableFuture<Integer> sendAction(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            final WebSocketAction action,
            Serializable... userids) {
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return this.localEngine.sendLocalAction(action, userids);
    }

    @Override
    public CompletableFuture<Integer> broadcastAction(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            final WebSocketAction action) {
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return this.localEngine.broadcastLocalAction(action);
    }

    @Override
    public CompletableFuture<Integer> getUserSize(
            @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress) {
        if (this.localEngine == null) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.completedFuture(this.localEngine.getLocalUserSize());
    }

    /**
     * 当用户连接到节点，需要更新到CacheSource
     *
     * @param userid Serializable
     * @param wsaddr WebSocketAddress
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> connect(Serializable userid, WebSocketAddress wsaddr) {
        tryAcquireSemaphore();
        CompletableFuture<Void> future =
                source.saddAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class, wsaddr);
        if (semaphore != null) {
            future.whenComplete((r, e) -> releaseSemaphore());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " connect from " + wsaddr);
        }
        return future;
    }

    /**
     * 当用户从一个节点断掉了所有的连接，需要从CacheSource中删除
     *
     * @param userid Serializable
     * @param wsaddr WebSocketAddress
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> disconnect(Serializable userid, WebSocketAddress wsaddr) {
        tryAcquireSemaphore();
        CompletableFuture<Long> future =
                source.sremAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class, wsaddr);
        if (semaphore != null) {
            future.whenComplete((r, e) -> releaseSemaphore());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(
                    WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " disconnect from " + wsaddr);
        }
        return future.thenApply(v -> null);
    }

    /**
     * 更改用户ID，需要更新到CacheSource
     *
     * @param olduserid Serializable
     * @param newuserid Serializable
     * @param wsaddr WebSocketAddress
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Void> changeUserid(
            Serializable olduserid, Serializable newuserid, WebSocketAddress wsaddr) {
        tryAcquireSemaphore();
        CompletableFuture<Void> future =
                source.saddAsync(WS_SOURCE_KEY_USERID_PREFIX + newuserid, WebSocketAddress.class, wsaddr);
        future = future.thenAccept(
                (a) -> source.sremAsync(WS_SOURCE_KEY_USERID_PREFIX + olduserid, WebSocketAddress.class, wsaddr));
        if (semaphore != null) {
            future.whenComplete((r, e) -> releaseSemaphore());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + olduserid + " changeUserid to "
                    + newuserid + " from " + wsaddr);
        }
        return future;
    }

    /**
     * 判断用户是否有WebSocket
     *
     * @param userid Serializable
     * @param topic RpcTargetTopic
     * @param targetAddress InetSocketAddress
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Boolean> existsWebSocket(
            Serializable userid, @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid + " existsWebSocket from "
                    + targetAddress);
        }
        if (localEngine == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(localEngine.existsLocalWebSocket(userid));
    }

    /**
     * 强制关闭用户的WebSocket
     *
     * @param userid Serializable
     * @param topic RpcTargetTopic
     * @param targetAddress InetSocketAddress
     * @return 无返回值
     */
    @Override
    public CompletableFuture<Integer> forceCloseWebSocket(
            Serializable userid, @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress) {
        // 不能从sncpNodeAddresses中移除，因为engine.forceCloseWebSocket 会调用到disconnect
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + userid
                    + " forceCloseWebSocket from " + targetAddress);
        }
        if (localEngine == null) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.completedFuture(localEngine.forceCloseLocalWebSocket(userid));
    }
}
