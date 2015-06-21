/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author zhangjx
 */
public abstract class WebSocket {

    WebSocketRunner runner;

    WebSocketEngine engine;

    WebSocketGroup group;

    String sessionid;

    Serializable groupid;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    protected WebSocket() {
    }

    //----------------------------------------------------------------
    public final void send(WebSocketPacket packet) {
        if (this.runner != null) this.runner.sendMessage(packet);
    }

    public final void close() {
        if (this.runner != null) this.runner.closeRunner();
    }

    public final void send(String text) {
        send(text, true);
    }

    public final void send(String text, boolean last) {
        send(new WebSocketPacket(text, last));
    }

    public final void send(byte[] data) {
        send(data, true);
    }

    public final void send(byte[] data, boolean last) {
        send(new WebSocketPacket(data, last));
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

    public final Serializable getGroupid() {
        return groupid;
    }

    public final String getSessionid() {
        return sessionid;
    }

    //-------------------------------------------------------------------
    protected final WebSocketGroup getWebSocketGroup() {
        return group;
    }

    protected final WebSocketGroup getWebSocketGroup(long groupid) {
        return engine.getWebSocketGroup(groupid);
    }

    //-------------------------------------------------------------------
    /**
     * 返回sessionid, null表示连接不合法或异常
     *
     * @param request
     * @return
     */
    public String onOpen(final HttpRequest request) {
        return request.getSessionid(false);
    }

    /**
     * 返回GroupID， null表示异常
     *
     * @return
     */
    public Serializable createGroupid() {
        return null;
    }

    /**
     * WebSocket流程顺序: onOpen、createGroupid、onConnected、onMessage/onFragment+、onClose
     */
    public void onConnected() {
    }

    public void onMessage(String text) {
    }

    public void onMessage(byte[] bytes) {
    }

    public void onFragment(String text, boolean last) {
    }

    public void onFragment(byte[] bytes, boolean last) {
    }

    public void onClose(int code, String reason) {
    }
}
