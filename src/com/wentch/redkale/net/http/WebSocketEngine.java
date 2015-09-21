/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import static com.wentch.redkale.net.http.WebSocketPacket.DEFAULT_PING_PACKET;
import static com.wentch.redkale.net.http.WebSocketServlet.DEFAILT_LIVEINTERVAL;
import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class WebSocketEngine {

    private final String engineid;

    private final Map<Serializable, WebSocketGroup> containers = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor scheduler;

    protected final Logger logger;

    protected final boolean finest;

    protected WebSocketEngine(String engineid, Logger logger) {
        this.engineid = engineid;
        this.logger = logger;
        this.finest = logger.isLoggable(Level.FINEST);
    }

    void init(AnyValue conf) {
        final int liveinterval = conf == null ? DEFAILT_LIVEINTERVAL : conf.getIntValue("liveinterval", DEFAILT_LIVEINTERVAL);
        if (liveinterval == 0) return;
        if (scheduler != null) return;
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, engineid + "-WebSocket-LiveInterval-Thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            getWebSocketGroups().stream().forEach(x -> x.sendEach(DEFAULT_PING_PACKET));
        }, (liveinterval - System.currentTimeMillis() / 1000 % liveinterval), liveinterval, TimeUnit.SECONDS);
        if (finest) logger.finest(this.getClass().getSimpleName() + "(" + engineid + ")" + " start keeplive(interval:" + liveinterval + "s) scheduler executor");
    }

    void add(WebSocket socket) {
        WebSocketGroup group = containers.get(socket.groupid);
        if (group == null) {
            group = new WebSocketGroup(socket.groupid);
            containers.put(socket.groupid, group);
        }
        group.add(socket);
    }

    void remove(WebSocket socket) {
        WebSocketGroup group = containers.get(socket.groupid);
        if (group == null) return;
        group.remove(socket);
        if (group.isEmpty()) containers.remove(socket.groupid);
    }

    Collection<WebSocketGroup> getWebSocketGroups() {
        return containers.values();
    }

    public WebSocketGroup getWebSocketGroup(Serializable groupid) {
        return containers.get(groupid);
    }

    void close() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    public String getEngineid() {
        return engineid;
    }
}
