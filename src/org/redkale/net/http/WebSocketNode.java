/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static org.redkale.net.http.WebSocket.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Stream;
import javax.annotation.*;
import org.redkale.boot.*;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
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
public abstract class WebSocketNode {

    @Comment("存储用户ID的key前缀")
    public static final String SOURCE_SNCP_USERID_PREFIX = "sncpws_uid:";

    @Comment("存储当前SNCP节点列表的key")
    public static final String SOURCE_SNCP_ADDRS_KEY = "sncpws_addrs";

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //"SNCP_ADDR" 如果不是分布式(没有SNCP) 值为null
    @Resource(name = Application.RESNAME_SNCP_ADDR)
    protected InetSocketAddress localSncpAddress;  //为SncpServer的服务address

    //如果不是分布式(没有SNCP) 值为null
    @RpcRemote
    protected WebSocketNode remoteNode;

    @Resource(name = "$_sendconvert")
    protected Convert sendConvert;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合， key: groupid
    //集合包含 localSncpAddress
    //如果不是分布式(没有SNCP)，sncpNodeAddresses 将不会被用到
    @Resource(name = "$")
    protected CacheSource<InetSocketAddress> sncpNodeAddresses;

    //当前节点的本地WebSocketEngine
    protected WebSocketEngine localEngine;

    protected Semaphore semaphore;

    private int tryAcquireSeconds = 12;

    public void init(AnyValue conf) {
        this.tryAcquireSeconds = Integer.getInteger("WebSocketNode.tryAcquireSeconds", 12);

        if (sncpNodeAddresses != null && "memory".equals(sncpNodeAddresses.getType())) {
            sncpNodeAddresses.initValueType(InetSocketAddress.class);
        }
        if (localEngine != null) {
            int wsthreads = localEngine.wsthreads;
            if (wsthreads == 0) wsthreads = Runtime.getRuntime().availableProcessors() * 8;
            if (wsthreads > 0) this.semaphore = new Semaphore(wsthreads);
        }
    }

    public void destroy(AnyValue conf) {
    }

    @Local
    public final Semaphore getSemaphore() {
        return semaphore;
    }

    @Local
    protected void postDestroy(AnyValue conf) {
        if (this.localEngine == null) return;
        //关掉所有本地本地WebSocket
        this.localEngine.getLocalWebSockets().forEach(g -> g.close());
        if (sncpNodeAddresses != null && localSncpAddress != null) {
            sncpNodeAddresses.removeSetItem(SOURCE_SNCP_ADDRS_KEY, InetSocketAddress.class, localSncpAddress);
        }
    }

    protected abstract CompletableFuture<List<String>> getWebSocketAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable userid);

    protected abstract CompletableFuture<Integer> sendMessage(@RpcTargetAddress InetSocketAddress targetAddress, Object message, boolean last, Serializable... userids);

    protected abstract CompletableFuture<Integer> broadcastMessage(@RpcTargetAddress InetSocketAddress targetAddress, WebSocketRange wsrange, Object message, boolean last);

    protected abstract CompletableFuture<Integer> sendAction(@RpcTargetAddress InetSocketAddress targetAddress, WebSocketAction action, Serializable... userids);

    protected abstract CompletableFuture<Integer> broadcastAction(@RpcTargetAddress InetSocketAddress targetAddress, WebSocketAction action);

    protected abstract CompletableFuture<Void> connect(Serializable userid, InetSocketAddress sncpAddr);

    protected abstract CompletableFuture<Void> disconnect(Serializable userid, InetSocketAddress sncpAddr);

    protected abstract CompletableFuture<Void> changeUserid(Serializable fromuserid, Serializable touserid, InetSocketAddress sncpAddr);

    protected abstract CompletableFuture<Boolean> existsWebSocket(Serializable userid, @RpcTargetAddress InetSocketAddress targetAddress);

    protected abstract CompletableFuture<Integer> forceCloseWebSocket(Serializable userid, @RpcTargetAddress InetSocketAddress targetAddress);

    //--------------------------------------------------------------------------------
    final CompletableFuture<Void> connect(final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) logger.finest(localSncpAddress + " receive websocket connect event (" + userid + " on " + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        return connect(userid, localSncpAddress);
    }

    final CompletableFuture<Void> disconnect(final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) logger.finest(localSncpAddress + " receive websocket disconnect event (" + userid + " on " + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        return disconnect(userid, localSncpAddress);
    }

    final CompletableFuture<Void> changeUserid(Serializable olduserid, final Serializable newuserid) {
        if (logger.isLoggable(Level.FINEST)) logger.finest(localSncpAddress + " receive websocket changeUserid event (from " + olduserid + " to " + newuserid + " on " + (this.localEngine == null ? null : this.localEngine.getEngineid()) + ").");
        return changeUserid(olduserid, newuserid, localSncpAddress);
    }

    //--------------------------------------------------------------------------------
    /**
     * 获取目标地址 <br>
     * 该方法仅供内部调用
     *
     * @param targetAddress InetSocketAddress
     * @param userid        Serializable
     *
     * @return 客户端地址列表
     */
    protected CompletableFuture<List<String>> remoteWebSocketAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable userid) {
        if (remoteNode == null) return CompletableFuture.completedFuture(null);
        try {
            return remoteNode.getWebSocketAddresses(targetAddress, userid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "remote " + targetAddress + " websocket getOnlineRemoteAddresses error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 获取用户在线的SNCP节点地址列表，不是分布式则返回元素数量为1，且元素值为null的列表<br>
     * InetSocketAddress 为 SNCP节点地址
     *
     * @param userid Serializable
     *
     * @return 地址列表
     */
    public CompletableFuture<Collection<InetSocketAddress>> getRpcNodeAddresses(final Serializable userid) {
        if (this.sncpNodeAddresses != null) {
            tryAcquireSemaphore();
            CompletableFuture<Collection<InetSocketAddress>> result = this.sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class);
            if (semaphore != null) result.whenComplete((r, e) -> releaseSemaphore());
            return result;
        }
        List<InetSocketAddress> rs = new ArrayList<>();
        rs.add(this.localSncpAddress);
        return CompletableFuture.completedFuture(rs);
    }

    /**
     * 获取在线用户的详细连接信息 <br>
     * Map.key 为 SNCP节点地址, 含值为null的key表示没有分布式
     * Map.value 为 用户客户端的IP
     *
     * @param userid Serializable
     *
     * @return 地址集合
     */
    public CompletableFuture<Map<InetSocketAddress, List<String>>> getRpcNodeWebSocketAddresses(final Serializable userid) {
        CompletableFuture<Collection<InetSocketAddress>> sncpFuture = getRpcNodeAddresses(userid);
        return sncpFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket found userid:" + userid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(new HashMap<>());
            CompletableFuture<Map<InetSocketAddress, List<String>>> future = null;
            for (final InetSocketAddress nodeAddress : addrs) {
                CompletableFuture<Map<InetSocketAddress, List<String>>> mapFuture = getWebSocketAddresses(nodeAddress, userid)
                    .thenCompose((List<String> list) -> CompletableFuture.completedFuture(Utility.ofMap(nodeAddress, list)));
                future = future == null ? mapFuture : future.thenCombine(mapFuture, (a, b) -> Utility.merge(a, b));
            }
            return future == null ? CompletableFuture.completedFuture(new HashMap<>()) : future;
        });
    }

    /**
     * 获取在线用户总数
     *
     *
     * @return boolean
     */
    public CompletableFuture<Integer> getUserSize() {
        if (this.localEngine != null && this.sncpNodeAddresses == null) {
            return CompletableFuture.completedFuture(this.localEngine.getLocalUserSize());
        }
        tryAcquireSemaphore();
        CompletableFuture<Integer> rs = this.sncpNodeAddresses.queryKeysStartsWithAsync(SOURCE_SNCP_USERID_PREFIX).thenApply(v -> v.size());
        if (semaphore != null) rs.whenComplete((r, e) -> releaseSemaphore());
        return rs;
    }

    /**
     * 判断指定用户是否WebSocket在线
     *
     * @param userid Serializable
     *
     * @return boolean
     */
    @Local
    public CompletableFuture<Boolean> existsWebSocket(final Serializable userid) {
        CompletableFuture<Boolean> localFuture = null;
        if (this.localEngine != null) localFuture = CompletableFuture.completedFuture(localEngine.existsLocalWebSocket(userid));
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return localFuture;
        }
        //远程节点关闭
        tryAcquireSemaphore();
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class);
        if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        CompletableFuture<Boolean> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket found userid:" + userid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(false);
            CompletableFuture<Boolean> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.existsWebSocket(userid, addr)
                    : future.thenCombine(remoteNode.existsWebSocket(userid, addr), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(false) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    /**
     * 强制关闭用户WebSocket
     *
     * @param userid Serializable
     *
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
     *
     * @return int
     */
    @Local
    public CompletableFuture<Integer> forceCloseWebSocket(final WebSocketUserAddress userAddress) {
        return forceCloseWebSocket(null, userAddress);
    }

    private CompletableFuture<Integer> forceCloseWebSocket(final Serializable userid, final WebSocketUserAddress userAddress) {
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) localFuture = CompletableFuture.completedFuture(localEngine.forceCloseLocalWebSocket(userAddress == null ? userid : userAddress.userid()));
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return localFuture;
        }
        //远程节点关闭
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture;
        if (userAddress == null) {
            tryAcquireSemaphore();
            addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class);
            if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        } else {
            Collection<InetSocketAddress> addrs = userAddress.sncpAddresses();
            if (userAddress.sncpAddress() != null) {
                if (addrs == null) addrs = new ArrayList<>();
                addrs.add(userAddress.sncpAddress());
            }
            addrsFuture = CompletableFuture.completedFuture(addrs);
        }
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket found userid:" + userid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(0);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.forceCloseWebSocket(userid, addr)
                    : future.thenCombine(remoteNode.forceCloseWebSocket(userid, addr), (a, b) -> a + b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a + b);
    }

    //--------------------------------------------------------------------------------
    /**
     * 获取本地的WebSocketEngine，没有则返回null
     *
     *
     * @return WebSocketEngine
     */
    @Local
    public final WebSocketEngine getLocalWebSocketEngine() {
        return this.localEngine;
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(Object message, final Stream<? extends Serializable> userids) {
        return sendMessage((Convert) null, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(Object message, final Serializable... userids) {
        return sendMessage((Convert) null, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message 消息内容
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, final Stream<? extends Serializable> userids) {
        return sendMessage(convert, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert Convert
     * @param message 消息内容
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, final Serializable... userids) {
        return sendMessage(convert, message, true, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param last    是否最后一条
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(final Object message, final boolean last, final Stream<? extends Serializable> userids) {
        return sendMessage((Convert) null, message, last, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param message 消息内容
     * @param last    是否最后一条
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(final Object message, final boolean last, final Serializable... userids) {
        return sendMessage((Convert) null, message, last, userids);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert  Convert
     * @param message0 消息内容
     * @param last     是否最后一条
     * @param userids  Stream
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> sendMessage(final Convert convert, final Object message0, final boolean last, final Stream<? extends Serializable> userids) {
        Object[] array = userids.toArray();
        Serializable[] ss = new Serializable[array.length];
        for (int i = 0; i < array.length; i++) {
            ss[i] = (Serializable) array[i];
        }
        return sendMessage(convert, message0, last, ss);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param convert  Convert
     * @param message0 消息内容
     * @param last     是否最后一条
     * @param userids  Serializable[]
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendMessage(final Convert convert, final Object message0, final boolean last, final Serializable... userids) {
        if (userids == null || userids.length < 1) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        if (message0 instanceof CompletableFuture) return ((CompletableFuture) message0).thenApply(msg -> sendMessage(convert, msg, last, userids));
        final Object message = (convert == null || message0 instanceof WebSocketPacket) ? message0 : ((convert instanceof TextConvert) ? new WebSocketPacket(((TextConvert) convert).convertTo(message0), last) : new WebSocketPacket(((BinaryConvert) convert).convertTo(message0), last));
        if (this.localEngine != null && this.sncpNodeAddresses == null) { //本地模式且没有分布式
            return this.localEngine.sendLocalMessage(message, last, userids);
        }
        final Object remoteMessage = formatRemoteMessage(message);
        CompletableFuture<Integer> rsfuture;
        if (userids.length == 1) {
            rsfuture = sendOneUserMessage(remoteMessage, last, userids[0]);
        } else {
            String[] keys = new String[userids.length];
            final Map<String, Serializable> keyuser = new HashMap<>();
            for (int i = 0; i < userids.length; i++) {
                keys[i] = SOURCE_SNCP_USERID_PREFIX + userids[i];
                keyuser.put(keys[i], userids[i]);
            }
            tryAcquireSemaphore();
            CompletableFuture<Map<String, Collection<InetSocketAddress>>> addrsFuture = sncpNodeAddresses.getCollectionMapAsync(true, InetSocketAddress.class, keys);
            if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
            rsfuture = addrsFuture.thenCompose((Map<String, Collection<InetSocketAddress>> addrs) -> {
                if (addrs == null || addrs.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) logger.finer("websocket not found userids:" + JsonConvert.root().convertTo(userids) + " on any node ");
                    return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
                }
                Map<InetSocketAddress, List<Serializable>> addrUsers = new HashMap<>();
                addrs.forEach((key, as) -> {
                    for (InetSocketAddress a : as) {
                        addrUsers.computeIfAbsent(a, k -> new ArrayList<>()).add(keyuser.get(key));
                    }
                });
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("websocket(localaddr=" + localSncpAddress + ", userids=" + JsonConvert.root().convertTo(userids) + ") found message-addr-userids: " + addrUsers);
                }
                CompletableFuture<Integer> future = null;
                for (Map.Entry<InetSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
                    Serializable[] oneaddrUserids = en.getValue().toArray(new Serializable[en.getValue().size()]);
                    future = future == null ? sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids)
                        : future.thenCombine(sendOneAddrMessage(en.getKey(), remoteMessage, last, oneaddrUserids), (a, b) -> a | b);
                }
                return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
            });
        }
        return rsfuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : rsfuture;
    }

    protected CompletableFuture<Integer> sendOneUserMessage(final Object message, final boolean last, final Serializable userid) {
        if (message instanceof CompletableFuture) return ((CompletableFuture) message).thenApply(msg -> sendOneUserMessage(msg, last, userid));
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send message {userid:" + userid + ", content:'" + (message instanceof WebSocketPacket ? ((WebSocketPacket) message).toSimpleString() : JsonConvert.root().convertTo(message)) + "'} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) localFuture = localEngine.sendLocalMessage(message, last, userid);
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localFuture;
        }
        //远程节点发送消息
        final Object remoteMessage = formatRemoteMessage(message);
        tryAcquireSemaphore();
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class);
        if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (addrs == null || addrs.isEmpty()) {
                if (logger.isLoggable(Level.FINER)) logger.finer("websocket not found userid:" + userid + " on any node ");
                return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
            }
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket(localaddr=" + localSncpAddress + ") found userid:" + userid + " on " + addrs);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.sendMessage(addr, remoteMessage, last, userid)
                    : future.thenCombine(remoteNode.sendMessage(addr, remoteMessage, last, userid), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    protected CompletableFuture<Integer> sendOneAddrMessage(final InetSocketAddress sncpAddr, final Object message, final boolean last, final Serializable... userids) {
        if (message instanceof CompletableFuture) return ((CompletableFuture) message).thenApply(msg -> sendOneAddrMessage(sncpAddr, msg, last, userids));
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send message {userids:" + JsonConvert.root().convertTo(userids) + ", sncpaddr:" + sncpAddr + ", content:'" + (message instanceof WebSocketPacket ? ((WebSocketPacket) message).toSimpleString() : JsonConvert.root().convertTo(message)) + "'} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        if (Objects.equals(sncpAddr, this.localSncpAddress)) {
            return this.localEngine == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localEngine.sendLocalMessage(message, last, userids);
        }
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        final Object remoteMessage = formatRemoteMessage(message);
        return remoteNode.sendMessage(sncpAddr, remoteMessage, last, userids);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     *
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
     *
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
     *
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
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Convert convert, final Object message) {
        return broadcastMessage(wsrange, convert, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     * @param last    是否最后一条
     *
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
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message, final boolean last) {
        return broadcastMessage(wsrange, (Convert) null, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param convert  Convert
     * @param message0 消息内容
     * @param last     是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public final CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message0, final boolean last) {
        return broadcastMessage((WebSocketRange) null, convert, message0, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange  过滤条件
     * @param convert  Convert
     * @param message0 消息内容
     * @param last     是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Convert convert, final Object message0, final boolean last) {
        if (message0 instanceof CompletableFuture) return ((CompletableFuture) message0).thenApply(msg -> broadcastMessage(wsrange, convert, msg, last));
        final Object message = (convert == null || message0 instanceof WebSocketPacket) ? message0 : ((convert instanceof TextConvert) ? new WebSocketPacket(((TextConvert) convert).convertTo(message0), last) : new WebSocketPacket(((BinaryConvert) convert).convertTo(message0), last));
        if (this.localEngine != null && this.sncpNodeAddresses == null) { //本地模式且没有分布式
            return this.localEngine.broadcastLocalMessage(wsrange, message, last);
        }
        final Object remoteMessage = formatRemoteMessage(message);
        CompletableFuture<Integer> localFuture = this.localEngine == null ? null : this.localEngine.broadcastLocalMessage(wsrange, message, last);
        tryAcquireSemaphore();
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_ADDRS_KEY, InetSocketAddress.class);
        if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket broadcast message (" + remoteMessage + ") on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(0);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.broadcastMessage(addr, wsrange, remoteMessage, last)
                    : future.thenCombine(remoteNode.broadcastMessage(addr, wsrange, remoteMessage, last), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    /**
     * 广播操作， 给所有人发操作
     *
     * @param action 操作参数
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> broadcastAction(final WebSocketAction action) {
        if (this.localEngine != null && this.sncpNodeAddresses == null) { //本地模式且没有分布式
            return this.localEngine.broadcastLocalAction(action);
        }
        CompletableFuture<Integer> localFuture = this.localEngine == null ? null : this.localEngine.broadcastLocalAction(action);
        tryAcquireSemaphore();
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_ADDRS_KEY, InetSocketAddress.class);
        if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket broadcast action (" + action + ") on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(0);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.broadcastAction(addr, action)
                    : future.thenCombine(remoteNode.broadcastAction(addr, action), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    /**
     * 向指定用户发送操作，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param action  操作参数
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    @Local
    public CompletableFuture<Integer> sendAction(final WebSocketAction action, final Serializable... userids) {
        if (userids == null || userids.length < 1) return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        if (this.localEngine != null && this.sncpNodeAddresses == null) { //本地模式且没有分布式
            return this.localEngine.sendLocalAction(action, userids);
        }
        CompletableFuture<Integer> rsfuture;
        if (userids.length == 1) {
            rsfuture = sendOneUserAction(action, userids[0]);
        } else {
            String[] keys = new String[userids.length];
            final Map<String, Serializable> keyuser = new HashMap<>();
            for (int i = 0; i < userids.length; i++) {
                keys[i] = SOURCE_SNCP_USERID_PREFIX + userids[i];
                keyuser.put(keys[i], userids[i]);
            }
            tryAcquireSemaphore();
            CompletableFuture<Map<String, Collection<InetSocketAddress>>> addrsFuture = sncpNodeAddresses.getCollectionMapAsync(true, InetSocketAddress.class, keys);
            if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
            rsfuture = addrsFuture.thenCompose((Map<String, Collection<InetSocketAddress>> addrs) -> {
                if (addrs == null || addrs.isEmpty()) {
                    if (logger.isLoggable(Level.FINER)) logger.finer("websocket not found userids:" + JsonConvert.root().convertTo(userids) + " on any node ");
                    return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
                }
                Map<InetSocketAddress, List<Serializable>> addrUsers = new HashMap<>();
                addrs.forEach((key, as) -> {
                    for (InetSocketAddress a : as) {
                        addrUsers.computeIfAbsent(a, k -> new ArrayList<>()).add(keyuser.get(key));
                    }
                });
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("websocket(localaddr=" + localSncpAddress + ", userids=" + JsonConvert.root().convertTo(userids) + ") found action-userid-addrs: " + addrUsers);
                }
                CompletableFuture<Integer> future = null;
                for (Map.Entry<InetSocketAddress, List<Serializable>> en : addrUsers.entrySet()) {
                    Serializable[] oneaddrUserids = en.getValue().toArray(new Serializable[en.getValue().size()]);
                    future = future == null ? sendOneAddrAction(en.getKey(), action, oneaddrUserids)
                        : future.thenCombine(sendOneAddrAction(en.getKey(), action, oneaddrUserids), (a, b) -> a | b);
                }
                return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
            });
        }
        return rsfuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : rsfuture;
    }

    protected CompletableFuture<Integer> sendOneUserAction(final WebSocketAction action, final Serializable userid) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send action {userid:" + userid + ", action:" + action + "} from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        CompletableFuture<Integer> localFuture = null;
        if (this.localEngine != null) localFuture = localEngine.sendLocalAction(action, userid);
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localFuture;
        }
        //远程节点发送操作
        tryAcquireSemaphore();
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(SOURCE_SNCP_USERID_PREFIX + userid, InetSocketAddress.class);
        if (semaphore != null) addrsFuture.whenComplete((r, e) -> releaseSemaphore());
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (addrs == null || addrs.isEmpty()) {
                if (logger.isLoggable(Level.FINER)) logger.finer("websocket not found userid:" + userid + " on any node ");
                return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
            }
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket(localaddr=" + localSncpAddress + ") found userid:" + userid + " on " + addrs);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.sendAction(addr, action, userid)
                    : future.thenCombine(remoteNode.sendAction(addr, action, userid), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

    protected CompletableFuture<Integer> sendOneAddrAction(final InetSocketAddress sncpAddr, final WebSocketAction action, final Serializable... userids) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("websocket want send action {userids:" + JsonConvert.root().convertTo(userids) + ", sncpaddr:" + sncpAddr + ", action:" + action + " from locale node to " + ((this.localEngine != null) ? "locale" : "remote") + " engine");
        }
        if (Objects.equals(sncpAddr, this.localSncpAddress)) {
            return this.localEngine == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localEngine.sendLocalAction(action, userids);
        }
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (logger.isLoggable(Level.FINEST)) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY);
        }
        return remoteNode.sendAction(sncpAddr, action, userids);
    }

    protected Object formatRemoteMessage(Object message) {
        if (message instanceof WebSocketPacket) return message;
        if (message instanceof byte[]) return message;
        if (message instanceof CharSequence) return message;
        if (sendConvert instanceof TextConvert) ((TextConvert) sendConvert).convertTo(message);
        if (sendConvert instanceof BinaryConvert) ((BinaryConvert) sendConvert).convertTo(message);
        return JsonConvert.root().convertTo(message);
    }

    protected boolean tryAcquireSemaphore() {
        if (this.semaphore == null) return true;
        try {
            return this.semaphore.tryAcquire(tryAcquireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    protected void releaseSemaphore() {
        if (this.semaphore != null) this.semaphore.release();
    }
}
