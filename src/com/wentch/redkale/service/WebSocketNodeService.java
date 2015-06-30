/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.net.http.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class WebSocketNodeService implements Service {

    public static final int RETCODE_ENGINE_NULL = 5001;

    public static final int RETCODE_NODESERVICE_NULL = 5002;

    public static final int RETCODE_GROUP_EMPTY = 5005;

    public static final int RETCODE_WSOFFLINE = 5011;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    @Resource(name = "APP_NODE")
    protected String localNodeName = "";

    @Resource
    protected HashMap<String, WebSocketNodeService> nodemaps;

    //用户分布在节点上的队列信息,只保存远程节点的用户分布信息
    protected final ConcurrentHashMap<Serializable, Set<String>> usernodes = new ConcurrentHashMap();

    protected final ConcurrentHashMap<String, WebSocketEngine> engines = new ConcurrentHashMap();

    public void initUserNodes() {
        if (this.nodemaps == null || this.nodemaps.isEmpty()) return;
        new Thread() {
            {
                setDaemon(true);
            }

            @Override
            public void run() {
                usernodes.putAll(queryNodes());
            }
        }.start();
    }

    public final void addWebSocketEngine(WebSocketEngine engine) {
        engines.put(engine.getEngineid(), engine);
    }

    @RemoteOn
    public Map<Serializable, Set<String>> queryNodes() {
        Map<Serializable, Set<String>> rs = new HashMap<>();
        this.nodemaps.forEach((x, y) -> {
            if (!rs.isEmpty()) return;
            try {
                rs.putAll(y.queryNodes());
            } catch (Exception e) {
                logger.log(Level.WARNING, this.getClass().getSimpleName() + " query error (" + x + ")", e);
            }
        });
        return rs;
    }

    public final Map<Serializable, Set<String>> onQueryNodes() {
        Map<Serializable, Set<String>> rs = new HashMap<>();
        rs.putAll(this.usernodes);
        return rs;
    }

    public void connectSelf(Serializable userid) {
        connect(this.localNodeName, userid);
    }

    public void disconnectSelf(Serializable userid) {
        disconnect(this.localNodeName, userid);
    }

    @RemoteOn
    public void connect(String nodeid, Serializable userid) {
        onConnect(nodeid, userid);
        if (this.nodemaps == null) return;
        this.nodemaps.forEach((x, y) -> {
            try {
                if (finest) logger.finest("Node(" + localNodeName + "->" + x + ") send websocket connect event (" + userid + " on " + nodeid + ")");
                y.connect(nodeid, userid);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Node(" + localNodeName + "->" + x + ") send websocket connect event (" + userid + " on " + nodeid + ")", e);
            }
        });
    }

    public final void onConnect(String nodeid, Serializable userid) {
        if (finest) logger.finest("Node (" + localNodeName + ") receive websocket connect event (" + userid + " on " + nodeid + ").");
        Set<String> userNodelist = usernodes.get(userid);
        if (userNodelist == null) {
            userNodelist = new CopyOnWriteArraySet<>();
            usernodes.put(userid, userNodelist);
        }
        userNodelist.add(nodeid);
    }

    @RemoteOn
    public void disconnect(String nodeid, Serializable userid) {
        onDisconnect(nodeid, userid);
        if (this.nodemaps == null) return;
        this.nodemaps.forEach((x, y) -> {
            try {
                if (finest) logger.finest("Node(" + localNodeName + "->" + x + ") send websocket disconnect event (" + userid + " on " + nodeid + ")");
                y.disconnect(nodeid, userid);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Node(" + localNodeName + "->" + x + ") send websocket disconnect event (" + userid + " on " + nodeid + ")", e);
            }
        });
    }

    public final void onDisconnect(String nodeid, Serializable userid) {
        if (finest) logger.finest("Node (" + localNodeName + ") receive websocket disconnect event (" + userid + " on " + nodeid + ").");
        Set<String> userNodelist = usernodes.get(userid);
        if (userNodelist == null) return;
        userNodelist.remove(nodeid);
        if (userNodelist.isEmpty()) usernodes.remove(userid);
    }

    @RemoteOn
    public int send(String engineid, Serializable groupid, String text) {
        return send(engineid, groupid, text, true);
    }

    public final int onSend(String engineid, Serializable groupid, String text) {
        return onSend(engineid, groupid, text, true);
    }

    @RemoteOn
    public int send(String engineid, Serializable groupid, String text, boolean last) {
        return send0(engineid, groupid, text, last);
    }

    public final int onSend(String engineid, Serializable groupid, String text, boolean last) {
        return onSend0(engineid, groupid, text, last);
    }

    @RemoteOn
    public int send(String engineid, Serializable groupid, byte[] data) {
        return send(engineid, groupid, data, true);
    }

    public final int onSend(String engineid, Serializable groupid, byte[] data) {
        return onSend(engineid, groupid, data, true);
    }

    @RemoteOn
    public int send(String engineid, Serializable groupid, byte[] data, boolean last) {
        return send0(engineid, groupid, data, last);
    }

    public final int onSend(String engineid, Serializable groupid, byte[] data, boolean last) {
        return onSend0(engineid, groupid, data, last);
    }

    private int send0(String engineid, Serializable groupid, Serializable text, boolean last) {
        final Set<String> nodes = usernodes.get(groupid);
        if (nodes == null) return RETCODE_WSOFFLINE; //未登录
        int rs = 0;
        if (nodes.contains(this.localNodeName)) rs = onSend0(engineid, groupid, text, last);
        if (nodemaps == null) return rs;
        this.nodemaps.forEach((x, y) -> {
            if (nodes.contains(x)) {
                int irs = -1;
                try {
                    if (text != null && text.getClass() == byte[].class) {
                        irs = y.send(engineid, groupid, (byte[]) text, last);
                    } else {
                        irs = y.send(engineid, groupid, (String) text, last);
                    }
                    if (finest) logger.finest("Node(" + localNodeName + "->" + x + ") send websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + text + "'} finish and result is " + irs);
                } catch (Exception e) {
                    onDisconnect(x, groupid);
                    logger.log(Level.WARNING, "Node(" + localNodeName + "->" + x + ") send websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + text + "'} failed and result is " + irs, e);
                }
            }
        });
        return rs;
    }

    /**
     * 消息接受者存在WebSocket并发送成功返回true， 否则返回false
     *
     * @param engineid
     * @param groupid  接收方
     * @param text
     * @return
     */
    private int onSend0(String engineid, Serializable groupid, Serializable text, boolean last) {
        WebSocketEngine webSocketEngine = engines.get(engineid);
        if (webSocketEngine == null) {
            if (finest) logger.finest("Node(" + localNodeName + ") receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + text + "'} but result is " + RETCODE_ENGINE_NULL);
            return RETCODE_ENGINE_NULL;
        }
        WebSocketGroup group = webSocketEngine.getWebSocketGroup(groupid);
        if (group == null || group.isEmpty()) {
            if (finest) logger.finest("Node(" + localNodeName + ") receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + text + "'} but result is " + RETCODE_GROUP_EMPTY);
            return RETCODE_GROUP_EMPTY;
        }
        if (finest) logger.finest("Node (" + localNodeName + ") receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + text + "'}.");
        if (text != null && text.getClass() == byte[].class) {
            group.getWebSockets().forEach(x -> x.send((byte[]) text, last));
        } else {
            group.getWebSockets().forEach(x -> x.send(text.toString(), last));
        }
        return 0;
    }

}
