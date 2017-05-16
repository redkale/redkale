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
import java.util.logging.*;
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

    //在线用户ID对应的WebSocket组，当WebSocketGroup内没有WebSocket会从containers删掉
    private final Map<Serializable, WebSocketGroup> containers = new ConcurrentHashMap<>();

    //用于PING的定时器
    private ScheduledThreadPoolExecutor scheduler;

    //日志
    protected final Logger logger;

    //FINEST日志级别
    protected final boolean finest;

    protected WebSocketEngine(String engineid, WebSocketNode node, Logger logger) {
        this.engineid = engineid;
        this.node = node;
        this.logger = logger;
        this.index = sequence.getAndIncrement();
        this.finest = logger.isLoggable(Level.FINEST);
    }

    void init(AnyValue conf) {
        final int liveinterval = conf == null ? DEFAILT_LIVEINTERVAL : conf.getIntValue("liveinterval", DEFAILT_LIVEINTERVAL);
        if (liveinterval <= 0) return;
        if (scheduler != null) return;
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, engineid + "-WebSocket-LiveInterval-Thread");
            t.setDaemon(true);
            return t;
        });
        long delay = (liveinterval - System.currentTimeMillis() / 1000 % liveinterval) + index * 5;
        scheduler.scheduleWithFixedDelay(() -> {
            getWebSocketGroups().stream().forEach(x -> x.sendEachPing());
        }, delay, liveinterval, TimeUnit.SECONDS);
        if (finest) logger.finest(this.getClass().getSimpleName() + "(" + engineid + ")" + " start keeplive(delay:" + delay + ", interval:" + liveinterval + "s) scheduler executor");
    }

    void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    void add(WebSocket socket) {  //非线程安全， 在常规场景中无需锁
        WebSocketGroup group = containers.get(socket._groupid);
        if (group == null) {
            group = new WebSocketGroup(socket._groupid);
            containers.putIfAbsent(socket._groupid, group);
            if (node != null) node.connect(socket._groupid);
        }
        group.add(socket);
    }

    void remove(WebSocket socket) { //非线程安全， 在常规场景中无需锁
        final WebSocketGroup group = containers.get(socket._groupid);
        if (group == null) {
            if (node != null) node.disconnect(socket._groupid);
            return;
        }
        group.remove(socket);
        if (group.isEmpty()) {
            containers.remove(socket._groupid);
            if (node != null) node.disconnect(socket._groupid);
        }
    }

    Collection<WebSocketGroup> getWebSocketGroups() {
        return containers.values();
    }

    public WebSocketGroup getWebSocketGroup(Serializable groupid) {
        return containers.get(groupid);
    }

    public boolean existsWebSocketGroup(Serializable groupid) {
        return containers.containsKey(groupid);
    }

    public String getEngineid() {
        return engineid;
    }
}
