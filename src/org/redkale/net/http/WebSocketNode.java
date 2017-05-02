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

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合
    @Resource(name = "$")
    protected CacheSource<Serializable, InetSocketAddress> sncpAddressNodes;

    //存放本地节点上所有在线用户的队列信息,Set<String> 为 engineid 的集合
    protected final ConcurrentHashMap<Serializable, Set<String>> localEngines = new ConcurrentHashMap();

    protected final ConcurrentHashMap<String, WebSocketEngine> engines = new ConcurrentHashMap();

    public void init(AnyValue conf) {

    }

    public void destroy(AnyValue conf) {

    }

    public final void postDestroy(AnyValue conf) {
        HashMap<Serializable, Set<String>> nodes = new HashMap<>(localEngines);
        nodes.forEach((k, v) -> {
            new HashSet<>(v).forEach(e -> {
                if (engines.containsKey(e)) disconnect(k, e);
            });
        });
    }

    protected abstract List<String> getOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid);

    protected abstract int sendMessage(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid, boolean recent, Object message, boolean last);

    protected abstract void connect(Serializable groupid, InetSocketAddress addr);

    protected abstract void disconnect(Serializable groupid, InetSocketAddress addr);

    //--------------------------------------------------------------------------------
    protected List<String> remoteOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid) {
        if (remoteNode == null) return null;
        try {
            return remoteNode.getOnlineRemoteAddresses(targetAddress, groupid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "remote " + targetAddress + " websocket getOnlineRemoteAddresses error", e);
            return null;
        }
    }

    /**
     * 获取在线用户的节点地址列表
     *
     * @param groupid groupid
     *
     * @return 地址列表
     */
    public Collection<InetSocketAddress> getOnlineNodes(final Serializable groupid) {
        return sncpAddressNodes == null ? null : sncpAddressNodes.getCollection(groupid);
    }

    /**
     * 获取在线用户的详细连接信息
     *
     * @param groupid groupid
     *
     * @return 地址集合
     */
    public Map<InetSocketAddress, List<String>> getOnlineRemoteAddress(final Serializable groupid) {
        Collection<InetSocketAddress> nodes = getOnlineNodes(groupid);
        if (nodes == null) return null;
        final Map<InetSocketAddress, List<String>> map = new HashMap();
        for (InetSocketAddress nodeAddress : nodes) {
            List<String> list = getOnlineRemoteAddresses(nodeAddress, groupid);
            if (list == null) list = new ArrayList();
            map.put(nodeAddress, list);
        }
        return map;
    }

     final void connect(Serializable groupid, String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket connect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localEngines.get(groupid);
        if (engineids == null) {
            engineids = new CopyOnWriteArraySet<>();
            localEngines.putIfAbsent(groupid, engineids);
        }
        if (localSncpAddress != null && engineids.isEmpty()) connect(groupid, localSncpAddress);
        engineids.add(engineid);
    }

    final void disconnect(Serializable groupid, String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket disconnect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localEngines.get(groupid);
        if (engineids == null || engineids.isEmpty()) return;
        engineids.remove(engineid);
        if (engineids.isEmpty()) {
            localEngines.remove(groupid);
            if (localSncpAddress != null) disconnect(groupid, localSncpAddress);
        }
    }

    final void putWebSocketEngine(WebSocketEngine engine) {
        engines.put(engine.getEngineid(), engine);
    }

    public final int sendMessage(Serializable groupid, boolean recent, Object message, boolean last) {
        final Set<String> engineids = localEngines.get(groupid);
        if (finest) logger.finest("websocket want send message {groupid:" + groupid + ", content:'" + message + "'} from locale node to " + engineids);
        int rscode = RETCODE_GROUP_EMPTY;
        if (engineids != null && !engineids.isEmpty()) {
            for (String engineid : engineids) {
                final WebSocketEngine engine = engines.get(engineid);
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
                rscode = remoteNode.sendMessage(one, groupid, recent, message, last);
            } else {
                for (InetSocketAddress addr : addrs) {
                    if (!addr.equals(localSncpAddress)) {
                        rscode |= remoteNode.sendMessage(addr, groupid, recent, message, last);
                    }
                }
            }
        } else {
            rscode = RETCODE_GROUP_EMPTY;
        }
        return rscode;
    }

    //--------------------------------------------------------------------------------
    public final int sendEachMessage(Serializable groupid, String text) {
        return sendMessage(groupid, false, (Object) text, true);
    }

    public final int sendEachMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, (Object) text, last);
    }

    public final int sendRecentMessage(Serializable groupid, String text) {
        return sendMessage(groupid, true, (Object) text, true);
    }

    public final int sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, (Object) text, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text) {
        return sendMessage(groupid, recent, (Object) text, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        return sendMessage(groupid, recent, (Object) text, last);
    }

    //--------------------------------------------------------------------------------
    public final int sendEachMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, false, (Object) data, true);
    }

    public final int sendEachMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, (Object) data, last);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, true, (Object) data, true);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, (Object) data, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data) {
        return sendMessage(groupid, recent, data, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        return sendMessage(groupid, recent, (Object) data, last);
    }

    //--------------------------------------------------------------------------------
    public final int sendEachMessage(Serializable groupid, Object message) {
        return sendMessage(groupid, false, message, true);
    }

    public final int sendEachMessage(Serializable groupid, Object message, boolean last) {
        return sendMessage(groupid, false, message, last);
    }

    public final int sendRecentMessage(Serializable groupid, Object message) {
        return sendMessage(groupid, true, message, true);
    }

    public final int sendRecentMessage(Serializable groupid, Object message, boolean last) {
        return sendMessage(groupid, true, message, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, Object message) {
        return sendMessage(groupid, recent, message, true);
    }

}
