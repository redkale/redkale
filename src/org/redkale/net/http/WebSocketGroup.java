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

    private final HttpContext context;

    private WebSocket recentWebSocket;

    private final List<WebSocket> list = new CopyOnWriteArrayList<>();

    private final Map<String, Object> attributes = new HashMap<>();

    WebSocketGroup(HttpContext context, Serializable groupid) {
        this.context = context;
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

    final CompletableFuture<Integer> send(boolean recent, final WebSocketPacket packet) {
        if (recent) {
            return recentWebSocket.send(packet);
        } else {
            return sendEach(packet);
        }
    }

    public final CompletableFuture<Integer> sendEach(Object message) {
        return sendEach(message, true);
    }

    public final CompletableFuture<Integer> sendEach(final WebSocketPacket packet) {
        CompletableFuture<Integer> future = null;
        final boolean more = packet.sendBuffers == null && list.size() > 1;
        if (more) packet.setSendBuffers(packet.encode(context.getBufferSupplier()));
        for (WebSocket s : list) {
            if (future == null) {
                future = s.sendPacket(packet);
            } else {
                future = future.thenCombine(s.sendPacket(packet), (a, b) -> a | (Integer) b);
            }
        }
        if (more && future != null) future.whenComplete((rs, ex) -> context.offerBuffer(packet.sendBuffers));
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
        if (message instanceof WebSocketPacket) {
            return sendEach((WebSocketPacket) message);
        } else if (message != null && !(message instanceof byte[]) && !(message instanceof CharSequence)) {
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
