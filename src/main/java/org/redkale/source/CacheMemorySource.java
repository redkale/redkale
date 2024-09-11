/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceType;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.inject.ResourceEvent;
import org.redkale.service.Local;
import org.redkale.util.*;

/**
 * CacheSource的默认实现--内存缓存 注意: url 需要指定为 memory:cachesource
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(CacheSource.class)
public final class CacheMemorySource extends AbstractCacheSource {

    @Resource(required = false)
    private JsonConvert defaultConvert = JsonConvert.root();

    @Resource(name = Resource.PARENT_NAME + "_convert", required = false)
    private JsonConvert convert;

    private String name;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final ConcurrentHashMap<String, CacheEntry> container = new ConcurrentHashMap<>();

    private final ReentrantLock containerLock = new ReentrantLock();

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitContainer = new ConcurrentHashMap<>();

    private final ReentrantLock rateLimitContainerLock = new ReentrantLock();

    // key: topic
    private final Map<String, Set<CacheEventListener<byte[]>>> pubsubListeners = new ConcurrentHashMap<>();

    private ExecutorService subExecutor;

    private final ReentrantLock subExecutorLock = new ReentrantLock();

    public CacheMemorySource(String resourceName) {
        this.name = resourceName;
    }

    @Override
    public final String getType() {
        return "memory";
    }

    @Override
    @ResourceChanged
    public void onResourceChange(ResourceEvent[] events) {
        // do nothing
    }

    protected ExecutorService subExecutor() {
        ExecutorService executor = subExecutor;
        if (executor != null) {
            return executor;
        }
        subExecutorLock.lock();
        try {
            if (subExecutor == null) {
                String threadNameFormat = "CacheSource-" + resourceName() + "-SubThread-%s";
                Function<String, ExecutorService> func = Utility.virtualExecutorFunction();
                final AtomicInteger counter = new AtomicInteger();
                subExecutor = func == null
                        ? Executors.newFixedThreadPool(Utility.cpus(), r -> {
                            Thread t = new Thread(r);
                            t.setDaemon(true);
                            int c = counter.incrementAndGet();
                            t.setName(String.format(
                                    threadNameFormat, "Virtual-" + (c < 10 ? ("00" + c) : (c < 100 ? ("0" + c) : c))));
                            return t;
                        })
                        : func.apply(threadNameFormat);
            }
            executor = subExecutor;
        } finally {
            subExecutorLock.unlock();
        }
        return executor;
    }

    public static boolean acceptsConf(AnyValue config) {
        String nodes = config.getValue(CACHE_SOURCE_NODES);
        return nodes != null && nodes.startsWith("memory:");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=memory, name='" + resourceName() + "', hash="
                + Objects.hashCode(this) + "}";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(AnyValue conf) {
        if (this.convert == null) {
            this.convert = this.defaultConvert;
        }
        if (this.convert == null) {
            this.convert = JsonConvert.root();
        }
        final CacheMemorySource self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("properties");
        String expireHandlerClass = prop == null ? null : prop.getValue("expirehandler");
        if (expireHandlerClass != null) {
            try {
                Class clazz = Thread.currentThread().getContextClassLoader().loadClass(expireHandlerClass);
                this.expireHandler =
                        (Consumer<CacheEntry>) clazz.getDeclaredConstructor().newInstance();
                RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, expireHandlerClass);
            } catch (Throwable e) {
                logger.log(
                        Level.SEVERE,
                        self.getClass().getSimpleName() + " new expirehandler class (" + expireHandlerClass
                                + ") instance error",
                        e);
            }
        }
        if (scheduler == null) {
            this.scheduler = Utility.newScheduledExecutor(
                    1, "Redkale-" + CacheMemorySource.class.getSimpleName() + "-" + resourceName() + "-Expirer-Thread");
            final List<String> keys = new ArrayList<>();
            int interval = 30;
            scheduler.scheduleWithFixedDelay(
                    () -> {
                        try {
                            keys.clear();
                            long now = System.currentTimeMillis();
                            container.forEach((k, x) -> {
                                if (x.isExpired(now)) {
                                    keys.add(x.key);
                                }
                            });
                            for (String key : keys) {
                                CacheEntry entry = container.remove(key);
                                if (expireHandler != null && entry != null) {
                                    expireHandler.accept(entry);
                                }
                            }
                            long now2 = System.currentTimeMillis();
                            rateLimitContainer.forEach((k, x) -> {
                                if (x.isExpired(now2)) {
                                    keys.add(x.key);
                                }
                            });
                            for (String key : keys) {
                                rateLimitContainer.remove(key);
                            }
                        } catch (Throwable t) {
                            logger.log(Level.SEVERE, "CacheMemorySource schedule(interval=" + interval + "s) error", t);
                        }
                    },
                    interval,
                    interval,
                    TimeUnit.SECONDS);
            logger.info(
                    self.getClass().getSimpleName() + ":" + self.resourceName() + " start schedule expire executor");
        }
    }

    @Override
    public void close() throws Exception { // 给Application 关闭时调用
        destroy(null);
    }

    @Override
    public String resourceName() {
        return name;
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (subExecutor != null) {
            subExecutor.shutdown();
            subExecutor = null;
        }
    }

    protected <U> CompletableFuture<U> supplyFuture(Supplier<U> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    protected CompletableFuture<Void> runFuture(Runnable runner) {
        try {
            runner.run();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    protected <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }

    protected <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    protected CompletableFuture<Void> runAsync(Runnable runner, Executor executor) {
        return CompletableFuture.runAsync(runner, executor);
    }

    @Override
    public CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(true);
    }

    // ------------------------ 订阅发布 SUB/PUB ------------------------
    @Override
    public CompletableFuture<List<String>> pubsubChannelsAsync(@Nullable String pattern) {
        Predicate<String> predicate =
                Utility.isEmpty(pattern) ? t -> true : Pattern.compile(pattern).asPredicate();
        return CompletableFuture.completedFuture(
                pubsubListeners.keySet().stream().filter(predicate).collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Void> subscribeAsync(CacheEventListener<byte[]> listener, String... topics) {
        Objects.requireNonNull(listener);
        if (Utility.isEmpty(topics)) {
            throw new RedkaleException("topics is empty");
        }
        for (String topic : topics) {
            pubsubListeners
                    .computeIfAbsent(topic, t -> new CopyOnWriteArraySet<>())
                    .add(listener);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> unsubscribeAsync(CacheEventListener listener, String... topics) {
        int c = 0;
        if (listener == null) {
            if (Utility.isEmpty(topics)) { // 清空所有订阅者
                for (Set<CacheEventListener<byte[]>> listeners : pubsubListeners.values()) {
                    c += listeners != null ? listeners.size() : 0;
                }
                pubsubListeners.clear();
            } else {
                for (String topic : topics) { // 清空指定topic的订阅者
                    Set<CacheEventListener<byte[]>> listeners = pubsubListeners.remove(topic);
                    c += listeners != null ? listeners.size() : 0;
                }
            }
        } else {
            if (Utility.isEmpty(topics)) {
                for (Set<CacheEventListener<byte[]>> listeners : pubsubListeners.values()) {
                    c += listeners != null && listeners.remove(listener) ? 1 : 0;
                }
            } else {
                for (String topic : topics) {
                    Set<CacheEventListener<byte[]>> listeners = pubsubListeners.get(topic);
                    c += listeners != null && listeners.remove(listener) ? 1 : 0;
                }
            }
        }
        return CompletableFuture.completedFuture(c);
    }

    @Override
    public CompletableFuture<Integer> publishAsync(final String topic, final byte[] message) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(message);
        Set<CacheEventListener<byte[]>> listeners = pubsubListeners.get(topic);
        if (listeners == null || listeners.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Executor executor = subExecutor();
        listeners.forEach(listener -> executor.execute(() -> {
            try {
                listener.onMessage(topic, message);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "CacheSource subscribe message error, topic: " + topic, t);
            }
        }));
        return CompletableFuture.completedFuture(1);
    }

    // ------------------------ 字符串 String ------------------------
    @Override
    public void mset(Serializable... keyVals) {
        if (keyVals.length % 2 != 0) {
            throw new SourceException("key value must be paired");
        }
        for (int i = 0; i < keyVals.length; i += 2) {
            String key = keyVals[i].toString();
            Object val = keyVals[i + 1];
            set0(key, 0, null, null, val);
        }
    }

    @Override
    public void mset(Map map) {
        map.forEach((key, val) -> set0(key.toString(), 0, null, null, val));
    }

    @Override
    public CompletableFuture<Void> msetAsync(Serializable... keyVals) {
        return runFuture(() -> mset(keyVals));
    }

    @Override
    public CompletableFuture<Boolean> msetnxAsync(Serializable... keyVals) {
        return supplyFuture(() -> msetnx(keyVals));
    }

    @Override
    public CompletableFuture<Void> msetAsync(Map map) {
        return runFuture(() -> mset(map));
    }

    @Override
    public CompletableFuture<Boolean> msetnxAsync(Map map) {
        return supplyFuture(() -> msetnx(map));
    }

    @Override
    public <T> void set(String key, Convert convert, Type type, T value) {
        set0(key, 0, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value) {
        return runFuture(() -> set(key, convert, type, value));
    }

    @Override
    public <T> boolean setnx(String key, Convert convert, Type type, T value) {
        return setnxex(key, 0, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxAsync(String key, Convert convert, Type type, T value) {
        return supplyFuture(() -> setnx(key, convert, type, value));
    }

    @Override
    public <T> boolean setnxex(String key, int expireSeconds, Convert convert, Type type, T value) {
        return setnxpx(key, expireSeconds * 1000L, convert, type, value);
    }

    @Override
    public <T> boolean setnxpx(String key, long milliSeconds, Convert convert, Type type, T value) {
        CacheEntry entry = find(key);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.OBJECT);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.OBJECT, key);
                    container.put(key, entry);
                    entry.setObjectValue(convert == null ? this.convert : convert, type, value);
                    entry.milliSeconds(milliSeconds);
                    entry.lastAccessed = System.currentTimeMillis();
                    return true;
                }
                return false;
            } finally {
                containerLock.unlock();
            }
        }
        return false;
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxexAsync(
            String key, int expireSeconds, Convert convert, Type type, T value) {
        return supplyFuture(() -> setnxex(key, expireSeconds, convert, type, value));
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxpxAsync(
            String key, long milliSeconds, Convert convert, Type type, T value) {
        return supplyFuture(() -> setnxpx(key, milliSeconds, convert, type, value));
    }

    @Override
    public <T> T getSet(String key, Convert convert, Type type, T value) {
        CacheEntry entry = find(key, CacheEntryType.OBJECT);
        T old = entry == null ? null : (T) entry.getObjectValue(convert == null ? this.convert : convert, type);
        set0(key, 0, convert, type, value);
        return old;
    }

    @Override
    public <T> CompletableFuture<T> getSetAsync(String key, Convert convert, Type type, T value) {
        return supplyFuture(() -> getSet(key, convert, type, value));
    }

    @Override
    public <T> T getDel(String key, Type type) {
        CacheEntry entry = find(key, CacheEntryType.OBJECT);
        if (entry == null) {
            return null;
        }
        containerLock.lock();
        try {
            container.remove(key);
        } finally {
            containerLock.unlock();
        }
        return entry.getObjectValue(convert, type);
    }

    @Override
    public <T> CompletableFuture<T> getDelAsync(String key, Type type) {
        return supplyFuture(() -> getDel(key, type));
    }

    private void set0(String key, long milliSeconds, Convert convert, Type type, Object value) {
        CacheEntry entry = find(key, CacheEntryType.OBJECT);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.OBJECT);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.OBJECT, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.setObjectValue(convert == null ? this.convert : convert, type, value);
            entry.milliSeconds(milliSeconds);
            entry.lastAccessed = System.currentTimeMillis();
        } finally {
            entry.unlock();
        }
    }

    @Override
    public boolean msetnx(Serializable... keyVals) {
        if (keyVals.length % 2 != 0) {
            throw new SourceException("key value must be paired");
        }
        return msetnx(Utility.ofMap((Object[]) keyVals));
    }

    @Override
    public boolean msetnx(Map map) {
        containerLock.lock();
        try {
            for (Object key : map.keySet()) {
                if (find(key.toString(), CacheEntryType.OBJECT) != null) {
                    return false;
                }
            }
            for (Map.Entry<String, Object> en : (Set<Map.Entry<String, Object>>) map.entrySet()) {
                CacheEntry entry = new CacheEntry(CacheEntryType.OBJECT, en.getKey());
                container.put(en.getKey(), entry);
                entry.setObjectValue(this.convert, null, en.getValue());
                entry.lastAccessed = System.currentTimeMillis();
            }
            return true;
        } finally {
            containerLock.unlock();
        }
    }

    @Override
    public <T> void setex(String key, int expireSeconds, Convert convert, Type type, T value) {
        set0(key, expireSeconds * 1000L, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return runFuture(() -> setex(key, expireSeconds, convert, type, value));
    }

    @Override
    public <T> void psetex(String key, long milliSeconds, Convert convert, Type type, T value) {
        set0(key, milliSeconds, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Void> psetexAsync(String key, long milliSeconds, Convert convert, Type type, T value) {
        return runFuture(() -> psetex(key, milliSeconds, convert, type, value));
    }

    @Override
    public void expire(String key, int expireSeconds) {
        pexpire(key, expireSeconds * 1000L);
    }

    @Override
    public void pexpire(String key, long milliSeconds) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return;
        }
        entry.lock();
        try {
            entry.milliSeconds(milliSeconds);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> expireAsync(String key, int expireSeconds) {
        return runFuture(() -> expire(key, expireSeconds));
    }

    @Override
    public CompletableFuture<Void> pexpireAsync(String key, long milliSeconds) {
        return runFuture(() -> pexpire(key, milliSeconds));
    }

    @Override
    public long ttl(String key) {
        long v = pttl(key);
        return v > 0 ? v / 1000 : v;
    }

    @Override
    public long pttl(String key) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return -2;
        }
        if (entry.expireMills < 1) {
            return -1;
        }
        return entry.initTime + entry.expireMills - System.currentTimeMillis();
    }

    @Override
    public CompletableFuture<Long> ttlAsync(String key) {
        return supplyFuture(() -> ttl(key));
    }

    @Override
    public CompletableFuture<Long> pttlAsync(String key) {
        return supplyFuture(() -> pttl(key));
    }

    @Override
    public void expireAt(String key, long secondsTime) {
        pexpireAt(key, secondsTime * 1000);
    }

    @Override
    public void pexpireAt(String key, long milliTime) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return;
        }
        if (milliTime > 0) {
            entry.lock();
            try {
                entry.endTime = milliTime;
            } finally {
                entry.unlock();
            }
        }
    }

    @Override
    public CompletableFuture<Void> pexpireAtAsync(String key, long milliTime) {
        return runFuture(() -> pexpireAt(key, milliTime));
    }

    @Override
    public CompletableFuture<Void> expireAtAsync(String key, long secondsTime) {
        return runFuture(() -> expireAt(key, secondsTime));
    }

    @Override
    public long expireTime(String key) {
        long v = pexpireTime(key);
        return v > 0 ? v / 1000 : v;
    }

    @Override
    public long pexpireTime(String key) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return -2;
        }
        if (entry.endTime < 1) {
            return -1;
        }
        return entry.endTime;
    }

    @Override
    public CompletableFuture<Long> expireTimeAsync(String key) {
        return supplyFuture(() -> expireTime(key));
    }

    @Override
    public CompletableFuture<Long> pexpireTimeAsync(String key) {
        return supplyFuture(() -> pexpireTime(key));
    }

    @Override
    public boolean persist(final String key) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return false;
        }
        entry.lock();
        try {
            if (entry.expireMills > 0) {
                entry.expireMills = 0;
                return true;
            } else {
                return false;
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> persistAsync(final String key) {
        return supplyFuture(() -> persist(key));
    }

    @Override
    public boolean rename(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) {
            return false;
        }
        containerLock.lock();
        try {
            CacheEntry oldEntry = find(oldKey);
            if (oldEntry == null) {
                return false;
            }
            oldEntry.key = newKey;
            container.put(newKey, oldEntry);
            container.remove(oldKey);
            return true;
        } finally {
            containerLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey) {
        return supplyFuture(() -> rename(oldKey, newKey));
    }

    @Override
    public boolean renamenx(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) {
            return false;
        }
        containerLock.lock();
        try {
            CacheEntry newEntry = find(newKey);
            if (newEntry != null) {
                return false;
            }
            CacheEntry oldEntry = find(oldKey);
            if (oldEntry == null) {
                return false;
            }
            oldEntry.key = newKey;
            container.put(newKey, oldEntry);
            container.remove(oldKey);
            return true;
        } finally {
            containerLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey) {
        return supplyFuture(() -> renamenx(oldKey, newKey));
    }

    @Override
    public long del(final String... keys) {
        if (keys == null) {
            return 0L;
        }
        long count = 0;
        containerLock.lock();
        try {
            for (String key : keys) {
                count += container.remove(key) == null ? 0 : 1;
            }
        } finally {
            containerLock.unlock();
        }
        return count;
    }

    @Override
    public CompletableFuture<Long> delAsync(final String... keys) {
        return supplyFuture(() -> del(keys));
    }

    @Override
    public long delex(String key, String expectedValue) {
        if (key == null) {
            return 0L;
        }
        containerLock.lock();
        try {
            CacheEntry entry = find(key);
            if (entry == null) {
                return 0;
            } else {
                entry.lock();
                try {
                    if (Objects.equals(expectedValue, entry.getObjectValue(convert, String.class))) {
                        return container.remove(key) == null ? 0 : 1;
                    } else {
                        return 0;
                    }
                } finally {
                    entry.unlock();
                }
            }
        } finally {
            containerLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Long> delexAsync(String key, String expectedValue) {
        return supplyFuture(() -> delex(key, expectedValue));
    }

    /**
     * 令牌桶算法限流， 返回负数表示无令牌， 其他为有令牌
     *
     * <pre>
     * 每秒限制请求1次:     rate:1,     capacity:1,     requested:1
     * 每秒限制请求10次:    rate:10,    capacity:10,    requested:1
     * 每分钟限制请求1次:   rate:1,     capacity:60,    requested:60
     * 每分钟限制请求10次:  rate:1,     capacity:60,    requested:6
     * 每小时限制请求1次:   rate:1,     capacity:3600,  requested:3600
     * 每小时限制请求10次:  rate:1,     capacity:3600,  requested:360
     * </pre>
     *
     * @param key 限流的键
     * @param rate 令牌桶每秒填充平均速率
     * @param capacity 令牌桶总容量
     * @param requested 需要的令牌数
     * @return 可用令牌数
     */
    @Override
    public long rateLimit(final String key, final long rate, final long capacity, final long requested) {
        if (key == null) {
            return 0L;
        }
        if (capacity < rate || capacity < requested || rate <= 0 || requested < 0) {
            throw new IllegalArgumentException("rate=" + rate + ", capacity=" + capacity + ", requested=" + requested);
        }
        RateLimitEntry entry = null;
        rateLimitContainerLock.lock();
        try {
            entry = rateLimitContainer.get(key);
            if (entry == null) {
                long newTokens = capacity - requested;
                entry = new RateLimitEntry(key, newTokens);
                rateLimitContainer.put(key, entry);
                return newTokens;
            }
        } finally {
            rateLimitContainerLock.unlock();
        }
        entry.lock();
        try {
            long now = System.currentTimeMillis();
            long ttl = capacity / rate * 2 * 1000;
            if (entry.isExpired()) {
                entry.milliSeconds(ttl).tokens = capacity;
            }
            long delta = Math.max(0, (now - entry.timestamp) / 1000);
            long filledTokens = Math.min(capacity, entry.tokens + (delta * rate));
            boolean allowed = filledTokens >= requested;
            long newTokens = filledTokens;
            if (allowed) {
                newTokens = filledTokens - requested;
            }
            if (ttl > 0) {
                entry.milliSeconds(ttl);
                entry.tokens = newTokens;
                entry.timestamp = now;
            }
            return allowed ? newTokens : (filledTokens - requested);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Long> rateLimitAsync(String key, long rate, long capacity, long requested) {
        return supplyFuture(() -> rateLimit(key, rate, capacity, requested));
    }

    @Override
    public long incr(final String key) {
        return incrby(key, 1);
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return supplyFuture(() -> incr(key));
    }

    @Override
    public long incrby(final String key, long num) {
        CacheEntry entry = find(key);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.ATOMIC);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.ATOMIC, key);
                    entry.objectValue = new AtomicLong();
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            if (entry.cacheType != CacheEntryType.ATOMIC) {
                entry.objectValue = new AtomicLong(Long.parseLong(entry.getObjectValue(convert, String.class)));
                entry.cacheType = CacheEntryType.ATOMIC;
            }
            return ((AtomicLong) entry.objectValue).addAndGet(num);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Long> incrbyAsync(final String key, long num) {
        return supplyFuture(() -> incrby(key, num));
    }

    @Override
    public double incrbyFloat(final String key, double num) {
        CacheEntry entry = find(key, CacheEntryType.DOUBLE);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.DOUBLE);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.DOUBLE, key);
                    entry.objectValue = new AtomicLong();
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            if (entry.cacheType != CacheEntryType.DOUBLE) {
                entry.objectValue = new AtomicLong(Long.parseLong(entry.getObjectValue(convert, String.class)));
                entry.cacheType = CacheEntryType.DOUBLE;
            }
            Long v = ((AtomicLong) entry.objectValue).addAndGet(Double.doubleToLongBits(num));
            return Double.longBitsToDouble(v.longValue());
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Double> incrbyFloatAsync(final String key, double num) {
        return supplyFuture(() -> incrbyFloat(key, num));
    }

    @Override
    public long decr(final String key) {
        return incrby(key, -1);
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key) {
        return supplyFuture(() -> decr(key));
    }

    @Override
    public long decrby(final String key, long num) {
        return incrby(key, -num);
    }

    @Override
    public CompletableFuture<Long> decrbyAsync(final String key, long num) {
        return supplyFuture(() -> decrby(key, num));
    }

    @Override
    public <T> List<T> mget(final Type componentType, final String... keys) {
        List<T> list = new ArrayList<>();
        for (String key : keys) {
            list.add(get0(key, 0, null, componentType));
        }
        return list;
    }

    @Override
    public <T> CompletableFuture<List<T>> mgetAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> mget(componentType, keys));
    }

    // ----------- hxxx --------------
    @Override
    public boolean exists(String key) {
        return find(key) != null;
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String key) {
        return supplyFuture(() -> exists(key));
    }

    @Override
    public <T> T get(final String key, final Type type) {
        return get0(key, 0, null, type);
    }

    @Override
    public <T> CompletableFuture<T> getAsync(final String key, final Type type) {
        return supplyFuture(() -> get(key, type));
    }

    @Override
    public <T> T getex(final String key, final int expireSeconds, final Type type) {
        return get0(key, expireSeconds, null, type);
    }

    @Override
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type) {
        return supplyFuture(() -> getex(key, expireSeconds, type));
    }

    private <T> T get0(final String key, final int expireSeconds, final Convert convert, final Type type) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return null;
        }
        if (expireSeconds > 0) {
            entry.milliSeconds(expireSeconds * 1000L);
        }
        final Convert c = convert == null ? this.convert : convert;
        // OBJECT, ATOMIC, DOUBLE, SSET, ZSET, LIST, MAP;
        switch (entry.cacheType) {
            case ATOMIC:
                return CacheEntry.serialToObj(c, type, (AtomicLong) entry.objectValue);
            case DOUBLE:
                return CacheEntry.serialToObj(
                        c, type, Double.longBitsToDouble(((AtomicLong) entry.objectValue).longValue()));
            case SSET:
                return (T) entry.ssetValue.stream()
                        .map(v -> CacheEntry.serialToObj(c, type, v))
                        .collect(Collectors.toSet());
            case ZSET:
                return (T) entry.zsetValue.stream()
                        .map(v -> new CacheScoredValue(v))
                        .collect(Collectors.toSet());
            case LIST:
                return (T) entry.listValue.stream()
                        .map(v -> CacheEntry.serialToObj(c, type, v))
                        .collect(Collectors.toList());
            case MAP:
                LinkedHashMap<String, Object> map = new LinkedHashMap();
                entry.mapValue.forEach((k, v) -> map.put(k, CacheEntry.serialToObj(c, type, v)));
                return (T) map;
            default:
                return entry.getObjectValue(c, type);
        }
    }

    // ------------------------ 哈希表 Hash ------------------------
    @Override
    public long hdel(final String key, String... fields) {
        long count = 0;
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return 0L;
        }
        Map map = entry.mapValue;
        entry.lock();
        try {
            for (String field : fields) {
                if (map.remove(field) != null) {
                    count++;
                }
            }
        } finally {
            entry.unlock();
        }
        return count;
    }

    @Override
    public CompletableFuture<Long> hdelAsync(final String key, String... fields) {
        return supplyFuture(() -> hdel(key, fields));
    }

    @Override
    public List<String> hkeys(final String key) {
        List<String> list = new ArrayList<>();
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return list;
        }
        list.addAll(entry.mapValue.keySet());
        return list;
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return supplyFuture(() -> hkeys(key));
    }

    @Override
    public long hlen(final String key) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        return entry == null ? 0L : (long) entry.mapValue.keySet().size();
    }

    @Override
    public CompletableFuture<Long> hlenAsync(final String key) {
        return supplyFuture(() -> hlen(key));
    }

    @Override
    public long hincr(final String key, String field) {
        return hincrby(key, field, 1);
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return supplyFuture(() -> hincr(key, field));
    }

    @Override
    public long hincrby(final String key, String field, long num) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.MAP);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.MAP, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            Map map = entry.mapValue;
            Serializable val = (Serializable) map.computeIfAbsent(field, f -> new AtomicLong());
            if (!(val instanceof AtomicLong)) {
                val = CacheEntry.objToSerial(convert, AtomicLong.class, val);
                map.put(field, val);
            }
            return ((AtomicLong) val).addAndGet(num);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Long> hincrbyAsync(final String key, String field, long num) {
        return supplyFuture(() -> hincrby(key, field, num));
    }

    @Override
    public double hincrbyFloat(final String key, String field, double num) {
        return Double.longBitsToDouble(hincrby(key, field, Double.doubleToLongBits(num)));
    }

    @Override
    public CompletableFuture<Double> hincrbyFloatAsync(final String key, String field, double num) {
        return supplyFuture(() -> hincrbyFloat(key, field, num));
    }

    @Override
    public long hdecr(final String key, String field) {
        return hincrby(key, field, -1);
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field) {
        return supplyFuture(() -> hdecr(key, field));
    }

    @Override
    public long hdecrby(final String key, String field, long num) {
        return hincrby(key, field, -num);
    }

    @Override
    public CompletableFuture<Long> hdecrbyAsync(final String key, String field, long num) {
        return supplyFuture(() -> hdecrby(key, field, num));
    }

    @Override
    public boolean hexists(final String key, String field) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        return entry != null && entry.mapValue.contains(field);
    }

    @Override
    public CompletableFuture<Boolean> hexistsAsync(final String key, String field) {
        return supplyFuture(() -> hexists(key, field));
    }

    // 需要给CacheFactory使用
    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value) {
        hset0(key, field, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(
            final String key, final String field, final Convert convert, final Type type, final T value) {
        return runFuture(() -> hset(key, field, type, value));
    }

    @Override
    public <T> boolean hsetnx(
            final String key, final String field, final Convert convert, final Type type, final T value) {
        if (value == null) {
            return false;
        }
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.MAP);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.MAP, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            boolean rs =
                    entry.setMapValueIfAbsent(field, convert == null ? this.convert : convert, type, value) == null;
            entry.lastAccessed = System.currentTimeMillis();
            return rs;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Boolean> hsetnxAsync(
            final String key, final String field, final Convert convert, final Type type, final T value) {
        return supplyFuture(() -> hsetnx(key, field, convert, type, value));
    }

    @Override
    public void hmset(final String key, final Serializable... values) {
        for (int i = 0; i < values.length; i += 2) {
            hset0(key, (String) values[i], null, null, values[i + 1]);
        }
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        return runFuture(() -> hmset(key, values));
    }

    @Override
    public void hmset(final String key, final Map map) {
        map.forEach((k, v) -> hset0(key, (String) k, null, null, v));
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Map map) {
        return runFuture(() -> hmset(key, map));
    }

    @Override
    public <T> List<T> hmget(final String key, final Type type, final String... fields) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return null;
        }
        List<T> rs = new ArrayList<>(fields.length);
        for (String field : fields) {
            rs.add(entry.getMapValue(field, convert, type));
        }
        return rs;
    }

    @Override
    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields) {
        return supplyFuture(() -> hmget(key, type, fields));
    }

    @Override
    public <T> Map<String, T> hgetall(final String key, final Type type) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return new LinkedHashMap();
        } else {
            Map map = new LinkedHashMap();
            entry.mapValue.forEach((k, v) -> map.put(k, CacheEntry.serialToObj(convert, type, v)));
            return map;
        }
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hgetallAsync(final String key, final Type type) {
        return supplyFuture(() -> hgetall(key, type));
    }

    @Override
    public <T> List<T> hvals(final String key, final Type type) {
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return new ArrayList();
        } else {
            Stream<T> stream = entry.mapValue.values().stream().map(v -> CacheEntry.serialToObj(convert, type, v));
            return new ArrayList(stream.collect(Collectors.toList()));
        }
    }

    @Override
    public <T> CompletableFuture<List<T>> hvalsAsync(final String key, final Type type) {
        return supplyFuture(() -> hvals(key, type));
    }

    @Override
    public <T> Map<String, T> hscan(final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        if (key == null) {
            return new HashMap();
        }
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return new HashMap();
        }
        if (Utility.isEmpty(pattern)) {
            Set<Map.Entry<String, Serializable>> set = entry.mapValue.entrySet();
            return set.stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, en -> CacheEntry.serialToObj(convert, type, en.getValue())));
        } else {
            Predicate<String> regex =
                    Pattern.compile(pattern.replace("*", ".*")).asPredicate();
            Set<Map.Entry<String, Serializable>> set = entry.mapValue.entrySet();
            return set.stream()
                    .filter(en -> regex.test(en.getKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, en -> CacheEntry.serialToObj(convert, type, en.getValue())));
        }
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hscanAsync(
            final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> hscan(key, type, cursor, limit, pattern));
    }

    // 需要给CacheFactory使用
    @Override
    public <T> T hget(final String key, final String field, final Type type) {
        if (key == null || field == null) {
            return null;
        }
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return null;
        }
        return entry.getMapValue(field, convert, type);
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return supplyFuture(() -> hget(key, field, type));
    }

    @Override
    public long hstrlen(final String key, final String field) {
        if (key == null || field == null) {
            return 0L;
        }
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            return 0L;
        }
        String obj = entry.getMapValue(field, convert, String.class);
        return obj == null ? 0L : (long) obj.length();
    }

    @Override
    public CompletableFuture<Long> hstrlenAsync(final String key, final String field) {
        return supplyFuture(() -> hstrlen(key, field));
    }

    private void hset0(String key, String field, Convert convert, Type type, Object value) {
        if (value == null) {
            return;
        }
        CacheEntry entry = find(key, CacheEntryType.MAP);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.MAP);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.MAP, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.setMapValue(field, convert == null ? this.convert : convert, type, value);
            entry.lastAccessed = System.currentTimeMillis();
        } finally {
            entry.unlock();
        }
    }

    // ------------------------ 列表 List ------------------------
    @Override
    public <T> List<T> lrange(final String key, final Type componentType, int start, int stop) {
        return get(key, componentType);
    }

    @Override
    public <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType, int start, int stop) {
        return supplyFuture(() -> lrange(key, componentType, start, stop));
    }

    @Override
    public <T> Map<String, List<T>> lranges(final Type componentType, final String... keys) {
        Map<String, List<T>> map = new HashMap<>();
        for (String key : keys) {
            List<T> s = (List<T>) get(key, componentType);
            if (s != null) {
                map.put(key, s);
            }
        }
        return map;
    }

    @Override
    public <T> CompletableFuture<Map<String, List<T>>> lrangesAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> lranges(componentType, keys));
    }

    @Override
    public long llen(final String key) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        return entry == null ? 0L : (long) entry.listValue.size();
    }

    @Override
    public CompletableFuture<Long> llenAsync(final String key) {
        return supplyFuture(() -> llen(key));
    }

    @Override
    public <T> T lindex(String key, Type componentType, int index) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return null;
        }
        List<Serializable> list = new ArrayList(entry.listValue);
        int pos = index >= 0 ? index : list.size() + index;
        return pos >= list.size() ? null : CacheEntry.serialToObj(convert, componentType, list.get(pos));
    }

    @Override
    public <T> CompletableFuture<T> lindexAsync(String key, Type componentType, int index) {
        return supplyFuture(() -> lindex(key, componentType, index));
    }

    @Override
    public <T> long linsertBefore(String key, Type componentType, T pivot, T value) {
        return linsert(key, componentType, true, pivot, value);
    }

    @Override
    public <T> CompletableFuture<Long> linsertBeforeAsync(String key, Type componentType, T pivot, T value) {
        return supplyFuture(() -> linsertBefore(key, componentType, pivot, value));
    }

    @Override
    public <T> long linsertAfter(String key, Type componentType, T pivot, T value) {
        return linsert(key, componentType, false, pivot, value);
    }

    @Override
    public <T> CompletableFuture<Long> linsertAfterAsync(String key, Type componentType, T pivot, T value) {
        return supplyFuture(() -> linsertAfter(key, componentType, pivot, value));
    }

    protected <T> long linsert(String key, Type componentType, boolean before, T pivot, T value) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return 0L;
        }
        entry.lock();
        Serializable val = CacheEntry.objToSerial(convert, componentType, value);
        try {
            List<Serializable> list = new ArrayList<>(entry.listValue);
            int pos = list.indexOf(pivot);
            if (pos < 0) {
                return -1L;
            }
            List<Serializable> newList = new ArrayList<>();
            if (before) {
                if (pos == 0) {
                    newList.add(val);
                    newList.addAll(list);
                } else {
                    newList.addAll(list.subList(0, pos));
                    newList.add(val);
                    newList.addAll(list.subList(pos, list.size()));
                }
            } else {
                if (pos == list.size() - 1) {
                    newList.addAll(list);
                    newList.add(val);
                } else {
                    newList.addAll(list.subList(0, pos + 1));
                    newList.add(val);
                    newList.addAll(list.subList(pos + 1, list.size()));
                }
            }
            entry.listValue.clear();
            entry.listValue.addAll(newList);
            return 1L;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> void lpush(final String key, final Type componentType, T... values) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.LIST);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.LIST, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            for (T val : values) {
                entry.listValue.addFirst(CacheEntry.objToSerial(convert, componentType, val));
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> lpushAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> lpush(key, componentType, values));
    }

    @Override
    public <T> void lpushx(final String key, final Type componentType, T... values) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return;
        }
        entry.lock();
        try {
            ConcurrentLinkedDeque list = entry.listValue;
            for (T val : values) {
                list.addFirst(CacheEntry.objToSerial(convert, componentType, val));
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> lpushxAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> lpushx(key, componentType, values));
    }

    @Override
    public <T> T lpop(final String key, final Type componentType) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return null;
        }
        entry.lock();
        try {
            return CacheEntry.serialToObj(convert, componentType, entry.listValue.pollFirst());
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<T> lpopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> lpop(key, componentType));
    }

    @Override
    public void ltrim(final String key, int start, int stop) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return;
        }
        entry.lock();
        try {
            ConcurrentLinkedDeque list = entry.listValue;
            Iterator it = list.iterator();
            int index = -1;
            int end = stop >= 0 ? stop : list.size() + stop;
            while (it.hasNext()) {
                ++index;
                if (index > end) {
                    break;
                } else if (index >= start) {
                    it.remove();
                }
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> ltrimAsync(final String key, int start, int stop) {
        return runFuture(() -> ltrim(key, start, stop));
    }

    @Override
    public <T> T rpoplpush(final String key, final String key2, final Type componentType) {
        T val = rpop(key, componentType);
        lpush(key2, componentType, val);
        return val;
    }

    @Override
    public <T> CompletableFuture<T> rpoplpushAsync(final String key, final String key2, final Type componentType) {
        return supplyFuture(() -> rpoplpush(key, key2, componentType));
    }

    @Override
    public <T> T rpop(final String key, final Type componentType) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return null;
        }
        entry.lock();
        try {
            return CacheEntry.serialToObj(convert, componentType, entry.listValue.pollLast());
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<T> rpopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> rpop(key, componentType));
    }

    @Override
    public <T> void rpushx(final String key, final Type componentType, final T... values) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return;
        }
        entry.lock();
        try {
            ConcurrentLinkedDeque<Serializable> list = entry.listValue;
            for (T val : values) {
                list.add(CacheEntry.objToSerial(convert, componentType, val));
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> rpushxAsync(final String key, final Type componentType, final T... values) {
        return runFuture(() -> rpushx(key, componentType, values));
    }

    @Override
    public <T> void rpush(final String key, final Type componentType, final T... values) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.LIST);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.LIST, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            ConcurrentLinkedDeque<Serializable> list = entry.listValue;
            for (T val : values) {
                list.add(CacheEntry.objToSerial(convert, componentType, val));
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T... values) {
        return runFuture(() -> rpush(key, componentType, values));
    }

    @Override
    public <T> long lrem(final String key, final Type componentType, T value) {
        CacheEntry entry = find(key, CacheEntryType.LIST);
        if (entry == null) {
            return 0L;
        }
        entry.lock();
        try {
            return entry.listValue.remove(value) ? 1L : 0L;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Long> lremAsync(final String key, final Type componentType, T value) {
        return supplyFuture(() -> lrem(key, componentType, value));
    }

    // ------------------------ 集合 Set ------------------------
    @Override
    public <T> List<T> srandmember(String key, Type componentType, int count) {
        List<T> list = new ArrayList<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return list;
        }
        List<Serializable> vals = new ArrayList<>(entry.ssetValue);
        if (count < 0) { // 可以重复
            for (int i = 0; i < Math.abs(count); i++) {
                int index = ThreadLocalRandom.current().nextInt(vals.size());
                Serializable val = vals.get(index);
                list.add(CacheEntry.serialToObj(convert, componentType, val));
            }
        } else { // 不可以重复
            if (count >= vals.size()) {
                return vals.stream()
                        .map(val -> (T) CacheEntry.serialToObj(convert, componentType, val))
                        .collect(Collectors.toList());
            }
            return vals.subList(0, count).stream()
                    .map(val -> (T) CacheEntry.serialToObj(convert, componentType, val))
                    .collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public <T> CompletableFuture<List<T>> srandmemberAsync(String key, Type componentType, int count) {
        return supplyFuture(() -> srandmember(key, componentType, count));
    }

    @Override
    public <T> boolean smove(String key, String key2, Type componentType, T member) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return false;
        }
        boolean rs = false;
        entry.lock();
        try {
            Serializable val = CacheEntry.objToSerial(convert, componentType, member);
            rs = entry.ssetValue.remove(val);
        } finally {
            entry.unlock();
        }
        if (rs) {
            CacheEntry entry2 = find(key2, CacheEntryType.SSET);
            if (entry2 == null) {
                containerLock.lock();
                try {
                    entry2 = find(key2, CacheEntryType.SSET);
                    if (entry2 == null) {
                        entry2 = new CacheEntry(CacheEntryType.SSET, key);
                        container.put(key2, entry2);
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            entry2.lock();
            try {
                entry2.addSsetValue(convert, componentType, member);
            } finally {
                entry2.unlock();
            }
        }
        return rs;
    }

    @Override
    public <T> CompletableFuture<Boolean> smoveAsync(String key, String key2, Type componentType, T member) {
        return supplyFuture(() -> smove(key, key2, componentType, member));
    }

    @Override
    public <T> Set<T> sdiff(final String key, final Type componentType, final String... key2s) {
        return sdiff0(key, key2s).stream()
                .map(v -> (T) CacheEntry.serialToObj(convert, componentType, v))
                .collect(Collectors.toSet());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sdiffAsync(final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> sdiff(key, componentType, key2s));
    }

    @Override
    public long sdiffstore(final String key, final String srcKey, final String... srcKey2s) {
        Set<Serializable> rs = sdiff0(srcKey, srcKey2s);
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.SSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.SSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.ssetValue.clear();
            entry.ssetValue.addAll(rs);
        } finally {
            entry.unlock();
        }
        return rs.size();
    }

    @Override
    public CompletableFuture<Long> sdiffstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> sdiffstore(key, srcKey, srcKey2s));
    }

    private Set<Serializable> sdiff0(final String key, final String... key2s) {
        Set<Serializable> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return rs;
        }
        rs.addAll(entry.ssetValue);
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                en2.ssetValue.forEach(rs::remove);
            }
        }
        return rs;
    }

    @Override
    public <T> Set<T> sinter(final String key, final Type componentType, final String... key2s) {
        return sinter0(key, key2s).stream()
                .map(v -> (T) CacheEntry.serialToObj(convert, componentType, v))
                .collect(Collectors.toSet());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sinterAsync(
            final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> sinter(key, componentType, key2s));
    }

    @Override
    public long sinterstore(final String key, final String srcKey, final String... srcKey2s) {
        Set<Serializable> rs = sinter0(srcKey, srcKey2s);
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.SSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.SSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.ssetValue.clear();
            entry.ssetValue.addAll(rs);
        } finally {
            entry.unlock();
        }
        return rs.size();
    }

    @Override
    public CompletableFuture<Long> sinterstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> sinterstore(key, srcKey, srcKey2s));
    }

    private Set<Serializable> sinter0(final String key, final String... key2s) {
        Set<Serializable> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return rs;
        }
        rs.addAll(entry.ssetValue);
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                Set<Serializable> rms = new HashSet<>();
                for (Serializable v : rs) {
                    if (!en2.ssetValue.contains(v)) {
                        rms.add(v);
                    }
                }
                rs.removeAll(rms);
            } else {
                rs.clear();
                return rs;
            }
        }
        return rs;
    }

    @Override
    public <T> Set<T> sunion(final String key, final Type componentType, final String... key2s) {
        return sunion0(key, key2s).stream()
                .map(v -> (T) CacheEntry.serialToObj(convert, componentType, v))
                .collect(Collectors.toSet());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sunionAsync(
            final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> sunion(key, componentType, key2s));
    }

    @Override
    public long sunionstore(final String key, final String srcKey, final String... srcKey2s) {
        Set<Serializable> rs = sunion0(srcKey, srcKey2s);

        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.SSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.SSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.ssetValue.clear();
            entry.ssetValue.addAll(rs);
        } finally {
            entry.unlock();
        }
        return rs.size();
    }

    @Override
    public CompletableFuture<Long> sunionstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> sunionstore(key, srcKey, srcKey2s));
    }

    private Set<Serializable> sunion0(final String key, final String... key2s) {
        Set<Serializable> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry != null) {
            rs.addAll(entry.ssetValue);
        }
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                rs.addAll(en2.ssetValue);
            }
        }
        return rs;
    }

    @Override
    public <T> Set<T> smembers(final String key, final Type componentType) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return new LinkedHashSet<>();
        }
        return entry.ssetValue.stream()
                .map(v -> (T) CacheEntry.serialToObj(convert, componentType, v))
                .collect(Collectors.toSet());
    }

    @Override
    public <T> CompletableFuture<Set<T>> smembersAsync(final String key, final Type componentType) {
        return supplyFuture(() -> smembers(key, componentType));
    }

    @Override
    public <T> Map<String, Set<T>> smembers(final Type componentType, final String... keys) {
        Map<String, Set<T>> map = new HashMap<>();
        for (String key : keys) {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry != null) {
                map.put(
                        key,
                        entry.ssetValue.stream()
                                .map(v -> (T) CacheEntry.serialToObj(convert, componentType, v))
                                .collect(Collectors.toSet()));
            }
        }
        return map;
    }

    @Override
    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> smembers(componentType, keys));
    }

    @Override
    public List<Boolean> smismembers(final String key, final String... members) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        List<Boolean> rs = new ArrayList<>();
        if (entry == null) {
            for (String member : members) {
                rs.add(false);
            }
            return rs;
        }
        Set<Serializable> set = entry.ssetValue;
        for (String member : members) {
            rs.add(set.contains(member));
        }
        return rs;
    }

    @Override
    public CompletableFuture<List<Boolean>> smismembersAsync(final String key, final String... members) {
        return supplyFuture(() -> smismembers(key, members));
    }

    @Override
    public <T> void sadd(final String key, final Type componentType, T... values) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.SSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.SSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            for (T val : values) {
                entry.addSsetValue(convert, componentType, val);
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> sadd(key, componentType, values));
    }

    @Override
    public long scard(final String key) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        return entry == null ? 0L : (long) entry.ssetValue.size();
    }

    @Override
    public CompletableFuture<Long> scardAsync(final String key) {
        return supplyFuture(() -> scard(key));
    }

    @Override
    public <T> boolean sismember(final String key, final Type type, final T value) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        return entry != null && entry.ssetValue.contains(CacheEntry.objToSerial(convert, type, value));
    }

    @Override
    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type type, final T value) {
        return supplyFuture(() -> sismember(key, type, value));
    }

    @Override
    public <T> T spop(final String key, final Type componentType) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return null;
        }
        entry.lock();
        try {
            final Set<Serializable> cset = entry.ssetValue;
            if (cset.isEmpty()) {
                return null;
            }
            Iterator<Serializable> it = cset.iterator();
            Serializable del = null;
            if (it.hasNext()) {
                del = it.next();
            }
            if (del != null) {
                cset.remove(del);
                return CacheEntry.serialToObj(convert, componentType, del);
            }
            return null;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<T> spopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> spop(key, componentType));
    }

    @Override
    public <T> Set<T> spop(final String key, final int count, final Type componentType) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return new LinkedHashSet<>();
        }
        entry.lock();
        try {
            final Set<Serializable> cset = entry.ssetValue;
            if (cset.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Iterator<Serializable> it = cset.iterator();
            Set<T> list = new LinkedHashSet<>();
            Set<Serializable> rms = new LinkedHashSet<>();
            int index = 0;
            while (it.hasNext()) {
                Serializable item = it.next();
                rms.add(item);
                list.add(CacheEntry.serialToObj(convert, componentType, item));
                if (++index >= count) {
                    break;
                }
            }
            cset.removeAll(rms);
            return list;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Set<T>> spopAsync(final String key, final int count, final Type componentType) {
        return supplyFuture(() -> spop(key, count, componentType));
    }

    @Override
    public <T> Set<T> sscan(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return new LinkedHashSet<>();
        }
        entry.lock();
        try {
            final Set<Serializable> cset = entry.ssetValue;
            if (cset.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Iterator<Serializable> it = cset.iterator();
            Set<T> list = new LinkedHashSet<>();
            while (it.hasNext()) {
                list.add(CacheEntry.serialToObj(convert, componentType, it.next()));
            }
            return list;
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Set<T>> sscanAsync(
            final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> sscan(key, componentType, cursor, limit, pattern));
    }

    @Override
    public <T> long srem(String key, Type type, T... values) {
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return 0L;
        }
        long count = 0;
        for (T val : values) {
            count += entry.ssetValue.remove(CacheEntry.objToSerial(convert, type, val)) ? 1 : 0;
        }
        return count;
    }

    @Override
    public <T> CompletableFuture<Long> sremAsync(String key, Type type, T... values) {
        return supplyFuture(() -> srem(key, type, values));
    }

    // ------------------------ 有序集合 Sorted Set ------------------------
    @Override
    public void zadd(String key, CacheScoredValue... values) {
        List<CacheScoredValue> list = new ArrayList<>();
        for (CacheScoredValue v : values) {
            list.add(new CacheScoredValue(v));
        }
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.ZSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.ZSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            entry.zsetValue.addAll(list);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> zaddAsync(String key, CacheScoredValue... values) {
        return runFuture(() -> zadd(key, values));
    }

    @Override
    public <T extends Number> T zincrby(String key, CacheScoredValue value) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            containerLock.lock();
            try {
                entry = find(key, CacheEntryType.ZSET);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.ZSET, key);
                    container.put(key, entry);
                }
            } finally {
                containerLock.unlock();
            }
        }
        entry.lock();
        try {
            Set<CacheScoredValue> sets = entry.zsetValue;
            CacheScoredValue old = sets.stream()
                    .filter(v -> Objects.equals(v.getValue(), value.getValue()))
                    .findAny()
                    .orElse(null);
            if (old == null) {
                sets.add(new CacheScoredValue(value.getScore().doubleValue(), value.getValue()));
                return (T) value.getScore();
            } else {
                Number ic = value.getScore();
                if (ic instanceof Integer) {
                    old.setScore((double) (old.getScore().intValue() + ic.intValue()));
                } else if (ic instanceof Long) {
                    old.setScore((double) (old.getScore().longValue() + ic.longValue()));
                } else if (ic instanceof Float) {
                    old.setScore((double) (old.getScore().floatValue() + ic.floatValue()));
                } else if (ic instanceof Double) {
                    old.setScore(old.getScore().doubleValue() + ic.doubleValue());
                } else if (ic instanceof AtomicInteger) {
                    ((AtomicInteger) old.getScore()).addAndGet(((AtomicInteger) ic).get());
                } else if (ic instanceof AtomicLong) {
                    ((AtomicLong) old.getScore()).addAndGet(((AtomicLong) ic).get());
                }
                return (T) old.getScore();
            }
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T extends Number> CompletableFuture<T> zincrbyAsync(String key, CacheScoredValue value) {
        return supplyFuture(() -> zincrby(key, value));
    }

    @Override
    public long zcard(String key) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return 0L;
        }
        return entry.zsetValue.size();
    }

    @Override
    public CompletableFuture<Long> zcardAsync(String key) {
        return supplyFuture(() -> zcard(key));
    }

    @Override
    public Long zrank(String key, String member) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return null;
        }
        List<CacheScoredValue> list = new ArrayList<>(entry.zsetValue);
        Collections.sort(list);
        long c = 0;
        for (CacheScoredValue v : list) {
            if (Objects.equals(v.getValue(), member)) {
                return c;
            }
            c++;
        }
        return null;
    }

    @Override
    public CompletableFuture<Long> zrankAsync(String key, String member) {
        return supplyFuture(() -> zrank(key, member));
    }

    @Override
    public Long zrevrank(String key, String member) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return null;
        }
        List<CacheScoredValue> list = new ArrayList<>(entry.zsetValue);
        Collections.sort(list, Collections.reverseOrder());
        long c = 0;
        for (CacheScoredValue v : list) {
            if (Objects.equals(v.getValue(), member)) {
                return c;
            }
            c++;
        }
        return null;
    }

    @Override
    public CompletableFuture<Long> zrevrankAsync(String key, String member) {
        return supplyFuture(() -> zrevrank(key, member));
    }

    @Override
    public List<String> zrange(String key, int start, int stop) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        Set<CacheScoredValue> sets = entry.zsetValue;
        long c = 0;
        for (CacheScoredValue v : sets) {
            if (c >= start && (stop < 0 || c <= stop)) {
                list.add(v.getValue());
            }
            c++;
        }
        return list;
    }

    @Override
    public CompletableFuture<List<String>> zrangeAsync(String key, int start, int stop) {
        return supplyFuture(() -> zrange(key, start, stop));
    }

    @Override
    public List<CacheScoredValue> zscan(String key, Type scoreType, AtomicLong cursor, int limit, String pattern) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return new ArrayList();
        }
        Set<CacheScoredValue> sets = entry.zsetValue;
        if (Utility.isEmpty(pattern)) {
            return sets.stream().collect(Collectors.toList());
        } else {
            Predicate<String> regex =
                    Pattern.compile(pattern.replace("*", ".*")).asPredicate();
            return sets.stream().filter(en -> regex.test(en.getValue())).collect(Collectors.toList());
        }
    }

    @Override
    public CompletableFuture<List<CacheScoredValue>> zscanAsync(
            String key, Type scoreType, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> zscan(key, scoreType, cursor, limit, pattern));
    }

    @Override
    public long zrem(String key, String... members) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return 0L;
        }
        Set<CacheScoredValue> sets = entry.zsetValue;
        long c = 0;
        Set<String> keys = Set.of(members);
        Iterator<CacheScoredValue> it = sets.iterator();
        Set<CacheScoredValue> dels = new HashSet<>();
        while (it.hasNext()) {
            CacheScoredValue v = it.next();
            if (keys.contains(v.getValue())) {
                c++;
                dels.add(v);
            }
        }
        sets.removeAll(dels);
        return c;
    }

    @Override
    public CompletableFuture<Long> zremAsync(String key, String... members) {
        return supplyFuture(() -> zrem(key, members));
    }

    @Override
    public <T extends Number> List<T> zmscore(String key, Class<T> scoreType, String... members) {
        List<T> list = new ArrayList<>();
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            for (String member : members) {
                list.add(null);
            }
            return list;
        }
        Set<String> keys = Set.of(members);
        Set<CacheScoredValue> sets = entry.zsetValue;
        Map<String, T> map = new HashMap<>();
        sets.stream()
                .filter(v -> keys.contains(v.getValue()))
                .forEach(v -> map.put(v.getValue(), formatScore(scoreType, v.getScore())));
        for (String m : members) {
            list.add(map.get(m));
        }
        return list;
    }

    @Override
    public <T extends Number> CompletableFuture<List<T>> zmscoreAsync(
            String key, Class<T> scoreType, String... members) {
        return supplyFuture(() -> zmscore(key, scoreType, members));
    }

    private <T extends Number> T formatScore(Class<T> scoreType, Number score) {
        if (score == null) {
            return null;
        }
        if (scoreType == int.class || scoreType == Integer.class) {
            return (T) (Number) score.intValue();
        } else if (scoreType == long.class || scoreType == Long.class) {
            return (T) (Number) score.longValue();
        } else if (scoreType == float.class || scoreType == Float.class) {
            return (T) (Number) score.floatValue();
        } else if (scoreType == double.class || scoreType == Double.class) {
            return (T) (Number) score.doubleValue();
        } else {
            return (T) score;
        }
    }

    @Override
    public <T extends Number> T zscore(String key, Class<T> scoreType, String member) {
        CacheEntry entry = find(key, CacheEntryType.ZSET);
        if (entry == null) {
            return null;
        }
        Set<CacheScoredValue> sets = entry.zsetValue;
        return formatScore(
                scoreType,
                sets.stream()
                        .filter(v -> Objects.equals(member, v.getValue()))
                        .findAny()
                        .map(v -> v.getScore())
                        .orElse(null));
    }

    @Override
    public <T extends Number> CompletableFuture<T> zscoreAsync(String key, Class<T> scoreType, String member) {
        return supplyFuture(() -> zscore(key, scoreType, member));
    }

    @Override
    public long dbsize() {
        return container.size();
    }

    @Override
    public CompletableFuture<Long> dbsizeAsync() {
        return supplyFuture(this::dbsize);
    }

    @Override
    public void flushdb() {
        container.clear();
    }

    @Override
    public CompletableFuture<Void> flushdbAsync() {
        return runFuture(this::flushdb);
    }

    @Override
    public void flushall() {
        container.clear();
    }

    @Override
    public CompletableFuture<Void> flushallAsync() {
        return runFuture(this::flushall);
    }

    @Override
    public List<String> keys(String pattern) {
        List<String> rs = new ArrayList<>();
        Predicate<String> filter =
                Utility.isEmpty(pattern) ? x -> true : Pattern.compile(pattern).asPredicate();
        container.forEach((k, v) -> {
            if (filter.test(k) && !v.isExpired()) {
                rs.add(k);
            }
        });
        return rs;
    }

    @Override
    public CompletableFuture<List<String>> keysAsync(String pattern) {
        return supplyFuture(() -> keys(pattern));
    }

    @Override
    public List<String> scan(AtomicLong cursor, int limit, String pattern) {
        cursor.set(0);
        return keys(pattern);
    }

    @Override
    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern) {
        return keysAsync(pattern).thenApply(v -> {
            cursor.set(0);
            return v;
        });
    }

    @Override
    public List<String> keysStartsWith(String startsWith) {
        List<String> rs = new ArrayList<>();
        Predicate<String> filter = Utility.isEmpty(startsWith) ? x -> true : x -> x.startsWith(startsWith);
        container.forEach((k, v) -> {
            if (filter.test(k) && !v.isExpired()) {
                rs.add(k);
            }
        });
        return rs;
    }

    @Override
    public CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        return supplyFuture(() -> keysStartsWith(startsWith));
    }

    protected CacheEntry find(String key) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        entry.lastAccessed = System.currentTimeMillis();
        return entry;
    }

    protected CacheEntry find(String key, CacheEntryType cacheType) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        if (entry.cacheType != cacheType) {
            throw new SourceException(key + " value is " + entry.cacheType + " type but need " + cacheType);
        }
        entry.lastAccessed = System.currentTimeMillis();
        return entry;
    }

    public static final class RateLimitEntry {

        private String key;

        volatile long lastAccessed; // 最后刷新时间

        // <=0表示永久保存
        private long expireMills;

        private long initTime;

        // 令牌数
        private long tokens;

        // 时间戳，单位:毫秒
        private long timestamp;

        private final ReentrantLock lock = new ReentrantLock();

        public RateLimitEntry(String key, long tokens) {
            this.key = key;
            this.tokens = tokens;
            this.timestamp = System.currentTimeMillis();
        }

        public RateLimitEntry milliSeconds(long milliSeconds) {
            this.initTime = System.currentTimeMillis();
            this.expireMills = milliSeconds > 0 ? milliSeconds : 0;
            return this;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public boolean isExpired(long now) {
            return expireMills > 0 && (initTime + expireMills) < now;
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }

        public long getExpireMills() {
            return expireMills;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public String getKey() {
            return key;
        }
    }

    public enum CacheEntryType {
        OBJECT,
        ATOMIC,
        DOUBLE,
        SSET,
        ZSET,
        LIST,
        MAP;
    }

    // Serializable的具体数据类型只能是: String、byte[]、AtomicLong
    public static final class CacheEntry {

        volatile long lastAccessed; // 最后刷新时间

        // CacheEntryType为ATOMIC、DOUBLE时类型为AtomicLong
        private Serializable objectValue;

        private CopyOnWriteArraySet<Serializable> ssetValue;

        private ConcurrentSkipListSet<CacheScoredValue> zsetValue;

        private ConcurrentLinkedDeque<Serializable> listValue;

        private ConcurrentHashMap<String, Serializable> mapValue;

        private CacheEntryType cacheType;

        private String key;

        // <=0表示永久保存
        private long expireMills;

        private long initTime;

        private long endTime;

        private final ReentrantLock lock = new ReentrantLock();

        public CacheEntry(CacheEntryType cacheType, String key) {
            this.cacheType = cacheType;
            this.key = key;
            if (cacheType == CacheEntryType.SSET) {
                this.ssetValue = new CopyOnWriteArraySet();
            } else if (cacheType == CacheEntryType.ZSET) {
                this.zsetValue = new ConcurrentSkipListSet();
            } else if (cacheType == CacheEntryType.LIST) {
                this.listValue = new ConcurrentLinkedDeque();
            } else if (cacheType == CacheEntryType.MAP) {
                this.mapValue = new ConcurrentHashMap();
            }
        }

        public CacheEntry milliSeconds(long milliSeconds) {
            this.initTime = System.currentTimeMillis();
            this.expireMills = milliSeconds > 0 ? milliSeconds : 0;
            return this;
        }

        public CacheEntry expireAt(long endtime) {
            this.endTime = endtime;
            return this;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public boolean isExpired(long now) {
            if (endTime > 0) {
                return now >= endTime;
            }
            return expireMills > 0 && (initTime + expireMills) < now;
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        // value类型只能是byte[]/String/AtomicLong
        public static <T> T serialToObj(@Nonnull Convert convert, @Nonnull Type type, Serializable value) {
            if (value == null) {
                return null;
            }
            if (value.getClass() == byte[].class) {
                return (T) convert.convertFrom(type, (byte[]) value);
            } else { // String/AtomicLong
                if (convert instanceof TextConvert) {
                    return (T) ((TextConvert) convert).convertFrom(type, value.toString());
                } else {
                    return (T) convert.convertFrom(type, value.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        // 返回类型只能是byte[]/String/AtomicLong
        public static Serializable objToSerial(@Nonnull Convert convert, Type type, Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String || value instanceof byte[]) {
                return (Serializable) value;
            }
            Type t = type == null ? value.getClass() : type;
            if (convert instanceof TextConvert) {
                return ((TextConvert) convert).convertTo(t, value);
            } else {
                return convert.convertToBytes(t, value);
            }
        }

        public <T> T getObjectValue(Convert convert, Type type) {
            return serialToObj(convert, type, this.objectValue);
        }

        public void setObjectValue(Convert convert, Type type, Object value) {
            this.objectValue = objToSerial(convert, type, value);
        }

        public <T> T getMapValue(String field, Convert convert, Type type) {
            Serializable val = this.mapValue.get(field);
            return val == null ? null : serialToObj(convert, type, val);
        }

        public void setMapValue(String field, Convert convert, Type type, Object value) {
            this.mapValue.put(field, objToSerial(convert, type, value));
        }

        public Object setMapValueIfAbsent(String field, Convert convert, Type type, Object value) {
            return this.mapValue.putIfAbsent(field, objToSerial(convert, type, value));
        }

        public void addSsetValue(Convert convert, Type type, Object value) {
            this.ssetValue.add(objToSerial(convert, type, value));
        }

        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }

        public CacheEntryType getCacheType() {
            return cacheType;
        }

        public long getExpireMills() {
            return expireMills;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public String getKey() {
            return key;
        }

        public Object getObjectValue() {
            return objectValue;
        }

        public Set getSsetValue() {
            return ssetValue;
        }

        public Set getZsetValue() {
            return zsetValue;
        }

        public ConcurrentLinkedDeque getListValue() {
            return listValue;
        }

        public ConcurrentHashMap<String, Serializable> getMapValue() {
            return mapValue;
        }
    }

    // -------------------------- 过期方法 ----------------------------------
    @Override
    @Deprecated(since = "2.8.0")
    public Collection<Long> getexLongCollection(String key, int expireSeconds) {
        return (Collection<Long>) getex(key, expireSeconds, long.class);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(
            final String key, final int expireSeconds, final Type componentType) {
        return supplyFuture(() -> getexCollection(key, expireSeconds, componentType));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getexStringCollectionAsync(final String key, final int expireSeconds) {
        return supplyFuture(() -> getexStringCollection(key, expireSeconds));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(final String key, final int expireSeconds) {
        return supplyFuture(() -> getexLongCollection(key, expireSeconds));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(
            boolean set, Type componentType, String... keys) {
        return supplyFuture(() -> getCollectionMap(set, componentType, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key) {
        return supplyFuture(() -> getStringCollection(key));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(
            final boolean set, final String... keys) {
        return supplyFuture(() -> getStringCollectionMap(set, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key) {
        return supplyFuture(() -> getLongCollection(key));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(
            final boolean set, final String... keys) {
        return supplyFuture(() -> getLongCollectionMap(set, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(String key, Type componentType) {
        return supplyFuture(() -> getCollection(key, componentType));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> Collection<T> getCollection(final String key, final Type componentType) {
        return (Collection<T>) get(key, componentType);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> Map<String, Collection<T>> getCollectionMap(
            final boolean set, final Type componentType, final String... keys) {
        Map<String, Collection<T>> map = new HashMap<>();
        for (String key : keys) {
            Collection<T> s = (Collection<T>) get(key, componentType);
            if (s != null) {
                map.put(key, s);
            }
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Collection<String> getStringCollection(final String key) {
        return (Collection<String>) get(key, String.class);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Map<String, Collection<String>> getStringCollectionMap(final boolean set, final String... keys) {
        Map<String, Collection<String>> map = new HashMap<>();
        for (String key : keys) {
            Collection<String> s = (Collection<String>) get(key, String.class);
            if (s != null) {
                map.put(key, s);
            }
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Map<String, Long> getLongMap(final String... keys) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String key : keys) {
            Number n = (Number) get(key, long.class);
            map.put(key, n == null ? null : n.longValue());
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Long[] getLongArray(final String... keys) {
        Long[] rs = new Long[keys.length];
        int index = -1;
        for (String key : keys) {
            Number n = (Number) get(key, long.class);
            rs[++index] = n == null ? null : n.longValue();
        }
        return rs;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Long>> getLongMapAsync(final String... keys) {
        return supplyFuture(() -> getLongMap(keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Long[]> getLongArrayAsync(final String... keys) {
        return supplyFuture(() -> getLongArray(keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Map<String, String> getStringMap(final String... keys) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : keys) {
            Object n = get(key, String.class);
            map.put(key, n == null ? null : n.toString());
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public String[] getStringArray(final String... keys) {
        String[] rs = new String[keys.length];
        int index = -1;
        for (String key : keys) {
            Object n = get(key, String.class);
            rs[++index] = n == null ? null : n.toString();
        }
        return rs;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, String>> getStringMapAsync(final String... keys) {
        return supplyFuture(() -> getStringMap(keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<String[]> getStringArrayAsync(final String... keys) {
        return supplyFuture(() -> getStringArray(keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> Map<String, T> getMap(final Type componentType, final String... keys) {
        Map<String, T> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, (T) get(key, componentType));
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> getMap(componentType, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Collection<Long> getLongCollection(final String key) {
        return (Collection<Long>) get(key, long.class);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Map<String, Collection<Long>> getLongCollectionMap(final boolean set, final String... keys) {
        Map<String, Collection<Long>> map = new HashMap<>();
        for (String key : keys) {
            Collection<Long> s = (Collection<Long>) get(key, long.class);
            if (s != null) {
                map.put(key, s);
            }
        }
        return map;
    }

    @Override
    @Deprecated(since = "2.8.0")
    public int getCollectionSize(final String key) {
        Collection collection = (Collection) get(key, Object.class);
        return collection == null ? 0 : collection.size();
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Integer> getCollectionSizeAsync(final String key) {
        return supplyFuture(() -> getCollectionSize(key));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> Collection<T> getexCollection(final String key, final int expireSeconds, final Type componentType) {
        return (Collection<T>) getex(key, expireSeconds, componentType);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public Collection<String> getexStringCollection(final String key, final int expireSeconds) {
        return (Collection<String>) getex(key, expireSeconds, String.class);
    }
}
