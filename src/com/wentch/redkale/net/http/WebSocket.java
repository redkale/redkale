/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.service.*;
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

    WebSocketNodeService nodeService;

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

    //----------------------------------------------------------------
    public final int sendMessage(Serializable groupid, String text) {
        return sendMessage(groupid, text, true);
    }

    public final int sendMessage(Serializable groupid, byte[] data) {
        return sendMessage(groupid, data, true);
    }

    public final int sendMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, text, last);
    }

    public final int sendMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, data, last);
    }

    public final int sendRecentMessage(Serializable groupid, String text) {
        return sendRecentMessage(groupid, text, true);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data) {
        return sendRecentMessage(groupid, data, true);
    }

    public final int sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, text, last);
    }

    public final int sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, data, last);
    }

    private int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        if (nodeService == null) return WebSocketNodeService.RETCODE_NODESERVICE_NULL;
        if (groupid == this.groupid) {
            return nodeService.onSend(this.engine.getEngineid(), groupid, recent, text, last);
        } else {
            return nodeService.send(this.engine.getEngineid(), groupid, recent, text, last);
        }
    }

    private int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        if (nodeService == null) return WebSocketNodeService.RETCODE_NODESERVICE_NULL;
        if (groupid == this.groupid) {
            return nodeService.onSend(this.engine.getEngineid(), groupid, recent, data, last);
        } else {
            return nodeService.send(this.engine.getEngineid(), groupid, recent, data, last);
        }
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

    protected final Collection<WebSocketGroup> getWebSocketGroups() {
        return engine.getWebSocketGroups();
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
     * WebSocketBinary模式流程顺序: onOpen、createGroupid、onRead
     * WebSocket流程顺序: onOpen、createGroupid、onConnected、onMessage/onFragment+、onClose
     */
    
    /**
     * 
     * @param channel 
     */
    public void onRead(AsyncConnection channel) {
    }

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
