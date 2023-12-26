/*
 *
 */
package org.redkale.cache;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.redkale.util.ThrowSupplier;

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

    /**
     * 默认的hash
     */
    public static final String DEFAULT_HASH = "cache-hash";

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
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    default <T> T localGet(final String key, final Type type) {
        return localGet(DEFAULT_HASH, key, type);
    }

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
     * 本地获取字符串缓存数据, 过期返回null
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 数据值
     */
    default String localGetString(final String key) {
        return localGetString(DEFAULT_HASH, key);
    }

    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> T localGetSet(final String hash, final String key, final Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier);

    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    default <T> T localGetSet(final String key, final Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier) {
        return localGetSet(DEFAULT_HASH, key, type, nullable, expire, supplier);
    }

    /**
     * 本地异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> localGetSetAsync(String hash, String key, Type type, boolean nullable, Duration expire, ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 本地异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    default <T> CompletableFuture<T> localGetSetAsync(String key, Type type, boolean nullable, Duration expire, ThrowSupplier<CompletableFuture<T>> supplier) {
        return localGetSetAsync(DEFAULT_HASH, key, type, nullable, expire, supplier);
    }

    /**
     * 本地缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    public <T> void localSet(String hash, String key, Type type, T value, Duration expire);

    /**
     * 本地缓存数据
     *
     * @param <T>    泛型
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default <T> void localSet(String key, Type type, T value, Duration expire) {
        localSet(DEFAULT_HASH, key, type, value, expire);
    }

    /**
     * 本地缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void localSetString(final String hash, final String key, final String value, Duration expire) {
        localSet(hash, key, String.class, value, expire);
    }

    /**
     * 本地缓存字符串数据
     *
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void localSetString(final String key, final String value, Duration expire) {
        localSetString(DEFAULT_HASH, key, value, expire);
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

    /**
     * 本地删除缓存数据
     *
     * @param key 缓存键
     *
     * @return 删除数量
     */
    default long localDel(String key) {
        return localDel(DEFAULT_HASH, key);
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
    public <T> T remoteGet(final String hash, final String key, final Type type);

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    default <T> T remoteGet(final String key, final Type type) {
        return remoteGet(DEFAULT_HASH, key, type);
    }

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
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     *
     * @return 数据值
     */
    default String remoteGetString(final String key) {
        return remoteGetString(DEFAULT_HASH, key);
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
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    default <T> CompletableFuture<T> remoteGetAsync(final String key, final Type type) {
        return remoteGetAsync(DEFAULT_HASH, key, type);
    }

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
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     *
     * @return 数据值
     */
    default CompletableFuture<String> remoteGetStringAsync(final String key) {
        return remoteGetStringAsync(DEFAULT_HASH, key);
    }

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> T remoteGetSet(final String hash, final String key, final Type type, boolean nullable,
        Duration expire, ThrowSupplier<T> supplier);

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    default <T> T remoteGetSet(final String key, final Type type, boolean nullable,
        Duration expire, ThrowSupplier<T> supplier) {
        return remoteGetSet(DEFAULT_HASH, key, type, nullable, expire, supplier);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> remoteGetSetAsync(String hash, String key, Type type, boolean nullable,
        Duration expire, ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    default <T> CompletableFuture<T> remoteGetSetAsync(String key, Type type, boolean nullable,
        Duration expire, ThrowSupplier<CompletableFuture<T>> supplier) {
        return remoteGetSetAsync(DEFAULT_HASH, key, type, nullable, expire, supplier);
    }

    /**
     * 远程缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    public <T> void remoteSet(final String hash, final String key, final Type type, final T value, Duration expire);

    /**
     * 远程缓存数据
     *
     * @param <T>    泛型
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default <T> void remoteSet(final String key, final Type type, final T value, Duration expire) {
        remoteSet(DEFAULT_HASH, key, type, value, expire);
    }

    /**
     * 远程缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void remoteSetString(final String hash, final String key, final String value, Duration expire) {
        remoteSet(hash, key, String.class, value, expire);
    }

    /**
     * 远程缓存字符串数据
     *
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void remoteSetString(final String key, final String value, Duration expire) {
        remoteSetString(DEFAULT_HASH, key, value, expire);
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T>    泛型
     * @param hash   缓存hash
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     *
     * @return void
     */
    public <T> CompletableFuture<Void> remoteSetAsync(String hash, String key, Type type, T value, Duration expire);

    /**
     * 远程异步缓存数据
     *
     * @param <T>    泛型
     * @param key    缓存键
     * @param type   数据类型
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     *
     * @return void
     */
    default <T> CompletableFuture<Void> remoteSetAsync(String key, Type type, T value, Duration expire) {
        return remoteSetAsync(DEFAULT_HASH, type, value, expire);
    }

    /**
     * 远程异步缓存字符串数据
     *
     * @param hash   缓存hash
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     *
     * @return void
     */
    default CompletableFuture<Void> remoteSetStringAsync(final String hash, final String key, final String value, Duration expire) {
        return remoteSetAsync(hash, key, String.class, value, expire);
    }

    /**
     * 远程异步缓存字符串数据
     *
     * @param key    缓存键
     * @param value  数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     *
     * @return void
     */
    default CompletableFuture<Void> remoteSetStringAsync(final String key, final String value, Duration expire) {
        return remoteSetStringAsync(DEFAULT_HASH, key, value, expire);
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
     * 远程删除缓存数据
     *
     * @param key 缓存键
     *
     * @return 删除数量
     */
    default long remoteDel(String key) {
        return remoteDel(DEFAULT_HASH, key);
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> remoteDelAsync(String hash, String key);

    /**
     * 远程异步删除缓存数据
     *
     * @param key 缓存键
     *
     * @return 删除数量
     */
    default CompletableFuture<Long> remoteDelAsync(String key) {
        return remoteDelAsync(DEFAULT_HASH, key);
    }

    //-------------------------------------- both缓存 --------------------------------------
    /**
     * 本地或远程获取缓存数据, 过期返回null
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
     * 本地或远程获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    default <T> T bothGet(final String key, final Type type) {
        return bothGet(DEFAULT_HASH, key, type);
    }

    /**
     * 本地或远程获取字符串缓存数据, 过期返回null
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
     * 本地或远程获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     *
     * @return 数据值
     */
    default String bothGetString(final String key) {
        return bothGetString(DEFAULT_HASH, key);
    }

    /**
     * 本地或远程异步获取缓存数据, 过期返回null
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
     * 本地或远程异步获取缓存数据, 过期返回null
     *
     * @param <T>  泛型
     * @param key  缓存键
     * @param type 数据类型
     *
     * @return 数据值
     */
    default <T> CompletableFuture<T> bothGetAsync(final String key, final Type type) {
        return bothGetAsync(DEFAULT_HASH, key, type);
    }

    /**
     * 本地或远程异步获取字符串缓存数据, 过期返回null
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
     * 本地或远程异步获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     *
     * @return 数据值
     */
    default CompletableFuture<String> bothGetStringAsync(final String key) {
        return bothGetStringAsync(DEFAULT_HASH, key);
    }

    /**
     * 本地或远程获取缓存数据, 过期返回null
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param nullable     是否缓存null值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier     数据函数
     *
     * @return 数据值
     */
    public <T> T bothGetSet(String hash, String key, Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, ThrowSupplier<T> supplier);

    /**
     * 本地或远程获取缓存数据, 过期返回null
     *
     * @param <T>          泛型
     * @param key          缓存键
     * @param type         数据类型
     * @param nullable     是否缓存null值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier     数据函数
     *
     * @return 数据值
     */
    default <T> T bothGetSet(String key, Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, ThrowSupplier<T> supplier) {
        return bothGetSet(DEFAULT_HASH, key, type, nullable, localExpire, remoteExpire, supplier);
    }

    /**
     * 本地或远程异步获取缓存数据, 过期返回null
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param nullable     是否缓存null值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier     数据函数
     *
     * @return 数据值
     */
    public <T> CompletableFuture<T> bothGetSetAsync(String hash, String key, Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 本地或远程异步获取缓存数据, 过期返回null
     *
     * @param <T>          泛型
     * @param key          缓存键
     * @param type         数据类型
     * @param nullable     是否缓存null值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier     数据函数
     *
     * @return 数据值
     */
    default <T> CompletableFuture<T> bothGetSetAsync(String key, Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, ThrowSupplier<CompletableFuture<T>> supplier) {
        return bothGetSetAsync(DEFAULT_HASH, key, type, nullable, localExpire, remoteExpire, supplier);
    }

    /**
     * 本地和远程缓存数据
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    public <T> void bothSet(final String hash, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire);

    /**
     * 本地和远程缓存数据
     *
     * @param <T>          泛型
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    default <T> void bothSet(final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire) {
        bothSet(DEFAULT_HASH, key, type, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程缓存字符串数据
     *
     * @param hash         缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    default void bothSetString(final String hash, final String key, final String value, Duration localExpire, Duration remoteExpire) {
        bothSet(hash, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程缓存字符串数据
     *
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    default void bothSetString(final String key, final String value, Duration localExpire, Duration remoteExpire) {
        bothSet(DEFAULT_HASH, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程异步缓存数据
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     *
     * @return void
     */
    public <T> CompletableFuture<Void> bothSetAsync(String hash, String key, Type type, T value, Duration localExpire, Duration remoteExpire);

    /**
     * 本地和远程异步缓存数据
     *
     * @param <T>          泛型
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     *
     * @return void
     */
    default <T> CompletableFuture<Void> bothSetAsync(String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(DEFAULT_HASH, key, type, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程异步缓存字符串数据
     *
     * @param hash         缓存hash
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     *
     * @return void
     */
    default CompletableFuture<Void> bothSetStringAsync(String hash, String key, String value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(hash, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程异步缓存字符串数据
     *
     * @param key          缓存键
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     *
     * @return void
     */
    default CompletableFuture<Void> bothSetStringAsync(String key, String value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(DEFAULT_HASH, key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public long bothDel(String hash, String key);

    /**
     * 本地和远程删除缓存数据
     *
     * @param key 缓存键
     *
     * @return 删除数量
     */
    default long bothDel(String key) {
        return bothDel(DEFAULT_HASH, key);
    }

    /**
     * 本地和远程异步删除缓存数据
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return 删除数量
     */
    public CompletableFuture<Long> bothDelAsync(String hash, String key);

    /**
     * 本地和远程异步删除缓存数据
     *
     * @param key 缓存键
     *
     * @return 删除数量
     */
    default CompletableFuture<Long> bothDelAsync(String key) {
        return bothDelAsync(DEFAULT_HASH, key);
    }

}
