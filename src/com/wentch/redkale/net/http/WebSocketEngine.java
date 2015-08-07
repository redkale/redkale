/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 */
public final class WebSocketEngine {

    private final String engineid;

    private final Map<Serializable, WebSocketGroup> containers = new ConcurrentHashMap<>();

    protected WebSocketEngine(String engineid) {
        this.engineid = engineid;
    }

    void add(WebSocket socket) {
        WebSocketGroup group = containers.get(socket.groupid);
        if (group == null) {
            group = new WebSocketGroup(socket.groupid);
            containers.put(socket.groupid, group);
        }
        group.add(socket);
    }

    void remove(WebSocket socket) {
        WebSocketGroup group = containers.get(socket.groupid);
        if (group == null) return;
        group.remove(socket);
        if (group.isEmpty()) containers.remove(socket.groupid);
    }

    Collection<WebSocketGroup> getWebSocketGroups() {
        return containers.values();
    }

    public WebSocketGroup getWebSocketGroup(Serializable groupid) {
        return containers.get(groupid);
    }

    void close() {
    }

    public String getEngineid() {
        return engineid;
    }
}
