/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.source.DataSource;
import com.wentch.redkale.source.DistributeGenerator.DistributeTables;
import static com.wentch.redkale.source.FilterInfo.formatToString;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.sql.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.persistence.*;
import javax.sql.*;
import javax.xml.stream.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class DataJDBCSource implements DataSource {

    public static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    private static final String JDBC_CONNECTIONMAX = "javax.persistence.connection.limit";

    private static final String JDBC_URL = "javax.persistence.jdbc.url";

    private static final String JDBC_USER = "javax.persistence.jdbc.user";

    private static final String JDBC_PWD = "javax.persistence.jdbc.password";

    private static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";

    private static final String JDBC_SOURCE = "javax.persistence.jdbc.source";

    private final Logger logger = Logger.getLogger(DataJDBCSource.class.getSimpleName());

    private final AtomicBoolean debug = new AtomicBoolean(logger.isLoggable(Level.FINEST));

    private final String name;

    private final URL conf;

    private final JDBCPoolSource readPool;

    private final JDBCPoolSource writePool;

    final List<Class> cacheClasses = new ArrayList<>();

    @Resource(name = "property.datasource.nodeid")
    private int nodeid;

    @Resource
    DataSQLListener writeListener;

    @Resource
    DataCacheListener cacheListener;

    private static class DataJDBCConnection extends DataConnection {

        private final Connection sqlconn;

        private DataJDBCConnection(Connection c) {
            super(c);
            this.sqlconn = c;
            try {
                this.sqlconn.setAutoCommit(true);
            } catch (Exception e) {
                //do nothing
            }
        }

        @Override
        public void close() {
            try {
                sqlconn.close();
            } catch (Exception e) {
                //do nothing
            }
        }

        @Override
        public boolean commit() {
            try {
                sqlconn.commit();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void rollback() {
            try {
                sqlconn.rollback();
            } catch (Exception e) {
                //do nothing
            }
        }
    }

    public DataJDBCSource() throws IOException {
        this("");
    }

    public DataJDBCSource(final String unitName) throws IOException {
        this(unitName, System.getProperty(DATASOURCE_CONFPATH) == null
                ? DataJDBCSource.class.getResource("/META-INF/persistence.xml")
                : new File(System.getProperty(DATASOURCE_CONFPATH)).toURI().toURL());
    }

    public DataJDBCSource(final String unitName, URL url) throws IOException {
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
        for (Map.Entry<Object, Object> en : readprop.entrySet()) {
            if ("cache".equalsIgnoreCase(en.getValue().toString())) {
                try {
                    cacheClasses.add(Class.forName(en.getKey().toString()));
                } catch (Exception e) {
                }
            }
        }
    }

    public DataJDBCSource(String unitName, Properties readprop, Properties writeprop) {
        this.name = unitName;
        this.conf = null;
        this.readPool = new JDBCPoolSource(this, "read", readprop);
        this.writePool = new JDBCPoolSource(this, "write", writeprop);
        for (Map.Entry<Object, Object> en : readprop.entrySet()) {
            if ("cache".equalsIgnoreCase(en.getValue().toString())) {
                try {
                    cacheClasses.add(Class.forName(en.getKey().toString()));
                } catch (Exception e) {
                }
            }
        }
    }

    public static Map<String, DataJDBCSource> create(final InputStream in) {
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
        Map<String, DataJDBCSource> result = new HashMap<>();
        maps.entrySet().stream().forEach((en) -> {
            result.put(en.getKey(), new DataJDBCSource(en.getKey(), en.getValue()[0], en.getValue()[1]));
        });
        return result;
    }

    private static Map<String, Properties> loadProperties(final InputStream in0) {
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
                    } else if (result.getProperty(JDBC_URL) != null) {
                    }
                }
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

    private static ConnectionPoolDataSource createDataSource(Properties property) {
        try {
            return createDataSource(property.getProperty(JDBC_SOURCE, property.getProperty(JDBC_DRIVER)),
                    property.getProperty(JDBC_URL), property.getProperty(JDBC_USER), property.getProperty(JDBC_PWD));
        } catch (Exception ex) {
            throw new RuntimeException("(" + property + ") have no jdbc parameters", ex);
        }
    }

    private static ConnectionPoolDataSource createDataSource(final String source0, String url, String user, String password) throws Exception {
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
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    source = "com.microsoft.sqlserver.jdbc.SQLServerConnectionPoolDataSource";
                    break;
            }
        }
        final Class clazz = Class.forName(source);
        Object pdsource = clazz.newInstance();
        Method seturlm;
        try {
            seturlm = clazz.getMethod("setUrl", String.class);
        } catch (Exception e) {
            seturlm = clazz.getMethod("setURL", String.class);
        }
        seturlm.invoke(pdsource, url);
        clazz.getMethod("setUser", String.class).invoke(pdsource, user);
        clazz.getMethod("setPassword", String.class).invoke(pdsource, password);
        return (ConnectionPoolDataSource) pdsource;
    }

    @Override
    public DataConnection createReadConnection() {
        return new DataJDBCConnection(createReadSQLConnection());
    }

    @Override
    public DataConnection createWriteConnection() {
        return new DataJDBCConnection(createWriteSQLConnection());
    }

    public void close() {
        readPool.close();
        writePool.close();
    }

    public String getName() {
        return name;
    }

    private Connection createReadSQLConnection() {
        return readPool.poll();
    }

    private Connection createWriteSQLConnection() {
        return writePool.poll();
    }

    private void closeSQLConnection(final Connection sqlconn) {
        try {
            sqlconn.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "closeSQLConnection abort", e);
        }
    }

    public <T> void execute(String... sqls) {
        Connection conn = createWriteSQLConnection();
        try {
            execute(conn, sqls);
        } finally {
            closeSQLConnection(conn);
        }
    }

    public <T> void execute(final DataConnection conn, String... sqls) {
        execute((Connection) conn.getConnection(), sqls);
    }

    private <T> void execute(final Connection conn, String... sqls) {
        if (sqls.length == 0) return;
        try {
            final Statement stmt = conn.createStatement();
            for (String sql : sqls) {
                stmt.execute(sql);
            }
            stmt.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将entity的对象全部加载到Cache中去，如果clazz没有被@javax.persistence.Cacheable注解则不做任何事
     * <p>
     * @param <T>
     * @param clazz
     */
    @Override
    public <T> void refreshCache(Class<T> clazz) {
        EntityInfo<T> info = EntityInfo.load(clazz, this);
        EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        cache.clear();
        List<T> all = queryList(clazz, null);
        cache.fullLoad(all);
    }

    //----------------------insert-----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void insert(T... values) {
        Connection conn = createWriteSQLConnection();
        try {
            insert(conn, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void insert(final DataConnection conn, T... values) {
        insert((Connection) conn.getConnection(), values);
    }

    private <T> void insert(final Connection conn, T... values) {
        if (values.length == 0) return;
        try {
            final Class<T> clazz = (Class<T>) values[0].getClass();
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            final EntityCache<T> cache = info.inner.getCache();
            final String sql = info.insert.sql;
            if (debug.get()) logger.finest(clazz.getSimpleName() + " insert sql=" + sql);
            final PreparedStatement prestmt = info.autoGenerated
                    ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);
            final Class primaryType = info.getPrimaryType();
            final Attribute primary = info.getPrimary();
            final boolean distributed = info.distributed;
            Attribute<T, ?>[] attrs = info.insert.attributes;
            String[] sqls = null;
            if (distributed && !info.initedPrimaryValue && primaryType.isPrimitive()) {
                synchronized (info) {
                    if (!info.initedPrimaryValue) {
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT MAX(" + info.getPrimarySQLColumn() + ") FROM " + info.getTable());
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
                        if (info.distributeTables != null) {
                            for (final Class t : info.distributeTables) {
                                EntityXInfo<T> infox = EntityXInfo.load(this, t);
                                stmt = conn.createStatement();
                                rs = stmt.executeQuery("SELECT MAX(" + infox.getPrimarySQLColumn() + ") FROM " + infox.getTable());
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
                            }
                        }
                        info.initedPrimaryValue = true;
                    }
                }
            }
            if (writeListener == null) {
                for (final T value : values) {
                    int i = 0;
                    if (distributed) info.createPrimaryValue(value);
                    for (Attribute<T, ?> attr : attrs) {
                        prestmt.setObject(++i, attr.get(value));
                    }
                    prestmt.addBatch();
                }
            } else {
                char[] sqlchars = sql.toCharArray();
                sqls = new String[values.length];
                String[] ps = new String[attrs.length];
                int index = 0;
                for (final T value : values) {
                    int i = 0;
                    if (distributed) info.createPrimaryValue(value);
                    for (Attribute<T, ?> attr : attrs) {
                        Object a = attr.get(value);
                        ps[i] = formatToString(a);
                        prestmt.setObject(++i, a);
                    }
                    prestmt.addBatch();
                    //-----------------------------
                    StringBuilder sb = new StringBuilder(128);
                    i = 0;
                    for (char ch : sqlchars) {
                        if (ch == '?') {
                            sb.append(ps[i++]);
                        } else {
                            sb.append(ch);
                        }
                    }
                    sqls[index++] = sb.toString();
                }
            }
            prestmt.executeBatch();
            if (writeListener != null) writeListener.insert(name, sqls);
            if (info.autoGenerated) {
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
            if (cache != null) {
                for (final T value : values) {
                    cache.insert(value);
                }
                if (cacheListener != null) cacheListener.insert(name, clazz, values);
            }
            prestmt.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void insertCache(T... values) {
        if (values.length == 0) return;
        final EntityXInfo<T> info = EntityXInfo.load(this, (Class<T>) values[0].getClass());
        final EntityCache<T> cache = info.inner.getCache();
        if (cache == null) return;
        for (T value : values) {
            cache.insert(value);
        }
    }

    //-------------------------delete--------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void delete(T... values) {
        Connection conn = createWriteSQLConnection();
        try {
            delete(conn, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void delete(final DataConnection conn, T... values) {
        delete((Connection) conn.getConnection(), values);
    }

    private <T> void delete(final Connection conn, T... values) {
        if (values.length == 0) return;
        final Class clazz = values[0].getClass();
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final Attribute primary = info.inner.getPrimary();
        Serializable[] ids = new Serializable[values.length];
        int i = 0;
        for (final T value : values) {
            ids[i++] = (Serializable) primary.get(value);
        }
        delete(conn, clazz, ids);
    }

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param ids   主键值
     */
    @Override
    public <T> void delete(Class<T> clazz, Serializable... ids) {
        Connection conn = createWriteSQLConnection();
        try {
            delete(conn, clazz, ids);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param ids
     */
    @Override
    public <T> void delete(final DataConnection conn, Class<T> clazz, Serializable... ids) {
        delete((Connection) conn.getConnection(), clazz, ids);
    }

    private <T> void delete(final Connection conn, Class<T> clazz, Serializable... ids) {
        deleteByColumn(conn, clazz, EntityXInfo.load(this, clazz).getPrimaryField(), ids);
    }

    /**
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     */
    @Override
    public <T> void deleteByColumn(Class<T> clazz, String column, Serializable... keys) {
        Connection conn = createWriteSQLConnection();
        try {
            deleteByColumn(conn, clazz, column, keys);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column
     * @param keys
     */
    @Override
    public <T> void deleteByColumn(final DataConnection conn, Class<T> clazz, String column, Serializable... keys) {
        deleteByColumn((Connection) conn.getConnection(), clazz, column, keys);
    }

    private <T> void deleteByColumn(final Connection conn, Class<T> clazz, String column, Serializable... keys) {
        if (keys.length == 0) return;
        try {
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            String sql = "DELETE FROM " + info.getTable() + " WHERE " + info.getSQLColumn(column);
            if (keys.length == 1) {
                sql += " = " + formatToString(keys[0]);
            } else {
                sql += " IN (";
                boolean flag = false;
                for (final Serializable value : keys) {
                    if (flag) sql += ",";
                    sql += formatToString(value);
                    flag = true;
                }
                sql += ")";
            }
            if (debug.get()) logger.finest(clazz.getSimpleName() + " delete sql=" + sql);
            final Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();
            if (writeListener != null) writeListener.delete(name, sql);
            //------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            final Attribute<T, ?> attr = info.getAttribute(column);
            Serializable[] ids = cache.delete((T t) -> Arrays.binarySearch(keys, attr.get(t)) >= 0);
            if (cacheListener != null) cacheListener.delete(name, clazz, ids);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    @Override
    public <T> void deleteByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        Connection conn = createWriteSQLConnection();
        try {
            deleteByTwoColumn(conn, clazz, column1, key1, column2, key2);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    @Override
    public <T> void deleteByTwoColumn(final DataConnection conn, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        deleteByTwoColumn((Connection) conn.getConnection(), clazz, column1, key1, column2, key2);
    }

    private <T> void deleteByTwoColumn(final Connection conn, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        try {
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            String sql = "DELETE FROM " + info.getTable() + " WHERE " + info.getSQLColumn(column1) + " = " + formatToString(key1)
                    + " AND " + info.getSQLColumn(column2) + " = " + formatToString(key2);
            if (debug.get()) logger.finest(clazz.getSimpleName() + " delete sql=" + sql);
            final Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();
            if (writeListener != null) writeListener.delete(name, sql);
            //------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            final Attribute<T, ?> attr1 = info.getAttribute(column1);
            final Attribute<T, ?> attr2 = info.getAttribute(column2);
            Serializable[] ids = cache.delete((T t) -> key1.equals(attr1.get(t)) && key2.equals(attr2.get(t)));
            if (cacheListener != null) cacheListener.delete(name, clazz, ids);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void deleteCache(Class<T> clazz, Serializable... ids) {
        if (ids.length == 0) return;
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache == null) return;
        for (Serializable id : ids) {
            cache.delete(id);
        }
    }

    //------------------------update---------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void update(T... values) {
        Connection conn = createWriteSQLConnection();
        try {
            update(conn, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void update(final DataConnection conn, T... values) {
        update((Connection) conn.getConnection(), values);
    }

    private <T> void update(final Connection conn, T... values) {
        try {
            Class clazz = values[0].getClass();
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            if (debug.get()) logger.finest(clazz.getSimpleName() + " update sql=" + info.update.sql);
            final PreparedStatement prestmt = conn.prepareStatement(info.update.sql);
            Attribute<T, ?>[] attrs = info.update.attributes;
            String[] sqls = null;
            if (writeListener == null) {
                for (final T value : values) {
                    int i = 0;
                    for (Attribute<T, ?> attr : attrs) {
                        prestmt.setObject(++i, attr.get(value));
                    }
                    prestmt.addBatch();
                }
            } else {
                char[] sqlchars = info.update.sql.toCharArray();
                sqls = new String[values.length];
                String[] ps = new String[attrs.length];
                int index = 0;
                for (final T value : values) {
                    int i = 0;
                    for (Attribute<T, ?> attr : attrs) {
                        Object a = attr.get(value);
                        ps[i] = formatToString(a);
                        prestmt.setObject(++i, a);
                    }
                    prestmt.addBatch();
                    //-----------------------------
                    StringBuilder sb = new StringBuilder(128);
                    i = 0;
                    for (char ch : sqlchars) {
                        if (ch == '?') {
                            sb.append(ps[i++]);
                        } else {
                            sb.append(ch);
                        }
                    }
                    sqls[index++] = sb.toString();
                }
            }
            prestmt.executeBatch();
            prestmt.close();
            if (writeListener != null) writeListener.update(name, sqls);
            //---------------------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            for (final T value : values) {
                cache.update(value);
            }
            if (cacheListener != null) cacheListener.update(name, clazz, values);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    @Override
    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value) {
        Connection conn = createWriteSQLConnection();
        try {
            updateColumn(conn, clazz, id, column, value);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    @Override
    public <T> void updateColumn(DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value) {
        updateColumn((Connection) conn.getConnection(), clazz, id, column, value);
    }

    private <T> void updateColumn(Connection conn, Class<T> clazz, Serializable id, String column, Serializable value) {
        try {
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            String sql = "UPDATE " + info.getTable() + " SET " + info.getSQLColumn(column) + " = " + formatToString(value) + " WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
            if (debug.get()) logger.finest(clazz.getSimpleName() + " update sql=" + sql);
            final Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();
            if (writeListener != null) writeListener.update(name, sql);
            //---------------------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            T rs = cache.update(id, (Attribute<T, Serializable>) info.getAttribute(column), value);
            if (cacheListener != null) cacheListener.update(name, clazz, rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值给对象的column对应的值+incvalue， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param id
     * @param column
     * @param incvalue
     */
    @Override
    public <T> void updateColumnIncrement(Class<T> clazz, Serializable id, String column, long incvalue) {
        Connection conn = createWriteSQLConnection();
        try {
            updateColumnIncrement(conn, clazz, id, column, incvalue);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值给对象的column对应的值+incvalue， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param id
     * @param column
     * @param incvalue
     */
    @Override
    public <T> void updateColumnIncrement(DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        updateColumnIncrement((Connection) conn.getConnection(), clazz, id, column, incvalue);
    }

    private <T> void updateColumnIncrement(Connection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        try {
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            String col = info.getSQLColumn(column);
            String sql = "UPDATE " + info.getTable() + " SET " + col + " = " + col + " + (" + incvalue + ") WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
            if (debug.get()) logger.finest(clazz.getSimpleName() + " update sql=" + sql);
            final Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();
            if (writeListener != null) writeListener.update(name, sql);
            //---------------------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            Attribute<T, Object> attr = (Attribute<T, Object>) info.getAttribute(column);
            T value = find(clazz, id);
            if (value == null) return;
            cache.update(id, attr, attr.get(value));
            if (cacheListener != null) cacheListener.update(name, clazz, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param value
     * @param columns
     */
    @Override
    public <T> void updateColumns(final T value, final String... columns) {
        Connection conn = createWriteSQLConnection();
        try {
            updateColumns(conn, value, columns);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param value
     * @param columns
     */
    @Override
    public <T> void updateColumns(final DataConnection conn, final T value, final String... columns) {
        updateColumns((Connection) conn.getConnection(), value, columns);
    }

    private <T> void updateColumns(final Connection conn, final T value, final String... columns) {
        if (columns.length < 1) return;
        try {
            final Class<T> clazz = (Class<T>) value.getClass();
            final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
            StringBuilder setsql = new StringBuilder();
            Attribute<T, ?>[] attrs = new Attribute[columns.length];
            int i = - 1;
            final Serializable id = (Serializable) info.getPrimary().get(value);
            for (String col : columns) {
                if (setsql.length() > 0) setsql.append(',');
                attrs[++i] = info.getAttribute(col);
                setsql.append(info.getSQLColumn(col)).append(" = ")
                        .append(formatToString(attrs[i].get(value)));
            }
            String sql = "UPDATE " + info.getTable() + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = "
                    + formatToString(id);
            if (debug.get()) logger.finest(value.getClass().getSimpleName() + ": " + sql);
            final Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();
            if (writeListener != null) writeListener.update(name, sql);
            //---------------------------------------------------
            final EntityCache<T> cache = info.inner.getCache();
            if (cache == null) return;
            cache.update(value, attrs);
            if (cacheListener != null) cacheListener.update(name, clazz, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void updateCache(Class<T> clazz, T... values) {
        if (values.length == 0) return;
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache == null) return;
        for (T value : values) {
            cache.update(value);
        }
    }

    public <T> void reloadCache(Class<T> clazz, Serializable... ids) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache == null) return;
        for (Serializable id : ids) {
            T value = find(clazz, false, id);
            if (value != null) cache.update(value);
        }
    }

    //-----------------------getSingleResult-----------------------------
    //-----------------------------MAX-----------------------------
    @Override
    public Number getMaxSingleResult(final Class entityClass, final String column) {
        return getMaxSingleResult(entityClass, column, null);
    }

    @Override
    public Number getMaxSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.MAX, entityClass, column, bean);
    }

    //-----------------------------MIN-----------------------------
    @Override
    public Number getMinSingleResult(final Class entityClass, final String column) {
        return getMinSingleResult(entityClass, column, null);
    }

    @Override
    public Number getMinSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.MIN, entityClass, column, bean);
    }

    //-----------------------------SUM-----------------------------
    @Override
    public Number getSumSingleResult(final Class entityClass, final String column) {
        return getSumSingleResult(entityClass, column, null);
    }

    @Override
    public Number getSumSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.SUM, entityClass, column, bean);
    }

    //----------------------------COUNT----------------------------
    @Override
    public Number getCountSingleResult(final Class entityClass) {
        return getCountSingleResult(entityClass, null);
    }

    @Override
    public Number getCountSingleResult(final Class entityClass, FilterBean bean) {
        return getSingleResult(ReckonType.COUNT, entityClass, null, bean);
    }

    //-----------------------------AVG-----------------------------
    @Override
    public Number getAvgSingleResult(final Class entityClass, final String column) {
        return getAvgSingleResult(entityClass, column, null);
    }

    @Override
    public Number getAvgSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.AVG, entityClass, column, bean);
    }

    private <T> Number getSingleResult(final ReckonType type, final Class<T> entityClass, final String column, FilterBean bean) {
        final Connection conn = createReadSQLConnection();
        try {
            final EntityXInfo<T> info = EntityXInfo.load(this, entityClass);
            final String sql = "SELECT " + type.getReckonColumn("a." + column) + " FROM " + info.getTable() + " a" + createWhereExpression(info, null, bean);
            if (debug.get()) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
            final PreparedStatement prestmt = conn.prepareStatement(sql);
            Number rs = null;
            ResultSet set = prestmt.executeQuery();
            if (set.next()) {
                rs = (Number) set.getObject(1);
            }
            set.close();
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>
     * @param clazz
     * @param pk
     * @return
     */
    @Override
    public <T> T find(Class<T> clazz, Serializable pk) {
        return find(clazz, true, pk);
    }

    private <T> T find(Class<T> clazz, boolean readcache, Serializable pk) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (readcache && cache != null) {
            T r = cache.find(pk);
            if (r != null || cache.isFullLoaded()) return r;
        }
        final Connection conn = createReadSQLConnection();
        try { 
            if (debug.get()) logger.finest(clazz.getSimpleName() + " find sql=" + info.query.sql.replace("?", String.valueOf(pk)));
            final PreparedStatement prestmt = conn.prepareStatement(info.query.sql);
            prestmt.setObject(1, pk);
            T rs = null;
            ResultSet set = prestmt.executeQuery();
            if (set.next()) {
                rs = info.createInstance();
                for (Attribute attr : info.query.attributes) {
                    attr.set(rs, set.getObject(attr.field()));
                }
            }
            set.close();
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值集合获取对象集合
     *
     * @param <T>
     * @param clazz
     * @param ids
     * @return
     */
    @Override
    public <T> T[] find(Class<T> clazz, Serializable... ids) {
        EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        return findByColumn(clazz, info, null, info.getPrimarySQLColumn(), ids);
    }

    /**
     * 根据唯一索引获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> T findByColumn(Class<T> clazz, String column, Serializable key) {
        EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        return findByColumn(clazz, info, null, info.getSQLColumn(column), key)[0];
    }

    /**
     * 根据两个字段的值获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @return
     */
    @Override
    public <T> T findByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache != null) {
            final Attribute<T, ?> attr1 = info.getAttribute(column1);
            final Attribute<T, ?> attr2 = info.getAttribute(column2);
            T r = cache.find((T t) -> key1.equals(attr1.get(t)) && key2.equals(attr2.get(t)));
            if (r != null || cache.isFullLoaded()) return r;
        }
        final Connection conn = createReadSQLConnection();
        try {
            final String sql = "SELECT * FROM " + info.getTable() + " WHERE " + info.getSQLColumn(column1) + " = ? AND " + info.getSQLColumn(column2) + " = ?";
            if (debug.get()) logger.finest(clazz.getSimpleName() + " find sql=" + sql.replaceFirst("\\?", String.valueOf(key1)).replaceFirst("\\?", String.valueOf(key2)));
            final PreparedStatement prestmt = conn.prepareStatement(sql);
            prestmt.setObject(1, key1);
            prestmt.setObject(2, key2);
            T rs = null;
            ResultSet set = prestmt.executeQuery();
            if (set.next()) {
                rs = info.createInstance();
                for (AttributeX attr : info.query.attributes) {
                    attr.setValue(null, rs, set);
                }
            }
            set.close();
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据唯一索引获取对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     * @return
     */
    @Override
    public <T> T[] findByColumn(Class<T> clazz, String column, Serializable... keys) {
        return findByColumn(clazz, (SelectColumn) null, column, keys);
    }

    /**
     * 根据字段值拉去对象， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects 只拉起指定字段名或者排除指定字段名的值
     * @param column
     * @param keys
     * @return
     */
    @Override

    public <T> T[] findByColumn(Class<T> clazz, final SelectColumn selects, String column, Serializable... keys) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache != null) {
            Attribute<T, ?> idattr = info.getAttribute(column);
            List<T> list = cache.queryList(selects, (x) -> Arrays.binarySearch(keys, idattr.get(x)) >= 0, null);
            final T[] rs = (T[]) Array.newInstance(clazz, keys.length);
            if (!list.isEmpty()) {
                for (int i = 0; i < rs.length; i++) {
                    T item = null;
                    for (T s : list) {
                        if (keys[i].equals(idattr.get(s))) {
                            item = s;
                            break;
                        }
                    }
                    rs[i] = item;
                }
            }
            if (!list.isEmpty() || cache.isFullLoaded()) return rs;
        }
        return findByColumn(clazz, info, selects, info.getSQLColumn(column), keys);
    }

    private <T> T[] findByColumn(Class<T> clazz, final EntityXInfo<T> info, final SelectColumn selects, String sqlcolumn, Serializable... keys) {
        if (keys.length < 1) return (T[]) Array.newInstance(clazz, 0);
        final Connection conn = createReadSQLConnection();
        try {
            final String sql = "SELECT * FROM " + info.getTable() + " WHERE " + sqlcolumn + " = ?";
            if (debug.get()) logger.finest(clazz.getSimpleName() + " query sql=" + sql);
            final PreparedStatement prestmt = conn.prepareStatement(sql);
            T[] rs = (T[]) Array.newInstance(clazz, keys.length);
            for (int i = 0; i < keys.length; i++) {
                prestmt.clearParameters();
                prestmt.setObject(1, keys[i]);
                T one = null;
                ResultSet set = prestmt.executeQuery();
                if (set.next()) {
                    one = info.createInstance();
                    for (AttributeX attr : info.query.attributes) {
                        attr.setValue(selects, one, set);
                    }
                }
                rs[i] = one;
                set.close();
            }
            prestmt.close();
            return rs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    //-----------------------list----------------------------
    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache != null) {
            final Attribute<T, ?> attr = info.getAttribute(column);
            List<T> list = cache.queryList(null, (T t) -> key.equals(attr.get(t)), null);
            final List<V> rs = new ArrayList<>();
            if (!list.isEmpty()) {
                final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
                for (T t : list) {
                    rs.add(selected.get(t));
                }
            }
            if (!rs.isEmpty() || cache.isFullLoaded()) return rs;
        }
        final Connection conn = createReadSQLConnection();
        try {
            final List<V> list = new ArrayList();
            final String sql = "SELECT " + info.getSQLColumn(selectedColumn) + " FROM " + info.getTable() + " WHERE " + info.getSQLColumn(column) + " = ?";
            if (debug.get()) logger.finest(clazz.getSimpleName() + " query sql=" + sql.replaceFirst("\\?", String.valueOf(key)));
            final PreparedStatement ps = conn.prepareStatement(sql);
            ps.setObject(1, key);
            final ResultSet set = ps.executeQuery();
            while (set.next()) {
                list.add((V) set.getObject(1));
            }
            set.close();
            ps.close();
            return list;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, String column, Serializable key) {
        return queryList(clazz, (SelectColumn) null, column, key);
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, String column, FilterExpress express, Serializable key) {
        return queryList(clazz, (SelectColumn) null, column, express, key);
    }

    /**
     * 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, Serializable key) {
        return queryList(clazz, selects, column, FilterExpress.EQUAL, key);
    }

    /**
     * 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param express
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, FilterExpress express, Serializable key) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache != null) {
            final Attribute<T, ?> attr = info.getAttribute(column);
            Predicate<T> filter = null;
            switch (express) {
                case EQUAL:
                    filter = (T t) -> key.equals(attr.get(t));
                    break;
                case NOTEQUAL:
                    filter = (T t) -> !key.equals(attr.get(t));
                    break;
                case GREATERTHAN:
                    filter = (T t) -> ((Number) attr.get(t)).longValue() > ((Number) key).longValue();
                    break;
                case LESSTHAN:
                    filter = (T t) -> ((Number) attr.get(t)).longValue() < ((Number) key).longValue();
                    break;
                case GREATERTHANOREQUALTO:
                    filter = (T t) -> ((Number) attr.get(t)).longValue() >= ((Number) key).longValue();
                    break;
                case LESSTHANOREQUALTO:
                    filter = (T t) -> ((Number) attr.get(t)).longValue() <= ((Number) key).longValue();
                    break;
                case LIKE:
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().contains(key.toString());
                    };
                    break;
                case NOTLIKE:
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().contains(key.toString());
                    };
                    break;
                case ISNULL:
                    filter = (T t) -> attr.get(t) == null;
                    break;
                case ISNOTNULL:
                    filter = (T t) -> attr.get(t) != null;
                    break;
                case OPAND:
                    filter = (T t) -> (((Number) attr.get(t)).longValue() & ((Number) key).longValue()) > 0;
                    break;
                case OPOR:
                    filter = (T t) -> (((Number) attr.get(t)).longValue() | ((Number) key).longValue()) > 0;
                    break;
                case OPANDNO:
                    filter = (T t) -> (((Number) attr.get(t)).longValue() & ((Number) key).longValue()) == 0;
                    break;
            }
            List<T> rs = cache.queryList(selects, filter, null);
            if (!rs.isEmpty() || cache.isFullLoaded()) return rs;
        }
        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final String sql = "SELECT * FROM " + info.getTable() + " WHERE " + info.getSQLColumn(column) + " " + express.value() + " ?";
            if (debug.get()) logger.finest(clazz.getSimpleName() + " query sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql);
            ps.setObject(1, key);
            final ResultSet set = ps.executeQuery();
            while (set.next()) {
                final T result = info.createInstance();
                for (AttributeX<T, Object> attr : info.query.attributes) {
                    attr.setValue(sels, result, set);
                }
                list.add(result);
            }
            set.close();
            ps.close();
            return list;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    /**
     * 根据过滤对象FilterBean查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param bean
     * @return
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, null, bean);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param bean
     * @return
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return querySheet(clazz, selects, null, bean).list(true);
    }

    //-----------------------sheet----------------------------
    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据
     *
     * @param <T>
     * @param clazz
     * @param flipper
     * @param bean
     * @return
     */
    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, null, flipper, bean);
    }

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param flipper
     * @param bean
     * @return
     */
    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        final EntityXInfo<T> info = EntityXInfo.load(this, clazz);
        final EntityCache<T> cache = info.inner.getCache();
        if (cache != null) {
            Predicate<T> filter = null;
            Comparator<T> sort = null;
            boolean valid = true;
            if (bean != null) {
                FilterInfo finfo = FilterInfo.load(bean.getClass(), this);
                valid = finfo.isValidCacheJoin();
                if (valid) {
                    filter = finfo.getFilterPredicate(info.inner, bean);
                    sort = finfo.getSortComparator(info.inner, flipper);
                }
            }
            if (valid) {
                Sheet<T> sheet = cache.querySheet(selects, filter, flipper, sort);
                if (!sheet.isEmpty() || cache.isFullLoaded()) return sheet;
            }
        }
        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final String sql = "SELECT a.* FROM " + info.getTable() + " a" + createWhereExpression(info, flipper, bean);
            if (debug.get()) logger.finest(clazz.getSimpleName() + " query sql=" + sql + (flipper == null ? "" : (" LIMIT " + flipper.index() + "," + flipper.getSize())));
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.index() > 0) set.absolute(flipper.index());
            final int limit = flipper == null ? Integer.MAX_VALUE : flipper.getSize();
            int i = 0;
            long total;
            while (set.next()) {
                i++;
                final T result = info.createInstance();
                for (AttributeX<T, Object> attr : info.query.attributes) {
                    attr.setValue(sels, result, set);
                }
                list.add(result);
                if (limit <= i) break;
            }
            if (flipper != null) {
                set.last();
                total = set.getRow();
            } else {
                total = list.size();
            }
            set.close();
            ps.close();
            return new Sheet<>(total, list);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    private <T> String createWhereExpression(final EntityXInfo<T> info, final Flipper flipper, final FilterBean bean) {
        if (bean == null && flipper == null) return "";
        boolean emptySort = flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty();
        StringBuilder where = null;
        boolean join = false;
        if (bean != null) {
            final FilterInfo filter = FilterInfo.load(bean.getClass(), this);
            join = filter.isJoin();
            where = filter.createWhereSql(info.getPrimarySQLColumn(), bean);
        }
        if (emptySort) return where == null ? "" : where.toString();
        if (where == null) where = new StringBuilder();
        where.append(" ORDER BY ");
        if (info.same && !join) {
            where.append(flipper.getSort());
        } else {
            boolean flag = false;
            for (String item : flipper.getSort().split(",")) {
                if (item.isEmpty()) continue;
                String[] sub = item.split("\\s+");
                if (flag) where.append(',');
                if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                    where.append("a.").append(info.getSQLColumn(sub[0])).append(" ASC");
                } else {
                    where.append("a.").append(info.getSQLColumn(sub[0])).append(" DESC");
                }
                flag = true;
            }
        }
        return where.toString();
    }

    //----------------------------------------------------------------------
    public static final class JDBCPoolSource {

        private static final Map<String, SimpleEntry<WatchService, List<WeakReference<JDBCPoolSource>>>> maps = new HashMap<>();

        private final AtomicLong usingCounter = new AtomicLong();

        private final AtomicLong creatCounter = new AtomicLong();

        private final AtomicLong cycleCounter = new AtomicLong();

        private final AtomicLong saveCounter = new AtomicLong();

        private final ConnectionPoolDataSource source;

        private final ArrayBlockingQueue<PooledConnection> queue;

        private final ConnectionEventListener listener;

        private final DataJDBCSource dataSource;

        private final String stype; // "" 或 "read"  或 "write"

        private final int max;

        private String url;

        private String user;

        private String password;

        public JDBCPoolSource(DataJDBCSource source, String stype, Properties prop) {
            this.dataSource = source;
            this.stype = stype;
            this.source = createDataSource(prop);
            this.url = prop.getProperty(JDBC_URL);
            this.user = prop.getProperty(JDBC_USER);
            this.password = prop.getProperty(JDBC_PWD);
            this.max = Integer.decode(prop.getProperty(JDBC_CONNECTIONMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
            this.queue = new ArrayBlockingQueue<>(this.max);
            this.listener = new ConnectionEventListener() {

                @Override
                public void connectionClosed(ConnectionEvent event) {
                    PooledConnection pc = (PooledConnection) event.getSource();
                    if (queue.offer(pc)) saveCounter.incrementAndGet();
                }

                @Override
                public void connectionErrorOccurred(ConnectionEvent event) {
                    usingCounter.decrementAndGet();
                    if ("08S01".equals(event.getSQLException().getSQLState())) return; //MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                    dataSource.logger.log(Level.WARNING, "connectionErronOccurred", event.getSQLException());
                }
            };
            try {
                this.watch();
            } catch (Exception e) {
                dataSource.logger.log(Level.WARNING, DataSource.class.getSimpleName() + " watch " + dataSource.conf + " error", e);
            }
        }

        private void watch() throws IOException {
            if (dataSource.conf == null || dataSource.name == null) return;
            final String file = dataSource.conf.getFile();
            final File f = new File(file);
            if (!f.isFile() || !f.canRead()) return;
            synchronized (maps) {
                SimpleEntry<WatchService, List<WeakReference<JDBCPoolSource>>> entry = maps.get(file);
                if (entry != null) {
                    entry.getValue().add(new WeakReference<>(this));
                    return;
                }
                final WatchService watcher = f.toPath().getFileSystem().newWatchService();
                final List<WeakReference<JDBCPoolSource>> list = new CopyOnWriteArrayList<>();
                Thread watchThread = new Thread() {

                    @Override
                    public void run() {
                        try {
                            while (!this.isInterrupted()) {
                                final WatchKey key = watcher.take();
                                Thread.sleep(3000); //防止文件正在更新过程中去读取
                                final Map<String, Properties> m = loadProperties(new FileInputStream(file));
                                key.pollEvents().stream().forEach((event) -> {
                                    if (event.kind() != ENTRY_MODIFY) return;
                                    if (!((Path) event.context()).toFile().getName().equals(f.getName())) return;
                                    for (WeakReference<JDBCPoolSource> ref : list) {
                                        JDBCPoolSource pool = ref.get();
                                        if (pool == null) continue;
                                        try {
                                            Properties property = m.get(pool.dataSource.name);
                                            if (property == null) property = m.get(pool.dataSource.name + "." + pool.stype);
                                            if (property != null) pool.change(property);
                                        } catch (Exception ex) {
                                            dataSource.logger.log(Level.INFO, event.context() + " occur error", ex);
                                        }
                                    }
                                });
                                key.reset();
                            }
                        } catch (Exception e) {
                            dataSource.logger.log(Level.WARNING, "DataSource watch " + file + " occur error", e);
                        }
                    }
                };
                f.getParentFile().toPath().register(watcher, ENTRY_MODIFY);
                watchThread.setName("DataSource-Watch-" + maps.size() + "-Thread");
                watchThread.setDaemon(true);
                watchThread.start();
                dataSource.logger.log(Level.FINER, watchThread.getName() + " start watching " + file);
                //-----------------------------------------------------------            
                list.add(new WeakReference<>(this));
                maps.put(file, new SimpleEntry<>(watcher, list));
            }
        }

        public void change(Properties property) {
            Method seturlm;
            Class clazz = source.getClass();
            String newurl = property.getProperty(JDBC_URL);
            String newuser = property.getProperty(JDBC_USER);
            String newpassword = property.getProperty(JDBC_PWD);
            if (this.url.equals(newurl) && this.user.equals(newuser) && this.password.equals(newpassword)) return;
            try {
                try {
                    seturlm = clazz.getMethod("setUrl", String.class);
                } catch (Exception e) {
                    seturlm = clazz.getMethod("setURL", String.class);
                }
                seturlm.invoke(source, newurl);
                clazz.getMethod("setUser", String.class).invoke(source, newuser);
                clazz.getMethod("setPassword", String.class).invoke(source, newpassword);
                this.url = newurl;
                this.user = newuser;
                this.password = newpassword;
                dataSource.logger.log(Level.INFO, DataSource.class.getSimpleName() + "(" + dataSource.name + "." + stype + ") change  (" + property + ")");
            } catch (Exception e) {
                dataSource.logger.log(Level.SEVERE, DataSource.class.getSimpleName() + " dynamic change JDBC (url userName password) error", e);
            }
        }

        public Connection poll() {
            return poll(0, null);
        }

        private Connection poll(final int count, SQLException e) {
            if (count >= 3) {
                dataSource.logger.log(Level.WARNING, "create pooled connection error", e);
                throw new RuntimeException(e);
            }
            PooledConnection result = queue.poll();
            if (result == null) {
                if (usingCounter.get() >= max) {
                    try {
                        result = queue.poll(6, TimeUnit.SECONDS);
                    } catch (Exception t) {
                        dataSource.logger.log(Level.WARNING, "take pooled connection error", t);
                    }
                }
                if (result == null) {
                    try {
                        result = source.getPooledConnection();
                        result.addConnectionEventListener(listener);
                        usingCounter.incrementAndGet();
                    } catch (SQLException ex) {
                        return poll(count + 1, ex);
                    }
                    creatCounter.incrementAndGet();
                }
            } else {
                cycleCounter.incrementAndGet();
            }
            Connection conn;
            try {
                conn = result.getConnection();
                if (!conn.isValid(1)) {
                    dataSource.logger.info("sql connection is not vaild");
                    usingCounter.decrementAndGet();
                    return poll(0, null);
                }
            } catch (SQLException ex) {
                dataSource.logger.log(Level.FINER, "result.getConnection from pooled connection abort", ex);
                return poll(0, null);
            }
            return conn;
        }

        public long getCreatCount() {
            return creatCounter.longValue();
        }

        public long getCycleCount() {
            return cycleCounter.longValue();
        }

        public long getSaveCount() {
            return saveCounter.longValue();
        }

        public void close() {
            queue.stream().forEach(x -> {
                try {
                    x.close();
                } catch (Exception e) {
                }
            });
        }
    }

    //----------------------------------------------------------------------
    private static class AttributeX<T, F> implements Attribute<T, F> {

        private final Class type;

        private final Attribute<T, F> attribute;

        private final String fieldName;

        public AttributeX(Class type, Attribute<T, F> attribute, String fieldname) {
            this.type = type;
            this.attribute = attribute;
            this.fieldName = fieldname;
        }

        @Override
        public String field() {
            return attribute.field();
        }

        @Override
        public F get(T obj) {
            return attribute.get(obj);
        }

        @Override
        public void set(T obj, F value) {
            Object o = value;
            if (o != null) {
                if (type == long.class) {
                    o = ((Number) o).longValue();
                } else if (type == int.class) {
                    o = ((Number) o).intValue();
                } else if (type == short.class) {
                    o = ((Number) o).shortValue();
                }
            }
            attribute.set(obj, (F) o);
        }

        public void setValue(SelectColumn sels, T obj, ResultSet set) throws SQLException {
            if (sels == null || sels.validate(this.fieldName)) {
                Object o = set.getObject(this.attribute.field());
                if (o != null) {
                    if (type == long.class) {
                        o = ((Number) o).longValue();
                    } else if (type == int.class) {
                        o = ((Number) o).intValue();
                    } else if (type == short.class) {
                        o = ((Number) o).shortValue();
                    }
                }
                attribute.set(obj, (F) o);
            }
        }
    }

    private static class EntityXInfo<T> {

        private static final ConcurrentHashMap<Class, EntityXInfo> entityxInfos = new ConcurrentHashMap<>();

        private final int nodeid;

        private final EntityInfo<T> inner;

        final Class[] distributeTables;

        final boolean autoGenerated;

        final boolean distributed;

        boolean initedPrimaryValue = false;

        final AtomicLong primaryValue = new AtomicLong(0);

        final int allocationSize;

        private final ActionInfo query;

        private final ActionInfo insert;

        private final ActionInfo update;

        private final ActionInfo delete;

        //表字段与字段名是否全部一致
        private final boolean same;

        private class ActionInfo {

            final String sql;

            final AttributeX<T, Object>[] attributes;

            public ActionInfo(String sql, List<AttributeX<T, Object>> list) {
                this.sql = sql;
                this.attributes = list.toArray(new AttributeX[list.size()]);
            }

            public ActionInfo(String sql, AttributeX<T, Object>... attributes) {
                this.sql = sql;
                this.attributes = attributes;
            }
        }

        public EntityXInfo(DataJDBCSource source, Class<T> type) {
            this.inner = EntityInfo.load(type, source);
            this.nodeid = source.nodeid;
            DistributeTables dt = type.getAnnotation(DistributeTables.class);
            this.distributeTables = dt == null ? null : dt.value();

            Class cltmp = type;
            Set<String> fields = new HashSet<>();
            boolean auto = false;
            boolean sqldistribute = false;
            int allocationSize0 = 0;
            String wheresql = "";
            List<AttributeX<T, Object>> queryattrs = new ArrayList<>();
            List<String> insertcols = new ArrayList<>();
            List<AttributeX<T, Object>> insertattrs = new ArrayList<>();
            List<String> updatecols = new ArrayList<>();
            List<AttributeX<T, Object>> updateattrs = new ArrayList<>();
            boolean same0 = true;
            Class idfieldtype = int.class;
            do {
                for (Field field : cltmp.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    if (field.getAnnotation(Transient.class) != null) continue;
                    final String fieldname = field.getName();
                    if (fields.contains(fieldname)) continue;
                    fields.add(fieldname);
                    final Column col = field.getAnnotation(Column.class);
                    final String sqlfield = col == null || col.name().isEmpty() ? fieldname : col.name();
                    if (same0) same0 = fieldname.equals(sqlfield);
                    final Class fieldtype = field.getType();
                    Attribute attribute = inner.getAttribute(fieldname);
                    if (attribute == null) continue;
                    AttributeX attr = new AttributeX(fieldtype, attribute, fieldname);
                    if (field.getAnnotation(Id.class) != null) {
                        idfieldtype = fieldtype;
                        GeneratedValue gv = field.getAnnotation(GeneratedValue.class);
                        auto = gv != null;
                        if (gv != null && gv.strategy() != GenerationType.IDENTITY) {
                            throw new RuntimeException(cltmp.getName() + "'s @ID primary not a GenerationType.IDENTITY");
                        }
                        DistributeGenerator dg = field.getAnnotation(DistributeGenerator.class);
                        if (dg != null) {
                            if (!fieldtype.isPrimitive()) throw new RuntimeException(cltmp.getName() + "'s @DistributeGenerator primary must be primitive class type field");
                            sqldistribute = true;
                            auto = false;
                            allocationSize0 = dg.allocationSize();
                            primaryValue.set(dg.initialValue());
                        }
                        wheresql = " WHERE " + sqlfield + " = ?";
                        if (!auto) {
                            insertcols.add(sqlfield);
                            insertattrs.add(attr);
                        }
                    } else {
                        if (col == null || col.insertable()) {
                            insertcols.add(sqlfield);
                            insertattrs.add(attr);
                        }
                        if (col == null || col.updatable()) {
                            updatecols.add(sqlfield);
                            updateattrs.add(attr);
                        }
                    }
                    queryattrs.add(attr);
                }
            } while ((cltmp = cltmp.getSuperclass()) != Object.class);
            AttributeX idxattr = new AttributeX(idfieldtype, inner.getPrimary(), inner.getPrimaryField());
            updateattrs.add(idxattr);
            this.autoGenerated = auto;
            this.delete = new ActionInfo("DELETE FROM " + inner.getTable() + wheresql, idxattr);
            StringBuilder updatesb = new StringBuilder();
            for (String col : updatecols) {
                if (updatesb.length() > 0) updatesb.append(',');
                updatesb.append(col).append(" = ?");
            }
            this.update = new ActionInfo("UPDATE " + inner.getTable() + " SET " + updatesb + wheresql, updateattrs);
            StringBuilder insertsb = new StringBuilder();
            StringBuilder insertsb2 = new StringBuilder();
            for (String col : insertcols) {
                if (insertsb.length() > 0) insertsb.append(',');
                insertsb.append(col);
                if (insertsb2.length() > 0) insertsb2.append(',');
                insertsb2.append('?');
            }
            String insertsql = "INSERT INTO " + inner.getTable() + "(" + insertsb + ") VALUES(" + insertsb2 + ")";
            this.same = same0;
            this.distributed = sqldistribute;
            this.allocationSize = allocationSize0;
            this.insert = new ActionInfo(insertsql, insertattrs);
            this.query = new ActionInfo("SELECT * FROM " + inner.getTable() + wheresql, queryattrs);
        }

        public static <T> EntityXInfo<T> load(DataJDBCSource source, Class<T> clazz) {
            EntityXInfo rs = entityxInfos.get(clazz);
            if (rs != null) return rs;
            synchronized (entityxInfos) {
                rs = entityxInfos.get(clazz);
                if (rs == null) {
                    rs = new EntityXInfo(source, clazz);
                    entityxInfos.put(clazz, rs);
                }
                return rs;
            }
        }

        public T createInstance() {
            return inner.getCreator().create();
        }

        public void createPrimaryValue(T src) {
            long v = primaryValue.incrementAndGet() * allocationSize + nodeid;
            Class p = inner.getPrimaryType();
            if (p == int.class || p == Integer.class) {
                getPrimary().set(src, (Integer) ((Long) v).intValue());
            } else {
                getPrimary().set(src, v);
            }
        }

        public Class getPrimaryType() {
            return inner.getPrimaryType();
        }

        public Attribute<T, Object> getPrimary() {
            return inner.getPrimary();
        }

        public String getPrimaryField() {
            return inner.getPrimaryField();
        }

        public String getTable() {
            return inner.getTable();
        }

        public String getPrimarySQLColumn() {
            return inner.getPrimary().field();
        }

        public String getSQLColumn(String fieldname) {
            if (same) return fieldname;
            return inner.getAttribute(fieldname).field();
        }

        public Attribute<T, ?> getAttribute(String fieldname) {
            return inner.getAttribute(fieldname);
        }
    }

    private static enum ReckonType {

        MAX, MIN, SUM, COUNT, AVG;

        public String getReckonColumn(String col) {
            if (this == COUNT) return this.name() + "(*)";
            return this.name() + "(" + col + ")";
        }
    }
}
