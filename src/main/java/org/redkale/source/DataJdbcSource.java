/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import java.util.logging.Level;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceListener;
import org.redkale.annotation.ResourceType;
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
    protected void updateOneResourceChange(Properties newProps, ResourceEvent[] events) {
        this.readPool.onResourceChange(events);
    }

    @Override
    protected void updateReadResourceChange(Properties newReadProps, ResourceEvent[] events) {
        this.readPool.onResourceChange(events);
    }

    @Override
    protected void updateWriteResourceChange(Properties newWriteProps, ResourceEvent[] events) {
        this.writePool.onResourceChange(events);
    }

    @Override
    public void destroy(AnyValue config) {
        if (readPool != null) readPool.close();
        if (writePool != null && writePool != readPool) writePool.close();
    }

    @Local
    @Override
    public void close() throws Exception {
        super.close();
        if (readPool != null) readPool.close();
        if (writePool != null && writePool != readPool) writePool.close();
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
    public CompletableFuture<Integer> batchAsync(final DataBatch batch) {
        return CompletableFuture.supplyAsync(() -> batch(batch), getExecutor());
    }

    protected <T> List<PreparedStatement> createInsertPreparedStatements(final Connection conn, EntityInfo<T> info, Map<String, PrepareInfo<T>> prepareInfos, T... entitys) throws SQLException {
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        final List<PreparedStatement> prestmts = new ArrayList<>();
        for (Map.Entry<String, PrepareInfo<T>> en : prepareInfos.entrySet()) {
            PrepareInfo<T> prepareInfo = en.getValue();
            PreparedStatement prestmt = conn.prepareStatement(prepareInfo.prepareSql);
            for (final T value : prepareInfo.entitys) {
                batchStatementParameters(conn, prestmt, info, attrs, value);
                prestmt.addBatch();
            }
            prestmts.add(prestmt);
        }
        return prestmts;
    }

    protected <T> PreparedStatement createInsertPreparedStatement(Connection conn, String sql, EntityInfo<T> info, T... entitys) throws SQLException {
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        final PreparedStatement prestmt = conn.prepareStatement(sql);
        for (final T value : entitys) {
            batchStatementParameters(conn, prestmt, info, attrs, value);
            prestmt.addBatch();
        }
        return prestmt;
    }

    protected <T> List<PreparedStatement> createUpdatePreparedStatements(final Connection conn, EntityInfo<T> info, Map<String, PrepareInfo<T>> prepareInfos, T... entitys) throws SQLException {
        Attribute<T, Serializable> primary = info.primary;
        Attribute<T, Serializable>[] attrs = info.updateAttributes;
        final List<PreparedStatement> prestmts = new ArrayList<>();
        for (Map.Entry<String, PrepareInfo<T>> en : prepareInfos.entrySet()) {
            PrepareInfo<T> prepareInfo = en.getValue();
            PreparedStatement prestmt = conn.prepareStatement(prepareInfo.prepareSql);
            for (final T value : prepareInfo.entitys) {
                int k = batchStatementParameters(conn, prestmt, info, attrs, value);
                prestmt.setObject(++k, primary.get(value));
                prestmt.addBatch();
            }
            prestmts.add(prestmt);
        }
        return prestmts;
    }

    protected <T> PreparedStatement createUpdatePreparedStatement(Connection conn, String sql, EntityInfo<T> info, T... entitys) throws SQLException {
        Attribute<T, Serializable> primary = info.primary;
        Attribute<T, Serializable>[] attrs = info.updateAttributes;
        final PreparedStatement prestmt = conn.prepareStatement(sql);
        for (final T value : entitys) {
            int k = batchStatementParameters(conn, prestmt, info, attrs, value);
            prestmt.setObject(++k, primary.get(value));
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
    protected <T> CompletableFuture<Integer> deleteDB(EntityInfo<T> info, Flipper flipper, String... sqls) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            int c = 0;
            if (sqls.length == 1) {
                String sql = sqls[0];
                sql += ((flipper == null || flipper.getLimit() < 1) ? "" : (" LIMIT " + flipper.getLimit()));
                if (info.isLoggable(logger, Level.FINEST, sql)) {
                    logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                }
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
            } else {
                if (flipper == null || flipper.getLimit() < 1) {
                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                        logger.finest(info.getType().getSimpleName() + " delete sqls=" + Arrays.toString(sqls));
                    }
                    final Statement stmt = conn.createStatement();
                    for (String sql : sqls) {
                        stmt.addBatch(sql);
                    }
                    int[] cs = stmt.executeBatch();
                    stmt.close();
                    for (int cc : cs) {
                        c += cc;
                    }
                } else {
                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                        logger.finest(info.getType().getSimpleName() + " limit " + flipper.getLimit() + " delete sqls=" + Arrays.toString(sqls));
                    }
                    final Statement stmt = conn.createStatement();
                    for (String sql : sqls) {
                        stmt.addBatch(sql + " LIMIT " + flipper.getLimit());
                    }
                    int[] cs = stmt.executeBatch();
                    stmt.close();
                    for (int cc : cs) {
                        c += cc;
                    }
                }
            }
            slowLog(s, sqls);
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                            return CompletableFuture.completedFuture(0);
                        } catch (SQLException e2) {
                            return CompletableFuture.failedFuture(e2);
                        }
                    }
                } else {
                    return CompletableFuture.failedFuture(e);
                }
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDB(EntityInfo<T> info, final String[] tables, String... sqls) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            int c = 0;
            final Statement stmt = conn.createStatement();
            if (sqls.length == 1) {
                String sql = sqls[0];
                c = stmt.executeUpdate(sql);
            } else {
                for (String sql : sqls) {
                    stmt.addBatch(sql);
                }
                int[] cs = stmt.executeBatch();
                for (int cc : cs) {
                    c += cc;
                }
            }
            stmt.close();
            slowLog(s, sqls);
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) return CompletableFuture.completedFuture(-1);
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDB(EntityInfo<T> info, String[] tables, String... sqls) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            int c = 0;
            final Statement stmt = conn.createStatement();
            if (sqls.length == 1) {
                String sql = sqls[0];
                c = stmt.executeUpdate(sql);
            } else {
                for (String sql : sqls) {
                    stmt.addBatch(sql);
                }
                int[] cs = stmt.executeBatch();
                for (int cc : cs) {
                    c += cc;
                }
            }
            stmt.close();
            if (info.getTableStrategy() != null) {
                for (String table : tables) {
                    String tablekey = table.indexOf('.') > 0 ? table : (conn.getCatalog() + '.' + table);
                    info.removeDisTable(tablekey);
                }
            }
            slowLog(s, sqls);
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) return CompletableFuture.completedFuture(-1);
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    public int batch(final DataBatch batch) {
        Objects.requireNonNull(batch);
        final List<BatchAction> actions = ((DefaultDataBatch) batch).actions;
        final DefaultBatchInfo batchInfo = parseBatchInfo((DefaultDataBatch) batch, this);

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            int c = 0;
            conn = writePool.pollConnection();
            Attribute<T, Serializable>[] attrs = info.insertAttributes;
            conn.setReadOnly(false);
            conn.setAutoCommit(false);

            String presql = null;
            PreparedStatement prestmt = null;

            List<PreparedStatement> prestmts = null;
            Map<String, PrepareInfo<T>> prepareInfos = null;

            if (info.getTableStrategy() == null) {
                presql = info.getInsertQuestionPrepareSQL(entitys[0]);
                prestmt = createInsertPreparedStatement(conn, presql, info, entitys);
            } else {
                prepareInfos = getInsertQuestionPrepareInfo(info, entitys);
                prestmts = createInsertPreparedStatements(conn, info, prepareInfos, entitys);
            }
            try {
                if (info.getTableStrategy() == null) {
                    int c1 = 0;
                    int[] cs = prestmt.executeBatch();
                    for (int cc : cs) {
                        c1 += cc;
                    }
                    c = c1;
                    prestmt.close();
                } else {
                    int c1 = 0;
                    for (PreparedStatement stmt : prestmts) {
                        int[] cs = stmt.executeBatch();
                        for (int cc : cs) {
                            c1 += cc;
                        }
                    }
                    c = c1;
                    for (PreparedStatement stmt : prestmts) {
                        stmt.close();
                    }
                }
                conn.commit();
            } catch (SQLException se) {
                conn.rollback();
                if (!isTableNotExist(info, se.getSQLState())) throw se;
                if (info.getTableStrategy() == null) { //单表
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls == null) throw se;
                    //创建单表结构
                    Statement st = conn.createStatement();
                    if (tableSqls.length == 1) {
                        st.execute(tableSqls[0]);
                    } else {
                        for (String tableSql : tableSqls) {
                            st.addBatch(tableSql);
                        }
                        st.executeBatch();
                    }
                    st.close();
                } else { //分库分表
                    synchronized (info.disTableLock()) {
                        final String catalog = conn.getCatalog();
                        final Set<String> newCatalogs = new LinkedHashSet<>();
                        final List<String> tableCopys = new ArrayList<>();
                        prepareInfos.forEach((t, p) -> {
                            int pos = t.indexOf('.');
                            if (pos > 0) {
                                newCatalogs.add(t.substring(0, pos));
                            }
                            tableCopys.add(getTableCopySQL(info, t));
                        });
                        try {
                            //执行一遍创建分表操作
                            Statement st = conn.createStatement();
                            for (String copySql : tableCopys) {
                                st.addBatch(copySql);
                            }
                            st.executeBatch();
                            st.close();
                        } catch (SQLException sqle) { //多进程并发时可能会出现重复建表
                            if (isTableNotExist(info, sqle.getSQLState())) {
                                if (newCatalogs.isEmpty()) { //分表的原始表不存在
                                    String[] tableSqls = createTableSqls(info);
                                    if (tableSqls != null) {
                                        //创建原始表
                                        Statement st = conn.createStatement();
                                        if (tableSqls.length == 1) {
                                            st.execute(tableSqls[0]);
                                        } else {
                                            for (String tableSql : tableSqls) {
                                                st.addBatch(tableSql);
                                            }
                                            st.executeBatch();
                                        }
                                        st.close();
                                        //再执行一遍创建分表操作
                                        st = conn.createStatement();
                                        for (String copySql : tableCopys) {
                                            st.addBatch(copySql);
                                        }
                                        st.executeBatch();
                                        st.close();
                                    }
                                } else { //需要先建库
                                    Statement st;
                                    try {
                                        st = conn.createStatement();
                                        for (String newCatalog : newCatalogs) {
                                            st.addBatch(("postgresql".equals(dbtype()) ? "CREATE SCHEMA IF NOT EXISTS " : "CREATE DATABASE IF NOT EXISTS ") + newCatalog);
                                        }
                                        st.executeBatch();
                                        st.close();
                                    } catch (SQLException sqle1) {
                                        logger.log(Level.SEVERE, "create database " + tableCopys + " error", sqle1);
                                    }
                                    try {
                                        //再执行一遍创建分表操作
                                        st = conn.createStatement();
                                        for (String copySql : tableCopys) {
                                            st.addBatch(copySql);
                                        }
                                        st.executeBatch();
                                        st.close();
                                    } catch (SQLException sqle2) {
                                        if (isTableNotExist(info, sqle2.getSQLState())) {
                                            String[] tableSqls = createTableSqls(info);
                                            if (tableSqls != null) { //创建原始表
                                                st = conn.createStatement();
                                                if (tableSqls.length == 1) {
                                                    st.execute(tableSqls[0]);
                                                } else {
                                                    for (String tableSql : tableSqls) {
                                                        st.addBatch(tableSql);
                                                    }
                                                    st.executeBatch();
                                                }
                                                st.close();
                                                //再执行一遍创建分表操作
                                                st = conn.createStatement();
                                                for (String copySql : tableCopys) {
                                                    st.addBatch(copySql);
                                                }
                                                st.executeBatch();
                                                st.close();
                                            }
                                        } else {
                                            logger.log(Level.SEVERE, "create table2 " + tableCopys + " error", sqle2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (info.getTableStrategy() == null) {
                    prestmt.close();
                    prestmt = createInsertPreparedStatement(conn, presql, info, entitys);
                    int c1 = 0;
                    int[] cs = prestmt.executeBatch();
                    for (int cc : cs) {
                        c1 += cc;
                    }
                    c = c1;
                    prestmt.close();
                } else {
                    for (PreparedStatement stmt : prestmts) {
                        stmt.close();
                    }
                    prestmts = createInsertPreparedStatements(conn, info, prepareInfos, entitys);
                    int c1 = 0;
                    for (PreparedStatement stmt : prestmts) {
                        int[] cs = stmt.executeBatch();
                        for (int cc : cs) {
                            c1 += cc;
                        }
                    }
                    c = c1;
                    for (PreparedStatement stmt : prestmts) {
                        stmt.close();
                    }
                }
                conn.commit();
            }
            //------------------------------------------------------------
            if (info.isLoggable(logger, Level.FINEST)) {  //打印调试信息
                if (info.getTableStrategy() == null) {
                    char[] sqlchars = presql.toCharArray();
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
                } else {
                    prepareInfos.forEach((t, p) -> {
                        char[] sqlchars = p.prepareSql.toCharArray();
                        for (final T value : p.entitys) {
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
                    });
                }
            } //打印结束         
            if (info.getTableStrategy() == null) {
                slowLog(s, presql);
            } else {
                List<String> presqls = new ArrayList<>();
                prepareInfos.forEach((t, p) -> {
                    presqls.add(p.prepareSql);
                });
                slowLog(s, presqls.toArray(new String[presqls.size()]));
            }
            return CompletableFuture.completedFuture(c);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateEntityDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        String presql = null;
        PreparedStatement prestmt = null;

        List<PreparedStatement> prestmts = null;
        Map<String, PrepareInfo<T>> prepareInfos = null;
        SQLException ex = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);

            int c = -1;
            final Attribute<T, Serializable>[] attrs = info.updateAttributes;
            int retry = 0;
            AGAIN:
            while (retry++ < MAX_RETRYS) {
                try {
                    if (info.getTableStrategy() == null) {
                        presql = info.getUpdateQuestionPrepareSQL(entitys[0]);
                        prestmt = createUpdatePreparedStatement(conn, presql, info, entitys);
                        int c1 = 0;
                        int[] pc = prestmt.executeBatch();
                        for (int p : pc) {
                            if (p >= 0) c1 += p;
                        }
                        c = c1;
                        prestmt.close();
                    } else {
                        if (prepareInfos == null) {
                            prepareInfos = getUpdateQuestionPrepareInfo(info, entitys);
                        }
                        prestmts = createUpdatePreparedStatements(conn, info, prepareInfos, entitys);
                        int c1 = 0;
                        for (PreparedStatement stmt : prestmts) {
                            int[] cs = stmt.executeBatch();
                            for (int cc : cs) {
                                c1 += cc;
                            }
                        }
                        c = c1;
                        for (PreparedStatement stmt : prestmts) {
                            stmt.close();
                        }
                    }
                    conn.commit();
                    break;
                } catch (SQLException se) {
                    ex = se;
                    conn.rollback();
                    if (isTableNotExist(info, se.getSQLState())) {
                        if (info.getTableStrategy() == null) {
                            String[] tableSqls = createTableSqls(info);
                            if (tableSqls != null) {
                                try {
                                    Statement st = conn.createStatement();
                                    if (tableSqls.length == 1) {
                                        st.execute(tableSqls[0]);
                                    } else {
                                        for (String tableSql : tableSqls) {
                                            st.addBatch(tableSql);
                                        }
                                        st.executeBatch();
                                    }
                                    st.close();
                                } catch (SQLException e2) {
                                }
                            }
                            //表都不存在，更新条数为0
                            return CompletableFuture.completedFuture(0);
                        } else {
                            String tableName = parseNotExistTableName(se);
                            if (tableName == null || prepareInfos == null) {
                                return CompletableFuture.failedFuture(se);
                            }
                            String minTableName = (tableName.indexOf('.') > 0) ? tableName.substring(tableName.indexOf('.') + 1) : null;
                            for (String t : prepareInfos.keySet()) {
                                if (t.equals(tableName)) {
                                    prepareInfos.remove(t);
                                    if (info.getTableStrategy() == null) {
                                        prestmt.close();
                                    } else {
                                        for (PreparedStatement stmt : prestmts) {
                                            stmt.close();
                                        }
                                    }
                                    continue AGAIN;
                                } else if (minTableName != null && t.equals(minTableName)) {
                                    prepareInfos.remove(t);
                                    if (info.getTableStrategy() == null) {
                                        prestmt.close();
                                    } else {
                                        for (PreparedStatement stmt : prestmts) {
                                            stmt.close();
                                        }
                                    }
                                    continue AGAIN;
                                }
                            }
                            return CompletableFuture.failedFuture(se);
                        }
                    }
                    throw se;
                }
            }

            if (info.isLoggable(logger, Level.FINEST)) {  //打印调试信息
                Attribute<T, Serializable> primary = info.getPrimary();
                if (info.getTableStrategy() == null) {
                    char[] sqlchars = presql.toCharArray();
                    for (final T value : entitys) {
                        //-----------------------------
                        StringBuilder sb = new StringBuilder(128);
                        int i = 0;
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
                        if (info.isLoggable(logger, Level.FINEST, debugsql)) logger.finest(info.getType().getSimpleName() + " update sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                    }
                } else {
                    prepareInfos.forEach((t, p) -> {
                        char[] sqlchars = p.prepareSql.toCharArray();
                        for (final T value : p.entitys) {
                            //-----------------------------
                            StringBuilder sb = new StringBuilder(128);
                            int i = 0;
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
                            if (info.isLoggable(logger, Level.FINEST, debugsql)) logger.finest(info.getType().getSimpleName() + " update sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                        }
                    });
                }
            } //打印结束         
            if (info.getTableStrategy() == null) {
                slowLog(s, presql);
            } else {
                List<String> presqls = new ArrayList<>();
                prepareInfos.forEach((t, p) -> {
                    presqls.add(p.prepareSql);
                });
                slowLog(s, presqls.toArray(new String[presqls.size()]));
            }
            if (c >= 0) {
                return CompletableFuture.completedFuture(c);
            } else {
                return CompletableFuture.failedFuture(ex);
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            return CompletableFuture.failedFuture(e);
        } finally {
            if (conn != null) writePool.offerConnection(conn);
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateColumnDB(EntityInfo<T> info, Flipper flipper, SqlInfo sql) { //String sql, boolean prepared, Object... blobs) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(true);
            if (sql.blobs != null || sql.tables != null) {
                final PreparedStatement prestmt = conn.prepareStatement(sql.sql);
                int c = 0;
                if (sql.tables == null) {
                    int index = 0;
                    for (byte[] param : sql.blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, param);
                        prestmt.setBlob(++index, blob);
                    }
                    c = prestmt.executeUpdate();
                } else {
                    for (String table : sql.tables) {
                        int index = 0;
                        if (sql.blobs != null) {
                            for (byte[] param : sql.blobs) {
                                Blob blob = conn.createBlob();
                                blob.setBytes(1, param);
                                prestmt.setBlob(++index, blob);
                            }
                        }
                        prestmt.setString(++index, table);
                        prestmt.addBatch();
                    }
                    int[] cs = prestmt.executeBatch();
                    for (int cc : cs) {
                        c += cc;
                    }
                }
                prestmt.close();
                slowLog(s, sql.sql);
                return CompletableFuture.completedFuture(c);
            } else {
                if (info.isLoggable(logger, Level.FINEST, sql.sql)) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                int c = stmt.executeUpdate(sql.sql);
                stmt.close();
                slowLog(s, sql.sql);
                return CompletableFuture.completedFuture(c);
            }
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
                                }
                                st.executeBatch();
                            }
                            st.close();
                        } catch (SQLException e2) {
                        }
                    }
                    return CompletableFuture.completedFuture(0);
                } else {
                    return CompletableFuture.failedFuture(e);
                }
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return CompletableFuture.completedFuture(map);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final DataResultSet set = createDataResultSet(info, ps.executeQuery());
            T rs = set.next() ? selects == null ? info.getFullEntityValue(set) : info.getEntityValue(selects, set) : null;
            set.close();
            ps.close();
            slowLog(s, sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return CompletableFuture.completedFuture(val == null ? defValue : val);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
        final long s = System.currentTimeMillis();
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists (" + rs + ") sql=" + sql);
            slowLog(s, sql);
            return CompletableFuture.completedFuture(rs);
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
                        try {
                            Statement st = conn.createStatement();
                            if (tableSqls.length == 1) {
                                st.execute(tableSqls[0]);
                            } else {
                                for (String tableSql : tableSqls) {
                                    st.addBatch(tableSql);
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
    protected <T> CompletableFuture<Sheet<T>> querySheetDB(EntityInfo<T> info, final boolean readcache, boolean needTotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();

        final SelectColumn sels = selects;
        final List<T> list = new ArrayList();
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String[] tables = info.getTables(node);
        final String joinAndWhere = (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        final boolean mysqlOrPgsql = "mysql".equals(dbtype()) || "postgresql".equals(dbtype());
        SQLException ex = null;
        try {
            conn = readPool.pollConnection();
            PreparedStatement ps = null;
            //conn.setReadOnly(true);
            int retry = 0;
            AGAIN:
            while (retry++ < MAX_RETRYS) {
                String listSql = null;
                String countSql = null;
                {  //组装listSql、countSql
                    String listSubSql;
                    StringBuilder union = new StringBuilder();
                    if (tables.length == 1) {
                        listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getFullQueryColumns("a", selects) + " FROM " + tables[0] + " a" + joinAndWhere;
                    } else {
                        int b = 0;
                        for (String table : tables) {
                            if (!union.isEmpty()) union.append(" UNION ALL ");
                            union.append("SELECT ").append(info.getFullQueryColumns("a", selects)).append(" FROM ").append(table).append(" a").append(joinAndWhere);
                        }
                        listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getFullQueryColumns("a", selects) + " FROM (" + (union) + ") a";
                    }
                    listSql = listSubSql + createSQLOrderby(info, flipper);
                    if (mysqlOrPgsql) {
                        listSql += (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset()));
                        if (readcache && info.isLoggable(logger, Level.FINEST, listSql)) {
                            logger.finest(info.getType().getSimpleName() + " query sql=" + listSql);
                        }
                    } else {
                        if (readcache && info.isLoggable(logger, Level.FINEST, listSql)) {
                            logger.finest(info.getType().getSimpleName() + " query sql=" + listSql + (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset())));
                        }
                    }
                    if (mysqlOrPgsql && needTotal) {
                        String countSubSql;
                        if (tables.length == 1) {
                            countSubSql = "SELECT " + (distinct ? "DISTINCT COUNT(" + info.getQueryColumns("a", selects) + ")" : "COUNT(*)") + " FROM " + tables[0] + " a" + joinAndWhere;
                        } else {
                            countSubSql = "SELECT " + (distinct ? "DISTINCT COUNT(" + info.getQueryColumns("a", selects) + ")" : "COUNT(*)") + " FROM (" + (union) + ") a";
                        }
                        countSql = countSubSql;
                        if (readcache && info.isLoggable(logger, Level.FINEST, countSql)) {
                            logger.finest(info.getType().getSimpleName() + " query countsql=" + countSql);
                        }
                    }
                }
                try {
                    if (mysqlOrPgsql) {  //sql可以带limit、offset
                        ps = conn.prepareStatement(listSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        ResultSet set = ps.executeQuery();
                        final DataResultSet rr = createDataResultSet(info, set);
                        while (set.next()) {
                            list.add(getEntityValue(info, sels, rr));
                        }
                        set.close();
                        ps.close();
                        long total = list.size();
                        if (needTotal) {
                            ps = conn.prepareStatement(countSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                            set = ps.executeQuery();
                            if (set.next()) total = set.getLong(1);
                            set.close();
                            ps.close();
                        }
                        slowLog(s, listSql);
                        return CompletableFuture.completedFuture(new Sheet<>(total, list));
                    } else {
                        //conn.setReadOnly(true);
                        ps = conn.prepareStatement(listSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        if (flipper != null && flipper.getLimit() > 0) ps.setFetchSize(flipper.getLimit());
                        ResultSet set = ps.executeQuery();
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
                        if (needTotal && flipper != null) {
                            set.last();
                            total = set.getRow();
                        }
                        set.close();
                        ps.close();
                        slowLog(s, listSql);
                        return CompletableFuture.completedFuture(new Sheet<>(total, list));
                    }
                } catch (SQLException se) {
                    ex = se;
                    if (isTableNotExist(info, se.getSQLState())) {
                        if (info.getTableStrategy() == null) {
                            String[] tableSqls = createTableSqls(info);
                            if (tableSqls != null) {
                                try {
                                    Statement st = conn.createStatement();
                                    if (tableSqls.length == 1) {
                                        st.execute(tableSqls[0]);
                                    } else {
                                        for (String tableSql : tableSqls) {
                                            st.addBatch(tableSql);
                                        }
                                        st.executeBatch();
                                    }
                                    st.close();
                                } catch (SQLException e2) {
                                }
                            }
                            return CompletableFuture.completedFuture(new Sheet<>(0, new ArrayList()));
                        } else if (tables != null && tables.length == 1) {
                            //只查一个不存在的分表
                            return CompletableFuture.completedFuture(new Sheet<>(0, new ArrayList()));
                        } else if (tables != null && tables.length > 1) {
                            //多分表查询中一个或多个分表不存在
                            String tableName = parseNotExistTableName(se);
                            if (tableName == null) {
                                return CompletableFuture.failedFuture(se);
                            }
                            String minTableName = (tableName.indexOf('.') > 0) ? tableName.substring(tableName.indexOf('.') + 1) : null;
                            if (ps != null) ps.close();
                            for (String t : tables) {
                                if (t.equals(tableName) || (minTableName != null && t.equals(minTableName))) {
                                    tables = Utility.remove(tables, t);
                                    if (tables.length < 1) { //分表都不存在
                                        return CompletableFuture.completedFuture(new Sheet<>(0, new ArrayList()));
                                    }
                                    continue AGAIN;
                                }
                            }
                        } else {
                            return CompletableFuture.failedFuture(se);
                        }
                    }
                    return CompletableFuture.failedFuture(se);
                }
            }
            throw ex;
        } catch (Exception e) {
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sqls);
            return rs;
        } catch (SQLException e) {
            throw new SourceException(e);
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
        final long s = System.currentTimeMillis();
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
            slowLog(s, sql);
            return rs;
        } catch (Exception ex) {
            throw new SourceException(ex);
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
                    throw new SourceException(e);
                }
            }

            @Override
            public List<String> getColumnLabels() {
                try {
                    ResultSetMetaData meta = rr.getMetaData();
                    int count = meta.getColumnCount();
                    List<String> labels = new ArrayList<>(count);
                    for (int i = 1; i <= count; i++) {
                        labels.add(meta.getColumnLabel(i));
                    }
                    return labels;
                } catch (SQLException e) {
                    throw new SourceException(e);
                }
            }

            @Override
            public boolean wasNull() {
                try {
                    return rr.wasNull();
                } catch (SQLException e) {
                    throw new SourceException(e);
                }
            }

            @Override
            public void close() {
                try {
                    rr.close();
                } catch (SQLException e) {
                    throw new SourceException(e);
                }
            }

            @Override
            public Object getObject(int index) {
                try {
                    return rr.getObject(index);
                } catch (SQLException e) {
                    throw new SourceException(e);
                }
            }

            @Override
            public Object getObject(String column) {
                try {
                    return rr.getObject(column);
                } catch (SQLException e) {
                    throw new SourceException(e);
                }
            }

            @Override
            public EntityInfo getEntityInfo() {
                return info;
            }

        };
    }

    protected class ConnectionPool implements AutoCloseable {

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

        protected int urlVersion;

        protected Properties clientInfo = new Properties();

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
                throw new SourceException(e);
            }
            clientInfo.put("version", String.valueOf(urlVersion));
        }

        @ResourceListener
        public synchronized void onResourceChange(ResourceEvent[] events) {
            String newUrl = this.url;
            int newConnectTimeoutSeconds = this.connectTimeoutSeconds;
            int newMaxconns = this.maxconns;
            String newUser = this.connectAttrs.getProperty("user");
            String newPassword = this.connectAttrs.getProperty("password");
            for (ResourceEvent event : events) {
                if (event.name().equals(DATA_SOURCE_URL) || event.name().endsWith("." + DATA_SOURCE_URL)) {
                    newUrl = event.newValue().toString();
                } else if (event.name().equals(DATA_SOURCE_CONNECTTIMEOUT_SECONDS) || event.name().endsWith("." + DATA_SOURCE_CONNECTTIMEOUT_SECONDS)) {
                    newConnectTimeoutSeconds = Integer.decode(event.newValue().toString());
                } else if (event.name().equals(DATA_SOURCE_USER) || event.name().endsWith("." + DATA_SOURCE_USER)) {
                    newUser = event.newValue().toString();
                } else if (event.name().equals(DATA_SOURCE_PASSWORD) || event.name().endsWith("." + DATA_SOURCE_PASSWORD)) {
                    newPassword = event.newValue().toString();
                } else if (event.name().equals(DATA_SOURCE_MAXCONNS) || event.name().endsWith("." + DATA_SOURCE_MAXCONNS)) {
                    newMaxconns = Math.max(1, Integer.decode(event.newValue().toString()));
                }
            }
            if (!Objects.equals(newUser, this.connectAttrs.get("user"))
                || !Objects.equals(newPassword, this.connectAttrs.get("password")) || !Objects.equals(newUrl, url)) {
                this.urlVersion++;
                Properties newClientInfo = new Properties();
                newClientInfo.put("version", String.valueOf(urlVersion));
                this.clientInfo = newClientInfo;
            }
            this.url = newUrl;
            this.connectTimeoutSeconds = newConnectTimeoutSeconds;
            this.connectAttrs.put("user", newUser);
            this.connectAttrs.put("password", newPassword);
            if (newMaxconns != this.maxconns) {
                ArrayBlockingQueue<Connection> newQueue = new ArrayBlockingQueue<>(newMaxconns);
                ArrayBlockingQueue<Connection> oldQueue = this.queue;
                this.queue = newQueue;
                this.maxconns = newMaxconns;
                Connection conn;
                while ((conn = oldQueue.poll()) != null) {
                    offerConnection(conn);
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
                    if (conn == null) throw new SourceException("create pooled connection timeout");
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
                conn.setClientInfo(clientInfo);
            } catch (SQLException ex) {
                throw new SourceException(ex);
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
                boolean rs = !conn.isClosed() && conn.isValid(1);
                if (!rs) return rs;
                Properties prop = conn.getClientInfo();
                if (prop == null) return false;
                return prop == clientInfo || Objects.equals(prop.getProperty("version"), clientInfo.getProperty("version"));
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
