/*
 *
 */
package org.redkale.cache;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
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
public class DefaultCacheManager implements CacheManager, Service {

    //是否开启缓存
    protected boolean enabled = true;

    //配置
    protected AnyValue config;

    //数据类型与CacheValue泛型的对应关系
    private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

    //本地缓存Source
    protected final CacheMemorySource localSource = new CacheMemorySource("cache-local");

    //缓存hash集合, 用于定时遍历删除过期数据
    protected final ConcurrentSkipListSet<String> hashNames = new ConcurrentSkipListSet<>();

    //缓存无效时使用的同步锁
    private final ConcurrentHashMap<String, CacheValue> syncLock = new ConcurrentHashMap<>();

    //缓存无效时使用的异步锁
    private final ConcurrentHashMap<String, CacheAsyncEntry> asyncLock = new ConcurrentHashMap<>();

    @Resource(required = false)
    protected Application application;

    //远程缓存Source
    protected CacheSource remoteSource;

    protected DefaultCacheManager(@Nullable CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    //一般用于独立组件
    public static DefaultCacheManager create(@Nullable CacheSource remoteSource) {
        return new DefaultCacheManager(remoteSource);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public DefaultCacheManager enabled(boolean val) {
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
                CacheSource source = application.getResourceFactory().find(remoteSourceName, CacheSource.class);
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

    public DefaultCacheManager addHash(String hash) {
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
        checkEnable();
        Type cacheType = loadCacheType(type);
        CacheValue<T> cacheVal = localSource.hget(hash, key, cacheType);
        return CacheValue.get(cacheVal);
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
    public <T> T localGet(final String hash, final String key, final Type type, boolean nullable, Duration expire, Supplier<T> supplier) {
        return get(localSource::hget, localSource::hset, hash, key, type, nullable, expire, supplier);
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
    @Override
    public <T> CompletableFuture<T> localGetAsync(String hash, String key, Type type, boolean nullable, Duration expire, Supplier<CompletableFuture<T>> supplier) {
        return getAsync(localSource::hgetAsync, localSource::hsetAsync, hash, key, type, nullable, expire, supplier);
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
    @Override
    public <T> void localSet(String hash, String key, Type type, T value, Duration expire) {
        checkEnable();
        Objects.requireNonNull(expire);
        Type cacheType = loadCacheType(type, value);
        CacheValue cacheVal = CacheValue.create(value, expire);
        localSource.hset(hash, key, cacheType, cacheVal);
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
        checkEnable();
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
        checkEnable();
        Type cacheType = loadCacheType(type);
        CacheValue<T> cacheVal = remoteSource.hget(hash, key, cacheType);
        return CacheValue.get(cacheVal);
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
        checkEnable();
        Type cacheType = loadCacheType(type);
        CompletableFuture<CacheValue<T>> future = remoteSource.hgetAsync(hash, key, cacheType);
        return future.thenApply(CacheValue::get);
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
    public <T> T remoteGet(final String hash, final String key, final Type type, boolean nullable, Duration expire, Supplier<T> supplier) {
        return get(remoteSource::hget, remoteSource::hset, hash, key, type, nullable, expire, supplier);
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
    public <T> CompletableFuture<T> remoteGetAsync(String hash, String key, Type type, boolean nullable, Duration expire, Supplier<CompletableFuture<T>> supplier) {
        return getAsync(remoteSource::hgetAsync, remoteSource::hsetAsync, hash, key, type, nullable, expire, supplier);
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
    public <T> void remoteSet(final String hash, final String key, final Type type, final T value, Duration expire) {
        checkEnable();
        Objects.requireNonNull(expire);
        Type cacheType = loadCacheType(type, value);
        CacheValue cacheVal = CacheValue.create(value, expire);
        remoteSource.hset(hash, key, cacheType, cacheVal);
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
     */
    public <T> CompletableFuture<Void> remoteSetAsync(String hash, String key, Type type, T value, Duration expire) {
        checkEnable();
        Objects.requireNonNull(expire);
        Type cacheType = loadCacheType(type, value);
        CacheValue cacheVal = CacheValue.create(value, expire);
        return remoteSource.hsetAsync(hash, key, cacheType, cacheVal);
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
        checkEnable();
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
        checkEnable();
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
    @Override
    public <T> T bothGet(final String hash, final String key, final Type type) {
        return CacheValue.get(bothGetCache(hash, key, type));
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
    public <T> CompletableFuture<T> bothGetAsync(final String hash, final String key, final Type type) {
        return bothGetCacheAsync(hash, key, type).thenApply(CacheValue::get);
    }

    /**
     * 远程获取缓存数据, 过期返回null
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     *
     * @param nullable     是否缓存null值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     * @param supplier     数据函数
     *
     * @return 数据值
     */
    @Override
    public <T> T bothGet(final String hash, final String key, final Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, Supplier<T> supplier) {
        if (localExpire == null) {  //只有远程缓存
            Objects.requireNonNull(remoteExpire);
            return remoteGet(hash, key, type, nullable, remoteExpire, supplier);
        }
        if (remoteExpire == null) { //只有本地缓存
            return localGet(hash, key, type, nullable, localExpire, supplier);
        }
        return get(this::bothGetCache, (h, k, t, v) -> {
            localSource.hset(h, k, t, v);
            if (remoteSource != null) {
                remoteSource.hset(h, k, t, CacheValue.create(v.getValue(), remoteExpire));
            }
        }, hash, key, type, nullable, localExpire, supplier);
    }

    /**
     * 远程异步获取缓存数据, 过期返回null
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
    @Override
    public <T> CompletableFuture<T> bothGetAsync(String hash, String key, Type type, boolean nullable,
        Duration localExpire, Duration remoteExpire, Supplier<CompletableFuture<T>> supplier) {
        if (localExpire == null) {  //只有远程缓存
            Objects.requireNonNull(remoteExpire);
            return remoteGetAsync(hash, key, type, nullable, remoteExpire, supplier);
        }
        if (remoteExpire == null) { //只有本地缓存
            return localGetAsync(hash, key, type, nullable, localExpire, supplier);
        }
        return getAsync(this::bothGetCacheAsync, (h, k, t, v) -> {
            localSource.hset(h, k, t, v);
            if (remoteSource != null) {
                return remoteSource.hsetAsync(h, k, t, CacheValue.create(v.getValue(), remoteExpire));
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }, hash, key, type, nullable, localExpire, supplier);
    }

    /**
     * 远程缓存数据
     *
     * @param <T>          泛型
     * @param hash         缓存hash
     * @param key          缓存键
     * @param type         数据类型
     * @param value        数据值
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     */
    public <T> void bothSet(final String hash, final String key, final Type type, final T value, Duration localExpire, Duration remoteExpire) {
        checkEnable();
        Type cacheType = loadCacheType(type, value);
        if (localExpire != null) {
            localSource.hset(hash, key, cacheType, CacheValue.create(value, localExpire));
        }
        if (remoteSource != null && remoteExpire != null) {
            remoteSource.hset(hash, key, cacheType, CacheValue.create(value, remoteExpire));
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
     * @param localExpire  本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
     * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
     *
     * @return void
     */
    public <T> CompletableFuture<Void> bothSetAsync(String hash, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
        checkEnable();
        Type cacheType = loadCacheType(type, value);
        if (localExpire != null) {
            localSource.hset(hash, key, cacheType, CacheValue.create(value, localExpire)); //内存操作，无需异步
        }
        if (remoteSource != null && remoteExpire != null) {
            return remoteSource.hsetAsync(hash, key, cacheType, CacheValue.create(value, remoteExpire));
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
        checkEnable();
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
        checkEnable();
        long v = localSource.hdel(hash, key); //内存操作，无需异步
        if (remoteSource != null) {
            return remoteSource.hdelAsync(hash, key);
        } else {
            return CompletableFuture.completedFuture(v);
        }
    }

    //-------------------------------------- 内部方法 --------------------------------------
    /**
     * 获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param getter   获取数据函数
     * @param setter   设置数据函数
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    protected <T> T get(GetterFunc<CacheValue<T>> getter, SetterSyncFunc setter,
        String hash, String key, Type type, boolean nullable, Duration expire, Supplier<T> supplier) {
        checkEnable();
        Objects.requireNonNull(expire);
        Objects.requireNonNull(supplier);
        final Type cacheType = loadCacheType(type);
        CacheValue<T> cacheVal = getter.get(hash, key, type);
        if (CacheValue.isValid(cacheVal)) {
            return cacheVal.getValue();
        }
        Function<String, CacheValue> func = k -> {
            CacheValue<T> oldCacheVal = getter.get(hash, key, type);
            if (CacheValue.isValid(oldCacheVal)) {
                return oldCacheVal;
            }
            CacheValue<T> newCacheVal = toCacheSupplier(nullable, expire, supplier).get();
            if (CacheValue.isValid(newCacheVal)) {
                setter.set(hash, key, cacheType, newCacheVal);
            }
            return newCacheVal;
        };
        final String lockId = lockId(hash, key);
        cacheVal = syncLock.computeIfAbsent(lockId, func);
        try {
            return CacheValue.get(cacheVal);
        } finally {
            syncLock.remove(lockId);
        }
    }

    /**
     * 异步获取缓存数据, 过期返回null
     *
     * @param <T>      泛型
     * @param getter   获取数据函数
     * @param setter   设置数据函数
     * @param hash     缓存hash
     * @param key      缓存键
     * @param type     数据类型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return 数据值
     */
    protected <T> CompletableFuture<T> getAsync(GetterFunc<CompletableFuture<CacheValue<T>>> getter, SetterAsyncFunc setter,
        String hash, String key, Type type, boolean nullable, Duration expire, Supplier<CompletableFuture<T>> supplier) {
        checkEnable();
        Objects.requireNonNull(supplier);
        final Type cacheType = loadCacheType(type);
        CompletableFuture<CacheValue<T>> sourceFuture = getter.get(hash, key, type);
        return sourceFuture.thenCompose(val -> {
            if (CacheValue.isValid(val)) {
                return CompletableFuture.completedFuture(val.getValue());
            }
            final String lockId = lockId(hash, key);
            final CacheAsyncEntry entry = asyncLock.computeIfAbsent(lockId, CacheAsyncEntry::new);
            CompletableFuture<T> future = new CompletableFuture<>();
            if (entry.compareAddFuture(future)) {
                try {
                    supplier.get().whenComplete((v, e) -> {
                        if (e != null) {
                            entry.fail(e);
                        }
                        CacheValue<T> cacheVal = toCacheValue(nullable, expire, v);
                        if (CacheValue.isValid(cacheVal)) {
                            setter.set(hash, key, cacheType, cacheVal)
                                .whenComplete((v2, e2) -> entry.success(CacheValue.get(cacheVal)));
                        } else {
                            entry.success(CacheValue.get(cacheVal));
                        }
                    });
                } catch (Throwable e) {
                    entry.fail(e);
                }
            }
            return future;
        });
    }

    protected <T> CacheValue<T> bothGetCache(final String hash, final String key, final Type type) {
        checkEnable();
        Type cacheType = loadCacheType(type);
        CacheValue<T> cacheVal = localSource.hget(hash, key, cacheType);
        if (CacheValue.isValid(cacheVal)) {
            return cacheVal;
        }
        if (remoteSource != null) {
            return remoteSource.hget(hash, key, cacheType);
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
    protected <T> CompletableFuture<CacheValue<T>> bothGetCacheAsync(final String hash, final String key, final Type type) {
        checkEnable();
        Type cacheType = loadCacheType(type);
        CacheValue<T> val = localSource.hget(hash, key, cacheType); //内存操作，无需异步
        if (CacheValue.isValid(val)) {
            return CompletableFuture.completedFuture(val);
        }
        if (remoteSource != null) {
            return remoteSource.hgetAsync(hash, key, cacheType);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    protected void checkEnable() {
        if (!enabled) {
            throw new RedkaleException(CacheManager.class.getSimpleName() + " is disabled");
        }
    }

    /**
     * 创建一个锁key
     *
     * @param hash 缓存hash
     * @param key  缓存键
     *
     * @return key
     */
    protected String lockId(String hash, String key) {
        return hash + ':' + key;
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param <T>      泛型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param value    缓存值
     *
     * @return CacheValue函数
     */
    protected <T> CacheValue<T> toCacheValue(boolean nullable, Duration expire, T value) {
        if (value == null) {
            return nullable ? CacheValue.create(value, expire) : null;
        }
        return CacheValue.create(value, expire);
    }

    /**
     * 将原始数据函数转换成获取CacheValue数据函数
     *
     * @param <T>      泛型
     * @param nullable 是否缓存null值
     * @param expire   过期时长，Duration.ZERO为永不过期
     * @param supplier 数据函数
     *
     * @return CacheValue函数
     */
    protected <T> Supplier<CacheValue<T>> toCacheSupplier(boolean nullable, Duration expire, Supplier<T> supplier) {
        return () -> toCacheValue(nullable, expire, supplier.get());
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

    private static final Object NIL = new Object();

    protected static interface GetterFunc<R> {

        public R get(String hash, String key, Type type);
    }

    protected static interface SetterSyncFunc {

        public void set(String hash, String key, Type cacheType, CacheValue cacheVal);
    }

    protected static interface SetterAsyncFunc {

        public CompletableFuture<Void> set(String hash, String key, Type cacheType, CacheValue cacheVal);
    }

    protected class CacheAsyncEntry {

        private final AtomicBoolean state = new AtomicBoolean();

        private final List<CompletableFuture> futures = new ArrayList<>();

        private final ReentrantLock lock = new ReentrantLock();

        private final String lockId;

        private Object resultObj = NIL;

        private Throwable resultExp;

        public CacheAsyncEntry(String lockId) {
            this.lockId = lockId;
        }

        public boolean compareAddFuture(CompletableFuture future) {
            lock.lock();
            try {
                if (resultObj != NIL) {
                    future.complete(resultObj);
                    return false;
                } else if (resultExp != null) {
                    future.completeExceptionally(resultExp);
                    return false;
                }
                boolean rs = state.compareAndSet(false, true);
                this.futures.add(future);
                return rs;
            } finally {
                lock.unlock();
            }
        }

        public void fail(Throwable t) {
            lock.lock();
            try {
                this.resultExp = t;
                for (CompletableFuture future : futures) {
                    future.completeExceptionally(t);
                }
                this.futures.clear();
            } finally {
                asyncLock.remove(lockId);
                lock.unlock();
            }
        }

        public <T> void success(T val) {
            lock.lock();
            try {
                this.resultObj = val;
                for (CompletableFuture future : futures) {
                    future.complete(val);
                }
                this.futures.clear();
            } finally {
                asyncLock.remove(lockId);
                lock.unlock();
            }
        }

    }
}
