/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.*;
import org.redkale.annotation.*;
import org.redkale.annotation.Comment;
import org.redkale.boot.Application;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.WorkThread;
import static org.redkale.net.http.WebSocket.RETCODE_GROUP_EMPTY;
import org.redkale.net.http.WebSocketPacket.FrameType;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.*;
import org.redkale.source.CacheSource;
import org.redkale.util.*;

/**
 * 注: 部署了WebSocketNodeService就必然要配置SNCP协议的Server，不然无法做到WebSocketNode.sendMessage方法的有效性
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class WebSocketNode implements Service {

    @Comment("存储用户ID的key前缀")
    public static final String WS_SOURCE_KEY_USERID_PREFIX = "sncpws_uid:";

    @Comment("存储当前SNCP节点列表的key")
    public static final String WS_SOURCE_KEY_NODES = "sncpws_nodes";

    protected final Logger logger = Logger.getLogger(WebSocketNode.class.getSimpleName());

    @Resource(name = RESNAME_APP_NODEID)
    protected String nodeid;

    // "SNCP_ADDR" 如果不是分布式(没有SNCP) 值为null
    @Resource(name = Application.RESNAME_SNCP_ADDRESS, required = false)
    protected InetSocketAddress localSncpAddress; // 为SncpServer的服务address

    protected WebSocketAddress wsNodeAddress;

    protected String name;

    // 如果不是分布式(没有SNCP) 值为null
    @RpcRemote
    protected WebSocketNode remoteNode;

    @Resource(name = Resource.PARENT_NAME + "_sendconvert", required = false)
    protected Convert sendConvert;

    // 存放所有用户分布在节点上的队列信息,Set<WebSocketAddress> 为 sncpnode 的集合， key: groupid
    // 集合包含 localSncpAddress
    // 如果不是分布式(没有SNCP)，source 将不会被用到
    @Resource(name = Resource.PARENT_NAME, required = false)
    protected CacheSource source;

    // 当前节点的本地WebSocketEngine
    protected WebSocketEngine localEngine;

    protected MessageAgent messageAgent;

    protected Semaphore semaphore;

    private int tryAcquireSeconds = 12;

    @Override
    public void init(AnyValue conf) {
        this.tryAcquireSeconds = Integer.getInteger("redkale.http.websocket.tryAcquireSeconds", 12);

        if (localEngine != null) {
            int wsthreads = localEngine.wsThreads;
            if (wsthreads == 0) {
                wsthreads = WorkThread.DEFAULT_WORK_POOL_SIZE;
            }
            if (wsthreads > 0) {
                this.semaphore = new Semaphore(wsthreads);
            }
        }
        String mqtopic = this.messageAgent == null ? null : Sncp.generateSncpReqTopic((Service) this, nodeid);
        if (mqtopic != null || this.localSncpAddress != null) {
            this.wsNodeAddress = new WebSocketAddress(mqtopic, localSncpAddress);
        }
        if (source != null && this.wsNodeAddress == null) { // 非分布式模式
            this.wsNodeAddress = new WebSocketAddress(mqtopic, new InetSocketAddress("127.0.0.1", 27));
        }
        if (source != null) {
            source.sadd(WS_SOURCE_KEY_NODES, WebSocketAddress.class, this.wsNodeAddress);
        }
    }

    @Override
    public void destroy(AnyValue conf) {}

    @Local
    public final Semaphore getSemaphore() {
        return semaphore;
    }

    @Local
    public final MessageAgent getMessageAgent() {
        return messageAgent;
    }

    @Local
    protected void postDestroy(AnyValue conf) {
        if (this.localEngine == null) {
            return;
        }
        // 关掉所有本地本地WebSocket
        this.localEngine.getLocalWebSockets().forEach(g -> g.close());
        if (source != null && wsNodeAddress != null) {
            source.srem(WS_SOURCE_KEY_NODES, WebSocketAddress.class, this.wsNodeAddress);
        }
    }

    protected abstract CompletableFuture<List<String>> getWebSocketAddresses(
            @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress, Serializable userid);

    protected abstract CompletableFuture<Integer> sendMessage(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            WebSocketPacket message,
            boolean last,
            Serializable... userids);

    protected abstract CompletableFuture<Integer> broadcastMessage(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            WebSocketRange wsrange,
            WebSocketPacket message,
            boolean last);

    protected abstract CompletableFuture<Integer> sendAction(
            @RpcTargetTopic String topic,
            @RpcTargetAddress InetSocketAddress targetAddress,
            WebSocketAction action,
            Serializable... userids);

    protected abstract CompletableFuture<Integer> broadcastAction(
            @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress, WebSocketAction action);

    protected abstract CompletableFuture<Integer> getUserSize(
            @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress);

    protected abstract CompletableFuture<Void> connect(Serializable userid, WebSocketAddress wsaddr);

    protected abstract CompletableFuture<Void> disconnect(Serializable userid, WebSocketAddress wsaddr);

    protected abstract CompletableFuture<Void> changeUserid(
            Serializable fromuserid, Serializable touserid, WebSocketAddress wsaddr);

    protected abstract CompletableFuture<Boolean> existsWebSocket(
            Serializable userid, @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress);

    protected abstract CompletableFuture<Integer> forceCloseWebSocket(
            Serializable userid, @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress);

    // --------------------------------------------------------------------------------
    final CompletableFuture<Void> connect(final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(wsNodeAddress + " receive websocket connect event (" + userid + " on "
                    + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        }
        return connect(userid, wsNodeAddress);
    }

    final CompletableFuture<Void> disconnect(final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(wsNodeAddress + " receive websocket disconnect event (" + userid + " on "
                    + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        }
        return disconnect(userid, wsNodeAddress);
    }

    final CompletableFuture<Void> changeUserid(Serializable olduserid, final Serializable newuserid) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(wsNodeAddress + " receive websocket changeUserid event (from " + olduserid + " to "
                    + newuserid + " on " + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        }
        return changeUserid(olduserid, newuserid, wsNodeAddress);
    }

    public final String getName() {
        return name;
    }

    // --------------------------------------------------------------------------------
    /**
     * 获取目标地址 <br>
     * 该方法仅供内部调用
     *
     * @param topic RpcTargetTopic
     * @param targetAddress InetSocketAddress
     * @param userid Serializable
     * @return 客户端地址列表
     */
    protected CompletableFuture<List<String>> remoteWebSocketAddresses(
            @RpcTargetTopic String topic, @RpcTargetAddress InetSocketAddress targetAddress, Serializable userid) {
        if (remoteNode == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return remoteNode.getWebSocketAddresses(topic, targetAddress, userid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "remote " + targetAddress + " websocket getOnlineRemoteAddresses error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 获取用户在线的SNCP节点地址列表，不是分布式则返回元素数量为1，且元素值为null的列表<br>
     * WebSocketAddress 为 SNCP节点地址
     *
     * @param userid Serializable
     * @return 地址列表
     */
    public CompletableFuture<Set<WebSocketAddress>> getRpcNodeAddresses(final Serializable userid) {
        if (this.source != null) {
            tryAcquireSemaphore();
            CompletableFuture<Set<WebSocketAddress>> result =
                    this.source.smembersAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class);
            if (semaphore != null) {
                result.whenComplete((r, e) -> releaseSemaphore());
            }
            return result;
        }
        Set<WebSocketAddress> rs = new LinkedHashSet<>();
        rs.add(this.wsNodeAddress);
        return CompletableFuture.completedFuture(rs);
    }

    /**
     * 获取在线用户的详细连接信息 <br>
     * Map.key 为 SNCP节点地址, 含值为null的key表示没有分布式 Map.value 为 用户客户端的IP
     *
     * @param userid Serializable
     * @return 地址集合
     */
    public CompletableFuture<Map<WebSocketAddress, List<String>>> getRpcNodeWebSocketAddresses(
            final Serializable userid) {
        CompletableFuture<Set<WebSocketAddress>> sncpFuture = getRpcNodeAddresses(userid);
        return sncpFuture.thenCompose((Collection<WebSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket found userid:" + userid + " on " + addrs);
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(new HashMap<>());
            }
            CompletableFuture<Map<WebSocketAddress, List<String>>> future = null;
            for (final WebSocketAddress nodeAddress : addrs) {
                CompletableFuture<Map<WebSocketAddress, List<String>>> mapFuture = getWebSocketAddresses(
                                nodeAddress.getTopic(), nodeAddress.getAddr(), userid)
                        .thenCompose((List<String> list) ->
                                CompletableFuture.completedFuture(Utility.ofMap(nodeAddress, list)));
                future = future == null ? mapFuture : future.thenCombine(mapFuture, (a, b) -> Utility.merge(a, b));
            }
            return future == null ? CompletableFuture.completedFuture(new HashMap<>()) : future;
        });
    }

    //    public CompletableFuture<Integer> getUserSize() {
    //        if (this.localEngine != null && this.source == null) {
    //            return CompletableFuture.completedFuture(this.localEngine.getLocalUserSize());
    //        }
    //        tryAcquireSemaphore();
    //        CompletableFuture<List<String>> listFuture =
    // this.source.queryKeysStartsWithAsync(WS_SOURCE_KEY_USERID_PREFIX);
    //        CompletableFuture<Integer> rs = listFuture.thenApply(v -> v.size());
    //        if (semaphore != null) rs.whenComplete((r, e) -> releaseSemaphore());
    //        return rs;
    //    }
    /**
     * 获取在线用户总数
     *
     * @return boolean
     */
    @Local
    public CompletableFuture<Integer> getUserSize() {
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return CompletableFuture.completedFuture(this.localEngine.getLocalUserSize());
        }
        CompletableFuture<Integer> localFuture = this.localEngine == null
                ? null
                : CompletableFuture.completedFuture(this.localEngine.getLocalUserSize());
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_NODES, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose(addrs -> {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket getUserSize on " + addrs);
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.getUserSize(addr.getTopic(), addr.getAddr())
                        : future.thenCombine(remoteNode.getUserSize(addr.getTopic(), addr.getAddr()), (a, b) -> a + b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a + b);
    }

    /**
     * 获取在线用户总数
     *
     * @return boolean
     */
    public CompletableFuture<Set<String>> getUserSet() {
        if (this.localEngine != null && this.source == null) {
            return CompletableFuture.completedFuture(new LinkedHashSet<>(this.localEngine.getLocalUserSet().stream()
                    .map(x -> String.valueOf(x))
                    .collect(Collectors.toList())));
        }
        tryAcquireSemaphore();
        CompletableFuture<List<String>> listFuture = this.source.keysStartsWithAsync(WS_SOURCE_KEY_USERID_PREFIX);
        CompletableFuture<Set<String>> rs = listFuture.thenApply(v -> new LinkedHashSet<>(v.stream()
                .map(x -> x.substring(WS_SOURCE_KEY_USERID_PREFIX.length()))
                .collect(Collectors.toList())));
        if (semaphore != null) {
            rs.whenComplete((r, e) -> releaseSemaphore());
        }
        return rs;
    }

    /**
     * 判断指定用户是否WebSocket在线
     *
     * @param userid Serializable
     * @return boolean
     */
    @Local
    public CompletableFuture<Boolean> existsWebSocket(final Serializable userid) {
        if (userid instanceof WebSocketUserAddress) {
            return existsWebSocket((WebSocketUserAddress) userid);
        }
        CompletableFuture<Boolean> localFuture = null;
        if (this.localEngine != null) {
            localFuture = CompletableFuture.completedFuture(localEngine.existsLocalWebSocket(userid));
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(false) : localFuture;
        }
        // 远程节点关闭
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Boolean> remoteFuture = addrsFuture.thenCompose(addrs -> {
            // if (logger.isLoggable(Level.FINEST)) logger.finest("websocket found userid:" + userid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.existsWebSocket(userid, addr.getTopic(), addr.getAddr())
                        : future.thenCombine(
                                remoteNode.existsWebSocket(userid, addr.getTopic(), addr.getAddr()), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(false) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a || b);
    }

    /**
     * 判断指定用户是否WebSocket在线
     *
     * @param userAddress WebSocketUserAddress
     * @return boolean
     */
    @Local
    public CompletableFuture<Boolean> existsWebSocket(final WebSocketUserAddress userAddress) {
        if (this.localEngine != null && localEngine.existsLocalWebSocket(userAddress.userid())) {
            return CompletableFuture.completedFuture(true);
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return CompletableFuture.completedFuture(false);
        }
        Collection<WebSocketAddress> addrs = userAddress.addresses();
        if (addrs != null) {
            addrs = new ArrayList<>(addrs); // 不能修改参数内部值
        }
        if (userAddress.address() != null) {
            if (addrs == null) {
                addrs = new ArrayList<>();
            }
            addrs.add(userAddress.address());
        }
        if (addrs == null || addrs.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = null;
        for (WebSocketAddress addr : addrs) {
            if (addr == null || addr.equals(wsNodeAddress)) {
                continue;
            }
            future = future == null
                    ? remoteNode.existsWebSocket(userAddress.userid(), addr.getTopic(), addr.getAddr())
                    : future.thenCombine(
                            remoteNode.existsWebSocket(userAddress.userid(), addr.getTopic(), addr.getAddr()),
                            (a, b) -> a | b);
        }
        return future == null ? CompletableFuture.completedFuture(false) : future;
    }

    /**
     * 强制关闭用户WebSocket
     *
     * @param userid Serializable
     * @return int
     */
    @Local
    public CompletableFuture<Integer> forceCloseWebSocket(final Serializable userid) {
        return forceCloseWebSocket(userid, (WebSocketUserAddress) null);
    }

    /**
     * 强制关闭用户WebSocket
     *
     * @param userAddress WebSocketUserAddress
     * @return int
     */
    @Local
    public CompletableFuture<Integer> forceCloseWebSocket(final WebSocketUserAddress userAddress) {
        return forceCloseWebSocket(null, userAddress);
    }

    private CompletableFuture<Integer> forceCloseWebSocket(
            final Serializable userid, final WebSocketUserAddress userAddress) {
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) {
            localFuture = CompletableFuture.completedFuture(
                    localEngine.forceCloseLocalWebSocket(userAddress == null ? userid : userAddress.userid()));
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(0) : localFuture;
        }
        // 远程节点关闭
        CompletableFuture<Collection<WebSocketAddress>> addrsFuture;
        if (userAddress == null) {
            tryAcquireSemaphore();
            addrsFuture = (CompletableFuture)
                    source.smembersAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class);
            if (semaphore != null) {
                addrsFuture.whenComplete((r, e) -> releaseSemaphore());
            }
        } else {
            Collection<WebSocketAddress> addrs = userAddress.addresses();
            if (addrs != null) {
                addrs = new ArrayList<>(addrs); // 不能修改参数内部值
            }
            if (userAddress.address() != null) {
                if (addrs == null) {
                    addrs = new ArrayList<>();
                }
                addrs.add(userAddress.address());
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            addrsFuture = CompletableFuture.completedFuture(addrs);
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<WebSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket found userid:" + userid + " on " + addrs);
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.forceCloseWebSocket(userid, addr.getTopic(), addr.getAddr())
                        : future.thenCombine(
                                remoteNode.forceCloseWebSocket(userid, addr.getTopic(), addr.getAddr()),
                                (a, b) -> a + b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a + b);
    }

    // --------------------------------------------------------------------------------
    /**
     * 获取本地的WebSocketEngine，没有则返回null
     *
     * @return WebSocketEngine
     */
    @Local
    public final WebSocketEngine getLocalWebSocketEngine() {
        return this.localEngine;
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param userids Serializable[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(Object message, final Serializable... userids) {
        return sendMessage((Convert) null, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param useraddrs WebSocketUserAddress[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(Object message, final WebSocketUserAddress... useraddrs) {
        return sendMessage((Convert) null, message, true, useraddrs);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message 消息内容
     * @param userids Serializable[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(
            final Convert convert, Object message, final Serializable... userids) {
        return sendMessage(convert, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message 消息内容
     * @param useraddrs WebSocketUserAddress[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(
            final Convert convert, Object message, final WebSocketUserAddress... useraddrs) {
        return sendMessage(convert, message, true, useraddrs);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param last 是否最后一条
     * @param userids Serializable[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(
            final Object message, final boolean last, final Serializable... userids) {
        return sendMessage((Convert) null, message, last, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param last 是否最后一条
     * @param useraddrs WebSocketUserAddress[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(
            final Object message, final boolean last, final WebSocketUserAddress... useraddrs) {
        return sendMessage((Convert) null, message, last, useraddrs);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message0 消息内容
     * @param last 是否最后一条
     * @param userids Serializable[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendMessage(
            final Convert convert, final Object message0, final boolean last, final Serializable... userids) {
        if (Utility.isEmpty(userids)) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        if (message0 instanceof CompletableFuture) {
            return ((CompletableFuture) message0).thenApply(msg -> sendMessage(convert, msg, last, userids));
        }
        final Object message = (convert == null || message0 instanceof WebSocketPacket)
                ? message0
                : ((convert instanceof TextConvert)
                        ? new WebSocketPacket(FrameType.TEXT, convert.convertToBytes(message0), last)
                        : new WebSocketPacket(FrameType.BINARY, convert.convertToBytes(message0), last));
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.sendLocalMessage(message, last, userids);
        }
        final WebSocketPacket remoteMessage = formatRemoteMessage(message);
        CompletableFuture<Integer> rsfuture;
        if (userids.length == 1) {
            rsfuture = sendOneUserMessage(remoteMessage, last, userids[0]);
        } else {
            String[] keys = new String[userids.length];
            final Map<String, Serializable> keyuser = new HashMap<>();
            for (int i = 0; i < userids.length; i++) {
                keys[i] = WS_SOURCE_KEY_USERID_PREFIX + userids[i];
                keyuser.put(keys[i], userids[i]);
            }
            tryAcquireSemaphore();
            CompletableFuture<Map<String, Set<WebSocketAddress>>> addrsFuture =
                    source.smembersAsync(WebSocketAddress.class, keys);
            if (semaphore != null) {
                addrsFuture.whenComplete((r, e) -> releaseSemaphore());
            }
            rsfuture = addrsFuture.thenCompose(addrs -> {
                if (addrs == null || addrs.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("websocket not found userids:"
                                + JsonConvert.root().convertTo(userids) + " on any node ");
                    }
                    return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
                }
                Map<WebSocketAddress, List<Serializable>> addrUsers = new HashMap<>();
                addrs.forEach((key, as) -> {
                    for (WebSocketAddress a : as) {
                        addrUsers.computeIfAbsent(a, k -> new ArrayList<>()).add(keyuser.get(key));
                    }
                });
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("websocket(localaddr=" + localSncpAddress + ", userids="
                            + JsonConvert.root().convertTo(userids) + ") found message-addr-userids: " + addrUsers);
                }
                CompletableFuture<Integer> future = null;
                for (Map.Entry<WebSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
                    Serializable[] oneaddrUserids =
                            en.getValue().toArray(new Serializable[en.getValue().size()]);
                    future = future == null
                            ? sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids)
                            : future.thenCombine(
                                    sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids),
                                    (a, b) -> a | b);
                }
                return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
            });
        }
        return rsfuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : rsfuture;
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message0 消息内容
     * @param last 是否最后一条
     * @param useraddrs WebSocketUserAddress[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendMessage(
            Convert convert, final Object message0, final boolean last, final WebSocketUserAddress... useraddrs) {
        if (Utility.isEmpty(useraddrs)) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        if (message0 instanceof CompletableFuture) {
            return ((CompletableFuture) message0).thenApply(msg -> sendMessage(convert, msg, last, useraddrs));
        }
        final Object message = (convert == null || message0 instanceof WebSocketPacket)
                ? message0
                : ((convert instanceof TextConvert)
                        ? new WebSocketPacket(((TextConvert) convert).convertTo(message0), last)
                        : new WebSocketPacket(((BinaryConvert) convert).convertTo(message0), last));
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.sendLocalMessage(message, last, userAddressToUserids(useraddrs));
        }

        final Object remoteMessage = formatRemoteMessage(message);
        final Map<WebSocketAddress, List<Serializable>> addrUsers = userAddressToAddrMap(useraddrs);
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket(localaddr=" + localSncpAddress + ", useraddrs="
                    + JsonConvert.root().convertTo(useraddrs) + ") found message-addr-userids: " + addrUsers);
        }
        CompletableFuture<Integer> future = null;
        for (Map.Entry<WebSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
            Serializable[] oneaddrUserids =
                    en.getValue().toArray(new Serializable[en.getValue().size()]);
            future = future == null
                    ? sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids)
                    : future.thenCombine(
                            sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids), (a, b) -> a | b);
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
    }

    protected CompletableFuture<Integer> sendOneUserMessage(
            final Object message, final boolean last, final Serializable userid) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenApply(msg -> sendOneUserMessage(msg, last, userid));
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send message {userid:" + userid + ", content:"
                    + (message instanceof WebSocketPacket
                            ? ((WebSocketPacket) message).toSimpleString()
                            : (message instanceof CharSequence
                                    ? message
                                    : JsonConvert.root().convertTo(message)))
                    + "} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) {
            localFuture = localEngine.sendLocalMessage(message, last, userid);
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localFuture;
        }
        // 远程节点发送消息
        final WebSocketPacket remoteMessage = formatRemoteMessage(message);
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose(addrs -> {
            if (addrs == null || addrs.isEmpty()) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("websocket not found userid:" + userid + " on any node ");
                }
                return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket(localaddr=" + wsNodeAddress + ") found userid:" + userid + " on " + addrs);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.sendMessage(addr.getTopic(), addr.getAddr(), remoteMessage, last, userid)
                        : future.thenCombine(
                                remoteNode.sendMessage(addr.getTopic(), addr.getAddr(), remoteMessage, last, userid),
                                (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    protected CompletableFuture<Integer> sendOneAddrMessage(
            final WebSocketAddress addr, final Object message, final boolean last, final Serializable... userids) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenApply(msg -> sendOneAddrMessage(addr, msg, last, userids));
        }
        if (logger.isLoggable(Level.FINEST) && this.localEngine == null) { // 只打印远程模式的
            logger.finest("websocket want send message {userids:"
                    + JsonConvert.root().convertTo(userids)
                    + ", sncpaddr:" + addr + ", content:"
                    + (message instanceof WebSocketPacket
                            ? ((WebSocketPacket) message).toSimpleString()
                            : (message instanceof CharSequence
                                    ? message
                                    : JsonConvert.root().convertTo(message)))
                    + "} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        if (Objects.equals(addr, this.wsNodeAddress)) {
            return this.localEngine == null
                    ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY)
                    : localEngine.sendLocalMessage(message, last, userids);
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        final WebSocketPacket remoteMessage = formatRemoteMessage(message);
        return remoteNode.sendMessage(addr.getTopic(), addr.getAddr(), remoteMessage, last, userids);
    }

    protected Serializable[] userAddressToUserids(WebSocketUserAddress... useraddrs) {
        if (useraddrs == null || useraddrs.length == 1) {
            return new Serializable[0];
        }
        Set<Serializable> set = new HashSet<>();
        for (WebSocketUserAddress userAddress : useraddrs) {
            set.add(userAddress.userid());
        }
        return set.toArray(new Serializable[set.size()]);
    }

    protected Map<WebSocketAddress, List<Serializable>> userAddressToAddrMap(WebSocketUserAddress... useraddrs) {
        final Map<WebSocketAddress, List<Serializable>> addrUsers = new HashMap<>();
        for (WebSocketUserAddress userAddress : useraddrs) {
            if (userAddress.address() != null) {
                addrUsers
                        .computeIfAbsent(userAddress.address(), k -> new ArrayList<>())
                        .add(userAddress.userid());
            }
            if (userAddress.addresses() != null) {
                for (WebSocketAddress addr : userAddress.addresses()) {
                    if (addr != null) {
                        addrUsers.computeIfAbsent(addr, k -> new ArrayList<>()).add(userAddress.userid());
                    }
                }
            }
        }
        return addrUsers;
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final Object message) {
        return broadcastMessage((Convert) null, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param message 消息内容
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message) {
        return broadcastMessage(wsrange, (Convert) null, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param convert Convert
     * @param message 消息内容
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message) {
        return broadcastMessage(convert, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param convert Convert
     * @param message 消息内容
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(
            final WebSocketRange wsrange, final Convert convert, final Object message) {
        return broadcastMessage(wsrange, convert, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     * @param last 是否最后一条
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final Object message, final boolean last) {
        return broadcastMessage((Convert) null, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param message 消息内容
     * @param last 是否最后一条
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(
            final WebSocketRange wsrange, final Object message, final boolean last) {
        return broadcastMessage(wsrange, (Convert) null, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param convert Convert
     * @param message0 消息内容
     * @param last 是否最后一条
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(
            final Convert convert, final Object message0, final boolean last) {
        return broadcastMessage((WebSocketRange) null, convert, message0, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param convert Convert
     * @param message0 消息内容
     * @param last 是否最后一条
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> broadcastMessage(
            WebSocketRange wsrange, Convert convert, Object message0, final boolean last) {
        if (message0 instanceof CompletableFuture) {
            return ((CompletableFuture) message0).thenApply(msg -> broadcastMessage(wsrange, convert, msg, last));
        }
        final Object message = (convert == null || message0 instanceof WebSocketPacket)
                ? message0
                : ((convert instanceof TextConvert)
                        ? new WebSocketPacket(((TextConvert) convert).convertTo(message0), last)
                        : new WebSocketPacket(((BinaryConvert) convert).convertTo(message0), last));
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.broadcastLocalMessage(wsrange, message, last);
        }
        final WebSocketPacket remoteMessage = formatRemoteMessage(message);
        CompletableFuture<Integer> localFuture =
                this.localEngine == null ? null : this.localEngine.broadcastLocalMessage(wsrange, message, last);
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_NODES, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose(addrs -> {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket broadcast message (" + remoteMessage + ") on " + addrs);
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.broadcastMessage(addr.getTopic(), addr.getAddr(), wsrange, remoteMessage, last)
                        : future.thenCombine(
                                remoteNode.broadcastMessage(
                                        addr.getTopic(), addr.getAddr(), wsrange, remoteMessage, last),
                                (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    /**
     * 广播操作， 给所有人发操作
     *
     * @param action 操作参数
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> broadcastAction(final WebSocketAction action) {
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.broadcastLocalAction(action);
        }
        CompletableFuture<Integer> localFuture =
                this.localEngine == null ? null : this.localEngine.broadcastLocalAction(action);
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_NODES, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose(addrs -> {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket broadcast action (" + action + ") on " + addrs);
            }
            if (addrs == null || addrs.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.broadcastAction(addr.getTopic(), addr.getAddr(), action)
                        : future.thenCombine(
                                remoteNode.broadcastAction(addr.getTopic(), addr.getAddr(), action), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    /**
     * 向指定用户发送操作，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param action 操作参数
     * @param userids Serializable[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendAction(final WebSocketAction action, final Serializable... userids) {
        if (Utility.isEmpty(userids)) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        if (userids[0] instanceof WebSocketUserAddress) {
            WebSocketUserAddress[] useraddrs = new WebSocketUserAddress[userids.length];
            for (int i = 0; i < useraddrs.length; i++) {
                useraddrs[i] = (WebSocketUserAddress) userids[i];
            }
            return sendAction(action, useraddrs);
        }
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.sendLocalAction(action, userids);
        }
        CompletableFuture<Integer> rsfuture;
        if (userids.length == 1) {
            rsfuture = sendOneUserAction(action, userids[0]);
        } else {
            String[] keys = new String[userids.length];
            final Map<String, Serializable> keyuser = new HashMap<>();
            for (int i = 0; i < userids.length; i++) {
                keys[i] = WS_SOURCE_KEY_USERID_PREFIX + userids[i];
                keyuser.put(keys[i], userids[i]);
            }
            tryAcquireSemaphore();
            CompletableFuture<Map<String, Set<WebSocketAddress>>> addrsFuture =
                    source.smembersAsync(WebSocketAddress.class, keys);
            if (semaphore != null) {
                addrsFuture.whenComplete((r, e) -> releaseSemaphore());
            }
            rsfuture = addrsFuture.thenCompose(addrs -> {
                if (addrs == null || addrs.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("websocket not found userids:"
                                + JsonConvert.root().convertTo(userids) + " on any node ");
                    }
                    return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
                }
                Map<WebSocketAddress, List<Serializable>> addrUsers = new HashMap<>();
                addrs.forEach((key, as) -> {
                    for (WebSocketAddress a : as) {
                        addrUsers.computeIfAbsent(a, k -> new ArrayList<>()).add(keyuser.get(key));
                    }
                });
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("websocket(localaddr=" + localSncpAddress + ", userids="
                            + JsonConvert.root().convertTo(userids) + ") found action-userid-addrs: " + addrUsers);
                }
                CompletableFuture<Integer> future = null;
                for (Map.Entry<WebSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
                    Serializable[] oneaddrUserids =
                            en.getValue().toArray(new Serializable[en.getValue().size()]);
                    future = future == null
                            ? sendOneAddrAction(en.getKey(), action, oneaddrUserids)
                            : future.thenCombine(
                                    sendOneAddrAction(en.getKey(), action, oneaddrUserids), (a, b) -> a | b);
                }
                return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
            });
        }
        return rsfuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : rsfuture;
    }

    /**
     * 向指定用户发送操作，先发送本地连接，再发送远程连接 <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param action 操作参数
     * @param useraddrs WebSocketUserAddress[]
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendAction(
            final WebSocketAction action, final WebSocketUserAddress... useraddrs) {
        if (Utility.isEmpty(useraddrs)) {
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        if (this.localEngine != null && this.source == null) { // 本地模式且没有分布式
            return this.localEngine.sendLocalAction(action, userAddressToUserids(useraddrs));
        }

        final Map<WebSocketAddress, List<Serializable>> addrUsers = userAddressToAddrMap(useraddrs);
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket(localaddr=" + localSncpAddress + ", useraddrs="
                    + JsonConvert.root().convertTo(useraddrs) + ") found action-userid-addrs: " + addrUsers);
        }
        CompletableFuture<Integer> future = null;
        for (Map.Entry<WebSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
            Serializable[] oneaddrUserids =
                    en.getValue().toArray(new Serializable[en.getValue().size()]);
            future = future == null
                    ? sendOneAddrAction(en.getKey(), action, oneaddrUserids)
                    : future.thenCombine(sendOneAddrAction(en.getKey(), action, oneaddrUserids), (a, b) -> a | b);
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
    }

    protected CompletableFuture<Integer> sendOneUserAction(final WebSocketAction action, final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send action {userid:" + userid + ", action:" + action
                    + "} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) {
            localFuture = localEngine.sendLocalAction(action, userid);
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localFuture;
        }
        // 远程节点发送操作
        tryAcquireSemaphore();
        CompletableFuture<Set<WebSocketAddress>> addrsFuture =
                source.smembersAsync(WS_SOURCE_KEY_USERID_PREFIX + userid, WebSocketAddress.class);
        if (semaphore != null) {
            addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose(addrs -> {
            if (addrs == null || addrs.isEmpty()) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("websocket not found userid:" + userid + " on any node ");
                }
                return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket(localaddr=" + localSncpAddress + ") found userid:" + userid + " on " + addrs);
            }
            CompletableFuture<Integer> future = null;
            for (WebSocketAddress addr : addrs) {
                if (addr == null || addr.equals(wsNodeAddress)) {
                    continue;
                }
                future = future == null
                        ? remoteNode.sendAction(addr.getTopic(), addr.getAddr(), action, userid)
                        : future.thenCombine(
                                remoteNode.sendAction(addr.getTopic(), addr.getAddr(), action, userid),
                                (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    protected CompletableFuture<Integer> sendOneAddrAction(
            WebSocketAddress addr, WebSocketAction action, Serializable... userids) {
        if (logger.isLoggable(Level.FINEST) && this.localEngine == null) { // 只打印远程模式的
            logger.finest(
                    "websocket want send action {userids:" + JsonConvert.root().convertTo(userids)
                            + ", sncpaddr:" + addr + ", action:" + action + " from locale node to "
                            + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        if (Objects.equals(addr, this.wsNodeAddress)) {
            return this.localEngine == null
                    ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY)
                    : localEngine.sendLocalAction(action, userids);
        }
        if (this.source == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("websocket "
                        + (this.remoteNode == null ? (this.source == null ? "remote and source" : "remote") : "source")
                        + " node is null");
            }
            // 没有CacheSource就不会有分布式节点
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return remoteNode.sendAction(addr.getTopic(), addr.getAddr(), action, userids);
    }

    protected WebSocketPacket formatRemoteMessage(Object message) {
        if (message instanceof WebSocketPacket) {
            return (WebSocketPacket) message;
        }
        if (message instanceof byte[]) {
            return new WebSocketPacket(FrameType.BINARY, (byte[]) message);
        }
        if (message instanceof CharSequence) {
            return new WebSocketPacket(FrameType.TEXT, message.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (sendConvert instanceof TextConvert) {
            return new WebSocketPacket(FrameType.TEXT, ((TextConvert) sendConvert).convertToBytes(message));
        }
        if (sendConvert instanceof BinaryConvert) {
            return new WebSocketPacket(FrameType.BINARY, ((BinaryConvert) sendConvert).convertToBytes(message));
        }
        return new WebSocketPacket(FrameType.TEXT, JsonConvert.root().convertToBytes(message));
    }

    protected boolean tryAcquireSemaphore() {
        if (this.semaphore == null) {
            return true;
        }
        try {
            return this.semaphore.tryAcquire(tryAcquireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    protected void releaseSemaphore() {
        if (this.semaphore != null) {
            this.semaphore.release();
        }
    }
}
