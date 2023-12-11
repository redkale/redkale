/*
 *
 */
package org.redkale.caching;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nonnull;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.ResourceType;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.util.AnyValue;
import org.redkale.util.TypeToken;

/**
 *
 * @author zhangjx
 */
@Local
@Component
@AutoLoad(false)
@ResourceType(CacheManager.class)
public class CacheManagerService implements CacheManager, Service {

    //缓存配置项
    protected final CacheConfig config;

    //数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    //本地缓存Source
    protected final CacheMemorySource localSource = new CacheMemorySource("caching");

    //远程缓存Source
    protected CacheSource remoteSource;

    protected CacheManagerService(@Nonnull CacheConfig config, @Nullable CacheSource remoteSource) {
        this.config = Objects.requireNonNull(config);
        this.remoteSource = remoteSource;
    }

    public static CacheManagerService create(@Nonnull CacheConfig config, @Nullable CacheSource remoteSource) {
        return new CacheManagerService(config, remoteSource);
    }

    @Override
    public void init(AnyValue conf) {
        this.localSource.init(conf);
    }

    @Override
    public void destroy(AnyValue conf) {
        this.localSource.destroy(conf);
    }

    //-------------------------------------- 本地缓存 --------------------------------------
    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param map  缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> T localGet(final String map, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(map, key, t);
        return CacheValue.get(val);
    }

    /**
     * 本地缓存数据
     *
     * @param <T>    泛型
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    @Override
    public <T> void localSet(String map, String key, Type type, T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        localSource.hset(map, key, t, val);
    }

    /**
     * 本地删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    @Override
    public long localDel(String map, String key) {
        return localSource.hdel(map, key);
    }

    //-------------------------------------- 远程缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param map  缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> T remoteGet(final String map, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = remoteSource.hget(map, key, t);
        return CacheValue.get(val);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param map  缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> remoteGetAsync(final String map, final String key, final Type type) {
        Type t = loadCacheType(type);
        CompletableFuture<CacheValue<T>> future = remoteSource.hgetAsync(map, key, t);
        return future.thenApply(CacheValue::get);
    }

    /**
     * 远程缓存数据
     *
     * @param <T>    泛型
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public <T> void remoteSet(final String map, final String key, final Type type, final T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        remoteSource.hset(map, key, t, val);
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T>    泛型
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public <T> CompletableFuture<Void> remoteSetAsync(String map, String key, Type type, T value, Duration expire) {
        Type t = loadCacheType(type, value);
        CacheValue val = CacheValue.create(value, expire);
        return remoteSource.hsetAsync(map, key, t, val);
    }

    /**
     * 远程删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    public long remoteDel(String map, String key) {
        return remoteSource.hdel(map, key);
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> remoteDelAsync(String map, String key) {
        return remoteSource.hdelAsync(map, key);
    }

    //-------------------------------------- both缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param map  缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    public <T> T bothGet(final String map, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(map, key, t);
        if (val != null && !val.isExpired()) {
            return val.getValue();
        }
        return CacheValue.get(remoteSource.hget(map, key, t));
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param map  缓存hash
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> bothGetAsync(final String map, final String key, final Type type) {
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(map, key, t);
        if (val != null && !val.isExpired()) {
            return CompletableFuture.completedFuture(val.getValue());
        }
        CompletableFuture<CacheValue<T>> future = remoteSource.hgetAsync(map, key, t);
        return future.thenApply(CacheValue::get);
    }

    /**
     * 远程缓存数据
     *
     * @param <T>          泛型
     * @param map          缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public <T> void bothSet(final String map, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire) {
        Type t = loadCacheType(type, value);
        localSource.hset(map, key, t, CacheValue.create(value, localExpire));
        remoteSource.hset(map, key, t, CacheValue.create(value, remoteExpire));
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T>          泛型
     * @param map          缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public <T> CompletableFuture<Void> bothSetAsync(String map, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        Type t = loadCacheType(type, value);
        localSource.hset(map, key, t, CacheValue.create(value, localExpire));
        return remoteSource.hsetAsync(map, key, t, CacheValue.create(value, remoteExpire));
    }

    /**
     * 远程删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    public long bothDel(String map, String key) {
        localSource.hdel(map, key);
        return remoteSource.hdel(map, key);
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> bothDelAsync(String map, String key) {
        localSource.hdel(map, key);
        return remoteSource.hdelAsync(map, key);
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
