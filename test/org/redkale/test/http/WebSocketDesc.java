/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.io.*;
import java.net.*;
import java.util.*;
import org.redkale.net.*;
import org.redkale.net.http.*;

/**
 *
 * @author zhangjx
 */
public interface WebSocketDesc {

    //发送消息体, 包含二进制/文本  返回结果0表示成功，非0表示错误码
    public int send(WebSocketPacket packet);

    //发送单一的文本消息  返回结果0表示成功，非0表示错误码
    public int send(String text);

    //发送文本消息  返回结果0表示成功，非0表示错误码
    public int send(String text, boolean last);

    //发送单一的二进制消息  返回结果0表示成功，非0表示错误码
    public int send(byte[] data);

    //发送单一的二进制消息  返回结果0表示成功，非0表示错误码
    public int send(byte[] data, boolean last);

    //发送消息, 消息类型是String或byte[]  返回结果0表示成功，非0表示错误码
    public int send(Serializable message, boolean last);

    //给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
    public int sendEachMessage(Serializable groupid, String text);

    //给指定groupid的WebSocketGroup下所有WebSocket节点发送文本消息
    public int sendEachMessage(Serializable groupid, String text, boolean last);

    //给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
    public int sendEachMessage(Serializable groupid, byte[] data);

    //给指定groupid的WebSocketGroup下所有WebSocket节点发送二进制消息
    public int sendEachMessage(Serializable groupid, byte[] data, boolean last);

    //给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送文本消息
    public int sendRecentMessage(Serializable groupid, String text);

    //给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送文本消息
    public int sendRecentMessage(Serializable groupid, String text, boolean last);

    //给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送二进制消息
    public int sendRecentMessage(Serializable groupid, byte[] data);

    //给指定groupid的WebSocketGroup下最近接入的WebSocket节点发送二进制消息
    public int sendRecentMessage(Serializable groupid, byte[] data, boolean last);

    //发送PING消息  返回结果0表示成功，非0表示错误码
    public int sendPing();

    //发送PING消息，附带其他信息  返回结果0表示成功，非0表示错误码
    public int sendPing(byte[] data);

    //发送PONG消息，附带其他信息  返回结果0表示成功，非0表示错误码
    public int sendPong(byte[] data);

    //获取当前WebSocket下的属性
    public <T> T getAttribute(String name);

    //移出当前WebSocket下的属性
    public <T> T removeAttribute(String name);

    //给当前WebSocket下的增加属性
    public void setAttribute(String name, Object value);

    //获取当前WebSocket所属的groupid
    public Serializable getGroupid();

    //获取当前WebSocket的会话ID， 不会为null
    public Serializable getSessionid();

    //获取客户端直接地址, 当WebSocket连接是由代理服务器转发的，则该值固定为代理服务器的IP地址
    public SocketAddress getRemoteAddress();

    //获取客户端真实地址 同 HttpRequest.getRemoteAddr()
    public String getRemoteAddr();

    //获取WebSocket创建时间
    public long getCreatetime();

    //显式地关闭WebSocket
    public void close();


    //获取当前WebSocket所属的WebSocketGroup， 不会为null
    /* protected */ WebSocketGroup getWebSocketGroup();


    //获取指定groupid的WebSocketGroup, 没有返回null
    /* protected */ WebSocketGroup getWebSocketGroup(Serializable groupid);


    //获取当前进程节点所有在线的WebSocketGroup
    /* protected */ Collection<WebSocketGroup> getWebSocketGroups();


    //获取在线用户的节点地址列表
    /* protected */ Collection<InetSocketAddress> getOnlineNodes(Serializable groupid);


    //获取在线用户的详细连接信息
    /* protected */ Map<InetSocketAddress, List<String>> getOnlineRemoteAddress(Serializable groupid);

    //返回sessionid, null表示连接不合法或异常,默认实现是request.getSessionid(false)，通常需要重写该方法
    public Serializable onOpen(final HttpRequest request);


    //创建groupid， null表示异常， 必须实现该方法， 通常为用户ID为groupid
    /* protected abstract */ Serializable createGroupid();

    //标记为@WebSocketBinary才需要重写此方法
    default void onRead(AsyncConnection channel) {
    }

    default void onConnected() {
    }

    default void onMessage(String text) {
    }

    default void onPing(byte[] bytes) {
    }

    default void onPong(byte[] bytes) {
    }

    default void onMessage(byte[] bytes) {
    }

    default void onFragment(String text, boolean last) {
    }

    default void onFragment(byte[] bytes, boolean last) {
    }

    default void onClose(int code, String reason) {
    }
}
