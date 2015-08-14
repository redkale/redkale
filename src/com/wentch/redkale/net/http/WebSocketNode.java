/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;

/**
 *
 * @author zhangjx
 */
public abstract class WebSocketNode {

    public static final int RETCODE_ENGINE_NULL = 5001;

    public static final int RETCODE_NODESERVICE_NULL = 5002;

    public static final int RETCODE_GROUP_EMPTY = 5005;

    public static final int RETCODE_WSOFFLINE = 5011;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    @Resource(name = "SERVER_ADDR")
    protected InetSocketAddress localSncpAddress;  //为SncpServer的服务address

    @SncpRemote
    protected WebSocketNode remoteNode;

    //存放所有用户分布在节点上的队列信息,Set<InetSocketAddress> 为 sncpnode 的集合
    protected final ConcurrentHashMap<Serializable, Set<InetSocketAddress>> dataNodes = new ConcurrentHashMap();

    //存放所有用户分布在节点上的队列信息,Set<String> 为 engineid 的集合
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
                        Map<Serializable, Set<InetSocketAddress>> map = remoteNode.getDataNodes();
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

    public Map<Serializable, Set<InetSocketAddress>> getDataNodes() {
        return dataNodes;
    }

    protected abstract int sendMessage(@SncpParameter InetSocketAddress addr, Serializable groupid, boolean recent, Serializable message, boolean last);

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
        int rscode = 0;
        if (engineids != null && !engineids.isEmpty()) {
            for (String engineid : engineids) {
                final WebSocketEngine engine = engines.get(engineid);
                if (engine != null) { //在本地
                    final WebSocketGroup group = engine.getWebSocketGroup(groupid);
                    if (group == null || group.isEmpty()) {
                        if (finest) logger.finest("receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + message + "'} but result is " + RETCODE_GROUP_EMPTY);
                        rscode = RETCODE_GROUP_EMPTY;
                        break;
                    }
                    group.send(recent, message, last);
                }
            }
        }
        if ((recent && rscode == 0) || remoteNode == null) return rscode;
        Set<InetSocketAddress> addrs = dataNodes.get(groupid);
        if (addrs != null && !addrs.isEmpty()) {   //对方连接在远程节点       
            for (InetSocketAddress addr : addrs) {
                if (!addr.equals(localSncpAddress)) {
                    remoteNode.sendMessage(addr, groupid, recent, message, last);
                }
            }
        } else {
            rscode = RETCODE_GROUP_EMPTY;
        }
        return rscode;
    }

    //--------------------------------------------------------------------------------
    public final int sendMessage(Serializable groupid, String text) {
        return sendMessage(groupid, false, text);
    }

    public final int sendMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, text, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text) {
        return sendMessage(groupid, recent, text, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        return sendMessage(groupid, recent, (Serializable) text, last);
    }

    //--------------------------------------------------------------------------------
    public final int sendMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, false, data);
    }

    public final int sendMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, data, last);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data) {
        return sendMessage(groupid, recent, data, true);
    }

    public final int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        return sendMessage(groupid, recent, (Serializable) data, last);
    }
}
