/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static org.redkale.net.http.WebSocketServlet.DEFAILT_LIVEINTERVAL;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import java.util.logging.*;
import java.util.stream.*;
import org.redkale.convert.Convert;
import static org.redkale.net.http.WebSocket.RETCODE_GROUP_EMPTY;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class WebSocketEngine {

    //全局自增长ID
    private static final AtomicInteger sequence = new AtomicInteger();

    //Engine自增长序号ID
    private final int index;

    //当前WebSocket对应的Engine
    private final String engineid;

    //当前WebSocket对应的Node
    protected final WebSocketNode node;

    //HttpContext
    protected final HttpContext context;

    //Convert
    protected final Convert sendConvert;

    protected final boolean single; //是否单用户单连接

    //在线用户ID对应的WebSocket组，用于单用户单连接模式
    private final Map<Serializable, WebSocket> websockets = new ConcurrentHashMap<>();

    //在线用户ID对应的WebSocket组，用于单用户多连接模式
    private final Map<Serializable, List<WebSocket>> websockets2 = new ConcurrentHashMap<>();

    //用于PING的定时器
    private ScheduledThreadPoolExecutor scheduler;

    //日志
    protected final Logger logger;

    //FINEST日志级别
    protected final boolean finest;

    private int liveinterval;

    protected WebSocketEngine(String engineid, boolean single, HttpContext context, int liveinterval, WebSocketNode node, Convert sendConvert, Logger logger) {
        this.engineid = engineid;
        this.single = single;
        this.context = context;
        this.sendConvert = sendConvert;
        this.node = node;
        this.liveinterval = liveinterval;
        this.logger = logger;
        this.index = sequence.getAndIncrement();
        this.finest = logger.isLoggable(Level.FINEST);
    }

    void init(AnyValue conf) {
        final int interval = conf == null ? (liveinterval < 0 ? DEFAILT_LIVEINTERVAL : liveinterval) : conf.getIntValue("liveinterval", (liveinterval < 0 ? DEFAILT_LIVEINTERVAL : liveinterval));
        if (interval <= 0) return;
        if (scheduler != null) return;
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, engineid + "-WebSocket-LiveInterval-Thread");
            t.setDaemon(true);
            return t;
        });
        long delay = (interval - System.currentTimeMillis() / 1000 % interval) + index * 5;
        final int intervalms = interval * 1000;
        scheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            getLocalWebSockets().stream().filter(x -> (now - x.getLastSendTime()) > intervalms).forEach(x -> x.sendPing());
        }, delay, interval, TimeUnit.SECONDS);
        if (finest) logger.finest(this.getClass().getSimpleName() + "(" + engineid + ")" + " start keeplive(delay:" + delay + ", interval:" + interval + "s) scheduler executor");
    }

    void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    void add(WebSocket socket) {
        if (single) {
            websockets.put(socket._userid, socket);
        } else { //非线程安全， 在常规场景中无需锁
            List<WebSocket> list = websockets2.get(socket._userid);
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
                websockets2.put(socket._userid, list);
            }
            list.add(socket);
        }
        if (node != null) node.connect(socket._userid);
    }

    void remove(WebSocket socket) {
        Serializable userid = socket._userid;
        if (single) {
            websockets.remove(userid);
            if (node != null) node.disconnect(userid);
        } else { //非线程安全， 在常规场景中无需锁
            List<WebSocket> list = websockets2.get(userid);
            if (list != null) {
                list.remove(socket);
                if (list.isEmpty()) {
                    websockets2.remove(userid);
                    if (node != null) node.disconnect(userid);
                }
            }
        }
    }

    public CompletableFuture<Integer> broadcastMessage(final Object message, final boolean last) {
        return broadcastMessage(null, message, last);
    }

    public CompletableFuture<Integer> broadcastMessage(final Predicate<WebSocket> predicate, final Object message, final boolean last) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> broadcastMessage(json, last));
        }
        final boolean more = (!(message instanceof WebSocketPacket) || ((WebSocketPacket) message).sendBuffers == null);
        if (more) {
            final WebSocketPacket packet = (message instanceof WebSocketPacket) ? (WebSocketPacket) message
                : ((message == null || message instanceof CharSequence || message instanceof byte[])
                    ? new WebSocketPacket((Serializable) message, last) : new WebSocketPacket(this.sendConvert, message, last));
            packet.setSendBuffers(packet.encode(context.getBufferSupplier()));
            CompletableFuture<Integer> future = null;
            if (single) {
                for (WebSocket websocket : websockets.values()) {
                    if (predicate != null && !predicate.test(websocket)) continue;
                    future = future == null ? websocket.sendPacket(packet) : future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
                }
            } else {
                for (List<WebSocket> list : websockets2.values()) {
                    for (WebSocket websocket : list) {
                        if (predicate != null && !predicate.test(websocket)) continue;
                        future = future == null ? websocket.sendPacket(packet) : future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
                    }
                }
            }
            if (future != null) future = future.whenComplete((rs, ex) -> context.offerBuffer(packet.sendBuffers));
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        } else {
            CompletableFuture<Integer> future = null;
            if (single) {
                for (WebSocket websocket : websockets.values()) {
                    if (predicate != null && !predicate.test(websocket)) continue;
                    future = future == null ? websocket.send(message, last) : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                }
            } else {
                for (List<WebSocket> list : websockets2.values()) {
                    for (WebSocket websocket : list) {
                        if (predicate != null && !predicate.test(websocket)) continue;
                        future = future == null ? websocket.send(message, last) : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                    }
                }
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        }
    }

    public CompletableFuture<Integer> sendMessage(final Object message, final boolean last, final Serializable... userids) {
        if (message instanceof CompletableFuture) {
            return ((CompletableFuture) message).thenCompose((json) -> sendMessage(json, last, userids));
        }
        final boolean more = (!(message instanceof WebSocketPacket) || ((WebSocketPacket) message).sendBuffers == null) && userids.length > 1;
        if (more) {
            final WebSocketPacket packet = (message instanceof WebSocketPacket) ? (WebSocketPacket) message
                : ((message == null || message instanceof CharSequence || message instanceof byte[])
                    ? new WebSocketPacket((Serializable) message, last) : new WebSocketPacket(this.sendConvert, message, last));
            packet.setSendBuffers(packet.encode(context.getBufferSupplier()));
            CompletableFuture<Integer> future = null;
            if (single) {
                for (Serializable userid : userids) {
                    WebSocket websocket = websockets.get(userid);
                    if (websocket == null) continue;
                    future = future == null ? websocket.sendPacket(packet) : future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
                }
            } else {
                for (Serializable userid : userids) {
                    List<WebSocket> list = websockets2.get(userid);
                    if (list == null) continue;
                    for (WebSocket websocket : list) {
                        future = future == null ? websocket.sendPacket(packet) : future.thenCombine(websocket.sendPacket(packet), (a, b) -> a | (Integer) b);
                    }
                }
            }
            if (future != null) future = future.whenComplete((rs, ex) -> context.offerBuffer(packet.sendBuffers));
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        } else {
            CompletableFuture<Integer> future = null;
            if (single) {
                for (Serializable userid : userids) {
                    WebSocket websocket = websockets.get(userid);
                    if (websocket == null) continue;
                    future = future == null ? websocket.send(message, last) : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                }
            } else {
                for (Serializable userid : userids) {
                    List<WebSocket> list = websockets2.get(userid);
                    if (list == null) continue;
                    for (WebSocket websocket : list) {
                        future = future == null ? websocket.send(message, last) : future.thenCombine(websocket.send(message, last), (a, b) -> a | (Integer) b);
                    }
                }
            }
            return future == null ? CompletableFuture.completedFuture(RETCODE_GROUP_EMPTY) : future;
        }
    }

    Collection<WebSocket> getLocalWebSockets() {
        if (single) return websockets.values();
        List<WebSocket> list = new ArrayList<>();
        websockets2.values().forEach(x -> list.addAll(x));
        return list;
    }

    //适用于单用户单连接模式
    public WebSocket findLocalWebSocket(Serializable userid) {
        if (single) return websockets.get(userid);
        List<WebSocket> list = websockets2.get(userid);
        return (list == null || list.isEmpty()) ? null : list.get(list.size() - 1);
    }

    //适用于单用户多连接模式
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
