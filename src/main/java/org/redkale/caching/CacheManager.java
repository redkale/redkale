/*
 *
 */
package org.redkale.caching;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.Nonnull;
import org.redkale.annotation.Nullable;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.util.TypeToken;

/**
 * //TODO 待实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class CacheManager {

    //缓存配置项
    protected final CacheConfig config;

    //数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    //本地缓存Source
    protected final CacheSource localSource = new CacheMemorySource("caching");

    //远程缓存Source
    protected CacheSource remoteSource;

    protected CacheManager(@Nonnull CacheConfig config, @Nullable CacheSource remoteSource) {
        this.config = Objects.requireNonNull(config);
        this.remoteSource = remoteSource;
    }

    public static CacheManager create(@Nonnull CacheConfig config, @Nullable CacheSource remoteSource) {
        return new CacheManager(config, remoteSource);
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
    public <T> T localGet(final String map, final String key, final Type type) {
        return get(localSource, map, key, type);
    }

    /**
     * 本地获取字符串缓存数据, 过期返回null
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 数据值
     */
    public final String localGetString(final String map, final String key) {
        return get(localSource, map, key, String.class);
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
    public <T> void localSet(final String map, final String key, final Type type, final T value, Duration expire) {
        set(localSource, map, key, type, value, expire);
    }

    /**
     * 本地缓存字符串数据
     *
     * @param map    缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public void localSetString(final String map, final String key, final String value, Duration expire) {
        set(localSource, map, key, String.class, value, expire);
    }

    /**
     * 本地删除缓存数据
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 删除数量
     */
    public long localDel(String map, String key) {
        return del(localSource, map, key);
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
    public <T> T remoteGet(final String map, final String key, final Type type) {
        return get(remoteSource, map, key, type);
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
    public <T> CompletableFuture<T> remoteGetAsync(final String map, final String key, final Type type) {
        return getAsync(remoteSource, map, key, type);
    }

    /**
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 数据值
     */
    public final String remoteGetString(final String map, final String key) {
        return get(remoteSource, map, key, String.class);
    }

    /**
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 数据值
     */
    public final CompletableFuture<String> remoteGetStringAsync(final String map, final String key) {
        return getAsync(remoteSource, map, key, String.class);
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
        set(remoteSource, map, key, type, value, expire);
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
    public <T> CompletableFuture<Void> remoteSetAsync(final String map, final String key, final Type type, final T value, Duration expire) {
        return setAsync(remoteSource, map, key, type, value, expire);
    }

    /**
     * 远程缓存字符串数据
     *
     * @param map    缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public void remoteSetString(final String map, final String key, final String value, Duration expire) {
        set(remoteSource, map, key, String.class, value, expire);
    }

    /**
     * 远程异步缓存字符串数据
     *
     * @param map    缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    public CompletableFuture<Void> remoteSetStringAsync(final String map, final String key, final String value, Duration expire) {
        return setAsync(remoteSource, map, key, String.class, value, expire);
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
        return del(remoteSource, map, key);
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
        return delAsync(remoteSource, map, key);
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
        T val = get(localSource, map, key, type);
        return val == null ? get(remoteSource, map, key, type) : val;
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
        T val = get(localSource, map, key, type);
        if (val != null) {
            return CompletableFuture.completedFuture(val);
        }
        return getAsync(remoteSource, map, key, type);
    }

    /**
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 数据值
     */
    public final String bothGetString(final String map, final String key) {
        return bothGet(map, key, String.class);
    }

    /**
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param map 缓存hash
     * @param key 缓存键
     *
     * @return 数据值
     */
    public final CompletableFuture<String> bothGetStringAsync(final String map, final String key) {
        return bothGetAsync(map, key, String.class);
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
        set(localSource, map, key, type, value, localExpire);
        set(remoteSource, map, key, type, value, remoteExpire);
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
    public <T> CompletableFuture<Void> bothSetAsync(final String map, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire) {
        set(localSource, map, key, type, value, localExpire);
        return setAsync(remoteSource, map, key, type, value, remoteExpire);
    }

    /**
     * 远程缓存字符串数据
     *
     * @param map          缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public void bothSetString(final String map, final String key, final String value, Duration localExpire, Duration remoteExpire) {
        bothSet(map, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 远程异步缓存字符串数据
     *
     * @param map          缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    public CompletableFuture<Void> bothSetStringAsync(final String map, final String key, final String value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(map, key, String.class, value, localExpire, remoteExpire);
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
        del(localSource, map, key);
        return del(remoteSource, map, key);
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
        del(localSource, map, key);
        return delAsync(remoteSource, map, key);
    }

    //-------------------------------------- 内部方法 --------------------------------------
    /**
     * 获取缓存数据, 过期返回null
     *
     * @param <T>    泛型
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     *
     * @return 数据值
     */
    protected <T> T get(final CacheSource source, final String map, final String key, final Type type) {
        CacheValue<T> val = source.hget(map, key, loadCacheType(type));
        return val != null && !val.isExpired() ? val.getValue() : null;
    }

    /**
     * 获取缓存数据, 过期返回null
     *
     * @param <T>    泛型
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     *
     * @return 数据值
     */
    protected <T> CompletableFuture<T> getAsync(final CacheSource source, final String map, final String key, final Type type) {
        return source.hgetAsync(map, key, loadCacheType(type)).thenApply(v -> {
            CacheValue<T> val = (CacheValue) v;
            return val != null && !val.isExpired() ? (T) val.getValue() : null;
        });
    }

    /**
     * 缓存数据
     *
     * @param <T>    泛型
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    protected <T> void set(final CacheSource source, final String map, final String key, final Type type, final T value, Duration expire) {
        Type t = loadCacheType(type, value);
        source.hset(map, key, t, CacheValue.create(value, expire));
    }

    /**
     * 缓存数据
     *
     * @param <T>    泛型
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    protected <T> CompletableFuture<Void> setAsync(final CacheSource source, final String map, final String key, final Type type, final T value, Duration expire) {
        Type t = loadCacheType(type, value);
        return source.hsetAsync(map, key, t, CacheValue.create(value, expire));
    }

    /**
     * 删除缓存数据
     *
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     *
     * @return 删除数量
     */
    protected long del(final CacheSource source, String map, String key) {
        return source.hdel(map, key);
    }

    /**
     * 删除缓存数据
     *
     * @param source 缓存源
     * @param map    缓存hash
     * @param key    缓存键
     *
     * @return 删除数量
     */
    protected CompletableFuture<Long> delAsync(final CacheSource source, String map, String key) {
        return source.hdelAsync(map, key);
    }

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
