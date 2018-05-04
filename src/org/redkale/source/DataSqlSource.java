/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.URL;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.redkale.service.*;
import static org.redkale.source.DataSources.*;
import org.redkale.util.*;

/**
 * DataSource的SQL抽象实现类 <br>
 * 注意: 所有的操作只能作用在一张表上，不能同时变更多张表
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

    protected URL persistxml;

    protected int threads;

    protected ObjectPool<ByteBuffer> bufferPool;

    protected ThreadPoolExecutor executor;

    protected boolean cacheForbidden;

    protected PoolSource<DBChannel> readPool;

    protected PoolSource<DBChannel> writePool;

    @Resource(name = "$")
    protected DataCacheListener cacheListener;

    protected final BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) logger.log(Level.SEVERE, "CompletableFuture complete error", (Throwable) t);
    };

    protected final BiFunction<DataSource, Class, List> fullloader = (s, t) -> ((Sheet) querySheet(false, false, t, null, null, (FilterNode) null).join()).list(true);

    @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    public DataSqlSource(String unitName, URL persistxml, Properties readprop, Properties writeprop) {
        final AtomicInteger counter = new AtomicInteger();
        this.threads = Integer.decode(readprop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        if (readprop != writeprop) {
            this.threads += Integer.decode(writeprop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        }
        final String cname = this.getClass().getSimpleName();
        final Thread.UncaughtExceptionHandler ueh = (t, e) -> {
            logger.log(Level.SEVERE, cname + " error", e);
        };
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads, (Runnable r) -> {
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
        final int bufferCapacity = Integer.decode(readprop.getProperty(JDBC_CONNECTIONSCAPACITY, "" + 16 * 1024));
        this.bufferPool = new ObjectPool<>(new AtomicLong(), new AtomicLong(), this.threads,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) return false;
                e.clear();
                return true;
            });
        this.name = unitName;
        this.persistxml = persistxml;
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty(JDBC_CACHE_MODE));
        this.readPool = createPoolSource(this, "read", readprop);
        this.writePool = createPoolSource(this, "write", writeprop);
    }

    //是否异步， 为true则只能调用pollAsync方法，为false则只能调用poll方法
    protected abstract boolean isAsync();

    //index从1开始
    protected abstract String getPrepareParamSign(int index);

    //创建连接池
    protected abstract PoolSource<DBChannel> createPoolSource(DataSource source, String rwtype, Properties prop);

    //插入新纪录
    protected abstract <T> CompletableFuture<Void> insertDB(final EntityInfo<T> info, T... values);

    //删除记录
    protected abstract <T> CompletableFuture<Integer> deleteDB(final EntityInfo<T> info, Flipper flipper, final String sql);

    //查询单条记录
    protected abstract <T> CompletableFuture<T> findDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final SelectColumn selects);

    //查询单条记录的单个字段
    protected abstract <T> CompletableFuture<Serializable> findColumnDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final String column, final Serializable defValue);

    //查询Number数据
    protected abstract <T> CompletableFuture<Number> getNumberResultDB(final EntityInfo<T> info, final String sql, final Number defVal, final String column);

    //查询Number Map数据
    protected abstract <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(final EntityInfo<T> info, final String sql, final FilterFuncColumn... columns);

    //查询Map数据
    protected abstract <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(final EntityInfo<T> info, final String sql, final String keyColumn);

    //判断记录是否存在
    protected abstract <T> CompletableFuture<Boolean> existsDB(final EntityInfo<T> info, final String sql, final boolean onlypk);

    //查询一页数据
    protected abstract <T> CompletableFuture<Sheet<T>> querySheetDB(final EntityInfo<T> info, final boolean needtotal, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    @Override
    protected ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void init(AnyValue config) {
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
        return isAsync() ? CompletableFuture.completedFuture(null) : null;
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
    ////检查对象是否都是同一个Entity类

    protected <T> CompletableFuture checkEntity(String action, boolean async, T... values) {
        if (values.length < 1) return null;
        Class clazz = null;
        for (T val : values) {
            if (clazz == null) {
                clazz = val.getClass();
                continue;
            }
            if (clazz != val.getClass()) {
                if (async) {
                    CompletableFuture future = new CompletableFuture<>();
                    future.completeExceptionally(new SQLException("DataSource." + action + " must the same Class Entity, but diff is " + clazz + " and " + val.getClass()));
                    return future;
                }
                throw new RuntimeException("DataSource." + action + " must the same Class Entity, but diff is " + clazz + " and " + val.getClass());
            }
        }
        return null;
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
        checkEntity("insert", false, values);
        if (info.isVirtualEntity()) {
            insertCache(info, values);
            return;
        }
        insertDB(info, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Void> insertAsync(@RpcCall(DataCallArrayAttribute.class) T... values) {
        if (values.length == 0) return completeVoidFuture();
        CompletableFuture future = checkEntity("insert", true, values);
        if (future != null) return future;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            return CompletableFuture.runAsync(() -> insertCache(info, values), getExecutor());
        }
        if (isAsync()) return insertDB(info, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    insertCache(info, values);
                }
            });
        return CompletableFuture.runAsync(() -> insertDB(info, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, values);
            }
        }), getExecutor());
    }

    protected <T> void insertCache(final EntityInfo<T> info, T... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        for (final T value : values) {
            cache.insert(value);
        }
        if (cacheListener != null) cacheListener.insertCache(info.getType(), values);
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
        checkEntity("delete", false, values);
        final Class<T> clazz = (Class<T>) values[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[values.length];
        int i = 0;
        for (final T value : values) {
            ids[i++] = (Serializable) primary.get(value);
        }
        return delete(clazz, ids);
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final T... values) {
        if (values.length == 0) return CompletableFuture.completedFuture(-1);
        CompletableFuture future = checkEntity("delete", true, values);
        if (future != null) return future;
        final Class<T> clazz = (Class<T>) values[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[values.length];
        int i = 0;
        for (final T value : values) {
            ids[i++] = (Serializable) primary.get(value);
        }
        return deleteAsync(clazz, ids);
    }

    @Override
    public <T> int delete(Class<T> clazz, Serializable... ids) {
        if (ids.length == 0) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return deleteCache(info, -1, ids);
        return delete(info, ids).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, ids);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Serializable... ids) {
        if (ids.length == 0) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> deleteCache(info, -1, ids), getExecutor());
        }
        if (isAsync()) return delete(info, ids).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, ids);
                }
            });
        return CompletableFuture.supplyAsync(() -> delete(info, ids).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, ids);
            }
        });
    }

    @Override
    public <T> int delete(Class<T> clazz, FilterNode node) {
        return delete(clazz, (Flipper) null, node);
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final FilterNode node) {
        return deleteAsync(clazz, (Flipper) null, node);
    }

    @Override
    public <T> int delete(Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return deleteCache(info, -1, flipper, node);
        return delete(info, flipper, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, flipper, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> deleteCache(info, -1, flipper, node), getExecutor());
        }
        if (isAsync()) return delete(info, flipper, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, flipper, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> delete(info, flipper, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, flipper, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> delete(final EntityInfo<T> info, final Serializable... ids) {
        if (ids.length == 1) {
            String sql = "DELETE FROM " + info.getTable(ids[0]) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(ids[0]);
            return deleteDB(info, null, sql);
        }
        String sql = "DELETE FROM " + info.getTable(ids[0]) + " WHERE " + info.getPrimarySQLColumn() + " IN (";
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sql += ',';
            sql += FilterNode.formatToString(ids[i]);
        }
        sql += ")";
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
        return deleteDB(info, null, sql);
    }

    protected <T> CompletableFuture<Integer> delete(final EntityInfo<T> info, final Flipper flipper, final FilterNode node) {
        Map<Class, String> joinTabalis = node.getJoinTabalis();
        CharSequence join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
        CharSequence where = node.createSQLExpress(info, joinTabalis);

        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multisplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multisplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        String sql = "DELETE " + ("mysql".equals(this.readPool.getDbtype()) ? "a" : "") + " FROM " + info.getTable(node) + " a" + (join1 == null ? "" : (", " + join1))
            + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2)))) + info.createSQLOrderby(flipper);
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " delete sql="
                + (sql + ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()))));
        return deleteDB(info, flipper, sql);
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Flipper flipper, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        Serializable[] ids = cache.delete(flipper, node);
        if (cacheListener != null) cacheListener.deleteCache(info.getType(), ids);
        return count >= 0 ? count : (ids == null ? 0 : ids.length);
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Serializable... keys) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c = 0;
        for (Serializable key : keys) {
            c += cache.delete(key);
        }
        if (cacheListener != null) cacheListener.deleteCache(info.getType(), keys);
        return count >= 0 ? count : c;
    }

    @Override
    public <T> int deleteCache(Class<T> clazz, Serializable... ids) {
        if (ids.length == 0) return 0;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c = 0;
        for (Serializable id : ids) {
            c += cache.delete(id);
        }
        return c;
    }

    protected static StringBuilder multisplit(char ch1, char ch2, String split, StringBuilder sb, String str, int from) {
        if (str == null) return sb;
        int pos1 = str.indexOf(ch1, from);
        if (pos1 < 0) return sb;
        int pos2 = str.indexOf(ch2, from);
        if (pos2 < 0) return sb;
        if (sb.length() > 0) sb.append(split);
        sb.append(str.substring(pos1 + 1, pos2));
        return multisplit(ch1, ch2, split, sb, str, pos2 + 1);
    }

    //---------------------------- update ----------------------------
    @Override
    public <T> int updateCache(Class<T> clazz, T... values) {
        if (values.length == 0) return 0;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c = 0;
        for (T value : values) {
            c += cache.update(value);
        }
        return c;
    }

    public <T> int reloadCache(Class<T> clazz, Serializable... ids) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        String column = info.getPrimary().field();
        int c = 0;
        for (Serializable id : ids) {
            Sheet<T> sheet = querySheet(false, true, clazz, null, FLIPPER_ONE, FilterNode.create(column, id)).join();
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) c += cache.update(value);
        }
        return c;
    }

    //------------------------- getNumberMap -------------------------
    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, (FilterNode) null, columns);
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(final Class entityClass, final FilterFuncColumn... columns) {
        return getNumberMapAsync(entityClass, (FilterNode) null, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return getNumberMapAsync(entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            final Map map = new HashMap<>();
            if (node == null || node.isCacheUseable(this)) {
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        map.put(ffc.col(col), cache.getNumberResult(ffc.func, ffc.defvalue, col, node));
                    }
                }
                return map;
            }
        }
        return (Map) getNumberMap(info, node, columns).join();
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            final Map map = new HashMap<>();
            if (node == null || node.isCacheUseable(this)) {
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        map.put(ffc.col(col), cache.getNumberResult(ffc.func, ffc.defvalue, col, node));
                    }
                }
                return CompletableFuture.completedFuture(map);
            }
        }
        if (isAsync()) return getNumberMap(info, node, columns);
        return CompletableFuture.supplyAsync(() -> (Map) getNumberMap(info, node, columns).join(), getExecutor());
    }

    protected <N extends Number> CompletableFuture<Map<String, N>> getNumberMap(final EntityInfo info, final FilterNode node, final FilterFuncColumn... columns) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        StringBuilder sb = new StringBuilder();
        for (FilterFuncColumn ffc : columns) {
            for (String col : ffc.cols()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(ffc.func.getColumn((col == null || col.isEmpty() ? "*" : info.getSQLColumn("a", col))));
            }
        }
        final String sql = "SELECT " + sb + " FROM " + info.getTable(node) + " a"
            + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " getnumbermap sql=" + sql);
        return getNumberMapDB(info, sql, columns);
    }

    //------------------------ getNumberResult -----------------------
    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResult(entityClass, func, null, column, (FilterNode) null);
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResultAsync(entityClass, func, null, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, null, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        return getNumberResultAsync(entityClass, func, null, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResult(entityClass, func, null, column, node);
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResultAsync(entityClass, func, null, column, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResult(entityClass, func, defVal, column, (FilterNode) null);
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResultAsync(entityClass, func, defVal, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResultAsync(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            if (node == null || node.isCacheUseable(this)) {
                return cache.getNumberResult(func, defVal, column, node);
            }
        }
        return getNumberResult(info, entityClass, func, defVal, column, node).join();
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            if (node == null || node.isCacheUseable(this)) {
                return CompletableFuture.completedFuture(cache.getNumberResult(func, defVal, column, node));
            }
        }
        if (isAsync()) return getNumberResult(info, entityClass, func, defVal, column, node);
        return CompletableFuture.supplyAsync(() -> getNumberResult(info, entityClass, func, defVal, column, node).join(), getExecutor());
    }

    protected CompletableFuture<Number> getNumberResult(final EntityInfo info, final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : info.getSQLColumn("a", column))) + " FROM " + info.getTable(node) + " a"
            + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(entityClass.getSimpleName() + " getnumberresult sql=" + sql);
        return getNumberResultDB(info, sql, defVal, column);
    }

    //------------------------ queryColumnMap ------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        return queryColumnMapAsync(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        return queryColumnMapAsync(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            if (node == null || node.isCacheUseable(this)) {
                return cache.queryColumnMap(keyColumn, func, funcColumn, node);
            }
        }
        return (Map) queryColumnMap(info, keyColumn, func, funcColumn, node).join();
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
            if (node == null || node.isCacheUseable(this)) {
                return CompletableFuture.completedFuture(cache.queryColumnMap(keyColumn, func, funcColumn, node));
            }
        }
        if (isAsync()) return queryColumnMap(info, keyColumn, func, funcColumn, node);
        return CompletableFuture.supplyAsync(() -> (Map) queryColumnMap(info, keyColumn, func, funcColumn, node).join(), getExecutor());
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMap(final EntityInfo<T> info, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final String sqlkey = info.getSQLColumn(null, keyColumn);
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT a." + sqlkey + ", " + func.getColumn((funcColumn == null || funcColumn.isEmpty() ? "*" : info.getSQLColumn("a", funcColumn)))
            + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + sqlkey;
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " querycolumnmap sql=" + sql);
        return queryColumnMapDB(info, sql, keyColumn);
    }

    //----------------------------- find -----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param pk    主键值
     *
     * @return Entity对象
     */
    @Override
    public <T> T find(Class<T> clazz, Serializable pk) {
        return find(clazz, (SelectColumn) null, pk);
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final Serializable pk) {
        return findAsync(clazz, (SelectColumn) null, pk);
    }

    @Override
    public <T> T find(Class<T> clazz, final SelectColumn selects, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return rs;
        }
        return find(info, selects, pk).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return find(info, selects, pk);
        return CompletableFuture.supplyAsync(() -> find(info, selects, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> find(final EntityInfo<T> info, final SelectColumn selects, Serializable pk) {
        final String sql = "SELECT " + info.getQueryColumns(null, selects) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findDB(info, sql, true, selects);
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return find(clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final String column, final Serializable key) {
        return findAsync(clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterBean bean) {
        return find(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final FilterBean bean) {
        return findAsync(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterNode node) {
        return find(clazz, null, node);
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final FilterNode node) {
        return findAsync(clazz, null, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return findAsync(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) return cache.find(selects, node);
        return find(info, selects, node).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) {
            return CompletableFuture.completedFuture(cache.find(selects, node));
        }
        if (isAsync()) return find(info, selects, node);
        return CompletableFuture.supplyAsync(() -> find(info, selects, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> find(final EntityInfo<T> info, final SelectColumn selects, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findDB(info, sql, false, selects);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk) {
        return findColumn(clazz, column, null, pk);
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable pk) {
        return findColumnAsync(clazz, column, null, pk);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean) {
        return findColumn(clazz, column, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final FilterBean bean) {
        return findColumnAsync(clazz, column, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node) {
        return findColumn(clazz, column, null, node);
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final FilterNode node) {
        return findColumnAsync(clazz, column, null, node);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return findColumn(clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return findColumnAsync(clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return val;
        }
        return findColumn(info, column, defValue, pk).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return findColumn(info, column, defValue, pk);
        return CompletableFuture.supplyAsync(() -> findColumn(info, column, defValue, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumn(final EntityInfo<T> info, String column, final Serializable defValue, final Serializable pk) {
        final String sql = "SELECT " + info.getSQLColumn(null, column) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findColumnDB(info, sql, true, column, defValue);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) return val;
        }
        return findColumn(info, column, defValue, node).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return findColumn(info, column, defValue, node);
        return CompletableFuture.supplyAsync(() -> findColumn(info, column, defValue, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumn(final EntityInfo<T> info, String column, final Serializable defValue, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + info.getSQLColumn("a", column) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findColumnDB(info, sql, false, column, defValue);
    }

    //---------------------------- exists ----------------------------
    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return rs;
        }
        return exists(info, pk).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return exists(info, pk);
        return CompletableFuture.supplyAsync(() -> exists(info, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> exists(final EntityInfo<T> info, Serializable pk) {
        final String sql = "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        return existsDB(info, sql, true);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterBean bean) {
        return existsAsync(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(node);
            if (rs || cache.isFullLoaded()) return rs;
        }
        return exists(info, node).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(node);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return exists(info, node);
        return CompletableFuture.supplyAsync(() -> exists(info, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> exists(final EntityInfo<T> info, FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        return existsDB(info, sql, false);
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, null, FilterNode.create(column, key)));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<HashSet<V>> queryColumnSetAsync(final String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return queryColumnListAsync(selectedColumn, clazz, null, FilterNode.create(column, key)).thenApply((list) -> new LinkedHashSet(list));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean)));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<HashSet<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnListAsync(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean)).thenApply((list) -> new LinkedHashSet(list));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, null, node));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<HashSet<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnListAsync(selectedColumn, clazz, null, node).thenApply((list) -> new LinkedHashSet(list));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        return queryColumnList(selectedColumn, clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        return queryColumnListAsync(selectedColumn, clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnListAsync(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnList(selectedColumn, clazz, null, node);
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnListAsync(selectedColumn, clazz, null, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnListAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        final List<T> list = queryList(clazz, SelectColumn.createIncludes(selectedColumn), flipper, node);
        final List<V> rs = new ArrayList<>();
        if (list.isEmpty()) return rs;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        for (T t : list) {
            rs.add(selected.get(t));
        }
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryListAsync(clazz, SelectColumn.createIncludes(selectedColumn), flipper, node).thenApply((List<T> list) -> {
            final List<V> rs = new ArrayList<>();
            if (list.isEmpty()) return rs;
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
            for (T t : list) {
                rs.add(selected.get(t));
            }
            return rs;
        });
    }

    /**
     * 根据指定参数查询对象某个字段的集合
     * <p>
     * @param <T>            Entity类的泛型
     * @param <V>            字段值的类型
     * @param selectedColumn 字段名
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param bean           过滤Bean
     *
     * @return 字段集合
     */
    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSheetAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> sheet = querySheet(clazz, SelectColumn.createIncludes(selectedColumn), flipper, node);
        final Sheet<V> rs = new Sheet<>();
        if (sheet.isEmpty()) return rs;
        rs.setTotal(sheet.getTotal());
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        final List<V> list = new ArrayList<>();
        for (T t : sheet.getRows()) {
            list.add(selected.get(t));
        }
        rs.setRows(list);
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheetAsync(clazz, SelectColumn.createIncludes(selectedColumn), flipper, node).thenApply((Sheet<T> sheet) -> {
            final Sheet<V> rs = new Sheet<>();
            if (sheet.isEmpty()) return rs;
            rs.setTotal(sheet.getTotal());
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
            final List<V> list = new ArrayList<>();
            for (T t : sheet.getRows()) {
                list.add(selected.get(t));
            }
            rs.setRows(list);
            return rs;
        });
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>       主键泛型
     * @param <T>       Entity泛型
     * @param clazz     Entity类
     * @param keyStream 主键Stream
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final Stream<K> keyStream) {
        return queryMap(clazz, null, keyStream);
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final Stream<K> keyStream) {
        return queryMapAsync(clazz, null, keyStream);
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>   主键泛型
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param bean  FilterBean
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final FilterBean bean) {
        return queryMap(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final FilterBean bean) {
        return queryMapAsync(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>   主键泛型
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param node  FilterNode
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final FilterNode node) {
        return queryMap(clazz, null, node);
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final FilterNode node) {
        return queryMapAsync(clazz, null, node);
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>       主键泛型
     * @param <T>       Entity泛型
     * @param clazz     Entity类
     * @param selects   指定字段
     * @param keyStream 主键Stream
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) return new LinkedHashMap<>();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final ArrayList<K> ids = new ArrayList<>();
        keyStream.forEach(k -> ids.add(k));
        final Attribute<T, Serializable> primary = info.primary;
        List<T> rs = queryList(clazz, FilterNode.create(primary.field(), ids));
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) return new LinkedHashMap<>();
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) return CompletableFuture.completedFuture(new LinkedHashMap<>());
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final ArrayList<K> ids = new ArrayList<>();
        keyStream.forEach(k -> ids.add(k));
        final Attribute<T, Serializable> primary = info.primary;
        return queryListAsync(clazz, FilterNode.create(primary.field(), ids)).thenApply((List<T> rs) -> {
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) return new LinkedHashMap<>();
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>     主键泛型
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param bean    FilterBean
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryMap(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryMapAsync(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>     主键泛型
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param node    FilterNode
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, node);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, Serializable> primary = info.primary;
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) return new LinkedHashMap<>();
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryListAsync(clazz, selects, node).thenApply((List<T> rs) -> {
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, Serializable> primary = info.primary;
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) return new LinkedHashMap<>();
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param key    过滤字段值
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return queryList(clazz, (SelectColumn) null, null, FilterNode.create(column, key));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final String column, final Serializable key) {
        return queryListAsync(clazz, (SelectColumn) null, null, FilterNode.create(column, key));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz) {
        return queryList(clazz, (SelectColumn) null, null, (FilterNode) null);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz) {
        return queryListAsync(clazz, (SelectColumn) null, null, (FilterNode) null);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param bean  过滤Bean
     *
     * @return Entity对象集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, (SelectColumn) null, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final FilterBean bean) {
        return queryListAsync(clazz, (SelectColumn) null, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, null, node);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final FilterNode node) {
        return queryListAsync(clazz, (SelectColumn) null, null, node);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合， 对象只填充或排除SelectField指定的字段
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param selects 收集的字段
     * @param bean    过滤Bean
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, selects, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, SelectColumn selects, final FilterBean bean) {
        return queryListAsync(clazz, selects, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryList(clazz, selects, null, node);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, SelectColumn selects, final FilterNode node) {
        return queryListAsync(clazz, selects, null, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return queryList(clazz, null, flipper, FilterNode.create(column, key));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return queryListAsync(clazz, null, flipper, FilterNode.create(column, key));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryListAsync(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryList(clazz, null, flipper, node);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryListAsync(clazz, null, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryListAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, clazz, selects, flipper, node).join().list(true);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, clazz, selects, flipper, node).thenApply((rs) -> rs.list(true));
    }

    //-----------------------sheet----------------------------
    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheetAsync(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheet(clazz, null, flipper, node);
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheetAsync(clazz, null, flipper, node);
    }

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据， 对象只填充或排除SelectField指定的字段
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param selects 收集的字段集合
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheetAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, true, clazz, selects, flipper, node).join();
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) return querySheet(true, true, clazz, selects, flipper, node);
        return CompletableFuture.supplyAsync(() -> querySheet(true, true, clazz, selects, flipper, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Sheet<T>> querySheet(final boolean readcache, final boolean needtotal, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readcache && cache != null && cache.isFullLoaded()) {
            if (node == null || node.isCacheUseable(this)) {
                if (info.isLoggable(logger, Level.FINEST)) logger.finest(clazz.getSimpleName() + " cache query predicate = " + (node == null ? null : node.createPredicate(cache)));
                return CompletableFuture.completedFuture(cache.querySheet(needtotal, selects, flipper, node));
            }
        }
        return querySheetDB(info, needtotal, selects, flipper, node);
    }
}
