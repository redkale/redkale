/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import org.redkale.annotation.Comment;
import org.redkale.convert.Convert;
import org.redkale.net.Cryptor;
import static org.redkale.net.http.WebSocket.RETCODE_GROUP_EMPTY;
import static org.redkale.net.http.WebSocketServlet.*;
import org.redkale.util.AnyValue;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WebSocketEngine {

    @Comment("全局自增长ID, 为了确保在一个进程里多个WebSocketEngine定时发送ping时不会同时进行")
    private static final AtomicInteger sequence = new AtomicInteger();

    @Comment("Engine自增长序号ID")
    private final int index;

    @Comment("当前WebSocket对应的Engine")
    private final String engineid;

    @Comment("当前WebSocket对应的Node")
    protected final WebSocketNode node;

    // HttpContext
    protected final HttpContext context;

    // Convert
    protected final Convert sendConvert;

    @Comment("是否单用户单连接")
    protected final boolean single;

    @Comment("在线用户ID对应的WebSocket组，用于单用户单连接模式")
    private final Map<Serializable, WebSocket> websockets = new ConcurrentHashMap<>();

    @Comment("在线用户ID对应的WebSocket组，用于单用户多连接模式")
    private final Map<Serializable, List<WebSocket>> websockets2 = new ConcurrentHashMap<>();

    @Comment("当前连接数")
    protected final AtomicInteger currConns = new AtomicInteger();

    @Comment("用于PING的定时器")
    private ScheduledThreadPoolExecutor scheduler;

    @Comment("日志")
    protected final Logger logger;

    @Comment("PING的间隔秒数")
    protected int liveInterval;

    @Comment("最大连接数, 为0表示无限制")
    protected int wsMaxConns;

    @Comment("操作WebSocketNode对应CacheSource并发数, 为-1表示无限制，为0表示系统默认值(CPU*8)")
    protected int wsThreads;

    @Comment("最大消息体长度, 小于1表示无限制")
    protected int wsMaxBody;

    @Comment("加密解密器")
    protected Cryptor cryptor;

    protected WebSocketEngine(
            String engineid,
            boolean single,
            HttpContext context,
            int liveInterval,
            int wsMaxConns,
            int wsThreads,
            int wsMaxBody,
            Cryptor cryptor,
            WebSocketNode node,
            Convert sendConvert,
            Logger logger) {
        this.engineid = engineid;
        this.single = single;
        this.context = context;
        this.sendConvert = sendConvert;
        this.node = node;
        this.liveInterval = liveInterval;
        this.wsMaxConns = wsMaxConns;
        this.wsThreads = wsThreads;
        this.wsMaxBody = wsMaxBody;
        this.cryptor = cryptor;
        this.logger = logger;
        this.index = sequence.getAndIncrement();
    }

    void init(AnyValue conf) {
        AnyValue props = conf;
        if (conf != null && conf.getAnyValue("properties") != null) {
            props = conf.getAnyValue("properties");
        }
        this.liveInterval = props == null
                ? (liveInterval < 0 ? DEFAILT_LIVEINTERVAL : liveInterval)
                : props.getIntValue(WEBPARAM_LIVEINTERVAL, (liveInterval < 0 ? DEFAILT_LIVEINTERVAL : liveInterval));
        if (liveInterval <= 0) {
            return;
        }
        if (props != null) {
            this.wsMaxConns = props.getIntValue(WEBPARAM_WSMAXCONNS, this.wsMaxConns);
        }
        if (props != null) {
            this.wsThreads = props.getIntValue(WEBPARAM_WSTHREADS, this.wsThreads);
        }
        if (props != null) {
            this.wsMaxBody = props.getIntValue(WEBPARAM_WSMAXBODY, this.wsMaxBody);
        }
        if (scheduler != null) {
            return;
        }
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-WebSocket-" + engineid + "-LiveInterval-Thread");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.setRemoveOnCancelPolicy(true);
        long delay = (liveInterval - System.currentTimeMillis() / 1000 % liveInterval) + index * 5;
        final int intervalms = liveInterval * 1000;
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        long now = System.currentTimeMillis();
                        getLocalWebSockets().stream()
                                .filter(x -> (now - Math.max(x.getLastReadTime(), x.getLastSendTime())) > intervalms)
                                .forEach(x -> x.sendPing());
                    } catch (Throwable t) {
                        logger.log(
                                Level.SEVERE, "WebSocketEngine schedule(interval=" + liveInterval + "s) ping error", t);
                    }
                },
                delay,
                liveInterval,
                TimeUnit.SECONDS);
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(this.getClass().getSimpleName() + "(" + engineid + ")" + " start keeplive(wsmaxconns:"
                    + wsMaxConns + ", delay:" + delay + "s, interval:" + liveInterval + "s) scheduler executor");
        }
    }

    void destroy(AnyValue conf) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Comment("添加WebSocket")
    CompletableFuture<Void> addLocal(WebSocket socket) {
        if (single) {
            currConns.incrementAndGet();
            websockets.put(socket._userid, socket);
        } else { // 非线程安全， 在常规场景中无需锁
            List<WebSocket> list = websockets2.get(socket._userid);
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
                websockets2.put(socket._userid, list);
            }
            currConns.incrementAndGet();
            list.add(socket);
        }
        if (node != null) {
            return node.connect(socket._userid);
        }
        return null;
    }

    @Comment("从WebSocketEngine删除指定WebSocket")
    CompletableFuture<Void> removeLocalThenDisconnect(WebSocket socket) {
        Serializable userid = socket._userid;
        if (userid == null) {
            return null; // 尚未登录成功
        }
        if (single) {
            currConns.decrementAndGet();
            websockets.remove(userid);
            if (node != null) {
                return node.disconnect(userid);
            }
        } else { // 非线程安全， 在常规场景中无需锁
            List<WebSocket> list = websockets2.get(userid);
            if (list != null) {
                currConns.decrementAndGet();
                list.remove(socket);
                if (list.isEmpty()) {
                    websockets2.remove(userid);
                    return node.disconnect(userid);
                }
            }
        }
        return null;
    }

    @Comment("更改WebSocket的userid")
    CompletableFuture<Void> changeLocalUserid(WebSocket socket, final Serializable newuserid) {
        if (newuserid == null) {
            throw new NullPointerException("newuserid is null");
        }
        final Serializable olduserid = socket._userid;
        socket._userid = newuserid;
        if (single) {
            websockets.remove(olduserid);
            websockets.put(newuserid, socket);
        } else { // 非线程安全， 在常规场景中无需锁
            List<WebSocket> oldlist = websockets2.get(olduserid);
            if (oldlist != null) {
                oldlist.remove(socket);
                if (oldlist.isEmpty()) {
                    websockets2.remove(olduserid);
                }
            }
            List<WebSocket> newlist = websockets2.get(newuserid);
            if (newlist == null) {
                newlist = new CopyOnWriteArrayList<>();
                websockets2.put(newuserid, newlist);
            }
            newlist.add(socket);
        }
        if (node != null) {
            return node.changeUserid(olduserid, newuserid);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Comment("强制关闭本地用户的WebSocket")
    public int forceCloseLocalWebSocket(Serializable userid) {
        if (single) {
            WebSocket ws = websockets.get(userid);
            if (ws == null) {
                return 0;
            }
            ws.close();
            return 1;
        }
        List<WebSocket> list = websockets2.get(userid);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        List<WebSocket> list2 = new ArrayList<>(list);
        for (WebSocket ws : list2) {
            ws.close();
        }
        return list2.size();
    }

    @Comment("给所有连接用户发送消息")
    public CompletableFuture<Integer> broadcastLocalMessage(final Object message, final boolean last) {
        return broadcastLocalMessage((Predicate) null, message, last);
    }

    @Comment("给指定WebSocket连接用户发送消息")
    public CompletableFuture<Integer> broadcastLocalMessage(
            final WebSocketRange wsrange, final Object message, final boolean last) {
        Predicate<WebSocket> predicate = wsrange == null ? null : ws -> ws.predicate(wsrange);
        return broadcastLocalMessage(predicate, message, last);
    }

    @Comment("给指定WebSocket连接用户发送消息")
    public CompletableFuture<Integer> broadcastLocalMessage(
            final Predicate<WebSocket> predicate, final Object message, final boolean last) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose(packet -> broadcastLocalMessage(predicate, packet, last));
        }
        //        final boolean more = (!(message instanceof WebSocketPacket) || ((WebSocketPacket) message).sendBuffers
        // == null);
        //        if (more) {
        //            Supplier<ByteBuffer> bufferSupplier = null;
        //            Consumer<ByteBuffer> bufferConsumer = null;
        //            //此处的WebSocketPacket只能是包含payload或bytes内容的，不能包含sendConvert、sendJson、sendBuffers
        //            final WebSocketPacket packet = (message instanceof WebSocketPacket) ? (WebSocketPacket) message
        //                : ((message == null || message instanceof CharSequence || message instanceof byte[])
        //                    ? new WebSocketPacket((Serializable) message, last) : new
        // WebSocketPacket(this.sendConvert, message, last));
        //            //packet.setSendBuffers(packet.encode(context.getBufferSupplier(), context.getBufferConsumer(),
        // cryptor));
        //            CompletableFuture<Integer> future = null;
        //            if (single) {
        //                for (WebSocket websocket : websockets.values()) {
        //                    if (predicate != null && !predicate.test(websocket)) continue;
        //                    if (bufferSupplier == null) {
        //                        bufferSupplier = websocket.getBufferSupplier();
        //                        bufferConsumer = websocket.getBufferConsumer();
        //                        packet.encodePacket(bufferSupplier, bufferConsumer, cryptor);
        //                    }
        //                    future = future == null ? websocket.sendPacket(packet) :
        // future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
        //                }
        //            } else {
        //                for (List<WebSocket> list : websockets2.values()) {
        //                    for (WebSocket websocket : list) {
        //                        if (predicate != null && !predicate.test(websocket)) continue;
        //                        if (bufferSupplier == null) {
        //                            bufferSupplier = websocket.getBufferSupplier();
        //                            bufferConsumer = websocket.getBufferConsumer();
        //                            packet.encodePacket(bufferSupplier, bufferConsumer, cryptor);
        //                        }
        //                        future = future == null ? websocket.sendPacket(packet) :
        // future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
        //                    }
        //                }
        //            }
        //            final Consumer<ByteBuffer> bufferConsumer0 = bufferConsumer;
        //            if (future != null) future.whenComplete((rs, ex) -> {
        //                    if (packet.sendBuffers != null && bufferConsumer0 != null) {
        //                        for (ByteBuffer buffer : packet.sendBuffers) {
        //                            bufferConsumer0.accept(buffer);
        //                        }
        //                    }
        //                });
        //            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        //        } else {
        CompletableFuture<Integer> future = null;
        if (single) {
            for (WebSocket websocket : websockets.values()) {
                if (predicate != null && !predicate.test(websocket)) {
                    continue;
                }
                future = future == null
                        ? websocket.send(message, last)
                        : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
            }
        } else {
            for (List<WebSocket> list : websockets2.values()) {
                for (WebSocket websocket : list) {
                    if (predicate != null && !predicate.test(websocket)) {
                        continue;
                    }
                    future = future == null
                            ? websocket.send(message, last)
                            : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                }
            }
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        // }
    }

    @Comment("给指定用户组发送消息")
    public CompletableFuture<Integer> sendLocalMessage(
            final Object message, final boolean last, final Stream<? extends Serializable> userids) {
        Object[] array = userids.toArray();
        Serializable[] ss = new Serializable[array.length];
        for (int i = 0; i < array.length; i++) {
            ss[i] = (Serializable) array[i];
        }
        return sendLocalMessage(message, last, ss);
    }

    @Comment("给指定用户组发送消息")
    public CompletableFuture<Integer> sendLocalMessage(
            final Object message, final boolean last, final Serializable... userids) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose(packet -> sendLocalMessage(packet, last, userids));
        }
        //        final boolean more = userids.length > 1;
        //        if (more) {
        //            Supplier<ByteBuffer> bufferSupplier = null;
        //            Consumer<ByteBuffer> bufferConsumer = null;
        //            //此处的WebSocketPacket只能是包含payload或bytes内容的，不能包含sendConvert、sendJson、sendBuffers
        //            final WebSocketPacket packet = (message instanceof WebSocketPacket) ? (WebSocketPacket) message
        //                : ((message == null || message instanceof CharSequence || message instanceof byte[])
        //                    ? new WebSocketPacket((Serializable) message, last) : new
        // WebSocketPacket(this.sendConvert, message, last));
        //            //packet.encode(context.getBufferSupplier(), context.getBufferConsumer(), cryptor);
        //            CompletableFuture<Integer> future = null;
        //            if (single) {
        //                for (Serializable userid : userids) {
        //                    WebSocket websocket = websockets.get(userid);
        //                    if (websocket == null) continue;
        //                    if (bufferSupplier == null) {
        //                        bufferSupplier = websocket.getBufferSupplier();
        //                        bufferConsumer = websocket.getBufferConsumer();
        //                        packet.encodePacket(bufferSupplier, bufferConsumer, cryptor);
        //                    }
        //                    future = future == null ? websocket.sendPacket(packet) :
        // future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
        //                }
        //            } else {
        //                for (Serializable userid : userids) {
        //                    List<WebSocket> list = websockets2.get(userid);
        //                    if (list == null) continue;
        //                    for (WebSocket websocket : list) {
        //                        if (bufferSupplier == null) {
        //                            bufferSupplier = websocket.getBufferSupplier();
        //                            bufferConsumer = websocket.getBufferConsumer();
        //                            packet.encodePacket(bufferSupplier, bufferConsumer, cryptor);
        //                        }
        //                        future = future == null ? websocket.sendPacket(packet) :
        // future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
        //                    }
        //                }
        //            }
        //            final Consumer<ByteBuffer> bufferConsumer0 = bufferConsumer;
        //            if (future != null) future.whenComplete((rs, ex) -> {
        //                    if (packet.sendBuffers != null && bufferConsumer0 != null) {
        //                        for (ByteBuffer buffer : packet.sendBuffers) {
        //                            bufferConsumer0.accept(buffer);
        //                        }
        //                    }
        //                });
        //            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        //        } else {
        CompletableFuture<Integer> future = null;
        if (single) {
            for (Serializable userid : userids) {
                WebSocket websocket = websockets.get(userid);
                if (websocket == null) {
                    continue;
                }
                future = future == null
                        ? websocket.send(message, last)
                        : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
            }
        } else {
            for (Serializable userid : userids) {
                List<WebSocket> list = websockets2.get(userid);
                if (list == null) {
                    continue;
                }
                for (WebSocket websocket : list) {
                    future = future == null
                            ? websocket.send(message, last)
                            : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                }
            }
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        // }
    }

    @Comment("给指定WebSocket连接用户发起操作指令")
    public CompletableFuture<Integer> broadcastLocalAction(final WebSocketAction action) {
        CompletableFuture<Integer> future = null;
        if (single) {
            for (WebSocket websocket : websockets.values()) {
                future = future == null
                        ? websocket.action(action)
                        : future.thenCombine(websocket.action(action), (a, b) -> a | (Integer) b);
            }
        } else {
            for (List<WebSocket> list : websockets2.values()) {
                for (WebSocket websocket : list) {
                    future = future == null
                            ? websocket.action(action)
                            : future.thenCombine(websocket.action(action), (a, b) -> a | (Integer) b);
                }
            }
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
    }

    @Comment("给指定用户组发送操作")
    public CompletableFuture<Integer> sendLocalAction(
            final WebSocketAction action, final Stream<? extends Serializable> userids) {
        Object[] array = userids.toArray();
        Serializable[] ss = new Serializable[array.length];
        for (int i = 0; i < array.length; i++) {
            ss[i] = (Serializable) array[i];
        }
        return sendLocalAction(action, ss);
    }

    @Comment("给指定用户组发送操作")
    public CompletableFuture<Integer> sendLocalAction(final WebSocketAction action, final Serializable... userids) {
        CompletableFuture<Integer> future = null;
        if (single) {
            for (Serializable userid : userids) {
                WebSocket websocket = websockets.get(userid);
                if (websocket == null) {
                    continue;
                }
                future = future == null
                        ? websocket.action(action)
                        : future.thenCombine(websocket.action(action), (a, b) -> a | (Integer) b);
            }
        } else {
            for (Serializable userid : userids) {
                List<WebSocket> list = websockets2.get(userid);
                if (list == null) {
                    continue;
                }
                for (WebSocket websocket : list) {
                    future = future == null
                            ? websocket.action(action)
                            : future.thenCombine(websocket.action(action), (a, b) -> a | (Integer) b);
                }
            }
        }
        return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
    }

    @Comment("获取WebSocketNode对象")
    public WebSocketNode getWebSocketNode() {
        return node;
    }

    @Comment("获取最大连接数")
    public int getLocalWsMaxConns() {
        return this.wsMaxConns;
    }

    @Comment("连接数是否达到上限")
    public boolean isLocalConnLimited() {
        if (this.wsMaxConns < 1) {
            return false;
        }
        return currConns.get() >= this.wsMaxConns;
    }

    @Comment("获取所有连接")
    public Collection<WebSocket> getLocalWebSockets() {
        if (single) {
            return websockets.values();
        }
        List<WebSocket> list = new ArrayList<>();
        websockets2.values().forEach(x -> list.addAll(x));
        return list;
    }

    @Comment("获取所有连接")
    public void forEachLocalWebSocket(Consumer<WebSocket> consumer) {
        if (consumer == null) {
            return;
        }
        if (single) {
            websockets.values().stream().forEach(consumer);
        } else {
            websockets2.values().forEach(x -> x.stream().forEach(consumer));
        }
    }

    @Comment("获取当前连接总数")
    public int getLocalWebSocketSize() {
        if (single) {
            return websockets.size();
        }
        return (int) websockets2.values().stream()
                .mapToInt(sublist -> sublist.size())
                .count();
    }

    @Comment("获取当前用户总数")
    public Set<Serializable> getLocalUserSet() {
        return single ? new LinkedHashSet<>(websockets.keySet()) : new LinkedHashSet<>(websockets2.keySet());
    }

    @Comment("获取当前用户总数")
    public int getLocalUserSize() {
        return single ? websockets.size() : websockets2.size();
    }

    @Comment("适用于单用户单连接模式")
    public WebSocket findLocalWebSocket(Serializable userid) {
        if (single) {
            return websockets.get(userid);
        }
        List<WebSocket> list = websockets2.get(userid);
        return (list == null || list.isEmpty()) ? null : list.get(list.size() - 1);
    }

    @Comment("适用于单用户多连接模式")
    public Stream<WebSocket> getLocalWebSockets(Serializable userid) {
        if (single) {
            WebSocket websocket = websockets.get(userid);
            return websocket == null ? Stream.empty() : Stream.of(websocket);
        } else {
            List<WebSocket> list = websockets2.get(userid);
            return list == null ? Stream.empty() : list.stream();
        }
    }

    public boolean existsLocalWebSocket(Serializable userid) {
        return single ? websockets.containsKey(userid) : websockets2.containsKey(userid);
    }

    public String getEngineid() {
        return engineid;
    }
}
