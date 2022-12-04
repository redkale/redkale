/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
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
public class DataJdbcSource extends DataSqlSource {

    protected ConnectionPool readPool;

    protected ConnectionPool writePool;

    public DataJdbcSource() {
        super();
    }

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
        this.readPool = new ConnectionPool(readConfProps);
        if (readConfProps == writeConfProps) {
            this.writePool = readPool;
        } else {
            this.writePool = new ConnectionPool(writeConfProps);
        }
    }

    @Override
    public void onChange(ResourceEvent[] events) {
        
    }

    @Override
    public void destroy(AnyValue config) {
        if (readPool != null) readPool.close();
        if (writePool != null) writePool.close();
    }

    @Local
    @Override
    public void close() throws Exception {
        super.close();
        if (readPool != null) readPool.close();
        if (writePool != null) writePool.close();
    }

    public static boolean acceptsConf(AnyValue conf) {
        try {
            AnyValue read = conf.getAnyValue("read");
            AnyValue node = read == null ? conf : read;
            final Class driverClass = DriverManager.getDriver(node.getValue(DATA_SOURCE_URL)).getClass();
            RedkaleClassLoader.putReflectionDeclaredConstructors(driverClass, driverClass.getName());
            RedkaleClassLoader.putServiceLoader(java.sql.Driver.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Local
    protected ConnectionPool readPool() {
        return readPool;
    }

    @Local
    protected ConnectionPool writePool() {
        return writePool;
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
    protected <T> CompletableFuture<Integer> insertDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            int c = 0;
            conn = writePool.pollConnection();
            final String sql = info.getInsertQuestionPrepareSQL(entitys[0]);
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
                if (!isTableNotExist(info, se.getSQLState())) throw se;
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls == null) throw se;
                    Statement st = conn.createStatement();
                    if (tablesqls.length == 1) {
                        st.execute(tablesqls[0]);
                    } else {
                        for (String tablesql : tablesqls) {
                            st.addBatch(tablesql);
                        }
                        st.executeBatch();
                    }
                    st.close();
                } else {
                    synchronized (info.disTableLock()) {
                        final String catalog = conn.getCatalog();
                        final String newTable = info.getTable(entitys[0]);
                        final String tablekey = newTable.indexOf('.') > 0 ? newTable : (catalog + '.' + newTable);
                        if (!info.containsDisTable(tablekey)) {
                            try {
                                //执行一遍复制表操作
                                Statement st = conn.createStatement();
                                st.execute(getTableCopySQL(info, newTable));
                                st.close();
                                info.addDisTable(tablekey);
                            } catch (SQLException sqle) { //多进程并发时可能会出现重复建表
                                if (isTableNotExist(info, sqle.getSQLState())) {
                                    if (newTable.indexOf('.') < 0) {
                                        String[] tablesqls = createTableSqls(info);
                                        if (tablesqls != null) {
                                            Statement st = conn.createStatement();
                                            if (tablesqls.length == 1) {
                                                st.execute(tablesqls[0]);
                                            } else {
                                                for (String tablesql : tablesqls) {
                                                    st.addBatch(tablesql);
                                                }
                                                st.executeBatch();
                                            }
                                            st.close();
                                            //再执行一遍复制表操作
                                            st = conn.createStatement();
                                            st.execute(getTableCopySQL(info, newTable));
                                            st.close();
                                            info.addDisTable(tablekey);
                                        }
                                    } else { //需要先建库
                                        Statement st;
                                        try {
                                            st = conn.createStatement();
                                            st.execute(("postgresql".equals(dbtype()) ? "CREATE SCHEMA IF NOT EXISTS " : "CREATE DATABASE IF NOT EXISTS ") + newTable.substring(0, newTable.indexOf('.')));
                                            st.close();
                                        } catch (SQLException sqle1) {
                                            logger.log(Level.SEVERE, "create database(" + newTable.substring(0, newTable.indexOf('.')) + ") error", sqle1);
                                        }
                                        try {
                                            //再执行一遍复制表操作
                                            st = conn.createStatement();
                                            st.execute(getTableCopySQL(info, newTable));
                                            st.close();
                                            info.addDisTable(tablekey);
                                        } catch (SQLException sqle2) {
                                            if (isTableNotExist(info, sqle2.getSQLState())) {
                                                String[] tablesqls = createTableSqls(info);
                                                if (tablesqls != null) {
                                                    st = conn.createStatement();
                                                    if (tablesqls.length == 1) {
                                                        st.execute(tablesqls[0]);
                                                    } else {
                                                        for (String tablesql : tablesqls) {
                                                            st.addBatch(tablesql);
                                                        }
                                                        st.executeBatch();
                                                    }
                                                    st.close();
                                                    //再执行一遍复制表操作
                                                    st = conn.createStatement();
                                                    st.execute(getTableCopySQL(info, newTable));
                                                    st.close();
                                                    info.addDisTable(tablekey);
                                                }
                                            } else {
                                                logger.log(Level.SEVERE, "create table2(" + getTableCopySQL(info, newTable) + ") error", sqle2);
                                            }
                                        }
                                    }
                                }
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
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    protected <T> PreparedStatement createInsertPreparedStatement(final Connection conn, final String sql,
        final EntityInfo<T> info, T... entitys) throws SQLException {
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        final PreparedStatement prestmt = conn.prepareStatement(sql);

        for (final T value : entitys) {
            batchStatementParameters(conn, prestmt, info, attrs, value);
            prestmt.addBatch();
        }
        return prestmt;
    }

    protected <T> int batchStatementParameters(Connection conn, PreparedStatement prestmt, EntityInfo<T> info, Attribute<T, Serializable>[] attrs, T entity) throws SQLException {
        int i = 0;
        for (Attribute<T, Serializable> attr : attrs) {
            Object val = getEntityAttrValue(info, attr, entity);
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
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            sql += ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()));
            if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                            return CompletableFuture.completedFuture(0);
                        } catch (SQLException e2) {
                            return CompletableFuture.failedFuture(e2);
                        }
                    }
                }
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDB(EntityInfo<T> info, final String table, String sql) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) return CompletableFuture.completedFuture(-1);
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDB(EntityInfo<T> info, final String table, String sql) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final Statement stmt = conn.createStatement();
            int c = stmt.executeUpdate(sql);
            stmt.close();
            if (info.getTableStrategy() != null) {
                String tablekey = table.indexOf('.') > 0 ? table : (conn.getCatalog() + '.' + table);
                info.removeDisTable(tablekey);
            }
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) return CompletableFuture.completedFuture(-1);
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateEntityDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            final String updateSQL = info.getUpdateQuestionPrepareSQL(entitys[0]);
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(0);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateColumnDB(EntityInfo<T> info, Flipper flipper, String sql, boolean prepared, Object... params) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(0);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(EntityInfo<T> info, String sql, FilterFuncColumn... columns) {
        Connection conn = null;
        final Map map = new HashMap<>();
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
            ResultSet set = stmt.executeQuery(sql);
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(map);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDB(EntityInfo<T> info, String sql, Number defVal, String column) {
        Connection conn = null;
        try {
            conn = readPool.pollConnection();
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(defVal);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(EntityInfo<T> info, String sql, String keyColumn) {
        Connection conn = null;
        Map<K, N> rs = new LinkedHashMap<>();
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(rs);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDB(EntityInfo<T> info, String sql, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        Connection conn = null;
        Map rs = new LinkedHashMap<>();
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final Statement stmt = conn.createStatement();
            ResultSet set = stmt.executeQuery(sql);
            ResultSetMetaData rsd = set.getMetaData();
            boolean[] smallints = null;
            while (set.next()) {
                int index = 0;
                Serializable[] keys = new Serializable[groupByColumns.length];
                if (smallints == null) {
                    smallints = new boolean[keys.length];
                    for (int i = 0; i < keys.length; i++) {
                        smallints[i] = rsd == null ? false : rsd.getColumnType(i + 1) == Types.SMALLINT;
                    }
                }
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = (Serializable) ((smallints[i] && index == 0) ? set.getShort(++index) : set.getObject(++index));
                }
                Number[] vals = new Number[funcNodes.length];
                for (int i = 0; i < vals.length; i++) {
                    vals[i] = (Number) set.getObject(++index);
                }
                rs.put(keys, vals);
            }
            set.close();
            stmt.close();
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(rs);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<T> findDB(EntityInfo<T> info, String sql, boolean onlypk, SelectColumn selects) {
        Connection conn = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final DataResultSet set = createDataResultSet(info, ps.executeQuery());
            T rs = set.next() ? selects == null ? info.getFullEntityValue(set) : info.getEntityValue(selects, set) : null;
            set.close();
            ps.close();
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDB(EntityInfo<T> info, String sql, boolean onlypk, String column, Serializable defValue) {
        Connection conn = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final Attribute<T, Serializable> attr = info.getAttribute(column);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final DataResultSet set = createDataResultSet(info, ps.executeQuery());
            Serializable val = defValue;
            if (set.next()) {
                val = info.getFieldValue(attr, set, 1);
            }
            set.close();
            ps.close();
            return CompletableFuture.completedFuture(val == null ? defValue : val);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDB(EntityInfo<T> info, String sql, boolean onlypk) {
        Connection conn = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists (" + rs + ") sql=" + sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(false);
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDB(EntityInfo<T> info, final boolean readcache, boolean needtotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        Connection conn = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
            final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
            if ("mysql".equals(dbtype()) || "postgresql".equals(dbtype())) {  //sql可以带limit、offset
                final String listsql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getFullQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                    + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + createSQLOrderby(info, flipper)
                    + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset()));
                if (readcache && info.isLoggable(logger, Level.FINEST, listsql)) {
                    logger.finest(info.getType().getSimpleName() + " query sql=" + listsql);
                }
                PreparedStatement ps = conn.prepareStatement(listsql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet set = ps.executeQuery();
                final DataResultSet rr = createDataResultSet(info, set);
                while (set.next()) {
                    list.add(getEntityValue(info, sels, rr));
                }
                set.close();
                ps.close();
                long total = list.size();
                if (needtotal) {
                    final String countsql = "SELECT " + (distinct ? "DISTINCT COUNT(" + info.getQueryColumns("a", selects) + ")" : "COUNT(*)") + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
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
            final String listsql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getFullQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + info.createSQLOrderby(flipper);
            if (readcache && info.isLoggable(logger, Level.FINEST, listsql)) {
                logger.finest(info.getType().getSimpleName() + " query sql=" + listsql + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset())));
            }
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(listsql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            if (flipper != null && flipper.getLimit() > 0) ps.setFetchSize(flipper.getLimit());
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.getOffset() > 0) set.absolute(flipper.getOffset());
            final int limit = flipper == null || flipper.getLimit() < 1 ? Integer.MAX_VALUE : flipper.getLimit();
            int i = 0;
            final DataResultSet rr = createDataResultSet(info, set);
            if (sels == null) {
                while (set.next()) {
                    i++;
                    list.add(info.getFullEntityValue(rr));
                    if (limit <= i) break;
                }
            } else {
                while (set.next()) {
                    i++;
                    list.add(info.getEntityValue(sels, rr));
                    if (limit <= i) break;
                }
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
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tablesqls = createTableSqls(info);
                    if (tablesqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tablesqls.length == 1) {
                                st.execute(tablesqls[0]);
                            } else {
                                for (String tablesql : tablesqls) {
                                    st.addBatch(tablesql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                }
                return CompletableFuture.completedFuture(new Sheet<>(0, new ArrayList()));
            }
            return CompletableFuture.failedFuture(e);
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
        Connection conn = writePool.pollConnection();
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
    public <V> V directQuery(String sql, Function<DataResultSet, V> handler) {
        final Connection conn = readPool.pollConnection();
        try {
            if (logger.isLoggable(Level.FINEST)) logger.finest("direct query sql=" + sql);
            //conn.setReadOnly(true);
            final Statement statement = conn.createStatement();
            //final PreparedStatement statement = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = statement.executeQuery(sql);// ps.executeQuery();
            V rs = handler.apply(createDataResultSet(null, set));
            set.close();
            statement.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (conn != null) readPool.offerConnection(conn);
        }
    }

    public static DataResultSet createDataResultSet(EntityInfo info, ResultSet set) {

        final ResultSet rr = set;

        return new DataResultSet() {

            @Override
            public <T> Serializable getObject(Attribute<T, Serializable> attr, int index, String column) {
                Class t = attr.type();
                if (t == java.util.Date.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);
                    if (val == null) return null;
                    return new java.util.Date(((java.sql.Date) val).getTime());
                } else if (t == java.time.LocalDate.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);
                    if (val == null) return null;
                    return ((java.sql.Date) val).toLocalDate();
                } else if (t == java.time.LocalTime.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);
                    if (val == null) return null;
                    return ((java.sql.Time) val).toLocalTime();
                } else if (t == java.time.LocalDateTime.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);
                    if (val == null) return null;
                    return ((java.sql.Timestamp) val).toLocalDateTime();
                } else if (t.getName().startsWith("java.sql.")) {
                    return index > 0 ? (Serializable) getObject(index) : (Serializable) getObject(column);
                }
                return DataResultSet.getRowColumnValue(this, attr, index, column);
            }

            @Override
            public boolean next() {
                try {
                    return rr.next();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean wasNull() {
                try {
                    return rr.wasNull();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() {
                try {
                    rr.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Object getObject(int index) {
                try {
                    return rr.getObject(index);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Object getObject(String column) {
                try {
                    return rr.getObject(column);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public EntityInfo getEntityInfo() {
                return info;
            }

        };
    }

    protected class ConnectionPool implements AutoCloseable, SourceChangeable {

        protected final LongAdder closeCounter = new LongAdder(); //已关闭连接数

        protected final LongAdder usingCounter = new LongAdder(); //使用中连接数

        protected final LongAdder creatCounter = new LongAdder(); //已创建连接数

        protected final LongAdder cycleCounter = new LongAdder(); //已复用连接数

        protected final LongAdder waitingCounter = new LongAdder(); //可用中连接数

        protected final java.sql.Driver driver;

        protected final Properties connectAttrs;

        protected ArrayBlockingQueue<Connection> queue;

        protected int connectTimeoutSeconds;

        protected int maxconns;

        protected String url;

        public ConnectionPool(Properties prop) {
            this.connectTimeoutSeconds = Integer.decode(prop.getProperty(DATA_SOURCE_CONNECTTIMEOUT_SECONDS, "6"));
            this.maxconns = Math.max(1, Integer.decode(prop.getProperty(DATA_SOURCE_MAXCONNS, "" + Utility.cpus() * 4)));
            this.queue = new ArrayBlockingQueue<>(maxconns);
            this.url = prop.getProperty(DATA_SOURCE_URL);
            String username = prop.getProperty(DATA_SOURCE_USER, "");
            String password = prop.getProperty(DATA_SOURCE_PASSWORD, "");
            this.connectAttrs = new Properties();
            if (username != null) this.connectAttrs.put("user", username);
            if (password != null) this.connectAttrs.put("password", password);
            try {
                this.driver = DriverManager.getDriver(this.url);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onChange(ResourceEvent[] events) {
            for (ResourceEvent event : events) {
                if (event.name().equals(DATA_SOURCE_CONNECTTIMEOUT_SECONDS) || event.name().endsWith("." + DATA_SOURCE_CONNECTTIMEOUT_SECONDS)) {
                    this.connectTimeoutSeconds = Integer.decode(event.newValue().toString());
                } else if (event.name().equals(DATA_SOURCE_URL) || event.name().endsWith("." + DATA_SOURCE_URL)) {
                    this.url = event.newValue().toString();
                } else if (event.name().equals(DATA_SOURCE_USER) || event.name().endsWith("." + DATA_SOURCE_USER)) {
                    this.connectAttrs.put("user", event.newValue().toString());
                } else if (event.name().equals(DATA_SOURCE_PASSWORD) || event.name().endsWith("." + DATA_SOURCE_PASSWORD)) {
                    this.connectAttrs.put("password", event.newValue().toString());
                } else if (event.name().equals(DATA_SOURCE_MAXCONNS) || event.name().endsWith("." + DATA_SOURCE_MAXCONNS)) {
                    logger.log(Level.WARNING, event.name() + " (new-value: " + event.newValue() + ") will not take effect");
                }
            }
        }

        public synchronized Connection pollConnection() {
            Connection conn = queue.poll();
            if (conn == null) {
                if (usingCounter.intValue() >= maxconns) {
                    try {
                        conn = queue.poll(connectTimeoutSeconds, TimeUnit.SECONDS);
                    } catch (InterruptedException t) {
                        logger.log(Level.WARNING, "take pooled connection error", t);
                    }
                    if (conn == null) throw new RuntimeException("create pooled connection timeout");
                }
            }
            if (conn != null) {
                usingCounter.increment();
                waitingCounter.decrement();
                if (checkValid(conn)) {
                    cycleCounter.increment();
                    return conn;
                } else {
                    offerConnection(conn);
                    conn = null;
                }
            }
            try {
                conn = driver.connect(url, connectAttrs);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            usingCounter.increment();
            creatCounter.increment();
            return conn;
        }

        public <C> void offerConnection(final C connection) {
            Connection conn = (Connection) connection;
            if (conn == null) return;
            try {
                if (checkValid(conn) && queue.offer(conn)) {
                    usingCounter.decrement();
                    waitingCounter.increment();
                } else {
                    usingCounter.decrement();
                    closeCounter.increment();
                    conn.close();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "closeSQLConnection abort", e);
            }
        }

        protected boolean checkValid(Connection conn) {
            try {
                return !conn.isClosed() && conn.isValid(1);
            } catch (SQLException ex) {
                if (!"08S01".equals(ex.getSQLState())) {//MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                    logger.log(Level.FINER, "result.getConnection from pooled connection abort [" + ex.getSQLState() + "]", ex);
                }
                return false;
            }
        }

        @Override
        public void close() {
            queue.stream().forEach(x -> {
                try {
                    x.close();
                } catch (Exception e) {
                }
            });
        }
    }
}
