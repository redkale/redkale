/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.cached.spi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Nullable;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.json.JsonFactory;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.Utility;

/**
 * 本地缓存源
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@AutoLoad(false)
public class CachedLocalSource implements Service {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final JsonConvert convert = JsonFactory.create().skipAllIgnore(true).getConvert();

    // key: name
    private final ConcurrentHashMap<String, CacheMap> container = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor scheduler;

    @Override
    public void init(AnyValue conf) {
        if (scheduler == null) {
            this.scheduler = Utility.newScheduledExecutor(
                    1, "Redkale-" + CachedLocalSource.class.getSimpleName() + "-Expirer-Thread");
            int interval = 15;
            scheduler.scheduleWithFixedDelay(
                    () -> {
                        try {
                            long now = System.currentTimeMillis();
                            container.forEach((n, m) -> m.removeExpired(now));
                        } catch (Throwable t) {
                            logger.log(
                                    Level.SEVERE,
                                    CachedLocalSource.class.getSimpleName() + " schedule(interval=" + interval
                                            + "s) error",
                                    t);
                        }
                    },
                    interval,
                    interval,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public <T> void set(String name, String key, int localLimit, long millis, Type type, T value) {
        // millis > 0 才需要过期设置
        String json = convert.convertTo(type, value);
        container.computeIfAbsent(name, n -> new CacheMap(localLimit)).set(key, json, millis);
    }

    public <T> T get(String name, String key, Type type) {
        CacheMap map = container.get(name);
        CacheItem item = map == null ? null : map.get(key);
        String json = item == null || item.isExpired() ? null : item.getValue();
        return json == null ? null : convert.convertFrom(type, json);
    }

    public long del(String name, String key) {
        CacheMap map = container.get(name);
        return map != null && map.remove(key) != null ? 1 : 0;
    }

    public <T> CompletableFuture<T> getAsync(String name, String key, Type type) {
        return CompletableFuture.completedFuture(get(name, key, type));
    }

    public CompletableFuture<Long> delAsync(String name, String key) {
        return CompletableFuture.completedFuture(del(name, key));
    }

    public <T> CompletableFuture<Void> setAsync(
            String name, String key, int localLimit, long millis, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(name, key, localLimit, millis, type, value));
    }

    public int getSize(String name) {
        CacheMap map = container.get(name);
        return map == null ? -1 : map.size();
    }

    public int updateLimit(String name, int limit) {
        CacheMap map = container.get(name);
        if (map == null) {
            return -1;
        }
        return map.updateLimit(limit);
    }

    protected static class CacheMap {

        protected final ReentrantLock lock = new ReentrantLock();

        protected final ConcurrentHashMap<String, CacheItem> map = new ConcurrentHashMap<>();

        protected volatile int limit;

        public CacheMap(int limit) {
            this.limit = limit;
        }

        public void forEach(BiConsumer<String, CacheItem> action) {
            map.forEach(action);
        }

        public CacheItem remove(String key) {
            return map.remove(key);
        }

        public CacheItem get(String key) {
            return map.get(key);
        }

        public void set(String key, String json, long millis) {
            lock.lock();
            try {
                map.put(key, new CacheItem(key, json, millis));
                checkLimit();
            } finally {
                lock.unlock();
            }
        }

        public int updateLimit(int limit) {
            lock.lock();
            try {
                int old = this.limit;
                this.limit = limit;
                checkLimit();
                return old;
            } finally {
                lock.unlock();
            }
        }

        public void removeExpired(long now) {
            forEach((key, item) -> {
                if (item.isExpired(now)) {
                    // A concurrent refresh replaces the item, so never remove by key alone.
                    map.remove(key, item);
                }
            });
        }

        public int size() {
            return map.size();
        }

        protected void checkLimit() {
            lock.lock();
            try {
                int l = limit;
                if (l > 0 && map.size() > l) {
                    List<CacheItem> items = new ArrayList<>(map.values());
                    Collections.sort(items);
                    int count = items.size() - l;
                    for (int i = 0; i < count && map.size() > l; i++) {
                        CacheItem item = items.get(i);
                        map.remove(item.getKey(), item);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    protected static class CacheItem implements Comparable<CacheItem> {

        private static final AtomicLong SEQUENCE = new AtomicLong();

        private final String key;

        @Nullable // json格式
        protected final String value;

        // 为0表示永久， 大于0表示有过期时间
        protected final long endMillis;

        private final long createTime;

        private final long sequence = SEQUENCE.incrementAndGet();

        public CacheItem(String key, String value) {
            this(key, value, 0);
        }

        public CacheItem(String key, String value, long millis) {
            this.key = key;
            this.value = value;
            this.createTime = System.currentTimeMillis();
            this.endMillis = millis > 0 ? (this.createTime + millis) : 0;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public long getCreateTime() {
            return createTime;
        }

        @ConvertDisabled
        public boolean isExpired() {
            return endMillis > 0 && System.currentTimeMillis() >= endMillis;
        }

        boolean isExpired(long now) {
            return endMillis > 0 && now >= endMillis;
        }

        @Override
        public int compareTo(CacheItem o) {
            long t1 = this.createTime;
            long t2 = o == null ? 0 : o.createTime;
            return t1 == t2 ? Long.compare(this.sequence, o == null ? 0 : o.sequence) : Long.compare(t1, t2);
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
