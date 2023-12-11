/*
 *
 */
package org.redkale.cache.support;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Supplier;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.boot.Application;
import org.redkale.cache.CacheManager;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleException;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * 缓存管理器
 *
 * @author zhangjx
 */
@Local
@Component
@AutoLoad(false)
@ResourceType(CacheManager.class)
public class CacheManagerService implements CacheManager, Service {

    //是否开启缓存
    protected boolean enabled = true;

    //是否缓存null值
    protected boolean nullable = false;

    //配置
    protected AnyValue config;

    //数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    //本地缓存Source
    protected final CacheMemorySource localSource = new CacheMemorySource("caching");

    //缓存hash集合, 用于定时遍历删除过期数据
    protected final ConcurrentSkipListSet<String> hashNames = new ConcurrentSkipListSet<>();

    //缓存无效时使用的锁
    private final ConcurrentHashMap<String, CacheValue> hashLock = new ConcurrentHashMap<>();

    @Resource(required = false)
    protected Application application;

    //远程缓存Source
    protected CacheSource remoteSource;

    protected CacheManagerService(@Nullable CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    //一般用于独立组件
    public static CacheManagerService create(@Nullable CacheSource remoteSource) {
        return new CacheManagerService(remoteSource);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public CacheManagerService enabled(boolean val) {
        this.enabled = val;
        return this;
    }

    public AnyValue getConfig() {
        return config;
    }

    @Override
    public void init(AnyValue conf) {
        this.config = conf;
        if (conf == null) {
            conf = AnyValue.create();
        }
        this.enabled = conf.getBoolValue("enabled", true);
        if (this.enabled) {
            this.localSource.init(conf);
            String remoteSourceName = conf.getValue("source");
            if (remoteSource == null && application != null && Utility.isNotBlank(remoteSourceName)) {
                CacheSource source = application.loadCacheSource(remoteSourceName, false);
                if (source == null) {
                    throw new RedkaleException("Not found CacheSource '" + remoteSourceName + "'");
                }
                this.remoteSource = source;
            }
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

    public CacheManagerService addHash(String hash) {
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
    public <T> T localGet(final String hash, final String key, final Type type, Duration expire, Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        Type t = loadCacheType(type);
        CacheValue<T> val = localSource.hget(hash, key, t);
        if (CacheValue.isValid(val)) {
            return val.getValue();
        }
        String lockKey = lockKey(hash, key);
        val = hashLock.computeIfAbsent(lockKey, k -> cacheSupplier(expire, supplier).get());
        try {
            if (CacheValue.isValid(val)) {
                localSource.hset(hash, key, t, val);
                return val.getValue();
            } else {
                return null;
            }
        } finally {
            hashLock.remove(lockKey);
        }
    }

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
    @Override
    public <T> CompletableFuture<T> localGetAsync(String hash, String key, Type type, Duration expire, Supplier<CompletableFuture<T>> supplier) {
        throw new UnsupportedOperationException("Not supported yet.");
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
     * 创建一个锁key
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return
     */
    protected String lockKey(String hash, String key) {
        return hash + (char) 8 + key;
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param expire   过期时长，为null表示永不过期
     * @param supplier 数据函数
     *
     * @return CacheValue函数
     */
    protected <T> CacheValue<T> cacheFunc(Duration expire, T value) {
        if (value == null) {
            return nullable ? CacheValue.create(value, expire) : null;
        }
        return CacheValue.create(value, expire);
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param expire   过期时长，为null表示永不过期
     * @param supplier 数据函数
     *
     * @return CacheValue函数
     */
    protected <T> Supplier<CacheValue<T>> cacheSupplier(Duration expire, Supplier<T> supplier) {
        return () -> cacheFunc(expire, supplier.get());
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
