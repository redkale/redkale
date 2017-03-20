/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.util.*;

/**
 * DataSource的JDBC实现类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class DataJdbcSource implements DataSource, DataCacheListener, Function<Class, EntityInfo>, AutoCloseable {

    private static final Flipper FLIPPER_ONE = new Flipper(1);

    final Logger logger = Logger.getLogger(DataJdbcSource.class.getSimpleName());

    final AtomicBoolean debug = new AtomicBoolean(logger.isLoggable(Level.FINEST));

    final String name;

    final URL conf;

    final boolean cacheForbidden;

    private final PoolJdbcSource readPool;

    private final PoolJdbcSource writePool;

    @Resource(name = "$")
    private DataCacheListener cacheListener;

    private final BiFunction<DataSource, Class, List> fullloader = (s, t) -> querySheet(false, false, t, null, null, (FilterNode) null).list(true);

    public DataJdbcSource(String unitName, Properties readprop, Properties writeprop) {
        this.name = unitName;
        this.conf = null;
        this.readPool = new PoolJdbcSource(this, "read", readprop);
        this.writePool = new PoolJdbcSource(this, "write", writeprop);
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty("shared-cache-mode"));
    }

    public final String name() {
        return name;
    }

    @Override
    public void close() throws Exception {
        readPool.close();
        writePool.close();
    }

    public String getName() {
        return name;
    }

    private Connection createReadSQLConnection() {
        return readPool.poll();
    }

    private <T> Connection createWriteSQLConnection() {
        return writePool.poll();
    }

    private void closeSQLConnection(final Connection sqlconn) {
        if (sqlconn == null) return;
        try {
            sqlconn.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "closeSQLConnection abort", e);
        }
    }

    @Override
    public EntityInfo apply(Class t) {
        return loadEntityInfo(t);
    }

    private <T> EntityInfo<T> loadEntityInfo(Class<T> clazz) {
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

    //----------------------insertCache-----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>    Entity类泛型
     * @param values Entity对象
     */
    @Override
    public <T> void insert(T... values) {
        if (values.length == 0) return;
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
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            insert(null, info, values);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            insert(conn, info, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void insert(final AsyncHandler<Void, T[]> handler, final T... values) {
        insert(values);
        if (handler != null) handler.completed(null, values);
    }

    private <T> void insert(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return;
        try {
            if (!info.isVirtualEntity()) {
                final String sql = info.getInsertSQL(values[0]);
                final Class primaryType = info.getPrimary().type();
                final Attribute primary = info.getPrimary();
                Attribute<T, Serializable>[] attrs = info.insertAttributes;
                conn.setReadOnly(false);
                PreparedStatement prestmt = createInsertPreparedStatement(conn, sql, info, values);
                try {
                    prestmt.executeBatch();
                } catch (SQLException se) {
                    if (info.tableStrategy == null || !info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) throw se;
                    synchronized (info.tables) {
                        final String oldTable = info.table;
                        final String newTable = info.getTable(values[0]);
                        if (!info.tables.contains(newTable)) {
                            try {
                                Statement st = conn.createStatement();
                                st.execute(info.tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", oldTable));
                                st.close();
                                info.tables.add(newTable);
                            } catch (SQLException sqle) { //多进程并发时可能会出现重复建表
                                if (newTable.indexOf('.') > 0 && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) {
                                    Statement st;
                                    try {
                                        st = conn.createStatement();
                                        st.execute("CREATE DATABASE " + newTable.substring(0, newTable.indexOf('.')));
                                        st.close();
                                    } catch (SQLException sqle1) {
                                        logger.log(Level.SEVERE, "create database(" + newTable.substring(0, newTable.indexOf('.')) + ") error", sqle1);
                                    }
                                    try {
                                        st = conn.createStatement();
                                        st.execute(info.tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", oldTable));
                                        st.close();
                                        info.tables.add(newTable);
                                    } catch (SQLException sqle2) {
                                        logger.log(Level.SEVERE, "create table2(" + info.tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", oldTable) + ") error", sqle2);
                                    }
                                } else {
                                    logger.log(Level.SEVERE, "create table(" + info.tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", oldTable) + ") error", sqle);
                                }
                            }
                        }
                    }
                    prestmt.close();
                    prestmt = createInsertPreparedStatement(conn, sql, info, values);
                    prestmt.executeBatch();
                }
                if (info.autoGenerated) { //由数据库自动生成主键值
                    ResultSet set = prestmt.getGeneratedKeys();
                    int i = -1;
                    while (set.next()) {
                        if (primaryType == int.class) {
                            primary.set(values[++i], set.getInt(1));
                        } else if (primaryType == long.class) {
                            primary.set(values[++i], set.getLong(1));
                        } else {
                            primary.set(values[++i], set.getObject(1));
                        }
                    }
                    set.close();
                }
                prestmt.close();
                //------------------------------------------------------------
                if (debug.get() && info.isLoggable(Level.FINEST)) {  //打印调试信息
                    char[] sqlchars = sql.toCharArray();
                    for (final T value : values) {
                        //-----------------------------
                        StringBuilder sb = new StringBuilder(128);
                        int i = 0;
                        for (char ch : sqlchars) {
                            if (ch == '?') {
                                Object obj = attrs[i++].get(value);
                                if (obj != null && obj.getClass().isArray()) {
                                    sb.append("'[length=").append(java.lang.reflect.Array.getLength(obj)).append("]'");
                                } else {
                                    sb.append(FilterNode.formatToString(obj));
                                }
                            } else {
                                sb.append(ch);
                            }
                        }
                        logger.finest(info.getType().getSimpleName() + " insert sql=" + sb.toString().replaceAll("(\r|\n)", "\\n"));
                    }
                } //打印结束
            }
            final EntityCache<T> cache = info.getCache();
            if (cache != null) { //更新缓存
                for (final T value : values) {
                    cache.insert(value);
                }
                if (cacheListener != null) cacheListener.insertCache(info.getType(), values);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> PreparedStatement createInsertPreparedStatement(final Connection conn, final String sql,
        final EntityInfo<T> info, T... values) throws SQLException {
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        final PreparedStatement prestmt = info.autoGenerated ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);

        for (final T value : values) {
            int i = 0;
            if (info.autouuid) info.createPrimaryValue(value);
            for (Attribute<T, Serializable> attr : attrs) {
                Serializable val = attr.get(value);
                if (val instanceof byte[]) {
                    Blob blob = conn.createBlob();
                    blob.setBytes(1, (byte[]) val);
                    prestmt.setObject(++i, blob);
                } else {
                    prestmt.setObject(++i, val);
                }
            }
            prestmt.addBatch();
        }
        return prestmt;
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

    //-------------------------deleteCache--------------------------
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
        if (values.length > 1) { //检查对象是否都是同一个Entity类
            Class clazz = null;
            for (T val : values) {
                if (clazz == null) {
                    clazz = val.getClass();
                    continue;
                }
                if (clazz != val.getClass()) {
                    throw new RuntimeException("DataSource.delete must the same Class Entity, but diff is " + clazz + " and " + val.getClass());
                }
            }
        }
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) { //虚拟表只更新缓存Cache
            return delete(null, info, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return delete(conn, info, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, T[]> handler, final T... values) {
        int rs = delete(values);
        if (handler != null) handler.completed(rs, values);
        return rs;
    }

    private <T> int delete(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return -1;
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[values.length];
        int i = 0;
        for (final T value : values) {
            ids[i++] = (Serializable) primary.get(value);
        }
        return delete(conn, info, ids);
    }

    @Override
    public <T> int delete(Class<T> clazz, Serializable... ids) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) { //虚拟表只更新缓存Cache
            return delete(null, info, ids);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return delete(conn, info, ids);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, Serializable[]> handler, final Class<T> clazz, final Serializable... ids) {
        int rs = delete(clazz, ids);
        if (handler != null) handler.completed(rs, ids);
        return rs;
    }

    private <T> int delete(final Connection conn, final EntityInfo<T> info, Serializable... keys) {
        if (keys.length == 0) return -1;
        int c = -1;
        int c2 = 0;
        try {
            if (!info.isVirtualEntity()) {
                conn.setReadOnly(false);
                final Statement stmt = conn.createStatement();
                for (Serializable key : keys) {
                    String sql = "DELETE FROM " + info.getTable(key) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(key);
                    if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                    stmt.addBatch(sql);
                }
                int[] pc = stmt.executeBatch();
                c = 0;
                for (int p : pc) {
                    if (p >= 0) c += p;
                }
                stmt.close();
            }
            //------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            for (Serializable key : keys) {
                c2 += cache.delete(key);
            }
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), keys);
            return c >= 0 ? c : c2;
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) return c >= 0 ? c : c2;
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> int delete(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return delete(null, info, null, node);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return delete(conn, info, null, node);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        int rs = delete(clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> int delete(Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return delete(null, info, flipper, node);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return delete(conn, info, flipper, node);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final Flipper flipper, FilterNode node) {
        int rs = delete(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T> int delete(final Connection conn, final EntityInfo<T> info, final Flipper flipper, final FilterNode node) {
        int c = -1;
        try {
            if (!info.isVirtualEntity()) {
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
                String sql = "DELETE " + (this.readPool.isMysql() ? "a" : "") + " FROM " + info.getTable(node) + " a" + (join1 == null ? "" : (", " + join1))
                    + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                        : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2)))) + info.createSQLOrderby(flipper)
                    + ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()));
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                conn.setReadOnly(false);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
            }
            //------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            Serializable[] ids = cache.delete(flipper, node);
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), ids);
            return c >= 0 ? c : (ids == null ? 0 : ids.length);
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) return c;
            throw new RuntimeException(e);
        }
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

    //------------------------update---------------------------
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
        if (values.length == 0) return 0;
        if (values.length > 1) { //检查对象是否都是同一个Entity类
            Class clazz = null;
            for (T val : values) {
                if (clazz == null) {
                    clazz = val.getClass();
                    continue;
                }
                if (clazz != val.getClass()) {
                    throw new RuntimeException("DataSource.update must the same Class Entity, but diff is " + clazz + " and " + val.getClass());
                }
            }
        }
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            return update(null, info, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return update(conn, info, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int update(final AsyncHandler<Integer, T[]> handler, final T... values) {
        int rs = update(values);
        if (handler != null) handler.completed(rs, values);
        return rs;
    }

    private <T> int update(final Connection conn, final EntityInfo<T> info, T... values) {
        try {
            Class clazz = info.getType();
            int c = -1;
            if (!info.isVirtualEntity()) {
                final String updateSQL = info.getUpdateSQL(values[0]);
                final Attribute<T, Serializable> primary = info.getPrimary();
                conn.setReadOnly(false);
                final PreparedStatement prestmt = conn.prepareStatement(updateSQL);
                Attribute<T, Serializable>[] attrs = info.updateAttributes;
                final boolean debugfinest = debug.get() && info.isLoggable(Level.FINEST);
                char[] sqlchars = debugfinest ? updateSQL.toCharArray() : null;
                for (final T value : values) {
                    int k = 0;
                    for (Attribute<T, Serializable> attr : attrs) {
                        Serializable val = attr.get(value);
                        if (val instanceof byte[]) {
                            Blob blob = conn.createBlob();
                            blob.setBytes(1, (byte[]) val);
                            prestmt.setObject(++k, blob);
                        } else {
                            prestmt.setObject(++k, val);
                        }
                    }
                    prestmt.setObject(++k, primary.get(value));
                    prestmt.addBatch();//------------------------------------------------------------
                    if (debugfinest) {  //打印调试信息
                        //-----------------------------
                        int i = 0;
                        StringBuilder sb = new StringBuilder(128);
                        for (char ch : sqlchars) {
                            if (ch == '?') {
                                Object obj = i == attrs.length ? primary.get(value) : attrs[i++].get(value);
                                if (obj != null && obj.getClass().isArray()) {
                                    sb.append("'[length=").append(java.lang.reflect.Array.getLength(obj)).append("]'");
                                } else {
                                    sb.append(FilterNode.formatToString(obj));
                                }
                            } else {
                                sb.append(ch);
                            }
                        }
                        logger.finest(info.getType().getSimpleName() + " update sql=" + sb.toString().replaceAll("(\r|\n)", "\\n"));
                    } //打印结束
                }
                int[] pc = prestmt.executeBatch();
                c = 0;
                for (int p : pc) {
                    if (p >= 0) c += p;
                }
                prestmt.close();
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            int c2 = 0;
            for (final T value : values) {
                c2 += cache.update(value);
            }
            if (cacheListener != null) cacheListener.updateCache(clazz, values);
            return c >= 0 ? c : c2;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        if (info.isVirtualEntity()) {
            return updateColumn(null, info, id, column, value);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, id, column, value);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        int rs = updateColumn(clazz, id, column, value);
        if (handler != null) handler.completed(rs, id);
        return rs;
    }

    private <T> int updateColumn(Connection conn, final EntityInfo<T> info, Serializable id, String column, final Serializable value) {
        try {
            int c = -1;
            if (!info.isVirtualEntity()) {
                if (value instanceof byte[]) {
                    String sql = "UPDATE " + info.getTable(id) + " SET " + info.getSQLColumn(null, column) + " = ? WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                    if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                    conn.setReadOnly(false);
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    Blob blob = conn.createBlob();
                    blob.setBytes(1, (byte[]) value);
                    stmt.setBlob(1, blob);
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                } else {
                    String sql = "UPDATE " + info.getTable(id) + " SET " + info.getSQLColumn(null, column) + " = "
                        + info.formatToString(value) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                    if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                    conn.setReadOnly(false);
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T rs = cache.update(id, info.getAttribute(column), value);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return c >= 0 ? c : (rs == null ? 0 : 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
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
        if (info.isVirtualEntity()) {
            return updateColumn(null, info, column, value, node);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, column, value, node);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final String column, final Serializable value, final FilterNode node) {
        int rs = updateColumn(clazz, column, value, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T> int updateColumn(Connection conn, final EntityInfo<T> info, String column, final Serializable value, FilterNode node) {
        try {
            int c = -1;
            if (!info.isVirtualEntity()) {
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
                if (value instanceof byte[]) {
                    String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                        + " SET " + info.getSQLColumn("a", column) + " = ?"
                        + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
                    if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                    conn.setReadOnly(false);
                    Blob blob = conn.createBlob();
                    blob.setBytes(1, (byte[]) value);
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setBlob(1, blob);
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                } else {
                    String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                        + " SET " + info.getSQLColumn("a", column) + " = " + info.formatToString(value)
                        + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
                    if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                    conn.setReadOnly(false);
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T[] rs = cache.update(info.getAttribute(column), value, node);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return c >= 0 ? c : (rs == null ? 0 : rs.length);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
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
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return updateColumn(null, info, id, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, id, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, final Serializable id, final ColumnValue... values) {
        int rs = updateColumn(clazz, id, values);
        if (handler != null) handler.completed(rs, id);
        return rs;
    }

    private <T> int updateColumn(final Connection conn, final EntityInfo<T> info, final Serializable id, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        try {
            StringBuilder setsql = new StringBuilder();
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final List<ColumnValue> cols = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            List<byte[]> blobs = null;
            for (ColumnValue col : values) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
                if (attr == null) continue;
                attrs.add(attr);
                cols.add(col);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    String c = info.getSQLColumn(null, col.getColumn());
                    if (col.getValue() instanceof byte[]) {
                        if (blobs == null) blobs = new ArrayList<>();
                        blobs.add((byte[]) col.getValue());
                        setsql.append(c).append(" = ?");
                    } else {
                        setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
                    }
                }
            }
            int c = -1;
            if (!virtual) {
                String sql = "UPDATE " + info.getTable(id) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + ": " + sql);
                conn.setReadOnly(false);
                if (blobs != null) {
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    int idx = 0;
                    for (byte[] bs : blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, bs);
                        stmt.setBlob(++idx, blob);
                    }
                    c = stmt.executeUpdate();
                    stmt.close();
                } else {
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T rs = cache.updateColumn(id, attrs, cols);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return c >= 0 ? c : (rs == null ? 0 : 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return updateColumn(null, info, node, null, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, node, null, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        int rs = updateColumn(clazz, node, values);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    /**
     * 根据主键值更新对象的多个column对应的值， 必须是Entity Class
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param node    过滤条件
     * @param flipper 翻页对象
     * @param values  字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            return updateColumn(null, info, node, flipper, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, node, flipper, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        int rs = updateColumn(clazz, node, flipper, values);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T> int updateColumn(final Connection conn, final EntityInfo<T> info, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        try {
            StringBuilder setsql = new StringBuilder();
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final List<ColumnValue> cols = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            List<byte[]> blobs = null;
            for (ColumnValue col : values) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
                if (attr == null) continue;
                attrs.add(attr);
                cols.add(col);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    String c = info.getSQLColumn("a", col.getColumn());
                    if (col.getValue() instanceof byte[]) {
                        if (blobs == null) blobs = new ArrayList<>();
                        blobs.add((byte[]) col.getValue());
                        setsql.append(c).append(" = ?");
                    } else {
                        setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
                    }
                }
            }
            int c = -1;
            if (!virtual) {
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
                //注：LIMIT 仅支持MySQL 且在多表关联式会异常， 该BUG尚未解决
                sql += info.createSQLOrderby(flipper) + ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()));
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                conn.setReadOnly(false);
                if (blobs != null) {
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    int idx = 0;
                    for (byte[] bs : blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, bs);
                        stmt.setBlob(++idx, blob);
                    }
                    c = stmt.executeUpdate();
                    stmt.close();
                } else {
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T[] rs = cache.updateColumn(node, flipper, attrs, cols);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return c >= 0 ? c : (rs == null ? 0 : rs.length);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> int updateColumn(final T bean, final String... columns) {
        return updateColumn(bean, SelectColumn.createIncludes(columns));
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, T> handler, final T bean, final String... columns) {
        int rs = updateColumn(bean, columns);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> int updateColumn(final T bean, final SelectColumn selects) {
        final EntityInfo<T> info = loadEntityInfo((Class<T>) bean.getClass());
        if (info.isVirtualEntity()) {
            return updateColumns(null, info, bean, selects);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumns(conn, info, bean, selects);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, T> handler, final T bean, final SelectColumn selects) {
        int rs = updateColumn(bean, selects);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    private <T> int updateColumns(final Connection conn, final EntityInfo<T> info, final T bean, final SelectColumn selects) {
        if (bean == null || selects == null) return -1;
        try {
            final Class<T> clazz = (Class<T>) bean.getClass();
            StringBuilder setsql = new StringBuilder();
            final Serializable id = info.getPrimary().get(bean);
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            List<byte[]> blobs = null;
            final boolean virtual = info.isVirtualEntity();
            for (Attribute<T, Serializable> attr : info.updateAttributes) {
                if (!selects.test(attr.field())) continue;
                attrs.add(attr);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    setsql.append(info.getSQLColumn(null, attr.field()));
                    Serializable val = attr.get(bean);
                    if (val instanceof byte[]) {
                        if (blobs == null) blobs = new ArrayList<>();
                        blobs.add((byte[]) val);
                        setsql.append(" = ?");
                    } else {
                        setsql.append(" = ").append(info.formatToString(val));
                    }
                }
            }
            int c = -1;
            if (!virtual) {
                String sql = "UPDATE " + info.getTable(id) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(bean.getClass().getSimpleName() + ": " + sql);
                conn.setReadOnly(false);
                if (blobs != null) {
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    int idx = 0;
                    for (byte[] bs : blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, bs);
                        stmt.setBlob(++idx, blob);
                    }
                    c = stmt.executeUpdate();
                    stmt.close();
                } else {
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T rs = cache.update(bean, attrs);
            if (cacheListener != null) cacheListener.updateCache(clazz, rs);
            return c >= 0 ? c : (rs == null ? 0 : 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> int updateColumn(final T bean, final FilterNode node, final String... columns) {
        return updateColumn(bean, node, SelectColumn.createIncludes(columns));
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, final FilterNode node, final String... columns) {
        int rs = updateColumn(bean, node, columns);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> int updateColumn(final T bean, final FilterNode node, final SelectColumn selects) {
        final EntityInfo<T> info = loadEntityInfo((Class<T>) bean.getClass());
        if (info.isVirtualEntity()) {
            return updateColumns(null, info, bean, node, selects);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumns(conn, info, bean, node, selects);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, final FilterNode node, final SelectColumn selects) {
        int rs = updateColumn(bean, node, selects);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T> int updateColumns(final Connection conn, final EntityInfo<T> info, final T bean, final FilterNode node, final SelectColumn selects) {
        if (bean == null || node == null || selects == null) return -1;
        try {
            final Class<T> clazz = (Class<T>) bean.getClass();
            StringBuilder setsql = new StringBuilder();
            final Serializable id = info.getPrimary().get(bean);
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            List<byte[]> blobs = null;
            final boolean virtual = info.isVirtualEntity();
            for (Attribute<T, Serializable> attr : info.updateAttributes) {
                if (!selects.test(attr.field())) continue;
                attrs.add(attr);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    setsql.append(info.getSQLColumn("a", attr.field()));
                    Serializable val = attr.get(bean);
                    if (val instanceof byte[]) {
                        if (blobs == null) blobs = new ArrayList<>();
                        blobs.add((byte[]) val);
                        setsql.append(" = ?");
                    } else {
                        setsql.append(" = ").append(info.formatToString(val));
                    }
                }
            }
            int c = -1;
            if (!virtual) {
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
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                conn.setReadOnly(false);
                if (blobs != null) {
                    final PreparedStatement stmt = conn.prepareStatement(sql);
                    int idx = 0;
                    for (byte[] bs : blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, bs);
                        stmt.setBlob(++idx, blob);
                    }
                    c = stmt.executeUpdate();
                    stmt.close();
                } else {
                    final Statement stmt = conn.createStatement();
                    c = stmt.executeUpdate(sql);
                    stmt.close();
                }
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T[] rs = cache.update(bean, attrs, node);
            if (cacheListener != null) cacheListener.updateCache(clazz, rs);
            return c >= 0 ? c : (rs == null ? 0 : rs.length);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
            Sheet<T> sheet = querySheet(false, true, clazz, null, FLIPPER_ONE, FilterNode.create(column, id));
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) c += cache.update(value);
        }
        return c;
    }

    //-----------------------getNumberResult-----------------------------
    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResult(entityClass, func, null, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final String column) {
        Number rs = getNumberResult(entityClass, func, column);
        if (handler != null) handler.completed(rs, column);
        return rs;
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, null, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <B extends FilterBean> Number getNumberResult(final AsyncHandler<Number, B> handler, final Class entityClass, final FilterFunc func, final String column, final B bean) {
        Number rs = getNumberResult(entityClass, func, column, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResult(entityClass, func, null, column, node);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        Number rs = getNumberResult(entityClass, func, column, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResult(entityClass, func, defVal, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        Number rs = getNumberResult(entityClass, func, defVal, column);
        if (handler != null) handler.completed(rs, column);
        return rs;
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterBean bean) {
        return getNumberResult(handler, entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, (FilterNode) null, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, FilterFuncColumn[]> handler, final Class entityClass, final FilterFuncColumn... columns) {
        Map<String, N> rs = getNumberMap(entityClass, columns);
        if (handler != null) handler.completed(rs, columns);
        return rs;
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    @Override
    public <N extends Number, B extends FilterBean> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, B> handler, final Class entityClass, final B bean, final FilterFuncColumn... columns) {
        Map<String, N> rs = getNumberMap(entityClass, bean, columns);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        if (columns == null || columns.length == 0) return new HashMap<>();
        final EntityInfo info = loadEntityInfo(entityClass);
        final Connection conn = createReadSQLConnection();
        final Map map = new HashMap<>();
        try {
            final EntityCache cache = info.getCache();
            if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
                if (node == null || node.isCacheUseable(this)) {
                    for (FilterFuncColumn ffc : columns) {
                        for (String col : ffc.cols()) {
                            map.put(ffc.col(col), cache.getNumberResult(ffc.func, ffc.defvalue, col, node));
                        }
                    }
                    return map;
                }
            }
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            StringBuilder sb = new StringBuilder();
            for (FilterFuncColumn ffc : columns) {
                for (String col : ffc.cols()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(ffc.func.getColumn((col == null || col.isEmpty() ? "*" : ("a." + col))));
                }
            }
            final String sql = "SELECT " + sb + " FROM " + info.getTable(node) + " a"
                + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement prestmt = conn.prepareStatement(sql);

            ResultSet set = prestmt.executeQuery();
            if (set.next()) {
                int index = 0;
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        Object o = set.getObject(++index);
                        Number rs = ffc.defvalue;
                        if (o != null) rs = (Number) o;
                        map.put(ffc.col(col), rs);
                    }
                }
            }
            set.close();
            prestmt.close();
            return map;
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) {
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        map.put(ffc.col(col), ffc.defvalue);
                    }
                }
                return map;
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, FilterNode> handler, final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        Map<String, N> rs = getNumberMap(entityClass, node, columns);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final Connection conn = createReadSQLConnection();
        try {
            final EntityCache cache = info.getCache();
            if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
                if (node == null || node.isCacheUseable(this)) {
                    return cache.getNumberResult(func, defVal, column, node);
                }
            }
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : ("a." + column))) + " FROM " + info.getTable(node) + " a"
                + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement prestmt = conn.prepareStatement(sql);
            Number rs = defVal;
            ResultSet set = prestmt.executeQuery();
            if (set.next()) {
                Object o = set.getObject(1);
                if (o != null) rs = (Number) o;
            }
            set.close();
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) return defVal;
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        Number rs = getNumberResult(entityClass, func, defVal, column, node);
        if (handler != null) handler.completed(rs, column);
        return rs;
    }

    //-----------------------queryColumnMap-----------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        Map<K, N> rs = queryColumnMap(entityClass, keyColumn, func, funcColumn);
        if (handler != null) handler.completed(rs, keyColumn);
        return rs;
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        return queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final Connection conn = createReadSQLConnection();
        try {
            final EntityCache cache = info.getCache();
            if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
                if (node == null || node.isCacheUseable(this)) {
                    return cache.queryColumnMap(keyColumn, func, funcColumn, node);
                }
            }
            final String sqlkey = info.getSQLColumn(null, keyColumn);
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a." + sqlkey + ", " + func.getColumn((funcColumn == null || funcColumn.isEmpty() ? "*" : ("a." + funcColumn)))
                + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + sqlkey;
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement prestmt = conn.prepareStatement(sql);
            Map<K, N> rs = new LinkedHashMap<>();
            ResultSet set = prestmt.executeQuery();
            ResultSetMetaData rsd = set.getMetaData();
            boolean smallint = rsd.getColumnType(1) == Types.SMALLINT;
            while (set.next()) {
                rs.put((K) (smallint ? set.getShort(1) : set.getObject(1)), (N) set.getObject(2));
            }
            set.close();
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) return new LinkedHashMap<>();
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node) {
        Map<K, N> rs = queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
        if (handler != null) handler.completed(rs, keyColumn);
        return rs;
    }

    //-----------------------find----------------------------
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
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, final Serializable pk) {
        T rs = find(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
        return rs;
    }

    @Override
    public <T> T find(Class<T> clazz, final SelectColumn selects, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return rs;
        }

        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final String sql = "SELECT * FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            T rs = set.next() ? info.getValue(sels, set) : null;
            set.close();
            ps.close();
            return rs;
        } catch (SQLException sex) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + sex.getSQLState() + ';')) return null;
            throw new RuntimeException(sex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, SelectColumn selects, final Serializable pk) {
        T rs = find(clazz, selects, pk);
        if (handler != null) handler.completed(rs, pk);
        return rs;
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return find(clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, final String column, final Serializable key) {
        T rs = find(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
        return rs;
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterBean bean) {
        return find(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> T find(final AsyncHandler<T, B> handler, final Class<T> clazz, final B bean) {
        T rs = find(clazz, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterNode node) {
        return find(clazz, null, node);
    }

    @Override
    public <T> T find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        T rs = find(clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> T find(final AsyncHandler<T, B> handler, final Class<T> clazz, final SelectColumn selects, final B bean) {
        T rs = find(clazz, selects, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) return cache.find(selects, node);

        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a.* FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            T rs = set.next() ? info.getValue(sels, set) : null;
            set.close();
            ps.close();
            return rs;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return null;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> T find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        T rs = find(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk) {
        return findColumn(clazz, column, null, pk);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, final Serializable pk) {
        Serializable rs = findColumn(clazz, column, pk);
        if (handler != null) handler.completed(rs, pk);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean) {
        return findColumn(clazz, column, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> Serializable findColumn(final AsyncHandler<Serializable, B> handler, final Class<T> clazz, final String column, final B bean) {
        Serializable rs = findColumn(clazz, column, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node) {
        return findColumn(clazz, column, null, node);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final FilterNode node) {
        Serializable rs = findColumn(clazz, column, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return val;
        }

        final Connection conn = createReadSQLConnection();
        try {
            final Attribute<T, Serializable> attr = info.getAttribute(column);
            final String sql = "SELECT " + info.getSQLColumn(null, column) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            Serializable val = defValue;
            if (set.next()) {
                if (attr.type() == byte[].class) {
                    Blob blob = set.getBlob(1);
                    if (blob != null) val = blob.getBytes(1, (int) blob.length());
                } else {
                    val = (Serializable) set.getObject(1);
                }
            }
            set.close();
            ps.close();
            return val == null ? defValue : val;
        } catch (SQLException sex) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + sex.getSQLState() + ';')) return defValue;
            throw new RuntimeException(sex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        Serializable rs = findColumn(clazz, column, defValue, pk);
        if (handler != null) handler.completed(rs, pk);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return findColumn(clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> Serializable findColumn(final AsyncHandler<Serializable, B> handler, final Class<T> clazz, final String column, final Serializable defValue, final B bean) {
        Serializable rs = findColumn(clazz, column, defValue, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) return cache.findColumn(column, defValue, node);

        final Connection conn = createReadSQLConnection();
        try {
            final Attribute<T, Serializable> attr = info.getAttribute(column);
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT " + info.getSQLColumn("a", column) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            Serializable val = defValue;
            if (set.next()) {
                if (attr.type() == byte[].class) {
                    Blob blob = set.getBlob(1);
                    if (blob != null) val = blob.getBytes(1, (int) blob.length());
                } else {
                    val = (Serializable) set.getObject(1);
                }
            }
            set.close();
            ps.close();
            return val == null ? defValue : val;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return defValue;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        Serializable rs = findColumn(clazz, column, defValue, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return rs;
        }

        final Connection conn = createReadSQLConnection();
        final boolean log = debug.get() && info.isLoggable(Level.FINEST);
        String logstr = null;
        try {
            final String sql = "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
            if (log) logstr = clazz.getSimpleName() + " exists sql=" + sql;
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (log) logstr = clazz.getSimpleName() + " exists (" + rs + ") sql=" + sql;
            return rs;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return false;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (logstr != null) logger.finest(logstr);
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> boolean exists(final AsyncHandler<Boolean, Serializable> handler, final Class<T> clazz, final Serializable pk) {
        boolean rs = exists(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
        return rs;
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> boolean exists(final AsyncHandler<Boolean, B> handler, final Class<T> clazz, final B bean) {
        boolean rs = exists(clazz, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) return cache.exists(node);

        final Connection conn = createReadSQLConnection();
        final boolean log = debug.get() && info.isLoggable(Level.FINEST);
        String logstr = null;
        try {
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (log) logstr = clazz.getSimpleName() + " exists sql=" + sql;
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (log) logstr = clazz.getSimpleName() + " exists (" + rs + ") sql=" + sql;
            return rs;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return false;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (logstr != null) logger.finest(logstr);
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> boolean exists(final AsyncHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        boolean rs = exists(clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return queryColumnSet(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, String> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, column);
        return rs;
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, B> handler, final String selectedColumn, final Class<T> clazz, final B bean) {
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, node));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        return queryColumnList(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        List<V> rs = queryColumnList(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
        return rs;
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> List<V> queryColumnList(final AsyncHandler<List<V>, B> handler, String selectedColumn, Class<T> clazz, B bean) {
        List<V> rs = queryColumnList(selectedColumn, clazz, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return (List<V>) queryColumnSheet(false, selectedColumn, clazz, null, node).list(true);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        List<V> rs = queryColumnList(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> List<V> queryColumnList(final AsyncHandler<List<V>, B> handler, String selectedColumn, Class<T> clazz, Flipper flipper, B bean) {
        List<V> rs = queryColumnList(selectedColumn, clazz, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return (List<V>) queryColumnSheet(false, selectedColumn, clazz, flipper, node).list(true);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, Flipper flipper, final FilterNode node) {
        List<V> rs = queryColumnList(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
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
    public <T, V extends Serializable, B extends FilterBean> Sheet<V> queryColumnSheet(final AsyncHandler<Sheet<V>, B> handler, String selectedColumn, Class<T> clazz, Flipper flipper, B bean) {
        Sheet<V> rs = queryColumnSheet(selectedColumn, clazz, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryColumnSheet(true, selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final AsyncHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<V> rs = queryColumnSheet(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T, V extends Serializable> Sheet<V> queryColumnSheet(final boolean needtotal, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> sheet = querySheet(true, needtotal, clazz, SelectColumn.createIncludes(selectedColumn), flipper, node);
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
        return queryList(clazz, FilterNode.create(column, key));
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, final Serializable key) {
        List<T> rs = queryList(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
        return rs;
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
        return queryList(clazz, (SelectColumn) null, bean);
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final B bean) {
        List<T> rs = queryList(clazz, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        List<T> rs = queryList(clazz, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
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
        return queryList(clazz, selects, (Flipper) null, bean);
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final SelectColumn selects, final B bean) {
        List<T> rs = queryList(clazz, selects, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryList(clazz, selects, (Flipper) null, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return queryList(clazz, flipper, FilterNode.create(column, key));
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        List<T> rs = queryList(clazz, flipper, column, key);
        if (handler != null) handler.completed(rs, key);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, null, flipper, bean);
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final Flipper flipper, final B bean) {
        List<T> rs = queryList(clazz, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryList(clazz, null, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        List<T> rs = queryList(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(true, false, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean)).list(true);
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final B bean) {
        List<T> rs = queryList(clazz, selects, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, clazz, selects, flipper, node).list(true);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
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
        return querySheet(clazz, null, flipper, bean);
    }

    @Override
    public <T, B extends FilterBean> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, B> handler, final Class<T> clazz, final Flipper flipper, final B bean) {
        Sheet<T> rs = querySheet(clazz, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheet(clazz, null, flipper, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> rs = querySheet(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
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
        return querySheet(true, true, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, B> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final B bean) {
        Sheet<T> rs = querySheet(clazz, selects, flipper, bean);
        if (handler != null) handler.completed(rs, bean);
        return rs;
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, true, clazz, selects, flipper, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        Sheet<T> rs = querySheet(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
        return rs;
    }

    private <T> Sheet<T> querySheet(final boolean readcache, final boolean needtotal, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readcache && cache != null && cache.isFullLoaded()) {
            if (node == null || node.isCacheUseable(this)) {
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " cache query predicate = " + (node == null ? null : node.createPredicate(cache)));
                return cache.querySheet(needtotal, selects, flipper, node);
            }
        }
        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a.* FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + info.createSQLOrderby(flipper);
            if (debug.get() && info.isLoggable(Level.FINEST)) {
                logger.finest(clazz.getSimpleName() + " query sql=" + sql + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getOffset() + "," + flipper.getLimit())));
            }
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            if (flipper != null && flipper.getLimit() > 0) ps.setFetchSize(flipper.getLimit());
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.getOffset() > 0) set.absolute(flipper.getOffset());
            final int limit = flipper == null || flipper.getLimit() < 1 ? Integer.MAX_VALUE : flipper.getLimit();
            int i = 0;
            while (set.next()) {
                i++;
                list.add(info.getValue(sels, set));
                if (limit <= i) break;
            }
            long total = list.size();
            if (needtotal && flipper != null) {
                set.last();
                total = set.getRow();
            }
            set.close();
            ps.close();
            return new Sheet<>(total, list);
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return new Sheet<>();
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private static StringBuilder multisplit(char ch1, char ch2, String split, StringBuilder sb, String str, int from) {
        if (str == null) return sb;
        int pos1 = str.indexOf(ch1, from);
        if (pos1 < 0) return sb;
        int pos2 = str.indexOf(ch2, from);
        if (pos2 < 0) return sb;
        if (sb.length() > 0) sb.append(split);
        sb.append(str.substring(pos1 + 1, pos2));
        return multisplit(ch1, ch2, split, sb, str, pos2 + 1);
    }

    @Override
    public final int[] directExecute(String... sqls) {
        Connection conn = createWriteSQLConnection();
        try {
            return directExecute(conn, sqls);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private int[] directExecute(final Connection conn, String... sqls) {
        if (sqls.length == 0) return new int[0];
        try {
            conn.setReadOnly(false);
            final Statement stmt = conn.createStatement();
            final int[] rs = new int[sqls.length];
            int i = -1;
            for (String sql : sqls) {
                rs[++i] = stmt.execute(sql) ? 1 : 0;
            }
            stmt.close();
            return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void directQuery(String sql, Consumer<ResultSet> consumer) {
        final Connection conn = createReadSQLConnection();
        try {
            if (debug.get()) logger.finest("direct query sql=" + sql);
            conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            consumer.accept(set);
            set.close();
            ps.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

}
