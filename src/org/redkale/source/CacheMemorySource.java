/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.beans.ConstructorProperties;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * CacheSource的默认实现--内存缓存
 *
 * @param <K> key类型
 * @param <V> value类型
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@LocalService
@AutoLoad(false)
@ResourceType({CacheSource.class})
public class CacheMemorySource<K extends Serializable, V extends Object> extends AbstractService implements CacheSource<K, V>, Service, AutoCloseable, Resourcable {

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

    public CacheMemorySource() {
    }

    public final CacheMemorySource setStoreType(Class keyType, Class valueType) {
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
        final CacheMemorySource self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("property");
        if (keyType == null && prop != null) {
            String storeKeyStr = prop.getValue("key-type");
            String storeValueStr = prop.getValue("value-type");
            if (storeKeyStr != null && storeValueStr != null) {
                try {
                    this.setStoreType(Class.forName(storeKeyStr), Class.forName(storeValueStr));
                } catch (Throwable e) {
                    logger.log(Level.SEVERE, self.getClass().getSimpleName() + " load key & value store class (" + storeKeyStr + ", " + storeValueStr + ") error", e);
                }
            }
            if (prop.getBoolValue("store-ignore", false)) setNeedStore(false);
        }
        String expireHandlerClass = prop == null ? null : prop.getValue("expirehandler");
        if (expireHandlerClass != null) {
            try {
                this.expireHandler = (Consumer<CacheEntry>) Class.forName(expireHandlerClass).newInstance();
            } catch (Throwable e) {
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
            logger.finest(self.getClass().getSimpleName() + ":" + self.resourceName() + " start schedule expire executor");
        }
        if (Sncp.isRemote(self)) return;

        boolean datasync = false; //是否从远程同步过数据
        //----------同步数据……-----------
        // TODO
        if (!this.needStore) return;
        try {
            File store = new File(home, "cache/" + resourceName());
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
            logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + resourceName() + ") load store file error ", e);
        }
    }

    @Override
    public void close() throws Exception {  //给Application 关闭时调用
        destroy(null);
    }

    @Override
    public String resourceName() {
        Resource res = this.getClass().getAnnotation(Resource.class);
        return res == null ? null : res.name();
    }

    @Override
    public void destroy(AnyValue conf) {
        if (scheduler != null) scheduler.shutdownNow();
        if (!this.needStore || Sncp.isRemote(this) || container.isEmpty()) return;
        try {
            File store = new File(home, "cache/" + resourceName());
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
            logger.log(Level.SEVERE, CacheSource.class.getSimpleName() + "(" + resourceName() + ") store to file error ", e);
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
    public CompletableFuture<Boolean> existsAsync(final K key) {
        CompletableFuture<Boolean> future = new CompletableFuture();
        future.complete(exists(key));
        return future;
    }

    @Override
    public void existsAsync(final AsyncHandler<Boolean, K> handler, @RpcAttachment final K key) {
        boolean rs = exists(key);
        if (handler != null) handler.completed(rs, key);
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
    public CompletableFuture<V> getAsync(final K key) {
        CompletableFuture<V> future = new CompletableFuture();
        future.complete(get(key));
        return future;
    }

    @Override
    public void getAsync(final AsyncHandler<V, K> handler, @RpcAttachment final K key) {
        V rs = get(key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    @RpcMultiRun
    public V getAndRefresh(K key, final int expireSeconds) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired() || entry.value == null) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        if (entry.isListCacheType()) return (V) new ArrayList((Collection) entry.value);
        if (entry.isSetCacheType()) return (V) new HashSet((Collection) entry.value);
        return (V) entry.getValue();
    }

    @Override
    public CompletableFuture<V> getAndRefreshAsync(final K key, final int expireSeconds) {
        CompletableFuture<V> future = new CompletableFuture();
        future.complete(getAndRefresh(key, expireSeconds));
        return future;
    }

    @Override
    public void getAndRefreshAsync(final AsyncHandler<V, K> handler, @RpcAttachment final K key, final int expireSeconds) {
        V rs = getAndRefresh(key, expireSeconds);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    @RpcMultiRun
    public void refresh(K key, final int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> refreshAsync(final K key, final int expireSeconds) {
        CompletableFuture<Void> future = new CompletableFuture();
        refresh(key, expireSeconds);
        future.complete(null);
        return future;
    }

    @Override
    public void refreshAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final int expireSeconds) {
        refresh(key, expireSeconds);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
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
    public CompletableFuture<Void> setAsync(K key, V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        set(key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void setAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final V value) {
        set(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
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
    public CompletableFuture<Void> setAsync(int expireSeconds, K key, V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        set(expireSeconds, key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void setAsync(final AsyncHandler<Void, K> handler, final int expireSeconds, @RpcAttachment final K key, final V value) {
        set(expireSeconds, key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
    public void setExpireSeconds(K key, int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> setExpireSecondsAsync(final K key, final int expireSeconds) {
        CompletableFuture<Void> future = new CompletableFuture();
        setExpireSeconds(key, expireSeconds);
        future.complete(null);
        return future;
    }

    @Override
    public void setExpireSecondsAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final int expireSeconds) {
        setExpireSeconds(key, expireSeconds);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
    public void remove(K key) {
        if (key == null) return;
        container.remove(key);
    }

    @Override
    public CompletableFuture<Void> removeAsync(final K key) {
        CompletableFuture<Void> future = new CompletableFuture();
        remove(key);
        future.complete(null);
        return future;
    }

    @Override
    public void removeAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key) {
        remove(key);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    public Collection<V> getCollection(final K key) {
        return (Collection<V>) get(key);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAsync(final K key) {
        CompletableFuture<Collection<V>> future = new CompletableFuture();
        future.complete((Collection<V>) get(key));
        return future;
    }

    @Override
    public void getCollectionAsync(final AsyncHandler<Collection<V>, K> handler, @RpcAttachment final K key) {
        Collection<V> rs = getCollection(key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public Collection<V> getCollectionAndRefresh(final K key, final int expireSeconds) {
        return (Collection<V>) getAndRefresh(key, expireSeconds);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final K key, final int expireSeconds) {
        CompletableFuture<Collection<V>> future = new CompletableFuture();
        future.complete((Collection<V>) getAndRefresh(key, expireSeconds));
        return future;
    }

    @Override
    public void getCollectionAndRefreshAsync(final AsyncHandler<Collection<V>, K> handler, @RpcAttachment final K key, final int expireSeconds) {
        Collection<V> rs = getCollectionAndRefresh(key, expireSeconds);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    @RpcMultiRun
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
    public CompletableFuture<Void> appendListItemAsync(final K key, final V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        appendListItem(key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void appendListItemAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final V value) {
        appendListItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
    public void removeListItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType()) return;
        ((Collection) entry.getValue()).remove(value);
    }

    @Override
    public CompletableFuture<Void> removeListItemAsync(final K key, final V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        removeListItem(key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void removeListItemAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final V value) {
        removeListItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
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
    public CompletableFuture<Void> appendSetItemAsync(final K key, final V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        appendSetItem(key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void appendSetItemAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final V value) {
        appendSetItem(key, value);
        if (handler != null) handler.completed(null, key);
    }

    @Override
    @RpcMultiRun
    public void removeSetItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !(entry.value instanceof Set)) return;
        ((Set) entry.getValue()).remove(value);
    }

    @Override
    public CompletableFuture<Void> removeSetItemAsync(final K key, final V value) {
        CompletableFuture<Void> future = new CompletableFuture();
        removeSetItem(key, value);
        future.complete(null);
        return future;
    }

    @Override
    public void removeSetItemAsync(final AsyncHandler<Void, K> handler, @RpcAttachment final K key, final V value) {
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

        @ConstructorProperties({"cacheType", "expireSeconds", "lastAccessed", "key", "value"})
        public CacheEntry(CacheEntryType cacheType, int expireSeconds, int lastAccessed, K key, T value) {
            this.cacheType = cacheType;
            this.expireSeconds = expireSeconds;
            this.lastAccessed = lastAccessed;
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        @ConvertColumn(ignore = true)
        public boolean isListCacheType() {
            return cacheType == CacheEntryType.LIST;
        }

        @ConvertColumn(ignore = true)
        public boolean isSetCacheType() {
            return cacheType == CacheEntryType.SET;
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

        public T getValue() {
            return value;
        }

        public K getKey() {
            return key;
        }

    }
}
