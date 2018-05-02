/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.service.*;
import static org.redkale.source.DataSources.*;
import org.redkale.util.*;

/**
 * DataSource的SQL抽象实现类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <DBChannel> 数据库连接
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public abstract class DataSqlSource<DBChannel> extends AbstractService implements DataSource, DataCacheListener, Function<Class, EntityInfo>, AutoCloseable, Resourcable {

    protected static final Flipper FLIPPER_ONE = new Flipper(1);

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected String name;

    protected URL confxml;

    protected int threads;

    protected ExecutorService executor;

    protected boolean cacheForbidden;

    protected PoolSource<DBChannel> readPool;

    protected PoolSource<DBChannel> writePool;

    @Resource(name = "$")
    protected DataCacheListener cacheListener;

    protected final BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) logger.log(Level.SEVERE, "CompletableFuture complete error", (Throwable) t);
    };

    protected final BiFunction<DataSource, Class, List> fullloader = (s, t) -> querySheet(false, false, t, null, null, (FilterNode) null).list(true);

    @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    public DataSqlSource(String unitName, URL confxml, Properties readprop, Properties writeprop) {
        final AtomicInteger counter = new AtomicInteger();
        this.threads = Integer.decode(readprop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        if (readprop != writeprop) {
            this.threads += Integer.decode(writeprop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        }
        final String cname = this.getClass().getSimpleName();
        final Thread.UncaughtExceptionHandler ueh = (t, e) -> {
            logger.log(Level.SEVERE, cname + " error", e);
        };
        this.executor = Executors.newFixedThreadPool(threads, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            String s = "" + counter.incrementAndGet();
            if (s.length() == 1) {
                s = "00" + s;
            } else if (s.length() == 2) {
                s = "0" + s;
            }
            t.setName(cname + "-Thread-" + s);
            t.setUncaughtExceptionHandler(ueh);
            return t;
        });
        this.name = unitName;
        this.confxml = confxml;
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty(JDBC_CACHE_MODE));
        this.readPool = createReadPoolSource(this, "read", readprop);
        this.writePool = createWritePoolSource(this, "write", writeprop);
    }

    //是否异步， 为true则只能调用pollAsync方法，为false则只能调用poll方法
    protected abstract boolean isAysnc();

    protected abstract PoolSource<DBChannel> createReadPoolSource(DataSource source, String stype, Properties prop);

    protected abstract PoolSource<DBChannel> createWritePoolSource(DataSource source, String stype, Properties prop);

    @Override
    protected ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void destroy(AnyValue config) {
        if (this.executor != null) this.executor.shutdownNow();
    }

    @Local
    @Override
    public String getType() {
        return "sql";
    }

    @Override
    public final String resourceName() {
        return name;
    }

    @Local
    @Override
    public void close() throws Exception {
        readPool.close();
        writePool.close();
    }

    @Local
    public PoolSource<DBChannel> getReadPoolSource() {
        return readPool;
    }

    @Local
    public PoolSource<DBChannel> getWritePoolSource() {
        return writePool;
    }

    @Local
    @Override
    public EntityInfo apply(Class t) {
        return loadEntityInfo(t);
    }

    protected <T> EntityInfo<T> loadEntityInfo(Class<T> clazz) {
        return EntityInfo.load(clazz, this.cacheForbidden, this.readPool.props, this, fullloader);
    }

    protected CompletableFuture<Void> completeVoidFuture() {
        return isAysnc() ? CompletableFuture.completedFuture(null) : null;
    }

    /**
     * 将entity的对象全部加载到Cache中去，如果clazz没有被@javax.persistence.Cacheable注解则不做任何事
     *
     * @param <T>   Entity类泛型
     * @param clazz Entity类
     */
    public <T> void refreshCache(Class<T> clazz) {
        EntityInfo<T> info = loadEntityInfo(clazz);
        EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        cache.fullLoad();
    }

    //----------------------------- insert -----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>    Entity类泛型
     * @param values Entity对象
     */
    @Override
    public <T> void insert(@RpcCall(DataCallArrayAttribute.class) T... values) {
        if (values.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            if (isAysnc()) {
                insert(null, info, values).join();
            } else {
                insert(null, info, values);
            }
        } else {
            if (isAysnc()) {
                writePool.pollAsync().thenApply(conn -> insert(conn, info, values)).join();
            } else {
                insert(writePool.poll(), info, values);
            }
        }
    }

    @Override
    public <T> CompletableFuture<Void> insertAsync(@RpcCall(DataCallArrayAttribute.class) T... values) {
        if (values.length == 0) return completeVoidFuture();
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            if (isAysnc()) {
                return insert(null, info, values).whenComplete(futureCompleteConsumer);
            } else {
                return CompletableFuture.runAsync(() -> insert(null, info, values), getExecutor()).whenComplete(futureCompleteConsumer);
            }
        } else {
            if (isAysnc()) {
                return writePool.pollAsync().thenApply(conn -> insert(conn, info, values)).whenComplete(futureCompleteConsumer);
            } else {
                return CompletableFuture.runAsync(() -> insert(writePool.poll(), info, values), getExecutor()).whenComplete(futureCompleteConsumer);
            }
        }
    }

    protected <T> CompletableFuture<Void> insert(final DBChannel conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return completeVoidFuture();
        if (values.length > 1) { //检查对象是否都是同一个Entity类
            Class clazz = null;
            for (T val : values) {
                if (clazz == null) {
                    clazz = val.getClass();
                    continue;
                }
                if (clazz != val.getClass()) {
                    throw new RuntimeException("DataSource.insert must the same Class Entity, but diff is " + clazz + " and " + val.getClass());
                }
            }
        }
        if (info.isVirtualEntity()) {
            final EntityCache<T> cache = info.getCache();
            if (cache != null) { //更新缓存
                for (final T value : values) {
                    cache.insert(value);
                }
                if (cacheListener != null) cacheListener.insertCache(info.getType(), values);
            }
            return completeVoidFuture();
        }
        if (isAysnc()) { //异步模式

        } else {

        }
        return completeVoidFuture();
    }

    @Override
    public <T> int insertCache(Class<T> clazz, T... values) {
        if (values.length == 0) return 0;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c = 0;
        for (T value : values) {
            c += cache.insert(value);
        }
        return c;
    }

    //----------------------------- delete -----------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>    Entity类泛型
     * @param values Entity对象
     *
     * @return 删除的数据条数
     */
    @Override
    public <T> int delete(T... values) {
        if (values.length == 0) return -1;
        return -1;
    }

    protected <T> CompletableFuture<Integer> delete(final DBChannel conn, final EntityInfo<T> info, Serializable... keys) {
        if (keys.length == 0) return CompletableFuture.completedFuture(-1);
        if (info.isVirtualEntity()) {
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return CompletableFuture.completedFuture(-1);
            int c = 0;
            for (Serializable key : keys) {
                c += cache.delete(key);
            }
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), keys);
            return CompletableFuture.completedFuture(c);
        }
        if (isAysnc()) { //异步模式

        } else {

        }
        return CompletableFuture.completedFuture(-1);
    }

    protected <T> Sheet<T> querySheet(final boolean readcache, final boolean needtotal, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return null;
    }
}
