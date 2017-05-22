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
import javax.annotation.*;
import org.redkale.boot.*;
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

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    //"SNCP_ADDR" 如果不是分布式(没有SNCP) 值为null
    @Resource(name = Application.RESNAME_SNCP_ADDR)
    protected InetSocketAddress localSncpAddress;  //为SncpServer的服务address

    //如果不是分布式(没有SNCP) 值为null
    @RpcRemote
    protected WebSocketNode remoteNode;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合， key: groupid
    //集合包含 localSncpAddress
    //如果不是分布式(没有SNCP)，sncpNodeAddresses 将不会被用到
    @Resource(name = "$")
    protected CacheSource<Serializable, InetSocketAddress> sncpNodeAddresses;

    //当前节点的本地WebSocketEngine
    protected WebSocketEngine localEngine;

    public void init(AnyValue conf) {
    }

    public void destroy(AnyValue conf) {
    }

    public final void postDestroy(AnyValue conf) {
        if (this.localEngine == null) return;
        //关掉所有本地本地WebSocket
        this.localEngine.getWebSocketGroups().forEach(g -> disconnect(g.getGroupid()));
    }

    protected abstract CompletableFuture<List<String>> getWebSocketAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid);

    protected abstract CompletableFuture<Integer> sendMessage(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid, boolean recent, Object message, boolean last);

    protected abstract CompletableFuture<Void> connect(Serializable groupid, InetSocketAddress addr);

    protected abstract CompletableFuture<Void> disconnect(Serializable groupid, InetSocketAddress addr);

    //--------------------------------------------------------------------------------
    final CompletableFuture<Void> connect(final Serializable groupid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket connect event (" + groupid + " on " + this.localEngine.getEngineid() + ").");
        return connect(groupid, localSncpAddress);
    }

    final CompletableFuture<Void> disconnect(Serializable groupid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket disconnect event (" + groupid + " on " + this.localEngine.getEngineid() + ").");
        return disconnect(groupid, localSncpAddress);
    }

    //--------------------------------------------------------------------------------
    /**
     * 获取目标地址 <br>
     * 该方法仅供内部调用
     *
     * @param targetAddress InetSocketAddress
     * @param groupid       Serializable
     *
     * @return 客户端地址列表
     */
    protected CompletableFuture<List<String>> remoteWebSocketAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid) {
        if (remoteNode == null) return CompletableFuture.completedFuture(null);
        try {
            return remoteNode.getWebSocketAddresses(targetAddress, groupid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "remote " + targetAddress + " websocket getOnlineRemoteAddresses error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 获取用户在线的SNCP节点地址列表，不是分布式则返回元素数量为1，且元素值为null的列表<br>
     * InetSocketAddress 为 SNCP节点地址
     *
     * @param groupid Serializable
     *
     * @return 地址列表
     */
    public CompletableFuture<Collection<InetSocketAddress>> getSncpNodeAddresses(final Serializable groupid) {
        if (this.sncpNodeAddresses != null) return this.sncpNodeAddresses.getCollectionAsync(groupid);
        List<InetSocketAddress> rs = new ArrayList<>();
        rs.add(this.localSncpAddress);
        return CompletableFuture.completedFuture(rs);
    }

    /**
     * 获取在线用户的详细连接信息 <br>
     * Map.key 为 SNCP节点地址, 含值为null的key表示没有分布式
     * Map.value 为 用户客户端的IP
     *
     * @param groupid Serializable
     *
     * @return 地址集合
     */
    public CompletableFuture<Map<InetSocketAddress, List<String>>> getSncpNodeWebSocketAddresses(final Serializable groupid) {
        CompletableFuture<Collection<InetSocketAddress>> sncpFuture = getSncpNodeAddresses(groupid);
        return sncpFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (finest) logger.finest("websocket found groupid:" + groupid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(new HashMap<>());
            CompletableFuture<Map<InetSocketAddress, List<String>>> future = null;
            for (final InetSocketAddress nodeAddress : addrs) {
                CompletableFuture<Map<InetSocketAddress, List<String>>> mapFuture = getWebSocketAddresses(nodeAddress, groupid)
                    .thenCompose((List<String> list) -> CompletableFuture.completedFuture(Utility.ofMap(nodeAddress, list)));
                future = future == null ? mapFuture : future.thenCombine(mapFuture, (a, b) -> Utility.merge(a, b));
            }
            return future == null ? CompletableFuture.completedFuture(new HashMap<>()) : future;
        });
    }

    //--------------------------------------------------------------------------------
    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, Object message) {
        return sendMessage(groupid, false, message, true);
    }

    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, Object message, boolean last) {
        return sendMessage(groupid, false, message, last);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, Object message) {
        return sendMessage(groupid, true, message, true);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, Object message, boolean last) {
        return sendMessage(groupid, true, message, last);
    }

    public final CompletableFuture<Integer> sendMessage(Serializable groupid, boolean recent, Object message) {
        return sendMessage(groupid, recent, message, true);
    }

    /**
     * 向指定用户发送消息，先发送本地连接，再发送远程连接  <br>
     * 如果当前WebSocketNode是远程模式，此方法只发送远程连接
     *
     * @param groupid String
     * @param recent  是否只发送给最近接入的WebSocket节点
     * @param message 消息内容
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示异常
     */
    //最近连接发送逻辑还没有理清楚 
    public final CompletableFuture<Integer> sendMessage(final Serializable groupid, final boolean recent, final Object message, final boolean last) {
        if (finest) logger.finest("websocket want send message {groupid:" + groupid + ", content:'" + message + "'} from locale node to locale engine");
        CompletableFuture<Integer> localFuture = null;
        final WebSocketGroup group = this.localEngine == null ? null : this.localEngine.getWebSocketGroup(groupid);
        if (group != null) localFuture = group.send(recent, message, last);
        if (recent && localFuture != null) { //已经给最近连接发送的消息
            if (finest) logger.finest("websocket want send recent message success");
            return localFuture;
        }
        if (this.sncpNodeAddresses == null || this.remoteNode == null) {
            if (finest) logger.finest("websocket remote node is null");
            //没有CacheSource就不会有分布式节点
            return localFuture == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : localFuture;
        }
        //远程节点发送消息
        CompletableFuture<Collection<InetSocketAddress>> addrsFuture = sncpNodeAddresses.getCollectionAsync(groupid);
        CompletableFuture<Integer> remoteFuture = addrsFuture.thenCompose((Collection<InetSocketAddress> addrs) -> {
            if (finest) logger.finest("websocket found groupid:" + groupid + " on " + addrs);
            if (addrs == null || addrs.isEmpty()) return CompletableFuture.completedFuture(0);
            CompletableFuture<Integer> future = null;
            for (InetSocketAddress addr : addrs) {
                if (addr == null || addr.equals(localSncpAddress)) continue;
                future = future == null ? remoteNode.sendMessage(addr, groupid, recent, message, last)
                    : future.thenCombine(remoteNode.sendMessage(addr, groupid, recent, message, last), (a, b) -> a | b);
            }
            return future == null ? CompletableFuture.completedFuture(0) : future;
        });
        return localFuture == null ? remoteFuture : localFuture.thenCombine(remoteFuture, (a, b) -> a | b);
    }

}
