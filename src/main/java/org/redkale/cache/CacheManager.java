/*
 *
 */
package org.redkale.cache;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 缓存管理器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public interface CacheManager {

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
    public <T> T localGet(final String hash, final String key, final Type type);

    /**
     * 本地获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default String localGetString(final String hash, final String key) {
        return localGet(hash, key, String.class);
    }

    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param expire   过期时长，为null表示永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> T localGet(final String hash, final String key, final Type type, Duration expire, Supplier<T> supplier);

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param expire   过期时长，为null表示永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> localGetAsync(String hash, String key, Type type, Duration expire, Supplier<CompletableFuture<T>> supplier);

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
    public <T> void localSet(String hash, String key, Type type, T value, Duration expire);

    /**
     * 本地缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    default void localSetString(final String hash, final String key, final String value, Duration expire) {
        localSet(hash, key, String.class, value, expire);
    }

    /**
     * 本地删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long localDel(String hash, String key);

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
    public <T> T remoteGet(final String hash, final String key, final Type type);

    /**
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default String remoteGetString(final String hash, final String key) {
        return remoteGet(hash, key, String.class);
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
    public <T> CompletableFuture<T> remoteGetAsync(final String hash, final String key, final Type type);

    /**
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default CompletableFuture<String> remoteGetStringAsync(final String hash, final String key) {
        return remoteGetAsync(hash, key, String.class);
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
    public <T> void remoteSet(final String hash, final String key, final Type type, final T value, Duration expire);

    /**
     * 远程缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    default void remoteSetString(final String hash, final String key, final String value, Duration expire) {
        remoteSet(hash, key, String.class, value, expire);
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
    public <T> CompletableFuture<Void> remoteSetAsync(String hash, String key, Type type, T value, Duration expire);

    /**
     * 远程异步缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，为null表示永不过期
     */
    default CompletableFuture<Void> remoteSetStringAsync(final String hash, final String key, final String value, Duration expire) {
        return remoteSetAsync(hash, key, String.class, value, expire);
    }

    /**
     * 远程删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long remoteDel(String hash, String key);

    /**
     * 远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> remoteDelAsync(String hash, String key);

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
    public <T> T bothGet(final String hash, final String key, final Type type);

    /**
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default String bothGetString(final String hash, final String key) {
        return bothGet(hash, key, String.class);
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
    public <T> CompletableFuture<T> bothGetAsync(final String hash, final String key, final Type type);

    /**
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default CompletableFuture<String> bothGetStringAsync(final String hash, final String key) {
        return bothGetAsync(hash, key, String.class);
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
    public <T> void bothSet(final String hash, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire);

    /**
     * 远程缓存字符串数据
     *
     * @param hash         缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    default void bothSetString(final String hash, final String key, final String value, Duration localExpire, Duration remoteExpire) {
        bothSet(hash, key, String.class, value, localExpire, remoteExpire);
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
    public <T> CompletableFuture<Void> bothSetAsync(String hash, String key, Type type, T value, Duration localExpire, Duration remoteExpire);

    /**
     * 远程异步缓存字符串数据
     *
     * @param hash         缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，为null表示永不过期
     * @param remoteExpire 远程过期时长，为null表示永不过期
     */
    default CompletableFuture<Void> bothSetStringAsync(String hash, String key, String value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(hash, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 远程删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long bothDel(String hash, String key);

    /**
     * 远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> bothDelAsync(String hash, String key);

}
