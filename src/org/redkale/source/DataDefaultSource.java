/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.channels.CompletionHandler;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.Resource;
import javax.sql.ConnectionPoolDataSource;
import javax.xml.stream.*;
import static org.redkale.source.FilterNode.formatToString;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class DataDefaultSource implements DataSource, Function<Class, EntityInfo>, AutoCloseable {

    public static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    static final String JDBC_CONNECTIONSMAX = "javax.persistence.connections.limit";

    static final String JDBC_CONTAIN_SQLTEMPLATE = "javax.persistence.contain.sqltemplate";

    static final String JDBC_NOTCONTAIN_SQLTEMPLATE = "javax.persistence.notcontain.sqltemplate";

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
    private DataSQLListener writeListener;

    @Resource(name = "$")
    private DataCacheListener cacheListener;

    private final Function<Class, List> fullloader = (t) -> querySheet(false, false, t, null, null, (FilterNode) null).list(true);

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
    public EntityInfo apply(Class t) {
        return loadEntityInfo(t);
    }

    private <T> EntityInfo<T> loadEntityInfo(Class<T> clazz) {
        return EntityInfo.load(clazz, this.nodeid, this.cacheForbidden, this.readPool.props, fullloader);
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
        cache.fullLoad(queryList(clazz, (FilterNode) null));
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

    @Override
    public <T> void insert(final CompletionHandler<Void, T[]> handler, final T... values) {
        insert(values);
        if (handler != null) handler.completed(null, values);
    }

    private <T> void insert(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return;
        try {
            if (!info.isVirtualEntity()) {
                final String sql = info.insertSQL;
                final PreparedStatement prestmt = info.autoGenerated
                    ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);
                final Class primaryType = info.getPrimary().type();
                final Attribute primary = info.getPrimary();
                final boolean distributed = info.distributed;
                Attribute<T, Serializable>[] attrs = info.insertAttributes;
                String[] sqls = null;
                if (distributed && !info.initedPrimaryValue && primaryType.isPrimitive()) { //由DataSource生成主键
                    synchronized (info) {
                        if (!info.initedPrimaryValue) { //初始化最大主键值
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
                            if (info.distributeTables != null) {  //是否还有其他表
                                for (final Class t : info.distributeTables) {
                                    EntityInfo<T> infox = loadEntityInfo(t);
                                    stmt = conn.createStatement();
                                    rs = stmt.executeQuery("SELECT MAX(" + info.getPrimarySQLColumn() + ") FROM " + infox.getTable()); // 必须是同一字段名
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
                        for (Attribute<T, Serializable> attr : attrs) {
                            prestmt.setObject(++i, attr.get(value));
                        }
                        prestmt.addBatch();
                    }
                } else { //调用writeListener回调接口
                    char[] sqlchars = sql.toCharArray();
                    sqls = new String[values.length];
                    CharSequence[] ps = new CharSequence[attrs.length];
                    int index = 0;
                    for (final T value : values) {
                        int i = 0;
                        if (distributed) info.createPrimaryValue(value);
                        for (Attribute<T, Serializable> attr : attrs) {
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
                if (writeListener != null) writeListener.insert(sqls);
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
                                    sb.append(formatToString(obj));
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
     */
    @Override
    public <T> void delete(T... values) {
        if (values.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) { //虚拟表只更新缓存Cache
            delete(null, info, values);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            delete(conn, info, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, T[]> handler, final T... values) {
        delete(values);
        if (handler != null) handler.completed(null, values);
    }

    private <T> void delete(final Connection conn, final EntityInfo<T> info, T... values) {
        if (values.length == 0) return;
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[values.length];
        int i = 0;
        for (final T value : values) {
            ids[i++] = (Serializable) primary.get(value);
        }
        delete(conn, info, ids);
    }

    @Override
    public <T> void delete(Class<T> clazz, Serializable... ids) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) { //虚拟表只更新缓存Cache
            delete(null, info, ids);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            delete(conn, info, ids);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, Serializable[]> handler, final Class<T> clazz, final Serializable... ids) {
        delete(clazz, ids);
        if (handler != null) handler.completed(null, ids);
    }

    private <T> void delete(final Connection conn, final EntityInfo<T> info, Serializable... keys) {
        if (keys.length == 0) return;
        try {
            if (!info.isVirtualEntity()) {
                String sql = "DELETE FROM " + info.getTable() + " WHERE " + info.getPrimarySQLColumn() + " IN " + formatToString(keys);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.delete(sql);
            }
            //------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            for (Serializable key : keys) {
                cache.delete(key);
            }
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), keys);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> void delete(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            delete(null, info, node);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            delete(conn, info, node);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        delete(clazz, node);
        if (handler != null) handler.completed(null, node);
    }

    private <T> void delete(final Connection conn, final EntityInfo<T> info, final FilterNode node) {
        try {
            if (!info.isVirtualEntity()) {
                Map<Class, String> joinTabalis = node.getJoinTabalis();
                CharSequence join = node.createSQLJoin(this, joinTabalis, info);
                CharSequence where = node.createSQLExpress(info, joinTabalis);
                String sql = "DELETE " + (this.readPool.isMysql() ? "a" : "") + " FROM " + info.getTable() + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.delete(sql);
            }
            //------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            Serializable[] ids = cache.delete(node);
            if (cacheListener != null) cacheListener.deleteCache(info.getType(), ids);
        } catch (SQLException e) {
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
     */
    @Override
    public <T> void update(T... values) {
        if (values.length == 0) return;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) values[0].getClass());
        if (info.isVirtualEntity()) {
            update(null, info, values);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            update(conn, info, values);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void update(final CompletionHandler<Void, T[]> handler, final T... values) {
        update(values);
        if (handler != null) handler.completed(null, values);
    }

    private <T> void update(final Connection conn, final EntityInfo<T> info, T... values) {
        try {
            Class clazz = info.getType();
            if (!info.isVirtualEntity()) {
                if (debug.get()) logger.finest(clazz.getSimpleName() + " update sql=" + info.updateSQL);
                final Attribute<T, Serializable> primary = info.getPrimary();
                final PreparedStatement prestmt = conn.prepareStatement(info.updateSQL);
                Attribute<T, Serializable>[] attrs = info.updateAttributes;
                String[] sqls = null;
                if (writeListener == null) {
                    for (final T value : values) {
                        int i = 0;
                        for (Attribute<T, Serializable> attr : attrs) {
                            prestmt.setObject(++i, attr.get(value));
                        }
                        prestmt.setObject(++i, primary.get(value));
                        prestmt.addBatch();
                    }
                } else {
                    char[] sqlchars = info.updateSQL.toCharArray();
                    sqls = new String[values.length];
                    CharSequence[] ps = new CharSequence[attrs.length];
                    int index = 0;
                    for (final T value : values) {
                        int i = 0;
                        for (Attribute<T, Serializable> attr : attrs) {
                            Object a = attr.get(value);
                            ps[i] = formatToString(a);
                            prestmt.setObject(++i, a);
                        }
                        prestmt.setObject(++i, primary.get(value));
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
                if (writeListener != null) writeListener.update(sqls);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            for (final T value : values) {
                cache.update(value);
            }
            if (cacheListener != null) cacheListener.updateCache(clazz, values);
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
     */
    @Override
    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            updateColumn(null, info, id, column, value);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            updateColumn(conn, info, id, column, value);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void updateColumn(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        updateColumn(clazz, id, column, value);
        if (handler != null) handler.completed(null, id);
    }

    private <T> void updateColumn(Connection conn, final EntityInfo<T> info, Serializable id, String column, Serializable value) {
        try {
            if (!info.isVirtualEntity()) {
                String sql = "UPDATE " + info.getTable() + " SET " + info.getSQLColumn(null, column) + " = "
                    + formatToString(value) + " WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.update(sql);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            T rs = cache.update(id, info.getAttribute(column), value);
            if (cacheListener != null) cacheListener.updateCache(info.getType(), rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值给对象的column对应的值+incvalue， 必须是Entity Class
     * 等价SQL: UPDATE {clazz} SET {column} = {column} + {incvalue} WHERE {primary} = {id}
     *
     * @param <T>      Entity类的泛型
     * @param clazz    Entity类
     * @param id       主键值
     * @param column   字段名
     * @param incvalue 字段加值
     */
    @Override
    public <T> void updateColumnIncrement(Class<T> clazz, Serializable id, String column, long incvalue) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            updateColumnIncrement(null, info, id, column, incvalue);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            updateColumnIncrement(conn, info, id, column, incvalue);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void updateColumnIncrement(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        updateColumnIncrement(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    private <T> void updateColumnIncrement(Connection conn, final EntityInfo<T> info, Serializable id, String column, long incvalue) {
        try {
            if (!info.isVirtualEntity()) {
                String col = info.getSQLColumn(null, column);
                String sql = "UPDATE " + info.getTable() + " SET " + col + " = " + col + " + (" + incvalue
                    + ") WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.update(sql);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            Attribute<T, Serializable> attr = info.getAttribute(column);
            T value = cache.updateColumnIncrement(id, attr, incvalue);
            if (value != null && cacheListener != null) cacheListener.updateCache(info.getType(), value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值给对象的column对应的值 &#38; andvalue， 必须是Entity Class
     * 等价SQL: UPDATE {clazz} SET {column} = {column} &#38; {incvalue} WHERE {primary} = {id}
     *
     * @param <T>      Entity类的泛型
     * @param clazz    Entity类
     * @param id       主键值
     * @param column   字段名
     * @param andvalue 字段与值
     */
    @Override
    public <T> void updateColumnAnd(Class<T> clazz, Serializable id, String column, long andvalue) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            updateColumnAnd(null, info, id, column, andvalue);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            updateColumnAnd(conn, info, id, column, andvalue);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void updateColumnAnd(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        updateColumnAnd(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    private <T> void updateColumnAnd(Connection conn, final EntityInfo<T> info, Serializable id, String column, long andvalue) {
        try {
            if (!info.isVirtualEntity()) {
                String col = info.getSQLColumn(null, column);
                String sql = "UPDATE " + info.getTable() + " SET " + col + " = " + col + " & (" + andvalue
                    + ") WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.update(sql);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            Attribute<T, Serializable> attr = info.getAttribute(column);
            T value = cache.updateColumnAnd(id, attr, andvalue);
            if (value != null && cacheListener != null) cacheListener.updateCache(info.getType(), value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 根据主键值给对象的column对应的值 | andvalue， 必须是Entity Class
     * 等价SQL: UPDATE {clazz} SET {column} = {column} | {incvalue} WHERE {primary} = {id}
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param id      主键值
     * @param column  字段名
     * @param orvalue 字段或值
     */
    @Override
    public <T> void updateColumnOr(Class<T> clazz, Serializable id, String column, long orvalue) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (info.isVirtualEntity()) {
            updateColumnOr(null, info, id, column, orvalue);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            updateColumnOr(conn, info, id, column, orvalue);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void updateColumnOr(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        updateColumnOr(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    private <T> void updateColumnOr(Connection conn, final EntityInfo<T> info, Serializable id, String column, long orvalue) {
        try {
            if (!info.isVirtualEntity()) {
                String col = info.getSQLColumn(null, column);
                String sql = "UPDATE " + info.getTable() + " SET " + col + " = " + col + " | (" + orvalue
                    + ") WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
                if (debug.get()) logger.finest(info.getType().getSimpleName() + " update sql=" + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.update(sql);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            Attribute<T, Serializable> attr = info.getAttribute(column);
            T value = cache.updateColumnOr(id, attr, orvalue);
            if (value != null && cacheListener != null) cacheListener.updateCache(info.getType(), value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>     Entity类的泛型
     * @param value   Entity对象
     * @param columns 需要更新的字段
     */
    @Override
    public <T> void updateColumns(final T value, final String... columns) {
        final EntityInfo<T> info = loadEntityInfo((Class<T>) value.getClass());
        if (info.isVirtualEntity()) {
            updateColumns(null, info, value, columns);
            return;
        }
        Connection conn = createWriteSQLConnection();
        try {
            updateColumns(conn, info, value, columns);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void updateColumns(final CompletionHandler<Void, T> handler, final T value, final String... columns) {
        updateColumns(value, columns);
        if (handler != null) handler.completed(null, value);
    }

    private <T> void updateColumns(final Connection conn, final EntityInfo<T> info, final T value, final String... columns) {
        if (value == null || columns.length < 1) return;
        try {
            final Class<T> clazz = (Class<T>) value.getClass();
            StringBuilder setsql = new StringBuilder();
            final Serializable id = info.getPrimary().get(value);
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            final boolean virtual = info.isVirtualEntity();
            for (String col : columns) {
                Attribute<T, Serializable> attr = info.getUpdateAttribute(col);
                if (attr == null) continue;
                attrs.add(attr);
                if (!virtual) {
                    if (setsql.length() > 0) setsql.append(", ");
                    setsql.append(info.getSQLColumn(null, col)).append(" = ").append(formatToString(attr.get(value)));
                }
            }
            if (!virtual) {
                String sql = "UPDATE " + info.getTable() + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(id);
                if (debug.get()) logger.finest(value.getClass().getSimpleName() + ": " + sql);
                final Statement stmt = conn.createStatement();
                stmt.execute(sql);
                stmt.close();
                if (writeListener != null) writeListener.update(sql);
            }
            //---------------------------------------------------
            final EntityCache<T> cache = info.getCache();
            if (cache == null) return;
            T rs = cache.update(value, attrs);
            if (cacheListener != null) cacheListener.updateCache(clazz, rs);
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
        return getNumberResult(entityClass, func, column, (FilterNode) null);
    }

    @Override
    public void getNumberResult(final CompletionHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final String column) {
        Number rs = getNumberResult(entityClass, func, column);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        getNumberResult(handler, entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        final Connection conn = createReadSQLConnection();
        try {
            final EntityInfo info = loadEntityInfo(entityClass);
            final EntityCache cache = info.getCache();
            if (cache != null && (info.isVirtualEntity() || cache.isFullLoaded())) {
                if (node == null || node.isCacheUseable(this)) {
                    return cache.getNumberResult(func, column, node);
                }
            }
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : ("a." + column))) + " FROM " + info.getTable() + " a"
                + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(entityClass.getSimpleName() + " single sql=" + sql);
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

    @Override
    public void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        Number rs = getNumberResult(entityClass, func, column, node);
        if (handler != null) handler.completed(rs, node);
    }

    //-----------------------queryColumnMap-----------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        Map<K, N> map = queryColumnMap(entityClass, keyColumn, func, funcColumn);
        if (handler != null) handler.completed(map, funcColumn);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final Connection conn = createReadSQLConnection();
        try {
            final EntityInfo info = loadEntityInfo(entityClass);
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
                + " FROM " + info.getTable() + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + sqlkey;
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
            throw new RuntimeException(e);
        } finally {
            if (conn != null) closeSQLConnection(conn);
        }
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node) {
        Map<K, N> map = queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
        if (handler != null) handler.completed(map, node);
    }

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param pk    主键值
     * @return Entity对象
     */
    @Override
    public <T> T find(Class<T> clazz, Serializable pk) {
        return find(clazz, (SelectColumn) null, pk);
    }

    @Override
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final Serializable pk) {
        T rs = find(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
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
            final String sql = "SELECT * FROM " + info.getTable() + " WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(pk);
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            T rs = set.next() ? info.getValue(sels, set) : null;
            set.close();
            ps.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final SelectColumn selects, final Serializable pk) {
        T rs = find(clazz, selects, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return find(clazz, null, FilterNode.create(column, key));
    }

    @Override
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final String column, final Serializable key) {
        T rs = find(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterBean bean) {
        return find(clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        T rs = find(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final FilterNode node) {
        return find(clazz, null, node);
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        T rs = find(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        T rs = find(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
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
            final String sql = "SELECT a.* FROM " + info.getTable() + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " find sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            T rs = set.next() ? info.getValue(sels, set) : null;
            set.close();
            ps.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        T rs = find(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
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
            final String sql = "SELECT COUNT(*) FROM " + info.getTable() + " WHERE " + info.getPrimarySQLColumn() + " = " + formatToString(pk);
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " exists sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void exists(final CompletionHandler<Boolean, Serializable> handler, final Class<T> clazz, final Serializable pk) {
        boolean rs = exists(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        boolean rs = exists(clazz, node);
        if (handler != null) handler.completed(rs, node);
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
            final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable() + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
            if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " exists sql=" + sql);
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            boolean rs = set.next() ? (set.getInt(1) > 0) : false;
            set.close();
            ps.close();
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
        }
    }

    @Override
    public <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        boolean rs = exists(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return queryColumnSet(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, bean));
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return new LinkedHashSet<>(queryColumnList(selectedColumn, clazz, node));
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        HashSet<V> rs = queryColumnSet(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        return queryColumnList(selectedColumn, clazz, FilterNode.create(column, key));
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key) {
        List<V> rs = queryColumnList(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return (List<V>) queryColumnSheet(selectedColumn, clazz, null, bean).list(true);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        final FilterNode node = FilterNodeBean.createFilterNode(bean);
        List<V> rs = queryColumnList(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return (List<V>) queryColumnSheet(selectedColumn, clazz, null, node).list(true);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        List<V> rs = queryColumnList(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
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
     * @return 字段集合
     */
    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        final FilterNode node = FilterNodeBean.createFilterNode(bean);
        Sheet<V> rs = queryColumnSheet(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> sheet = querySheet(true, true, clazz, SelectColumn.createIncludes(selectedColumn), flipper, node);
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
    public <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<V> rs = queryColumnSheet(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
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
     * @return Entity对象的集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return queryList(clazz, FilterNode.create(column, key));
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, final Serializable key) {
        List<T> rs = queryList(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param bean  过滤Bean
     * @return Entity对象集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, (SelectColumn) null, bean);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        List<T> rs = queryList(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterNode node) {
        List<T> rs = queryList(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合， 对象只填充或排除SelectField指定的字段
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param selects 收集的字段
     * @param bean    过滤Bean
     * @return Entity对象的集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, selects, (Flipper) null, bean);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        List<T> rs = queryList(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryList(clazz, selects, (Flipper) null, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return queryList(clazz, flipper, FilterNode.create(column, key));
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        List<T> rs = queryList(clazz, flipper, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, null, flipper, bean);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        List<T> rs = queryList(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryList(clazz, null, flipper, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        List<T> rs = queryList(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(true, false, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean)).list(true);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        List<T> rs = queryList(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, clazz, selects, flipper, node).list(true);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    //-----------------------sheet----------------------------
    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     * @return Entity对象的集合
     */
    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, null, flipper, bean);
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        Sheet<T> rs = querySheet(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheet(clazz, null, flipper, node);
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> rs = querySheet(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据， 对象只填充或排除SelectField指定的字段
     *
     * @param <T>     Entity类的泛型
     * @param clazz   Entity类
     * @param selects 收集的字段集合
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     * @return Entity对象的集合
     */
    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(true, true, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        Sheet<T> rs = querySheet(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, true, clazz, selects, flipper, node);
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        Sheet<T> rs = querySheet(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    private <T> Sheet<T> querySheet(final boolean readcache, final boolean needtotal, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readcache && cache != null && cache.isFullLoaded()) {
            if (node == null || node.isCacheUseable(this)) {
                if (debug.get() && info.isLoggable(Level.FINEST)) logger.finest(clazz.getSimpleName() + " cache query predicate = " + (node == null ? null : node.createPredicate(cache)));
                Sheet<T> sheet = cache.querySheet(needtotal, selects, flipper, node);
                if (!sheet.isEmpty() || info.isVirtualEntity()) return sheet;
            }
        }
        final Connection conn = createReadSQLConnection();
        try {
            final SelectColumn sels = selects;
            final List<T> list = new ArrayList();
            final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
            final CharSequence join = node == null ? null : node.createSQLJoin(this, joinTabalis, info);
            final CharSequence where = node == null ? null : node.createSQLExpress(info, joinTabalis);
            final String sql = "SELECT a.* FROM " + info.getTable() + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + info.createSQLOrderby(flipper);
            if (debug.get() && info.isLoggable(Level.FINEST))
                logger.finest(clazz.getSimpleName() + " query sql=" + sql + (flipper == null ? "" : (" LIMIT " + flipper.index() + "," + flipper.getSize())));
            final PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            final ResultSet set = ps.executeQuery();
            if (flipper != null && flipper.index() > 0) set.absolute(flipper.index());
            final int limit = flipper == null ? Integer.MAX_VALUE : flipper.getSize();
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
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeSQLConnection(conn);
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
