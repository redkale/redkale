/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.net.http.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class WebSocketNodeService extends WebSocketNode implements Service {

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
    }

    @Override
    public void destroy(AnyValue conf) {
        super.destroy(conf);
    }

    @Override
    public int sendMessage(Serializable groupid, boolean recent, Serializable message, boolean last) {
        final Set<String> engineids = localNodes.get(groupid);
        if (engineids == null || engineids.isEmpty()) return RETCODE_GROUP_EMPTY;
        for (String engineid : engineids) {
            final WebSocketEngine engine = engines.get(engineid);
            if (engine != null) { //在本地
                final WebSocketGroup group = engine.getWebSocketGroup(groupid);
                if (group == null || group.isEmpty()) {
                    if (finest) logger.finest("receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + message + "'} but result is " + RETCODE_GROUP_EMPTY);
                    return RETCODE_GROUP_EMPTY;
                }
                group.send(recent, message, last);
            } else { //对方连接在远程节点
                return RETCODE_WSOFFLINE;
            }
        }
        return 0;
    }

    @Override
    @MultiRun
    public void connect(Serializable groupid, InetSocketAddress addr) {
        Set<InetSocketAddress> addrs = dataNodes.get(groupid);
        if (addrs == null) {
            addrs = new CopyOnWriteArraySet<>();
            dataNodes.put(groupid, addrs);
        }
        addrs.add(addr);
        if(finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + groupid +" connect from " + addr); 
    }

    @Override
    @MultiRun
    public void disconnect(Serializable groupid, InetSocketAddress addr) {
        Set<InetSocketAddress> addrs = dataNodes.get(groupid);
        if (addrs == null) return;
        addrs.remove(addr);
        if (addrs.isEmpty()) dataNodes.remove(groupid);
        if(finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + groupid +" disconnect from " + addr); 
    }
}
