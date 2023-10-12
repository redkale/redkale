/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Type;
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
import org.redkale.annotation.ResourceListener;
import org.redkale.annotation.ResourceType;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.service.Local;
import org.redkale.util.*;
import static org.redkale.util.Utility.isEmpty;

/**
 * CacheSource的默认实现--内存缓存, 此实现只可用于调试，不可用于生产环境
 * 注意: url 需要指定为 memory:cachesource
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(CacheSource.class)
public final class CacheMemorySource extends AbstractCacheSource {

    @Resource
    private JsonConvert defaultConvert;

    @Resource(name = Resource.PARENT_NAME + "_convert", required = false)
    private JsonConvert convert;

    private String name;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final ConcurrentHashMap<String, CacheEntry> container = new ConcurrentHashMap<>();

    private final ReentrantLock containerLock = new ReentrantLock();

    private final BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) {
            logger.log(Level.SEVERE, "CompletableFuture complete error", (Throwable) t);
        }
    };

    //key: topic
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
    @ResourceListener
    public void onResourceChange(ResourceEvent[] events) {
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
                subExecutor = func == null ? Executors.newFixedThreadPool(Utility.cpus(), r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    int c = counter.incrementAndGet();
                    t.setName(String.format(threadNameFormat, "Virtual-" + (c < 10 ? ("00" + c) : (c < 100 ? ("0" + c) : c))));
                    return t;
                }) : func.apply(threadNameFormat);
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
        return getClass().getSimpleName() + "{type=memory, name='" + resourceName() + "'}";
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
                this.expireHandler = (Consumer<CacheEntry>) clazz.getDeclaredConstructor().newInstance();
                RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, expireHandlerClass);
            } catch (Throwable e) {
                logger.log(Level.SEVERE, self.getClass().getSimpleName() + " new expirehandler class (" + expireHandlerClass + ") instance error", e);
            }
        }
        if (scheduler == null) {
            this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                final Thread t = new Thread(r, "Redkale-" + self.getClass().getSimpleName() + "-" + resourceName() + "-Expirer-Thread");
                t.setDaemon(true);
                return t;
            });
            final List<String> keys = new ArrayList<>();
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    keys.clear();
                    long now = System.currentTimeMillis();
                    container.forEach((k, x) -> {
                        if (x.expireMills > 0 && (now > (x.lastAccessed + x.expireMills))) {
                            keys.add(x.key);
                        }
                    });
                    for (String key : keys) {
                        CacheEntry entry = container.remove(key);
                        if (expireHandler != null && entry != null) {
                            expireHandler.accept(entry);
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "CacheMemorySource schedule(interval=" + 10 + "s) error", t);
                }
            }, 10, 10, TimeUnit.SECONDS);
            logger.info(self.getClass().getSimpleName() + ":" + self.resourceName() + " start schedule expire executor");
        }
    }

    @Override
    public void close() throws Exception {  //给Application 关闭时调用
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
        }
        if (subExecutor != null) {
            subExecutor.shutdown();
        }
    }

    @Override
    public CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(true);
    }

    //------------------------ 订阅发布 SUB/PUB ------------------------ 
    @Override
    public CompletableFuture<List<String>> pubsubChannelsAsync(@Nullable String pattern) {
        Predicate<String> predicate = isEmpty(pattern) ? t -> true : Pattern.compile(pattern).asPredicate();
        return CompletableFuture.completedFuture(pubsubListeners.keySet().stream().filter(predicate).collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Void> subscribeAsync(CacheEventListener<byte[]> listener, String... topics) {
        Objects.requireNonNull(listener);
        if (topics == null || topics.length < 1) {
            throw new RedkaleException("topics is empty");
        }
        for (String topic : topics) {
            pubsubListeners.computeIfAbsent(topic, t -> new CopyOnWriteArraySet<>()).add(listener);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> unsubscribeAsync(CacheEventListener listener, String... topics) {
        int c = 0;
        if (listener == null) {
            if (topics == null || topics.length < 1) {  //清空所有订阅者
                for (Set<CacheEventListener<byte[]>> listeners : pubsubListeners.values()) {
                    c += listeners != null ? listeners.size() : 0;
                }
                pubsubListeners.clear();
            } else {
                for (String topic : topics) {  //清空指定topic的订阅者
                    Set<CacheEventListener<byte[]>> listeners = pubsubListeners.remove(topic);
                    c += listeners != null ? listeners.size() : 0;
                }
            }
        } else {
            if (topics == null || topics.length < 1) {
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

    //------------------------ 字符串 String ------------------------  
    @Override
    public CompletableFuture<Void> msetAsync(Serializable... keyVals) {
        return runFuture(() -> {
            if (keyVals.length % 2 != 0) {
                throw new SourceException("key value must be paired");
            }
            for (int i = 0; i < keyVals.length; i += 2) {
                String key = keyVals[i].toString();
                Object val = keyVals[i + 1];
                set0(key.toString(), 0, null, val);
            }
        });
    }

    @Override
    public CompletableFuture<Void> msetAsync(Map map) {
        return runFuture(() -> {
            map.forEach((key, val) -> {
                set0(key.toString(), 0, null, val);
            });
        });
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value) {
        return runFuture(() -> {
            set0(key, 0, type, value);
        });
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxAsync(String key, Convert convert, Type type, T value) {
        return setnxexAsync(key, 0, convert, type, value);
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key);
            if (entry == null) {
                containerLock.lock();
                try {
                    entry = find(key, CacheEntryType.OBJECT);
                    if (entry == null) {
                        entry = new CacheEntry(CacheEntryType.OBJECT, key);
                        container.put(key, entry);
                        entry.objectValue = value;
                        entry.expireSeconds(expireSeconds);
                        entry.lastAccessed = System.currentTimeMillis();
                        return true;
                    }
                    return false;
                } finally {
                    containerLock.unlock();
                }
            }
            return false;
        });
    }

    @Override
    public <T> CompletableFuture<T> getSetAsync(String key, Convert convert, Type type, T value) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.OBJECT);
            T old = entry == null ? null : (T) entry.objectValue;
            set0(key, 0, type, value);
            return old;
        });
    }

    @Override
    public <T> CompletableFuture<T> getDelAsync(String key, Type type) {
        return supplyFuture(() -> {
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
            return (T) entry.objectValue;
        });
    }

    private void set0(String key, int expireSeconds, Type type, Object value) {
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
            entry.objectValue = Utility.convertValue(type, value);
            entry.expireSeconds(expireSeconds);
            entry.lastAccessed = System.currentTimeMillis();
        } finally {
            entry.unlock();
        }
    }

    @Override
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return runFuture(() -> {
            set0(key, expireSeconds, type, value);
        });
    }

    @Override
    public CompletableFuture<Void> expireAsync(String key, int expireSeconds) {
        return runFuture(() -> {
            CacheEntry entry = find(key);
            if (entry == null) {
                return;
            }
            entry.lock();
            try {
                entry.expireSeconds(expireSeconds);
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> persistAsync(final String key) {
        return supplyFuture(() -> {
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
        });
    }

    @Override
    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey) {
        return supplyFuture(() -> {
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
        });
    }

    @Override
    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey) {
        return supplyFuture(() -> {
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
        });
    }

    @Override
    public CompletableFuture<Long> delAsync(final String... keys) {
        return supplyFuture(() -> {
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
        });
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return incrbyAsync(key, 1);
    }

    @Override
    public CompletableFuture<Long> incrbyAsync(final String key, long num) {
        return supplyFuture(() -> {
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
                    entry.objectValue = new AtomicLong(Long.parseLong(entry.objectValue.toString()));
                    entry.cacheType = CacheEntryType.ATOMIC;
                }
                return ((AtomicLong) entry.objectValue).addAndGet(num);
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Double> incrbyFloatAsync(final String key, double num) {
        return supplyFuture(() -> {
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
                    entry.objectValue = new AtomicLong(Long.parseLong(entry.objectValue.toString()));
                    entry.cacheType = CacheEntryType.DOUBLE;
                }
                Long v = ((AtomicLong) entry.objectValue).addAndGet(Double.doubleToLongBits(num));
                return Double.longBitsToDouble(v.intValue());
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key) {
        return incrbyAsync(key, -1);
    }

    @Override
    public CompletableFuture<Long> decrbyAsync(final String key, long num) {
        return incrbyAsync(key, -num);
    }

    @Override
    public <T> CompletableFuture<List<T>> mgetAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> {
            List<T> list = new ArrayList<>();
            for (String key : keys) {
                list.add(get0(key, 0, componentType));
            }
            return list;
        });
    }

    //----------- hxxx --------------
    @Override
    public CompletableFuture<Boolean> existsAsync(String key) {
        return supplyFuture(() -> find(key) != null);
    }

    @Override
    public <T> CompletableFuture<T> getAsync(final String key, final Type type) {
        return supplyFuture(() -> get0(key, 0, type));
    }

    @Override
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type) {
        return supplyFuture(() -> get0(key, expireSeconds, type));
    }

    private <T> T get0(final String key, final int expireSeconds, final Type type) {
        CacheEntry entry = find(key);
        if (entry == null) {
            return null;
        }
        if (expireSeconds > 0) {
            entry.expireSeconds(expireSeconds);
        }
        // OBJECT, ATOMIC, DOUBLE, SSET, ZSET, LIST, MAP;
        switch (entry.cacheType) {
            case ATOMIC:
                return Utility.convertValue(type, (AtomicLong) entry.objectValue);
            case DOUBLE:
                return Utility.convertValue(type, Double.longBitsToDouble(((AtomicLong) entry.objectValue).intValue()));
            case SSET:
                return (T) new LinkedHashSet(entry.setValue);
            case ZSET:
                return (T) new LinkedHashSet(entry.setValue);
            case LIST:
                return (T) new ArrayList(entry.listValue);
            case MAP:
                return (T) new LinkedHashMap<>(entry.mapValue);
            default:
                Object obj = entry.objectValue;
                if (obj != null && obj.getClass() != type) {
                    return (T) JsonConvert.root().convertFrom(type, JsonConvert.root().convertToBytes(obj));
                }
                return (T) obj;
        }
    }

    //------------------------ 哈希表 Hash ------------------------
    @Override
    public CompletableFuture<Long> hdelAsync(final String key, String... fields) {
        return supplyFuture(() -> {
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
        });
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return supplyFuture(() -> {
            List<String> list = new ArrayList<>();
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return list;
            }
            list.addAll(entry.mapValue.keySet());
            return list;
        });
    }

    @Override
    public CompletableFuture<Long> hlenAsync(final String key) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.MAP);
            return entry == null ? 0L : (long) entry.mapValue.keySet().size();
        });
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return hincrbyAsync(key, field, 1);
    }

    private long hincrby0(final String key, String field, long num) {
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
                val = new AtomicLong(((Number) val).longValue());
                map.put(field, val);
            }
            return ((AtomicLong) val).addAndGet(num);
        } finally {
            entry.unlock();
        }
    }

    @Override
    public CompletableFuture<Long> hincrbyAsync(final String key, String field, long num) {
        return supplyFuture(() -> hincrby0(key, field, num));
    }

    @Override
    public CompletableFuture<Double> hincrbyFloatAsync(final String key, String field, double num) {
        return supplyFuture(() -> Double.longBitsToDouble(hincrby0(key, field, Double.doubleToLongBits(num))));
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field) {
        return hincrbyAsync(key, field, -1);
    }

    @Override
    public CompletableFuture<Long> hdecrbyAsync(final String key, String field, long num) {
        return hincrbyAsync(key, field, -num);
    }

    @Override
    public CompletableFuture<Boolean> hexistsAsync(final String key, String field) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.MAP);
            return entry != null && entry.mapValue.contains(field);
        });
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return runFuture(() -> hset0(key, field, type, value));
    }

    @Override
    public <T> CompletableFuture<Boolean> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return supplyFuture(() -> {
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
                boolean rs = entry.mapValue.putIfAbsent(field, Utility.convertValue(type, value)) == null;
                entry.lastAccessed = System.currentTimeMillis();
                return rs;
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        return runFuture(() -> {
            for (int i = 0; i < values.length; i += 2) {
                hset0(key, (String) values[i], null, values[i + 1]);
            }
        });
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Map map) {
        return runFuture(() -> {
            map.forEach((k, v) -> hset0(key, (String) k, null, v));
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return null;
            }
            Map map = entry.mapValue;
            List<T> rs = new ArrayList<>(fields.length);
            for (String field : fields) {
                rs.add((T) Utility.convertValue(type, map.get(field)));
            }
            return rs;
        });
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hgetallAsync(final String key, final Type type) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return new LinkedHashMap();
            } else {
                Map map = new LinkedHashMap();
                entry.mapValue.forEach((k, v) -> {
                    map.put(k, Utility.convertValue(type, v));
                });
                return map;
            }
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> hvalsAsync(final String key, final Type type) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return new ArrayList();
            } else {
                Stream<T> stream = entry.mapValue.values().stream().map(v -> Utility.convertValue(type, v));
                return new ArrayList(stream.collect(Collectors.toList()));
            }
        });
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hscanAsync(final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> {
            if (key == null) {
                return new HashMap();
            }
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return new HashMap();
            }
            if (Utility.isEmpty(pattern)) {
                return new LinkedHashMap(entry.mapValue);
            } else {
                Predicate<String> regx = Pattern.compile(pattern.replace("*", ".*")).asPredicate();
                Set<Map.Entry<String, Serializable>> set = entry.mapValue.entrySet();
                return (Map) set.stream().filter(en -> regx.test(en.getKey())).collect(Collectors.toMap(en -> en.getKey(), en -> en.getValue()));
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return supplyFuture(() -> {
            if (key == null || field == null) {
                return null;
            }
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return null;
            }
            Object obj = entry.mapValue.get(field);
            return obj == null ? null : Utility.convertValue(type, obj);
        });
    }

    @Override
    public CompletableFuture<Long> hstrlenAsync(final String key, final String field) {
        return supplyFuture(() -> {
            if (key == null || field == null) {
                return 0L;
            }
            CacheEntry entry = find(key, CacheEntryType.MAP);
            if (entry == null) {
                return 0L;
            }
            Object obj = entry.mapValue.get(field);
            return obj == null ? 0L : (long) obj.toString().length();
        });
    }

    private void hset0(String key, String field, Type type, Object value) {
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
            entry.mapValue.put(field, Utility.convertValue(type, value));
            entry.lastAccessed = System.currentTimeMillis();
        } finally {
            entry.unlock();
        }
    }

    //------------------------ 列表 List ------------------------
    @Override
    public <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType, int start, int stop) {
        return getAsync(key, componentType);
    }

    @Override
    public <T> CompletableFuture<Map<String, List<T>>> lrangesAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> {
            Map<String, List<T>> map = new HashMap<>();
            for (String key : keys) {
                List<T> s = (List<T>) get(key, componentType);
                if (s != null) {
                    map.put(key, s);
                }
            }
            return map;
        });
    }

    @Override
    public CompletableFuture<Long> llenAsync(final String key) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            return entry == null ? 0L : (long) entry.listValue.size();
        });
    }

    @Override
    public <T> CompletableFuture<T> lindexAsync(String key, Type componentType, int index) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return null;
            }
            List list = new ArrayList(entry.listValue);
            int pos = index >= 0 ? index : list.size() + index;
            return pos >= list.size() ? null : (T) list.get(pos);
        });
    }

    @Override
    public <T> CompletableFuture<Long> linsertBeforeAsync(String key, Type componentType, T pivot, T value) {
        return linsertAsync(key, componentType, true, pivot, value);
    }

    @Override
    public <T> CompletableFuture<Long> linsertAfterAsync(String key, Type componentType, T pivot, T value) {
        return linsertAsync(key, componentType, false, pivot, value);
    }

    protected <T> CompletableFuture<Long> linsertAsync(String key, Type componentType, boolean before, T pivot, T value) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return 0L;
            }
            entry.lock();
            try {
                List<T> list = new ArrayList<>(entry.listValue);
                int pos = list.indexOf(pivot);
                if (pos < 0) {
                    return -1L;
                }
                List<T> newList = new ArrayList<>();
                if (before) {
                    if (pos == 0) {
                        newList.add(value);
                        newList.addAll(list);
                    } else {
                        newList.addAll(list.subList(0, pos));
                        newList.add(value);
                        newList.addAll(list.subList(pos, list.size()));
                    }
                } else {
                    if (pos == list.size() - 1) {
                        newList.addAll(list);
                        newList.add(value);
                    } else {
                        newList.addAll(list.subList(0, pos + 1));
                        newList.add(value);
                        newList.addAll(list.subList(pos + 1, list.size()));
                    }
                }
                entry.listValue.clear();
                entry.listValue.addAll(newList);
                return 1L;
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Void> lpushAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> {
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
                    entry.listValue.addFirst(val);
                }
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Void> lpushxAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return;
            }
            entry.lock();
            try {
                ConcurrentLinkedDeque list = entry.listValue;
                for (T val : values) {
                    list.addFirst(val);
                }
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> lpopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return null;
            }
            entry.lock();
            try {
                return Utility.convertValue(componentType, entry.listValue.pollFirst());
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Void> ltrimAsync(final String key, int start, int stop) {
        return runFuture(() -> {
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
        });
    }

    @Override
    public <T> CompletableFuture<T> rpoplpushAsync(final String key, final String key2, final Type componentType) {
        return supplyFuture(() -> {
            T val = rpop(key, componentType);
            lpush(key2, componentType, val);
            return val;
        });
    }

    @Override
    public <T> CompletableFuture<T> rpopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return null;
            }
            entry.lock();
            try {
                return Utility.convertValue(componentType, entry.listValue.pollLast());
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Void> rpushxAsync(final String key, final Type componentType, final T... values) {
        return runFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.LIST);
            if (entry == null) {
                return;
            }
            entry.lock();
            try {
                ConcurrentLinkedDeque list = entry.listValue;
                for (T val : values) {
                    list.add(val);
                }
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T... values) {
        return runFuture(() -> {
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
                ConcurrentLinkedDeque list = entry.listValue;
                for (T val : values) {
                    list.add(val);
                }
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Long> lremAsync(final String key, final Type componentType, T value) {
        return supplyFuture(() -> {
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
        });
    }

    //------------------------ 集合 Set ------------------------    
    @Override
    public <T> CompletableFuture<List<T>> srandmemberAsync(String key, Type componentType, int count) {
        return supplyFuture(() -> {
            List<T> list = new ArrayList<>();
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return list;
            }
            List<T> vals = new ArrayList<>(entry.setValue);
            if (count < 0) {  //可以重复
                for (int i = 0; i < Math.abs(count); i++) {
                    int index = ThreadLocalRandom.current().nextInt(vals.size());
                    T val = vals.get(index);
                    list.add(Utility.convertValue(componentType, val));
                }
            } else { //不可以重复
                if (count >= vals.size()) {
                    return vals;
                }
                return vals.subList(0, count);
            }
            return list;
        });
    }

    @Override
    public <T> CompletableFuture<Boolean> smoveAsync(String key, String key2, Type componentType, T member) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return false;
            }
            boolean rs = false;
            entry.lock();
            try {
                rs = entry.setValue.remove(member);
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
                    entry2.setValue.add(member);
                } finally {
                    entry2.unlock();
                }
            }
            return rs;
        });
    }

    @Override
    public <T> CompletableFuture<Set<T>> sdiffAsync(final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> {
            return sdiff0(key, key2s);
        });
    }

    @Override
    public CompletableFuture<Long> sdiffstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> {
            Set rs = sdiff0(srcKey, srcKey2s);
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
                entry.setValue.clear();
                entry.setValue.addAll(rs);
            } finally {
                entry.unlock();
            }
            return (long) rs.size();
        });
    }

    private <T> Set<T> sdiff0(final String key, final String... key2s) {
        Set<T> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return rs;
        }
        rs.addAll(entry.setValue);
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                en2.setValue.forEach(v -> rs.remove(v));
            }
        }
        return rs;
    }

    @Override
    public <T> CompletableFuture<Set<T>> sinterAsync(final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> {
            return sinter0(key, key2s);
        });
    }

    @Override
    public CompletableFuture<Long> sinterstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> {
            Set rs = sinter0(srcKey, srcKey2s);
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
                entry.setValue.clear();
                entry.setValue.addAll(rs);
            } finally {
                entry.unlock();
            }
            return (long) rs.size();
        });
    }

    private <T> Set<T> sinter0(final String key, final String... key2s) {
        Set<T> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry == null) {
            return rs;
        }
        rs.addAll(entry.setValue);
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                Set<T> removes = new HashSet<>();
                for (T v : rs) {
                    if (!en2.setValue.contains(v)) {
                        removes.add(v);
                    }
                }
                rs.removeAll(removes);
            } else {
                rs.clear();
                return rs;
            }
        }
        return rs;
    }

    @Override
    public <T> CompletableFuture<Set<T>> sunionAsync(final String key, final Type componentType, final String... key2s) {
        return supplyFuture(() -> {
            return sunion0(key, key2s);
        });
    }

    @Override
    public CompletableFuture<Long> sunionstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyFuture(() -> {
            Set rs = sunion0(srcKey, srcKey2s);

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
                entry.setValue.clear();
                entry.setValue.addAll(rs);
            } finally {
                entry.unlock();
            }
            return (long) rs.size();
        });
    }

    private <T> Set<T> sunion0(final String key, final String... key2s) {
        Set<T> rs = new HashSet<>();
        CacheEntry entry = find(key, CacheEntryType.SSET);
        if (entry != null) {
            rs.addAll(entry.setValue);
        }
        for (String k : key2s) {
            CacheEntry en2 = find(k, CacheEntryType.SSET);
            if (en2 != null) {
                rs.addAll(en2.setValue);
            }
        }
        return rs;
    }

    @Override
    public <T> CompletableFuture<Set<T>> smembersAsync(final String key, final Type componentType) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return new LinkedHashSet<T>();
            }
            return new LinkedHashSet<>(entry.setValue);
        });
    }

    @Override
    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(final Type componentType, final String... keys) {
        return supplyFuture(() -> {
            Map<String, Set<T>> map = new HashMap<>();
            for (String key : keys) {
                CacheEntry entry = find(key, CacheEntryType.SSET);
                if (entry != null) {
                    map.put(key, new LinkedHashSet<>(entry.setValue));
                }
            }
            return map;
        });
    }

    @Override
    public CompletableFuture<List<Boolean>> smismembersAsync(final String key, final String... members) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            List<Boolean> rs = new ArrayList<>();
            if (entry == null) {
                for (String member : members) {
                    rs.add(false);
                }
                return rs;
            }
            Set set = entry.setValue;
            for (String member : members) {
                rs.add(set.contains(member));
            }
            return rs;
        });
    }

    @Override
    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, T... values) {
        return runFuture(() -> {
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
                Set set = entry.setValue;
                for (T val : values) {
                    set.add(val);
                }
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Long> scardAsync(final String key) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            return entry == null ? 0L : (long) entry.setValue.size();
        });
    }

    @Override
    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type type, final T value) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            return entry != null && entry.setValue.contains(value);
        });
    }

    @Override
    public <T> CompletableFuture<T> spopAsync(final String key, final Type componentType) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return null;
            }
            entry.lock();
            try {
                final Set cset = entry.setValue;
                if (cset.isEmpty()) {
                    return null;
                }
                Iterator it = cset.iterator();
                Object del = null;
                if (it.hasNext()) {
                    del = it.next();
                }
                if (del != null) {
                    cset.remove(del);
                    return (T) del;
                }
                return null;
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Set<T>> spopAsync(final String key, final int count, final Type componentType) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return new LinkedHashSet<>();
            }
            entry.lock();
            try {
                final Set cset = entry.setValue;
                if (cset.isEmpty()) {
                    return new LinkedHashSet<>();
                }
                Iterator it = cset.iterator();
                Set<T> list = new LinkedHashSet<>();
                int index = 0;
                while (it.hasNext()) {
                    list.add(Utility.convertValue(componentType, it.next()));
                    if (++index >= count) {
                        break;
                    }
                }
                cset.removeAll(list);
                return list;
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Set<T>> sscanAsync(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return new LinkedHashSet<>();
            }
            entry.lock();
            try {
                final Set cset = entry.setValue;
                if (cset.isEmpty()) {
                    return new LinkedHashSet<>();
                }
                Iterator it = cset.iterator();
                Set<T> list = new LinkedHashSet<>();
                while (it.hasNext()) {
                    list.add((T) Utility.convertValue(componentType, it.next()));
                }
                return list;
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T> CompletableFuture<Long> sremAsync(String key, Type type, T... values) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.SSET);
            if (entry == null) {
                return 0L;
            }
            return entry.setValue.removeAll(Arrays.asList(values)) ? 1L : 0L;
        });
    }

    //------------------------ 有序集合 Sorted Set ------------------------
    @Override
    public CompletableFuture<Void> zaddAsync(String key, CacheScoredValue... values) {
        return runFuture(() -> {
            List<Object> list = new ArrayList<>();
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
                entry.setValue.addAll(list);
            } finally {
                entry.unlock();
            }
        });
    }

    @Override
    public <T extends Number> CompletableFuture<T> zincrbyAsync(String key, CacheScoredValue value) {
        return supplyFuture(() -> {
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
                Set<CacheScoredValue> sets = entry.setValue;
                CacheScoredValue old = sets.stream().filter(v -> Objects.equals(v.getValue(), value.getValue())).findAny().orElse(null);
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
        });
    }

    @Override
    public CompletableFuture<Long> zcardAsync(String key) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return 0L;
            }
            return (long) entry.setValue.size();
        });
    }

    @Override
    public CompletableFuture<Long> zrankAsync(String key, String member) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return null;
            }
            List<CacheScoredValue> list = new ArrayList<>(entry.setValue);
            Collections.sort(list);
            long c = 0;
            for (CacheScoredValue v : list) {
                if (Objects.equals(v.getValue(), member)) {
                    return c;
                }
                c++;
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Long> zrevrankAsync(String key, String member) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return null;
            }
            List<CacheScoredValue> list = new ArrayList<>(entry.setValue);
            Collections.sort(list, Collections.reverseOrder());
            long c = 0;
            for (CacheScoredValue v : list) {
                if (Objects.equals(v.getValue(), member)) {
                    return c;
                }
                c++;
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<String>> zrangeAsync(String key, int start, int stop) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return new ArrayList<>();
            }
            List<String> list = new ArrayList<>();
            Set<CacheScoredValue> sets = entry.setValue;
            long c = 0;
            for (CacheScoredValue v : sets) {
                if (c >= start && (stop < 0 || c <= stop)) {
                    list.add(v.getValue());
                }
                c++;
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<CacheScoredValue>> zscanAsync(String key, Type scoreType, AtomicLong cursor, int limit, String pattern) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return new ArrayList();
            }
            Set<CacheScoredValue> sets = entry.setValue;
            if (Utility.isEmpty(pattern)) {
                return sets.stream().collect(Collectors.toList());
            } else {
                Predicate<String> regx = Pattern.compile(pattern.replace("*", ".*")).asPredicate();
                return sets.stream().filter(en -> regx.test(en.getValue())).collect(Collectors.toList());
            }
        });
    }

    @Override
    public CompletableFuture<Long> zremAsync(String key, String... members) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return 0L;
            }
            Set<CacheScoredValue> sets = entry.setValue;
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
        });
    }

    @Override
    public <T extends Number> CompletableFuture<List<T>> zmscoreAsync(String key, Class<T> scoreType, String... members) {
        return supplyFuture(() -> {
            List<T> list = new ArrayList<>();
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                for (int i = 0; i < members.length; i++) {
                    list.add(null);
                }
                return list;
            }
            Set<String> keys = Set.of(members);
            Set<CacheScoredValue> sets = entry.setValue;
            Map<String, T> map = new HashMap<>();
            sets.stream().filter(v -> keys.contains(v.getValue())).forEach(v -> {
                map.put(v.getValue(), formatScore(scoreType, v.getScore()));
            });
            for (String m : members) {
                list.add(map.get(m));
            }
            return list;
        });
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
    public <T extends Number> CompletableFuture<T> zscoreAsync(String key, Class<T> scoreType, String member) {
        return supplyFuture(() -> {
            CacheEntry entry = find(key, CacheEntryType.ZSET);
            if (entry == null) {
                return null;
            }
            Set<CacheScoredValue> sets = entry.setValue;
            return formatScore(scoreType, sets.stream().filter(v -> Objects.equals(member, v.getValue())).findAny().map(v -> v.getScore()).orElse(null));
        });
    }

    @Override
    public CompletableFuture<Long> dbsizeAsync() {
        return supplyFuture(() -> {
            return (long) container.size();
        });
    }

    @Override
    public CompletableFuture<Void> flushdbAsync() {
        return runFuture(() -> {
            container.clear();
        });
    }

    @Override
    public CompletableFuture<Void> flushallAsync() {
        return runFuture(() -> {
            container.clear();
        });
    }

    @Override
    public CompletableFuture<List<String>> keysAsync(String pattern) {
        return supplyFuture(() -> {
            List<String> rs = new ArrayList<>();
            Predicate<String> filter = isEmpty(pattern) ? x -> true : Pattern.compile(pattern).asPredicate();
            container.forEach((k, v) -> {
                if (filter.test(k) && !v.isExpired()) {
                    rs.add(k);
                }
            });
            return rs;
        });
    }

    @Override
    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern) {
        return keysAsync(pattern).thenApply(v -> {
            cursor.set(0);
            return v;
        });
    }

    @Override
    public CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        return supplyFuture(() -> {
            List<String> rs = new ArrayList<>();
            Predicate<String> filter = isEmpty(startsWith) ? x -> true : x -> x.startsWith(startsWith);
            container.forEach((k, v) -> {
                if (filter.test(k) && !v.isExpired()) {
                    rs.add(k);
                }
            });
            return rs;
        });
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

    public static enum CacheEntryType {
        OBJECT, ATOMIC, DOUBLE, SSET, ZSET, LIST, MAP;
    }

    public static final class CacheEntry {

        volatile long lastAccessed; //最后刷新时间

        Object objectValue;

        Set setValue;

        ConcurrentLinkedDeque listValue;

        ConcurrentHashMap mapValue;

        private CacheEntryType cacheType;

        private String key;

        private int expireMills; //<=0表示永久保存

        private final ReentrantLock lock = new ReentrantLock();

        public CacheEntry(CacheEntryType cacheType, String key) {
            this.cacheType = cacheType;
            this.key = key;
            if (cacheType == CacheEntryType.SSET) {
                this.setValue = new CopyOnWriteArraySet();
            } else if (cacheType == CacheEntryType.ZSET) {
                this.setValue = new ConcurrentSkipListSet();
            } else if (cacheType == CacheEntryType.LIST) {
                this.listValue = new ConcurrentLinkedDeque();
            } else if (cacheType == CacheEntryType.MAP) {
                this.mapValue = new ConcurrentHashMap();
            }
        }

        public CacheEntry expireSeconds(int expireSeconds) {
            this.expireMills = expireSeconds > 0 ? expireSeconds * 1000 : 0;
            return this;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return expireMills > 0 && (lastAccessed + expireMills) < System.currentTimeMillis();
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

        public int getExpireMills() {
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

        public Set getSetValue() {
            return setValue;
        }

        public ConcurrentLinkedDeque getListValue() {
            return listValue;
        }

        public ConcurrentHashMap<String, Serializable> getMapValue() {
            return mapValue;
        }
    }

    //-------------------------- 过期方法 ----------------------------------
    @Override
    @Deprecated(since = "2.8.0")
    public Collection<Long> getexLongCollection(String key, int expireSeconds) {
        return (Collection<Long>) getex(key, expireSeconds, long.class);
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(final String key, final int expireSeconds, final Type componentType) {
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
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(boolean set, Type componentType, String... keys) {
        return supplyFuture(() -> getCollectionMap(set, componentType, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key) {
        return supplyFuture(() -> getStringCollection(key));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys) {
        return supplyFuture(() -> getStringCollectionMap(set, keys));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key) {
        return supplyFuture(() -> getLongCollection(key));
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys) {
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
    public <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, final String... keys) {
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
