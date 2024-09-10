/*
 *
 */
package org.redkale.cached;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.redkale.cached.spi.CachedAction;
import org.redkale.inject.Resourcable;
import org.redkale.source.CacheSource;
import org.redkale.util.ThrowSupplier;

/**
 * 缓存管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface CachedManager extends Resourcable {

    /**
     * 默认的schema
     */
    public static final String CACHED_SCHEMA = "cached-schema";

    /**
     * 默认的远程缓存订阅消息的管道名称
     */
    public static final String CACHED_TOPIC = "cached-topic";

    /**
     * 资源名称
     *
     * @return 名称
     */
    @Override
    public String resourceName();

    /**
     * 唯一标识
     *
     * @return  node
     */
    public String getNode();

    /**
     * 缓存的schema, 不能含有':'、'#'、'@'字符
     *
     * @return  schema
     */
    public String getSchema();

    /**
     * 获取本地缓存Source
     *
     * @return  {@link org.redkale.source.CacheSource}
     */
    public CacheSource getLocalSource();

    /**
     * 获取远程缓存Source, 可能为null
     *
     * @return  {@link org.redkale.source.CacheSource}
     */
    public CacheSource getRemoteSource();

    /**
     * 远程缓存订阅消息的管道名称
     *
     * @return 管道名称
     */
    default String getChannelTopic() {
        String n = resourceName();
        if (n.isEmpty()) {
            return CACHED_TOPIC;
        } else {
            return CACHED_TOPIC + ':' + n;
        }
    }

    /**
     * 获取{@link org.redkale.cached.spi.CachedAction}集合
     *
     * @return CachedAction集合
     */
    public List<CachedAction> getCachedActions();

    /**
     * 处理指定缓存key的{@link org.redkale.cached.spi.CachedAction}<br>
     * 可用于动态调整缓存时长
     *
     * @param templetKey 缓存key
     * @param consumer 处理函数
     */
    public void acceptCachedAction(String templetKey, Consumer<CachedAction> consumer);

    // -------------------------------------- 本地缓存 --------------------------------------
    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    public <T> T localGet(final String key, final Type type);

    /**
     * 本地获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     * @return 数据值
     */
    default String localGetString(final String key) {
        return localGet(key, String.class);
    }

    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> T localGetSet(String key, Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier);

    /**
     * 本地异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> CompletableFuture<T> localGetSetAsync(
            String key, Type type, boolean nullable, Duration expire, ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 本地缓存数据
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    public <T> void localSet(String key, Type type, T value, Duration expire);

    /**
     * 本地缓存字符串数据
     *
     * @param key 缓存键
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void localSetString(final String key, final String value, Duration expire) {
        localSet(key, String.class, value, expire);
    }

    /**
     * 本地删除缓存数据
     *
     * @param key 缓存键
     * @return 删除数量
     */
    public long localDel(String key);

    // -------------------------------------- 远程缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    public <T> T remoteGet(final String key, final Type type);

    /**
     * 远程获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     * @return 数据值
     */
    default String remoteGetString(final String key) {
        return remoteGet(key, String.class);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    public <T> CompletableFuture<T> remoteGetAsync(final String key, final Type type);

    /**
     * 远程异步获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     * @return 数据值
     */
    default CompletableFuture<String> remoteGetStringAsync(final String key) {
        return remoteGetAsync(key, String.class);
    }

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> T remoteGetSet(String key, Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier);

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> CompletableFuture<T> remoteGetSetAsync(
            String key, Type type, boolean nullable, Duration expire, ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 远程缓存数据
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    public <T> void remoteSet(final String key, final Type type, final T value, Duration expire);

    /**
     * 远程缓存字符串数据
     *
     * @param key 缓存键
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    default void remoteSetString(final String key, final String value, Duration expire) {
        remoteSet(key, String.class, value, expire);
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @return void
     */
    public <T> CompletableFuture<Void> remoteSetAsync(String key, Type type, T value, Duration expire);

    /**
     * 远程异步缓存字符串数据
     *
     * @param key 缓存键
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @return void
     */
    default CompletableFuture<Void> remoteSetStringAsync(final String key, final String value, Duration expire) {
        return remoteSetAsync(key, String.class, value, expire);
    }

    /**
     * 远程删除缓存数据
     *
     * @param key 缓存键
     * @return 删除数量
     */
    public long remoteDel(String key);

    /**
     * 远程异步删除缓存数据
     *
     * @param key 缓存键
     * @return 删除数量
     */
    public CompletableFuture<Long> remoteDelAsync(String key);

    // -------------------------------------- both缓存 --------------------------------------
    /**
     * 本地或远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    public <T> T bothGet(final String key, final Type type);

    /**
     * 本地或远程获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     * @return 数据值
     */
    default String bothGetString(final String key) {
        return bothGet(key, String.class);
    }

    /**
     * 本地或远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    public <T> CompletableFuture<T> bothGetAsync(final String key, final Type type);

    /**
     * 本地或远程异步获取字符串缓存数据, 过期返回null
     *
     * @param key 缓存键
     * @return 数据值
     */
    default CompletableFuture<String> bothGetStringAsync(final String key) {
        return bothGetAsync(key, String.class);
    }

    /**
     * 本地或远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> T bothGetSet(
            String key,
            Type type,
            boolean nullable,
            Duration localExpire,
            Duration remoteExpire,
            ThrowSupplier<T> supplier);

    /**
     * 本地或远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier 数据函数
     * @return 数据值
     */
    public <T> CompletableFuture<T> bothGetSetAsync(
            String key,
            Type type,
            boolean nullable,
            Duration localExpire,
            Duration remoteExpire,
            ThrowSupplier<CompletableFuture<T>> supplier);

    /**
     * 本地和远程缓存数据
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    public <T> void bothSet(String key, Type type, T value, Duration localExpire, Duration remoteExpire);

    /**
     * 本地和远程缓存字符串数据
     *
     * @param key 缓存键
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    default void bothSetString(final String key, final String value, Duration localExpire, Duration remoteExpire) {
        bothSet(key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程异步缓存数据
     *
     * @param <T> 泛型
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @return void
     */
    public <T> CompletableFuture<Void> bothSetAsync(
            String key, Type type, T value, Duration localExpire, Duration remoteExpire);

    /**
     * 本地和远程异步缓存字符串数据
     *
     * @param key 缓存键
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @return void
     */
    default CompletableFuture<Void> bothSetStringAsync(
            String key, String value, Duration localExpire, Duration remoteExpire) {
        return bothSetAsync(key, String.class, value, localExpire, remoteExpire);
    }

    /**
     * 本地和远程删除缓存数据
     *
     * @param key 缓存键
     * @return 删除数量
     */
    public long bothDel(String key);

    /**
     * 本地和远程异步删除缓存数据
     *
     * @param key 缓存键
     * @return 删除数量
     */
    public CompletableFuture<Long> bothDelAsync(String key);
}
