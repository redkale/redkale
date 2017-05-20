/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 *
 * <p>
 * 详情见: https://redkale.org
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
        socket._group = this;
        this.recentWebSocket = socket;
        list.add(socket);
    }

    void setRecentWebSocket(WebSocket socket) {
        this.recentWebSocket = socket;
    }

    public final boolean isEmpty() {
        return list.isEmpty();
    }

    public final int size() {
        return list.size();
    }

    /**
     * 最近发送消息的WebSocket
     *
     * @return WebSocket
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

    public final CompletableFuture<Integer> send(boolean recent, Object message, boolean last) {
        if (recent) {
            return recentWebSocket.send(message, last);
        } else {
            return sendEach(message, last);
        }
    }

    public final CompletableFuture<Integer> sendEach(Object message) {
        return sendEach(message, true);
    }

    public final CompletableFuture<Integer> sendEach(WebSocketPacket packet) {
        CompletableFuture<Integer> future = null;
        for (WebSocket s : list) {
            if (future == null) {
                future = s.send(packet);
            } else {
                future.thenCombine(s.send(packet), (a, b) -> a | b);
            }
        }
        return future == null ? CompletableFuture.completedFuture(0) : future;
    }

    public final CompletableFuture<Integer> sendEachPing() {
        return sendEach(WebSocketPacket.DEFAULT_PING_PACKET);
    }

    public final CompletableFuture<Integer> sendRecent(Object message) {
        return sendRecent(message, true);
    }

    public final CompletableFuture<Integer> sendRecent(WebSocketPacket packet) {
        return recentWebSocket.send(packet);
    }

    public final CompletableFuture<Integer> sendEach(Object message, boolean last) {
        if (message != null && !(message instanceof byte[]) && !(message instanceof CharSequence)) {
            message = recentWebSocket._jsonConvert.convertTo(message);
        }
        return sendEach(new WebSocketPacket((Serializable) message, last));
    }

    public final CompletableFuture<Integer> sendRecent(Object message, boolean last) {
        return recentWebSocket.send(message, last);
    }

    @Override
    public String toString() {
        return "{groupid: " + groupid + ", list.size: " + (list == null ? -1 : list.size()) + "}";
    }

}
