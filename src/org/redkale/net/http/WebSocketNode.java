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

    @Resource(name = Application.RESNAME_SERVER_ADDR)
    protected InetSocketAddress localSncpAddress;  //为SncpServer的服务address

    @RpcRemote
    protected WebSocketNode remoteNode;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合， key: groupid
    @Resource(name = "$")
    protected CacheSource<Serializable, InetSocketAddress> sncpAddressNodes;

    //protected WebSocketEngine onlyoneEngine; 
    
    //存放本地节点上所有WebSocketEngine
    protected final ConcurrentHashMap<String, WebSocketEngine> localEngines = new ConcurrentHashMap();

    //存放本地节点上所有在线用户的队列信息,Set<String> 为 engineid 的集合， key: groupid
    protected final ConcurrentHashMap<Serializable, Set<String>> localEngineids = new ConcurrentHashMap();

    public void init(AnyValue conf) {

    }

    public void destroy(AnyValue conf) {

    }

    public final void postDestroy(AnyValue conf) {
        HashMap<Serializable, Set<String>> engines = new HashMap<>(localEngineids);
        engines.forEach((k, v) -> {
            new HashSet<>(v).forEach(e -> {
                if (localEngines.containsKey(e)) disconnect(k, e);
            });
        });
    }

    protected abstract CompletableFuture<List<String>> getOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid);

    protected abstract CompletableFuture<Integer> sendMessage(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid, boolean recent, Object message, boolean last);

    protected abstract CompletableFuture<Void> connect(Serializable groupid, InetSocketAddress addr);

    protected abstract CompletableFuture<Void> disconnect(Serializable groupid, InetSocketAddress addr);

    //--------------------------------------------------------------------------------
    final void connect(final Serializable groupid, final String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket connect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localEngineids.get(groupid);
        if (engineids == null) {
            engineids = new CopyOnWriteArraySet<>();
            localEngineids.putIfAbsent(groupid, engineids);
        }
        final Set<String> engineids0 = engineids;
        if (localSncpAddress != null && engineids.isEmpty()) {
            CompletableFuture<Void> future = connect(groupid, localSncpAddress);
            if (future != null) {
                future.whenComplete((u, e) -> { //成功才记录
                    if (e != null) engineids0.add(engineid);
                });
            }
        }

    }

    final void disconnect(Serializable groupid, String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket disconnect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localEngineids.get(groupid);
        if (engineids == null || engineids.isEmpty()) return;
        engineids.remove(engineid);
        if (engineids.isEmpty()) {
            localEngineids.remove(groupid);
            if (localSncpAddress != null) disconnect(groupid, localSncpAddress);
        }
    }

    final void putWebSocketEngine(WebSocketEngine engine) {
        localEngines.put(engine.getEngineid(), engine);
    }

    //--------------------------------------------------------------------------------
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

    //异步待优化
    public final CompletableFuture<Integer> sendMessage(final Serializable groupid, final boolean recent, final Object message, final boolean last) {
        return CompletableFuture.supplyAsync(() -> {
            final Set<String> engineids = localEngineids.get(groupid);
            if (finest) logger.finest("websocket want send message {groupid:" + groupid + ", content:'" + message + "'} from locale node to " + engineids);
            int rscode = RETCODE_GROUP_EMPTY;
            if (engineids != null && !engineids.isEmpty()) {
                for (String engineid : engineids) {
                    final WebSocketEngine engine = localEngines.get(engineid);
                    if (engine != null) { //在本地
                        final WebSocketGroup group = engine.getWebSocketGroup(groupid);
                        if (group == null || group.isEmpty()) {
                            engineids.remove(engineid);
                            if (finest) logger.finest("websocket want send message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + message + "'} but websocket group is empty ");
                            rscode = RETCODE_GROUP_EMPTY;
                            break;
                        }
                        rscode = group.send(recent, message, last);
                    }
                }
            }
            if ((recent && rscode == 0) || remoteNode == null || sncpAddressNodes == null) {
                if (finest) {
                    if ((recent && rscode == 0)) {
                        logger.finest("websocket want send recent message success");
                    } else {
                        logger.finest("websocket remote node is null");
                    }
                }
                return rscode;
            }
            //-----------------------发送远程的-----------------------------
            Collection<InetSocketAddress> addrs = sncpAddressNodes.getCollection(groupid);
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
