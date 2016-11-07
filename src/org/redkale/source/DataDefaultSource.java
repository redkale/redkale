/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
import javax.sql.ConnectionPoolDataSource;
import javax.xml.stream.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class DataDefaultSource implements DataSource, Function<Class, EntityInfo>, AutoCloseable {

    public static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    static final String JDBC_CONNECTIONSMAX = "javax.persistence.connections.limit";

    static final String JDBC_CONTAIN_SQLTEMPLATE = "javax.persistence.contain.sqltemplate";

    static final String JDBC_NOTCONTAIN_SQLTEMPLATE = "javax.persistence.notcontain.sqltemplate";

    static final String JDBC_TABLENOTEXIST_SQLSTATES = "javax.persistence.tablenotexist.sqlstates";

    static final String JDBC_TABLECOPY_SQLTEMPLATE = "javax.persistence.tablecopy.sqltemplate";

    static final String JDBC_URL = "javax.persistence.jdbc.url";

    static final String JDBC_USER = "javax.persistence.jdbc.user";

    static final String JDBC_PWD = "javax.persistence.jdbc.password";

    static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";

    static final String JDBC_SOURCE = "javax.persistence.jdbc.source";

    private static final Flipper FLIPPER_ONE = new Flipper(1);

    final Logger logger = Logger.getLogger(DataDefaultSource.class.getSimpleName());

    final AtomicBoolean debug = new AtomicBoolean(logger.isLoggable(Level.FINEST));

    final String name;

    final URL conf;

    final boolean cacheForbidden;

    private final JDBCPoolSource readPool;

    private final JDBCPoolSource writePool;

    @Resource(name = "property.datasource.nodeid")
    private int nodeid;

    @Resource(name = "$")
    private DataCacheListener cacheListener;

    private final BiFunction<DataSource, Class, List> fullloader = (s, t) -> querySheet(false, false, t, null, null, (FilterNode) null).list(true);

    public DataDefaultSource() throws IOException {
        this("");
    }

    public DataDefaultSource(final String unitName) throws IOException {
        this(unitName, System.getProperty(DATASOURCE_CONFPATH) == null
            ? DataDefaultSource.class.getResource("/META-INF/persistence.xml")
            : new File(System.getProperty(DATASOURCE_CONFPATH)).toURI().toURL());
    }

    public DataDefaultSource(final String unitName, URL url) throws IOException {
        if (url == null) url = this.getClass().getResource("/persistence.xml");
        InputStream in = url.openStream();
        Map<String, Properties> map = loadProperties(in);
        Properties readprop = null;
        Properties writeprop = null;
        if (unitName != null) {
            readprop = map.get(unitName);
            writeprop = readprop;
            if (readprop == null) {
                readprop = map.get(unitName + ".read");
                writeprop = map.get(unitName + ".write");
            }
        }
        if ((unitName == null || unitName.isEmpty()) || readprop == null) {
            String key = null;
            for (Map.Entry<String, Properties> en : map.entrySet()) {
                key = en.getKey();
                readprop = en.getValue();
                writeprop = readprop;
                break;
            }
            if (key != null && (key.endsWith(".read") || key.endsWith(".write"))) {
                if (key.endsWith(".read")) {
                    writeprop = map.get(key.substring(0, key.lastIndexOf('.')) + ".write");
                } else {
                    readprop = map.get(key.substring(0, key.lastIndexOf('.')) + ".read");
                }
            }
        }
        if (readprop == null) throw new RuntimeException("not found persistence properties (unit:" + unitName + ")");
        this.name = unitName;
        this.conf = url;
        this.readPool = new JDBCPoolSource(this, "read", readprop);
        this.writePool = new JDBCPoolSource(this, "write", writeprop);
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty("shared-cache-mode"));
    }

    public DataDefaultSource(String unitName, Properties readprop, Properties writeprop) {
        this.name = unitName;
        this.conf = null;
        this.readPool = new JDBCPoolSource(this, "read", readprop);
        this.writePool = new JDBCPoolSource(this, "write", writeprop);
        this.cacheForbidden = "NONE".equalsIgnoreCase(readprop.getProperty("shared-cache-mode"));
    }

    public static Map<String, DataDefaultSource> create(final InputStream in) {
        Map<String, Properties> map = loadProperties(in);
        Map<String, Properties[]> maps = new HashMap<>();
        map.entrySet().stream().forEach((en) -> {
            if (en.getKey().endsWith(".read") || en.getKey().endsWith(".write")) {
                String key = en.getKey().substring(0, en.getKey().lastIndexOf('.'));
                if (maps.containsKey(key)) return;
                boolean read = en.getKey().endsWith(".read");
                Properties rp = read ? en.getValue() : map.get(key + ".read");
                Properties wp = read ? map.get(key + ".write") : en.getValue();
                maps.put(key, new Properties[]{rp, wp});
            } else {
                maps.put(en.getKey(), new Properties[]{en.getValue(), en.getValue()});
            }
        });
        Map<String, DataDefaultSource> result = new HashMap<>();
        maps.entrySet().stream().forEach((en) -> {
            result.put(en.getKey(), new DataDefaultSource(en.getKey(), en.getValue()[0], en.getValue()[1]));
        });
        return result;
    }

    static Map<String, Properties> loadProperties(final InputStream in0) {
        final Map<String, Properties> map = new LinkedHashMap();
        Properties result = new Properties();
        boolean flag = false;
        try (final InputStream in = in0) {
            XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("persistence-unit".equalsIgnoreCase(reader.getLocalName())) {
                        if (!result.isEmpty()) result = new Properties();
                        map.put(reader.getAttributeValue(null, "name"), result);
                        flag = true;
                    } else if (flag && "property".equalsIgnoreCase(reader.getLocalName())) {
                        String name = reader.getAttributeValue(null, "name");
                        String value = reader.getAttributeValue(null, "value");
                        if (name == null) continue;
                        result.put(name, value);
                    } else if (flag && "shared-cache-mode".equalsIgnoreCase(reader.getLocalName())) {
                        result.put(reader.getLocalName(), reader.getElementText());
                    }
                }
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

    static ConnectionPoolDataSource createDataSource(Properties property) {
        try {
            return createDataSource(property.getProperty(JDBC_SOURCE, property.getProperty(JDBC_DRIVER)),
                property.getProperty(JDBC_URL), property.getProperty(JDBC_USER), property.getProperty(JDBC_PWD));
        } catch (Exception ex) {
            throw new RuntimeException("(" + property + ") have no jdbc parameters", ex);
        }
    }

    static ConnectionPoolDataSource createDataSource(final String source0, String url, String user, String password) throws Exception {
        String source = source0;
        if (source0.contains("Driver")) {  //为了兼容JPA的配置文件
            switch (source0) {
                case "org.mariadb.jdbc.Driver":
                    source = "org.mariadb.jdbc.MySQLDataSource";
                    break;
                case "com.mysql.jdbc.Driver":
                    source = "com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource";
                    break;
                case "oracle.jdbc.driver.OracleDriver":
                    source = "oracle.jdbc.pool.OracleConnectionPoolDataSource";
                    break;
                case "org.postgresql.Driver":
                    source = "org.postgresql.ds.PGConnectionPoolDataSource";
                    break;
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    source = "com.microsoft.sqlserver.jdbc.SQLServerConnectionPoolDataSource";
                    break;
            }
        }
        final Class clazz = Class.forName(source);
        Object pdsource = clazz.newInstance();
        if (source.contains(".postgresql.")) {
            Class driver = Class.forName("org.postgresql.Driver");
            Properties properties = (Properties) driver.getMethod("parseURL", String.class, Properties.class).invoke(null, url, new Properties());
            clazz.getMethod("setServerName", String.class).invoke(pdsource, properties.getProperty("PGHOST"));
            clazz.getMethod("setDatabaseName", String.class).invoke(pdsource, properties.getProperty("PGDBNAME"));
            clazz.getMethod("setPortNumber", int.class).invoke(pdsource, Integer.parseInt(properties.getProperty("PGPORT", "5432")));
        } else {
            Method seturlm;
            try {
                seturlm = clazz.getMethod("setUrl", String.class);
            } catch (Exception e) {
                seturlm = clazz.getMethod("setURL", String.class);
            }
            seturlm.invoke(pdsource, url);
        }
        clazz.getMethod("setUser", String.class).invoke(pdsource, user);
        clazz.getMethod("setPassword", String.class).invoke(pdsource, password);
        return (ConnectionPoolDataSource) pdsource;
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
        return EntityInfo.load(clazz, this.nodeid, this.cacheForbidden, this.readPool.props, this, fullloader);
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

    private <T> void insert(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return;
        try {
            if (!info.isVirtualEntity()) {
                final String sql = info.getInsertSQL(values[0]);
                final Class primaryType = info.getPrimary().type();
                final Attribute primary = info.getPrimary();
                final boolean distributed = info.distributed;
                Attribute<T, Serializable>[] attrs = info.insertAttributes;
                if (distributed && !info.initedPrimaryValue && primaryType.isPrimitive()) { //由DataSource生成主键
                    synchronized (info) {
                        if (!info.initedPrimaryValue) { //初始化最大主键值
                            try {
                                Statement stmt = conn.createStatement();
                                ResultSet rs = stmt.executeQuery("SELECT MAX(" + info.getPrimarySQLColumn() + ") FROM " + info.getTable(values[0]));
                                if (rs.next()) {
                                    if (primaryType == int.class) {
                                        int v = rs.getInt(1) / info.allocationSize;
                                        if (v > info.primaryValue.get()) info.primaryValue.set(v);
                                    } else {
                                        long v = rs.getLong(1) / info.allocationSize;
                                        if (v > info.primaryValue.get()) info.primaryValue.set(v);
                                    }
                                }
                                rs.close();
                                stmt.close();
                            } catch (SQLException se) {
                                if (info.tableStrategy == null) throw se;
                            }
                            info.initedPrimaryValue = true;
                        }
                    }
                }
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
                                    Statement st = conn.createStatement();
                                    st.execute("CREATE DATABASE " + newTable.substring(0, newTable.indexOf('.')));
                                    st.close();
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
                if (debug.get()) {  //打印调试信息
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
        final PreparedStatement prestmt = info.autoGenerated
            ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);

        for (final T value : values) {
            int i = 0;
            if (info.distributed || info.autouuid) info.createPrimaryValue(value);
            for (Attribute<T, Serializable> attr : attrs) {
                prestmt.setObject(++i, attr.get(value));
            }
            prestmt.addBatch();
        }
        return prestmt;
    }

    public <T> void insertCache(Class<T> clazz, T... values) {
        if (values.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        for (T value : values) {
            cache.insert(value);
        }
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
        if (values.length == 0) return 0;
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

    private <T> int delete(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return 0;
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

    private <T> int delete(final Connection conn, final EntityInfo<T> info, Serializable... keys) {
        if (keys.length == 0) return -1;
        int c = -1;
        int c2 = 0;
        try {
            if (!info.isVirtualEntity()) {
                final Statement stmt = conn.createStatement();
                for (Serializable key : keys) {
                    String sql = "DELETE FROM " + info.getTable(key) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(key);
                    if (debug.get()) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                    stmt.addBatch(sql);
                }
                int[] pc = stmt.executeBatch();
                c = 0;
                for (int p : pc) {
                    c += p;
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
            return delete(null, info, node);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return delete(conn, info, node);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private <T> int delete(final Connection conn, final EntityInfo<T> info, final FilterNode node) {
        int c = -1;
        try {
            if (!info.isVirtualEntity()) {
                Map<Class, String> joinTabalis = node.getJoinTabalis();
                CharSequence join = node.createSQLJoin(this, joinTabalis, info);
                CharSequence where = node.createSQLExpress(info, joinTabalis);
                String sql = "DELETE " + (this.readPool.isMysql() ? "a" : "") + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
            }
            //------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            Serializable[] ids = cache.delete(node);
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), ids);
            return c >= 0 ? c : (ids == null ? 0 : ids.length);
        } catch (SQLException e) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + e.getSQLState() + ';')) return c;
            throw new RuntimeException(e);
        }
    }

    public <T> void deleteCache(Class<T> clazz, Serializable... ids) {
        if (ids.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        for (Serializable id : ids) {
            cache.delete(id);
        }
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

    private <T> int update(final Connection conn, final EntityInfo<T> info, T... values) {
        try {
            Class clazz = info.getType();
            int c = -1;
            if (!info.isVirtualEntity()) {
                final String updateSQL = info.getUpdateSQL(values[0]);
                final Attribute<T, Serializable> primary = info.getPrimary();
                final PreparedStatement prestmt = conn.prepareStatement(updateSQL);
                Attribute<T, Serializable>[] attrs = info.updateAttributes;
                final boolean debugfinest = debug.get();
                char[] sqlchars = debugfinest ? updateSQL.toCharArray() : null;
                for (final T value : values) {
                    int k = 0;
                    for (Attribute<T, Serializable> attr : attrs) {
                        prestmt.setObject(++k, attr.get(value));
                    }
                    prestmt.setObject(++k, primary.get(value));
                    prestmt.addBatch();//------------------------------------------------------------
                    if (debugfinest) {  //打印调试信息
                        //-----------------------------
                        int i = 0;
                        StringBuilder sb = new StringBuilder(128);
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
                        logger.finest(info.getType().getSimpleName() + " update sql=" + sb.toString().replaceAll("(\r|\n)", "\\n"));
                    } //打印结束
                }
                int[] pc = prestmt.executeBatch();
                c = 0;
                for (int p : pc) {
                    c += p;
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

    private <T> int updateColumn(Connection conn, final EntityInfo<T> info, Serializable id, String column, Serializable value) {
        try {
            int c = -1;
            if (!info.isVirtualEntity()) {
                String sql = "UPDATE " + info.getTable(id) + " SET " + info.getSQLColumn(null, column) + " = "
                    + info.formatToString(value) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
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

    private <T> int updateColumn(Connection conn, final EntityInfo<T> info, String column, Serializable value, FilterNode node) {
        try {
            int c = -1;
            if (!info.isVirtualEntity()) {
                Map<Class, String> joinTabalis = node.getJoinTabalis();
                CharSequence join = node.createSQLJoin(this, joinTabalis, info);
                CharSequence where = node.createSQLExpress(info, joinTabalis);

                String sql = "UPDATE " + info.getTable(node) + " a SET " + info.getSQLColumn("a", column) + " = "
                    + info.formatToString(value) + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
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

    private <T> int updateColumn(final Connection conn, final EntityInfo<T> info, final Serializable id, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        try {
            StringBuilder setsql = new StringBuilder();
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final List<ColumnValue> cols = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            for (ColumnValue col : values) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
                if (attr == null) continue;
                attrs.add(attr);
                cols.add(col);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    String c = info.getSQLColumn(null, col.getColumn());
                    setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
                }
            }
            int c = -1;
            if (!virtual) {
                String sql = "UPDATE " + info.getTable(id) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + ": " + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
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
            return updateColumn(null, info, node, values);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumn(conn, info, node, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private <T> int updateColumn(final Connection conn, final EntityInfo<T> info, final FilterNode node, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        try {
            StringBuilder setsql = new StringBuilder();
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final List<ColumnValue> cols = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            for (ColumnValue col : values) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
                if (attr == null) continue;
                attrs.add(attr);
                cols.add(col);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    String c = info.getSQLColumn("a", col.getColumn());
                    setsql.append(c).append(" = ").append(info.formatSQLValue(c, col));
                }
            }
            int c = -1;
            if (!virtual) {
                Map<Class, String> joinTabalis = node.getJoinTabalis();
                CharSequence join = node.createSQLJoin(this, joinTabalis, info);
                CharSequence where = node.createSQLExpress(info, joinTabalis);

                String sql = "UPDATE " + info.getTable(node) + " a SET " + setsql + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return c;
            T[] rs = cache.updateColumn(node, attrs, cols);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
            return c >= 0 ? c : (rs == null ? 0 : rs.length);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>     Entity类的泛型
     * @param bean    Entity对象
     * @param columns 需要更新的字段
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumns(final T bean, final String... columns) {
        final EntityInfo<T> info = loadEntityInfo((Class<T>) bean.getClass());
        if (info.isVirtualEntity()) {
            return updateColumns(null, info, bean, columns);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumns(conn, info, bean, columns);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private <T> int updateColumns(final Connection conn, final EntityInfo<T> info, final T bean, final String... columns) {
        if (bean == null || columns.length < 1) return -1;
        try {
            final Class<T> clazz = (Class<T>) bean.getClass();
            StringBuilder setsql = new StringBuilder();
            final Serializable id = info.getPrimary().get(bean);
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            for (String col : columns) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col);
                if (attr == null) continue;
                attrs.add(attr);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    setsql.append(info.getSQLColumn(null, col)).append(" = ").append(info.formatToString(attr.get(bean)));
                }
            }
            int c = -1;
            if (!virtual) {
                String sql = "UPDATE " + info.getTable(id) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(id);
                if (debug.get()) logger.finest(bean.getClass().getSimpleName() + ": " + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
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

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>     Entity类的泛型
     * @param bean    Entity对象
     * @param node    过滤node 不能为null
     * @param columns 需要更新的字段
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumns(final T bean, final FilterNode node, final String... columns) {
        final EntityInfo<T> info = loadEntityInfo((Class<T>) bean.getClass());
        if (info.isVirtualEntity()) {
            return updateColumns(null, info, bean, node, columns);
        }
        Connection conn = createWriteSQLConnection();
        try {
            return updateColumns(conn, info, bean, node, columns);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private <T> int updateColumns(final Connection conn, final EntityInfo<T> info, final T bean, final FilterNode node, final String... columns) {
        if (bean == null || node == null || columns.length < 1) return -1;
        try {
            final Class<T> clazz = (Class<T>) bean.getClass();
            StringBuilder setsql = new StringBuilder();
            final Serializable id = info.getPrimary().get(bean);
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            for (String col : columns) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col);
                if (attr == null) continue;
                attrs.add(attr);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    setsql.append(info.getSQLColumn("a", col)).append(" = ").append(info.formatToString(attr.get(bean)));
                }
            }
            int c = -1;
            if (!virtual) {
                Map<Class, String> joinTabalis = node.getJoinTabalis();
                CharSequence join = node.createSQLJoin(this, joinTabalis, info);
                CharSequence where = node.createSQLExpress(info, joinTabalis);

                String sql = "UPDATE " + info.getTable(node) + " a SET " + setsql
                    + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                c = stmt.executeUpdate(sql);
                stmt.close();
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

    public <T> void updateCache(Class<T> clazz, T... values) {
        if (values.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        for (T value : values) {
            cache.update(value);
        }
    }

    public <T> void reloadCache(Class<T> clazz, Serializable... ids) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        String column = info.getPrimary().field();
        for (Serializable id : ids) {
            Sheet<T> sheet = querySheet(false, true, clazz, null, FLIPPER_ONE, FilterNode.create(column, id));
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) cache.update(value);
        }
    }

    //-----------------------getNumberResult-----------------------------
    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResult(entityClass, func, null, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, null, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResult(entityClass, func, null, column, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResult(entityClass, func, defVal, column, (FilterNode) null);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
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
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : ("a." + column))) + " FROM " + info.getTable(node) + " a"
                + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
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

    //-----------------------queryColumnMap-----------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
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
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a." + sqlkey + ", " + func.getColumn((funcColumn == null || funcColumn.isEmpty() ? "*" : ("a." + funcColumn)))
                + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + sqlkey;
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
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
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
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
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return find(clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterBean bean) {
        return find(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterNode node) {
        return find(clazz, null, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
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
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a.* FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
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
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return rs;
        }

        final Connection conn = createReadSQLConnection();
        try {
            final String sql = "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + " = " + FilterNode.formatToString(pk);
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " exists sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            return rs;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return false;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || node.isCacheUseable(this))) return cache.exists(node);

        final Connection conn = createReadSQLConnection();
        try {
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " exists sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            return rs;
        } catch (SQLException se) {
            if (info.tableStrategy != null && info.tablenotexistSqlstates.contains(';' + se.getSQLState() + ';')) return false;
            throw new RuntimeException(se);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return queryColumnSet(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, bean));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, node));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        return queryColumnList(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return (List<V>) queryColumnSheet(false, selectedColumn, clazz, null, node).list(true);
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
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryColumnSheet(true, selectedColumn, clazz, flipper, node);
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

    private <K extends Serializable, T> Map<K, T> formatMap(final Class<T> clazz, final Collection<T> list) {
        Map<K, T> map = new LinkedHashMap<>();
        if (list == null || list.isEmpty()) return map;
        final Attribute<T, K> attr = (Attribute<T, K>) loadEntityInfo(clazz).getPrimary();
        for (T t : list) {
            map.put(attr.get(t), t);
        }
        return map;
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
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, node);
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
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryList(clazz, selects, (Flipper) null, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return queryList(clazz, flipper, FilterNode.create(column, key));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, null, flipper, bean);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryList(clazz, null, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(true, false, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean)).list(true);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, clazz, selects, flipper, node).list(true);
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
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheet(clazz, null, flipper, node);
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
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, true, clazz, selects, flipper, node);
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
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a.* FROM " + info.getTable(node) + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + info.createSQLOrderby(flipper);
            if (debug.get() && info.isLoggable(Level.FINEST))
                logger.finest(clazz.getSimpleName() + " query sql=" + sql + (flipper == null ? "" : (" LIMIT " + flipper.getOffset() + "," + flipper.getLimit())));
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.getOffset() > 0) set.absolute(flipper.getOffset());
            final int limit = flipper == null ? Integer.MAX_VALUE : flipper.getLimit();
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
