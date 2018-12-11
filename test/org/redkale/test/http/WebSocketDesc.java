/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.redkale.convert.Convert;
import org.redkale.net.http.*;

/**
 *
 * @author zhangjx
 */
public interface WebSocketDesc<G, T> {

    //给自身发送消息, 消息类型是String或byte[]或可JavaBean对象  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> send(Object message);

    //给自身发送消息, 消息类型是key-value键值对  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMap(Object... messages);

    //给自身发送消息, 消息类型是String或byte[]或可JavaBean对象  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> send(Object message, boolean last);

    //给自身发送消息, 消息类型是key-value键值对  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMap(boolean last, Object... messages);

    //给自身发送消息, 消息类型是JavaBean对象  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> send(Convert convert, Object message);

    //给自身发送消息, 消息类型是JavaBean对象  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> send(Convert convert, Object message, boolean last);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Object message, G... userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Object message, Stream<G> userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Convert convert, Object message, G... userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Convert convert, Object message, Stream<G> userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Object message, boolean last, G... userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Object message, boolean last, Stream<G> userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Convert convert, Object message, boolean last, G... userids);

    //给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendMessage(Convert convert, Object message, boolean last, Stream<G> userids);

    //给所有人广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final Object message);

    //给所有人广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message);

    //给符合条件的人群广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message);

    //给符合条件的人群广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Convert convert, final Object message);

    //给所有人广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final Object message, boolean last);

    //给所有人广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message, boolean last);

    //给符合条件的人群广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message, boolean last);

    //给符合条件的人群广播消息, 返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> broadcastMessage(WebSocketRange wsrange, Convert convert, final Object message, boolean last);

    //给指定userid的WebSocket节点发送操作
    public CompletableFuture<Integer> sendAction(final WebSocketAction action, Serializable... userids);

    //广播操作， 给所有人发操作指令
    public CompletableFuture<Integer> broadcastAction(final WebSocketAction action);

    //获取用户在线的SNCP节点地址列表，不是分布式则返回元素数量为1，且元素值为null的列表
    public CompletableFuture<Collection<InetSocketAddress>> getRpcNodeAddresses(final Serializable userid);

    //获取在线用户的详细连接信息
    public CompletableFuture<Map<InetSocketAddress, List<String>>> getRpcNodeWebSocketAddresses(final Serializable userid);

    //发送PING消息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendPing();

    //发送PING消息，附带其他信息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendPing(byte[] data);

    //发送PONG消息，附带其他信息  返回结果0表示成功，非0表示错误码
    public CompletableFuture<Integer> sendPong(byte[] data);

    //强制关闭用户的所有WebSocket
    public CompletableFuture<Integer> forceCloseWebSocket(Serializable userid);

    //更改本WebSocket的userid
    public CompletableFuture<Void> changeUserid(final G newuserid);


    //获取指定userid的WebSocket数组, 没有返回null  此方法用于单用户多连接模式
    /* protected */    Stream<WebSocket> getLocalWebSockets(G userid);


    //获取指定userid的WebSocket数组, 没有返回null 此方法用于单用户单连接模式
    /* protected */ WebSocket findLocalWebSocket(G userid);


    //获取当前进程节点所有在线的WebSocket
    /* protected */ Collection<WebSocket> getLocalWebSockets();


    //获取ByteBuffer资源池
    /* protected */ Supplier<ByteBuffer> getByteBufferSupplier();


    //返回sessionid, null表示连接不合法或异常,默认实现是request.sessionid(true)，通常需要重写该方法
    /* protected */ CompletableFuture<String> onOpen(final HttpRequest request);


    //创建userid， null表示异常， 必须实现该方法
    /* protected abstract */    CompletableFuture<G> createUserid();


    //WebSocket.broadcastMessage时的过滤条件
    /* protected */ boolean predicate(WebSocketRange wsrange);

    //WebSokcet连接成功后的回调方法
    public void onConnected();

    //ping后的回调方法
    public void onPing(byte[] bytes);

    //pong后的回调方法
    public void onPong(byte[] bytes);

    //接收到消息的回调方法
    public void onMessage(T message, boolean last);

    //接收到文本消息的回调方法
    public void onMessage(String message, boolean last);

    //接收到二进制消息的回调方法
    public void onMessage(byte[] bytes, boolean last);

    //关闭的回调方法，调用此方法时WebSocket已经被关闭
    public void onClose(int code, String reason);

    //获取当前WebSocket下的属性
    public T getAttribute(String name);

    //移出当前WebSocket下的属性
    public T removeAttribute(String name);

    //给当前WebSocket下的增加属性
    public void setAttribute(String name, Object value);

    //获取当前WebSocket所属的userid
    public G getUserid();

    //获取当前WebSocket的会话ID， 不会为null
    public Serializable getSessionid();

    //获取客户端直接地址, 当WebSocket连接是由代理服务器转发的，则该值固定为代理服务器的IP地址
    public SocketAddress getRemoteAddress();

    //获取客户端真实地址 同 HttpRequest.getRemoteAddr()
    public String getRemoteAddr();

    //获取WebSocket创建时间
    public long getCreatetime();

    //获取最后一次发送消息的时间
    public long getLastSendTime();

    //获取最后一次读取消息的时间
    public long getLastReadTime();

    //获取最后一次ping的时间
    public long getLastPingTime();

    //显式地关闭WebSocket
    public void close();

    //WebSocket是否已关闭
    public boolean isClosed();
}
