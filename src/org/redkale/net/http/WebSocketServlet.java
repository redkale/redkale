/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import javax.annotation.*;
import org.redkale.service.WebSocketNodeService;
import org.redkale.util.*;

/**
 * <blockquote><pre>
 * 当WebSocketServlet接收一个TCP连接后，进行协议判断，如果成功就会创建一个WebSocket。
 *
 *                                    WebSocketServlet
 *                                            |
 *                                            |
 *                                   WebSocketEngine
 *                                    WebSocketNode
 *                                   /             \
 *                                 /                \
 *                               /                   \
 *                     WebSocketGroup1          WebSocketGroup2
 *                        /        \                /        \
 *                      /           \             /           \
 *               WebSocket1     WebSocket2   WebSocket3    WebSocket4
 *
 * </pre></blockquote>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class WebSocketServlet extends HttpServlet {

    @Comment("WebScoket服务器给客户端进行ping操作的间隔时间, 单位: 秒")
    public static final String WEBPARAM__LIVEINTERVAL = "liveinterval";

    @Comment("WebScoket服务器给客户端进行ping操作的默认间隔时间, 单位: 秒")
    public static final int DEFAILT_LIVEINTERVAL = 60;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final MessageDigest digest = getMessageDigest();

    private static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Comment("是否用于二进制流传输")
    protected final boolean wsbinary = getClass().getAnnotation(WebSocketBinary.class) != null;

    @Resource(name = "$")
    protected WebSocketNode node;

    protected WebSocketEngine engine;

    public final void preInit(HttpContext context, AnyValue conf) {
        InetSocketAddress addr = context.getServerAddress();
        this.engine = new WebSocketEngine(addr.getHostString() + ":" + addr.getPort() + "-[" + name() + "]", this.node, logger);
        if (this.node == null) this.node = createWebSocketNode();
        if (this.node == null) {
            this.node = new WebSocketNodeService();
            if (logger.isLoggable(Level.WARNING)) logger.warning("Not found WebSocketNode, create a default value for " + getClass().getName());
        }
        this.node.putWebSocketEngine(engine);
        this.node.init(conf);
        this.engine.init(conf);
    }

    public final void postDestroy(HttpContext context, AnyValue conf) {
        this.node.destroy(conf);
        super.destroy(context, conf);
        engine.close();
    }

    public String name() {
        return this.getClass().getSimpleName().replace("Servlet", "").replace("WebSocket", "").toLowerCase();
    }

    @Override
    public final void execute(final HttpRequest request, final HttpResponse response) throws IOException {
        final boolean debug = logger.isLoggable(Level.FINEST);
        if (!"GET".equalsIgnoreCase(request.getMethod())
            || !request.getConnection().contains("Upgrade")
            || !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            if (debug) logger.finest("WebSocket connect abort, (Not GET Method) or (Connection != Upgrade) or (Upgrade != websocket). request=" + request);
            response.finish(true);
            return;
        }
        String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            if (debug) logger.finest("WebSocket connect abort, Not found Sec-WebSocket-Key header. request=" + request);
            response.finish(true);
            return;
        }
        final WebSocket webSocket = this.createWebSocket();
        webSocket._engine = engine;
        webSocket._remoteAddress = request.getRemoteAddress();
        webSocket._remoteAddr = request.getRemoteAddr();
        Serializable sessionid = webSocket.onOpen(request);
        if (sessionid == null) {
            if (debug) logger.finest("WebSocket connect abort, Not found sessionid. request=" + request);
            response.finish(true);
            return;
        }
        webSocket._sessionid = sessionid;
        request.setKeepAlive(true);
        byte[] bytes = (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes();
        synchronized (digest) {
            bytes = digest.digest(bytes);
        }
        key = Base64.getEncoder().encodeToString(bytes);
        response.setStatus(101);
        response.setHeader("Connection", "Upgrade");
        response.addHeader("Upgrade", "websocket");
        response.addHeader("Sec-WebSocket-Accept", key);
        response.sendBody((ByteBuffer) null, null, new AsyncHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                HttpContext context = response.getContext();
                Serializable groupid = webSocket.createGroupid();
                if (groupid == null) {
                    if (debug) logger.finest("WebSocket connect abort, Create groupid abort. request = " + request);
                    response.finish(true);
                    return;
                }
                webSocket._groupid = groupid;
                engine.add(webSocket);
                context.submit(new WebSocketRunner(context, webSocket, response.removeChannel(), wsbinary));
                response.finish(true);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                logger.log(Level.FINEST, "WebSocket connect abort, Response send abort. request = " + request, exc);
                response.finish(true);
            }
        });
    }

    protected WebSocketNode createWebSocketNode() {
        return null;
    }

    protected abstract WebSocket createWebSocket();
}
