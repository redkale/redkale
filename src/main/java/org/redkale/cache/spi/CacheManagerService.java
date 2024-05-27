/*
 *
 */
package org.redkale.cache.spi;

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
import org.redkale.util.ThrowSupplier;
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

	// 是否开启缓存
	protected boolean enabled = true;

	// 配置
	protected AnyValue config;

	// 数据类型与CacheValue泛型的对应关系
	private final ConcurrentHashMap<Type, Type> cacheValueTypes = new ConcurrentHashMap<>();

	// 本地缓存Source
	protected final CacheMemorySource localSource = new CacheMemorySource("cache-local");

	// 缓存hash集合, 用于定时遍历删除过期数据
	protected final ConcurrentSkipListSet<String> hashNames = new ConcurrentSkipListSet<>();

	// 缓存无效时使用的同步锁
	private final ConcurrentHashMap<String, CacheValue> syncLock = new ConcurrentHashMap<>();

	// 缓存无效时使用的异步锁
	private final ConcurrentHashMap<String, CacheAsyncEntry> asyncLock = new ConcurrentHashMap<>();

	@Resource(required = false)
	protected Application application;

	// 远程缓存Source
	protected CacheSource remoteSource;

	protected CacheManagerService(@Nullable CacheSource remoteSource) {
		this.remoteSource = remoteSource;
	}

	// 一般用于独立组件
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

	@Override
	public void init(AnyValue conf) {
		this.config = conf;
		if (conf == null) {
			conf = AnyValue.create();
		}
		this.enabled = conf.getBoolValue("enabled", true);
		if (this.enabled) {
			this.localSource.init(conf);
			String remoteSourceName = conf.getValue("remote");
			if (Utility.isNotBlank(remoteSourceName)) {
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

	// -------------------------------------- 本地缓存 --------------------------------------
	/**
	 * 本地获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	@Override
	public <T> T localGet(final String hash, final String key, final Type type) {
		checkEnable();
		return CacheValue.get(localSource.get(idFor(hash, key), loadCacheType(type)));
	}

	/**
	 * 本地获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	@Override
	public <T> T localGetSet(
			final String hash,
			final String key,
			final Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<T> supplier) {
		return getSet(localSource::get, this::localSetCache, hash, key, type, nullable, expire, supplier);
	}

	/**
	 * 本地异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	@Override
	public <T> CompletableFuture<T> localGetSetAsync(
			String hash,
			String key,
			Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<CompletableFuture<T>> supplier) {
		return getSetAsync(
				localSource::getAsync, this::localSetCacheAsync, hash, key, type, nullable, expire, supplier);
	}

	/**
	 * 本地缓存数据
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param value 数据值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 */
	@Override
	public <T> void localSet(String hash, String key, Type type, T value, Duration expire) {
		setCache(localSource, hash, key, type, value, expire);
	}

	/**
	 * 本地删除缓存数据
	 *
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @return 删除数量
	 */
	@Override
	public long localDel(String hash, String key) {
		checkEnable();
		return localSource.del(idFor(hash, key));
	}

	// -------------------------------------- 远程缓存 --------------------------------------
	/**
	 * 远程获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	@Override
	public <T> T remoteGet(final String hash, final String key, final Type type) {
		checkEnable();
		return CacheValue.get(remoteSource.get(idFor(hash, key), loadCacheType(type)));
	}

	/**
	 * 远程异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	@Override
	public <T> CompletableFuture<T> remoteGetAsync(final String hash, final String key, final Type type) {
		checkEnable();
		CompletableFuture<CacheValue<T>> future = remoteSource.getAsync(idFor(hash, key), loadCacheType(type));
		return future.thenApply(CacheValue::get);
	}

	/**
	 * 远程获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	@Override
	public <T> T remoteGetSet(
			final String hash,
			final String key,
			final Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<T> supplier) {
		return getSet(remoteSource::get, this::remoteSetCache, hash, key, type, nullable, expire, supplier);
	}

	/**
	 * 远程异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	@Override
	public <T> CompletableFuture<T> remoteGetSetAsync(
			String hash,
			String key,
			Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<CompletableFuture<T>> supplier) {
		return getSetAsync(
				remoteSource::getAsync, this::remoteSetCacheAsync, hash, key, type, nullable, expire, supplier);
	}

	/**
	 * 远程缓存数据
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param value 数据值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 */
	@Override
	public <T> void remoteSet(final String hash, final String key, final Type type, final T value, Duration expire) {
		setCache(remoteSource, hash, key, type, value, expire);
	}

	/**
	 * 远程异步缓存数据
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param value 数据值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 */
	@Override
	public <T> CompletableFuture<Void> remoteSetAsync(String hash, String key, Type type, T value, Duration expire) {
		return setCacheAsync(remoteSource, hash, key, type, value, expire);
	}

	/**
	 * 远程删除缓存数据
	 *
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @return 删除数量
	 */
	@Override
	public long remoteDel(String hash, String key) {
		checkEnable();
		return remoteSource.del(idFor(hash, key));
	}

	/**
	 * 远程异步删除缓存数据
	 *
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @return 删除数量
	 */
	@Override
	public CompletableFuture<Long> remoteDelAsync(String hash, String key) {
		checkEnable();
		return remoteSource.delAsync(idFor(hash, key));
	}

	// -------------------------------------- both缓存 --------------------------------------
	/**
	 * 远程获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	@Override
	public <T> T bothGet(final String hash, final String key, final Type type) {
		return CacheValue.get(bothGetCache(hash, key, type));
	}

	/**
	 * 远程异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	@Override
	public <T> CompletableFuture<T> bothGetAsync(final String hash, final String key, final Type type) {
		return bothGetCacheAsync(hash, key, type).thenApply(CacheValue::get);
	}

	/**
	 * 远程获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
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
			final String hash,
			final String key,
			final Type type,
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
			return localGetSet(hash, key, type, nullable, localExpire, supplier);
		}
		if (localExpire == null) { // 只有远程缓存
			Objects.requireNonNull(remoteExpire);
			return remoteGetSet(hash, key, type, nullable, remoteExpire, supplier);
		}
		return getSet(
				this::bothGetCache,
				(i, e, t, v) -> {
					localSetCache(i, localExpire, t, v);
					if (remoteSource != null) {
						remoteSetCache(i, remoteExpire, t, v);
					}
				},
				hash,
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
	 * @param hash 缓存hash
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
			String hash,
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
			return localGetSetAsync(hash, key, type, nullable, localExpire, supplier);
		}
		if (localExpire == null) { // 只有远程缓存
			Objects.requireNonNull(remoteExpire);
			return remoteGetSetAsync(hash, key, type, nullable, remoteExpire, supplier);
		}
		return getSetAsync(
				this::bothGetCacheAsync,
				(i, e, t, v) -> {
					localSetCache(i, localExpire, t, v);
					if (remoteSource != null) {
						return remoteSetCacheAsync(i, remoteExpire, t, v);
					} else {
						return CompletableFuture.completedFuture(null);
					}
				},
				hash,
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
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param value 数据值
	 * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
	 * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
	 */
	@Override
	public <T> void bothSet(
			final String hash,
			final String key,
			final Type type,
			final T value,
			Duration localExpire,
			Duration remoteExpire) {
		checkEnable();
		if (localExpire != null) {
			setCache(localSource, hash, key, type, value, localExpire);
		}
		if (remoteSource != null && remoteExpire != null) {
			setCache(remoteSource, hash, key, type, value, remoteExpire);
		}
	}

	/**
	 * 远程异步缓存数据
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param value 数据值
	 * @param localExpire 本地过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
	 * @param remoteExpire 远程过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
	 * @return void
	 */
	@Override
	public <T> CompletableFuture<Void> bothSetAsync(
			String hash, String key, Type type, T value, Duration localExpire, Duration remoteExpire) {
		checkEnable();
		if (localExpire != null) {
			setCache(localSource, hash, key, type, value, localExpire);
		}
		if (remoteSource != null && remoteExpire != null) {
			return setCacheAsync(remoteSource, hash, key, type, value, remoteExpire);
		} else {
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * 远程删除缓存数据
	 *
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @return 删除数量
	 */
	@Override
	public long bothDel(String hash, String key) {
		checkEnable();
		String id = idFor(hash, key);
		long v = localSource.del(id);
		if (remoteSource != null) {
			return remoteSource.del(id);
		} else {
			return v;
		}
	}

	/**
	 * 远程异步删除缓存数据
	 *
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @return 删除数量
	 */
	@Override
	public CompletableFuture<Long> bothDelAsync(String hash, String key) {
		checkEnable();
		String id = idFor(hash, key);
		long v = localSource.del(id); // 内存操作，无需异步
		if (remoteSource != null) {
			return remoteSource.delAsync(id);
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
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	protected <T> T getSet(
			GetterFunc<CacheValue<T>> getter,
			SetterSyncFunc setter,
			String hash,
			String key,
			Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<T> supplier) {
		checkEnable();
		Objects.requireNonNull(expire);
		Objects.requireNonNull(supplier);
		final Type cacheType = loadCacheType(type);
		final String id = idFor(hash, key);
		CacheValue<T> cacheVal = getter.get(id, cacheType);
		if (CacheValue.isValid(cacheVal)) {
			return cacheVal.getVal();
		}
		Function<String, CacheValue> func = k -> {
			CacheValue<T> oldCacheVal = getter.get(id, cacheType);
			if (CacheValue.isValid(oldCacheVal)) {
				return oldCacheVal;
			}
			CacheValue<T> newCacheVal;
			try {
				newCacheVal = toCacheSupplier(nullable, supplier).get();
			} catch (RuntimeException e) {
				throw e;
			} catch (Throwable t) {
				throw new RedkaleException(t);
			}
			if (CacheValue.isValid(newCacheVal)) {
				setter.set(id, expire, cacheType, newCacheVal);
			}
			return newCacheVal;
		};
		cacheVal = syncLock.computeIfAbsent(id, func);
		try {
			return CacheValue.get(cacheVal);
		} finally {
			syncLock.remove(id);
		}
	}

	/**
	 * 异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param getter 获取数据函数
	 * @param setter 设置数据函数
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @param nullable 是否缓存null值
	 * @param expire 过期时长，Duration.ZERO为永不过期
	 * @param supplier 数据函数
	 * @return 数据值
	 */
	protected <T> CompletableFuture<T> getSetAsync(
			GetterFunc<CompletableFuture<CacheValue<T>>> getter,
			SetterAsyncFunc setter,
			String hash,
			String key,
			Type type,
			boolean nullable,
			Duration expire,
			ThrowSupplier<CompletableFuture<T>> supplier) {
		checkEnable();
		Objects.requireNonNull(supplier);
		final Type cacheType = loadCacheType(type);
		final String id = idFor(hash, key);
		CompletableFuture<CacheValue<T>> sourceFuture = getter.get(id, cacheType);
		return sourceFuture.thenCompose(val -> {
			if (CacheValue.isValid(val)) {
				return CompletableFuture.completedFuture(val.getVal());
			}
			final CacheAsyncEntry entry = asyncLock.computeIfAbsent(id, CacheAsyncEntry::new);
			CompletableFuture<T> future = new CompletableFuture<>();
			if (entry.compareAddFuture(future)) {
				try {
					supplier.get().whenComplete((v, e) -> {
						if (e != null) {
							entry.fail(e);
						}
						CacheValue<T> cacheVal = toCacheValue(nullable, v);
						if (CacheValue.isValid(cacheVal)) {
							setter.set(id, expire, cacheType, cacheVal)
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

	protected <T> CompletableFuture<Void> localSetCacheAsync(
			String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		return setCacheAsync(localSource, id, expire, cacheType, cacheVal);
	}

	protected <T> CompletableFuture<Void> remoteSetCacheAsync(
			String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		return setCacheAsync(remoteSource, id, expire, cacheType, cacheVal);
	}

	protected <T> void localSetCache(String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		setCache(localSource, id, expire, cacheType, cacheVal);
	}

	protected <T> void remoteSetCache(String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		setCache(remoteSource, id, expire, cacheType, cacheVal);
	}

	protected <T> void setCache(
			CacheSource source, String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		checkEnable();
		Objects.requireNonNull(expire);
		long millis = expire.toMillis();
		if (millis > 0) {
			source.psetex(id, millis, cacheType, cacheVal);
		} else {
			source.set(id, cacheType, cacheVal);
		}
	}

	protected <T> CompletableFuture<Void> setCacheAsync(
			CacheSource source, String id, Duration expire, Type cacheType, CacheValue<T> cacheVal) {
		checkEnable();
		Objects.requireNonNull(expire);
		long millis = expire.toMillis();
		if (millis > 0) {
			return source.psetexAsync(id, millis, cacheType, cacheVal);
		} else {
			return source.setAsync(id, cacheType, cacheVal);
		}
	}

	protected <T> void setCache(CacheSource source, String hash, String key, Type type, T value, Duration expire) {
		setCache(source, idFor(hash, key), expire, loadCacheType(type, value), CacheValue.create(value));
	}

	protected <T> CompletableFuture<Void> setCacheAsync(
			CacheSource source, String hash, String key, Type type, T value, Duration expire) {
		return setCacheAsync(source, idFor(hash, key), expire, loadCacheType(type, value), CacheValue.create(value));
	}

	protected <T> CacheValue<T> bothGetCache(final String hash, final String key, final Type type) {
		return bothGetCache(idFor(hash, key), loadCacheType(type));
	}

	/**
	 * 远程异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param hash 缓存hash
	 * @param key 缓存键
	 * @param type 数据类型
	 * @return 数据值
	 */
	protected <T> CompletableFuture<CacheValue<T>> bothGetCacheAsync(
			final String hash, final String key, final Type type) {
		return bothGetCacheAsync(idFor(hash, key), loadCacheType(type));
	}

	protected <T> CacheValue<T> bothGetCache(final String id, final Type cacheType) {
		checkEnable();
		CacheValue<T> cacheVal = localSource.get(id, cacheType);
		if (CacheValue.isValid(cacheVal)) {
			return cacheVal;
		}
		if (remoteSource != null) {
			return remoteSource.get(id, cacheType);
		} else {
			return null;
		}
	}

	/**
	 * 远程异步获取缓存数据, 过期返回null
	 *
	 * @param <T> 泛型
	 * @param id 缓存键
	 * @param cacheType 数据类型
	 * @return 数据值
	 */
	protected <T> CompletableFuture<CacheValue<T>> bothGetCacheAsync(final String id, final Type cacheType) {
		checkEnable();
		CacheValue<T> val = localSource.get(id, cacheType); // 内存操作，无需异步
		if (CacheValue.isValid(val)) {
			return CompletableFuture.completedFuture(val);
		}
		if (remoteSource != null) {
			return remoteSource.getAsync(id, cacheType);
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
	 * @param key 缓存键
	 * @return key
	 */
	protected String idFor(String hash, String key) {
		return hash + ':' + key;
	}

	/**
	 * 将原始数据函数转换成获取CacheValue数据函数
	 *
	 * @param <T> 泛型
	 * @param nullable 是否缓存null值
	 * @param value 缓存值
	 * @return CacheValue函数
	 */
	protected <T> CacheValue<T> toCacheValue(boolean nullable, T value) {
		if (value == null) {
			return nullable ? CacheValue.create(value) : null;
		}
		return CacheValue.create(value);
	}

	/**
	 * 将原始数据函数转换成获取CacheValue数据函数
	 *
	 * @param <T> 泛型
	 * @param nullable 是否缓存null值
	 * @param supplier 数据函数
	 * @return CacheValue函数
	 */
	protected <T> ThrowSupplier<CacheValue<T>> toCacheSupplier(boolean nullable, ThrowSupplier<T> supplier) {
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
				type, t -> TypeToken.createParameterizedType(null, CacheValue.class, type));
	}

	private static final Object NIL = new Object();

	protected static interface GetterFunc<R> {

		public R get(String id, Type cacheType);
	}

	protected static interface SetterSyncFunc {

		public void set(String id, Duration expire, Type cacheType, CacheValue cacheVal);
	}

	protected static interface SetterAsyncFunc {

		public CompletableFuture<Void> set(String id, Duration expire, Type cacheType, CacheValue cacheVal);
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
