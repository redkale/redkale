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

    @RpcRemote
    protected WebSocketNode remoteNode;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合， key: groupid
    //如果不是分布式(没有SNCP)，sncpAddressNodes 将不会被用到
    @Resource(name = "$")
    protected CacheSource<Serializable, InetSocketAddress> sncpAddressNodes;

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

    protected abstract CompletableFuture<List<String>> getOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid);

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
     * 获取目标地址
     *
     * @param targetAddress
     * @param groupid
     *
     * @return
     */
    protected CompletableFuture<List<String>> remoteOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid) {
        if (remoteNode == null) return CompletableFuture.completedFuture(null);
        try {
            return remoteNode.getOnlineRemoteAddresses(targetAddress, groupid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "remote " + targetAddress + " websocket getOnlineRemoteAddresses error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 获取在线用户的节点地址列表
     *
     * @param groupid groupid
     *
     * @return 地址列表
     */
    public CompletableFuture<Collection<InetSocketAddress>> getOnlineNodes(final Serializable groupid) {
        return sncpAddressNodes == null ? CompletableFuture.completedFuture(null) : sncpAddressNodes.getCollectionAsync(groupid);
    }

    /**
     * 获取在线用户的详细连接信息
     *
     * @param groupid groupid
     *
     * @return 地址集合
     */
    //异步待优化
    public CompletableFuture<Map<InetSocketAddress, List<String>>> getOnlineRemoteAddress(final Serializable groupid) {
        final CompletableFuture<Map<InetSocketAddress, List<String>>> rs = new CompletableFuture<>();
        CompletableFuture< Collection<InetSocketAddress>> nodesFuture = getOnlineNodes(groupid);
        if (nodesFuture == null) return CompletableFuture.completedFuture(null);
        nodesFuture.whenComplete((nodes, e) -> {
            if (e != null) {
                rs.completeExceptionally(e);
            } else {
                final Map<InetSocketAddress, List<String>> map = new HashMap();
                for (final InetSocketAddress nodeAddress : nodes) {
                    List<String> list = getOnlineRemoteAddresses(nodeAddress, groupid).join();
                    if (list == null) list = new ArrayList();
                    map.put(nodeAddress, list);
                }
                rs.complete(map);
            }
        });
        return rs;
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
    //异步待优化
    public final CompletableFuture<Integer> sendMessage(final Serializable groupid, final boolean recent, final Object message, final boolean last) {
        return CompletableFuture.supplyAsync(() -> {
            if (finest) logger.finest("websocket want send message {groupid:" + groupid + ", content:'" + message + "'} from locale node to locale engine");
            int rscode = RETCODE_GROUP_EMPTY;
            WebSocketGroup group = this.localEngine == null ? null : this.localEngine.getWebSocketGroup(groupid);
            if (group != null) rscode = group.send(recent, message, last);
            if (recent && rscode == 0) { //已经给最近连接发送的消息
                if (finest) logger.finest("websocket want send recent message success");
                return rscode;
            }
            if (this.sncpAddressNodes == null || this.remoteNode == null) {
                if (finest) logger.finest("websocket remote node is null");
                //没有CacheSource就不会有分布式节点
                return rscode;
            }
            //-----------------------发送远程的-----------------------------
            Collection<InetSocketAddress> addrs = sncpAddressNodes.getCollectionAsync(groupid).join();
            if (finest) logger.finest("websocket found groupid:" + groupid + " on " + addrs);
            if (addrs != null && !addrs.isEmpty()) {   //对方连接在远程节点(包含本地节点)，所以正常情况下addrs不会为空。
                if (recent) {
                    InetSocketAddress one = null;
                    for (InetSocketAddress addr : addrs) {
                        one = addr;
                    }
                    rscode = remoteNode.sendMessage(one, groupid, recent, message, last).join();
                } else {
                    for (InetSocketAddress addr : addrs) {
                        if (!addr.equals(localSncpAddress)) {
                            rscode |= remoteNode.sendMessage(addr, groupid, recent, message, last).join();
                        }
                    }
                }
            } else {
                rscode = RETCODE_GROUP_EMPTY;
            }
            return rscode;
        });
    }

    //--------------------------------------------------------------------------------
    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, String text) {
        return sendMessage(groupid, false, (Object) text, true);
    }

    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, (Object) text, last);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, String text) {
        return sendMessage(groupid, true, (Object) text, true);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, (Object) text, last);
    }

    public final CompletableFuture<Integer> sendMessage(Serializable groupid, boolean recent, String text) {
        return sendMessage(groupid, recent, (Object) text, true);
    }

    public final CompletableFuture<Integer> sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        return sendMessage(groupid, recent, (Object) text, last);
    }

    //--------------------------------------------------------------------------------
    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, false, (Object) data, true);
    }

    public final CompletableFuture<Integer> sendEachMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, (Object) data, last);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, true, (Object) data, true);
    }

    public final CompletableFuture<Integer> sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, (Object) data, last);
    }

    public final CompletableFuture<Integer> sendMessage(Serializable groupid, boolean recent, byte[] data) {
        return sendMessage(groupid, recent, data, true);
    }

    public final CompletableFuture<Integer> sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        return sendMessage(groupid, recent, (Object) data, last);
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

}
