/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.service.Local;
import org.redkale.util.*;

/**
 * DataSource的JDBC实现类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public class DataJdbcSource extends DataSqlSource<Connection> {

    public DataJdbcSource(String unitName, URL persistxml, Properties readprop, Properties writeprop) {
        super(unitName, persistxml, readprop, writeprop);
    }

    @Override
    protected final String prepareParamSign(int index) {
        return "?";
    }

    @Override
    protected final boolean isAsync() {
        return false;
    }

    @Override
    protected PoolSource<Connection> createPoolSource(DataSource source, String rwtype, ArrayBlockingQueue queue, Semaphore semaphore, Properties prop) {
        return new PoolJdbcSource(this.name, this.persistxml, rwtype, queue, semaphore, prop, this.logger);
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            int c = 0;
            conn = writePool.poll();
            final String sql = info.getInsertPrepareSQL(entitys[0]);
            final Class primaryType = info.getPrimary().type();
            final Attribute primary = info.getPrimary();
            Attribute<T, Serializable>[] attrs = info.insertAttributes;
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            PreparedStatement prestmt = createInsertPreparedStatement(conn, sql, info, entitys);
            try {
                int[] cs = prestmt.executeBatch();
                int c1 = 0;
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
            } catch (SQLException se) {
                if (info.tableStrategy == null || !info.isTableNotExist(se)) throw se;
                synchronized (info.tables) {
                    final String oldTable = info.table;
                    final String catalog = conn.getCatalog();
                    final String newTable = info.getTable(entitys[0]);
                    final String tablekey = newTable.indexOf('.') > 0 ? newTable : (catalog + '.' + newTable);
                    if (!info.tables.contains(tablekey)) {
                        try {
                            Statement st = conn.createStatement();
                            st.execute(info.tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", oldTable));
                            st.close();
                            info.tables.add(tablekey);
                        } catch (SQLException sqle) { //多进程并发时可能会出现重复建表
                            if (newTable.indexOf('.') > 0 && info.isTableNotExist(se)) {
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
                                    info.tables.add(tablekey);
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
                prestmt = createInsertPreparedStatement(conn, sql, info, entitys);
                int[] cs = prestmt.executeBatch();
                int c1 = 0;
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
            }
            if (info.autoGenerated) { //由数据库自动生成主键值
                ResultSet set = prestmt.getGeneratedKeys();
                int i = -1;
                while (set.next()) {
                    if (primaryType == int.class) {
                        primary.set(entitys[++i], set.getInt(1));
                    } else if (primaryType == long.class) {
                        primary.set(entitys[++i], set.getLong(1));
                    } else {
                        primary.set(entitys[++i], set.getObject(1));
                    }
                }
                set.close();
            }
            prestmt.close();
            //------------------------------------------------------------
            if (info.isLoggable(logger, Level.FINEST)) {  //打印调试信息
                char[] sqlchars = sql.toCharArray();
                for (final T value : entitys) {
                    //-----------------------------
                    StringBuilder sb = new StringBuilder(128);
                    int i = 0;
                    for (char ch : sqlchars) {
                        if (ch == '?') {
                            Object obj = info.getSQLValue(attrs[i++], value);
                            if (obj != null && obj.getClass().isArray()) {
                                sb.append("'[length=").append(java.lang.reflect.Array.getLength(obj)).append("]'");
                            } else {
                                sb.append(info.formatSQLValue(obj, sqlFormatter));
                            }
                        } else {
                            sb.append(ch);
                        }
                    }
                    String debugsql = sb.toString();
                    if (info.isLoggable(logger, Level.FINEST, debugsql)) logger.finest(info.getType().getSimpleName() + " insert sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                }
            } //打印结束
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    protected <T> PreparedStatement createInsertPreparedStatement(final Connection conn, final String sql,
        final EntityInfo<T> info, T... entitys) throws SQLException {
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        final PreparedStatement prestmt = info.autoGenerated ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);

        for (final T value : entitys) {
            if (info.autouuid) info.createPrimaryValue(value);
            batchStatementParameters(conn, prestmt, info, attrs, value);
            prestmt.addBatch();
        }
        return prestmt;
    }

    protected <T> int batchStatementParameters(Connection conn, PreparedStatement prestmt, EntityInfo<T> info, Attribute<T, Serializable>[] attrs, T entity) throws SQLException {
        int i = 0;
        for (Attribute<T, Serializable> attr : attrs) {
            Object val = info.getSQLValue(attr, entity);
            if (val instanceof byte[]) {
                Blob blob = conn.createBlob();
                blob.setBytes(1, (byte[]) val);
                prestmt.setObject(++i, blob);
            } else if (val instanceof Boolean) {
                prestmt.setObject(++i, ((Boolean) val) ? (byte) 1 : (byte) 0);
            } else if (val instanceof AtomicInteger) {
                prestmt.setObject(++i, ((AtomicInteger) val).get());
            } else if (val instanceof AtomicLong) {
                prestmt.setObject(++i, ((AtomicLong) val).get());
            } else if (val != null && !(val instanceof Number) && !(val instanceof CharSequence) && !(entity instanceof java.util.Date)
                && !val.getClass().getName().startsWith("java.sql.") && !val.getClass().getName().startsWith("java.time.")) {
                prestmt.setObject(++i, info.jsonConvert.convertTo(attr.genericType(), val));
            } else {
                prestmt.setObject(++i, val);
            }
        }
        return i;
    }

    @Override
    protected <T> CompletableFuture<Integer> deleteDB(EntityInfo<T> info, Flipper flipper, String sql) {
        Connection conn = null;
        try {
            conn = writePool.poll();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            sql += ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()));
            if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDB(EntityInfo<T> info, String sql) {
        Connection conn = null;
        try {
            conn = writePool.poll();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (info.isTableNotExist(e)) return CompletableFuture.completedFuture(-1);
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDB(EntityInfo<T> info, String sql) {
        Connection conn = null;
        try {
            conn = writePool.poll();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (info.isTableNotExist(e)) return CompletableFuture.completedFuture(-1);
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            conn = writePool.poll();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final String updateSQL = info.getUpdatePrepareSQL(entitys[0]);
            final PreparedStatement prestmt = conn.prepareStatement(updateSQL);
            Attribute<T, Serializable>[] attrs = info.updateAttributes;
            final boolean debugfinest = info.isLoggable(logger, Level.FINEST);
            char[] sqlchars = debugfinest ? updateSQL.toCharArray() : null;
            final Attribute<T, Serializable> primary = info.getPrimary();
            for (final T value : entitys) {
                int k = batchStatementParameters(conn, prestmt, info, attrs, value);
                prestmt.setObject(++k, primary.get(value));
                prestmt.addBatch();//------------------------------------------------------------
                if (debugfinest) {  //打印调试信息
                    //-----------------------------
                    int i = 0;
                    StringBuilder sb = new StringBuilder(128);
                    for (char ch : sqlchars) {
                        if (ch == '?') {
                            Object obj = i == attrs.length ? info.getSQLValue(primary, value) : info.getSQLValue(attrs[i++], value);
                            if (obj != null && obj.getClass().isArray()) {
                                sb.append("'[length=").append(java.lang.reflect.Array.getLength(obj)).append("]'");
                            } else {
                                sb.append(info.formatSQLValue(obj, sqlFormatter));
                            }
                        } else {
                            sb.append(ch);
                        }
                    }
                    String debugsql = sb.toString();
                    if (info.isLoggable(logger, Level.FINEST, debugsql)) logger.finest(info.getType().getSimpleName() + " updates sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                } //打印结束
            }
            int[] pc = prestmt.executeBatch();
            int c = 0;
            for (int p : pc) {
                if (p >= 0) c += p;
            }
            prestmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateDB(EntityInfo<T> info, Flipper flipper, String sql, boolean prepared, Object... params) {
        Connection conn = null;
        try {
            conn = writePool.poll();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            if (prepared) {
                final PreparedStatement prestmt = conn.prepareStatement(sql);
                int index = 0;
                for (Object param : params) {
                    Blob blob = conn.createBlob();
                    blob.setBytes(1, (byte[]) param);
                    prestmt.setBlob(++index, blob);
                }
                int c = prestmt.executeUpdate();
                prestmt.close();
                return CompletableFuture.completedFuture(c);
            } else {
                if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                int c = stmt.executeUpdate(sql);
                stmt.close();
                return CompletableFuture.completedFuture(c);
            }
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(EntityInfo<T> info, String sql, FilterFuncColumn... columns) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
            ResultSet set = stmt.executeQuery(sql);
            final Map map = new HashMap<>();
            if (set.next()) {
                int index = 0;
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        Object o = set.getObject(++index);
                        Number rs = ffc.getDefvalue();
                        if (o != null) rs = (Number) o;
                        map.put(ffc.col(col), rs);
                    }
                }
            }
            set.close();
            stmt.close();
            return CompletableFuture.completedFuture(map);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDB(EntityInfo<T> info, String sql, Number defVal, String column) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
            Number rs = defVal;
            ResultSet set = stmt.executeQuery(sql);
            if (set.next()) {
                Object o = set.getObject(1);
                if (o != null) rs = (Number) o;
            }
            set.close();
            stmt.close();
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(EntityInfo<T> info, String sql, String keyColumn) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
            Map<K, N> rs = new LinkedHashMap<>();
            ResultSet set = stmt.executeQuery(sql);
            ResultSetMetaData rsd = set.getMetaData();
            boolean smallint = rsd == null ? false : rsd.getColumnType(1) == Types.SMALLINT;
            while (set.next()) {
                rs.put((K) (smallint ? set.getShort(1) : set.getObject(1)), (N) set.getObject(2));
            }
            set.close();
            stmt.close();
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<T> findDB(EntityInfo<T> info, String sql, boolean onlypk, SelectColumn selects) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            T rs = set.next() ? info.getEntityValue(selects, set) : null;
            set.close();
            ps.close();
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.isTableNotExist(e)) return CompletableFuture.completedFuture(null);
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDB(EntityInfo<T> info, String sql, boolean onlypk, String column, Serializable defValue) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final Attribute<T, Serializable> attr = info.getAttribute(column);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final ResultSet set = ps.executeQuery();
            Serializable val = defValue;
            if (set.next()) {
                val = info.getFieldValue(attr, set, 1);
            }
            set.close();
            ps.close();
            return CompletableFuture.completedFuture(val == null ? defValue : val);
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.isTableNotExist(e)) return CompletableFuture.completedFuture(defValue);
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDB(EntityInfo<T> info, String sql, boolean onlypk) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists (" + rs + ") sql=" + sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.isTableNotExist(e)) return CompletableFuture.completedFuture(false);
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDB(EntityInfo<T> info, final boolean readcache, boolean needtotal, SelectColumn selects, Flipper flipper, FilterNode node) {
        Connection conn = null;
        try {
            conn = readPool.poll();
            //conn.setReadOnly(true);
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String dbtype = this.readPool.getDbtype();
            if ("mysql".equals(dbtype) || "postgresql".equals(dbtype)) {
                final String listsql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                    + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + createSQLOrderby(info, flipper) + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset()));
                if (readcache && info.isLoggable(logger, Level.FINEST, listsql)) {
                    logger.finest(info.getType().getSimpleName() + " query sql=" + listsql);
                }
                PreparedStatement ps = conn.prepareStatement(listsql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet set = ps.executeQuery();
                while (set.next()) {
                    list.add(getEntityValue(info, sels, set));
                }
                set.close();
                ps.close();
                long total = list.size();
                if (needtotal) {
                    final String countsql = "SELECT COUNT(*) FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                    if (readcache && info.isLoggable(logger, Level.FINEST, countsql)) {
                        logger.finest(info.getType().getSimpleName() + " query countsql=" + countsql);
                    }
                    ps = conn.prepareStatement(countsql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                    set = ps.executeQuery();
                    if (set.next()) total = set.getLong(1);
                    set.close();
                    ps.close();
                }
                return CompletableFuture.completedFuture(new Sheet<>(total, list));
            }
            final String sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + info.createSQLOrderby(flipper);
            if (readcache && info.isLoggable(logger, Level.FINEST, sql)) {
                logger.finest(info.getType().getSimpleName() + " query sql=" + sql + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset())));
            }
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            if (flipper != null && flipper.getLimit() > 0) ps.setFetchSize(flipper.getLimit());
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.getOffset() > 0) set.absolute(flipper.getOffset());
            final int limit = flipper == null || flipper.getLimit() < 1 ? Integer.MAX_VALUE : flipper.getLimit();
            int i = 0;
            while (set.next()) {
                i++;
                list.add(info.getEntityValue(sels, set));
                if (limit <= i) break;
            }
            long total = list.size();
            if (needtotal && flipper != null) {
                set.last();
                total = set.getRow();
            }
            set.close();
            ps.close();
            return CompletableFuture.completedFuture(new Sheet<>(total, list));
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.isTableNotExist(e)) return CompletableFuture.completedFuture(new Sheet<>());
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    /**
     * 直接本地执行SQL语句进行增删改操作，远程模式不可用   <br>
     * 通常用于复杂的更新操作   <br>
     *
     * @param sql SQL语句
     *
     * @return 结果数组
     */
    @Local
    @Override
    public int directExecute(String sql) {
        return directExecute(new String[]{sql})[0];
    }

    /**
     * 直接本地执行SQL语句进行增删改操作，远程模式不可用   <br>
     * 通常用于复杂的更新操作   <br>
     *
     * @param sqls SQL语句
     *
     * @return 结果数组
     */
    @Local
    @Override
    public int[] directExecute(String... sqls) {
        if (sqls.length == 0) return new int[0];
        Connection conn = writePool.poll();
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
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    /**
     * 直接本地执行SQL语句进行查询，远程模式不可用   <br>
     * 通常用于复杂的关联查询   <br>
     *
     * @param <V>     泛型
     * @param sql     SQL语句
     * @param handler 回调函数
     *
     * @return 结果
     */
    @Local
    @Override
    public <V> V directQuery(String sql, Function<ResultSet, V> handler) {
        final Connection conn = readPool.poll();
        try {
            if (logger.isLoggable(Level.FINEST)) logger.finest("direct query sql=" + sql);
            //conn.setReadOnly(true);
            final Statement statement = conn.createStatement();
            //final PreparedStatement statement = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = statement.executeQuery(sql);// ps.executeQuery();
            V rs = handler.apply(set);
            set.close();
            statement.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }
}
