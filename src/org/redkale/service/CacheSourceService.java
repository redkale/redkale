/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;
import org.redkale.convert.json.*;
import org.redkale.net.sncp.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 *
 * @param <K>
 * @param <V>
 * @see http://www.redkale.org
 * @author zhangjx
 */
@AutoLoad(false)
public class CacheSourceService<K extends Serializable, V extends Object> implements CacheSource<K, V>, Service, AutoCloseable {

    @Resource(name = "APP_HOME")
    private File home;

    @Resource
    private JsonConvert convert;

    private boolean needStore;

    private Class keyType;

    private Type objValueType;

    private Type setValueType;

    private Type listValueType;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<K, CacheEntry<K, ?>> container = new ConcurrentHashMap<>();

    public CacheSourceService() {
    }

    public final CacheSourceService setStoreType(Class keyType, Class valueType) {
        this.keyType = keyType;
        this.objValueType = valueType;
        this.setValueType = TypeToken.createParameterizedType(null, CopyOnWriteArraySet.class, valueType);
        this.listValueType = TypeToken.createParameterizedType(null, ConcurrentLinkedQueue.class, valueType);
        this.setNeedStore(this.keyType != null && this.keyType != Serializable.class && this.objValueType != null);
        return this;
    }

    public final void setNeedStore(boolean needStore) {
        this.needStore = needStore;
    }

    @Override
    public void init(AnyValue conf) {
        final CacheSourceService self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("property");
        if (keyType == null && prop != null) {
            String storeKeyStr = prop.getValue("key-type");
            String storeValueStr = prop.getValue("value-type");
            if (storeKeyStr != null && storeValueStr != null) {
                try {
                    this.setStoreType(Class.forName(storeKeyStr), Class.forName(storeValueStr));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, self.getClass().getSimpleName() + " load key & value store class (" + storeKeyStr + ", " + storeValueStr + ") error", e);
                }
            }
            if (prop.getBoolValue("store-ignore", false)) setNeedStore(false);
        }
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
            final List<K> keys = new ArrayList<>();
            scheduler.scheduleWithFixedDelay(() -> {
                keys.clear();
                int now = (int) (System.currentTimeMillis() / 1000);
                container.forEach((k, x) -> {
                    if (x.expireSeconds > 0 && (now > (x.lastAccessed + x.expireSeconds))) {
                        keys.add(x.key);
                    }
                });
                for (K key : keys) {
                    CacheEntry entry = container.remove(key);
                    if (expireHandler != null && entry != null) expireHandler.accept(entry);
                }
            }, 10, 10, TimeUnit.SECONDS);
            logger.finest(self.getClass().getSimpleName() + ":" + self.name() + " start schedule expire executor");
        }
        if (Sncp.isRemote(self)) return;

        boolean datasync = false; //是否从远程同步过数据
        //----------同步数据……-----------
        // TODO
        if (!this.needStore) return;
        try {
            File store = new File(home, "cache/" + name());
            if (!store.isFile() || !store.canRead()) return;
            LineNumberReader reader = new LineNumberReader(new FileReader(store));
            if (this.keyType == null) this.keyType = Serializable.class;
            if (this.objValueType == null) {
                this.objValueType = Object.class;
                this.setValueType = TypeToken.createParameterizedType(null, CopyOnWriteArraySet.class, this.objValueType);
                this.listValueType = TypeToken.createParameterizedType(null, ConcurrentLinkedQueue.class, this.objValueType);
            }
            final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, objValueType);
            final Type storeSetType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, setValueType);
            final Type storeListType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, listValueType);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                CacheEntry<K, ?> entry = convert.convertFrom(line.startsWith(CacheEntry.JSON_SET_KEY) ? storeSetType : (line.startsWith(CacheEntry.JSON_LIST_KEY) ? storeListType : storeObjType), line);
                if (entry.isExpired()) continue;
                if (datasync && container.containsKey(entry.key)) continue; //已经同步了
                container.put(entry.key, entry);
            }
            reader.close();
            store.delete();
        } catch (Exception e) {
            logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + name() + ") load store file error ", e);
        }
    }

    @Override
    public void close() throws Exception {  //给Application 关闭时调用
        destroy(null);
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
        if (!this.needStore || Sncp.isRemote(this) || container.isEmpty()) return;
        try {
            File store = new File(home, "cache/" + name());
            store.getParentFile().mkdirs();
            PrintStream stream = new PrintStream(store, "UTF-8");
            final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, objValueType);
            final Type storeSetType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, setValueType);
            final Type storeListType = TypeToken.createParameterizedType(null, CacheEntry.class, keyType, listValueType);
            Collection<CacheEntry<K, ?>> entrys = (Collection<CacheEntry<K, ?>>) container.values();
            for (CacheEntry entry : entrys) {
                stream.println(convert.convertTo(entry.isSetCacheType() ? storeSetType : (entry.isListCacheType() ? storeListType : storeObjType), entry));
            }
            container.clear();
            stream.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + name() + ") store to file error ", e);
        }
    }

    @Override
    public boolean exists(K key) {
        if (key == null) return false;
        CacheEntry entry = container.get(key);
        if (entry == null) return false;
        return !entry.isExpired();
    }

    @Override
    public void exists(final CompletionHandler<Boolean, K> handler, @DynAttachment final K key) {
        if (handler != null) handler.completed(exists(key), key);
    }

    @Override
    public V get(K key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.value == null) return null;
        if (entry.isListCacheType()) return (V) new ArrayList((Collection) entry.value);
        if (entry.isSetCacheType()) return (V) new HashSet((Collection) entry.value);
        return (V) entry.getValue();
    }

    @Override
    public void get(final CompletionHandler<V, K> handler, @DynAttachment final K key) {
        if (handler != null) handler.completed(get(key), key);
    }

    @Override
    @MultiRun
    public V getAndRefresh(K key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.value == null) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        if (entry.isListCacheType()) return (V) new ArrayList((Collection) entry.value);
        if (entry.isSetCacheType()) return (V) new HashSet((Collection) entry.value);
        return (V) entry.getValue();
    }

    @Override
    public void getAndRefresh(final CompletionHandler<V, K> handler, @DynAttachment final K key) {
        V rs = getAndRefresh(key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    @MultiRun
    public void refresh(K key) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
    }

    @Override
    public void refresh(final CompletionHandler<Void, K> handler, final K key) {
        refresh(key);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void set(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.OBJECT, key, value);
            container.putIfAbsent(key, entry);
        } else {
            entry.expireSeconds = 0;
            entry.value = value;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        }
    }

    @Override
    public void set(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final V value) {
        set(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void set(int expireSeconds, K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.OBJECT, expireSeconds, key, value);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) entry.expireSeconds = expireSeconds;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            entry.value = value;
        }
    }

    @Override
    public void set(final CompletionHandler<Void, K> handler, final int expireSeconds, @DynAttachment final K key, final V value) {
        set(expireSeconds, key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void setExpireSeconds(K key, int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public void setExpireSeconds(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final int expireSeconds) {
        setExpireSeconds(key, expireSeconds);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void remove(K key) {
        if (key == null) return;
        container.remove(key);
    }

    @Override
    public void remove(final CompletionHandler<Void, K> handler, @DynAttachment final K key) {
        remove(key);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    public Collection<V> getCollection(final K key) {
        return (Collection<V>) get(key);
    }

    @Override
    public void getCollection(final CompletionHandler<Collection<V>, K> handler, @DynAttachment final K key) {
        if (handler != null) handler.completed(getCollection(key), key);
    }

    @Override
    public Collection<V> getCollectionAndRefresh(final K key) {
        return (Collection<V>) getAndRefresh(key);
    }

    @Override
    public void getCollectionAndRefresh(final CompletionHandler<Collection<V>, K> handler, @DynAttachment final K key) {
        if (handler != null) handler.completed(getCollectionAndRefresh(key), key);
    }

    @Override
    @MultiRun
    public void appendListItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType()) {
            Collection<V> list = new ConcurrentLinkedQueue<>();
            entry = new CacheEntry(CacheEntryType.LIST, key, list);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) list = (Collection) old.value;
            list.add(value);
        } else {
            ((Collection) entry.getValue()).add(value);
        }
    }

    @Override
    public void appendListItem(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final V value) {
        appendListItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void removeListItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType()) return;
        ((Collection) entry.getValue()).remove(value);
    }

    @Override
    public void removeListItem(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final V value) {
        removeListItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void appendSetItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType()) {
            Collection<V> set = new CopyOnWriteArraySet();
            entry = new CacheEntry(CacheEntryType.SET, key, set);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) set = (Collection) old.value;
            set.add(value);
        } else {
            ((Collection) entry.getValue()).add(value);
        }
    }

    @Override
    public void appendSetItem(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final V value) {
        appendSetItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @MultiRun
    public void removeSetItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof Set)) return;
        ((Set) entry.getValue()).remove(value);
    }

    @Override
    public void removeSetItem(final CompletionHandler<Void, K> handler, @DynAttachment final K key, final V value) {
        removeSetItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    public static enum CacheEntryType {
        OBJECT, SET, LIST;
    }

    public static final class CacheEntry<K extends Serializable, T> {

        public static final String JSON_SET_KEY = "{\"cacheType\":\"" + CacheEntryType.SET + "\"";

        public static final String JSON_LIST_KEY = "{\"cacheType\":\"" + CacheEntryType.LIST + "\"";

        private final CacheEntryType cacheType;

        private final K key;

        //<=0表示永久保存
        private int expireSeconds;

        private volatile int lastAccessed; //最后刷新时间

        private T value;

        public CacheEntry(CacheEntryType cacheType, K key, T value) {
            this(cacheType, 0, key, value);
        }

        public CacheEntry(CacheEntryType cacheType, int expireSeconds, K key, T value) {
            this(cacheType, expireSeconds, (int) (System.currentTimeMillis() / 1000), key, value);
        }

        private CacheEntry(CacheEntryType cacheType, int expireSeconds, int lastAccessed, K key, T value) {
            this.cacheType = cacheType;
            this.expireSeconds = expireSeconds;
            this.lastAccessed = lastAccessed;
            this.key = key;
            this.value = value;
        }

        private static Creator createCreator() {
            return new Creator<CacheEntry>() {
                @Override
                public CacheEntry create(Object... params) {
                    return new CacheEntry((CacheEntryType) params[0], (Integer) params[1], (Integer) params[2], (Serializable) params[3], params[4]);
                }
            };
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public CacheEntryType getCacheType() {
            return cacheType;
        }

        @Ignore
        public boolean isListCacheType() {
            return cacheType == CacheEntryType.LIST;
        }

        @Ignore
        public boolean isSetCacheType() {
            return cacheType == CacheEntryType.SET;
        }

        @Ignore
        public boolean isExpired() {
            return (expireSeconds > 0 && lastAccessed + expireSeconds < (System.currentTimeMillis() / 1000));
        }

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public int getLastAccessed() {
            return lastAccessed;
        }

        public T getValue() {
            return value;
        }

        public K getKey() {
            return key;
        }

    }
}
