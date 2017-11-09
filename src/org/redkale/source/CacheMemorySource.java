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
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * CacheSource的默认实现--内存缓存
 *
 * @param <V> value类型
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@ResourceType(CacheSource.class)
public class CacheMemorySource<V extends Object> extends AbstractService implements CacheSource<V>, Service, AutoCloseable, Resourcable {

    @Resource(name = "APP_HOME")
    private File home;

    @Resource
    private JsonConvert defaultConvert;

    @Resource(name = "$_convert")
    private JsonConvert convert;

    private boolean needStore;

    private Type objValueType;

    private Type setValueType;

    private Type listValueType;

    private ScheduledThreadPoolExecutor scheduler;

    private Consumer<CacheEntry> expireHandler;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final ConcurrentHashMap<String, CacheEntry<Object>> container = new ConcurrentHashMap<>();

    @RpcRemote
    protected CacheSource<V> remoteSource;

    public CacheMemorySource() {
    }

    @Override
    public final void initValueType(Type valueType) {
        this.objValueType = valueType;
        this.setValueType = TypeToken.createParameterizedType(null, CopyOnWriteArraySet.class, valueType);
        this.listValueType = TypeToken.createParameterizedType(null, ConcurrentLinkedQueue.class, valueType);
        this.initTransient(this.objValueType == null);
    }

    @Override
    public final void initTransient(boolean flag) {
        this.needStore = !flag;
    }

    @Override
    public final String getType() {
        return "memory";
    }

    @Override
    public void init(AnyValue conf) {
        if (this.convert == null) this.convert = this.defaultConvert;
        final CacheMemorySource self = this;
        AnyValue prop = conf == null ? null : conf.getAnyValue("property");
        if (prop != null) {
            String storeValueStr = prop.getValue("value-type");
            if (storeValueStr != null) {
                try {
                    this.initValueType(Thread.currentThread().getContextClassLoader().loadClass(storeValueStr));
                } catch (Throwable e) {
                    logger.log(Level.SEVERE, self.getClass().getSimpleName() + " load key & value store class (" + storeValueStr + ") error", e);
                }
            }
            this.initTransient(prop.getBoolValue("store-ignore", false));
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
            final List<String> keys = new ArrayList<>();
            scheduler.scheduleWithFixedDelay(() -> {
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
                if (this.objValueType == null) {
                    this.objValueType = Object.class;
                    this.setValueType = TypeToken.createParameterizedType(null, CopyOnWriteArraySet.class, this.objValueType);
                    this.listValueType = TypeToken.createParameterizedType(null, ConcurrentLinkedQueue.class, this.objValueType);
                }
                final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);
                final Type storeSetType = TypeToken.createParameterizedType(null, CacheEntry.class, setValueType);
                final Type storeListType = TypeToken.createParameterizedType(null, CacheEntry.class, listValueType);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    CacheEntry<Object> entry = convert.convertFrom(line.startsWith(CacheEntry.JSON_SET_KEY) ? storeSetType : (line.startsWith(CacheEntry.JSON_LIST_KEY) ? storeListType : storeObjType), line);
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
            SncpClient client = Sncp.getSncpClient((Service) remoteSource);
            if (client != null && client.getRemoteGroupTransport() != null) {
                super.runAsync(() -> {
                    try {
                        CompletableFuture<List<CacheEntry<Object>>> listFuture = remoteSource.queryListAsync();
                        listFuture.whenComplete((list, exp) -> {
                            if (exp != null) {
                                logger.log(Level.FINEST, CacheSource.class.getSimpleName() + "(" + resourceName() + ") queryListAsync error", exp);
                            } else {
                                for (CacheEntry<Object> entry : list) {
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
            final Type storeObjType = TypeToken.createParameterizedType(null, CacheEntry.class, objValueType);
            final Type storeSetType = TypeToken.createParameterizedType(null, CacheEntry.class, setValueType);
            final Type storeListType = TypeToken.createParameterizedType(null, CacheEntry.class, listValueType);
            Collection<CacheEntry<Object>> entrys = container.values();
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
    public V get(String key) {
        if (key == null) return null;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.isExpired()) return null;
        if (entry.isListCacheType()) return (V) (entry.listValue == null ? null : new ArrayList(entry.listValue));
        if (entry.isSetCacheType()) return (V) (entry.setValue == null ? null : new HashSet(entry.setValue));
        return (V) entry.objectValue;
    }

    @Override
    public CompletableFuture<V> getAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> get(key), getExecutor());
    }

    @Override
    @RpcMultiRun
    public V getAndRefresh(String key, final int expireSeconds) {
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
    public CompletableFuture<V> getAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getAndRefresh(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void refresh(String key, final int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.lastAccessed = (int) (System.currentTimeMillis() / 1000);
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> refreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.runAsync(() -> refresh(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void set(String key, V value) {
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
    public CompletableFuture<Void> setAsync(String key, V value) {
        return CompletableFuture.runAsync(() -> set(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void set(int expireSeconds, String key, V value) {
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
    public CompletableFuture<Void> setAsync(int expireSeconds, String key, V value) {
        return CompletableFuture.runAsync(() -> set(expireSeconds, key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void setExpireSeconds(String key, int expireSeconds) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null) return;
        entry.expireSeconds = expireSeconds;
    }

    @Override
    public CompletableFuture<Void> setExpireSecondsAsync(final String key, final int expireSeconds) {
        return CompletableFuture.runAsync(() -> setExpireSeconds(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void remove(String key) {
        if (key == null) return;
        container.remove(key);
    }

    @Override
    public CompletableFuture<Void> removeAsync(final String key) {
        return CompletableFuture.runAsync(() -> remove(key), getExecutor());
    }

    @Override
    public Collection<V> getCollection(final String key) {
        return (Collection<V>) get(key);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getCollection(key), getExecutor());
    }

    @Override
    public int getCollectionSize(final String key) {
        Collection<V> collection = (Collection<V>) get(key);
        return collection == null ? 0 : collection.size();
    }

    @Override
    public CompletableFuture<Integer> getCollectionSizeAsync(final String key) {
        return CompletableFuture.supplyAsync(() -> getCollectionSize(key), getExecutor());
    }

    @Override
    public Collection<V> getCollectionAndRefresh(final String key, final int expireSeconds) {
        return (Collection<V>) getAndRefresh(key, expireSeconds);
    }

    @Override
    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final String key, final int expireSeconds) {
        return CompletableFuture.supplyAsync(() -> getCollectionAndRefresh(key, expireSeconds), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void appendListItem(String key, V value) {
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
    public CompletableFuture<Void> appendListItemAsync(final String key, final V value) {
        return CompletableFuture.runAsync(() -> appendListItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void removeListItem(String key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.listValue == null) return;
        entry.listValue.remove(value);
    }

    @Override
    public CompletableFuture<Void> removeListItemAsync(final String key, final V value) {
        return CompletableFuture.runAsync(() -> removeListItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void appendSetItem(String key, V value) {
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
    public CompletableFuture<Void> appendSetItemAsync(final String key, final V value) {
        return CompletableFuture.runAsync(() -> appendSetItem(key, value), getExecutor());
    }

    @Override
    @RpcMultiRun
    public void removeSetItem(String key, V value) {
        if (key == null) return;
        CacheEntry entry = container.get(key);
        if (entry == null || entry.setValue == null) return;
        entry.setValue.remove(value);
    }

    @Override
    public CompletableFuture<Void> removeSetItemAsync(final String key, final V value) {
        return CompletableFuture.runAsync(() -> removeSetItem(key, value), getExecutor());
    }

    @Override
    public List<String> queryKeys() {
        return new ArrayList<>(container.keySet());
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
    public CompletableFuture<Integer> getKeySizeAsync() {
        return CompletableFuture.completedFuture(container.size());
    }
}
