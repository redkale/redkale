/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.logging.*;
import java.util.zip.*;
import org.redkale.annotation.*;
import org.redkale.annotation.Comment;
import org.redkale.boot.Application;
import static org.redkale.boot.Application.RESNAME_SERVER_RESFACTORY;
import org.redkale.convert.Convert;
import org.redkale.inject.Resourcable;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 *
 *
 * <blockquote>
 *
 * <pre>
 * 当WebSocketServlet接收一个TCP连接后，进行协议判断，如果成功就会创建一个WebSocket。
 *
 *                                    WebSocketServlet
 *                                            |
 *                                            |
 *                                   WebSocketEngine
 *                                    WebSocketNode
 *                                   /             \
 *                                  /               \
 *                                 /                 \
 *                            WebSocket1          WebSocket2
 *
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class WebSocketServlet extends HttpServlet implements Resourcable {

    @Comment("WebScoket服务器给客户端进行ping操作的间隔时间, 单位: 秒")
    public static final String WEBPARAM_LIVEINTERVAL = "liveinterval";

    @Comment("WebScoket服务器最大连接数，为0表示无限制")
    public static final String WEBPARAM_WSMAXCONNS = "wsmaxconns";

    @Comment("WebScoket服务器操作WebSocketNode对应CacheSource并发数, 为-1表示无限制，为0表示系统默认值(CPU*8)")
    public static final String WEBPARAM_WSTHREADS = "wsthreads";

    @Comment("最大消息体长度, 小于1表示无限制")
    public static final String WEBPARAM_WSMAXBODY = "wsmaxbody";

    @Comment("加密解密器")
    public static final String WEBPARAM_CRYPTOR = "cryptor";

    @Comment("WebScoket服务器给客户端进行ping操作的默认间隔时间, 单位: 秒")
    public static final int DEFAILT_LIVEINTERVAL = 15;

    protected final Logger logger =
            Logger.getLogger(this.getClass().getSuperclass().getSimpleName());

    private final BiConsumer<WebSocket, Object> restMessageConsumer = createRestOnMessageConsumer();

    private final ObjectPool<ByteArray> byteArrayPool =
            ObjectPool.createSafePool(1000, () -> new ByteArray(), null, ByteArray::recycle);

    // RestWebSocket时会被修改
    protected Type messageRestType;

    // 同RestWebSocket.single，是否单用户单连接
    protected boolean single = true;

    // 同RestWebSocket.liveinterval
    protected int liveinterval = DEFAILT_LIVEINTERVAL;

    // 同RestWebSocket.wsmaxconns
    protected int wsmaxconns = 0;

    // 同RestWebSocket.wsthreads
    protected int wsthreads = 0;

    // 同RestWebSocket.wsmaxbody
    protected int wsmaxbody = 32 * 1024;

    // 同RestWebSocket.anyuser
    protected boolean anyuser = false;

    // 同RestWebSocket.cryptor, 变量名不可改， 被Rest.createRestWebSocketServlet用到
    protected Cryptor cryptor;

    protected boolean permessageDeflate = false;

    protected MessageAgent messageAgent;

    @Resource(name = "jsonconvert", required = false)
    protected Convert jsonConvert;

    @Resource(name = Resource.PARENT_NAME + "_textconvert", required = false)
    protected Convert textConvert;

    @Resource(name = Resource.PARENT_NAME + "_binaryconvert", required = false)
    protected Convert binaryConvert;

    @Resource(name = Resource.PARENT_NAME + "_sendconvert", required = false)
    protected Convert sendConvert;

    @Resource(name = Resource.PARENT_NAME)
    protected WebSocketNode webSocketNode;

    @Resource(name = RESNAME_SERVER_RESFACTORY)
    protected ResourceFactory resourceFactory;

    protected WebSocketServlet() {
        Type msgType = String.class;
        try {
            for (Method method : this.getClass().getDeclaredMethods()) {
                if (!method.getName().equals("createWebSocket")) {
                    continue;
                }
                if (method.getParameterCount() > 0) {
                    continue;
                }
                Type rt = TypeToken.getGenericType(method.getGenericReturnType(), this.getClass());
                if (rt instanceof ParameterizedType) {
                    msgType = ((ParameterizedType) rt).getActualTypeArguments()[1];
                }
                if (msgType == Object.class) {
                    msgType = String.class;
                }
                break;
            }
        } catch (Exception e) {
            logger.warning(this.getClass().getName() + " not designate text message type on createWebSocket Method");
        }
        this.messageRestType = msgType;
    }

    @Override
    final void preInit(Application application, HttpContext context, AnyValue conf) {
        this._nonBlocking = true;
        if (this.textConvert == null) {
            this.textConvert = jsonConvert;
        }
        if (this.sendConvert == null) {
            this.sendConvert = jsonConvert;
        }
        InetSocketAddress addr = context.getServerAddress();
        if (this.webSocketNode == null) {
            this.webSocketNode = createWebSocketNode();
        }
        if (this.webSocketNode == null) { // 没有部署SNCP，即不是分布式
            this.webSocketNode = new WebSocketNodeService();
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Not found WebSocketNode, create a default value for "
                        + getClass().getName());
            }
        }
        if (this.webSocketNode.sendConvert == null) {
            this.webSocketNode.sendConvert = this.sendConvert;
        }
        if (this.messageAgent != null) {
            this.webSocketNode.messageAgent = this.messageAgent;
        }
        {
            AnyValue props = conf;
            if (conf != null && conf.getAnyValue("properties") != null) {
                props = conf.getAnyValue("properties");
            }
            if (props != null) {
                String cryptorClass = props.getValue(WEBPARAM_CRYPTOR);
                if (cryptorClass != null && !cryptorClass.isEmpty()) {
                    try {
                        Class clazz =
                                Thread.currentThread().getContextClassLoader().loadClass(cryptorClass);
                        this.cryptor = (Cryptor) clazz.getDeclaredConstructor().newInstance();
                        RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, cryptorClass);
                        if (resourceFactory != null && this.cryptor != null) {
                            resourceFactory.inject(this.cryptor);
                        }
                    } catch (Exception e) {
                        throw new HttpException(e);
                    }
                }
            }
        }
        if (application != null && application.isCompileMode()) {
            return;
        }
        // 存在WebSocketServlet，则此WebSocketNode必须是本地模式Service
        String id = "WebSocketEngine-" + addr.getHostString() + ":" + addr.getPort() + "-[" + resourceName() + "]";
        this.webSocketNode.localEngine = new WebSocketEngine(
                id,
                this.single,
                context,
                liveinterval,
                wsmaxconns,
                wsthreads,
                wsmaxbody,
                this.cryptor,
                this.webSocketNode,
                this.sendConvert,
                logger);
        this.webSocketNode.init(conf);
        this.webSocketNode.localEngine.init(conf);
    }

    @Override
    final void postDestroy(Application application, HttpContext context, AnyValue conf) {
        this.webSocketNode.postDestroy(conf);
        super.destroy(context, conf);
        if (application != null && application.isCompileMode()) {
            return;
        }
        this.webSocketNode.localEngine.destroy(conf);
    }

    @Override
    public String resourceName() {
        return this.getClass()
                .getSimpleName()
                .replace("_Dyn", "")
                .toLowerCase()
                .replaceAll("websocket.*$", "")
                .replaceAll("servlet.*$", "");
    }

    @Override // 在IOThread中执行
    @NonBlocking
    public final void execute(final HttpRequest request, final HttpResponse response) throws IOException {
        final boolean debug = logger.isLoggable(Level.FINER);
        final boolean fine = logger.isLoggable(Level.FINE);
        if (!request.isWebSocket()) {
            if (fine) {
                logger.log(
                        Level.FINE,
                        "WebSocket connect abort, (Not GET Method)/(Connection!=Upgrade)/(Upgrade!=websocket). request="
                                + request);
            }
            response.kill();
            return;
        }
        final String key = request.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            if (fine) {
                logger.log(
                        Level.FINE, "WebSocket connect abort, Not found Sec-WebSocket-Key header. request=" + request);
            }
            response.kill();
            return;
        }
        if (this.webSocketNode.localEngine.isLocalConnLimited()) {
            logger.log(
                    Level.WARNING,
                    "WebSocket connections limit, wsmaxconns=" + this.webSocketNode.localEngine.getLocalWsMaxConns());
            response.kill();
            return;
        }
        final WebSocket webSocket = this.createWebSocket();
        webSocket._engine = this.webSocketNode.localEngine;
        webSocket._channel = response.getChannel();
        webSocket._messageRestType = this.messageRestType;
        webSocket._textConvert = textConvert;
        webSocket._binaryConvert = binaryConvert;
        webSocket._sendConvert = sendConvert;
        webSocket._remoteAddress = request.getRemoteAddress();
        webSocket._remoteAddr = request.getRemoteAddr();
        webSocket._sncpAddress = this.webSocketNode.localSncpAddress;
        if (this.permessageDeflate
                && request.getHeader("Sec-WebSocket-Extensions", "").contains("permessage-deflate")) {
            webSocket.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            webSocket.inflater = new Inflater(true);
        }

        initRestWebSocket(webSocket);
        CompletableFuture<String> sessionFuture = webSocket.onOpen(request);
        if (sessionFuture == null) {
            if (debug) {
                logger.log(Level.FINER, "WebSocket connect abort, Not found sessionid. request=" + request);
            }
            response.kill();
            return;
        }
        BiConsumer<String, Throwable> sessionConsumer = (sessionid, ex) -> {
            if ((sessionid == null && webSocket.delayPackets == null) || ex != null) {
                if (debug || ex != null) {
                    logger.log(
                            ex == null ? Level.FINER : Level.FINE,
                            "WebSocket connect abort, Not found sessionid or occur error. request=" + request,
                            ex);
                }
                response.kill();
                return;
            }
            // onOpen成功或者存在delayPackets
            webSocket._sessionid = sessionid;
            request.setKeepAlive(true);
            byte[] bytes = sha1(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11");

            response.setStatus(101);
            response.setHeader("Connection", "Upgrade");
            response.addHeader("Upgrade", "websocket");
            response.addHeader("Sec-WebSocket-Accept", Base64.getEncoder().encodeToString(bytes));
            if (webSocket.deflater != null) {
                response.addHeader("Sec-WebSocket-Extensions", "permessage-deflate");
            }

            response.sendBody((ByteBuffer) null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    Traces.currentTraceid(request.getTraceid());
                    webSocket._readHandler = new WebSocketReadHandler(
                            response.getContext(), webSocket, byteArrayPool, restMessageConsumer);
                    webSocket._writeHandler =
                            new WebSocketWriteHandler(response.getContext(), webSocket, byteArrayPool);

                    Runnable createUseridHandler = () -> {
                        CompletableFuture<Serializable> userFuture = webSocket.createUserid();
                        if (userFuture == null) {
                            if (debug) {
                                logger.log(
                                        Level.FINEST,
                                        "WebSocket connect abort, Create userid abort. request = " + request);
                            }
                            response.kill();
                            return;
                        }
                        userFuture.whenComplete((userid, ex2) -> {
                            Traces.currentTraceid(request.getTraceid());
                            if ((userid == null && webSocket.delayPackets == null) || ex2 != null) {
                                if (debug || ex2 != null) {
                                    logger.log(
                                            ex2 == null ? Level.FINEST : Level.FINE,
                                            "WebSocket connect abort, Create userid abort. request = " + request,
                                            ex2);
                                }
                                response.kill();
                                return;
                            }
                            if (userid != null
                                    && userid.getClass() != Integer.class
                                    && userid.getClass() != Long.class
                                    && userid.getClass() != String.class
                                    && userid.getClass() != BigInteger.class) {
                                logger.log(
                                        Level.SEVERE,
                                        "WebSocket userid must be Integer/Long/String/BigInteger type, but "
                                                + userid.getClass().getName());
                                response.kill();
                                return;
                            }

                            Runnable runHandler = () -> {
                                webSocket._userid = userid;
                                if (single && !anyuser) {
                                    webSocketNode.existsWebSocket(userid).whenComplete((rs, nex) -> {
                                        Traces.currentTraceid(request.getTraceid());
                                        if (rs) {
                                            CompletableFuture<Boolean> rcFuture = webSocket.onSingleRepeatConnect();
                                            Consumer<Boolean> task = oldkilled -> {
                                                if (oldkilled) {
                                                    webSocketNode.localEngine.addLocal(webSocket);
                                                    response.removeChannel();
                                                    webSocket._readHandler.startRead();
                                                    response.kill();
                                                } else { // 关闭新连接
                                                    response.kill();
                                                }
                                            };
                                            if (rcFuture == null) {
                                                task.accept(false);
                                            } else {
                                                rcFuture.whenComplete((r, e) -> {
                                                    if (e != null) {
                                                        response.kill();
                                                    } else {
                                                        task.accept(r);
                                                    }
                                                });
                                            }
                                        } else {
                                            webSocketNode.localEngine.addLocal(webSocket);
                                            response.removeChannel();
                                            webSocket._readHandler.startRead();
                                            response.kill();
                                        }
                                    });
                                } else {
                                    webSocketNode.localEngine.addLocal(webSocket);
                                    response.removeChannel();
                                    webSocket._readHandler.startRead();
                                    response.kill();
                                }
                            };
                            if (webSocket.delayPackets != null) { // 存在待发送的消息
                                List<WebSocketPacket> delayPackets = webSocket.delayPackets;
                                webSocket.delayPackets = null;
                                // CompletableFuture<Integer> cf = webSocket._writeIOThread.send(webSocket,
                                // delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                                CompletableFuture<Integer> cf = webSocket._writeHandler.send(
                                        delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                                cf.whenComplete((Integer v, Throwable t) -> {
                                    Traces.currentTraceid(request.getTraceid());
                                    if (userid == null || t != null) {
                                        if (t != null) {
                                            logger.log(
                                                    Level.FINEST,
                                                    "WebSocket connect abort, Response send delayPackets abort. request = "
                                                            + request,
                                                    t);
                                        }
                                        response.kill();
                                    } else {
                                        runHandler.run();
                                    }
                                });
                            } else {
                                runHandler.run();
                            }
                        });
                    };
                    if (webSocket.delayPackets != null) { // 存在待发送的消息
                        List<WebSocketPacket> delayPackets = webSocket.delayPackets;
                        webSocket.delayPackets = null;
                        // CompletableFuture<Integer> cf = webSocket._writeIOThread.send(webSocket,
                        // delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                        CompletableFuture<Integer> cf = webSocket._writeHandler.send(
                                delayPackets.toArray(new WebSocketPacket[delayPackets.size()]));
                        cf.whenComplete((Integer v, Throwable t) -> {
                            Traces.currentTraceid(request.getTraceid());
                            if (sessionid == null || t != null) {
                                if (t != null) {
                                    logger.log(
                                            Level.FINEST,
                                            "WebSocket connect abort, Response send delayPackets abort. request = "
                                                    + request,
                                            t);
                                }
                                response.kill();
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
                    response.kill();
                }
            });
        };
        WorkThread workThread = WorkThread.currentWorkThread();
        sessionFuture.whenComplete((sessionid, ex) -> {
            Traces.currentTraceid(request.getTraceid());
            if (workThread == null || workThread == Thread.currentThread()) {
                sessionConsumer.accept(sessionid, ex);
            } else {
                workThread.runWork(() -> {
                    Traces.currentTraceid(request.getTraceid());
                    sessionConsumer.accept(sessionid, ex);
                });
            }
        });
    }

    protected abstract <G extends Serializable, T> WebSocket<G, T> createWebSocket();

    protected WebSocketNode createWebSocketNode() {
        return null;
    }

    protected void initRestWebSocket(WebSocket websocket) { // 设置WebSocket中的@Resource资源
    }

    protected BiConsumer<WebSocket, Object> createRestOnMessageConsumer() {
        return null;
    }

    private static byte[] sha1(String str) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(str.getBytes());
        } catch (Exception e) {
            throw new HttpException(e);
        }
    }
}
