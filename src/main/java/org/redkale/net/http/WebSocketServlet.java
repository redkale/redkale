/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.logging.*;
import java.util.zip.*;
import org.redkale.annotation.Comment;
import org.redkale.annotation.*;
import org.redkale.boot.Application;
import static org.redkale.boot.Application.RESNAME_SERVER_RESFACTORY;
import org.redkale.convert.Convert;
import org.redkale.mq.MessageAgent;
import org.redkale.net.*;
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
 *                     WebSocket1                 WebSocket2
 *
 * </pre></blockquote>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class WebSocketServlet extends HttpServlet implements Resourcable {

    @Comment("WebScoket服务器给客户端进行ping操作的间隔时间, 单位: 秒")
    public static final String WEBPARAM__LIVEINTERVAL = "liveinterval";

    @Comment("WebScoket服务器最大连接数，为0表示无限制")
    public static final String WEBPARAM__WSMAXCONNS = "wsmaxconns";

    @Comment("WebScoket服务器操作WebSocketNode对应CacheSource并发数, 为-1表示无限制，为0表示系统默认值(CPU*8)")
    public static final String WEBPARAM__WSTHREADS = "wsthreads";

    @Comment("最大消息体长度, 小于1表示无限制")
    public static final String WEBPARAM__WSMAXBODY = "wsmaxbody";

    @Comment("接收客户端的分包(last=false)消息时是否自动合并包")
    public static final String WEBPARAM__WSMERGEMSG = "wsmergemsg";

    @Comment("加密解密器")
    public static final String WEBPARAM__CRYPTOR = "cryptor";

    @Comment("WebScoket服务器给客户端进行ping操作的默认间隔时间, 单位: 秒")
    public static final int DEFAILT_LIVEINTERVAL = 15;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final MessageDigest digest = getMessageDigest();

    private final BiConsumer<WebSocket, Object> restMessageConsumer = createRestOnMessageConsumer();

    protected Type messageRestType;  //RestWebSocket时会被修改

    //同RestWebSocket.single
    protected boolean single = true; //是否单用户单连接

    //同RestWebSocket.liveinterval
    protected int liveinterval = DEFAILT_LIVEINTERVAL;

    //同RestWebSocket.wsmaxconns
    protected int wsmaxconns = 0;

    //同RestWebSocket.wsthreads
    protected int wsthreads = 0;

    //同RestWebSocket.wsmaxbody
    protected int wsmaxbody = 32 * 1024;

    //同RestWebSocket.anyuser
    protected boolean anyuser = false;

    //同RestWebSocket.mergemsg
    protected boolean mergemsg = true;

    //同RestWebSocket.cryptor, 变量名不可改， 被Rest.createRestWebSocketServlet用到
    protected Cryptor cryptor;

    protected boolean permessageDeflate = false;

    protected MessageAgent messageAgent;

    @Resource(name = "jsonconvert", required = false)
    protected Convert jsonConvert;

    @Resource(name = "$_textconvert", required = false)
    protected Convert textConvert;

    @Resource(name = "$_binaryconvert", required = false)
    protected Convert binaryConvert;

    @Resource(name = "$_sendconvert", required = false)
    protected Convert sendConvert;

    @Resource(name = "$")
    protected WebSocketNode node;

    @Resource(name = RESNAME_SERVER_RESFACTORY)
    protected ResourceFactory resourceFactory;

    protected WebSocketServlet() {
        Type msgtype = String.class;
        try {
            for (Method method : this.getClass().getDeclaredMethods()) {
                if (!method.getName().equals("createWebSocket")) continue;
                if (method.getParameterCount() > 0) continue;
                Type rt = TypeToken.getGenericType(method.getGenericReturnType(), this.getClass());
                if (rt instanceof ParameterizedType) {
                    msgtype = ((ParameterizedType) rt).getActualTypeArguments()[1];
                }
                if (msgtype == Object.class) msgtype = String.class;
                break;
            }
        } catch (Exception e) {
            logger.warning(this.getClass().getName() + " not designate text message type on createWebSocket Method");
        }
        this.messageRestType = msgtype;
    }

    @Override
    final void preInit(Application application, HttpContext context, AnyValue conf) {
        if (this.textConvert == null) this.textConvert = jsonConvert;
        if (this.sendConvert == null) this.sendConvert = jsonConvert;
        InetSocketAddress addr = context.getServerAddress();
        if (this.node == null) this.node = createWebSocketNode();
        if (this.node == null) {  //没有部署SNCP，即不是分布式
            this.node = new WebSocketNodeService();
            if (logger.isLoggable(Level.WARNING)) logger.warning("Not found WebSocketNode, create a default value for " + getClass().getName());
        }
        if (this.node.sendConvert == null) this.node.sendConvert = this.sendConvert;
        if (this.messageAgent != null) this.node.messageAgent = this.messageAgent;
        {
            AnyValue props = conf;
            if (conf != null && conf.getAnyValue("properties") != null) props = conf.getAnyValue("properties");
            if (props != null) {
                String cryptorClass = props.getValue(WEBPARAM__CRYPTOR);
                if (cryptorClass != null && !cryptorClass.isEmpty()) {
                    try {
                        Class clazz = Thread.currentThread().getContextClassLoader().loadClass(cryptorClass);
                        this.cryptor = (Cryptor) clazz.getDeclaredConstructor().newInstance();
                        RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, cryptorClass);
                        if (resourceFactory != null && this.cryptor != null) resourceFactory.inject(this.cryptor);
                    } catch (Exception e) {
                        throw new HttpException(e);
                    }
                }
            }
        }
        if (application != null && application.isCompileMode()) return;
        //存在WebSocketServlet，则此WebSocketNode必须是本地模式Service
        this.node.localEngine = new WebSocketEngine("WebSocketEngine-" + addr.getHostString() + ":" + addr.getPort() + "-[" + resourceName() + "]",
            this.single, context, liveinterval, wsmaxconns, wsthreads, wsmaxbody, mergemsg, this.cryptor, this.node, this.sendConvert, logger);
        this.node.init(conf);
        this.node.localEngine.init(conf);

    }

    @Override
    final void postDestroy(Application application, HttpContext context, AnyValue conf) {
        this.node.postDestroy(conf);
        super.destroy(context, conf);
        if (application != null && application.isCompileMode()) return;
        this.node.localEngine.destroy(conf);
    }

    @Override
    public String resourceName() {
        return this.getClass().getSimpleName().replace("_Dyn", "").toLowerCase().replaceAll("websocket.*$", "").replaceAll("servlet.*$", "");
    }

    @Override
    public final void execute(final HttpRequest request, final HttpResponse response) throws IOException {
        final boolean debug = logger.isLoggable(Level.FINEST);
        if (!request.isWebSocket()) {
            if (debug) logger.finest("WebSocket connect abort, (Not GET Method) or (Connection != Upgrade) or (Upgrade != websocket). request=" + request);
            response.finish(true);
            return;
        }
        final String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            if (debug) logger.finest("WebSocket connect abort, Not found Sec-WebSocket-Key header. request=" + request);
            response.finish(true);
            return;
        }
        if (this.node.localEngine.isLocalConnLimited()) {
            if (debug) logger.finest("WebSocket connections limit, wsmaxconns=" + this.node.localEngine.getLocalWsMaxConns());
            response.finish(true);
            return;
        }
        final WebSocket webSocket = this.createWebSocket();
        webSocket._engine = this.node.localEngine;
        webSocket._channel = response.getChannel();
        webSocket._messageRestType = this.messageRestType;
        webSocket._textConvert = textConvert;
        webSocket._binaryConvert = binaryConvert;
        webSocket._sendConvert = sendConvert;
        webSocket._remoteAddress = request.getRemoteAddress();
        webSocket._remoteAddr = request.getRemoteAddr();
        webSocket._sncpAddress = this.node.localSncpAddress;
        if (this.permessageDeflate && request.getHeader("Sec-WebSocket-Extensions", "").contains("permessage-deflate")) {
            webSocket.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            webSocket.inflater = new Inflater(true);
        }
        initRestWebSocket(webSocket);
        CompletableFuture<String> sessionFuture = webSocket.onOpen(request);
        if (sessionFuture == null) {
            if (debug) logger.finest("WebSocket connect abort, Not found sessionid. request=" + request);
            response.finish(true);
            return;
        }
        BiConsumer<String, Throwable> sessionConsumer = (sessionid, ex) -> {
            if ((sessionid == null && webSocket.delayPackets == null) || ex != null) {
                if (debug || ex != null) logger.log(ex == null ? Level.FINEST : Level.FINE, "WebSocket connect abort, Not found sessionid or occur error. request=" + request, ex);
                response.finish(true);
                return;
            }
            //onOpen成功或者存在delayPackets
            webSocket._sessionid = sessionid;
            request.setKeepAlive(true);
            byte[] bytes = (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes();
            synchronized (digest) {
                bytes = digest.digest(bytes);
            }
            response.setStatus(101);
            response.setHeader("Connection", "Upgrade");
            response.addHeader("Upgrade", "websocket");
            response.addHeader("Sec-WebSocket-Accept", Base64.getEncoder().encodeToString(bytes));
            if (webSocket.deflater != null) response.addHeader("Sec-WebSocket-Extensions", "permessage-deflate");

            response.sendBody((ByteBuffer) null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    webSocket._readHandler = new WebSocketReadHandler(response.getContext(), webSocket, restMessageConsumer);
                    webSocket._writeHandler = new WebSocketWriteHandler(response.getContext(), webSocket);
                    Runnable createUseridHandler = () -> {
                        CompletableFuture<Serializable> userFuture = webSocket.createUserid();
                        if (userFuture == null) {
                            if (debug) logger.finest("WebSocket connect abort, Create userid abort. request = " + request);
                            response.finish(true);
                            return;
                        }
                        userFuture.whenComplete((userid, ex2) -> {
                            if ((userid == null && webSocket.delayPackets == null) || ex2 != null) {
                                if (debug || ex2 != null) logger.log(ex2 == null ? Level.FINEST : Level.FINE, "WebSocket connect abort, Create userid abort. request = " + request, ex2);
                                response.finish(true);
                                return;
                            }
                            Runnable runHandler = () -> {
                                webSocket._userid = userid;
                                if (single && !anyuser) {
                                    WebSocketServlet.this.node.existsWebSocket(userid).whenComplete((rs, nex) -> {
                                        if (rs) {
                                            CompletableFuture<Boolean> rcFuture = webSocket.onSingleRepeatConnect();
                                            Consumer<Boolean> task = (oldkilled) -> {
                                                if (oldkilled) {
                                                    WebSocketServlet.this.node.localEngine.addLocal(webSocket);
                                                    response.removeChannel();
                                                    webSocket._readHandler.startRead();
//                                                    WebSocketRunner runner = new WebSocketRunner(context, webSocket, restMessageConsumer);
//                                                    webSocket._runner = runner;
//                                                    runner.run(); //context.runAsync(runner);
                                                    response.finish(true);
                                                } else { //关闭新连接
                                                    response.finish(true);
                                                }
                                            };
                                            if (rcFuture == null) {
                                                task.accept(false);
                                            } else {
                                                rcFuture.whenComplete((r, e) -> {
                                                    if (e != null) {
                                                        response.finish(true);
                                                    } else {
                                                        task.accept(r);
                                                    }
                                                });
                                            }
                                        } else {
                                            WebSocketServlet.this.node.localEngine.addLocal(webSocket);
                                            response.removeChannel();
                                            webSocket._readHandler.startRead();
//                                            WebSocketRunner runner = new WebSocketRunner(context, webSocket, restMessageConsumer);
//                                            webSocket._runner = runner;
//                                            runner.run(); //context.runAsync(runner);
                                            response.finish(true);
                                        }
                                    });
                                } else {
                                    WebSocketServlet.this.node.localEngine.addLocal(webSocket);
                                    response.removeChannel();
                                    webSocket._readHandler.startRead();
//                                    WebSocketRunner runner = new WebSocketRunner(context, webSocket, restMessageConsumer);
//                                    webSocket._runner = runner;
//                                    runner.run(); //context.runAsync(runner);
                                    response.finish(true);
                                }
                            };
                            if (webSocket.delayPackets != null) { //存在待发送的消息
                                List<WebSocketPacket> delayPackets = webSocket.delayPackets;
                                webSocket.delayPackets = null;
                                CompletableFuture<Integer> cf = webSocket._writeHandler.send(delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                                cf.whenComplete((Integer v, Throwable t) -> {
                                    if (userid == null || t != null) {
                                        if (t != null) logger.log(Level.FINEST, "WebSocket connect abort, Response send delayPackets abort. request = " + request, t);
                                        response.finish(true);
                                    } else {
                                        runHandler.run();
                                    }
                                });
                            } else {
                                runHandler.run();
                            }
                        });
                    };
                    if (webSocket.delayPackets != null) { //存在待发送的消息
                        List<WebSocketPacket> delayPackets = webSocket.delayPackets;
                        webSocket.delayPackets = null;
                        CompletableFuture<Integer> cf = webSocket._writeHandler.send(delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                        cf.whenComplete((Integer v, Throwable t) -> {
                            if (sessionid == null || t != null) {
                                if (t != null) logger.log(Level.FINEST, "WebSocket connect abort, Response send delayPackets abort. request = " + request, t);
                                response.finish(true);
                            } else {
                                createUseridHandler.run();
                            }
                        });
                    } else {
                        createUseridHandler.run();
                    }
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    logger.log(Level.FINEST, "WebSocket connect abort, Response send abort. request = " + request, exc);
                    response.finish(true);
                }
            });
        };
        WorkThread workThread = WorkThread.currWorkThread();
        sessionFuture.whenComplete((sessionid, ex) -> {
            if (workThread == null || workThread == Thread.currentThread()) {
                sessionConsumer.accept(sessionid, ex);
            } else {
                workThread.execute(() -> sessionConsumer.accept(sessionid, ex));
            }
        });
    }

    protected abstract <G extends Serializable, T> WebSocket<G, T> createWebSocket();

    protected WebSocketNode createWebSocketNode() {
        return null;
    }

    protected void initRestWebSocket(WebSocket websocket) { //设置WebSocket中的@Resource资源
    }

    protected BiConsumer<WebSocket, Object> createRestOnMessageConsumer() {
        return null;
    }

    private static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new HttpException(e);
        }
    }

}
