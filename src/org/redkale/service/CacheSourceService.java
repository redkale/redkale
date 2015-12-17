/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.convert.json.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class CacheSourceService implements CacheSource, Service {

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<Serializable, CacheEntry> container = new ConcurrentHashMap<>();

    @Override
    public void init(AnyValue conf) {
        final CacheSourceService self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("property");
        String expireHandlerClass = prop == null ? null : prop.getValue("expirehandler");
        if (expireHandlerClass != null) {
            try {
                this.expireHandler = (Consumer<CacheEntry>) Class.forName(expireHandlerClass).newInstance();
            } catch (Exception e) {
                logger.log(Level.SEVERE, self.getClass().getSimpleName() + " new expirehandler class (" + expireHandlerClass + ") instance error", e);
            }
        }
        if (scheduler == null) {
            this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                final Thread t = new Thread(r, self.getClass().getSimpleName() + "-Expirer-Thread");
                t.setDaemon(true);
                return t;
            });
            final List<Serializable> keys = new ArrayList<>();
            scheduler.scheduleWithFixedDelay(() -> {
                keys.clear();
                int now = (int) (System.currentTimeMillis() / 1000);
                container.forEach((k, x) -> {
                    if (x.expireSeconds > 0 && (now > (x.lastAccessed + x.expireSeconds))) {
                        keys.add(x.key);
                    }
                });
                for (Serializable key : keys) {
                    CacheEntry entry = container.remove(key);
                    if (expireHandler != null && entry != null) expireHandler.accept(entry);
                }
            }, 10, 10, TimeUnit.SECONDS);
            logger.finest(self.getClass().getSimpleName() + ":" + self.name() + " start schedule expire executor");
        }
    }

    public void close() {  //给Application 关闭时调用
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public boolean exists(Serializable key) {
        if (key == null) return false;
        return container.containsKey(key);
    }

    @Override
    public <T> T get(Serializable key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null) return null;
        return (T) entry.getValue();
    }

    @Override
    @MultiRun
    public <T> T refreshAndGet(Serializable key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        return (T) entry.getValue();
    }

    @Override
    @MultiRun
    public void refresh(Serializable key) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
    }

    @Override
    @MultiRun
    public <T> void set(Serializable key, T value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(key, value);
            container.putIfAbsent(key, entry);
        } else {
            entry.value = value;
        }
    }

    @Override
    @MultiRun
    public void setExpireSeconds(Serializable key, int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.expireSeconds = expireSeconds;
    }

    @Override
    @MultiRun
    public <T> void set(int expireSeconds, Serializable key, T value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(expireSeconds, key, value);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) entry.expireSeconds = expireSeconds;
            entry.value = value;
        }
    }

    @Override
    @MultiRun
    public void remove(Serializable key) {
        if (key == null) return;
        container.remove(key);
    }

    @Override
    @MultiRun
    public <V> void appendListItem(Serializable key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof List)) {
            List<V> list = new CopyOnWriteArrayList<>();
            entry = new CacheEntry(key, list);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) list = (List) old.value;
            list.add(value);
        } else {
            ((List) entry.getValue()).add(value);
        }
    }

    @Override
    @MultiRun
    public <V> void removeListItem(Serializable key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof List)) return;
        ((List) entry.getValue()).remove(value);
    }

    @Override
    public <V> void appendSetItem(Serializable key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof Set)) {
            Set<V> set = new CopyOnWriteArraySet();
            entry = new CacheEntry(key, set);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) set = (Set) old.value;
            set.add(value);
        } else {
            ((Set) entry.getValue()).add(value);
        }
    }

    @Override
    @MultiRun
    public <V> void removeSetItem(Serializable key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof Set)) return;
        ((Set) entry.getValue()).remove(value);
    }

    public static final class CacheEntry<T> {

        private final int createTime; //创建时间

        private volatile int lastAccessed; //最后刷新时间

        //<=0表示永久保存
        private int expireSeconds;

        private T value;

        private final Serializable key;

        public CacheEntry(Serializable key, T value) {
            this(0, key, value);
        }

        public CacheEntry(int expireSecond, Serializable key, T value) {
            this.expireSeconds = expireSecond;
            this.createTime = (int) (System.currentTimeMillis() / 1000);
            this.lastAccessed = this.createTime;
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public long getCreateTime() {
            return createTime;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public T getValue() {
            return value;
        }

        public Serializable getKey() {
            return key;
        }

    }
}
