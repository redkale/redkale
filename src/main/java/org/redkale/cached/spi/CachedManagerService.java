/*
 *
 */
package org.redkale.cached.spi;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.boot.Application;
import org.redkale.cached.CachedManager;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.source.CacheEventListener;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleException;
import org.redkale.util.ThrowSupplier;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * 缓存管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Local
@Component
@AutoLoad(false)
@ResourceType(CachedManager.class)
public class CachedManagerService implements CachedManager, CachedActionFunc, Service {

    protected final Logger logger = Logger.getLogger(getClass().getSimpleName());

    protected Level logLevel = Level.FINER;

    // 唯一标识
    protected final String node = Utility.uuid();

    // 名称
    protected String name;

    // 缓存的hash
    protected String schema;

    // 是否开启缓存
    protected boolean enabled = true;

    // 是否开启本地缓存变更通知
    protected boolean broadcastable = true;

    // 配置
    protected AnyValue config;

    // 数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    // 本地缓存Source
    protected final CacheMemorySource localSource = new CacheMemorySource("cache-local");

    // 缓存无效时使用的同步锁
    private final ConcurrentHashMap<String, CachedValue> syncLockMap = new ConcurrentHashMap<>();

    // 缓存无效时使用的异步锁
    private final ConcurrentHashMap<String, CachedAsyncLock> asyncLockMap = new ConcurrentHashMap<>();

    protected final List<CachedAction> actions = new CopyOnWriteArrayList<>();

    @Resource(required = false)
    protected Application application;

    // 远程缓存Source
    protected CacheSource remoteSource;

    protected CacheEventListener remoteListener;

    protected CachedManagerService(@Nullable CacheSource remoteSource) {
        this.remoteSource = remoteSource;
        this.name = "";
    }

    // 一般用于独立组件
    public static CachedManagerService create(@Nullable CacheSource remoteSource) {
        return new CachedManagerService(remoteSource);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public CachedManagerService enabled(boolean val) {
        this.enabled = val;
        return this;
    }

    @Override
    public void init(AnyValue conf) {
        this.config = conf;
        if (conf == null) {
            conf = AnyValue.create();
        }
        this.name = conf.getValue("name", "");
        this.enabled = conf.getBoolValue("enabled", true);
        this.schema = checkSchema(conf.getValue("schema", CACHED_SCHEMA));
        if (this.enabled) {
            this.localSource.init(conf);
            String remoteSourceName = conf.getValue("remote", "");
            if (remoteSource == null && remoteSourceName != null) {
                CacheSource source = application.loadCacheSource(remoteSourceName, false);
                if (source == null && !remoteSourceName.isEmpty()) {
                    throw new RedkaleException("Not found CacheSource '" + remoteSourceName + "'");
                }
                this.remoteSource = source;
                this.broadcastable = conf.getBoolValue("broadcastable", true);
            }
            if (remoteSource != null && this.broadcastable) {
                this.remoteListener = new CacheRemoteListener();
                this.remoteSource.subscribe(CachedEventMessage.class, remoteListener, getChannelTopic());
            }
        }
    }

    /**
     * 检查name是否含特殊字符
     *
     * @param value 参数
     * @return value
     */
    protected String checkSchema(String value) {
        if (value != null && !value.isEmpty()) {
            for (char ch : value.toCharArray()) {
                if (ch == ':' || ch == '#' || ch == '@') { // 不能含特殊字符
                    throw new RedkaleException("schema cannot contains : # @");
                }
            }
        }
        return value;
    }

    @Override
    public void destroy(AnyValue conf) {
        if (this.enabled) {
            this.localSource.destroy(conf);
        }
        if (this.remoteSource != null && this.remoteListener != null) {
            this.remoteSource.unsubscribe(remoteListener, getChannelTopic());
        }
    }

    @Override
    public String resourceName() {
        return name;
    }

    @Override
    public String getNode() {
        return node;
    }

    @Override
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema == null ? "" : schema.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateBroadcastable(boolean broadcastable) {
        CacheSource remote = this.remoteSource;
        if (this.broadcastable != broadcastable && remote != null) {
            if (broadcastable) {
                this.remoteListener = new CacheRemoteListener();
                remote.subscribe(CachedEventMessage.class, remoteListener, getChannelTopic());
            } else {
                if (this.remoteListener != null) {
                    remote.unsubscribe(remoteListener, getChannelTopic());
                    this.remoteListener = null;
                }
            }
            this.broadcastable = broadcastable;
        }
    }

    /**
     * 获取本地缓存Source
     *
     * @return  {@link org.redkale.source.CacheSource}
     */
    @Override
    public CacheSource getLocalSource() {
        return localSource;
    }

    /**
     * 获取远程缓存Source, 可能为null
     *
     * @return  {@link org.redkale.source.CacheSource}
     */
    @Override
    public CacheSource getRemoteSource() {
        return remoteSource;
    }

    @Override
    public void addAction(CachedAction action) {
        actions.add(action);
    }

    /**
     * 获取{@link org.redkale.cached.spi.CachedAction}集合
     *
     * @return CachedAction集合
     */
    @Override
    public List<CachedAction> getCachedActions() {
        return new ArrayList<>(actions);
    }

    /**
     * 处理指定缓存key的{@link org.redkale.cached.spi.CachedAction}
     *
     * @param name 缓存名称
     * @param consumer 处理函数
     */
    @Override
    public void acceptCachedAction(String name, Consumer<CachedAction> consumer) {
        actions.stream().filter(v -> Objects.equals(v.getName(), name)).forEach(consumer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hash(this) + "{name='" + name + "', schema='" + schema + "'}";
    }

    // -------------------------------------- 本地缓存 --------------------------------------
    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    @Override
    public <T> T localGet(String name, String key, Type type) {
        checkEnable();
        return CachedValue.get(localSource.get(idFor(name, key), loadCacheType(type)));
    }

    /**
     * 本地获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> T localGetSet(
            String name, String key, Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier) {
        return getSet(
                (n, k, ex, ct) -> localSource.get(idFor(n, k), ct),
                this::localSetCache,
                name,
                key,
                type,
                nullable,
                expire,
                supplier);
    }

    /**
     * 本地异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> localGetSetAsync(
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration expire,
            ThrowSupplier<CompletableFuture<T>> supplier) {
        return getSetAsync(
                (n, k, e, c) -> localSource.getAsync(idFor(n, k), c),
                this::localSetCacheAsync,
                name,
                key,
                type,
                nullable,
                expire,
                supplier);
    }

    /**
     * 本地缓存数据
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    @Override
    public <T> void localSet(String name, String key, Type type, T value, Duration expire) {
        localSetCache(name, key, type, value, expire);
    }

    /**
     * 本地删除缓存数据
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return 删除数量
     */
    @Override
    public long localDel(String name, String key) {
        checkEnable();
        return localSource.del(idFor(name, key));
    }

    // -------------------------------------- 远程缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    @Override
    public <T> T remoteGet(String name, String key, Type type) {
        checkEnable();
        return CachedValue.get(remoteSource.get(idFor(name, key), loadCacheType(type)));
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> remoteGetAsync(String name, String key, Type type) {
        checkEnable();
        CompletableFuture<CachedValue<T>> future = remoteSource.getAsync(idFor(name, key), loadCacheType(type));
        return future.thenApply(CachedValue::get);
    }

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> T remoteGetSet(
            String name, String key, Type type, boolean nullable, Duration expire, ThrowSupplier<T> supplier) {
        return getSet(
                (n, k, ex, ct) -> remoteSource.get(idFor(n, k), ct),
                this::remoteSetCache,
                name,
                key,
                type,
                nullable,
                expire,
                supplier);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> remoteGetSetAsync(
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration expire,
            ThrowSupplier<CompletableFuture<T>> supplier) {
        return getSetAsync(
                (n, k, ex, ct) -> remoteSource.getAsync(idFor(n, k), ct),
                this::remoteSetCacheAsync,
                name,
                key,
                type,
                nullable,
                expire,
                supplier);
    }

    /**
     * 远程缓存数据
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    @Override
    public <T> void remoteSet(String name, String key, Type type, T value, Duration expire) {
        remoteSetCache(name, key, type, value, expire);
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param expire 过期时长，Duration.ZERO为永不过期
     */
    @Override
    public <T> CompletableFuture<Void> remoteSetAsync(String name, String key, Type type, T value, Duration expire) {
        return remoteSetCacheAsync(name, key, type, value, expire);
    }

    /**
     * 远程删除缓存数据
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return 删除数量
     */
    @Override
    public long remoteDel(String name, String key) {
        checkEnable();
        return remoteSource.del(idFor(name, key));
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return 删除数量
     */
    @Override
    public CompletableFuture<Long> remoteDelAsync(String name, String key) {
        checkEnable();
        return remoteSource.delAsync(idFor(name, key));
    }

    // -------------------------------------- both缓存 --------------------------------------
    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    @Override
    public <T> T bothGet(String name, String key, Type type) {
        return CachedValue.get(bothGetCache(name, key, (Duration) null, type));
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> bothGetAsync(String name, String key, Type type) {
        return bothGetCacheAsync(name, key, (Duration) null, type).thenApply(CachedValue::get);
    }

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> T bothGetSet(
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration localExpire,
            Duration remoteExpire,
            ThrowSupplier<T> supplier) {
        if (!enabled) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RedkaleException(t);
            }
        }
        if (remoteExpire == null) { // 只有本地缓存
            Objects.requireNonNull(localExpire);
            return localGetSet(name, key, type, nullable, localExpire, supplier);
        }
        if (localExpire == null) { // 只有远程缓存
            Objects.requireNonNull(remoteExpire);
            return remoteGetSet(name, key, type, nullable, remoteExpire, supplier);
        }
        return getSet(
                this::bothGetCache,
                (n, k, e, t, v) -> {
                    localSetCache(n, k, localExpire, t, v);
                    if (remoteSource != null) {
                        remoteSetCache(n, k, remoteExpire, t, v);
                    }
                },
                name,
                key,
                type,
                nullable,
                localExpire,
                supplier);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier 数据函数
     * @return 数据值
     */
    @Override
    public <T> CompletableFuture<T> bothGetSetAsync(
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration localExpire,
            Duration remoteExpire,
            ThrowSupplier<CompletableFuture<T>> supplier) {
        if (!enabled) {
            try {
                return supplier.get();
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        if (remoteExpire == null) { // 只有本地缓存
            Objects.requireNonNull(localExpire);
            return localGetSetAsync(name, key, type, nullable, localExpire, supplier);
        }
        if (localExpire == null) { // 只有远程缓存
            Objects.requireNonNull(remoteExpire);
            return remoteGetSetAsync(name, key, type, nullable, remoteExpire, supplier);
        }
        return getSetAsync(
                this::bothGetCacheAsync,
                (n, k, e, t, v) -> {
                    localSetCache(n, k, localExpire, t, v);
                    if (remoteSource != null) {
                        return remoteSetCacheAsync(n, k, remoteExpire, t, v);
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                },
                name,
                key,
                type,
                nullable,
                localExpire,
                supplier);
    }

    /**
     * 远程缓存数据
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    @Override
    public <T> void bothSet(String name, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        checkEnable();
        if (localExpire != null) {
            localSetCache(name, key, type, value, localExpire);
        }
        if (remoteExpire != null && remoteSource != null) {
            remoteSetCache(name, key, type, value, remoteExpire);
        }
        if (remoteSource != null && broadcastable) {
            remoteSource.publish(getChannelTopic(), new CachedEventMessage(node, name, key));
        }
    }

    /**
     * 远程异步缓存数据
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param value 数据值
     * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @return void
     */
    @Override
    public <T> CompletableFuture<Void> bothSetAsync(
            String name, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        checkEnable();
        if (localExpire != null) {
            localSetCache(name, key, type, value, localExpire);
        }
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (remoteSource != null && remoteExpire != null) {
            future = remoteSetCacheAsync(name, key, type, value, remoteExpire);
        }
        if (remoteSource != null && broadcastable) {
            future = future.thenCompose(r -> remoteSource
                    .publishAsync(getChannelTopic(), new CachedEventMessage(node, name, key))
                    .thenApply(n -> r));
        }
        return future;
    }

    /**
     * 远程删除缓存数据
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return 删除数量
     */
    @Override
    public long bothDel(String name, String key) {
        checkEnable();
        String id = idFor(name, key);
        long v = localSource.del(id);
        if (remoteSource != null) {
            v = remoteSource.del(id);
            if (broadcastable) {
                remoteSource.publish(getChannelTopic(), new CachedEventMessage(node, name, key));
            }
        }
        return v;
    }

    /**
     * 远程异步删除缓存数据
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return 删除数量
     */
    @Override
    public CompletableFuture<Long> bothDelAsync(String name, String key) {
        checkEnable();
        String id = idFor(name, key);
        long v = localSource.del(id); // 内存操作，无需异步
        if (remoteSource != null) {
            return remoteSource.delAsync(id).thenCompose(r -> {
                return broadcastable
                        ? remoteSource
                                .publishAsync(getChannelTopic(), new CachedEventMessage(node, name, key))
                                .thenApply(n -> r)
                        : CompletableFuture.completedFuture(v);
            });
        } else {
            return CompletableFuture.completedFuture(v);
        }
    }

    // -------------------------------------- 内部方法 --------------------------------------
    /**
     * 获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param getter 获取数据函数
     * @param setter 设置数据函数
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    protected <T> T getSet(
            GetterFunc<CachedValue<T>> getter,
            SetterSyncFunc setter,
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration expire,
            ThrowSupplier<T> supplier) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(expire);
        Objects.requireNonNull(supplier);
        final Type cacheType = loadCacheType(type);
        final String id = idFor(name, key);
        CachedValue<T> cacheVal = getter.get(name, key, expire, cacheType);
        if (CachedValue.isValid(cacheVal)) {
            if (logable) {
                logger.log(logLevel, "Cached got id(" + id + ") value from eitherSource");
            }
            return cacheVal.getVal();
        }
        Function<String, CachedValue> func = k -> {
            CachedValue<T> oldCacheVal = getter.get(name, key, expire, cacheType);
            if (CachedValue.isValid(oldCacheVal)) {
                return oldCacheVal;
            }
            CachedValue<T> newCacheVal;
            try {
                newCacheVal = toCacheSupplier(nullable, supplier).get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RedkaleException(t);
            }
            if (CachedValue.isValid(newCacheVal)) {
                setter.set(name, key, expire, cacheType, newCacheVal);
            }
            return newCacheVal;
        };
        cacheVal = syncLockMap.computeIfAbsent(id, func);
        try {
            return CachedValue.get(cacheVal);
        } finally {
            syncLockMap.remove(id);
        }
    }

    /**
     * 异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param getter 获取数据函数
     * @param setter 设置数据函数
     * @param name 缓存名称
     * @param key 缓存键
     * @param type 数据类型
     * @param nullable 是否缓存null值
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     * @return 数据值
     */
    protected <T> CompletableFuture<T> getSetAsync(
            GetterFunc<CompletableFuture<CachedValue<T>>> getter,
            SetterAsyncFunc setter,
            String name,
            String key,
            Type type,
            boolean nullable,
            Duration expire,
            ThrowSupplier<CompletableFuture<T>> supplier) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(supplier);
        final Type cacheType = loadCacheType(type);
        final String id = idFor(name, key);
        CompletableFuture<CachedValue<T>> sourceFuture = getter.get(name, key, expire, cacheType);
        return sourceFuture.thenCompose(val -> {
            if (CachedValue.isValid(val)) {
                if (logable) {
                    logger.log(logLevel, "Cached got id(" + id + ") value from eitherSource");
                }
                return CompletableFuture.completedFuture(val.getVal());
            }
            final CachedAsyncLock lock = asyncLockMap.computeIfAbsent(id, k -> new CachedAsyncLock(asyncLockMap, k));
            CompletableFuture<T> future = new CompletableFuture<>();
            if (lock.compareAddFuture(future)) {
                try {
                    supplier.get().whenComplete((v, e) -> {
                        if (e != null) {
                            lock.fail(e);
                        }
                        CachedValue<T> cacheVal = toCacheValue(nullable, v);
                        if (CachedValue.isValid(cacheVal)) {
                            setter.set(name, key, expire, cacheType, cacheVal)
                                    .whenComplete((v2, e2) -> lock.success(CachedValue.get(cacheVal)));
                        } else {
                            lock.success(CachedValue.get(cacheVal));
                        }
                    });
                } catch (Throwable e) {
                    lock.fail(e);
                }
            }
            return future;
        });
    }

    protected <T> void localSetCache(String name, String key, Type type, T value, Duration expire) {
        localSetCache(name, key, expire, loadCacheType(type, value), CachedValue.create(value));
    }

    protected <T> void localSetCache(
            String name, String key, Duration expire, Type cacheType, CachedValue<T> cacheVal) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(expire);
        long millis = expire.toMillis();
        String id = idFor(name, key);
        if (logable) {
            logger.log(logLevel, "Cached set id(" + id + ") value to localSource expire " + millis + " ms");
        }
        if (millis > 0) {
            localSource.psetex(id, millis, cacheType, cacheVal);
        } else {
            localSource.set(id, cacheType, cacheVal);
        }
    }

    protected <T> void remoteSetCache(String name, String key, Type type, T value, Duration expire) {
        remoteSetCache(name, key, expire, loadCacheType(type, value), CachedValue.create(value));
    }

    protected <T> void remoteSetCache(
            String name, String key, Duration expire, Type cacheType, CachedValue<T> cacheVal) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(expire);
        long millis = expire.toMillis();
        String id = idFor(name, key);
        if (logable) {
            logger.log(logLevel, "Cached set id(" + id + ") value to remoteSource expire " + millis + " ms");
        }
        if (millis > 0) {
            remoteSource.psetex(id, millis, cacheType, cacheVal);
        } else {
            remoteSource.set(id, cacheType, cacheVal);
        }
    }

    protected <T> CompletableFuture<Void> localSetCacheAsync(
            String name, String key, Type type, T value, Duration expire) {
        return localSetCacheAsync(name, key, expire, loadCacheType(type, value), CachedValue.create(value));
    }

    protected <T> CompletableFuture<Void> localSetCacheAsync(
            String name, String key, Duration expire, Type cacheType, CachedValue<T> cacheVal) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(expire);
        String id = idFor(name, key);
        long millis = expire.toMillis();
        if (logable) {
            logger.log(logLevel, "Cached set id(" + id + ") value to localSource expire " + millis + " ms");
        }
        if (millis > 0) {
            return localSource.psetexAsync(id, millis, cacheType, cacheVal);
        } else {
            return localSource.setAsync(id, cacheType, cacheVal);
        }
    }

    protected <T> CompletableFuture<Void> remoteSetCacheAsync(
            String name, String key, Type type, T value, Duration expire) {
        return remoteSetCacheAsync(name, key, expire, loadCacheType(type, value), CachedValue.create(value));
    }

    protected <T> CompletableFuture<Void> remoteSetCacheAsync(
            String name, String key, Duration expire, Type cacheType, CachedValue<T> cacheVal) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        Objects.requireNonNull(expire);
        String id = idFor(name, key);
        long millis = expire.toMillis();
        if (logable) {
            logger.log(logLevel, "Cached set id(" + id + ") value to remoteSource expire " + millis + " ms");
        }
        if (millis > 0) {
            return remoteSource.psetexAsync(id, millis, cacheType, cacheVal);
        } else {
            return remoteSource.setAsync(id, cacheType, cacheVal);
        }
    }

    protected <T> CachedValue<T> bothGetCache(
            final String name, final String key, final Duration expire, final Type cacheType) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        String id = idFor(name, key);
        CachedValue<T> cacheVal = localSource.get(id, cacheType);
        if (CachedValue.isValid(cacheVal)) {
            if (logable) {
                logger.log(logLevel, "Cached got id(" + id + ") value from localSource");
            }
            return cacheVal;
        }
        if (remoteSource != null) {
            cacheVal = remoteSource.get(id, cacheType);
            if (CachedValue.isValid(cacheVal)) {
                if (expire != null) {
                    if (logable) {
                        logger.log(logLevel, "Cached set id(" + id + ") value to localSource from remoteSource");
                    }
                    localSetCache(name, key, expire, cacheType, cacheVal);
                }
                if (logable) {
                    logger.log(logLevel, "Cached got id(" + id + ") value from remoteSource");
                }
            }
            return cacheVal;
        } else {
            return null;
        }
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
     *
     * @param <T> 泛型
     * @param name 缓存名称
     * @param key 缓存键
     * @param expire 过期时长，Duration.ZERO为永不过期
     * @param cacheType 数据类型
     * @return 数据值
     */
    protected <T> CompletableFuture<CachedValue<T>> bothGetCacheAsync(
            String name, String key, Duration expire, Type cacheType) {
        checkEnable();
        boolean logable = logger.isLoggable(logLevel);
        String id = idFor(name, key);
        CachedValue<T> val = localSource.get(id, cacheType); // 内存操作，无需异步
        if (CachedValue.isValid(val)) {
            if (logable) {
                logger.log(logLevel, "Cached got id(" + id + ") value from localSource");
            }
            return CompletableFuture.completedFuture(val);
        }
        if (remoteSource != null) {
            CompletableFuture<CachedValue<T>> future = remoteSource.getAsync(id, cacheType);
            return future.thenApply(v -> {
                if (CachedValue.isValid(v)) {
                    if (expire != null) {
                        if (logable) {
                            logger.log(logLevel, "Cached set id(" + id + ") value to localSource from remoteSource");
                        }
                        localSetCache(name, key, expire, cacheType, v);
                    }
                    if (logable) {
                        logger.log(logLevel, "Cached got id(" + id + ") value from remoteSource");
                    }
                }
                return v;
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    protected void checkEnable() {
        if (!enabled) {
            throw new RedkaleException(CachedManager.class.getSimpleName() + " is disabled");
        }
    }

    /**
     * 创建一个锁key
     *
     * @param name 缓存名称
     * @param key 缓存键
     * @return key
     */
    protected String idFor(String name, String key) {
        return schema + ':' + name + ':' + key;
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param <T> 泛型
     * @param nullable 是否缓存null值
     * @param value 缓存值
     * @return CacheValue函数
     */
    protected <T> CachedValue<T> toCacheValue(boolean nullable, T value) {
        if (value == null) {
            return nullable ? CachedValue.create(value) : null;
        }
        return CachedValue.create(value);
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param <T> 泛型
     * @param nullable 是否缓存null值
     * @param supplier 数据函数
     * @return CacheValue函数
     */
    protected <T> ThrowSupplier<CachedValue<T>> toCacheSupplier(boolean nullable, ThrowSupplier<T> supplier) {
        return () -> toCacheValue(nullable, supplier.get());
    }

    /**
     * 创建数据类型创建对应CacheValue泛型
     *
     * @param type 数据类型，为null则取value的类型
     * @param value 数据值
     * @return CacheValue泛型
     */
    protected Type loadCacheType(Type type, final Object value) {
        return loadCacheType(type == null ? value.getClass() : type);
    }

    /**
     * 创建数据类型创建对应CacheValue泛型
     *
     * @param type 数据类型
     * @return CacheValue泛型
     */
    protected Type loadCacheType(Type type) {
        return cacheValueTypes.computeIfAbsent(
                type, t -> TypeToken.createParameterizedType(null, CachedValue.class, type));
    }

    protected static interface GetterFunc<R> {

        public R get(String name, String key, Duration expire, Type cacheType);
    }

    protected static interface SetterSyncFunc {

        public void set(String name, String key, Duration expire, Type cacheType, CachedValue cacheVal);
    }

    protected static interface SetterAsyncFunc {

        public CompletableFuture<Void> set(
                String name, String key, Duration expire, Type cacheType, CachedValue cacheVal);
    }

    public class CacheRemoteListener implements CacheEventListener<CachedEventMessage> {

        @Override
        public void onMessage(String topic, CachedEventMessage message) {
            if (!Objects.equals(getNode(), message.getNode())) {
                localSource.del(idFor(message.getName(), message.getKey()));
            }
        }
    }
}
