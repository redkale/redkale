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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import java.util.regex.Pattern;
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
        return config.getValue(CACHE_SOURCE_URL).startsWith("memory:");
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
                    int now = (int) (System.currentTimeMillis() / 1000);
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
    public long hdel(final String key, String... fields) {
        long count = 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) {
            return 0;
        }
        for (String field : fields) {
            if (entry.mapValue.remove(field) != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> hkeys(final String key) {
        List<String> list = new ArrayList<>();
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) {
            return list;
        }
        list.addAll(entry.mapValue.keySet());
        return list;
    }

    @Override
    public long hlen(final String key) {
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) {
            return 0;
        }
        return entry.mapValue.keySet().size();
    }

    @Override
    public long hincr(final String key, String field) {
        return hincrby(key, field, 1);
    }

    @Override
    public long hincrby(final String key, String field, long num) {
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
            entry.mapLock.lock();
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
                entry.mapLock.unlock();
            }
        }
        return ((AtomicLong) entry.mapValue.get(field)).addAndGet(num);
    }

    @Override
    public double hincrbyFloat(final String key, String field, double num) {
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
            entry.mapLock.lock();
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
                entry.mapLock.unlock();
            }
        }
        return Double.longBitsToDouble(((AtomicLong) entry.mapValue.get(field)).addAndGet(Double.doubleToLongBits(num)));
    }

    @Override
    public long hdecr(final String key, String field) {
        return hincrby(key, field, -1);
    }

    @Override
    public long hdecrby(final String key, String field, long num) {
        return hincrby(key, field, -num);
    }

    @Override
    public boolean hexists(final String key, String field) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) {
            return false;
        }
        return entry.mapValue.contains(field);
    }

    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public <T> boolean hsetnx(final String key, final String field, final Convert convert, final Type type, final T value) {
        return hsetnx(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public void hmset(final String key, final Serializable... values) {
        for (int i = 0; i < values.length; i += 2) {
            hset(CacheEntryType.MAP, key, (String) values[i], values[i + 1]);
        }
    }

    @Override
    public void hmset(final String key, final Map map) {
        map.forEach((k, v) -> hset(CacheEntryType.MAP, key, (String) k, v));
    }

    @Override
    public <T> List<T> hmget(final String key, final Type type, final String... fields) {
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
    }

    @Override
    public <T> Map<String, T> hgetall(final String key, final Type type) {
        return hgetall(CacheEntryType.MAP, key);
    }

    @Override
    public <T> List<T> hvals(final String key, final Type type) {
        return hvals(CacheEntryType.MAP, key);
    }

    @Override
    public <T> Map<String, T> hscan(final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        if (key == null) {
            return new HashMap();
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) {
            return new HashMap();
        }
        return new HashMap(entry.mapValue);
    }

    @Override
    public <T> T hget(final String key, final String field, final Type type) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) {
            return null;
        }
        return (T) entry.mapValue.get(field);
    }

    //----------- hxxx --------------
    @Override
    public boolean exists(String key) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return false;
        }
        return !entry.isExpired();
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(final String key) {
        return supplyAsync(() -> exists(key), getExecutor());
    }

    @Override
    public <T> T get(final String key, final Type type) {
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
            return (T) (entry.csetValue == null ? null : new HashSet(entry.csetValue));
        }
        return entry.cacheType == CacheEntryType.DOUBLE ? (T) (Double) Double.longBitsToDouble(((AtomicLong) entry.objectValue).intValue()) : (T) entry.objectValue;
    }

    //----------- hxxx --------------
    @Override
    public CompletableFuture<Long> hdelAsync(final String key, String... fields) {
        return supplyAsync(() -> hdel(key, fields), getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return supplyAsync(() -> hkeys(key), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hlenAsync(final String key) {
        return supplyAsync(() -> hlen(key), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return supplyAsync(() -> hincr(key, field), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrbyAsync(final String key, String field, long num) {
        return supplyAsync(() -> hincrby(key, field, num), getExecutor());
    }

    @Override
    public CompletableFuture<Double> hincrbyFloatAsync(final String key, String field, double num) {
        return supplyAsync(() -> hincrbyFloat(key, field, num), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field) {
        return supplyAsync(() -> hdecr(key, field), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hdecrbyAsync(final String key, String field, long num) {
        return supplyAsync(() -> hdecrby(key, field, num), getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> hexistsAsync(final String key, String field) {
        return supplyAsync(() -> hexists(key, field), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return runAsync(() -> hset(key, field, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Boolean> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return supplyAsync(() -> hsetnx(key, field, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        return runAsync(() -> hmset(key, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Map map) {
        return runAsync(() -> hmset(key, map), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields) {
        return supplyAsync(() -> hmget(key, type, fields), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hgetallAsync(final String key, final Type type) {
        return supplyAsync(() -> hgetall(key, type), getExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> hvalsAsync(final String key, final Type type) {
        return supplyAsync(() -> hvals(key, type), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hscanAsync(final String key, final Type type, AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> hscan(key, type, cursor, limit, pattern), getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return supplyAsync(() -> hget(key, field, type), getExecutor());
    }

    //----------- hxxx --------------
    @Override
    public <T> CompletableFuture<T> getAsync(final String key, final Type type) {
        return supplyAsync(() -> (T) get(key, type), getExecutor());
    }

    @Override
    public <T> T getex(final String key, final int expireSeconds, final Type type) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        if (entry.isListCacheType()) {
            return (T) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        }
        if (entry.isSetCacheType()) {
            return (T) (entry.csetValue == null ? null : new HashSet(entry.csetValue));
        }
        return (T) entry.objectValue;
    }

    @Override
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type) {
        return supplyAsync(() -> getex(key, expireSeconds, type), getExecutor());
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
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
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
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            return false;
        }
    }

    protected void hset(CacheEntryType cacheType, String key, String field, Object value) {
        if (key == null) {
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
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
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
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            return false;
        }
    }

    protected Map hgetall(CacheEntryType cacheType, String key) {
        if (key == null) {
            return new LinkedHashMap();
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return new LinkedHashMap();
        } else {
            return new LinkedHashMap(entry.mapValue);
        }
    }

    protected List hvals(CacheEntryType cacheType, String key) {
        if (key == null) {
            return new ArrayList();
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return new ArrayList();
        } else {
            return new ArrayList(entry.mapValue.values());
        }
    }

    @Override
    public void mset(Serializable... keyVals) {
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
    }

    @Override
    public void mset(Map map) {
        map.forEach((key, val) -> {
            if (val instanceof String) {
                set(CacheEntryType.STRING, (String) key, val);
            } else if (val instanceof Number) {
                set(CacheEntryType.LONG, (String) key, ((Number) val).longValue());
            } else {
                set(CacheEntryType.OBJECT, (String) key, val);
            }
        });
    }

    @Override
    public <T> void set(String key, Convert convert, Type type, T value) {
        set(findEntryType(type), key, value);
    }

    @Override
    public <T> boolean setnx(String key, Convert convert, Type type, T value) {
        return setnx(findEntryType(type), key, value);
    }

    @Override
    public <T> boolean setnxex(String key, int expireSeconds, Convert convert, Type type, T value) {
        return setnxex(findEntryType(type), expireSeconds, key, value);
    }

    @Override
    public <T> T getSet(String key, Convert convert, Type type, T value) {
        T old = get(key, type);
        set(findEntryType(type), key, value);
        return old;
    }

    @Override
    public CompletableFuture<Void> msetAsync(final Serializable... keyVals) {
        return runAsync(() -> mset(keyVals), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> msetAsync(final Map map) {
        return runAsync(() -> mset(map), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value) {
        return runAsync(() -> set(key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxAsync(String key, Convert convert, Type type, T value) {
        return supplyAsync(() -> setnx(key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<T> getSetAsync(String key, Convert convert, Type type, T value) {
        return runAsync(() -> getSet(key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    protected void set(CacheEntryType cacheType, int expireSeconds, String key, Object value) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, expireSeconds, key, value, null, null, null);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) {
                entry.expireSeconds = expireSeconds;
            }
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            entry.objectValue = value;
        }
    }

    protected boolean setnxex(CacheEntryType cacheType, int expireSeconds, String key, Object value) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, expireSeconds, key, value, null, null, null);
            container.putIfAbsent(key, entry);
            return true;
        } else {
            if (expireSeconds > 0) {
                entry.expireSeconds = expireSeconds;
            }
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            return false;
        }
    }

    @Override
    public <T> void setex(String key, int expireSeconds, Convert convert, Type type, T value) {
        set(findEntryType(type), expireSeconds, key, value);
    }

    @Override
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Type type, T value) {
        return runAsync(() -> setex(key, expireSeconds, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Convert convert, Type type, T value) {
        return runAsync(() -> setex(key, expireSeconds, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxexAsync(final String key, final int expireSeconds, final Type type, final T value) {
        return supplyAsync(() -> setnxex(key, expireSeconds, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Boolean> setnxexAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value) {
        return supplyAsync(() -> setnxex(key, expireSeconds, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public void expire(String key, int expireSeconds) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return;
        }
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public boolean persist(final String key) {
        if (key == null) {
            return false;
        }
        CacheEntry entry = container.get(key);
        if (entry == null) {
            return false;
        }
        entry.expireSeconds = 0;
        return true;
    }

    @Override
    public boolean rename(String oldKey, String newKey) {
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
    }

    @Override
    public boolean renamenx(String oldKey, String newKey) {
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
    }

    @Override
    public CompletableFuture<Void> expireAsync(final String key, final int expireSeconds) {
        return runAsync(() -> expire(key, expireSeconds), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Boolean> persistAsync(String key) {
        return supplyAsync(() -> persist(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey) {
        return supplyAsync(() -> rename(oldKey, newKey), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey) {
        return supplyAsync(() -> renamenx(oldKey, newKey), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long del(final String... keys) {
        if (keys == null) {
            return 0;
        }
        int count = 0;
        for (String key : keys) {
            count += container.remove(key) == null ? 0 : 1;
        }
        return count;
    }

    @Override
    public long incr(final String key) {
        return incrby(key, 1);
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return supplyAsync(() -> incr(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long incrby(final String key, long num) {
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
        return ((AtomicLong) entry.objectValue).addAndGet(num);
    }

    @Override
    public double incrbyFloat(final String key, double num) {
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
    }

    @Override
    public CompletableFuture<Long> incrbyAsync(final String key, long num) {
        return supplyAsync(() -> incrby(key, num), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Double> incrbyFloatAsync(final String key, double num) {
        return supplyAsync(() -> incrbyFloat(key, num), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long decr(final String key) {
        return incrby(key, -1);
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key) {
        return supplyAsync(() -> decr(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long decrby(final String key, long num) {
        return incrby(key, -num);
    }

    @Override
    public CompletableFuture<Long> decrbyAsync(final String key, long num) {
        return supplyAsync(() -> decrby(key, num), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Long> delAsync(final String... keys) {
        return supplyAsync(() -> del(keys), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Set<T>> sdiffAsync(final String key, final Type componentType, final String... key2s) {
        return supplyAsync(() -> sdiff(key, componentType, key2s), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Long> sdiffstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyAsync(() -> sdiffstore(key, srcKey, srcKey2s), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<List<Boolean>> smismembersAsync(final String key, final String... members) {
        return supplyAsync(() -> smismembers(key, members), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> Set<T> sdiff(final String key, final Type componentType, final String... key2s) {
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
    }

    @Override
    public long sdiffstore(final String key, final String srcKey, final String... srcKey2s) {
        Set rs = sdiff(srcKey, Object.class, srcKey2s);
        if (container.containsKey(key)) {
            CopyOnWriteArraySet set = container.get(srcKey).csetValue;
            set.clear();
            set.addAll(rs);
        } else {
            appendSetItem(CacheEntryType.OBJECT_SET, key, rs);
        }
        return rs.size();
    }

    @Override
    public <T> CompletableFuture<Set<T>> sinterAsync(final String key, final Type componentType, final String... key2s) {
        return supplyAsync(() -> sinter(key, componentType, key2s), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Long> sinterstoreAsync(final String key, final String srcKey, final String... srcKey2s) {
        return supplyAsync(() -> sinterstore(key, srcKey, srcKey2s), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> Set<T> sinter(final String key, final Type componentType, final String... key2s) {
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
    }

    @Override
    public long sinterstore(final String key, final String srcKey, final String... srcKey2s) {
        Set rs = sinter(srcKey, Object.class, srcKey2s);
        if (container.containsKey(key)) {
            CopyOnWriteArraySet set = container.get(srcKey).csetValue;
            set.clear();
            set.addAll(rs);
        } else {
            appendSetItem(CacheEntryType.OBJECT_SET, key, rs);
        }
        return rs.size();
    }

    @Override
    public <T> Set<T> smembers(final String key, final Type componentType) {
        return (Set<T>) get(key, componentType);
    }

    @Override
    public <T> List<T> lrange(final String key, final Type componentType, int start, int stop) {
        return (List<T>) get(key, componentType);
    }

    @Override
    public <T> Map<String, Set<T>> smembers(final Type componentType, final String... keys) {
        Map<String, Set<T>> map = new HashMap<>();
        for (String key : keys) {
            Set<T> s = (Set<T>) get(key, componentType);
            if (s != null) {
                map.put(key, s);
            }
        }
        return map;
    }

    @Override
    public List<Boolean> smismembers(final String key, final String... members) {
        Set s = (Set) get(key, Object.class);
        List<Boolean> rs = new ArrayList<>();
        for (String member : members) {
            rs.add(s != null && s.contains(member));
        }
        return rs;
    }

    @Override
    public <T> Map<String, List<T>> lrange(final Type componentType, final String... keys) {
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
    public <T> Map<String, T> mget(final Type componentType, final String... keys) {
        Map<String, T> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, (T) get(key, componentType));
        }
        return map;
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> mgetAsync(final Type componentType, final String... keys) {
        return CompletableFuture.completedFuture(mget(componentType, keys));
    }

    @Override
    public <T> CompletableFuture<Map<String, List<T>>> lrangeAsync(Type componentType, String... keys) {
        return supplyAsync(() -> lrange(componentType, keys), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(Type componentType, String... keys) {
        return supplyAsync(() -> smembers(componentType, keys), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Set<T>> smembersAsync(String key, Type componentType) {
        return supplyAsync(() -> smembers(key, componentType), getExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> lrangeAsync(String key, Type componentType, int start, int stop) {
        return supplyAsync(() -> lrange(key, componentType, start, stop), getExecutor());
    }

    @Override
    public long llen(final String key) {
        Collection collection = (Collection) get(key, Object.class);
        return collection == null ? 0 : collection.size();
    }

    @Override
    public long scard(final String key) {
        Collection collection = (Collection) get(key, Object.class);
        return collection == null ? 0 : collection.size();
    }

    @Override
    public CompletableFuture<Long> llenAsync(final String key) {
        return supplyAsync(() -> llen(key), getExecutor());
    }

    @Override
    public CompletableFuture<Long> scardAsync(final String key) {
        return supplyAsync(() -> scard(key), getExecutor());
    }

    @Override
    public <T> boolean sismember(final String key, final Type type, final T value) {
        Collection list = get(key, type);
        return list != null && list.contains(value);
    }

    @Override
    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type type, final T value) {
        return supplyAsync(() -> sismember(key, type, value), getExecutor());
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
    public <T> void lpush(final String key, final Type componentType, T... values) {
        for (T value : values) {
            appendListItem(CacheEntryType.OBJECT_LIST, false, key, value);
        }
    }

    @Override
    public <T> CompletableFuture<Void> lpushAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> lpush(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> void lpushx(final String key, final Type componentType, T... values) {
        if (container.containsKey(key)) {
            for (T value : values) {
                appendListItem(CacheEntryType.OBJECT_LIST, false, key, value);
            }
        }
    }

    @Override
    public <T> CompletableFuture<Void> lpushxAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> lpushx(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> T lpop(final String key, final Type componentType) {
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
    }

    @Override
    public void ltrim(final String key, int start, int stop) {
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
    }

    @Override
    public CompletableFuture<Void> ltrimAsync(final String key, int start, int stop) {
        return runAsync(() -> ltrim(key, start, stop), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<T> lpopAsync(final String key, final Type componentType) {
        return supplyAsync(() -> lpop(key, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> T rpoplpush(final String list1, final String list2, final Type componentType) {
        T val = rpop(list1, componentType);
        lpush(list2, componentType, val);
        return val;
    }

    @Override
    public <T> CompletableFuture<T> rpoplpushAsync(final String key, final String key2, final Type componentType) {
        return supplyAsync(() -> rpoplpush(key, key2, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> T rpop(final String key, final Type componentType) {
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
    }

    @Override
    public <T> CompletableFuture<T> rpopAsync(final String key, final Type componentType) {
        return supplyAsync(() -> rpop(key, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> void rpushx(String key, Type componentType, T... values) {
        if (container.containsKey(key)) {
            for (T value : values) {
                appendListItem(CacheEntryType.OBJECT_LIST, key, value);
            }
        }
    }

    @Override
    public <T> CompletableFuture<Void> rpushxAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> rpushx(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> void rpush(String key, Type componentType, T... values) {
        appendListItem(CacheEntryType.OBJECT_LIST, key, values);
    }

    @Override
    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T... values) {
        return runAsync(() -> rpush(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> int lrem(String key, final Type componentType, T value) {
        if (key == null) {
            return 0;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) {
            return 0;
        }
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public int lremString(String key, String value) {
        if (key == null) {
            return 0;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) {
            return 0;
        }
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public int lremLong(String key, long value) {
        if (key == null) {
            return 0;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) {
            return 0;
        }
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public <T> CompletableFuture<Integer> lremAsync(final String key, final Type componentType, T value) {
        return supplyAsync(() -> lrem(key, componentType, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> T spop(final String key, final Type componentType) {
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
        if (it.hasNext()) {
            Object obj = it.next();
            if (obj != null && componentType == long.class) {
                obj = ((Number) obj).longValue();
            }
            it.remove();
            return (T) obj;
        }
        return null;
    }

    @Override
    public <T> Set<T> spop(final String key, final int count, final Type componentType) {
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
            it.remove();
            if (++index >= count) {
                break;
            }
        }
        return list;
    }

    @Override
    public <T> Set< T> sscan(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
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
        int index = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj != null && componentType == long.class) {
                obj = ((Number) obj).longValue();
            }
            list.add((T) obj);
            it.remove();
            if (limit > 0 && ++index >= limit) {
                break;
            }
        }
        return list;
    }

    protected void appendSetItem(CacheEntryType cacheType, String key, Collection<Object> values) {
        if (key == null) {
            return;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
            CopyOnWriteArraySet set = new CopyOnWriteArraySet();
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
    public <T> void sadd(String key, final Type componentType, T... values) {
        appendSetItem(CacheEntryType.OBJECT_SET, key, List.of(values));
    }

    @Override
    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, T... values) {
        return runAsync(() -> sadd(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> long srem(String key, Type type, T... values) {
        if (key == null) {
            return 0;
        }
        CacheEntry entry = container.get(key);
        if (entry == null || entry.csetValue == null) {
            return 0;
        }
        return entry.csetValue.removeAll(List.of(values)) ? 1 : 0;
    }

    @Override
    public <T> CompletableFuture<Long> sremAsync(final String key, final Type componentType, final T... values) {
        return supplyAsync(() -> srem(key, componentType, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long dbsize() {
        return container.size();
    }

    @Override
    public void flushdb() {
        container.clear();
    }

    @Override
    public CompletableFuture<Void> flushdbAsync() {
        return runAsync(() -> flushdb(), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public void flushall() {
        container.clear();
    }

    @Override
    public CompletableFuture<Void> flushallAsync() {
        return runAsync(() -> flushall(), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public List<String> keys(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new ArrayList<>(container.keySet());
        } else {
            List<String> rs = new ArrayList<>();
            Predicate<String> filter = Pattern.compile(pattern).asPredicate();
            container.keySet().stream().filter(filter).forEach(x -> rs.add(x));
            return rs;
        }
    }

    @Override
    public List<String> scan(AtomicLong cursor, int limit, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new ArrayList<>(container.keySet());
        } else {
            List<String> rs = new ArrayList<>();
            Predicate<String> filter = Pattern.compile(pattern).asPredicate();
            container.keySet().stream().filter(filter).forEach(x -> rs.add(x));
            return rs;
        }
    }

    @Override
    public List<String> keysStartsWith(String startsWith) {
        if (startsWith == null) {
            return keys();
        }
        List<String> rs = new ArrayList<>();
        container.keySet().stream().filter(x -> x.startsWith(startsWith)).forEach(x -> rs.add(x));
        return rs;
    }

    @Override
    public CompletableFuture<List<String>> keysAsync(String pattern) {
        return CompletableFuture.completedFuture(keys(pattern));
    }

    @Override
    public CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        return CompletableFuture.completedFuture(keysStartsWith(startsWith));
    }

    @Override
    public CompletableFuture<Long> dbsizeAsync() {
        return CompletableFuture.completedFuture((long) container.size());
    }

    @Override
    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> scan(cursor, limit, pattern), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<T> spopAsync(String key, Type componentType) {
        return supplyAsync(() -> spop(key, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Set<T>> spopAsync(String key, int count, Type componentType) {
        return supplyAsync(() -> spop(key, count, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Set<T>> sscanAsync(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern) {
        return supplyAsync(() -> sscan(key, componentType, cursor, limit, pattern), getExecutor()).whenComplete(futureCompleteConsumer);
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
        LONG_SET, STRING_SET, OBJECT_SET,
        LONG_LIST, STRING_LIST, OBJECT_LIST;
    }

    public static final class CacheEntry<T> {

        final CacheEntryType cacheType;

        String key;

        //<=0表示永久保存
        int expireSeconds;

        volatile int lastAccessed; //最后刷新时间

        T objectValue;

        ConcurrentHashMap<String, Serializable> mapValue;

        final ReentrantLock mapLock = new ReentrantLock();

        CopyOnWriteArraySet<T> csetValue;

        ConcurrentLinkedDeque<T> listValue;

        public CacheEntry(CacheEntryType cacheType, String key, T objectValue, CopyOnWriteArraySet<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
            this(cacheType, 0, key, objectValue, csetValue, listValue, mapValue);
        }

        public CacheEntry(CacheEntryType cacheType, int expireSeconds, String key, T objectValue, CopyOnWriteArraySet<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
            this(cacheType, expireSeconds, (int) (System.currentTimeMillis() / 1000), key, objectValue, csetValue, listValue, mapValue);
        }

        @ConstructorParameters({"cacheType", "expireSeconds", "lastAccessed", "key", "objectValue", "csetValue", "listValue", "mapValue"})
        public CacheEntry(CacheEntryType cacheType, int expireSeconds, int lastAccessed, String key, T objectValue, CopyOnWriteArraySet<T> csetValue, ConcurrentLinkedDeque<T> listValue, ConcurrentHashMap<String, Serializable> mapValue) {
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
            return cacheType == CacheEntryType.LONG_LIST || cacheType == CacheEntryType.STRING_LIST || cacheType == CacheEntryType.OBJECT_LIST;
        }

        @ConvertColumn(ignore = true)
        public boolean isSetCacheType() {
            return cacheType == CacheEntryType.LONG_SET || cacheType == CacheEntryType.STRING_SET || cacheType == CacheEntryType.OBJECT_SET;
        }

        @ConvertColumn(ignore = true)
        public boolean isMapCacheType() {
            return cacheType == CacheEntryType.MAP;
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return (expireSeconds > 0 && lastAccessed + expireSeconds < (System.currentTimeMillis() / 1000));
        }

        public CacheEntryType getCacheType() {
            return cacheType;
        }

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public int getLastAccessed() {
            return lastAccessed;
        }

        public String getKey() {
            return key;
        }

        public T getObjectValue() {
            return objectValue;
        }

        public CopyOnWriteArraySet<T> getCsetValue() {
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
