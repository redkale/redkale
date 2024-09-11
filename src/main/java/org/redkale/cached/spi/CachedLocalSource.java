/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.cached.spi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

    // key: name, sub-key: key
    private final ConcurrentHashMap<String, Map<String, CacheItem>> container = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor scheduler;

    @Override
    public void init(AnyValue conf) {
        if (scheduler == null) {
            this.scheduler = Utility.newScheduledExecutor(
                    1, "Redkale-" + CachedLocalSource.class.getSimpleName() + "-Expirer-Thread");
            final List<String> keys = new ArrayList<>();
            int interval = 30;
            scheduler.scheduleWithFixedDelay(
                    () -> {
                        try {
                            container.forEach((n, m) -> {
                                keys.clear();
                                long now = System.currentTimeMillis();
                                m.forEach((k, x) -> {
                                    if (x.isExpired(now)) {
                                        keys.add(k);
                                    }
                                });
                                for (String key : keys) {
                                    m.remove(key);
                                }
                            });
                        } catch (Throwable t) {
                            logger.log(Level.SEVERE, "CachedLocalSource schedule(interval=" + interval + "s) error", t);
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

    public <T> void set(String name, String key, long millis, Type type, T value) {
        // millis > 0 才需要过期设置
        String json = convert.convertTo(type, value);
        container
                .computeIfAbsent(name, n -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> new CacheItem(json))
                .set(json, millis);
    }

    public <T> T get(String name, String key, Type type) {
        Map<String, CacheItem> map = container.get(name);
        CacheItem item = map == null ? null : map.get(key);
        String json = item == null || item.isExpired() ? null : item.getValue();
        return json == null ? null : convert.convertFrom(type, json);
    }

    public long del(String name, String key) {
        Map<String, CacheItem> map = container.get(name);
        return map != null && map.remove(key) != null ? 1 : 0;
    }

    public <T> CompletableFuture<T> getAsync(String name, String key, Type type) {
        return CompletableFuture.completedFuture(get(name, key, type));
    }

    public CompletableFuture<Long> delAsync(String name, String key) {
        return CompletableFuture.completedFuture(del(name, key));
    }

    public <T> CompletableFuture<Void> setAsync(String name, String key, long millis, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(name, key, millis, type, value));
    }

    protected static class CacheItem {

        @Nullable // json格式
        protected String value;

        // 为0表示永久， 大于0表示有过期时间
        private long endMillis;

        public CacheItem(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void set(String value, long millis) {
            this.value = value;
            this.endMillis = millis > 0 ? (System.currentTimeMillis() + millis) : 0;
        }

        @ConvertDisabled
        public boolean isExpired() {
            return endMillis > 0 && System.currentTimeMillis() >= endMillis;
        }

        boolean isExpired(long now) {
            return endMillis > 0 && now >= endMillis;
        }
    }
}
