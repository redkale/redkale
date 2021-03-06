/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.convert.Convert;
import org.redkale.convert.json.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * CacheSource的默认实现--内存缓存
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
public final class CacheMemorySource extends AbstractService implements CacheSource, Service, AutoCloseable, Resourcable {

    private static final Type STRING_ENTRY_TYPE = new TypeToken<CacheEntry<String>>() {
    }.getType();

    private static final Type LONG_ENTRY_TYPE = new TypeToken<CacheEntry<Long>>() {
    }.getType();

    private static final Type ATOMIC_ENTRY_TYPE = new TypeToken<CacheEntry<AtomicLong>>() {
    }.getType();

    private static final Type MAP_ENTRY_TYPE = new TypeToken<CacheEntry<ConcurrentHashMap>>() {
    }.getType();

    @Resource(name = "APP_HOME")
    private File home;

    @Resource
    private JsonConvert defaultConvert;

    @Resource(name = "$_convert")
    private JsonConvert convert;

    private boolean needStore;

    private Type objValueType;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<String, CacheEntry<Object>> container = new ConcurrentHashMap<>();

    protected final BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) logger.log(Level.SEVERE, "CompletableFuture complete error", (Throwable) t);
    };

    public CacheMemorySource() {
    }

    @Override
    public final String getType() {
        return "memory";
    }

    @Override
    public String toString() {
        return "CacheMemorySource(type=memory)";
    }

    @Override //ServiceLoader时判断配置是否符合当前实现类
    public boolean match(AnyValue config) {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(AnyValue conf) {
        if (this.convert == null) this.convert = this.defaultConvert;
        if (this.convert == null) this.convert = JsonConvert.root();
        final CacheMemorySource self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("properties");
        String expireHandlerClass = prop == null ? null : prop.getValue("expirehandler");
        if (expireHandlerClass != null) {
            try {
                this.expireHandler = (Consumer<CacheEntry>) Thread.currentThread().getContextClassLoader().loadClass(expireHandlerClass).getDeclaredConstructor().newInstance();
            } catch (Throwable e) {
                logger.log(Level.SEVERE, self.getClass().getSimpleName() + " new expirehandler class (" + expireHandlerClass + ") instance error", e);
            }
        }
        if (scheduler == null) {
            this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                final Thread t = new Thread(r, "Redkale-" + self.getClass().getSimpleName() + "-Expirer-Thread");
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
                        if (expireHandler != null && entry != null) expireHandler.accept(entry);
                    }
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "CacheMemorySource schedule(interval=" + 10 + "s) error", t);
                }
            }, 10, 10, TimeUnit.SECONDS);
            if (logger.isLoggable(Level.FINEST)) logger.finest(self.getClass().getSimpleName() + ":" + self.resourceName() + " start schedule expire executor");
        }
        if (Sncp.isRemote(self)) return;

        boolean datasync = false; //是否从远程同步过数据
        //----------同步数据……-----------
        // TODO
        if (this.needStore && false) { //不存储
            try {
                File store = home == null ? new File("cache/" + resourceName()) : new File(home, "cache/" + resourceName());
                if (!store.isFile() || !store.canRead()) return;
                LineNumberReader reader = new LineNumberReader(new FileReader(store));
                if (this.objValueType == null) this.objValueType = Object.class;
                final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    Type convertType = storeObjType;
                    if (line.startsWith("{\"cacheType\":\"" + CacheEntryType.LONG)) {
                        convertType = LONG_ENTRY_TYPE;
                    } else if (line.startsWith("{\"cacheType\":\"" + CacheEntryType.STRING)) {
                        convertType = STRING_ENTRY_TYPE;
                    } else if (line.startsWith("{\"cacheType\":\"" + CacheEntryType.ATOMIC)) {
                        convertType = ATOMIC_ENTRY_TYPE;
                    } else {
                        continue;
                    }
                    CacheEntry<Object> entry = convert.convertFrom(convertType, line);
                    if (entry.isExpired()) continue;
                    if (datasync && container.containsKey(entry.key)) continue; //已经同步了
                    container.put(entry.key, entry);
                }
                reader.close();
                store.delete();
            } catch (Exception e) {
                logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + resourceName() + ") load store file error ", e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
        conf.addValue("node", new AnyValue.DefaultAnyValue().addValue("addr", "127.0.0.1").addValue("port", "6379"));

        CacheMemorySource source = new CacheMemorySource();
        source.defaultConvert = JsonFactory.root().getConvert();

        source.init(conf);

        System.out.println("------------------------------------");
        source.remove("key1");
        source.remove("key2");
        source.remove("300");
        source.setString("key1", "value1");
        source.setString("keystr1", "strvalue1");
        source.setLong("keylong1", 333L);
        source.setString("300", "4000");
        source.getStringAndRefresh("key1", 3500);
        System.out.println("[有值] 300 GET : " + source.get("300", String.class));
        System.out.println("[有值] key1 GET : " + source.get("key1", String.class));
        System.out.println("[无值] key2 GET : " + source.get("key2", String.class));
        System.out.println("[有值] keylong1 GET : " + source.getLong("keylong1", 0L));
        System.out.println("[有值] key1 EXISTS : " + source.exists("key1"));
        System.out.println("[无值] key2 EXISTS : " + source.exists("key2"));

        source.remove("keys3");
        source.appendStringListItem("keys3", "vals1");
        source.appendStringListItem("keys3", "vals2");
        System.out.println("-------- keys3 追加了两个值 --------");
        System.out.println("[两值] keys3 VALUES : " + source.getStringCollection("keys3"));
        System.out.println("[有值] keys3 EXISTS : " + source.exists("keys3"));
        source.removeStringListItem("keys3", "vals1");
        System.out.println("[一值] keys3 VALUES : " + source.getStringCollection("keys3"));
        source.getStringCollectionAndRefresh("keys3", 3000);

        source.remove("sets3");
        source.appendStringSetItem("sets3", "setvals1");
        source.appendStringSetItem("sets3", "setvals2");
        source.appendStringSetItem("sets3", "setvals1");
        System.out.println("[两值] sets3 VALUES : " + source.getStringCollection("sets3"));
        System.out.println("[有值] sets3 EXISTS : " + source.exists("sets3"));
        source.removeStringSetItem("sets3", "setvals1");
        System.out.println("[一值] sets3 VALUES : " + source.getStringCollection("sets3"));
        System.out.println("sets3 大小 : " + source.getCollectionSize("sets3"));
        System.out.println("all keys: " + source.queryKeys());
        System.out.println("newnum 值 : " + source.incr("newnum"));
        System.out.println("newnum 值 : " + source.decr("newnum"));

        source.remove("hmap1");
        source.hincr("map", "key1");
        System.out.println("map.key1 值 : " + source.hgetLong("map", "key1", -1));
        source.hmset("map", "key2", "haha", "key3", 333);
        System.out.println("map.[key1,key2,key3] 值 : " + source.hmget("map", String.class, "key1", "key2", "key3"));

        System.out.println("------------------------------------");
        source.destroy(null);
        source.init(null);
        System.out.println("all keys: " + source.queryKeys());
        System.out.println("[有值] keylong1 GET : " + source.getLong("keylong1", 0L));
    }

    @Override
    public void close() throws Exception {  //给Application 关闭时调用
        destroy(null);
    }

    @Override
    public String resourceName() {
        Resource res = this.getClass().getAnnotation(Resource.class);
        return res == null ? "cachememory" : res.name();
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
        if (!this.needStore || Sncp.isRemote(this) || container.isEmpty()) return;
        if (true) return; //不存储
        try {
            File store = new File(home, "cache/" + resourceName());
            store.getParentFile().mkdirs();
            PrintStream stream = new PrintStream(store, "UTF-8");
            final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);
            final Type storeSetType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);
            final Type storeListType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);
            Collection<CacheEntry<Object>> entrys = container.values();
            for (CacheEntry entry : entrys) {
                Type convertType = storeObjType;
                if (entry.cacheType == CacheEntryType.LONG) {
                    convertType = LONG_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.STRING) {
                    convertType = STRING_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.ATOMIC) {
                    convertType = ATOMIC_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.OBJECT) {
                    convertType = storeObjType;
                } else if (entry.cacheType == CacheEntryType.LONG_LIST) {
                    convertType = LONG_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.LONG_SET) {
                    convertType = LONG_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.STRING_LIST) {
                    convertType = STRING_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.STRING_SET) {
                    convertType = STRING_ENTRY_TYPE;
                } else if (entry.cacheType == CacheEntryType.OBJECT_LIST) {
                    convertType = storeListType;
                } else if (entry.cacheType == CacheEntryType.OBJECT_SET) {
                    convertType = storeSetType;
                } else if (entry.cacheType == CacheEntryType.BYTES) {
                    convertType = byte[].class;
                }
                try {
                    stream.println(convert.convertTo(convertType, entry));
                } catch (Exception ee) {
                    System.err.println(storeSetType + "-----" + entry);
                    throw ee;
                }
            }
            container.clear();
            stream.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + resourceName() + ") store to file error ", e);
        }
    }
    
    //----------- hxxx --------------

    @Override
    public int hremove(final String key, String... fields) {
        int count = 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) return 0;
        for (String field : fields) {
            if (entry.mapValue.remove(field) != null) count++;
        }
        return count;
    }

    @Override
    public List<String> hkeys(final String key) {
        List<String> list = new ArrayList<>();
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) return list;
        list.addAll(entry.mapValue.keySet());
        return list;
    }

    @Override
    public int hsize(final String key) {
        CacheEntry entry = container.get(key);
        if (entry == null || entry.mapValue == null) return 0;
        return entry.mapValue.keySet().size();
    }

    @Override
    public long hincr(final String key, String field) {
        return hincr(key, field, 1);
    }

    @Override
    public long hincr(final String key, String field, long num) {
        CacheEntry entry = container.get(key);
        if (entry == null) {
            synchronized (container) {
                entry = container.get(key);
                if (entry == null) {
                    ConcurrentHashMap<String, Serializable> map = new ConcurrentHashMap();
                    map.put(field, new AtomicLong());
                    entry = new CacheEntry(CacheEntryType.MAP, key, new AtomicLong(), null, null, map);
                    container.put(key, entry);
                }
            }
        }
        Serializable val = (Serializable) entry.mapValue.computeIfAbsent(field, f -> new AtomicLong());
        if (!(val instanceof AtomicLong)) {
            synchronized (entry.mapValue) {
                if (!(val instanceof AtomicLong)) {
                    if (val == null) {
                        val = new AtomicLong();
                    } else {
                        val = new AtomicLong(((Number) val).longValue());
                    }
                    entry.mapValue.put(field, val);
                }
            }
        }
        return ((AtomicLong) entry.mapValue.get(field)).addAndGet(num);
    }

    @Override
    public long hdecr(final String key, String field) {
        return hincr(key, field, -1);
    }

    @Override
    public long hdecr(final String key, String field, long num) {
        return hincr(key, field, -num);
    }

    @Override
    public boolean hexists(final String key, String field) {
        if (key == null) return false;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return false;
        return entry.mapValue.contains(field);
    }

    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final T value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public <T> void hset(final String key, final String field, final Type type, final T value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public void hsetString(final String key, final String field, final String value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public void hsetLong(final String key, final String field, final long value) {
        hset(CacheEntryType.MAP, key, field, value);
    }

    @Override
    public void hmset(final String key, final Serializable... values) {
        for (int i = 0; i < values.length; i += 2) {
            hset(CacheEntryType.MAP, key, (String) values[i], values[i + 1]);
        }
    }

    @Override
    public <T> List<T> hmget(final String key, final Type type, final String... fields) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return null;
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
    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit) {
        return hmap(key, type, offset, limit, null);
    }

    @Override
    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit, String pattern) {
        if (key == null) return new HashMap();
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return new HashMap();
        return new HashMap(entry.mapValue);
    }

    @Override
    public <T> T hget(final String key, final String field, final Type type) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return null;
        return (T) entry.mapValue.get(field);
    }

    @Override
    public String hgetString(final String key, final String field) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return null;
        return (String) entry.mapValue.get(field);
    }

    @Override
    public long hgetLong(final String key, final String field, long defValue) {
        if (key == null) return defValue;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.mapValue == null) return defValue;
        return ((Number) entry.mapValue.getOrDefault(field, defValue)).longValue();
    }
    //----------- hxxx --------------

    @Override
    public boolean exists(String key) {
        if (key == null) return false;
        CacheEntry entry = container.get(key);
        if (entry == null) return false;
        return !entry.isExpired();
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> exists(key), getExecutor());
    }

    @Override
    public <T> T get(final String key, final Type type) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        if (entry.isListCacheType()) return (T) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        if (entry.isSetCacheType()) return (T) (entry.csetValue == null ? null : new HashSet(entry.csetValue));
        return (T) entry.objectValue;
    }

    @Override
    public String getString(String key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        return (String) entry.objectValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        if (key == null) return defValue;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return defValue;
        return entry.objectValue == null ? defValue : (entry.objectValue instanceof AtomicLong ? ((AtomicLong) entry.objectValue).get() : (Long) entry.objectValue);
    }
    //----------- hxxx --------------

    @Override
    public CompletableFuture<Integer> hremoveAsync(final String key, String... fields) {
        return CompletableFuture.supplyAsync(() -> hremove(key, fields), getExecutor());
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> hkeys(key), getExecutor());
    }

    @Override
    public CompletableFuture<Integer> hsizeAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> hsize(key), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return CompletableFuture.supplyAsync(() -> hincr(key, field), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field, long num) {
        return CompletableFuture.supplyAsync(() -> hincr(key, field, num), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field) {
        return CompletableFuture.supplyAsync(() -> hdecr(key, field), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field, long num) {
        return CompletableFuture.supplyAsync(() -> hdecr(key, field, num), getExecutor());
    }

    @Override
    public CompletableFuture<Boolean> hexistsAsync(final String key, String field) {
        return CompletableFuture.supplyAsync(() -> hexists(key, field), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final T value) {
        return CompletableFuture.runAsync(() -> hset(key, field, convert, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Type type, final T value) {
        return CompletableFuture.runAsync(() -> hset(key, field, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        return CompletableFuture.runAsync(() -> hset(key, field, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> hsetStringAsync(final String key, final String field, final String value) {
        return CompletableFuture.runAsync(() -> hsetString(key, field, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> hsetLongAsync(final String key, final String field, final long value) {
        return CompletableFuture.runAsync(() -> hsetLong(key, field, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        return CompletableFuture.runAsync(() -> hmset(key, values), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields) {
        return CompletableFuture.supplyAsync(() -> hmget(key, type, fields), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> hmap(key, type, offset, limit), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit, String pattern) {
        return CompletableFuture.supplyAsync(() -> hmap(key, type, offset, limit, pattern), getExecutor());
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return CompletableFuture.supplyAsync(() -> hget(key, field, type), getExecutor());
    }

    @Override
    public CompletableFuture<String> hgetStringAsync(final String key, final String field) {
        return CompletableFuture.supplyAsync(() -> hgetString(key, field), getExecutor());
    }

    @Override
    public CompletableFuture<Long> hgetLongAsync(final String key, final String field, long defValue) {
        return CompletableFuture.supplyAsync(() -> hgetLong(key, field, defValue), getExecutor());
    }

    //----------- hxxx --------------
    @Override
    public <T> CompletableFuture<T> getAsync(final String key, final Type type) {
        return CompletableFuture.supplyAsync(() -> (T) get(key, type), getExecutor());
    }

    @Override
    public CompletableFuture<String> getStringAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getString(key), getExecutor());
    }

    @Override
    public CompletableFuture<Long> getLongAsync(final String key, long defValue) {
        return CompletableFuture.supplyAsync(() -> getLong(key, defValue), getExecutor());
    }

    @Override
    public <T> T getAndRefresh(final String key, final int expireSeconds, final Type type) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        if (entry.isListCacheType()) return (T) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        if (entry.isSetCacheType()) return (T) (entry.csetValue == null ? null : new HashSet(entry.csetValue));
        return (T) entry.objectValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getStringAndRefresh(String key, final int expireSeconds) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        return (String) entry.objectValue;
    }

    @Override
    public long getLongAndRefresh(String key, final int expireSeconds, long defValue) {
        if (key == null) return defValue;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return defValue;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        return entry.objectValue == null ? defValue : (entry.objectValue instanceof AtomicLong ? ((AtomicLong) entry.objectValue).get() : (Long) entry.objectValue);

    }

    @Override
    public <T> CompletableFuture<T> getAndRefreshAsync(final String key, final int expireSeconds, final Type type) {
        return CompletableFuture.supplyAsync(() -> getAndRefresh(key, expireSeconds, type), getExecutor());
    }

    @Override
    public CompletableFuture<String> getStringAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getStringAndRefresh(key, expireSeconds), getExecutor());
    }

    @Override
    public CompletableFuture<Long> getLongAndRefreshAsync(final String key, final int expireSeconds, long defValue) {
        return CompletableFuture.supplyAsync(() -> getLongAndRefresh(key, expireSeconds, defValue), getExecutor());
    }

    @Override
    public void refresh(String key, final int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> refreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.runAsync(() -> refresh(key, expireSeconds), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    protected void set(CacheEntryType cacheType, String key, Object value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, key, value, null, null, null);
            container.putIfAbsent(key, entry);
        } else {
            entry.expireSeconds = 0;
            entry.objectValue = value;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        }
    }

    protected void hset(CacheEntryType cacheType, String key, String field, Object value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.MAP, key, value, null, null, new ConcurrentHashMap<>());
            container.putIfAbsent(key, entry);
            entry.mapValue.put(field, value);
        } else {
            entry.expireSeconds = 0;
            entry.mapValue.put(field, value);
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        }
    }

    @Override
    public <T> void set(String key, Convert convert, T value) {
        set(CacheEntryType.OBJECT, key, value);
    }

    @Override
    public <T> void set(String key, Type type, T value) {
        set(CacheEntryType.OBJECT, key, value);
    }

    @Override
    public <T> void set(String key, Convert convert, Type type, T value) {
        set(CacheEntryType.OBJECT, key, value);
    }

    @Override
    public void setString(String key, String value) {
        set(CacheEntryType.STRING, key, value);
    }

    @Override
    public void setLong(String key, long value) {
        set(CacheEntryType.LONG, key, value);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, T value) {
        return CompletableFuture.runAsync(() -> set(key, convert, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(key, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> setStringAsync(String key, String value) {
        return CompletableFuture.runAsync(() -> setString(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> setLongAsync(String key, long value) {
        return CompletableFuture.runAsync(() -> setLong(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    protected void set(CacheEntryType cacheType, int expireSeconds, String key, Object value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(cacheType, expireSeconds, key, value, null, null, null);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) entry.expireSeconds = expireSeconds;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            entry.objectValue = value;
        }
    }

    @Override
    public <T> void set(final int expireSeconds, String key, Convert convert, T value) {
        set(CacheEntryType.OBJECT, expireSeconds, key, value);
    }

    @Override
    public <T> void set(final int expireSeconds, String key, Type type, T value) {
        set(CacheEntryType.OBJECT, expireSeconds, key, value);
    }

    @Override
    public <T> void set(final int expireSeconds, String key, Convert convert, Type type, T value) {
        set(CacheEntryType.OBJECT, expireSeconds, key, value);
    }

    @Override
    public void setString(int expireSeconds, String key, String value) {
        set(CacheEntryType.STRING, expireSeconds, key, value);
    }

    @Override
    public void setLong(int expireSeconds, String key, long value) {
        set(CacheEntryType.LONG, expireSeconds, key, value);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, T value) {
        return CompletableFuture.runAsync(() -> set(expireSeconds, key, convert, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(expireSeconds, key, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, Type type, T value) {
        return CompletableFuture.runAsync(() -> set(expireSeconds, key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> setStringAsync(int expireSeconds, String key, String value) {
        return CompletableFuture.runAsync(() -> setString(expireSeconds, key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> setLongAsync(int expireSeconds, String key, long value) {
        return CompletableFuture.runAsync(() -> setLong(expireSeconds, key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public void setExpireSeconds(String key, int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> setExpireSecondsAsync(final String key, final int expireSeconds) {
        return CompletableFuture.runAsync(() -> setExpireSeconds(key, expireSeconds), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public int remove(String key) {
        if (key == null) return 0;
        return container.remove(key) == null ? 0 : 1;
    }

    @Override
    public long incr(final String key) {
        return incr(key, 1);
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> incr(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long incr(final String key, long num) {
        CacheEntry entry = container.get(key);
        if (entry == null) {
            synchronized (container) {
                entry = container.get(key);
                if (entry == null) {
                    entry = new CacheEntry(CacheEntryType.ATOMIC, key, new AtomicLong(), null, null, null);
                    container.put(key, entry);
                }
            }
        }
        return ((AtomicLong) entry.objectValue).addAndGet(num);
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key, long num) {
        return CompletableFuture.supplyAsync(() -> incr(key, num), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long decr(final String key) {
        return incr(key, -1);
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> decr(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public long decr(final String key, long num) {
        return incr(key, -num);
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key, long num) {
        return CompletableFuture.supplyAsync(() -> decr(key, num), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Integer> removeAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> remove(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> Collection<T> getCollection(final String key, final Type componentType) {
        return (Collection<T>) get(key, componentType);
    }

    @Override
    public <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, final String... keys) {
        Map<String, Collection<T>> map = new HashMap<>();
        for (String key : keys) {
            Collection<T> s = (Collection<T>) get(key, componentType);
            if (s != null) map.put(key, s);
        }
        return map;
    }

    @Override
    public Collection<String> getStringCollection(final String key) {
        return (Collection<String>) get(key, String.class);
    }

    @Override
    public Map<String, Collection<String>> getStringCollectionMap(final boolean set, final String... keys) {
        Map<String, Collection<String>> map = new HashMap<>();
        for (String key : keys) {
            Collection<String> s = (Collection<String>) get(key, String.class);
            if (s != null) map.put(key, s);
        }
        return map;
    }

    @Override
    public Map<String, Long> getLongMap(final String... keys) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String key : keys) {
            Number n = (Number) get(key, long.class);
            map.put(key, n == null ? null : n.longValue());
        }
        return map;
    }

    @Override
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
    public CompletableFuture<Map<String, Long>> getLongMapAsync(final String... keys) {
        return CompletableFuture.supplyAsync(() -> getLongMap(keys), getExecutor());
    }

    @Override
    public CompletableFuture<Long[]> getLongArrayAsync(final String... keys) {
        return CompletableFuture.supplyAsync(() -> getLongArray(keys), getExecutor());
    }

    @Override
    public Map<String, String> getStringMap(final String... keys) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : keys) {
            Object n = get(key, String.class);
            map.put(key, n == null ? null : n.toString());
        }
        return map;
    }

    @Override
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
    public CompletableFuture<Map<String, String>> getStringMapAsync(final String... keys) {
        return CompletableFuture.supplyAsync(() -> getStringMap(keys), getExecutor());
    }

    @Override
    public CompletableFuture<String[]> getStringArrayAsync(final String... keys) {
        return CompletableFuture.supplyAsync(() -> getStringArray(keys), getExecutor());
    }

    @Override
    public <T> Map<String, T> getMap(final Type componentType, final String... keys) {
        Map<String, T> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, (T) get(key, componentType));
        }
        return map;
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, final String... keys) {
        return CompletableFuture.supplyAsync(() -> getMap(componentType, keys), getExecutor());
    }

    @Override
    public Collection<Long> getLongCollection(final String key) {
        return (Collection<Long>) get(key, long.class);
    }

    @Override
    public Map<String, Collection<Long>> getLongCollectionMap(final boolean set, final String... keys) {
        Map<String, Collection<Long>> map = new HashMap<>();
        for (String key : keys) {
            Collection<Long> s = (Collection<Long>) get(key, long.class);
            if (s != null) map.put(key, s);
        }
        return map;
    }

    @Override
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(boolean set, Type componentType, String... keys) {
        return CompletableFuture.supplyAsync(() -> getCollectionMap(set, componentType, keys), getExecutor());
    }

    @Override
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getStringCollection(key), getExecutor());
    }

    @Override
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys) {
        return CompletableFuture.supplyAsync(() -> getStringCollectionMap(set, keys), getExecutor());
    }

    @Override
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getLongCollection(key), getExecutor());
    }

    @Override
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys) {
        return CompletableFuture.supplyAsync(() -> getLongCollectionMap(set, keys), getExecutor());
    }

    @Override
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(String key, Type componentType) {
        return CompletableFuture.supplyAsync(() -> getCollection(key, componentType), getExecutor());
    }

    @Override
    public int getCollectionSize(final String key) {
        Collection collection = (Collection) get(key, Object.class);
        return collection == null ? 0 : collection.size();
    }

    @Override
    public CompletableFuture<Integer> getCollectionSizeAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getCollectionSize(key), getExecutor());
    }

    @Override
    public <T> Collection<T> getCollectionAndRefresh(final String key, final int expireSeconds, final Type componentType) {
        return (Collection<T>) getAndRefresh(key, expireSeconds, componentType);
    }

    @Override
    public Collection<String> getStringCollectionAndRefresh(final String key, final int expireSeconds) {
        return (Collection<String>) getAndRefresh(key, expireSeconds, String.class);
    }

    @Override
    public <T> boolean existsSetItem(final String key, final Type type, final T value) {
        Collection list = getCollection(key, type);
        return list != null && list.contains(value);
    }

    @Override
    public <T> CompletableFuture<Boolean> existsSetItemAsync(final String key, final Type type, final T value) {
        return CompletableFuture.supplyAsync(() -> existsSetItem(key, type, value), getExecutor());
    }

    @Override
    public boolean existsStringSetItem(final String key, final String value) {
        Collection<String> list = getStringCollection(key);
        return list != null && list.contains(value);
    }

    @Override
    public CompletableFuture<Boolean> existsStringSetItemAsync(final String key, final String value) {
        return CompletableFuture.supplyAsync(() -> existsStringSetItem(key, value), getExecutor());
    }

    @Override
    public boolean existsLongSetItem(final String key, final long value) {
        Collection<Long> list = getLongCollection(key);
        return list != null && list.contains(value);
    }

    @Override
    public CompletableFuture<Boolean> existsLongSetItemAsync(final String key, final long value) {
        return CompletableFuture.supplyAsync(() -> existsLongSetItem(key, value), getExecutor());
    }

    @Override
    public Collection<Long> getLongCollectionAndRefresh(String key, int expireSeconds) {
        return (Collection<Long>) getAndRefresh(key, expireSeconds, long.class);
    }

    @Override
    public <T> CompletableFuture<Collection<T>> getCollectionAndRefreshAsync(final String key, final int expireSeconds, final Type componentType) {
        return CompletableFuture.supplyAsync(() -> getCollectionAndRefresh(key, expireSeconds, componentType), getExecutor());
    }

    @Override
    public CompletableFuture<Collection<String>> getStringCollectionAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getStringCollectionAndRefresh(key, expireSeconds), getExecutor());
    }

    @Override
    public CompletableFuture<Collection<Long>> getLongCollectionAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getLongCollectionAndRefresh(key, expireSeconds), getExecutor());
    }

    protected void appendListItem(CacheEntryType cacheType, String key, Object value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
            ConcurrentLinkedQueue list = new ConcurrentLinkedQueue();
            entry = new CacheEntry(cacheType, key, null, null, list, null);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) list = old.listValue;
            if (list != null) list.add(value);
        } else {
            entry.listValue.add(value);
        }
    }

    @Override
    public <T> void appendListItem(String key, Type componentType, T value) {
        appendListItem(CacheEntryType.OBJECT_LIST, key, value);
    }

    @Override
    public void appendStringListItem(String key, String value) {
        appendListItem(CacheEntryType.STRING_LIST, key, value);
    }

    @Override
    public void appendLongListItem(String key, long value) {
        appendListItem(CacheEntryType.LONG_LIST, key, value);
    }

    @Override
    public <T> CompletableFuture<Void> appendListItemAsync(final String key, final Type componentType, final T value) {
        return CompletableFuture.runAsync(() -> appendListItem(key, componentType, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> appendStringListItemAsync(final String key, final String value) {
        return CompletableFuture.runAsync(() -> appendStringListItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> appendLongListItemAsync(final String key, final long value) {
        return CompletableFuture.runAsync(() -> appendLongListItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> int removeListItem(String key, final Type componentType, T value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) return 0;
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public int removeStringListItem(String key, String value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) return 0;
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public int removeLongListItem(String key, long value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) return 0;
        return entry.listValue.remove(value) ? 1 : 0;
    }

    @Override
    public <T> CompletableFuture<Integer> removeListItemAsync(final String key, final Type componentType, T value) {
        return CompletableFuture.supplyAsync(() -> removeListItem(key, componentType, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Integer> removeStringListItemAsync(final String key, final String value) {
        return CompletableFuture.supplyAsync(() -> removeStringListItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Integer> removeLongListItemAsync(final String key, final long value) {
        return CompletableFuture.supplyAsync(() -> removeLongListItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public String spopStringSetItem(final String key) {
        return (String) spopSetItem(key, String.class);
    }

    @Override
    public List<String> spopStringSetItem(final String key, int count) {
        return (List) spopSetItem(key, count, String.class);
    }

    @Override
    public Long spopLongSetItem(final String key) {
        return (Long) spopSetItem(key, long.class);
    }

    @Override
    public List<Long> spopLongSetItem(final String key, int count) {
        return (List) spopSetItem(key, count, long.class);
    }

    @Override
    public <T> T spopSetItem(final String key, final Type componentType) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
            return null;
        }
        if (entry.csetValue.isEmpty()) return null;
        Iterator it = entry.csetValue.iterator();
        if (it.hasNext()) {
            Object obj = it.next();
            if (obj != null && componentType == long.class) obj = ((Number) obj).longValue();
            it.remove();
            return (T) obj;
        }
        return null;
    }

    @Override
    public <T> List<T> spopSetItem(final String key, final int count, final Type componentType) {
        if (key == null) return new ArrayList<>();
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
            return new ArrayList<>();
        }
        if (entry.csetValue.isEmpty()) return new ArrayList<>();
        Iterator it = entry.csetValue.iterator();
        List<T> list = new ArrayList<>();
        int index = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj != null && componentType == long.class) obj = ((Number) obj).longValue();
            list.add((T) obj);
            it.remove();
            if (++index >= count) break;
        }
        return list;
    }

    protected void appendSetItem(CacheEntryType cacheType, String key, Object value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.csetValue == null) {
            CopyOnWriteArraySet set = new CopyOnWriteArraySet();
            entry = new CacheEntry(cacheType, key, null, set, null, null);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) set = old.csetValue;
            if (set != null) set.add(value);
        } else {
            entry.csetValue.add(value);
        }
    }

    @Override
    public <T> void appendSetItem(String key, final Type componentType, T value) {
        appendSetItem(CacheEntryType.OBJECT_SET, key, value);
    }

    @Override
    public void appendStringSetItem(String key, String value) {
        appendSetItem(CacheEntryType.OBJECT_SET, key, value);
    }

    @Override
    public void appendLongSetItem(String key, long value) {
        appendSetItem(CacheEntryType.OBJECT_SET, key, value);
    }

    @Override
    public <T> CompletableFuture<Void> appendSetItemAsync(final String key, final Type componentType, T value) {
        return CompletableFuture.runAsync(() -> appendSetItem(key, componentType, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> appendStringSetItemAsync(final String key, final String value) {
        return CompletableFuture.runAsync(() -> appendStringSetItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Void> appendLongSetItemAsync(final String key, final long value) {
        return CompletableFuture.runAsync(() -> appendLongSetItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> int removeSetItem(String key, Type type, T value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.csetValue == null) return 0;
        return entry.csetValue.remove(value) ? 1 : 0;
    }

    @Override
    public int removeStringSetItem(String key, String value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.csetValue == null) return 0;
        return entry.csetValue.remove(value) ? 1 : 0;
    }

    @Override
    public int removeLongSetItem(String key, long value) {
        if (key == null) return 0;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.csetValue == null) return 0;
        return entry.csetValue.remove(value) ? 1 : 0;
    }

    @Override
    public <T> CompletableFuture<Integer> removeSetItemAsync(final String key, final Type componentType, final T value) {
        return CompletableFuture.supplyAsync(() -> removeSetItem(key, componentType, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Integer> removeStringSetItemAsync(final String key, final String value) {
        return CompletableFuture.supplyAsync(() -> removeStringSetItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Integer> removeLongSetItemAsync(final String key, final long value) {
        return CompletableFuture.supplyAsync(() -> removeLongSetItem(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public byte[] getBytes(final String key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        return (byte[]) entry.objectValue;
    }

    @Override
    public CompletableFuture<byte[]> getBytesAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getBytes(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public byte[] getBytesAndRefresh(String key, final int expireSeconds) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        return (byte[]) entry.objectValue;
    }

    @Override
    public CompletableFuture<byte[]> getBytesAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getBytesAndRefresh(key, expireSeconds), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public void setBytes(String key, byte[] value) {
        set(CacheEntryType.BYTES, key, value);
    }

    @Override
    public CompletableFuture<Void> setBytesAsync(final String key, byte[] value) {
        return CompletableFuture.runAsync(() -> setBytes(key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public void setBytes(final int expireSeconds, final String key, final byte[] value) {
        set(CacheEntryType.BYTES, expireSeconds, key, value);
    }

    @Override
    public CompletableFuture<Void> setBytesAsync(final int expireSeconds, final String key, byte[] value) {
        return CompletableFuture.runAsync(() -> setBytes(expireSeconds, key, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> void setBytes(final String key, final Convert convert, final Type type, final T value) {
        set(CacheEntryType.BYTES, key, convert.convertToBytes(type, value));
    }

    @Override
    public <T> CompletableFuture<Void> setBytesAsync(final String key, final Convert convert, final Type type, final T value) {
        return CompletableFuture.runAsync(() -> setBytes(key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> void setBytes(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        set(CacheEntryType.BYTES, expireSeconds, key, convert.convertToBytes(type, value));
    }

    @Override
    public <T> CompletableFuture<Void> setBytesAsync(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        return CompletableFuture.runAsync(() -> setBytes(expireSeconds, key, convert, type, value), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public List<String> queryKeys() {
        return new ArrayList<>(container.keySet());
    }

    @Override
    public List<String> queryKeysStartsWith(String startsWith) {
        if (startsWith == null) return queryKeys();
        List<String> rs = new ArrayList<>();
        container.keySet().stream().filter(x -> x.startsWith(startsWith)).forEach(x -> rs.add(x));
        return rs;
    }

    @Override
    public List<String> queryKeysEndsWith(String endsWith) {
        if (endsWith == null) return queryKeys();
        List<String> rs = new ArrayList<>();
        container.keySet().stream().filter(x -> x.endsWith(endsWith)).forEach(x -> rs.add(x));
        return rs;
    }

    @Override
    public int getKeySize() {
        return container.size();
    }

    @Override
    public CompletableFuture<List<CacheEntry<Object>>> queryListAsync() {
        return CompletableFuture.completedFuture(new ArrayList<>(container.values()));
    }

    @Override
    public List<CacheEntry< Object>> queryList() {
        return new ArrayList<>(container.values());
    }

    @Override
    public CompletableFuture<List<String>> queryKeysAsync() {
        return CompletableFuture.completedFuture(new ArrayList<>(container.keySet()));
    }

    @Override
    public CompletableFuture<List<String>> queryKeysStartsWithAsync(String startsWith) {
        return CompletableFuture.completedFuture(queryKeysStartsWith(startsWith));
    }

    @Override
    public CompletableFuture<List<String>> queryKeysEndsWithAsync(String endsWith) {
        return CompletableFuture.completedFuture(queryKeysEndsWith(endsWith));
    }

    @Override
    public CompletableFuture<Integer> getKeySizeAsync() {
        return CompletableFuture.completedFuture(container.size());
    }

    @Override
    public <T> CompletableFuture<T> spopSetItemAsync(String key, Type componentType) {
        return CompletableFuture.supplyAsync(() -> spopSetItem(key, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public <T> CompletableFuture<List<T>> spopSetItemAsync(String key, int count, Type componentType) {
        return CompletableFuture.supplyAsync(() -> spopSetItem(key, count, componentType), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<String> spopStringSetItemAsync(String key) {
        return CompletableFuture.supplyAsync(() -> spopStringSetItem(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<List<String>> spopStringSetItemAsync(String key, int count) {
        return CompletableFuture.supplyAsync(() -> spopStringSetItem(key, count), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<Long> spopLongSetItemAsync(String key) {
        return CompletableFuture.supplyAsync(() -> spopLongSetItem(key), getExecutor()).whenComplete(futureCompleteConsumer);
    }

    @Override
    public CompletableFuture<List<Long>> spopLongSetItemAsync(String key, int count) {
        return CompletableFuture.supplyAsync(() -> spopLongSetItem(key, count), getExecutor()).whenComplete(futureCompleteConsumer);
    }

}
