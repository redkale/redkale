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

    private static final AtomicInteger sequence = new AtomicInteger();

    private final int index;

    private final String engineid;

    protected final WebSocketNode node;

    private final Map<Serializable, WebSocketGroup> containers = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor scheduler;

    protected final Logger logger;

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

    void add(WebSocket socket) {
        WebSocketGroup group = containers.get(socket._groupid);
        if (group == null) {
            group = new WebSocketGroup(socket._groupid);
            containers.putIfAbsent(socket._groupid, group);
        }
        group.add(socket);
        if (node != null) node.connect(socket._groupid, engineid);
    }

    void remove(WebSocket socket) {
        final WebSocketGroup group = containers.get(socket._groupid);
        if (group == null) {
            if (node != null) node.disconnect(socket._groupid, engineid);
            return;
        }
        group.remove(socket);
        if (group.isEmpty()) {
            containers.remove(socket._groupid);
            if (node != null) node.disconnect(socket._groupid, engineid);
        }
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
