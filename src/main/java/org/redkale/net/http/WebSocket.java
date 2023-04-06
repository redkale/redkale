/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;
import java.util.stream.Stream;
import java.util.zip.*;
import org.redkale.annotation.Comment;
import org.redkale.convert.Convert;
import org.redkale.net.AsyncConnection;
import org.redkale.net.http.WebSocketPacket.FrameType;

/**
 * <blockquote><pre>
 * 一个WebSocket连接对应一个WebSocket实体，即一个WebSocket会绑定一个TCP连接。
 * 协议上符合HTML5规范, 其流程顺序如下:
 *      1.1 onOpen 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断登录态。
 *      1.2 createUserid 若返回null，视为WebSocket的连接不合法，强制关闭WebSocket连接；通常用于判断用户权限是否符合。
 *      1.3 onConnected WebSocket成功连接后在准备接收数据前回调此方法。
 *      1.4 onMessage/onFragment+ WebSocket接收到消息后回调此消息类方法。
 *      1.5 onClose WebSocket被关闭后回调此方法。
 *  普通模式下 以上方法都应该被重载。
 * </pre></blockquote>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <G> Groupid的泛型
 * @param <T> Message的泛型
 */
public abstract class WebSocket<G extends Serializable, T> {

    //--------------------------- CLOSECODE -------------------------------
    @Comment("服务器主动关闭")
    public static final int CLOSECODE_SERVERCLOSE = 3001;

    @Comment("客户端主动关闭")
    public static final int CLOSECODE_CLIENTCLOSE = 3002;

    @Comment("异常关闭")
    public static final int CLOSECODE_WSEXCEPTION = 3003;

    @Comment("异常数据强制关闭")
    public static final int CLOSECODE_ILLPACKET = 3004;

    //---------------------------- RETCODE --------------------------------
    @Comment("消息不合法")
    public static final int RETCODE_SEND_ILLPACKET = 1 << 1; //2

    @Comment("WebSocket已经关闭")
    public static final int RETCODE_WSOCKET_CLOSED = 1 << 2; //4

    @Comment("Socket的buffer不合法")
    public static final int RETCODE_ILLEGALBUFFER = 1 << 3; //8

    @Comment("WebSocket发送消息异常")
    public static final int RETCODE_SENDEXCEPTION = 1 << 4; //16

    @Comment("WebSocketEngine实例不存在")
    public static final int RETCODE_ENGINE_NULL = 1 << 5; //32

    @Comment("WebSocketNode实例不存在")
    public static final int RETCODE_NODESERVICE_NULL = 1 << 6; //64

    @Comment("WebSocket组为空, 表示无WebSocket连接")
    public static final int RETCODE_GROUP_EMPTY = 1 << 7; //128

    @Comment("WebSocket已离线")
    public static final int RETCODE_WSOFFLINE = 1 << 8; //256

    @Comment("WebSocket将延迟发送")
    public static final int RETCODE_DEAYSEND = 1 << 9; //512

    WebSocketEngine _engine; //不可能为空 

    WebSocketReadHandler _readHandler;

    WebSocketWriteHandler _writeHandler;

    InetSocketAddress _sncpAddress; //分布式下不可为空

    AsyncConnection _channel;//不可能为空 

    String _sessionid; //不可能为空 

    G _userid; //不可能为空 

    SocketAddress _remoteAddress;//不可能为空 

    String _remoteAddr;//不可能为空 

    Convert _textConvert; //不可能为空 

    Convert _binaryConvert; //可能为空 

    Convert _sendConvert; //不可能为空 

    java.lang.reflect.Type _messageRestType; //不可能为空

    Deflater deflater; //压缩

    Inflater inflater; //解压

    long createTime = System.currentTimeMillis();

    List<WebSocketPacket> delayPackets;

    private Map<String, Object> attributes = new HashMap<>(); //非线程安全

    private long lastPingTime;

    long lastReadTime;

    long lastSendTime;

    boolean initiateClosed; //收到客户端发送的CLOSE消息

    private final AtomicBoolean closed = new AtomicBoolean();

    protected WebSocket() {
    }

    //----------------------------------------------------------------
    public final CompletableFuture<Integer> sendPing() {
        this.lastPingTime = System.currentTimeMillis();
        //if (_engine.finest) _engine.logger.finest(this + " on "+_engine.getEngineid()+" ping...");
        return sendPacket(WebSocketPacket.DEFAULT_PING_PACKET);
    }

    public final CompletableFuture<Integer> sendPing(byte[] data) {
        if (data == null) {
            return sendPing();
        }
        this.lastPingTime = System.currentTimeMillis();
        return sendPacket(new WebSocketPacket(FrameType.PING, data));
    }

    public final CompletableFuture<Integer> sendPong(byte[] data) {
        return sendPacket(new WebSocketPacket(FrameType.PONG, data));
    }

    public final long getCreateTime() {
        return createTime;
    }

    /**
     * 给自身发送消息, 消息类型是String或byte[]或可JavaBean对象
     *
     * @param message 不可为空, 只能是String或byte[]或可JavaBean对象
     *
     * @return 0表示成功， 非0表示错误码
     */
    public final CompletableFuture<Integer> send(Object message) {
        return send(message, true);
    }

    /**
     * 给自身发送消息, 消息类型是String或byte[]或可JavaBean对象
     *
     * @param message 不可为空, 只能是String或byte[]或可JavaBean对象
     * @param last    是否最后一条
     *
     * @return 0表示成功， 非0表示错误码
     */
    public final CompletableFuture<Integer> send(Object message, boolean last) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> {
                if (json instanceof CharSequence) {
                    return sendPacket(new WebSocketPacket(json.toString(), last));
                } else if (json == null || json instanceof byte[]) {
                    return sendPacket(new WebSocketPacket((byte[]) json, last));
                } else if (message instanceof WebSocketPacket) {
                    return sendPacket((WebSocketPacket) message);
                } else {
                    Convert convert = getSendConvert();
                    return sendPacket(new WebSocketPacket(convert.isBinary() ? FrameType.BINARY : FrameType.TEXT, convert.convertToBytes(json), last));
                }
            });
        }
        Object json = message;
        if (json instanceof CharSequence) {
            return sendPacket(new WebSocketPacket(FrameType.TEXT, json.toString().getBytes(StandardCharsets.UTF_8), last));
        } else if (json == null || json instanceof byte[]) {
            return sendPacket(new WebSocketPacket(FrameType.BINARY, (byte[]) json, last));
        } else if (message instanceof WebSocketPacket) {
            return sendPacket((WebSocketPacket) message);
        } else {
            Convert convert = getSendConvert();
            return sendPacket(new WebSocketPacket(convert.isBinary() ? FrameType.BINARY : FrameType.TEXT, convert.convertToBytes(json), last));
        }
    }

    /**
     * 给自身发送消息, 消息类型是JavaBean对象
     *
     * @param convert Convert
     * @param message 不可为空, 只能是JSON对象
     *
     * @return 0表示成功， 非0表示错误码
     */
    public final CompletableFuture<Integer> send(Convert convert, Object message) {
        return send(convert, message, true);
    }

    /**
     * 给自身发送消息, 消息类型是JavaBean对象
     *
     * @param convert Convert
     * @param message 不可为空, 只能是JavaBean对象
     * @param last    是否最后一条
     *
     * @return 0表示成功， 非0表示错误码
     */
    public final CompletableFuture<Integer> send(Convert convert, Object message, boolean last) {
        final Convert c = convert == null ? getSendConvert() : convert;
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> sendPacket(new WebSocketPacket(c.isBinary() ? FrameType.BINARY : FrameType.TEXT, c.convertToBytes(json), last)));
        }
        return sendPacket(new WebSocketPacket(c.isBinary() ? FrameType.BINARY : FrameType.TEXT, c.convertToBytes(message), last));
    }

    /**
     * 给自身发送消息体, 包含二进制/文本
     *
     * @param packet WebSocketPacket
     *
     * @return 0表示成功， 非0表示错误码
     */
    CompletableFuture<Integer> sendPacket(WebSocketPacket packet) {
        if (this._writeHandler == null) { //if (this._writeIOThread == null) {
            if (delayPackets == null) {
                delayPackets = new ArrayList<>();
            }
            delayPackets.add(packet);
            return CompletableFuture.completedFuture(RETCODE_DEAYSEND);
        }
        CompletableFuture<Integer> rs = this._writeHandler.send(packet); //this._writeIOThread.send(this, packet);
        if (_engine.logger.isLoggable(Level.FINER) && packet != WebSocketPacket.DEFAULT_PING_PACKET) {
            _engine.logger.finer("userid:" + getUserid() + " send websocket message(" + packet + ")" + " on " + this);
        }
        return rs == null ? CompletableFuture.completedFuture(RETCODE_WSOCKET_CLOSED) : rs;
    }

    //----------------------------------------------------------------
    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param message 不可为空
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(Object message, Stream<G> userids) {
        return sendMessage(message, true, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param message 不可为空
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(Object message, G... userids) {
        return sendMessage(message, true, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param convert Convert
     * @param message 不可为空
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, Stream<G> userids) {
        return sendMessage(convert, message, true, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param convert Convert
     * @param message 不可为空
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, G... userids) {
        return sendMessage(convert, message, true, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param message 不可为空
     * @param last    是否最后一条
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(Object message, boolean last, Stream<G> userids) {
        return sendMessage((Convert) null, message, last, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param message 不可为空
     * @param last    是否最后一条
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(Object message, boolean last, G... userids) {
        return sendMessage((Convert) null, message, last, userids);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param convert Convert
     * @param message 不可为空
     * @param last    是否最后一条
     * @param userids Stream
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, boolean last, final Stream<G> userids) {
        Object[] array = userids.toArray();
        Serializable[] ss = new Serializable[array.length];
        for (int i = 0; i < array.length; i++) {
            ss[i] = (Serializable) array[i];
        }
        return sendMessage(convert, message, last, ss);
    }

    /**
     * 给指定userid的WebSocket节点发送 二进制消息/文本消息/JavaBean对象消息
     *
     * @param convert Convert
     * @param message 不可为空
     * @param last    是否最后一条
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendMessage(final Convert convert, Object message, boolean last, Serializable... userids) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(RETCODE_NODESERVICE_NULL);
        }
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> _engine.node.sendMessage(convert, json, last, userids));
        }
        CompletableFuture<Integer> rs = _engine.node.sendMessage(convert, message, last, userids);
        if (_engine.logger.isLoggable(Level.FINER)) {
            _engine.logger.finer("userids:" + Arrays.toString(userids) + " send websocket message(" + message + ")");
        }
        return rs;
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final Object message) {
        return broadcastMessage((Convert) null, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param message 消息内容
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message) {
        return broadcastMessage((WebSocketRange) null, (Convert) null, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param convert Convert
     * @param message 消息内容
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message) {
        return broadcastMessage(convert, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param convert Convert
     * @param message 消息内容
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Convert convert, final Object message) {
        return broadcastMessage((WebSocketRange) null, convert, message, true);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param message 消息内容
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final Object message, final boolean last) {
        return broadcastMessage((Convert) null, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param message 消息内容
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Object message, final boolean last) {
        return broadcastMessage(wsrange, (Convert) null, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param convert Convert
     * @param message 消息内容
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final Convert convert, final Object message, final boolean last) {
        return broadcastMessage((WebSocketRange) null, convert, message, last);
    }

    /**
     * 广播消息， 给所有人发消息
     *
     * @param wsrange 过滤条件
     * @param convert Convert
     * @param message 消息内容
     * @param last    是否最后一条
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastMessage(final WebSocketRange wsrange, final Convert convert, final Object message, final boolean last) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(RETCODE_NODESERVICE_NULL);
        }
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> _engine.node.broadcastMessage(wsrange, convert, json, last));
        }
        CompletableFuture<Integer> rs = _engine.node.broadcastMessage(wsrange, convert, message, last);
        if (_engine.logger.isLoggable(Level.FINER)) {
            _engine.logger.finer("broadcast send websocket message(" + message + ")");
        }
        return rs;
    }

    /**
     * 给指定userid的WebSocket节点发送操作
     *
     * @param action  操作参数
     * @param userids Serializable[]
     *
     * @return 为0表示成功， 其他值表示异常
     */
    public final CompletableFuture<Integer> sendAction(final WebSocketAction action, Serializable... userids) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(RETCODE_NODESERVICE_NULL);
        }
        CompletableFuture<Integer> rs = _engine.node.sendAction(action, userids);
        if (_engine.logger.isLoggable(Level.FINER)) {
            _engine.logger.finer("userids:" + Arrays.toString(userids) + " send websocket action(" + action + ")");
        }
        return rs;
    }

    /**
     * 广播操作， 给所有人发操作指令
     *
     * @param action 操作参数
     *
     * @return 为0表示成功， 其他值表示部分发送异常
     */
    public final CompletableFuture<Integer> broadcastAction(final WebSocketAction action) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(RETCODE_NODESERVICE_NULL);
        }
        CompletableFuture<Integer> rs = _engine.node.broadcastAction(action);
        if (_engine.logger.isLoggable(Level.FINER)) {
            _engine.logger.finer("broadcast send websocket action(" + action + ")");
        }
        return rs;
    }

    /**
     * 获取用户在线的SNCP节点地址列表，不是分布式则返回元素数量为1，且元素值为null的列表<br>
     * InetSocketAddress 为 SNCP节点地址
     *
     * @param userid Serializable
     *
     * @return 地址列表
     */
    public CompletableFuture<Set<WebSocketAddress>> getRpcNodeAddresses(final Serializable userid) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(null);
        }
        return _engine.node.getRpcNodeAddresses(userid);
    }

    /**
     * 获取在线用户的详细连接信息 <br>
     * Map.key 为 SNCP节点地址, 含值为null的key表示没有分布式
     * Map.value 为 用户客户端的IP
     *
     * @param userid Serializable
     *
     * @return 地址集合
     */
    public CompletableFuture<Map<WebSocketAddress, List<String>>> getRpcNodeWebSocketAddresses(final Serializable userid) {
        if (_engine.node == null) {
            return CompletableFuture.completedFuture(null);
        }
        return _engine.node.getRpcNodeWebSocketAddresses(userid);
    }

    /**
     * 更改本WebSocket的userid
     *
     * @param newuserid 新用户ID，不能为null
     *
     * @return CompletableFuture
     */
    public CompletableFuture<Void> changeUserid(final G newuserid) {
        if (newuserid == null) {
            throw new NullPointerException("newuserid is null");
        }
        return _engine.changeLocalUserid(this, newuserid);
    }

    /**
     * 强制关闭用户的所有WebSocket
     *
     * @param userid Serializable
     *
     * @return int
     */
    @Comment("强制关闭用户的所有WebSocket")
    public CompletableFuture<Integer> forceCloseWebSocket(Serializable userid) {
        return _engine.node.forceCloseWebSocket(userid);
    }

    /**
     * 获取WebSocketNode
     *
     *
     * @return WebSocketNode
     */
    @Comment("获取WebSocketNode")
    public final WebSocketNode webSocketNode() {
        return _engine.node;
    }

    /**
     * 获取当前WebSocket下的属性，非线程安全
     *
     * @param <V>  属性值的类型
     * @param name 属性名
     *
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public final <V> V getAttribute(String name) {
        return attributes == null ? null : (V) attributes.get(name);
    }

    /**
     * 移出当前WebSocket下的属性，非线程安全
     *
     * @param <V>  属性值的类型
     * @param name 属性名
     *
     * @return 属性值
     */
    public final <V> V removeAttribute(String name) {
        return attributes == null ? null : (V) attributes.remove(name);
    }

    /**
     * 给当前WebSocket下的增加属性，非线程安全
     *
     * @param name  属性值
     * @param value 属性值
     */
    public final void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, value);
    }

    /**
     * 获取当前WebSocket所属的userid
     *
     * @return userid
     */
    public final G getUserid() {
        return _userid;
    }

    /**
     * 获取当前WebSocket的会话ID， 不会为null
     *
     * @return sessionid
     */
    public final String getSessionid() {
        return _sessionid;
    }

    /**
     * 获取客户端直接地址, 当WebSocket连接是由代理服务器转发的，则该值固定为代理服务器的IP地址
     *
     * @return SocketAddress
     */
    public final SocketAddress getRemoteAddress() {
        return _remoteAddress;
    }

    /**
     * 获取客户端真实地址 同 HttpRequest.getRemoteAddr()
     *
     * @return String
     */
    public final String getRemoteAddr() {
        return _remoteAddr;
    }

    protected Convert getTextConvert() {
        return _textConvert;
    }

    protected Convert getBinaryConvert() {
        return _binaryConvert;
    }

    protected Convert getSendConvert() {
        return _sendConvert;
    }

    protected void setTextConvert(Convert convert) {
        this._textConvert = convert;
    }

    protected void setBinaryConvert(Convert convert) {
        this._binaryConvert = convert;
    }

    protected void setSendConvert(Convert convert) {
        this._sendConvert = convert;
    }

    //-------------------------------------------------------------------
    /**
     * 获取指定userid的WebSocket数组, 没有返回null <br>
     * 此方法用于单用户多连接模式
     *
     * @param userid Serializable
     *
     * @return WebSocket集合
     */
    protected final Stream<WebSocket> getLocalWebSockets(G userid) {
        return _engine.getLocalWebSockets(userid);
    }

    /**
     * 获取指定userid的WebSocket数组, 没有返回null<br>
     * 此方法用于单用户单连接模式
     *
     * @param userid Serializable
     *
     * @return WebSocket
     */
    protected final WebSocket findLocalWebSocket(G userid) {
        return _engine.findLocalWebSocket(userid);
    }

    /**
     * 获取当前进程节点所有在线的WebSocket
     *
     * @return WebSocketGroup列表
     */
    protected final Collection<WebSocket> getLocalWebSockets() {
        return _engine.getLocalWebSockets();
    }

    //-------------------------------------------------------------------
    /**
     * 返回sessionid, null表示连接不合法或异常,默认实现是request.sessionid(true)，通常需要重写该方法
     *
     * @param request HttpRequest
     *
     * @return sessionid
     */
    protected CompletableFuture<String> onOpen(final HttpRequest request) {
        return CompletableFuture.completedFuture(request.getSessionid(true));
    }

    /**
     * 创建userid， null表示异常， 必须实现该方法
     *
     * @return userid
     */
    protected abstract CompletableFuture<G> createUserid();

    /**
     * WebSocket.broadcastMessage时的过滤条件
     *
     * @param wsrange 过滤条件
     *
     * @return boolean
     */
    protected boolean predicate(WebSocketRange wsrange) {
        return true;
    }

    /**
     * WebSocket.broadcastAction时的操作
     *
     * @param action 操作参数
     *
     * @return CompletableFuture
     *
     */
    protected CompletableFuture<Integer> action(WebSocketAction action) {
        return CompletableFuture.completedFuture(0);
    }

    /**
     * WebSokcet连接成功后的回调方法
     *
     * @return Future 可以为null
     */
    public CompletableFuture onConnected() {
        return null;
    }

    /**
     * ping后的回调方法
     *
     * @param bytes 数据
     */
    public void onPing(byte[] bytes) {
    }

    /**
     * pong后的回调方法
     *
     * @param bytes 数据
     */
    public void onPong(byte[] bytes) {
    }

    /**
     *
     * 接收到消息前的拦截方法， ping/pong不在其内 <br>
     * 注意：处理完后需要调用 messageEvent.run() 才能响应onMessage
     *
     * @param restMapping  Rest的方法名，没有则为空字符串
     * @param param        onMessage方法的参数
     * @param messageEvent onMessage事件
     */
    public void preOnMessage(String restMapping, WebSocketParam param, Runnable messageEvent) {
        messageEvent.run();
    }

    /**
     * 接收到消息的回调方法
     *
     * @param message 消息
     * @param last    是否最后一条
     */
    public void onMessage(T message, boolean last) {
    }

    /**
     * 接收到文本消息的回调方法
     *
     * @param text 消息
     * @param last 是否最后一条
     */
    public void onMessage(String text, boolean last) {
    }

    /**
     * 接收到二进制消息的回调方法
     *
     * @param bytes 消息
     * @param last  是否最后一条
     */
    public void onMessage(byte[] bytes, boolean last) {
    }

    /**
     * 关闭的回调方法，调用此方法时WebSocket已经被关闭
     *
     * @param code   结果码，非0表示非正常关闭
     * @param reason 关闭原因
     *
     * @return Future 可以为null
     */
    public CompletableFuture onClose(int code, String reason) {
        return null;
    }

    /**
     * 发生异常时调用
     *
     * @param t       异常
     * @param buffers ByteBuffer[]
     */
    public void onOccurException(Throwable t, ByteBuffer[] buffers) {
        this.getLogger().log(Level.SEVERE, "WebSocket receive or send Message error", t);
    }

    /**
     * 当Single模式下用户重复登录时回调函数，默认处理方式: 关闭旧连接
     *
     * @return Future 可以为null, 为null或者Future值为false表示关闭新连接， Future值为true表示关闭旧连接
     */
    public CompletableFuture<Boolean> onSingleRepeatConnect() {
        return forceCloseWebSocket(getUserid()).thenApply((r) -> true);
    }

    /**
     * 获取分布式情况下的SNCP地址, 非分布式下为null
     *
     * @return InetSocketAddress sncpAddress
     */
    public InetSocketAddress getSncpAddress() {
        return _sncpAddress;
    }

    /**
     * 获取Logger
     *
     * @return Logger Logger
     */
    public Logger getLogger() {
        return this._engine.logger;
    }

    /**
     * 获取最后一次发送消息的时间
     *
     * @return long
     */
    public long getLastSendTime() {
        return this.lastSendTime;
    }

    /**
     * 获取最后一次读取消息的时间
     *
     * @return long
     */
    public long getLastReadTime() {
        return this.lastReadTime;
    }

    /**
     * 获取最后一次发送PING消息的时间
     *
     * @return long
     */
    public long getLastPingTime() {
        return this.lastPingTime;
    }

    /**
     * 显式地关闭WebSocket
     */
    public final void close() {
        if (this.deflater != null) {
            this.deflater.end();
        }
        if (this.inflater != null) {
            this.inflater.end();
        }
        CompletableFuture<Void> future = kill(CLOSECODE_SERVERCLOSE, "user close");
        if (future != null) {
            future.join();
        }
    }

    //closeRunner
    CompletableFuture<Void> kill(int code, String reason) {
        if (closed.compareAndSet(false, true)) {
            if (_channel == null) {
                return null;
            }
            CompletableFuture<Void> future = _engine.removeLocalThenDisconnect(this);
            _channel.dispose();
            if (_readHandler != null) {
                _readHandler.byteArrayPool.accept(_readHandler.halfFrameBytes);
            }
            if (_writeHandler != null) {
                _writeHandler.byteArrayPool.accept(_writeHandler.writeArray);
            }
            CompletableFuture closeFuture = onClose(code, reason);
            if (closeFuture == null) {
                return future;
            }
            return CompletableFuture.allOf(future, closeFuture);
        } else {
            return null;
        }
    }

    /**
     * 是否关闭
     *
     * @return boolean
     */
    public final boolean isClosed() {
        return this.closed.get();
    }

    @Override
    public String toString() {
        return this.getUserid() + "@" + _remoteAddr + "@" + Objects.hashCode(this);
    }
}
