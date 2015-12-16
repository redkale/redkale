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
import org.redkale.net.sncp.*;
import org.redkale.util.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public abstract class WebSocketNode {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    @Resource(name = "SERVER_ADDR")
    protected InetSocketAddress localSncpAddress;  //为SncpServer的服务address

    @SncpRemote
    protected WebSocketNode remoteNode;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合
    protected final ConcurrentHashMap<Serializable, LinkedHashSet<InetSocketAddress>> dataNodes = new ConcurrentHashMap();

    //存放本地节点上所有在线用户的队列信息,Set<String> 为 engineid 的集合
    protected final ConcurrentHashMap<Serializable, Set<String>> localNodes = new ConcurrentHashMap();

    protected final ConcurrentHashMap<String, WebSocketEngine> engines = new ConcurrentHashMap();

    public void init(AnyValue conf) {
        if (remoteNode != null) {
            new Thread() {
                {
                    setDaemon(true);
                }

                @Override
                public void run() {
                    try {
                        Map<Serializable, LinkedHashSet<InetSocketAddress>> map = remoteNode.getDataNodes();
                        if (map != null) dataNodes.putAll(map);
                    } catch (Exception e) {
                        logger.log(Level.INFO, WebSocketNode.class.getSimpleName() + "(" + localSncpAddress + ") not load data nodes ", e);
                    }
                }
            }.start();
        }
    }

    public void destroy(AnyValue conf) {
        HashMap<Serializable, Set<String>> nodes = new HashMap<>(localNodes);
        nodes.forEach((k, v) -> {
            new HashSet<>(v).forEach(e -> {
                if (engines.containsKey(e)) disconnect(k, e);
            });
        });
    }

    public Map<Serializable, LinkedHashSet<InetSocketAddress>> getDataNodes() {
        return dataNodes;
    }

    protected abstract int sendMessage(@SncpParam(SncpParamType.TargetAddress) InetSocketAddress targetAddress, Serializable groupid, boolean recent, Serializable message, boolean last);

    protected abstract void connect(Serializable groupid, InetSocketAddress addr);

    protected abstract void disconnect(Serializable groupid, InetSocketAddress addr);

    //--------------------------------------------------------------------------------
    public final void connect(Serializable groupid, String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket connect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localNodes.get(groupid);
        if (engineids == null) {
            engineids = new CopyOnWriteArraySet<>();
            localNodes.put(groupid, engineids);
        }
        if (localSncpAddress != null && engineids.isEmpty()) connect(groupid, localSncpAddress);
        engineids.add(engineid);
    }

    public final void disconnect(Serializable groupid, String engineid) {
        if (finest) logger.finest(localSncpAddress + " receive websocket disconnect event (" + groupid + " on " + engineid + ").");
        Set<String> engineids = localNodes.get(groupid);
        if (engineids == null || engineids.isEmpty()) return;
        engineids.remove(engineid);
        if (engineids.isEmpty()) {
            localNodes.remove(groupid);
            if (localSncpAddress != null) disconnect(groupid, localSncpAddress);
        }
    }

    public final void putWebSocketEngine(WebSocketEngine engine) {
        engines.put(engine.getEngineid(), engine);
    }

    public final int sendMessage(Serializable groupid, boolean recent, Serializable message, boolean last) {
        final Set<String> engineids = localNodes.get(groupid);
        int rscode = RETCODE_GROUP_EMPTY;
        if (engineids != null && !engineids.isEmpty()) {
            for (String engineid : engineids) {
                final WebSocketEngine engine = engines.get(engineid);
                if (engine != null) { //在本地
                    final WebSocketGroup group = engine.getWebSocketGroup(groupid);
                    if (group == null || group.isEmpty()) {
                        if (finest) logger.finest("receive websocket but result is " + RETCODE_GROUP_EMPTY + " in message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + message + "'}");
                        rscode = RETCODE_GROUP_EMPTY;
                        break;
                    }
                    rscode = group.send(recent, message, last);
                }
            }
        }
        if ((recent && rscode == 0) || remoteNode == null) return rscode;
        LinkedHashSet<InetSocketAddress> addrs = dataNodes.get(groupid);
        if (addrs != null && !addrs.isEmpty()) {   //对方连接在远程节点      
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
        return sendMessage(groupid, false, text);
    }

    public final int sendEachMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, text, last);
    }

    public final int sendRecentMessage(Serializable groupid, String text) {
        return sendMessage(groupid, true, text);
    }

    public final int sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, text, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text) {
        return sendMessage(groupid, recent, text, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        return sendMessage(groupid, recent, (Serializable) text, last);
    }

    //--------------------------------------------------------------------------------
    public final int sendEachMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, false, data);
    }

    public final int sendEachMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, data, last);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, true, data);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, data, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data) {
        return sendMessage(groupid, recent, data, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        return sendMessage(groupid, recent, (Serializable) data, last);
    }
}
