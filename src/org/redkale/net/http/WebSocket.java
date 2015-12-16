/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.http.WebSocketPacket.FrameType;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.net.*;

/**
 * 一个WebSocket连接对应一个WebSocket实体，即一个WebSocket会绑定一个TCP连接。
 * WebSocket 有两种模式: 
 *  1) 普通模式: 协议上符合HTML5规范, 其流程顺序如下:
 *         1.1 onOpen                 如果方法返回null，则视为WebSocket的连接不合法，框架会强制关闭WebSocket连接；通常用于判断登录态。
 *         1.2 createGroupid          如果方法返回null，则视为WebSocket的连接不合法，框架会强制关闭WebSocket连接；通常用于判断用户权限是否符合。
 *         1.3 onConnected            WebSocket成功连接后在准备接收数据前回调此方法。
 *         1.4 onMessage/onFragment+  WebSocket接收到消息后回调此消息类方法。
 *         1.5 onClose                WebSocket被关闭后回调此方法。
 * 
 * 此模式下 以上方法都应该被重载。
 * 
 *  2) 原始二进制模式: 此模式有别于HTML5规范，可以视为原始的TCP连接。通常用于音频视频通讯场景。期流程顺序如下:
 *         2.1 onOpen                 如果方法返回null，则视为WebSocket的连接不合法，框架会强制关闭WebSocket连接；通常用于判断登录态。
 *         2.2 createGroupid          如果方法返回null，则视为WebSocket的连接不合法，框架会强制关闭WebSocket连接；通常用于判断用户权限是否符合。
 *         2.3 onRead                 WebSocket成功连接后回调此方法， 由此方法处理原始的TCP连接， 同时业务代码去控制WebSocket的关闭。
 * 
 * 此模式下 以上方法都应该被重载。
 * <p>
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public abstract class WebSocket {

    //消息不合法
    public static final int RETCODE_SEND_ILLPACKET = 1 << 1; //2

    //ws已经关闭
    public static final int RETCODE_WSOCKET_CLOSED = 1 << 2; //4

    //socket的buffer不合法
    public static final int RETCODE_ILLEGALBUFFER = 1 << 3; //8

    //ws发送消息异常
    public static final int RETCODE_SENDEXCEPTION = 1 << 4; //16

    public static final int RETCODE_ENGINE_NULL = 1 << 5; //32

    public static final int RETCODE_NODESERVICE_NULL = 1 << 6; //64

    public static final int RETCODE_GROUP_EMPTY = 1 << 7; //128

    public static final int RETCODE_WSOFFLINE = 1 << 8; //256

    WebSocketRunner runner;

    WebSocketEngine engine;

    WebSocketGroup group;

    WebSocketNode node;

    Serializable sessionid;

    Serializable groupid;

    private final long createtime = System.currentTimeMillis();

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    protected WebSocket() {
    }

    //----------------------------------------------------------------
    /**
     * 发送消息体, 包含二进制/文本
     * <p>
     * @param packet
     * @return 
     */
    public final int send(WebSocketPacket packet) {
        if (this.runner != null) return this.runner.sendMessage(packet);
        return RETCODE_WSOCKET_CLOSED;
    }

    /**
     * 显式地关闭WebSocket
     */
    public final void close() {
        if (this.runner != null) this.runner.closeRunner();
    }

    /**
     * 发送单一的文本消息
     * <p>
     * @param text 不可为空
     * @return 
     */
    public final int send(String text) {
        return send(text, true);
    }

    /**
     * 发送文本消息
     * <p>
     * @param text 不可为空
     * @param last 是否最后一条
     * @return 
     */
    public final int send(String text, boolean last) {
        return send(new WebSocketPacket(text, last));
    }

    /**
     * 发送单一的二进制消息
     * <p>
     * @param data
     * @return 
     */
    public final int send(byte[] data) {
        return send(data, true);
    }

    public final int sendPing(byte[] data) {
        return send(new WebSocketPacket(FrameType.PING, data));
    }

    public final int sendPong(byte[] data) {
        return send(new WebSocketPacket(FrameType.PONG, data));
    }

    public final long getCreatetime() {
        return createtime;
    }

    /**
     * 发送二进制消息
     * <p>
     * @param data 不可为空
     * @param last 是否最后一条
     * @return 
     */
    public final int send(byte[] data, boolean last) {
        return send(new WebSocketPacket(data, last));
    }

    /**
     * 发送消息, 消息类型是String或byte[]
     * <p>
     * @param message 不可为空, 只能是String或者byte[]
     * @param last 是否最后一条
     * @return 
     */
    public final int send(Serializable message, boolean last) {
        return send(new WebSocketPacket(message, last));
    }

    //----------------------------------------------------------------
    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
     * <p>
     * @param groupid
     * @param text    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, String text) {
        return sendEachMessage(groupid, text, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
     * <p>
     * @param groupid
     * @param data    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, byte[] data) {
        return WebSocket.this.sendEachMessage(groupid, data, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
     * <p>
     * @param groupid
     * @param text    不可为空
     * @param last
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, false, text, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
     * <p>
     * @param groupid
     * @param data    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendEachMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, false, data, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近活跃的WebSocket节点发送文本消息
     * <p>
     * @param groupid
     * @param text    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, String text) {
        return sendRecentMessage(groupid, text, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近活跃的WebSocket节点发送二进制消息
     * <p>
     * @param groupid
     * @param data    不可为空
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, byte[] data) {
        return sendRecentMessage(groupid, data, true);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近活跃的WebSocket节点发送文本消息
     * <p>
     * @param groupid
     * @param text    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, String text, boolean last) {
        return sendMessage(groupid, true, text, last);
    }

    /**
     * 给指定groupid的WebSocketGroup下最近活跃的WebSocket节点发送二进制消息
     * <p>
     * @param groupid
     * @param data    不可为空
     * @param last    是否最后一条
     * @return 为0表示成功， 其他值表示异常
     */
    public final int sendRecentMessage(Serializable groupid, byte[] data, boolean last) {
        return sendMessage(groupid, true, data, last);
    }

    private int sendMessage(Serializable groupid, boolean recent, String text, boolean last) {
        if (node == null) return RETCODE_NODESERVICE_NULL;
        return node.sendMessage(groupid, recent, text, last);
    }

    private int sendMessage(Serializable groupid, boolean recent, byte[] data, boolean last) {
        if (node == null) return RETCODE_NODESERVICE_NULL;
        return node.sendMessage(groupid, recent, data, last);
    }

    /**
     * 获取当前WebSocket下的属性
     * <p>
     * @param <T>
     * @param name
     * @return
     */
    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    /**
     * 移出当前WebSocket下的属性
     * <p>
     * @param <T>
     * @param name
     * @return
     */
    public final <T> T removeAttribute(String name) {
        return (T) attributes.remove(name);
    }

    /**
     * 给当前WebSocket下的增加属性
     * <p>
     * @param name
     * @param value
     */
    public final void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * 获取当前WebSocket所属的groupid
     * <p>
     * @return
     */
    public final Serializable getGroupid() {
        return groupid;
    }

    /**
     * 获取当前WebSocket的会话ID， 不会为null
     * <p>
     * @return
     */
    public final Serializable getSessionid() {
        return sessionid;
    }

    //-------------------------------------------------------------------
    /**
     * 获取当前WebSocket所属的WebSocketGroup， 不会为null
     * <p>
     * @return
     */
    protected final WebSocketGroup getWebSocketGroup() {
        return group;
    }

    /**
     * 获取指定groupid的WebSocketGroup, 没有返回null
     * <p>
     * @param groupid
     * @return
     */
    protected final WebSocketGroup getWebSocketGroup(Serializable groupid) {
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
    public Serializable onOpen(final HttpRequest request) {
        return request.getSessionid(false);
    }

    /**
     * 创建groupid， null表示异常
     *
     * @return
     */
    protected abstract Serializable createGroupid();

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

    public void onPing(byte[] bytes) {
    }

    public void onPong(byte[] bytes) {
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
