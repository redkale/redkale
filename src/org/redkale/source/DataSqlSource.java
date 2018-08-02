/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.URL;
import java.nio.ByteBuffer;
import java.sql.*;
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

    protected final BiFunction<DataSource, Class, List> fullloader = (s, t) -> ((Sheet) querySheetCompose(false, false, t, null, null, (FilterNode) null).join()).list(true);

    @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    public DataSqlSource(String unitName, URL persistxml, Properties readprop, Properties writeprop) {
        final AtomicInteger counter = new AtomicInteger();
        this.threads = Integer.decode(readprop.getProperty(JDBC_CONNECTIONS_LIMIT, "" + Runtime.getRuntime().availableProcessors() * 16));
        int maxconns = Math.max(8, Integer.decode(readprop.getProperty(JDBC_CONNECTIONS_LIMIT, "" + Runtime.getRuntime().availableProcessors() * 200)));
        if (readprop != writeprop) {
            this.threads += Integer.decode(writeprop.getProperty(JDBC_CONNECTIONS_LIMIT, "" + Runtime.getRuntime().availableProcessors() * 16));
            maxconns = 0;
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
        final int bufferCapacity = Math.max(8 * 1024, Integer.decode(readprop.getProperty(JDBC_CONNECTIONSCAPACITY, "" + 8 * 1024)));
        this.bufferPool = new ObjectPool<>(new AtomicLong(), new AtomicLong(), this.threads,
            (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) return false;
                e.clear();
                return true;
            });
        this.name = unitName;
        this.persistxml = persistxml;
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty(JDBC_CACHE_MODE));
        ArrayBlockingQueue<DBChannel> queue = maxconns > 0 ? new ArrayBlockingQueue(maxconns) : null;
        this.readPool = createPoolSource(this, "read", queue, readprop);
        this.writePool = createPoolSource(this, "write", queue, writeprop);
    }

    @Local
    public abstract int directExecute(String sql);

    @Local
    public abstract int[] directExecute(String... sqls);

    @Local
    public abstract void directQuery(String sql, Consumer<ResultSet> consumer);

    //是否异步， 为true则只能调用pollAsync方法，为false则只能调用poll方法
    protected abstract boolean isAsync();

    //index从1开始
    protected abstract String prepareParamSign(int index);

    //创建连接池
    protected abstract PoolSource<DBChannel> createPoolSource(DataSource source, String rwtype, ArrayBlockingQueue queue, Properties prop);

    //插入纪录
    protected abstract <T> CompletableFuture<Integer> insertDB(final EntityInfo<T> info, T... values);

    //删除记录
    protected abstract <T> CompletableFuture<Integer> deleteDB(final EntityInfo<T> info, Flipper flipper, final String sql);

    //更新纪录
    protected abstract <T> CompletableFuture<Integer> updateDB(final EntityInfo<T> info, T... values);

    //更新纪录
    protected abstract <T> CompletableFuture<Integer> updateDB(final EntityInfo<T> info, Flipper flipper, final String sql, final boolean prepared, Object... params);

    //查询Number Map数据
    protected abstract <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(final EntityInfo<T> info, final String sql, final FilterFuncColumn... columns);

    //查询Number数据
    protected abstract <T> CompletableFuture<Number> getNumberResultDB(final EntityInfo<T> info, final String sql, final Number defVal, final String column);

    //查询Map数据
    protected abstract <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(final EntityInfo<T> info, final String sql, final String keyColumn);

    //查询单条记录
    protected abstract <T> CompletableFuture<T> findDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final SelectColumn selects);

    //查询单条记录的单个字段
    protected abstract <T> CompletableFuture<Serializable> findColumnDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final String column, final Serializable defValue);

    //判断记录是否存在
    protected abstract <T> CompletableFuture<Boolean> existsDB(final EntityInfo<T> info, final String sql, final boolean onlypk);

    //查询一页数据
    protected abstract <T> CompletableFuture<Sheet<T>> querySheetDB(final EntityInfo<T> info, final boolean needtotal, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    protected <T> T infoGetValue(EntityInfo<T> info, final SelectColumn sels, final ResultSet set) throws SQLException {
        return info.getValue(sels, set);
    }

    protected <T> String createSQLOrderby(EntityInfo<T> info, Flipper flipper) {
        return info.createSQLOrderby(flipper);
    }

    protected Map<Class, String> getJoinTabalis(FilterNode node) {
        return node == null ? null : node.getJoinTabalis();
    }

    protected <T> CharSequence createSQLJoin(FilterNode node, final Function<Class, EntityInfo> func, final boolean update, final Map<Class, String> joinTabalis, final Set<String> haset, final EntityInfo<T> info) {
        return node == null ? null : node.createSQLJoin(func, update, joinTabalis, haset, info);
    }

    protected <T> CharSequence createSQLExpress(FilterNode node, final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return node == null ? null : node.createSQLExpress(info, joinTabalis);
    }

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
     *
     * @return 影响的记录条数
     */
    @Override
    public <T> int insert(@RpcCall(DataCallArrayAttribute.class) T... values) {
        if (values.length == 0) return 0;
        checkEntity("insert", false, values);
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.autouuid) {
            for (T value : values) {
                info.createPrimaryValue(value);
            }
        }
        if (info.isVirtualEntity()) return insertCache(info, values);
        return insertDB(info, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> insertAsync(@RpcCall(DataCallArrayAttribute.class) T... values) {
        if (values.length == 0) return CompletableFuture.completedFuture(0);
        CompletableFuture future = checkEntity("insert", true, values);
        if (future != null) return future;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.autouuid) {
            for (T value : values) {
                info.createPrimaryValue(value);
            }
        }
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> insertCache(info, values), getExecutor());
        }
        if (isAsync()) return insertDB(info, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    insertCache(info, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> insertDB(info, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, values);
            }
        });
    }

    protected <T> int insertCache(final EntityInfo<T> info, T... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return 0;
        int c = 0;
        for (final T value : values) {
            c += cache.insert(value);
        }
        if (cacheListener != null) cacheListener.insertCache(info.getType(), values);
        return c;
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

    //----------------------------- deleteCompose -----------------------------
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
        return deleteCompose(info, ids).whenComplete((rs, t) -> {
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
        if (isAsync()) return deleteCompose(info, ids).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, ids);
                }
            });
        return CompletableFuture.supplyAsync(() -> deleteCompose(info, ids).join(), getExecutor()).whenComplete((rs, t) -> {
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
        return DataSqlSource.this.deleteCompose(info, flipper, node).whenComplete((rs, t) -> {
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
        if (isAsync()) return DataSqlSource.this.deleteCompose(info, flipper, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, flipper, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.deleteCompose(info, flipper, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, flipper, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> deleteCompose(final EntityInfo<T> info, final Serializable... ids) {
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
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
        return deleteDB(info, null, sql);
    }

    protected <T> CompletableFuture<Integer> deleteCompose(final EntityInfo<T> info, final Flipper flipper, final FilterNode node) {
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
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " delete sql="
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
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>    Entity类泛型
     * @param values Entity对象
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int update(T... values) {
        if (values.length == 0) return -1;
        checkEntity("update", false, values);
        final Class<T> clazz = (Class<T>) values[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, values);
        return updateDB(info, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateAsync(final T... values) {
        if (values.length == 0) return CompletableFuture.completedFuture(-1);
        CompletableFuture future = checkEntity("update", true, values);
        if (future != null) return future;
        final Class<T> clazz = (Class<T>) values[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return CompletableFuture.supplyAsync(() -> updateCache(info, -1, values), getExecutor());
        if (isAsync()) return updateDB(info, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> updateDB(info, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, values);
            }
        });
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param id     主键值
     * @param column 过滤字段名
     * @param value  过滤字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, Serializable id, String column, Serializable value) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, id, column, value);
        return updateColumnCompose(info, id, column, value).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, id, column, value);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, id, column, value), getExecutor());
        }
        if (isAsync()) return updateColumnCompose(info, id, column, value).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, id, column, value);
                }
            });
        return CompletableFuture.supplyAsync(() -> updateColumnCompose(info, id, column, value).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, id, column, value);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, Serializable id, String column, final Serializable value) {
        if (value instanceof byte[]) {
            String sql = "UPDATE " + info.getTable(id) + " SET " + info.getSQLColumn(null, column) + " = " + prepareParamSign(1) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
            return updateDB(info, null, sql, true, value);
        } else {
            String sql = "UPDATE " + info.getTable(id) + " SET " + info.getSQLColumn(null, column) + " = "
                + info.formatToString(value) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
            return updateDB(info, null, sql, false);
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param value  过滤字段值
     * @param node   过滤node 不能为null
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, String column, Serializable value, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, column, value, node);
        return DataSqlSource.this.updateColumnCompose(info, column, value, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, column, value, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final String column, final Serializable value, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, column, value, node), getExecutor());
        }
        if (isAsync()) return DataSqlSource.this.updateColumnCompose(info, column, value, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, column, value, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.updateColumnCompose(info, column, value, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, column, value, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final String column, final Serializable value, final FilterNode node) {
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
        String alias = "postgresql".equals(writePool.dbtype) ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        if (value instanceof byte[]) {
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                + " SET " + info.getSQLColumn(alias, column) + " = " + prepareParamSign(1)
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return updateDB(info, null, sql, true, value);
        } else {
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                + " SET " + info.getSQLColumn(alias, column) + " = " + info.formatToString(value)
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return updateDB(info, null, sql, false);
        }
    }

    /**
     * 根据主键值更新对象的多个column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param id     主键值
     * @param values 字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, id, values);
        return DataSqlSource.this.updateColumnCompose(info, id, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, id, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final Serializable id, final ColumnValue... values) {
        if (values == null || values.length < 1) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, id, values), getExecutor());
        }
        if (isAsync()) return DataSqlSource.this.updateColumnCompose(info, id, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, id, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.updateColumnCompose(info, id, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, id, values);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final Serializable id, final ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        for (ColumnValue col : values) {
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) throw new RuntimeException(info.getType() + " cannot found column " + col.getColumn());
            if (setsql.length() > 0) setsql.append(", ");
            String c = info.getSQLColumn(null, col.getColumn());
            if (col.getValue() instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) col.getValue());
                setsql.append(c).append(" = ").append(prepareParamSign(++index));
            } else {
                setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
            }
        }
        String sql = "UPDATE " + info.getTable(id) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
        if (blobs == null) return updateDB(info, null, sql, false);
        return updateDB(info, null, sql, true, blobs.toArray());
    }

    /**
     * 根据主键值更新对象的多个column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param node   过滤条件
     * @param values 字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return updateColumn(clazz, node, null, values);
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return updateColumnAsync(clazz, node, null, values);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, node, flipper, values);
        return DataSqlSource.this.updateColumnCompose(info, node, flipper, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, node, flipper, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (values == null || values.length < 1) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, node, flipper, values), getExecutor());
        }
        if (isAsync()) return DataSqlSource.this.updateColumnCompose(info, node, flipper, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, node, flipper, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.updateColumnCompose(info, node, flipper, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, node, flipper, values);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        String alias = "postgresql".equals(writePool.dbtype) ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        for (ColumnValue col : values) {
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            if (setsql.length() > 0) setsql.append(", ");
            String c = info.getSQLColumn(alias, col.getColumn());
            if (col.getValue() instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) col.getValue());
                setsql.append(c).append(" = ").append(prepareParamSign(++index));
            } else {
                setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
            }
        }
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
        String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
            + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))))
            + info.createSQLOrderby(flipper);
        if (blobs == null) return updateDB(info, null, sql, false);
        return updateDB(info, flipper, sql, true, blobs.toArray());
    }

    @Override
    public <T> int updateColumn(final T bean, final String... columns) {
        return updateColumn(bean, SelectColumn.includes(columns));
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T bean, final String... columns) {
        return updateColumnAsync(bean, SelectColumn.includes(columns));
    }

    @Override
    public <T> int updateColumn(final T bean, final FilterNode node, final String... columns) {
        return updateColumn(bean, node, SelectColumn.includes(columns));
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T bean, final FilterNode node, final String... columns) {
        return updateColumnAsync(bean, node, SelectColumn.includes(columns));
    }

    @Override
    public <T> int updateColumn(final T bean, final SelectColumn selects) {
        if (bean == null || selects == null) return -1;
        Class<T> clazz = (Class) bean.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, false, bean, null, selects);
        return DataSqlSource.this.updateColumnCompose(info, false, bean, null, selects).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, false, bean, null, selects);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T bean, final SelectColumn selects) {
        if (bean == null || selects == null) return CompletableFuture.completedFuture(-1);
        Class<T> clazz = (Class) bean.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, false, bean, null, selects), getExecutor());
        }
        if (isAsync()) return DataSqlSource.this.updateColumnCompose(info, false, bean, null, selects).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, false, bean, null, selects);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.updateColumnCompose(info, false, bean, null, selects).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, false, bean, null, selects);
            }
        });
    }

    @Override
    public <T> int updateColumn(final T bean, final FilterNode node, final SelectColumn selects) {
        if (bean == null || node == null || selects == null) return -1;
        Class<T> clazz = (Class) bean.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) return updateCache(info, -1, true, bean, node, selects);
        return DataSqlSource.this.updateColumnCompose(info, true, bean, node, selects).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, true, bean, node, selects);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T bean, final FilterNode node, final SelectColumn selects) {
        if (bean == null || node == null || selects == null) return CompletableFuture.completedFuture(-1);
        Class<T> clazz = (Class) bean.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return CompletableFuture.supplyAsync(() -> updateCache(info, -1, true, bean, node, selects), getExecutor());
        }
        if (isAsync()) return DataSqlSource.this.updateColumnCompose(info, true, bean, node, selects).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, true, bean, node, selects);
                }
            });
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.updateColumnCompose(info, true, bean, node, selects).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, true, bean, node, selects);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final boolean neednode, final T bean, final FilterNode node, final SelectColumn selects) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        String alias = "postgresql".equals(writePool.dbtype) ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) continue;
            if (setsql.length() > 0) setsql.append(", ");
            setsql.append(info.getSQLColumn(alias, attr.field()));
            Serializable val = attr.get(bean);
            if (val instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) val);
                setsql.append(" = ").append(prepareParamSign(++index));
            } else {
                setsql.append(" = ").append(info.formatToString(val));
            }
        }
        if (neednode) {
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
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            if (blobs == null) return updateDB(info, null, sql, false);
            return updateDB(info, null, sql, true, blobs.toArray());
        } else {
            final Serializable id = info.getPrimary().get(bean);
            String sql = "UPDATE " + info.getTable(id) + " a SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
            if (blobs == null) return updateDB(info, null, sql, false);
            return updateDB(info, null, sql, true, blobs.toArray());
        }
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final boolean neednode, final T bean, final FilterNode node, final SelectColumn selects) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) continue;
            attrs.add(attr);
        }
        if (neednode) {
            T[] rs = cache.update(bean, attrs, node);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return count >= 0 ? count : (rs == null ? 0 : rs.length);
        } else {
            T rs = cache.update(bean, attrs);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return count >= 0 ? count : (rs == null ? 0 : 1);
        }
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            attrs.add(attr);
            cols.add(col);
        }
        T[] rs = cache.updateColumn(node, flipper, attrs, cols);
        if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final Serializable id, final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            attrs.add(attr);
            cols.add(col);
        }
        T rs = cache.updateColumn(id, attrs, cols);
        if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, String column, final Serializable value, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        T[] rs = cache.update(info.getAttribute(column), value, node);
        if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final Serializable id, final String column, final Serializable value) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        T rs = cache.update(id, info.getAttribute(column), value);
        if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, T... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c2 = 0;
        for (final T value : values) {
            c2 += cache.update(value);
        }
        if (cacheListener != null) cacheListener.updateCache(info.getType(), values);
        return count >= 0 ? count : c2;
    }

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
            Sheet<T> sheet = querySheetCompose(false, true, clazz, null, FLIPPER_ONE, FilterNode.create(column, id)).join();
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) c += cache.update(value);
        }
        return c;
    }

    //------------------------- getNumberMapCompose -------------------------
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
        return (Map) getNumberMapCompose(info, node, columns).join();
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
        if (isAsync()) return getNumberMapCompose(info, node, columns);
        return CompletableFuture.supplyAsync(() -> (Map) getNumberMapCompose(info, node, columns).join(), getExecutor());
    }

    protected <N extends Number> CompletableFuture<Map<String, N>> getNumberMapCompose(final EntityInfo info, final FilterNode node, final FilterFuncColumn... columns) {
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
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " getnumbermap sql=" + sql);
        return getNumberMapDB(info, sql, columns);
    }

    //------------------------ getNumberResultCompose -----------------------
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
        return getNumberResultCompose(info, entityClass, func, defVal, column, node).join();
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
        if (isAsync()) return getNumberResultCompose(info, entityClass, func, defVal, column, node);
        return CompletableFuture.supplyAsync(() -> getNumberResultCompose(info, entityClass, func, defVal, column, node).join(), getExecutor());
    }

    protected CompletableFuture<Number> getNumberResultCompose(final EntityInfo info, final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : info.getSQLColumn("a", column))) + " FROM " + info.getTable(node) + " a"
            + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(entityClass.getSimpleName() + " getnumberresult sql=" + sql);
        return getNumberResultDB(info, sql, defVal, column);
    }

    //------------------------ queryColumnMapCompose ------------------------
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
        return (Map) queryColumnMapCompose(info, keyColumn, func, funcColumn, node).join();
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
        if (isAsync()) return queryColumnMapCompose(info, keyColumn, func, funcColumn, node);
        return CompletableFuture.supplyAsync(() -> (Map) queryColumnMapCompose(info, keyColumn, func, funcColumn, node).join(), getExecutor());
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapCompose(final EntityInfo<T> info, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final String sqlkey = info.getSQLColumn(null, keyColumn);
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT a." + sqlkey + ", " + func.getColumn((funcColumn == null || funcColumn.isEmpty() ? "*" : info.getSQLColumn("a", funcColumn)))
            + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + sqlkey;
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " querycolumnmap sql=" + sql);
        return queryColumnMapDB(info, sql, keyColumn);
    }

    //----------------------------- findCompose -----------------------------
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
        return findCompose(info, selects, pk).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return findCompose(info, selects, pk);
        return CompletableFuture.supplyAsync(() -> findCompose(info, selects, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> findCompose(final EntityInfo<T> info, final SelectColumn selects, Serializable pk) {
        final String sql = "SELECT " + info.getQueryColumns(null, selects) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
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
        return DataSqlSource.this.findCompose(info, selects, node).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) {
            return CompletableFuture.completedFuture(cache.find(selects, node));
        }
        if (isAsync()) return DataSqlSource.this.findCompose(info, selects, node);
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.findCompose(info, selects, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> findCompose(final EntityInfo<T> info, final SelectColumn selects, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
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
        return findColumnCompose(info, column, defValue, pk).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return findColumnCompose(info, column, defValue, pk);
        return CompletableFuture.supplyAsync(() -> findColumnCompose(info, column, defValue, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumnCompose(final EntityInfo<T> info, String column, final Serializable defValue, final Serializable pk) {
        final String sql = "SELECT " + info.getSQLColumn(null, column) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
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
        return DataSqlSource.this.findColumnCompose(info, column, defValue, node).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return DataSqlSource.this.findColumnCompose(info, column, defValue, node);
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.findColumnCompose(info, column, defValue, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumnCompose(final EntityInfo<T> info, String column, final Serializable defValue, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT " + info.getSQLColumn("a", column) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findColumnDB(info, sql, false, column, defValue);
    }

    //---------------------------- existsCompose ----------------------------
    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return rs;
        }
        return existsCompose(info, pk).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return existsCompose(info, pk);
        return CompletableFuture.supplyAsync(() -> existsCompose(info, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> existsCompose(final EntityInfo<T> info, Serializable pk) {
        final String sql = "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
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
        return DataSqlSource.this.existsCompose(info, node).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(node);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return DataSqlSource.this.existsCompose(info, node);
        return CompletableFuture.supplyAsync(() -> DataSqlSource.this.existsCompose(info, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> existsCompose(final EntityInfo<T> info, FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
        final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
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
        final List<T> list = queryList(clazz, SelectColumn.includes(selectedColumn), flipper, node);
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
        return queryListAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node).thenApply((List<T> list) -> {
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
        Sheet<T> sheet = querySheet(clazz, SelectColumn.includes(selectedColumn), flipper, node);
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
        return querySheetAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node).thenApply((Sheet<T> sheet) -> {
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
        return querySheetCompose(true, false, clazz, selects, flipper, node).join().list(true);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheetCompose(true, false, clazz, selects, flipper, node).thenApply((rs) -> rs.list(true));
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
        return querySheetCompose(true, true, clazz, selects, flipper, node).join();
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) return querySheetCompose(true, true, clazz, selects, flipper, node);
        return CompletableFuture.supplyAsync(() -> querySheetCompose(true, true, clazz, selects, flipper, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Sheet<T>> querySheetCompose(final boolean readcache, final boolean needtotal, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readcache && cache != null && cache.isFullLoaded()) {
            if (node == null || node.isCacheUseable(this)) {
                if (info.isLoggable(logger, Level.FINEST, " cache query predicate = ")) logger.finest(clazz.getSimpleName() + " cache query predicate = " + (node == null ? null : node.createPredicate(cache)));
                return CompletableFuture.completedFuture(cache.querySheet(needtotal, selects, flipper, node));
            }
        }
        return querySheetDB(info, needtotal, selects, flipper, node);
    }
}
