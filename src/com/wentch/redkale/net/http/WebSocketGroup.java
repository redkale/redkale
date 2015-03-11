/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 *
 * @author zhangjx
 */
public final class WebSocketGroup {

    private final long groupid;

    private final List<WebSocket> list = new CopyOnWriteArrayList<>();

    private final Map<String, Object> attributes = new HashMap<>();

    WebSocketGroup(long groupid) {
        this.groupid = groupid;
    }

    public long getGroupid() {
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
        list.add(socket);
    }

    public final boolean isEmpty() {
        return list.isEmpty();
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
}
