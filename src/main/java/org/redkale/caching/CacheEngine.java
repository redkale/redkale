/*
 *
 */
package org.redkale.caching;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.ResourceType;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.util.AnyValue;
import org.redkale.util.TypeToken;

/**
 * 缓存管理器
 *
 * @author zhangjx
 */
@Local
@Component
@AutoLoad(false)
@ResourceType(CacheManager.class)
public class CacheEngine implements CacheManager, Service {

    //缓存配置项
    protected boolean enabled = true;

    //数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    //本地缓存Source
    protected final CacheMemorySource localSource = new CacheMemorySource("caching");

    //缓存hash集合, 用于定时遍历删除过期数据
    protected final ConcurrentSkipListSet<String> hashNames = new ConcurrentSkipListSet<>();

    //远程缓存Source
    protected CacheSource remoteSource;

    protected CacheEngine(@Nullable CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    public static CacheEngine create(@Nullable CacheSource remoteSource) {
        return new CacheEngine(remoteSource);
    }

    @Override
    public void init(AnyValue conf) {
        if (conf == null) {
            conf = AnyValue.create();
        }
        this.enabled = conf.getBoolValue("enabled", true);
        if (this.enabled) {
            this.localSource.init(conf);
        }

    }

    @Override
    public void destroy(AnyValue conf) {
        if (this.enabled) {
            this.localSource.destroy(conf);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CacheEngine addHash(String hash) {
        this.hashNames.add(hash);
        return this;
    }

    //-------------------------------------- 本地缓存 --------------------------------------
    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param hash 缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> T localGet(final String hash, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(hash, key, t);
        return CacheValue.get(val);
    }

    /**
     * 本地缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    @Override
    public <T> void localSet(String hash, String key, Type type, T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        localSource.hset(hash, key, t, val);
    }

    /**
     * 本地删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    @Override
    public long localDel(String hash, String key) {
        return localSource.hdel(hash, key);
    }

    //-------------------------------------- 远程缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param hash 缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> T remoteGet(final String hash, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = remoteSource.hget(hash, key, t);
        return CacheValue.get(val);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param hash 缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> remoteGetAsync(final String hash, final String key, final Type type) {
        Type t = loadCacheType(type);
        CompletableFuture<CacheValue<T>> future = remoteSource.hgetAsync(hash, key, t);
        return future.thenApply(CacheValue::get);
    }

    /**
     * 远程缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public <T> void remoteSet(final String hash, final String key, final Type type, final T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        remoteSource.hset(hash, key, t, val);
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public <T> CompletableFuture<Void> remoteSetAsync(String hash, String key, Type type, T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        return remoteSource.hsetAsync(hash, key, t, val);
    }

    /**
     * 远程删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long remoteDel(String hash, String key) {
        return remoteSource.hdel(hash, key);
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> remoteDelAsync(String hash, String key) {
        return remoteSource.hdelAsync(hash, key);
    }

    //-------------------------------------- both缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param hash 缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    public <T> T bothGet(final String hash, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(hash, key, t);
        if (CacheValue.isValid(val)) {
            return val.getValue();
        }
        if (remoteSource != null) {
            return CacheValue.get(remoteSource.hget(hash, key, t));
        } else {
            return null;
        }
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param hash 缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> bothGetAsync(final String hash, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(hash, key, t);
        if (CacheValue.isValid(val)) {
            return CompletableFuture.completedFuture(val.getValue());
        }
        if (remoteSource != null) {
            CompletableFuture<CacheValue<T>> future = remoteSource.hgetAsync(hash, key, t);
            return future.thenApply(CacheValue::get);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 远程缓存数据
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public <T> void bothSet(final String hash, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire) {
        Type t = loadCacheType(type, value);
        localSource.hset(hash, key, t, CacheValue.create(value, localExpire));
        if (remoteSource != null) {
            remoteSource.hset(hash, key, t, CacheValue.create(value, remoteExpire));
        }
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public <T> CompletableFuture<Void> bothSetAsync(String hash, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        Type t = loadCacheType(type, value);
        localSource.hset(hash, key, t, CacheValue.create(value, localExpire));
        if (remoteSource != null) {
            return remoteSource.hsetAsync(hash, key, t, CacheValue.create(value, remoteExpire));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 远程删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long bothDel(String hash, String key) {
        long v = localSource.hdel(hash, key);
        if (remoteSource != null) {
            return remoteSource.hdel(hash, key);
        } else {
            return v;
        }
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> bothDelAsync(String hash, String key) {
        long v = localSource.hdel(hash, key);
        if (remoteSource != null) {
            return remoteSource.hdelAsync(hash, key);
        } else {
            return CompletableFuture.completedFuture(v);
        }
    }

    //-------------------------------------- 内部方法 --------------------------------------
    /**
     * 创建数据类型创建对应CacheValue泛型
     *
     * @param type  数据类型，为null则取value的类型
     * @param value 数据值
     *
     * @return CacheValue泛型
     */
    protected Type loadCacheType(Type type, final Object value) {
        return loadCacheType(type == null ? value.getClass() : type);
    }

    /**
     * 创建数据类型创建对应CacheValue泛型
     *
     * @param type 数据类型
     *
     * @return CacheValue泛型
     */
    protected Type loadCacheType(Type type) {
        return cacheValueTypes.computeIfAbsent(type, t -> TypeToken.createParameterizedType(null, CacheValue.class, type));
    }
}
