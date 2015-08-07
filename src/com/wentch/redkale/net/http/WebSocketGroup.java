/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 *
 * @author zhangjx
 */
public final class WebSocketGroup {

    private final Serializable groupid;

    private WebSocket recentWebSocket;

    private final List<WebSocket> list = new CopyOnWriteArrayList<>();

    private final Map<String, Object> attributes = new HashMap<>();

    WebSocketGroup(Serializable groupid) {
        this.groupid = groupid;
    }

    public Serializable getGroupid() {
        return groupid;
    }

    public Stream<WebSocket> getWebSockets() {
        return list.stream();
    }

    void remove(WebSocket socket) {
        list.remove(socket);
    }

    void add(WebSocket socket) {
        socket.group = this;
        this.recentWebSocket = socket;
        list.add(socket);
    }

    void setRecentWebSocket(WebSocket socket) {
        this.recentWebSocket = socket;
    }

    public final boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * 最近发送消息的WebSocket
     * <p>
     * @return
     */
    public final WebSocket getRecentWebSocket() {
        return recentWebSocket;
    }

    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    public final void removeAttribute(String name) {
        attributes.remove(name);
    }

    public final void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public final void send(boolean recent, Serializable message, boolean last) {
        if (recent) {
            recentWebSocket.send(message, last);
        } else {
            list.forEach(x -> x.send(message, last));
        }
    }

    public final void sendEach(Serializable message, boolean last) {
        list.forEach(x -> x.send(message, last));
    }

    public final void sendRecent(Serializable message, boolean last) {
        recentWebSocket.send(message, last);
    }

    @Override
    public String toString() {
        return "{groupid: " + groupid + ", list.size: " + (list == null ? -1 : list.size()) + "}";
    }
}
