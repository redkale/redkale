/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import javax.annotation.*;

/**
 * 当WebSocketServlet接收一个TCP连接后，进行协议判断，如果成功就会创建一个WebSocket。
 * 
 *                                    WebSocketServlet
 *                                            |
 *                                            |
 *                                    WebSocketEngine   
 *                                    /             \
 *                                 /                  \
 *                              /                       \
 *                     WebSocketGroup1            WebSocketGroup2
 *                        /        \                /        \
 *                      /           \             /           \  
 *               WebSocket1     WebSocket2   WebSocket3    WebSocket4
 *
 * @author zhangjx
 */
public abstract class WebSocketServlet extends HttpServlet implements Nameable {

    public static final String WEBPARAM__LIVEINTERVAL = "liveinterval";

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

    //是否用于二进制流传输
    protected final boolean wsbinary = getClass().getAnnotation(WebSocketBinary.class) != null;

    @Resource(name = "$")
    protected WebSocketNode node;

    protected WebSocketEngine engine;

    @Override
    public void init(Context context, AnyValue conf) {
        InetSocketAddress addr = context.getServerAddress();
        this.engine = new WebSocketEngine(addr.getHostString() + ":" + addr.getPort() + "-" + name(), logger);
        this.node.putWebSocketEngine(engine);
        this.node.init(conf);
        this.engine.init(conf);
    }

    @Override
    public void destroy(Context context, AnyValue conf) {
        this.node.destroy(conf);
        super.destroy(context, conf);
        engine.close();
    }

    @Override
    public String name() {
        return this.getClass().getSimpleName().replace("Servlet", "").replace("WebSocket", "").toLowerCase();
    }

    @Override
    public final void execute(HttpRequest request, HttpResponse response) throws IOException {
        final boolean debug = logger.isLoggable(Level.FINER);
        if (!"GET".equalsIgnoreCase(request.getMethod())
                || !request.getConnection().contains("Upgrade")
                || !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            if (debug) logger.finer("WebSocket connect abort, (Not GET Method) or (Connection != Upgrade) or (Upgrade != websocket). request=" + request);
            response.finish(true);
            return;
        }
        String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            if (debug) logger.finer("WebSocket connect abort, Not found Sec-WebSocket-Key header. request=" + request);
            response.finish(true);
            return;
        }
        final WebSocket webSocket = this.createWebSocket();
        webSocket.engine = engine;
        webSocket.node = node;
        Serializable sessionid = webSocket.onOpen(request);
        if (sessionid == null) {
            if (debug) logger.finer("WebSocket connect abort, Not found sessionid. request=" + request);
            response.finish(true);
            return;
        }
        webSocket.sessionid = sessionid;
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
        final boolean mask = "13".equals(request.getHeader("Sec-WebSocket-Version"));
        response.sendBody((ByteBuffer) null, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                HttpContext context = response.getContext();
                Serializable groupid = webSocket.createGroupid();
                if (groupid == null) {
                    if (debug) logger.finer("WebSocket connect abort, Create groupid abort");
                    response.finish(true);
                    return;
                }
                webSocket.groupid = groupid;
                engine.add(webSocket);
                context.submit(new WebSocketRunner(context, webSocket, response.removeChannel(), mask, wsbinary));
                response.finish(true);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                logger.log(Level.FINER, "WebSocket connect abort, Response send abort", exc);
                response.finish(true);
            }
        });
    }

    protected abstract WebSocket createWebSocket();
}
