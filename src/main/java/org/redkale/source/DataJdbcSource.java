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
public class DataJdbcSource extends AbstractDataSqlSource {

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
        if (readPool != null) {
            readPool.close();
        }
        if (writePool != null && writePool != readPool) {
            writePool.close();
        }
    }

    @Local
    @Override
    public void close() throws Exception {
        super.close();
        if (readPool != null) {
            readPool.close();
        }
        if (writePool != null && writePool != readPool) {
            writePool.close();
        }
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
    public int batch(final DataBatch batch) {
        Objects.requireNonNull(batch);
        final DefaultDataBatch dataBatch = (DefaultDataBatch) batch;
        if (dataBatch.actions.isEmpty()) {
            return 0;
        }
        int c = 0;
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            for (BatchAction action : dataBatch.actions) {
                if (action instanceof InsertBatchAction1) {
                    InsertBatchAction1 act = (InsertBatchAction1) action;
                    EntityInfo info = apply(act.entity.getClass());
                    c += insertDB(true, conn, info, act.entity);

                } else if (action instanceof DeleteBatchAction1) {
                    DeleteBatchAction1 act = (DeleteBatchAction1) action;
                    EntityInfo info = apply(act.entity.getClass());
                    Serializable pk = info.getPrimaryValue(act.entity);
                    Map<String, List<Serializable>> pkmap = info.getTableMap(pk);
                    String[] tables = pkmap.keySet().toArray(new String[pkmap.size()]);
                    String[] sqls = deleteSql(info, pkmap);
                    c += deleteDB(true, conn, info, tables, null, null, pkmap, sqls);

                } else if (action instanceof DeleteBatchAction2) {
                    DeleteBatchAction2 act = (DeleteBatchAction2) action;
                    EntityInfo info = apply(act.clazz);
                    Map<String, List<Serializable>> pkmap = info.getTableMap(act.pk);
                    String[] tables = pkmap.keySet().toArray(new String[pkmap.size()]);
                    String[] sqls = deleteSql(info, pkmap);
                    c += deleteDB(true, conn, info, tables, null, null, pkmap, sqls);

                } else if (action instanceof DeleteBatchAction3) {
                    DeleteBatchAction3 act = (DeleteBatchAction3) action;
                    EntityInfo info = apply(act.clazz);
                    String[] tables = info.getTables(act.node);
                    String[] sqls = deleteSql(info, tables, act.flipper, act.node);
                    c += deleteDB(true, conn, info, tables, act.flipper, act.node, null, sqls);

                } else if (action instanceof UpdateBatchAction1) {
                    UpdateBatchAction1 act = (UpdateBatchAction1) action;
                    EntityInfo info = apply(act.entity.getClass());
                    c += updateEntityDB(true, conn, info, act.entity);

                } else if (action instanceof UpdateBatchAction2) {
                    UpdateBatchAction2 act = (UpdateBatchAction2) action;
                    EntityInfo info = apply(act.clazz);
                    UpdateSqlInfo sql = updateColumnSql(info, act.pk, act.values);
                    c += updateColumnDB(true, conn, info, null, sql);

                } else if (action instanceof UpdateBatchAction3) {
                    UpdateBatchAction3 act = (UpdateBatchAction3) action;
                    EntityInfo info = apply(act.clazz);
                    UpdateSqlInfo sql = updateColumnSql(info, act.node, act.flipper, act.values);
                    c += updateColumnDB(true, conn, info, act.flipper, sql);

                } else if (action instanceof UpdateBatchAction4) {
                    UpdateBatchAction4 act = (UpdateBatchAction4) action;
                    EntityInfo info = apply(act.entity.getClass());
                    UpdateSqlInfo sql = updateColumnSql(info, false, act.entity, act.node, act.selects);
                    c += updateColumnDB(true, conn, info, null, sql);
                }
            }
            conn.commit();
            return c;
        } catch (SourceException se) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException sqe) {
                }
            }
            throw se;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    @Override
    public CompletableFuture<Integer> batchAsync(final DataBatch batch) {
        return CompletableFuture.supplyAsync(() -> batch(batch), getExecutor());
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDBAsync(EntityInfo<T> info, T... entitys) {
        return supplyAsync(() -> insertDB(info, entitys));
    }

    @Override
    protected <T> int insertDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c = insertDB(false, conn, info, entitys);
            conn.commit();
            return c;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    private <T> int insertDB(final boolean batch, final Connection conn, final EntityInfo<T> info, T... entitys) throws SQLException {
        final long s = System.currentTimeMillis();
        int c = 0;
        String presql = null;
        PreparedStatement prestmt = null;
        List<PreparedStatement> prestmts = null;
        Map<String, PrepareInfo<T>> prepareInfos = null;
        Attribute<T, Serializable>[] attrs = info.insertAttributes;
        if (info.getTableStrategy() == null) { //单库单表
            presql = info.getInsertQuestionPrepareSQL(entitys[0]);
            prestmt = createInsertPreparedStatement(conn, presql, info, entitys);
        } else {  //分库分表
            prepareInfos = getInsertQuestionPrepareInfo(info, entitys);
            prestmts = createInsertPreparedStatements(conn, info, prepareInfos, entitys);
        }
        try {
            if (info.getTableStrategy() == null) { //单库单表
                int c1 = 0;
                int[] cs = prestmt.executeBatch();
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
                prestmt.close();
            } else {  //分库分表
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
            if (!batch) {
                conn.commit();
            }
        } catch (SQLException se) {
            if (!batch) {
                conn.rollback();
            }
            if (!isTableNotExist(info, se.getSQLState())) {
                throw se;
            }
            if (info.getTableStrategy() == null) { //单库单表
                String[] tableSqls = createTableSqls(info);
                if (tableSqls == null) {
                    throw se;
                }
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
                info.disTableLock().lock();
                try {
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
                } finally {
                    info.disTableLock().unlock();
                }
            }
            if (info.getTableStrategy() == null) { //单库单表
                prestmt.close();
                prestmt = createInsertPreparedStatement(conn, presql, info, entitys);
                int c1 = 0;
                int[] cs = prestmt.executeBatch();
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
                prestmt.close();
            } else { //分库分表
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
                    if (info.isLoggable(logger, Level.FINEST, debugsql)) {
                        logger.finest(info.getType().getSimpleName() + " insert sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                    }
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
                        if (info.isLoggable(logger, Level.FINEST, debugsql)) {
                            logger.finest(info.getType().getSimpleName() + " insert sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                        }
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
        return c;
    }

    @Override
    protected <T> CompletableFuture<Integer> deleteDBAsync(final EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, final String... sqls) {
        return supplyAsync(() -> deleteDB(info, tables, flipper, node, pkmap, sqls));
    }

    @Override
    protected <T> int deleteDB(EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, String... sqls) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c = deleteDB(false, conn, info, tables, flipper, node, pkmap, sqls);
            conn.commit();
            return c;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    private <T> int deleteDB(final boolean batch, final Connection conn, final EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, String... sqls) throws SQLException {
        final long s = System.currentTimeMillis();
        try {
            int c;
            if (sqls.length == 1) {
                final Statement stmt = conn.createStatement();
                int c1 = stmt.executeUpdate(sqls[0]);
                stmt.close();
                c = c1;
            } else {
                final Statement stmt = conn.createStatement();
                for (String sql : sqls) {
                    stmt.addBatch(sql);
                }
                int c1 = 0;
                int[] cs = stmt.executeBatch();
                stmt.close();
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
            }
            if (!batch) {
                conn.commit();
            }
            slowLog(s, sqls);
            return c;
        } catch (SQLException e) {
            if (!batch) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    String[] tableSqls = createTableSqls(info);
                    if (tableSqls != null) {
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
                        return 0;
                    }
                    //单表结构不存在
                    return 0;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return 0;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw e;
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw e;
                    }
                    for (String t : notExistTables) {
                        if (pkmap != null) {
                            pkmap.remove(t);
                        } else {
                            tables = Utility.remove(tables, t);
                        }
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "delete, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + (pkmap != null ? pkmap.keySet() : Arrays.toString(tables)));
                    }
                    if ((pkmap != null ? pkmap.size() : tables.length) == 0) { //分表全部不存在
                        return 0;
                    }
                    sqls = pkmap != null ? deleteSql(info, pkmap) : deleteSql(info, tables, flipper, node);
                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                        logger.finest(info.getType().getSimpleName() + " delete sql=" + Arrays.toString(sqls));
                    }
                    try {
                        final Statement stmt = conn.createStatement();
                        for (String sql : sqls) {
                            stmt.addBatch(sql);
                        }
                        int c = 0;
                        int[] cs = stmt.executeBatch();
                        stmt.close();
                        for (int cc : cs) {
                            c += cc;
                        }
                        conn.commit();
                        slowLog(s, sqls);
                        return c;
                    } catch (SQLException se) {
                        throw se;
                    }
                } else {
                    throw e;
                }
            }
            throw e;
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDBAsync(EntityInfo<T> info, final String[] tables, FilterNode node, String... sqls) {
        return supplyAsync(() -> clearTableDB(info, tables, node, sqls));
    }

    @Override
    protected <T> int clearTableDB(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c;
            if (sqls.length == 1) {
                final Statement stmt = conn.createStatement();
                int c1 = stmt.executeUpdate(sqls[0]);
                stmt.close();
                c = c1;
            } else {
                final Statement stmt = conn.createStatement();
                for (String sql : sqls) {
                    stmt.addBatch(sql);
                }
                int c1 = 0;
                int[] cs = stmt.executeBatch();
                stmt.close();
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
            }
            conn.commit();
            slowLog(s, sqls);
            return c;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException se) {
            }
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //单表结构不存在
                    return 0;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return 0;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "clearTable, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return 0;
                    }
                    sqls = clearTableSql(info, tables, node);
                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                        logger.finest(info.getType().getSimpleName() + " clearTable sql=" + Arrays.toString(sqls));
                    }
                    try {
                        final Statement stmt = conn.createStatement();
                        for (String sql : sqls) {
                            stmt.addBatch(sql);
                        }
                        int c = 0;
                        int[] cs = stmt.executeBatch();
                        stmt.close();
                        for (int cc : cs) {
                            c += cc;
                        }
                        conn.commit();
                        slowLog(s, sqls);
                        return c;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                } else {
                    throw new SourceException(e);
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> createTableDBAsync(EntityInfo<T> info, String copyTableSql, final Serializable pk, String... sqls) {
        return supplyAsync(() -> createTableDB(info, copyTableSql, pk, sqls));
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDBAsync(EntityInfo<T> info, final String[] tables, FilterNode node, String... sqls) {
        return supplyAsync(() -> dropTableDB(info, tables, node, sqls));
    }

    @Override
    protected <T> int createTableDB(EntityInfo<T> info, String copyTableSql, Serializable pk, String... sqls) {
        Connection conn = null;
        Statement stmt;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c;
            if (copyTableSql == null) {
                if (sqls.length == 1) {
                    stmt = conn.createStatement();
                    int c1 = stmt.executeUpdate(sqls[0]);
                    stmt.close();
                    c = c1;
                } else {
                    stmt = conn.createStatement();
                    for (String sql : sqls) {
                        stmt.addBatch(sql);
                    }
                    int c1 = 0;
                    int[] cs = stmt.executeBatch();
                    stmt.close();
                    for (int cc : cs) {
                        c1 += cc;
                    }
                    c = c1;
                }
            } else { //建分表
                try {
                    stmt = conn.createStatement();
                    c = stmt.executeUpdate(copyTableSql);
                } catch (SQLException se) {
                    if (isTableNotExist(info, se.getSQLState())) { //分表的原始表不存在
                        final String newTable = info.getTable(pk);
                        if (newTable.indexOf('.') <= 0) { //分表的原始表不存在
                            if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                                logger.finest(info.getType().getSimpleName() + " createTable sql=" + Arrays.toString(sqls));
                            }
                            //创建原始表
                            stmt = conn.createStatement();
                            if (sqls.length == 1) {
                                stmt.execute(sqls[0]);
                            } else {
                                for (String tableSql : sqls) {
                                    stmt.addBatch(tableSql);
                                }
                                stmt.executeBatch();
                            }
                            stmt.close();
                            //再执行一遍创建分表操作
                            if (info.isLoggable(logger, Level.FINEST, copyTableSql)) {
                                logger.finest(info.getType().getSimpleName() + " createTable sql=" + copyTableSql);
                            }
                            stmt = conn.createStatement();
                            c = stmt.executeUpdate(copyTableSql);
                            stmt.close();

                        } else { //需要先建库
                            String newCatalog = newTable.substring(0, newTable.indexOf('.'));
                            String catalogSql = ("postgresql".equals(dbtype()) ? "CREATE SCHEMA IF NOT EXISTS " : "CREATE DATABASE IF NOT EXISTS ") + newCatalog;
                            try {
                                if (info.isLoggable(logger, Level.FINEST, catalogSql)) {
                                    logger.finest(info.getType().getSimpleName() + " createCatalog sql=" + catalogSql);
                                }
                                stmt = conn.createStatement();
                                stmt.executeUpdate(catalogSql);
                                stmt.close();
                            } catch (SQLException sqle1) {
                                logger.log(Level.SEVERE, "create database " + copyTableSql + " error", sqle1);
                            }
                            try {
                                //再执行一遍创建分表操作
                                if (info.isLoggable(logger, Level.FINEST, copyTableSql)) {
                                    logger.finest(info.getType().getSimpleName() + " createTable sql=" + copyTableSql);
                                }
                                stmt = conn.createStatement();
                                c = stmt.executeUpdate(copyTableSql);
                                stmt.close();
                            } catch (SQLException sqle2) {
                                if (isTableNotExist(info, sqle2.getSQLState())) {
                                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                                        logger.finest(info.getType().getSimpleName() + " createTable sql=" + Arrays.toString(sqls));
                                    }
                                    //创建原始表
                                    stmt = conn.createStatement();
                                    if (sqls.length == 1) {
                                        stmt.execute(sqls[0]);
                                    } else {
                                        for (String tableSql : sqls) {
                                            stmt.addBatch(tableSql);
                                        }
                                        stmt.executeBatch();
                                    }
                                    stmt.close();
                                    //再执行一遍创建分表操作
                                    if (info.isLoggable(logger, Level.FINEST, copyTableSql)) {
                                        logger.finest(info.getType().getSimpleName() + " createTable sql=" + copyTableSql);
                                    }
                                    stmt = conn.createStatement();
                                    c = stmt.executeUpdate(copyTableSql);
                                    stmt.close();
                                } else {
                                    throw new SourceException(sqle2);
                                }
                            }
                        }
                    }
                    throw new SourceException(se);
                }
            }
            conn.commit();
            slowLog(s, sqls);
            return c;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> int dropTableDB(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c;
            if (sqls.length == 1) {
                final Statement stmt = conn.createStatement();
                int c1 = stmt.executeUpdate(sqls[0]);
                stmt.close();
                c = c1;
            } else {
                final Statement stmt = conn.createStatement();
                for (String sql : sqls) {
                    stmt.addBatch(sql);
                }
                int c1 = 0;
                int[] cs = stmt.executeBatch();
                stmt.close();
                for (int cc : cs) {
                    c1 += cc;
                }
                c = c1;
            }
            conn.commit();
            slowLog(s, sqls);
            return c;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException se) {
            }
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //单表结构不存在
                    return 0;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return 0;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "dropTable, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return 0;
                    }
                    sqls = dropTableSql(info, tables, node);
                    if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                        logger.finest(info.getType().getSimpleName() + " dropTable sql=" + Arrays.toString(sqls));
                    }
                    try {
                        final Statement stmt = conn.createStatement();
                        for (String sql : sqls) {
                            stmt.addBatch(sql);
                        }
                        int c = 0;
                        int[] cs = stmt.executeBatch();
                        stmt.close();
                        for (int cc : cs) {
                            c += cc;
                        }
                        conn.commit();
                        slowLog(s, sqls);
                        return c;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                } else {
                    throw new SourceException(e);
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Integer> updateEntityDBAsync(EntityInfo<T> info, T... entitys) {
        return supplyAsync(() -> updateEntityDB(info, entitys));
    }

    @Override
    protected <T> int updateEntityDB(EntityInfo<T> info, T... entitys) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c = updateEntityDB(false, conn, info, entitys);
            conn.commit();
            return c;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    private <T> int updateEntityDB(final boolean batch, final Connection conn, final EntityInfo<T> info, T... entitys) throws SQLException {
        final long s = System.currentTimeMillis();
        String presql = null;
        PreparedStatement prestmt = null;
        List<PreparedStatement> prestmts = null;
        Map<String, PrepareInfo<T>> prepareInfos = null;
        int c = -1;
        final Attribute<T, Serializable>[] attrs = info.updateAttributes;
        try {
            if (info.getTableStrategy() == null) {
                presql = info.getUpdateQuestionPrepareSQL(entitys[0]);
                prestmt = createUpdatePreparedStatement(conn, presql, info, entitys);
                int c1 = 0;
                int[] pc = prestmt.executeBatch();
                for (int p : pc) {
                    if (p >= 0) {
                        c1 += p;
                    }
                }
                c = c1;
                prestmt.close();
            } else {
                prepareInfos = getUpdateQuestionPrepareInfo(info, entitys);
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
            if (!batch) {
                conn.commit();
            }
        } catch (SQLException se) {
            if (!batch) {
                conn.rollback();
            }
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
                    //表不存在，更新条数为0
                    return 0;
                } else {
                    //String tableName = parseNotExistTableName(se);
                    if (prepareInfos == null) {
                        throw se;
                    }
                    for (PreparedStatement stmt : prestmts) {
                        stmt.close();
                    }

                    String[] oldTables = prepareInfos.keySet().toArray(new String[prepareInfos.size()]);
                    List<String> notExistTables = checkNotExistTables(conn, oldTables);
                    if (notExistTables.isEmpty()) {
                        throw se;
                    }
                    for (String t : notExistTables) {
                        prepareInfos.remove(t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "update entitys, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + prepareInfos.keySet());
                    }
                    if (prepareInfos.isEmpty()) { //分表全部不存在
                        return 0;
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
                    conn.commit();
                }
            } else {
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
                    if (info.isLoggable(logger, Level.FINEST, debugsql)) {
                        logger.finest(info.getType().getSimpleName() + " update sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                    }
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
                        if (info.isLoggable(logger, Level.FINEST, debugsql)) {
                            logger.finest(info.getType().getSimpleName() + " update sql=" + debugsql.replaceAll("(\r|\n)", "\\n"));
                        }
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
        return c;
    }

    @Override
    protected <T> CompletableFuture<Integer> updateColumnDBAsync(EntityInfo<T> info, Flipper flipper, UpdateSqlInfo sql) {
        return supplyAsync(() -> updateColumnDB(info, flipper, sql));
    }

    @Override
    protected <T> int updateColumnDB(EntityInfo<T> info, Flipper flipper, UpdateSqlInfo sql) {
        Connection conn = null;
        try {
            conn = writePool.pollConnection();
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            int c = updateColumnDB(false, conn, info, flipper, sql);
            conn.commit();
            return c;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException se) {
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
        }
    }

    private <T> int updateColumnDB(final boolean batch, final Connection conn, final EntityInfo<T> info, Flipper flipper, UpdateSqlInfo sql) throws SQLException { //String sql, boolean prepared, Object... blobs) {
        final long s = System.currentTimeMillis();
        int c = -1;
        String firstTable = null;
        try {
            if (sql.blobs != null || sql.tables != null) {
                if (sql.tables == null) {
                    final PreparedStatement prestmt = conn.prepareStatement(sql.sql);
                    int index = 0;
                    for (byte[] param : sql.blobs) {
                        Blob blob = conn.createBlob();
                        blob.setBytes(1, param);
                        prestmt.setBlob(++index, blob);
                    }
                    if (info.isLoggable(logger, Level.FINEST, sql.sql)) {
                        logger.finest(info.getType().getSimpleName() + " updateColumn sql=" + sql);
                    }
                    c = prestmt.executeUpdate();
                    prestmt.close();
                    if (!batch) {
                        conn.commit();
                    }
                    slowLog(s, sql.sql);
                    return c;
                } else {
                    firstTable = sql.tables[0];
                    List<PreparedStatement> prestmts = new ArrayList<>();
                    String[] sqls = new String[sql.tables.length];
                    for (int i = 0; i < sql.tables.length; i++) {
                        sqls[i] = i == 0 ? sql.sql : sql.sql.replaceFirst(firstTable, sql.tables[i]);
                        PreparedStatement prestmt = conn.prepareStatement(sqls[i]);
                        int index = 0;
                        if (sql.blobs != null) {
                            for (byte[] param : sql.blobs) {
                                Blob blob = conn.createBlob();
                                blob.setBytes(1, param);
                                prestmt.setBlob(++index, blob);
                            }
                        }
                        prestmt.addBatch();
                        prestmts.add(prestmt);
                    }
                    if (info.isLoggable(logger, Level.FINEST, sql.sql)) {
                        logger.finest(info.getType().getSimpleName() + " updateColumn sql=" + Arrays.toString(sqls));
                    }
                    int c1 = 0;
                    for (PreparedStatement prestmt : prestmts) {
                        int[] cs = prestmt.executeBatch();
                        for (int cc : cs) {
                            c1 += cc;
                        }
                        prestmt.close();
                    }
                    c = c1;
                    if (!batch) {
                        conn.commit();
                    }
                    slowLog(s, sqls);
                }
                return c;
            } else {
                if (info.isLoggable(logger, Level.FINEST, sql.sql)) {
                    logger.finest(info.getType().getSimpleName() + " updateColumn sql=" + sql);
                }
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql.sql);
                stmt.close();
                if (!batch) {
                    conn.commit();
                }
                slowLog(s, sql.sql);
                return c;
            }
        } catch (SQLException se) {
            if (!batch) {
                conn.rollback();
            }
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
                    //表不存在，更新条数为0
                    return 0;
                } else if (sql.tables == null) {
                    //单一分表不存在
                    return 0;
                } else {
//                        String tableName = parseNotExistTableName(se);
//                        if (tableName == null) {
//                            throw se;
//                        }
                    String[] oldTables = sql.tables;
                    List<String> notExistTables = checkNotExistTables(conn, oldTables);
                    if (notExistTables.isEmpty()) {
                        throw se;
                    }
                    for (String t : notExistTables) {
                        sql.tables = Utility.remove(sql.tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "updateColumn, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(sql.tables));
                    }
                    if (sql.tables.length == 0) { //分表全部不存在
                        return 0;
                    }
                    List<PreparedStatement> prestmts = new ArrayList<>();
                    String[] sqls = new String[sql.tables.length];
                    for (int i = 0; i < sql.tables.length; i++) {
                        sqls[i] = sql.sql.replaceFirst(firstTable, sql.tables[i]);
                        PreparedStatement prestmt = conn.prepareStatement(sqls[i]);
                        int index = 0;
                        if (sql.blobs != null) {
                            for (byte[] param : sql.blobs) {
                                Blob blob = conn.createBlob();
                                blob.setBytes(1, param);
                                prestmt.setBlob(++index, blob);
                            }
                        }
                        prestmt.addBatch();
                        prestmts.add(prestmt);
                    }
                    if (info.isLoggable(logger, Level.FINEST, sql.sql)) {
                        logger.finest(info.getType().getSimpleName() + " updateColumn sql=" + Arrays.toString(sqls));
                    }
                    int c1 = 0;
                    for (PreparedStatement prestmt : prestmts) {
                        int[] cs = prestmt.executeBatch();
                        for (int cc : cs) {
                            c1 += cc;
                        }
                        prestmt.close();
                    }
                    c = c1;
                    if (!batch) {
                        conn.commit();
                    }
                    slowLog(s, sqls);
                    return c;
                }
            } else {
                throw se;
            }
        }
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBAsync(EntityInfo<T> info, String[] tables, String sql, FilterNode node, FilterFuncColumn... columns) {
        return supplyAsync(() -> getNumberMapDB(info, tables, sql, node, columns));
    }

    @Override
    protected <T, N extends Number> Map<String, N> getNumberMapDB(EntityInfo<T> info, String[] tables, String sql, FilterNode node, FilterFuncColumn... columns) {
        Connection conn = null;
        final Map map = new HashMap<>();
        final long s = System.currentTimeMillis();
        Statement stmt = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            stmt = conn.createStatement();
            ResultSet set = stmt.executeQuery(sql);
            if (set.next()) {
                int index = 0;
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        Object o = set.getObject(++index);
                        Number rs = ffc.getDefvalue();
                        if (o != null) {
                            rs = (Number) o;
                        }
                        map.put(ffc.col(col), rs);
                    }
                }
            }
            set.close();
            stmt.close();
            slowLog(s, sql);
            return map;
        } catch (SQLException e) {
            map.clear();
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return map;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return map;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "getNumberMap, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return map;
                    }

                    //重新查询一次
                    try {
                        sql = getNumberMapSql(info, tables, node, columns);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " getNumberMap sql=" + sql);
                        }
                        if (stmt != null) {
                            stmt.close();
                        }
                        stmt = conn.createStatement();
                        ResultSet set = stmt.executeQuery(sql);
                        if (set.next()) {
                            int index = 0;
                            for (FilterFuncColumn ffc : columns) {
                                for (String col : ffc.cols()) {
                                    Object o = set.getObject(++index);
                                    Number rs = ffc.getDefvalue();
                                    if (o != null) {
                                        rs = (Number) o;
                                    }
                                    map.put(ffc.col(col), rs);
                                }
                            }
                        }
                        set.close();
                        stmt.close();
                        slowLog(s, sql);
                        return map;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDBAsync(EntityInfo<T> info, String[] tables, String sql, FilterFunc func, Number defVal, String column, FilterNode node) {
        return supplyAsync(() -> getNumberResultDB(info, tables, sql, func, defVal, column, node));
    }

    @Override
    protected <T> Number getNumberResultDB(EntityInfo<T> info, String[] tables, String sql, FilterFunc func, Number defVal, String column, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        Statement stmt = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            stmt = conn.createStatement();
            Number rs = defVal;
            ResultSet set = stmt.executeQuery(sql);
            if (set.next()) {
                Object o = set.getObject(1);
                if (o != null) {
                    rs = (Number) o;
                }
            }
            set.close();
            stmt.close();
            slowLog(s, sql);
            return rs;
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return defVal;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return defVal;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "getNumberResult, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return defVal;
                    }

                    //重新查询一次
                    try {
                        sql = getNumberResultSql(info, info.getType(), tables, func, defVal, column, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " getNumberResult sql=" + sql);
                        }
                        if (stmt != null) {
                            stmt.close();
                        }
                        stmt = conn.createStatement();
                        Number rs = defVal;
                        ResultSet set = stmt.executeQuery(sql);
                        if (set.next()) {
                            Object o = set.getObject(1);
                            if (o != null) {
                                rs = (Number) o;
                            }
                        }
                        set.close();
                        stmt.close();
                        slowLog(s, sql);
                        return rs;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBAsync(EntityInfo<T> info, String[] tables, String sql, String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
        return supplyAsync(() -> queryColumnMapDB(info, tables, sql, keyColumn, func, funcColumn, node));
    }

    @Override
    protected <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMapDB(EntityInfo<T> info, String[] tables, String sql, String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        Map<K, N> rs = new LinkedHashMap<>();
        Statement stmt = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            stmt = conn.createStatement();
            ResultSet set = stmt.executeQuery(sql);
            ResultSetMetaData rsd = set.getMetaData();
            boolean smallint = rsd == null ? false : rsd.getColumnType(1) == Types.SMALLINT;
            while (set.next()) {
                rs.put((K) (smallint ? set.getShort(1) : set.getObject(1)), (N) set.getObject(2));
            }
            set.close();
            stmt.close();
            slowLog(s, sql);
            return rs;
        } catch (SQLException e) {
            rs.clear();
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return rs;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return rs;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "queryColumnMap, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return rs;
                    }

                    //重新查询一次
                    try {
                        sql = queryColumnMapSql(info, tables, keyColumn, func, funcColumn, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
                        }
                        if (stmt != null) {
                            stmt.close();
                        }
                        stmt = conn.createStatement();
                        ResultSet set = stmt.executeQuery(sql);
                        ResultSetMetaData rsd = set.getMetaData();
                        boolean smallint = rsd == null ? false : rsd.getColumnType(1) == Types.SMALLINT;
                        while (set.next()) {
                            rs.put((K) (smallint ? set.getShort(1) : set.getObject(1)), (N) set.getObject(2));
                        }
                        set.close();
                        stmt.close();
                        slowLog(s, sql);
                        return rs;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDBAsync(EntityInfo<T> info, String[] tables, String sql, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterNode node) {
        return supplyAsync(() -> queryColumnMapDB(info, tables, sql, funcNodes, groupByColumns, node));
    }

    @Override
    protected <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMapDB(EntityInfo<T> info, String[] tables, String sql, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterNode node) {
        Connection conn = null;
        Map rs = new LinkedHashMap<>();
        final long s = System.currentTimeMillis();
        Statement stmt = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            stmt = conn.createStatement();
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
            return rs;
        } catch (SQLException e) {
            rs.clear();
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return rs;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return rs;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "queryColumnMap, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return rs;
                    }

                    //重新查询一次
                    try {
                        sql = queryColumnMapSql(info, tables, funcNodes, groupByColumns, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
                        }
                        if (stmt != null) {
                            stmt.close();
                        }
                        stmt = conn.createStatement();
                        ResultSet set = stmt.executeQuery(sql);
                        ResultSetMetaData rsd = set.getMetaData();
                        boolean smallint = rsd == null ? false : rsd.getColumnType(1) == Types.SMALLINT;
                        while (set.next()) {
                            rs.put((K) (smallint ? set.getShort(1) : set.getObject(1)), (N) set.getObject(2));
                        }
                        set.close();
                        stmt.close();
                        slowLog(s, sql);
                        return rs;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<T> findDBAsync(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, SelectColumn selects, Serializable pk, FilterNode node) {
        return supplyAsync(() -> findDB(info, tables, sql, onlypk, selects, pk, node));
    }

    @Override
    protected <T> T findDB(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, SelectColumn selects, Serializable pk, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        PreparedStatement ps = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final DataResultSet set = createDataResultSet(info, ps.executeQuery());
            T rs = set.next() ? selects == null ? info.getFullEntityValue(set) : info.getEntityValue(selects, set) : null;
            set.close();
            ps.close();
            slowLog(s, sql);
            return rs;
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return null;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return null;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "find, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return null;
                    }

                    //重新查询一次
                    try {
                        sql = findSql(info, tables, selects, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
                        }
                        if (ps != null) {
                            ps.close();
                        }
                        ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        ps.setFetchSize(1);
                        final DataResultSet set = createDataResultSet(info, ps.executeQuery());
                        T rs = set.next() ? selects == null ? info.getFullEntityValue(set) : info.getEntityValue(selects, set) : null;
                        set.close();
                        ps.close();
                        slowLog(s, sql);
                        return rs;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDBAsync(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk, String column, Serializable defValue, Serializable pk, FilterNode node) {
        return supplyAsync(() -> findColumnDB(info, tables, sql, onlypk, column, defValue, pk, node));
    }

    @Override
    protected <T> Serializable findColumnDB(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, String column, Serializable defValue, Serializable pk, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        PreparedStatement ps = null;
        final Attribute<T, Serializable> attr = info.getAttribute(column);
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(1);
            final DataResultSet set = createDataResultSet(info, ps.executeQuery());
            Serializable val = defValue;
            if (set.next()) {
                val = info.getFieldValue(attr, set, 1);
            }
            set.close();
            ps.close();
            slowLog(s, sql);
            return val == null ? defValue : val;
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return defValue;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return defValue;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "findColumn, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return defValue;
                    }

                    //重新查询一次
                    try {
                        sql = findColumnSql(info, tables, column, defValue, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " findColumn sql=" + sql);
                        }
                        if (ps != null) {
                            ps.close();
                        }
                        ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        ps.setFetchSize(1);
                        final DataResultSet set = createDataResultSet(info, ps.executeQuery());
                        Serializable val = defValue;
                        if (set.next()) {
                            val = info.getFieldValue(attr, set, 1);
                        }
                        set.close();
                        ps.close();
                        slowLog(s, sql);
                        return val == null ? defValue : val;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDBAsync(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk, Serializable pk, FilterNode node) {
        return supplyAsync(() -> existsDB(info, tables, sql, onlypk, pk, node));
    }

    @Override
    protected <T> boolean existsDB(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, Serializable pk, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        PreparedStatement ps = null;
        try {
            conn = readPool.pollConnection();
            //conn.setReadOnly(true);
            ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            if (info.isLoggable(logger, Level.FINEST, sql)) {
                logger.finest(info.getType().getSimpleName() + " exists (" + rs + ") sql=" + sql);
            }
            slowLog(s, sql);
            return rs;
        } catch (SQLException e) {
            if (isTableNotExist(info, e.getSQLState())) {
                if (info.getTableStrategy() == null) {
                    //读操作不自动创建表，可能存在读写分离
                    return false;
                } else if (tables != null && tables.length == 1) {
                    //只查一个不存在的分表
                    return false;
                } else if (tables != null && tables.length > 1) {
                    //多分表查询中一个或多个分表不存在
//                    String tableName = parseNotExistTableName(e);
//                    if (tableName == null) {
//                        throw new SourceException(e);
//                    }
                    String[] oldTables = tables;
                    List<String> notExistTables = checkNotExistTablesNoThrows(conn, tables);
                    if (notExistTables.isEmpty()) {
                        throw new SourceException(e);
                    }
                    for (String t : notExistTables) {
                        tables = Utility.remove(tables, t);
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "exists, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                    }
                    if (tables.length == 0) { //分表全部不存在
                        return false;
                    }

                    //重新查询一次
                    try {
                        sql = existsSql(info, tables, node);
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
                        }
                        if (ps != null) {
                            ps.close();
                        }
                        ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        final ResultSet set = ps.executeQuery();
                        boolean rs = set.next() ? (set.getInt(1) > 0) : false;
                        set.close();
                        ps.close();
                        if (info.isLoggable(logger, Level.FINEST, sql)) {
                            logger.finest(info.getType().getSimpleName() + " exists (" + rs + ") sql=" + sql);
                        }
                        slowLog(s, sql);
                        return rs;
                    } catch (SQLException se) {
                        throw new SourceException(se);
                    }
                }
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDBAsync(EntityInfo<T> info, final boolean readCache, boolean needTotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        return supplyAsync(() -> querySheetDB(info, readCache, needTotal, distinct, selects, flipper, node));
    }

    @Override
    protected <T> Sheet<T> querySheetDB(EntityInfo<T> info, final boolean readCache, boolean needTotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        Connection conn = null;
        final long s = System.currentTimeMillis();
        final SelectColumn sels = selects;
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String[] tables = info.getTables(node);
        final String joinAndWhere = (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        final boolean mysqlOrPgsql = "mysql".equals(dbtype()) || "postgresql".equals(dbtype());
        try {
            conn = readPool.pollConnection();
            String[] sqls = createSheetListAndCountSql(info, readCache, needTotal, distinct, selects, flipper, mysqlOrPgsql, tables, joinAndWhere);
            String listSql = sqls[0];
            String countSql = sqls[1];
            try {
                return executeQuerySheet(info, needTotal, flipper, sels, s, conn, mysqlOrPgsql, listSql, countSql);
            } catch (SQLException se) {
                if (isTableNotExist(info, se.getSQLState())) {
                    if (info.getTableStrategy() == null) {
                        //读操作不自动创建表，可能存在读写分离
                        return new Sheet<>(0, new ArrayList());
                    } else if (tables != null && tables.length == 1) {
                        //只查一个不存在的分表
                        return new Sheet<>(0, new ArrayList());
                    } else if (tables != null && tables.length > 1) {
                        //多分表查询中一个或多个分表不存在
//                        String tableName = parseNotExistTableName(se);
//                        if (tableName == null) {
//                            throw new SourceException(se);
//                        }
                        String[] oldTables = tables;
                        List<String> notExistTables = checkNotExistTables(conn, tables);
                        if (notExistTables.isEmpty()) {
                            throw new SourceException(se);
                        }
                        for (String t : notExistTables) {
                            tables = Utility.remove(tables, t);
                        }
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "querySheet, old-tables: " + Arrays.toString(oldTables) + ", new-tables: " + Arrays.toString(tables));
                        }
                        if (tables.length == 0) { //分表全部不存在
                            return new Sheet<>(0, new ArrayList());
                        }
                        if (tables.length == oldTables.length) { //没有变化, 不异常会陷入死循环
                            throw new SourceException(se);
                        }

                        //重新查询一次
                        sqls = createSheetListAndCountSql(info, readCache, needTotal, distinct, selects, flipper, mysqlOrPgsql, tables, joinAndWhere);
                        listSql = sqls[0];
                        countSql = sqls[1];
                        return executeQuerySheet(info, needTotal, flipper, sels, s, conn, mysqlOrPgsql, listSql, countSql);
                    } else {
                        throw new SourceException(se);
                    }
                }
                throw new SourceException(se);
            }
        } catch (SourceException se) {
            throw se;
        } catch (Exception e) {
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                readPool.offerConnection(conn);
            }
        }
    }

    private <T> Sheet<T> executeQuerySheet(EntityInfo<T> info, boolean needTotal, Flipper flipper, SelectColumn sels,
        long s, Connection conn, boolean mysqlOrPgsql, String listSql, String countSql) throws SQLException {
        final List<T> list = new ArrayList();
        if (mysqlOrPgsql) {  //sql可以带limit、offset
            PreparedStatement ps = conn.prepareStatement(listSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
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
                if (set.next()) {
                    total = set.getLong(1);
                }
                set.close();
                ps.close();
            }
            slowLog(s, listSql);
            return new Sheet<>(total, list);
        } else {
            //conn.setReadOnly(true);
            PreparedStatement ps = conn.prepareStatement(listSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            if (flipper != null && flipper.getLimit() > 0) {
                ps.setFetchSize(flipper.getLimit());
            }
            ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.getOffset() > 0) {
                set.absolute(flipper.getOffset());
            }
            final int limit = flipper == null || flipper.getLimit() < 1 ? Integer.MAX_VALUE : flipper.getLimit();
            int i = 0;
            final DataResultSet rr = createDataResultSet(info, set);
            if (sels == null) {
                while (set.next()) {
                    i++;
                    list.add(info.getFullEntityValue(rr));
                    if (limit <= i) {
                        break;
                    }
                }
            } else {
                while (set.next()) {
                    i++;
                    list.add(info.getEntityValue(sels, rr));
                    if (limit <= i) {
                        break;
                    }
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
            return new Sheet<>(total, list);
        }
    }

    private <T> String[] createSheetListAndCountSql(EntityInfo<T> info, final boolean readCache, boolean needTotal,
        final boolean distinct, SelectColumn selects, Flipper flipper, boolean mysqlOrPgsql, String[] tables, String joinAndWhere) {
        String listSql = null;
        String countSql = null;
        {  //组装listSql、countSql
            String listSubSql;
            StringBuilder union = new StringBuilder();
            if (tables.length == 1) {
                listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getQueryColumns("a", selects) + " FROM " + tables[0] + " a" + joinAndWhere;
            } else {
                int b = 0;
                for (String table : tables) {
                    if (union.length() > 0) {
                        union.append(" UNION ALL ");
                    }
                    union.append("SELECT ").append(info.getQueryColumns("a", selects)).append(" FROM ").append(table).append(" a").append(joinAndWhere);
                }
                listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getQueryColumns("a", selects) + " FROM (" + (union) + ") a";
            }
            listSql = listSubSql + createSQLOrderby(info, flipper);
            if (mysqlOrPgsql) {
                listSql += (flipper == null || flipper.getLimit() < 1 ? "" : (" LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset()));
                if (readCache && info.isLoggable(logger, Level.FINEST, listSql)) {
                    logger.finest(info.getType().getSimpleName() + " query sql=" + listSql);
                }
            } else {
                if (readCache && info.isLoggable(logger, Level.FINEST, listSql)) {
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
                if (readCache && info.isLoggable(logger, Level.FINEST, countSql)) {
                    logger.finest(info.getType().getSimpleName() + " querySheet countsql=" + countSql);
                }
            }
        }
        return new String[]{listSql, countSql};
    }

    protected List<String> checkNotExistTablesNoThrows(Connection conn, String[] tables) {
        try {
            return checkNotExistTables(conn, tables); //, firstNotExistTable
        } catch (SQLException e) {
            throw new SourceException(e);
        }
    }

    protected List<String> checkNotExistTables(Connection conn, String[] tables) throws SQLException { //, String firstNotExistTable
//        数据库不一定要按批量提交的SQL顺序执行， 所以第一个不存在的表不一定在tables的第一位, 
//        比如 DELETE FROM  table1; DELETE FROM  table2;  如果table1、table2都不存在，SQL可能会抛出table2不存在的异常
//        List<String> maybeNoTables = new ArrayList<>();
//        String minTableName = (firstNotExistTable.indexOf('.') > 0) ? firstNotExistTable.substring(firstNotExistTable.indexOf('.') + 1) : null;
//        for (String t : tables) {
//            if (!maybeNoTables.isEmpty()) {
//                maybeNoTables.add(t);
//            }
//            if (t.equals(firstNotExistTable) || (minTableName != null && t.equals(minTableName))) {
//                maybeNoTables.add(t);
//            }
//        }
//        if (maybeNoTables.isEmpty()) {
//            return maybeNoTables;
//        }

        String[] tableTypes = new String[]{"TABLE"};
        DatabaseMetaData dmd = conn.getMetaData();
        List<String> rs = new ArrayList<>();
        for (String t : tables) { //maybeNoTables
            String catalog = null;
            String table = t;
            int pos = t.indexOf('.');
            if (pos > 0) {
                catalog = t.substring(0, pos);
                table = t.substring(pos + 1);
            }
            ResultSet dmdrs = dmd.getTables(catalog, null, table, tableTypes);
            if (!dmdrs.next()) { //不存在表             
                rs.add(t);
            }
            dmdrs.close();
        }
        return rs;
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
        if (sqls.length == 0) {
            return new int[0];
        }
        final long s = System.currentTimeMillis();
        Connection conn = writePool.pollConnection();
        try {
            conn.setReadOnly(false);
            conn.setAutoCommit(false);
            final Statement stmt = conn.createStatement();
            final int[] rs = new int[sqls.length];
            int i = -1;
            for (String sql : sqls) {
                rs[++i] = stmt.execute(sql) ? 1 : 0;
            }
            stmt.close();
            conn.commit();
            slowLog(s, sqls);
            return rs;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException se) {
            }
            throw new SourceException(e);
        } finally {
            if (conn != null) {
                writePool.offerConnection(conn);
            }
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
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("direct query sql=" + sql);
            }
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
            if (conn != null) {
                readPool.offerConnection(conn);
            }
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

                    if (val
                        == null) {
                        return null;
                    }
                    return new java.util.Date(
                        ((java.sql.Date) val).getTime());
                } else if (t == java.time.LocalDate.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);

                    if (val
                        == null) {
                        return null;
                    }
                    return ((java.sql.Date) val).toLocalDate();
                } else if (t == java.time.LocalTime.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);

                    if (val
                        == null) {
                        return null;
                    }
                    return ((java.sql.Time) val).toLocalTime();
                } else if (t == java.time.LocalDateTime.class) {
                    Object val = index > 0 ? getObject(index) : getObject(column);

                    if (val
                        == null) {
                        return null;
                    }
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

        protected int maxConns;

        protected String url;

        protected int urlVersion;

        protected Properties clientInfo = new Properties();

        public ConnectionPool(Properties prop) {
            this.connectTimeoutSeconds = Integer.decode(prop.getProperty(DATA_SOURCE_CONNECTTIMEOUT_SECONDS, "6"));
            int defMaxConns = Utility.cpus() * 4;
            if (workExecutor instanceof ThreadPoolExecutor) {
                defMaxConns = ((ThreadPoolExecutor) workExecutor).getCorePoolSize();
            } else if (workExecutor instanceof ThreadHashExecutor) {
                defMaxConns = ((ThreadHashExecutor) workExecutor).getCorePoolSize();
            } else if (workExecutor != null) { //maybe virtual thread pool
                defMaxConns = Math.min(1000, Utility.cpus() * 100);
            }
            this.maxConns = Math.max(1, Integer.decode(prop.getProperty(DATA_SOURCE_MAXCONNS, "" + defMaxConns)));
            this.queue = new ArrayBlockingQueue<>(maxConns);
            this.url = prop.getProperty(DATA_SOURCE_URL);
            String username = prop.getProperty(DATA_SOURCE_USER, "");
            String password = prop.getProperty(DATA_SOURCE_PASSWORD, "");
            this.connectAttrs = new Properties();
            if (username != null) {
                this.connectAttrs.put("user", username);
            }
            if (password != null) {
                this.connectAttrs.put("password", password);
            }
            try {
                this.driver = DriverManager.getDriver(this.url);
            } catch (SQLException e) {
                throw new SourceException(e);
            }
            clientInfo.put("version", String.valueOf(urlVersion));
        }

        @ResourceListener
        public void onResourceChange(ResourceEvent[] events) {
            String newUrl = this.url;
            int newConnectTimeoutSeconds = this.connectTimeoutSeconds;
            int newMaxconns = this.maxConns;
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
            if (newMaxconns != this.maxConns) {
                ArrayBlockingQueue<Connection> newQueue = new ArrayBlockingQueue<>(newMaxconns);
                ArrayBlockingQueue<Connection> oldQueue = this.queue;
                this.queue = newQueue;
                this.maxConns = newMaxconns;
                Connection conn;
                while ((conn = oldQueue.poll()) != null) {
                    offerConnection(conn);
                }
            }
        }

        public Connection pollConnection() {
            Connection conn = queue.poll();
            if (conn == null) {
                if (usingCounter.intValue() >= maxConns) {
                    try {
                        conn = queue.poll(connectTimeoutSeconds, TimeUnit.SECONDS);
                    } catch (InterruptedException t) {
                        logger.log(Level.WARNING, "take pooled connection error", t);
                    }
                    if (conn == null) {
                        throw new SourceException("create pooled connection timeout");
                    }
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
            if (conn == null) {
                return;
            }
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
                if (!rs) {
                    return rs;
                }
                Properties prop = conn.getClientInfo();
                if (prop == null) {
                    return false;
                }
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
