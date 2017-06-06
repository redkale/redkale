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
import java.util.function.Consumer;
import java.util.logging.*;
import javax.annotation.Resource;
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
@Local
@AutoLoad(false)
@ResourceType(CacheSource.class)
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

    protected final ConcurrentHashMap<K, CacheEntry<K, Object>> container = new ConcurrentHashMap<>();

    @RpcRemote
    protected CacheSource<K, V> remoteSource;

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
                    this.setStoreType(Thread.currentThread().getContextClassLoader().loadClass(storeKeyStr), Thread.currentThread().getContextClassLoader().loadClass(storeValueStr));
                } catch (Throwable e) {
                    logger.log(Level.SEVERE, self.getClass().getSimpleName() + " load key & value store class (" + storeKeyStr + ", " + storeValueStr + ") error", e);
                }
            }
            if (prop.getBoolValue("store-ignore", false)) setNeedStore(false);
        }
        String expireHandlerClass = prop == null ? null : prop.getValue("expirehandler");
        if (expireHandlerClass != null) {
            try {
                this.expireHandler = (Consumer<CacheEntry>) Thread.currentThread().getContextClassLoader().loadClass(expireHandlerClass).newInstance();
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
        if (this.needStore) {
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
                    CacheEntry<K, Object> entry = convert.convertFrom(line.startsWith(CacheEntry.JSON_SET_KEY) ? storeSetType : (line.startsWith(CacheEntry.JSON_LIST_KEY) ? storeListType : storeObjType), line);
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
        if (remoteSource != null && !Sncp.isRemote(this)) {
            super.runAsync(() -> {
                try {
                    CompletableFuture<List<CacheEntry<K, Object>>> listFuture = remoteSource.queryListAsync();
                    listFuture.whenComplete((list, exp) -> {
                        if (exp != null) {
                            logger.log(Level.FINEST, CacheSource.class.getSimpleName() + "(" + resourceName() + ") queryListAsync error", exp);
                        } else {
                            for (CacheEntry<K, Object> entry : list) {
                                container.put(entry.key, entry);
                            }
                        }
                    });
                } catch (Exception e) {
                    logger.log(Level.FINEST, CacheSource.class.getSimpleName() + "(" + resourceName() + ") queryListAsync error, maybe remote node connot connect ", e);
                }
            });
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
            Collection<CacheEntry<K, Object>> entrys = container.values();
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
        return CompletableFuture.supplyAsync(() -> exists(key), getExecutor());
    }

    @Override
    public V get(K key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        if (entry.isListCacheType()) return (V) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        if (entry.isSetCacheType()) return (V) (entry.setValue == null ? null : new HashSet(entry.setValue));
        return (V) entry.objectValue;
    }

    @Override
    public CompletableFuture<V> getAsync(final K key) {
        return CompletableFuture.supplyAsync(() -> get(key), getExecutor());
    }

    @Override
    @RpcMultiRun
    public V getAndRefresh(K key, final int expireSeconds) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
        if (entry.isListCacheType()) return (V) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        if (entry.isSetCacheType()) return (V) (entry.setValue == null ? null : new HashSet(entry.setValue));
        return (V) entry.objectValue;
    }

    @Override
    public CompletableFuture<V> getAndRefreshAsync(final K key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getAndRefresh(key, expireSeconds), getExecutor());
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
        return CompletableFuture.runAsync(() -> refresh(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void set(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.OBJECT, key, value, null, null);
            container.putIfAbsent(key, entry);
        } else {
            entry.expireSeconds = 0;
            entry.objectValue = value;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        }
    }

    @Override
    public CompletableFuture<Void> setAsync(K key, V value) {
        return CompletableFuture.runAsync(() -> set(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void set(int expireSeconds, K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) {
            entry = new CacheEntry(CacheEntryType.OBJECT, expireSeconds, key, value, null, null);
            container.putIfAbsent(key, entry);
        } else {
            if (expireSeconds > 0) entry.expireSeconds = expireSeconds;
            entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
            entry.objectValue = value;
        }
    }

    @Override
    public CompletableFuture<Void> setAsync(int expireSeconds, K key, V value) {
        return CompletableFuture.runAsync(() -> set(expireSeconds, key, value), getExecutor());
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
        return CompletableFuture.runAsync(() -> setExpireSeconds(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void remove(K key) {
        if (key == null) return;
        container.remove(key);
    }

    @Override
    public CompletableFuture<Void> removeAsync(final K key) {
        return CompletableFuture.runAsync(() -> remove(key), getExecutor());
    }

    @Override
    public Collection<V> getCollection(final K key) {
        return (Collection<V>) get(key);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAsync(final K key) {
        return CompletableFuture.supplyAsync(() -> getCollection(key), getExecutor());
    }

    @Override
    public long getCollectionSize(final K key) {
        Collection<V> collection = (Collection<V>) get(key);
        return collection == null ? 0 : collection.size();
    }

    @Override
    public CompletableFuture<Long> getCollectionSizeAsync(final K key) {
        return CompletableFuture.supplyAsync(() -> getCollectionSize(key), getExecutor());
    }

    @Override
    public Collection<V> getCollectionAndRefresh(final K key, final int expireSeconds) {
        return (Collection<V>) getAndRefresh(key, expireSeconds);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final K key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getCollectionAndRefresh(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void appendListItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isListCacheType() || entry.listValue == null) {
            ConcurrentLinkedQueue<V> list = new ConcurrentLinkedQueue();
            entry = new CacheEntry(CacheEntryType.LIST, key, null, null, list);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) list = old.listValue;
            if (list != null) list.add(value);
        } else {
            entry.listValue.add(value);
        }
    }

    @Override
    public CompletableFuture<Void> appendListItemAsync(final K key, final V value) {
        return CompletableFuture.runAsync(() -> appendListItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void removeListItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) return;
        entry.listValue.remove(value);
    }

    @Override
    public CompletableFuture<Void> removeListItemAsync(final K key, final V value) {
        return CompletableFuture.runAsync(() -> removeListItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void appendSetItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || !entry.isSetCacheType() || entry.setValue == null) {
            CopyOnWriteArraySet<V> set = new CopyOnWriteArraySet();
            entry = new CacheEntry(CacheEntryType.SET, key, null, set, null);
            CacheEntry old = container.putIfAbsent(key, entry);
            if (old != null) set = old.setValue;
            if (set != null) set.add(value);
        } else {
            entry.setValue.add(value);
        }
    }

    @Override
    public CompletableFuture<Void> appendSetItemAsync(final K key, final V value) {
        return CompletableFuture.runAsync(() -> appendSetItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void removeSetItem(K key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.setValue == null) return;
        entry.setValue.remove(value);
    }

    @Override
    public CompletableFuture<Void> removeSetItemAsync(final K key, final V value) {
        return CompletableFuture.runAsync(() -> removeSetItem(key, value), getExecutor());
    }

    @Override
    public List<K> queryKeys() {
        return new ArrayList<>(container.keySet());
    }

    @Override
    public CompletableFuture<List<CacheEntry<K, Object>>> queryListAsync() {
        return CompletableFuture.completedFuture(new ArrayList<>(container.values()));
    }

    @Override
    public List<CacheEntry<K, Object>> queryList() {
        return new ArrayList<>(container.values());
    }

    @Override
    public CompletableFuture<List<K>> queryKeysAsync() {
        return CompletableFuture.completedFuture(new ArrayList<>(container.keySet()));
    }

}
