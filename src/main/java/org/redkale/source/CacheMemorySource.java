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
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.annotation.*;
import org.redkale.annotation.ResourceListener;
import org.redkale.annotation.ResourceType;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.service.Local;
import org.redkale.util.*;

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

    @Resource(name = "$_convert", required = false)
    private JsonConvert convert;

    private String name;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<String, CacheEntry<Object>> container = new ConcurrentHashMap<>();

    protected final ReentrantLock containerLock = new ReentrantLock();

    protected final BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) {
            logger.log(Level.SEVERE, "CompletableFuture complete error", (Throwable) t);
        }
    };

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

    public static boolean acceptsConf(AnyValue config) {
        return config.getValue(CACHE_SOURCE_NODES).startsWith("memory:");
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
                        if (x.expireSeconds > 0 && (now > (x.lastAccessed + x.expireSeconds))) {
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
    }

    @Override
    public CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(true);
    }

    //----------- hxxx --------------
    @Override
    public CompletableFuture<Long> hdelAsync(final String key, String... fields) {
        return supplyAsync(() -> {
            long count = 0;
            CacheEntry entry = container.get(key);
            if (entry == null || entry.mapValue == null) {
                return 0L;
            }
            for (String field : fields) {
                if (entry.mapValue.remove(field) != null) {
                    count++;
                }
            }
            return count;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return supplyAsync(() -> {
            List<String> list = new ArrayList<>();
            CacheEntry entry = container.get(key);
            if (entry == null || entry.mapValue == null) {
                return list;
            }
            list.addAll(entry.mapValue.keySet());
            return list;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> hlenAsync(final String key) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null || entry.mapValue == null) {
                return 0L;
            }
            return (long) entry.mapValue.keySet().size();
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return hincrbyAsync(key, field, 1);
    }

    @Override
    public CompletableFuture<Long> hincrbyAsync(final String key, String field, long num) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null) {
                containerLock.lock();
                try {
                    entry = container.get(key);
                    if (entry == null) {
                        ConcurrentHashMap<String, Serializable> map = new ConcurrentHashMap();
                        map.put(field, new AtomicLong());
                        entry = new CacheEntry(CacheEntryType.MAP, key, new AtomicLong(), null, null, map);
                        container.put(key, entry);
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            Serializable val = (Serializable) entry.mapValue.computeIfAbsent(field, f -> new AtomicLong());
            if (!(val instanceof AtomicLong)) {
                entry.lock.lock();
                try {
                    if (!(val instanceof AtomicLong)) {
                        if (val == null) {
                            val = new AtomicLong();
                        } else {
                            val = new AtomicLong(((Number) val).longValue());
                        }
                        entry.mapValue.put(field, val);
                    }
                } finally {
                    entry.lock.unlock();
                }
            }
            return ((AtomicLong) entry.mapValue.get(field)).addAndGet(num);
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Double> hincrbyFloatAsync(final String key, String field, double num) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null) {
                containerLock.lock();
                try {
                    entry = container.get(key);
                    if (entry == null) {
                        ConcurrentHashMap<String, Serializable> map = new ConcurrentHashMap();
                        map.put(field, new AtomicLong());
                        entry = new CacheEntry(CacheEntryType.MAP, key, new AtomicLong(), null, null, map);
                        container.put(key, entry);
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            Serializable val = (Serializable) entry.mapValue.computeIfAbsent(field, f -> new AtomicLong());
            if (!(val instanceof AtomicLong)) {
                entry.lock.lock();
                try {
                    if (!(val instanceof AtomicLong)) {
                        if (val == null) {
                            val = new AtomicLong();
                        } else {
                            val = new AtomicLong(((Number) val).longValue());
                        }
                        entry.mapValue.put(field, val);
                    }
                } finally {
                    entry.lock.unlock();
                }
            }
            return Double.longBitsToDouble(((AtomicLong) entry.mapValue.get(field)).addAndGet(Double.doubleToLongBits(num)));
        }, getExecutor());
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
        return supplyAsync(() -> {
            if (key == null) {
                return false;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.mapValue == null) {
                return false;
            }
            return entry.mapValue.contains(field);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return runAsync(() -> {
            hset(CacheEntryType.MAP, key, field, value);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Boolean> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return supplyAsync(() -> {
            return hsetnx(CacheEntryType.MAP, key, field, value);
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        return runAsync(() -> {
            for (int i = 0; i < values.length; i += 2) {
                hset(CacheEntryType.MAP, key, (String) values[i], values[i + 1]);
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Map map) {
        return runAsync(() -> {
            map.forEach((k, v) -> hset(CacheEntryType.MAP, key, (String) k, v));
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.mapValue == null) {
                return null;
            }
            List<T> rs = new ArrayList<>(fields.length);
            for (int i = 0; i < fields.length; i++) {
                Serializable val = (Serializable) entry.mapValue.get(fields[i]);
                if (type == String.class) {
                    rs.add(val == null ? null : (T) String.valueOf(val));
                } else {
                    rs.add((T) val);
                }
            }
            return rs;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hgetallAsync(final String key, final Type type) {
        return supplyAsync(() -> {
            return hgetall(CacheEntryType.MAP, key, type);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> hvalsAsync(final String key, final Type type) {
        return supplyAsync(() -> {
            return hvals(CacheEntryType.MAP, key, type);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hscanAsync(final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> {
            if (key == null) {
                return new HashMap();
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.mapValue == null) {
                return new HashMap();
            }
            if (Utility.isEmpty(pattern)) {
                return new HashMap(entry.mapValue);
            } else {
                Predicate<String> regx = Pattern.compile(pattern.replace("*", ".*")).asPredicate();
                Set<Map.Entry<String, T>> set = entry.mapValue.entrySet();
                return set.stream().filter(en -> regx.test(en.getKey())).collect(Collectors.toMap(en -> en.getKey(), en -> en.getValue()));
            }
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.mapValue == null) {
                return null;
            }
            Object obj = entry.mapValue.get(field);
            if (obj == null) {
                return null;
            }
            if (type == long.class || type == Long.class) {
                return (T) (obj instanceof Long ? obj : Long.parseLong(obj.toString()));
            }
            return (T) obj;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> hstrlenAsync(final String key, final String field) {
        return supplyAsync(() -> {
            if (key == null || field == null) {
                return 0L;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.mapValue == null) {
                return 0L;
            }
            Object obj = entry.mapValue.get(field);
            if (obj == null) {
                return 0L;
            }
            return (long) obj.toString().length();
        }, getExecutor());
    }

    //----------- hxxx --------------
    @Override
    public CompletableFuture<Boolean> existsAsync(String key) {
        return supplyAsync(() -> {
            if (key == null) {
                return false;
            }
            CacheEntry entry = container.get(key);
            if (entry == null) {
                return false;
            }
            return !entry.isExpired();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> getAsync(final String key, final Type type) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired()) {
                return null;
            }
            if (entry.isListCacheType()) {
                return (T) (entry.listValue == null ? null : new ArrayList(entry.listValue));
            }
            if (entry.isSetCacheType()) {
                return (T) (entry.csetValue == null ? null : new LinkedHashSet<>(entry.csetValue));
            }
            if (entry.cacheType == CacheEntryType.DOUBLE) {
                return (T) (Double) Double.longBitsToDouble(((AtomicLong) entry.objectValue).intValue());
            }
            Object obj = entry.objectValue;
            if (obj != null && obj.getClass() != type) {
                return (T) JsonConvert.root().convertFrom(type, JsonConvert.root().convertToBytes(obj));
            }
            return (T) obj;
        }, getExecutor());
    }

    //----------- hxxx --------------
    @Override
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired()) {
                return null;
            }
            entry.lastAccessed = System.currentTimeMillis();
            entry.expireSeconds = expireSeconds;
            if (entry.isListCacheType()) {
                return (T) (entry.listValue == null ? null : new ArrayList(entry.listValue));
            }
            if (entry.isSetCacheType()) {
                return (T) (entry.csetValue == null ? null : new HashSet(entry.csetValue));
            }
            return (T) entry.objectValue;
        }, getExecutor());
    }

    protected void set(CacheEntryType cacheType, String key, Object value) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, key, value, null, null, null);
            container.put(key, entry);
        } else {
            entry.expireSeconds = 0;
            entry.objectValue = value;
            entry.lastAccessed = System.currentTimeMillis();
        }
    }

    protected boolean setnx(CacheEntryType cacheType, String key, Object value) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, key, value, null, null, null);
            container.putIfAbsent(key, entry);
            return true;
        } else {
            entry.expireSeconds = 0;
            entry.lastAccessed = System.currentTimeMillis();
            return false;
        }
    }

    protected void hset(CacheEntryType cacheType, String key, String field, Object value) {
        if (key == null || value == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.MAP, key, value, null, null, new ConcurrentHashMap<>());
            container.put(key, entry);
            entry.mapValue.put(field, value);
        } else {
            entry.expireSeconds = 0;
            entry.mapValue.put(field, value);
            entry.lastAccessed = System.currentTimeMillis();
        }
    }

    protected boolean hsetnx(CacheEntryType cacheType, String key, String field, Object value) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.MAP, key, value, null, null, new ConcurrentHashMap<>());
            container.putIfAbsent(key, entry);
            entry.mapValue.putIfAbsent(field, value);
            return true;
        } else {
            entry.expireSeconds = 0;
            entry.lastAccessed = System.currentTimeMillis();
            return false;
        }
    }

    protected Map hgetall(CacheEntryType cacheType, String key, final Type type) {
        if (key == null) {
            return new LinkedHashMap();
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return new LinkedHashMap();
        } else if (type == long.class || type == Long.class) {
            Map map = new LinkedHashMap();
            entry.mapValue.forEach((k, v) -> {
                map.put(k, v instanceof Long ? v : (v == null ? null : Long.parseLong(v.toString())));
            });
            return map;
        } else {
            return new LinkedHashMap(entry.mapValue);
        }
    }

    protected List hvals(CacheEntryType cacheType, String key, final Type type) {
        if (key == null) {
            return new ArrayList();
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return new ArrayList();
        } else {
            if (type == long.class || type == Long.class) {
                return entry.mapValue.values().stream().map(v -> v instanceof Long ? v : (v == null ? null : Long.parseLong(v.toString()))).toList();
            } else {
                return new ArrayList(entry.mapValue.values());
            }
        }
    }

    @Override
    public CompletableFuture<Void> msetAsync(Serializable... keyVals) {
        return runAsync(() -> {
            if (keyVals.length % 2 != 0) {
                throw new SourceException("key value must be paired");
            }
            for (int i = 0; i < keyVals.length; i += 2) {
                String key = keyVals[i].toString();
                Object val = keyVals[i + 1];
                if (val instanceof String) {
                    set(CacheEntryType.STRING, key, val);
                } else if (val instanceof Number) {
                    set(CacheEntryType.LONG, key, ((Number) val).longValue());
                } else {
                    set(CacheEntryType.OBJECT, key, val);
                }
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> msetAsync(Map map) {
        return runAsync(() -> {
            map.forEach((key, val) -> {
                if (val instanceof String) {
                    set(CacheEntryType.STRING, (String) key, val);
                } else if (val instanceof Number) {
                    set(CacheEntryType.LONG, (String) key, ((Number) val).longValue());
                } else {
                    set(CacheEntryType.OBJECT, (String) key, val);
                }
            });
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value) {
        return runAsync(() -> {
            set(findEntryType(type), key, value);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxAsync(String key, Convert convert, Type type, T value) {
        return supplyAsync(() -> {
            return setnx(findEntryType(type), key, value);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return supplyAsync(() -> {
            return setnxex(findEntryType(type), expireSeconds, key, value);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> getSetAsync(String key, Convert convert, Type type, T value) {
        return supplyAsync(() -> {
            T old = get(key, type);
            set(findEntryType(type), key, value);
            return old;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> getDelAsync(String key, Type type) {
        return supplyAsync(() -> {
            CacheEntry entry = container.remove(key);
            return entry == null ? null : (T) entry.objectValue;
        }, getExecutor());
    }

    protected void set(CacheEntryType cacheType, int expireSeconds, String key, Object value) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) {
            entry = new CacheEntry(cacheType, expireSeconds, key, value, null, null, null);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) {
                entry.expireSeconds = expireSeconds;
            }
            entry.lastAccessed = System.currentTimeMillis();
            entry.objectValue = value;
        }
    }

    protected boolean setnxex(CacheEntryType cacheType, int expireSeconds, String key, Object value) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) {
            entry = new CacheEntry(cacheType, expireSeconds, key, value, null, null, null);
            container.put(key, entry);
            return true;
        } else {
            entry.expireSeconds = expireSeconds > 0 ? expireSeconds : 0;
            entry.lastAccessed = System.currentTimeMillis();
            return false;
        }
    }

    @Override
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return runAsync(() -> {
            set(findEntryType(type), expireSeconds, key, value);
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> expireAsync(String key, int expireSeconds) {
        return runAsync(() -> {
            if (key == null) {
                return;
            }
            CacheEntry entry = container.get(key);
            if (entry == null) {
                return;
            }
            entry.expireSeconds = expireSeconds;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> persistAsync(final String key) {
        return supplyAsync(() -> {
            if (key == null) {
                return false;
            }
            CacheEntry entry = container.get(key);
            if (entry == null) {
                return false;
            }
            if (entry.expireSeconds > 0) {
                entry.expireSeconds = 0;
                return true;
            } else {
                return false;
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey) {
        return supplyAsync(() -> {
            if (oldKey == null || newKey == null) {
                return false;
            }
            CacheEntry entry = container.get(oldKey);
            if (entry == null) {
                return false;
            }
            entry.key = newKey;
            container.put(newKey, entry);
            container.remove(oldKey);
            return true;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey) {
        return supplyAsync(() -> {
            if (oldKey == null || newKey == null) {
                return false;
            }
            if (container.containsKey(newKey)) {
                return false;
            }
            CacheEntry entry = container.get(oldKey);
            if (entry == null) {
                return false;
            }
            entry.key = newKey;
            container.put(newKey, entry);
            container.remove(oldKey);
            return true;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> delAsync(final String... keys) {
        return supplyAsync(() -> {
            if (keys == null) {
                return 0L;
            }
            long count = 0;
            for (String key : keys) {
                count += container.remove(key) == null ? 0 : 1;
            }
            return count;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return incrbyAsync(key, 1);
    }

    @Override
    public CompletableFuture<Long> incrbyAsync(final String key, long num) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null) {
                containerLock.lock();
                try {
                    entry = container.get(key);
                    if (entry == null) {
                        entry = new CacheEntry(CacheEntryType.ATOMIC, key, new AtomicLong(), null, null, null);
                        container.put(key, entry);
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            if (!(entry.objectValue instanceof AtomicLong)) {
                containerLock.lock();
                try {
                    if (!(entry.objectValue instanceof AtomicLong)) {
                        entry.objectValue = new AtomicLong(Long.parseLong(entry.objectValue.toString()));
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            return ((AtomicLong) entry.objectValue).addAndGet(num);
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Double> incrbyFloatAsync(final String key, double num) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null) {
                containerLock.lock();
                try {
                    entry = container.get(key);
                    if (entry == null) {
                        entry = new CacheEntry(CacheEntryType.DOUBLE, key, new AtomicLong(), null, null, null);
                        container.put(key, entry);
                    }
                } finally {
                    containerLock.unlock();
                }
            }
            Long v = ((AtomicLong) entry.objectValue).addAndGet(Double.doubleToLongBits(num));
            return Double.longBitsToDouble(v.intValue());
        }, getExecutor());
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
    public <T> CompletableFuture<List<T>> srandmemberAsync(String key, Type componentType, int count) {
        return supplyAsync(() -> {
            List<T> list = new ArrayList<>();
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return list;
            }
            List<T> vals = new ArrayList<>(entry.csetValue);
            if (count < 0) {  //可以重复
                for (int i = 0; i < Math.abs(count); i++) {
                    int index = ThreadLocalRandom.current().nextInt(vals.size());
                    T val = vals.get(index);
                    list.add(val);
                }
            } else { //不可以重复
                if (count >= vals.size()) {
                    return vals;
                }
                return vals.subList(0, count);
            }
            return list;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Boolean> smoveAsync(String key, String key2, Type componentType, T member) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return false;
            }
            boolean rs = entry.csetValue.remove(member);
            if (rs) {
                CacheEntry entry2 = container.get(key2);
                if (entry2 == null || entry2.csetValue == null) {
                    appendSetItem(componentType == String.class ? CacheEntryType.SET_STRING : CacheEntryType.SET_OBJECT, key2, List.of(member));
                } else {
                    entry2.csetValue.add(member);
                }
            }
            return rs;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sdiffAsync(final String key, final Type componentType, final String... key2s) {
        return supplyAsync(() -> {
            Set<T> rs = new HashSet<>();
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return rs;
            }
            rs.addAll(entry.csetValue);
            for (String k : key2s) {
                CacheEntry en2 = container.get(k);
                if (en2 != null && en2.csetValue != null) {
                    en2.csetValue.forEach(v -> rs.remove(v));
                }
            }
            return rs;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> sdiffstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyAsync(() -> {
            Set rs = sdiff(srcKey, Object.class, srcKey2s);
            if (container.containsKey(key)) {
                Set set = container.get(srcKey).csetValue;
                set.clear();
                set.addAll(rs);
            } else {
                appendSetItem(CacheEntryType.SET_OBJECT, key, rs);
            }
            return (long) rs.size();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sinterAsync(final String key, final Type componentType, final String... key2s) {
        return supplyAsync(() -> {
            Set<T> rs = new HashSet<>();
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return rs;
            }
            rs.addAll(entry.csetValue);
            for (String k : key2s) {
                CacheEntry en2 = container.get(k);
                if (en2 != null && en2.csetValue != null) {
                    Set<T> removes = new HashSet<>();
                    for (T v : rs) {
                        if (!en2.csetValue.contains(v)) {
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
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> sinterstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyAsync(() -> {
            Set rs = sinter(srcKey, Object.class, srcKey2s);
            if (container.containsKey(key)) {
                Set set = container.get(srcKey).csetValue;
                set.clear();
                set.addAll(rs);
            } else {
                appendSetItem(CacheEntryType.SET_OBJECT, key, rs);
            }
            return (long) rs.size();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sunionAsync(final String key, final Type componentType, final String... key2s) {
        return supplyAsync(() -> {
            Set<T> rs = new HashSet<>();
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return rs;
            }
            rs.addAll(entry.csetValue);
            for (String k : key2s) {
                CacheEntry en2 = container.get(k);
                if (en2 != null && en2.csetValue != null) {
                    rs.addAll(en2.csetValue);
                }
            }
            return rs;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> sunionstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyAsync(() -> {
            Set rs = sunion(srcKey, Object.class, srcKey2s);
            if (container.containsKey(key)) {
                Set set = container.get(srcKey).csetValue;
                set.clear();
                set.addAll(rs);
            } else {
                appendSetItem(CacheEntryType.SET_OBJECT, key, rs);
            }
            return (long) rs.size();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> smembersAsync(final String key, final Type componentType) {
        return getAsync(key, componentType);
    }

    @Override
    public <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType, int start, int stop) {
        return getAsync(key, componentType);
    }

    @Override
    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(final Type componentType, final String... keys) {
        return supplyAsync(() -> {
            Map<String, Set<T>> map = new HashMap<>();
            for (String key : keys) {
                Set<T> s = (Set<T>) get(key, componentType);
                if (s != null) {
                    map.put(key, s);
                }
            }
            return map;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<Boolean>> smismembersAsync(final String key, final String... members) {
        return supplyAsync(() -> {
            Set s = (Set) get(key, Object.class);
            List<Boolean> rs = new ArrayList<>();
            for (String member : members) {
                rs.add(s != null && s.contains(member));
            }
            return rs;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, List<T>>> lrangesAsync(final Type componentType, final String... keys) {
        return supplyAsync(() -> {
            Map<String, List<T>> map = new HashMap<>();
            for (String key : keys) {
                List<T> s = (List<T>) get(key, componentType);
                if (s != null) {
                    map.put(key, s);
                }
            }
            return map;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> mgetAsync(final Type componentType, final String... keys) {
        return supplyAsync(() -> {
            List<T> list = new ArrayList<>();
            for (String key : keys) {
                Object v = get(key, componentType);
                if (v != null) {
                    if (componentType == String.class) {
                        v = v.toString();
                    } else if (componentType == long.class || componentType == Long.class) {
                        v = (Object) ((Number) v).longValue();
                    }
                }
                list.add((T) v);
            }
            return list;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> llenAsync(final String key) {
        return supplyAsync(() -> {
            Collection collection = (Collection) get(key, Object.class);
            return collection == null ? 0L : collection.size();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> lindexAsync(String key, Type componentType, int index) {
        return supplyAsync(() -> {
            List<T> list = (List) get(key, Object.class);
            if (list == null || list.isEmpty()) {
                return null;
            }
            int pos = index >= 0 ? index : list.size() + index;
            return pos >= list.size() ? null : list.get(pos);
        }, getExecutor());
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
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
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
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, T... values) {
        return runAsync(() -> {
            appendSetItem(componentType == String.class ? CacheEntryType.SET_STRING : CacheEntryType.SET_OBJECT, key, List.of(values));
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> scardAsync(final String key) {
        return supplyAsync(() -> {
            Collection collection = (Collection) get(key, Object.class);
            return collection == null ? 0L : collection.size();
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type type, final T value) {
        return supplyAsync(() -> {
            Collection list = get(key, type);
            return list != null && list.contains(value);
        }, getExecutor());
    }

    protected void appendListItem(CacheEntryType cacheType, String key, Object... values) {
        appendListItem(cacheType, true, key, values);
    }

    protected void appendListItem(CacheEntryType cacheType, boolean tail, String key, Object... values) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
            ConcurrentLinkedDeque list = new ConcurrentLinkedDeque();
            entry = new CacheEntry(cacheType, key, null, null, list, null);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) {
                list = old.listValue;
            }
            if (list != null) {
                if (tail) {
                    list.addAll(List.of(values));
                } else {
                    for (Object v : values) {
                        list.addFirst(v);
                    }
                }
            }
        } else {
            if (tail) {
                entry.listValue.addAll(List.of(values));
            } else {
                for (Object v : values) {
                    entry.listValue.addFirst(v);
                }
            }
        }
    }

    @Override
    public <T> CompletableFuture<Void> lpushAsync(final String key, final Type componentType, T... values) {
        return runAsync(() -> {
            for (T value : values) {
                appendListItem(CacheEntryType.LIST_OBJECT, false, key, value);
            }
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> lpushxAsync(final String key, final Type componentType, T... values) {
        return runAsync(() -> {
            if (container.containsKey(key)) {
                for (T value : values) {
                    appendListItem(CacheEntryType.LIST_OBJECT, false, key, value);
                }
            }
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> lpopAsync(final String key, final Type componentType) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
                return null;
            }
            if (entry.listValue.isEmpty()) {
                return null;
            }
            Object obj = entry.listValue.pollFirst();
            if (obj != null && componentType == long.class) {
                obj = ((Number) obj).longValue();
            }
            return (T) obj;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> ltrimAsync(final String key, int start, int stop) {
        return runAsync(() -> {
            if (key == null) {
                return;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
                return;
            }
            if (entry.listValue.isEmpty()) {
                return;
            }
            Iterator it = entry.listValue.iterator();
            int index = -1;
            int end = stop >= 0 ? stop : entry.listValue.size() + stop;
            while (it.hasNext()) {
                ++index;
                if (index > end) {
                    break;
                } else if (index >= start) {
                    it.remove();
                }
            }
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> rpoplpushAsync(final String key, final String key2, final Type componentType) {
        return supplyAsync(() -> {
            T val = rpop(key, componentType);
            lpush(key2, componentType, val);
            return val;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> rpopAsync(final String key, final Type componentType) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
                return null;
            }
            if (entry.listValue.isEmpty()) {
                return null;
            }
            Object obj = entry.listValue.pollLast();
            if (obj != null && componentType == long.class) {
                obj = ((Number) obj).longValue();
            }
            return (T) obj;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> rpushxAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> {
            if (container.containsKey(key)) {
                for (T value : values) {
                    appendListItem(CacheEntryType.LIST_OBJECT, key, value);
                }
            }
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> {
            appendListItem(CacheEntryType.LIST_OBJECT, key, values);
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Long> lremAsync(final String key, final Type componentType, T value) {
        return supplyAsync(() -> {
            if (key == null) {
                return 0L;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.listValue == null) {
                return 0L;
            }
            return entry.listValue.remove(value) ? 1L : 0L;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> spopAsync(final String key, final Type componentType) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return null;
            }
            if (entry.csetValue.isEmpty()) {
                return null;
            }
            Iterator it = entry.csetValue.iterator();
            Object del = null;
            if (it.hasNext()) {
                Object obj = it.next();
                if (obj != null && componentType == long.class) {
                    obj = ((Number) obj).longValue();
                }
                del = obj;
            }
            if (del != null) {
                entry.csetValue.remove(del);
                return (T) del;
            }
            return null;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> spopAsync(final String key, final int count, final Type componentType) {
        return supplyAsync(() -> {
            if (key == null) {
                return new LinkedHashSet<>();
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return new LinkedHashSet<>();
            }
            if (entry.csetValue.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Iterator it = entry.csetValue.iterator();
            Set<T> list = new LinkedHashSet<>();
            int index = 0;
            while (it.hasNext()) {
                Object obj = it.next();
                if (obj != null && componentType == long.class) {
                    obj = ((Number) obj).longValue();
                }
                list.add((T) obj);
                if (++index >= count) {
                    break;
                }
            }
            entry.csetValue.removeAll(list);
            return list;
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> sscanAsync(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> {
            if (key == null) {
                return new LinkedHashSet();
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return new LinkedHashSet<>();
            }
            if (entry.csetValue.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Iterator it = entry.csetValue.iterator();
            Set<T> list = new LinkedHashSet<>();
            while (it.hasNext()) {
                Object obj = it.next();
                if (obj != null && componentType == long.class) {
                    obj = ((Number) obj).longValue();
                }
                list.add((T) obj);
            }
            return list;
        }, getExecutor());
    }

    protected void appendSetItem(CacheEntryType cacheType, String key, Collection<Object> values) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
            Set set = cacheType == CacheEntryType.SET_SORTED ? new ConcurrentSkipListSet<>() : new CopyOnWriteArraySet();
            entry = new CacheEntry(cacheType, key, null, set, null, null);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) {
                set = old.csetValue;
            }
            if (set != null) {
                set.addAll(values);
            }
        } else {
            entry.csetValue.addAll(values);
        }
    }

    @Override
    public CompletableFuture<Void> zaddAsync(String key, CacheScoredValue... values) {
        return runAsync(() -> {
            List<Object> list = new ArrayList<>();
            for (CacheScoredValue v : values) {
                list.add(new CacheScoredValue.NumberScoredValue(v));
            }
            appendSetItem(CacheEntryType.SET_SORTED, key, list);
        }, getExecutor());
    }

    @Override
    public <T extends Number> CompletableFuture<T> zincrbyAsync(String key, CacheScoredValue value) {
        return supplyAsync(() -> {
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.csetValue == null) {
                containerLock.lock();
                try {
                    entry = container.get(key);
                    if (entry == null || entry.isExpired()) {
                        appendSetItem(CacheEntryType.SET_SORTED, key, List.of(new CacheScoredValue.NumberScoredValue(value.getScore().doubleValue(), value.getValue())));
                    }
                } finally {
                    containerLock.unlock();
                }
                return (T) value.getScore();
            }
            entry.lock.lock();
            try {
                Set<CacheScoredValue.NumberScoredValue> sets = entry.csetValue;
                CacheScoredValue.NumberScoredValue old = sets.stream().filter(v -> Objects.equals(v.getValue(), value.getValue())).findAny().orElse(null);
                if (old == null) {
                    sets.add(new CacheScoredValue.NumberScoredValue(value.getScore().doubleValue(), value.getValue()));
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
                entry.lock.unlock();
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> zcardAsync(String key) {
        return supplyAsync(() -> {
            if (key == null) {
                return 0L;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return 0L;
            }
            return (long) entry.csetValue.size();
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> zrankAsync(String key, String member) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return null;
            }
            List<CacheScoredValue.NumberScoredValue> list = new ArrayList<>(entry.csetValue);
            Collections.sort(list);
            long c = 0;
            for (CacheScoredValue.NumberScoredValue v : list) {
                if (Objects.equals(v.getValue(), member)) {
                    return c;
                }
                c++;
            }
            return null;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> zrevrankAsync(String key, String member) {
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return null;
            }
            List<CacheScoredValue.NumberScoredValue> list = new ArrayList<>(entry.csetValue);
            Collections.sort(list, Collections.reverseOrder());
            long c = 0;
            for (CacheScoredValue.NumberScoredValue v : list) {
                if (Objects.equals(v.getValue(), member)) {
                    return c;
                }
                c++;
            }
            return null;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> zrangeAsync(String key, int start, int stop) {
        return supplyAsync(() -> {
            if (key == null) {
                return new ArrayList<>();
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return new ArrayList<>();
            }
            List<String> list = new ArrayList<>();
            Set<CacheScoredValue> sets = entry.csetValue;
            long c = 0;
            for (CacheScoredValue v : sets) {
                if (c >= start && (stop < 0 || c <= stop)) {
                    list.add(v.getValue());
                }
                c++;
            }
            return list;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<CacheScoredValue.NumberScoredValue>> zscanAsync(String key, Type scoreType, AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> {
            if (key == null) {
                return new ArrayList<>();
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.isExpired() || entry.csetValue == null) {
                return new ArrayList();
            }
            Set<CacheScoredValue.NumberScoredValue> sets = entry.csetValue;
            if (Utility.isEmpty(pattern)) {
                return sets.stream().collect(Collectors.toList());
            } else {
                Predicate<String> regx = Pattern.compile(pattern.replace("*", ".*")).asPredicate();
                return sets.stream().filter(en -> regx.test(en.getValue())).collect(Collectors.toList());
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> zremAsync(String key, String... members) {
        return supplyAsync(() -> {
            if (key == null) {
                return 0L;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return 0L;
            }
            Set<CacheScoredValue> sets = entry.csetValue;
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
        }, getExecutor());
    }

    @Override
    public <T extends Number> CompletableFuture<List<T>> zmscoreAsync(String key, Class<T> scoreType, String... members) {
        return supplyAsync(() -> {
            List<T> list = new ArrayList<>();
            if (key == null) {
                for (int i = 0; i < members.length; i++) {
                    list.add(null);
                }
                return list;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                for (int i = 0; i < members.length; i++) {
                    list.add(null);
                }
                return list;
            }
            Set<String> keys = Set.of(members);
            Set<CacheScoredValue> sets = entry.csetValue;
            Map<String, T> map = new HashMap<>();
            sets.stream().filter(v -> keys.contains(v.getValue())).forEach(v -> {
                map.put(v.getValue(), formatScore(scoreType, v.getScore()));
            });
            for (String m : members) {
                list.add(map.get(m));
            }
            return list;
        }, getExecutor());
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
        return supplyAsync(() -> {
            if (key == null) {
                return null;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
                return null;
            }
            Set<CacheScoredValue> sets = entry.csetValue;
            return formatScore(scoreType, sets.stream().filter(v -> Objects.equals(member, v.getValue())).findAny().map(v -> v.getScore()).orElse(null));
        }, getExecutor());
    }

    @Override
    public <T> CompletableFuture<Long> sremAsync(String key, Type type, T... values) {
        return supplyAsync(() -> {
            if (key == null) {
                return 0L;
            }
            CacheEntry entry = container.get(key);
            if (entry == null || entry.csetValue == null) {
                return 0L;
            }
            return entry.csetValue.removeAll(List.of(values)) ? 1L : 0L;
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Long> dbsizeAsync() {
        return supplyAsync(() -> {
            return (long) container.size();
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> flushdbAsync() {
        return runAsync(() -> {
            container.clear();
        }, getExecutor());
    }

    @Override
    public CompletableFuture<Void> flushallAsync() {
        return runAsync(() -> {
            container.clear();
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> keysAsync(String pattern) {
        return supplyAsync(() -> {
            if (pattern == null || pattern.isEmpty()) {
                return new ArrayList<>(container.keySet());
            } else {
                List<String> rs = new ArrayList<>();
                Predicate<String> filter = Pattern.compile(pattern).asPredicate();
                container.keySet().stream().filter(filter).forEach(x -> rs.add(x));
                return rs;
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> {
            if (pattern == null || pattern.isEmpty()) {
                return new ArrayList<>(container.keySet());
            } else {
                List<String> rs = new ArrayList<>();
                Predicate<String> filter = Pattern.compile(pattern).asPredicate();
                container.keySet().stream().filter(filter).forEach(x -> rs.add(x));
                return rs;
            }
        }, getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        if (startsWith == null) {
            return keysAsync();
        }
        return supplyAsync(() -> {
            List<String> rs = new ArrayList<>();
            container.keySet().stream().filter(x -> x.startsWith(startsWith)).forEach(x -> rs.add(x));
            return rs;
        }, getExecutor());
    }

    protected CacheEntryType findEntryType(Type type) {
        if (type == String.class) {
            return CacheEntryType.STRING;
        } else if (type == long.class || type == Long.class) {
            return CacheEntryType.LONG;
        } else if (type == byte[].class) {
            return CacheEntryType.BYTES;
        }
        return CacheEntryType.OBJECT;
    }

    public static enum CacheEntryType {
        LONG, STRING, OBJECT, BYTES, ATOMIC, MAP, DOUBLE,
        SET_LONG, SET_STRING, SET_OBJECT, SET_SORTED,
        LIST_LONG, LIST_STRING, LIST_OBJECT;
    }

    public static final class CacheEntry<T> {

        final CacheEntryType cacheType;

        String key;

        //<=0表示永久保存
        int expireSeconds;

        volatile long lastAccessed; //最后刷新时间

        T objectValue;

        ConcurrentHashMap<String, Serializable> mapValue;

        private final ReentrantLock lock = new ReentrantLock();

        Set<T> csetValue;

        ConcurrentLinkedDeque<T> listValue;

        public CacheEntry(CacheEntryType cacheType, String key, T objectValue, Set<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
            this(cacheType, 0, key, objectValue, csetValue, listValue, mapValue);
        }

        public CacheEntry(CacheEntryType cacheType, int expireSeconds, String key, T objectValue, Set<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
            this(cacheType, expireSeconds, System.currentTimeMillis(), key, objectValue, csetValue, listValue, mapValue);
        }

        @ConstructorParameters({"cacheType", "expireSeconds", "lastAccessed", "key", "objectValue", "csetValue", "listValue", "mapValue"})
        public CacheEntry(CacheEntryType cacheType, int expireSeconds, long lastAccessed, String key, T objectValue, Set<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
            this.cacheType = cacheType;
            this.expireSeconds = expireSeconds;
            this.lastAccessed = lastAccessed;
            this.key = key;
            this.objectValue = objectValue;
            this.csetValue = csetValue;
            this.listValue = listValue;
            this.mapValue = mapValue;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        @ConvertColumn(ignore = true)
        public boolean isListCacheType() {
            return cacheType == CacheEntryType.LIST_LONG || cacheType == CacheEntryType.LIST_STRING || cacheType == CacheEntryType.LIST_OBJECT;
        }

        @ConvertColumn(ignore = true)
        public boolean isSetCacheType() {
            return cacheType == CacheEntryType.SET_LONG || cacheType == CacheEntryType.SET_STRING || cacheType == CacheEntryType.SET_OBJECT || cacheType == CacheEntryType.SET_SORTED;
        }

        @ConvertColumn(ignore = true)
        public boolean isMapCacheType() {
            return cacheType == CacheEntryType.MAP;
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return expireSeconds > 0 && (lastAccessed + expireSeconds * 1000) < System.currentTimeMillis();
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

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public String getKey() {
            return key;
        }

        public T getObjectValue() {
            return objectValue;
        }

        public Set<T> getCsetValue() {
            return csetValue;
        }

        public ConcurrentLinkedDeque<T> getListValue() {
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
        return supplyAsync(() -> getexCollection(key, expireSeconds, componentType), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getexStringCollectionAsync(final String key, final int expireSeconds) {
        return supplyAsync(() -> getexStringCollection(key, expireSeconds), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(final String key, final int expireSeconds) {
        return supplyAsync(() -> getexLongCollection(key, expireSeconds), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(boolean set, Type componentType, String... keys) {
        return supplyAsync(() -> getCollectionMap(set, componentType, keys), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key) {
        return supplyAsync(() -> getStringCollection(key), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys) {
        return supplyAsync(() -> getStringCollectionMap(set, keys), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key) {
        return supplyAsync(() -> getLongCollection(key), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys) {
        return supplyAsync(() -> getLongCollectionMap(set, keys), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(String key, Type componentType) {
        return supplyAsync(() -> getCollection(key, componentType), getExecutor());
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
        return supplyAsync(() -> getLongMap(keys), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<Long[]> getLongArrayAsync(final String... keys) {
        return supplyAsync(() -> getLongArray(keys), getExecutor());
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
        return supplyAsync(() -> getStringMap(keys), getExecutor());
    }

    @Override
    @Deprecated(since = "2.8.0")
    public CompletableFuture<String[]> getStringArrayAsync(final String... keys) {
        return supplyAsync(() -> getStringArray(keys), getExecutor());
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
        return supplyAsync(() -> getMap(componentType, keys), getExecutor());
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
        return supplyAsync(() -> getCollectionSize(key), getExecutor());
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
