/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.stream.Stream;
import javax.persistence.Entity;
import org.redkale.convert.json.JsonConvert;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * DataSource的S抽象实现类 <br>
 * 注意: 所有的操作只能作用在一张表上，不能同时变更多张表
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public abstract class AbstractDataSource extends AbstractService implements DataSource, AutoCloseable, Resourcable {

    //@since 2.7.0 格式: x.x.x.x:yyyy
    public static final String DATA_SOURCE_PROXY_ADDRESS = "proxy-address";

    //@since 2.7.0 值: SOCKS/DIRECT/HTTP，默认值: http
    public static final String DATA_SOURCE_PROXY_TYPE = "proxy-type";

    //@since 2.7.0 
    public static final String DATA_SOURCE_PROXY_USER = "proxy-user";

    //@since 2.7.0 
    public static final String DATA_SOURCE_PROXY_PASSWORD = "proxy-password";

    //@since 2.7.0 值: true/false，默认值: true
    public static final String DATA_SOURCE_PROXY_ENABLE = "proxy-enable";

    //@since 2.7.0
    public static final String DATA_SOURCE_URL = "url";

    //@since 2.7.0
    public static final String DATA_SOURCE_USER = "user";

    //@since 2.7.0
    public static final String DATA_SOURCE_PASSWORD = "password";

    //@since 2.7.0
    public static final String DATA_SOURCE_ENCODING = "encoding";

    //@since 2.7.0
    public static final String DATA_SOURCE_MAXCONNS = "maxconns";

    //@since 2.7.0
    public static final String DATA_SOURCE_PIPELINES = "pipelines";

    //@since 2.7.0
    public static final String DATA_SOURCE_AUTOMAPPING = "auto-mapping";

    //@since 2.7.0
    public static final String DATA_SOURCE_TABLE_AUTODDL = "table-autoddl";

    //@since 2.7.0
    public static final String DATA_SOURCE_CONNECTTIMEOUT_SECONDS = "connecttimeout";

    //@since 2.7.0 //NONE ALL 设置@Cacheable是否生效
    public static final String DATA_SOURCE_CACHEMODE = "cachemode";

    //@since 2.7.0
    public static final String DATA_SOURCE_CONNECTIONS_CAPACITY = "connections-bufcapacity";

    //@since 2.7.0
    public static final String DATA_SOURCE_CONTAIN_SQLTEMPLATE = "contain-sqltemplate";

    //@since 2.7.0
    public static final String DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE = "notcontain-sqltemplate";

    //@since 2.7.0
    public static final String DATA_SOURCE_TABLENOTEXIST_SQLSTATES = "tablenotexist-sqlstates";

    //@since 2.7.0
    public static final String DATA_SOURCE_TABLECOPY_SQLTEMPLATE = "tablecopy-sqltemplate";

    public static String parseDbtype(String url) {
        String dbtype = null;
        if (url == null) return dbtype;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("search://") || url.startsWith("searchs://")) { //elasticsearch or opensearch
            dbtype = "search";
        } else {
            /* jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串 */
            int pos = url.indexOf("://");
            if (pos > 0) {
                String url0 = url.substring(0, pos);
                pos = url0.lastIndexOf(':');
                if (pos > 0) {
                    dbtype = url0.substring(pos + 1);
                } else { //mongodb://127.0.01:27017
                    dbtype = url0;
                }
            } else { //jdbc:oracle:thin:@localhost:1521
                String url0 = url.substring(url.indexOf(":") + 1);
                pos = url0.indexOf(':');
                if (pos > 0) dbtype = url0.substring(0, pos);
            }
        }
        if ("mariadb".equals(dbtype)) return "mysql";
        return dbtype;
    }

    protected UrlInfo parseUrl(final String url) {
        final UrlInfo info = new UrlInfo();
        info.url = url;
        if (url.startsWith("jdbc:h2:")) return info;
        String url0 = url.substring(url.indexOf("://") + 3);
        int pos = url0.indexOf('?'); //127.0.0.1:5432/db?charset=utr8&xxx=yy
        if (pos > 0) {
            String params = url0.substring(pos + 1).replace("&amp;", "&");
            for (String param : params.split("&")) {
                int p = param.indexOf('=');
                if (p < 1) continue;
                info.attributes.put(param.substring(0, p), param.substring(p + 1));
            }
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf('/'); //127.0.0.1:5432/db
        if (pos > 0) {
            info.database = url0.substring(pos + 1);
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf(':');
        if (pos > 0) {
            info.servaddr = new InetSocketAddress(url0.substring(0, pos), Integer.parseInt(url0.substring(pos + 1)));
        } else if (url.startsWith("http://")) {
            info.servaddr = new InetSocketAddress(url0, 80);
        } else if (url.startsWith("https://")) {
            info.servaddr = new InetSocketAddress(url0, 443);
        } else {
            throw new RuntimeException(url + " parse port error");
        }
        return info;
    }

    protected static class UrlInfo {

        public Properties attributes = new Properties();

        public String url;

        public String database;

        public InetSocketAddress servaddr;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    /**
     * 是否虚拟化的持久对象
     *
     * @param info EntityInfo
     *
     * @return boolean
     */
    protected boolean isOnlyCache(EntityInfo info) {
        return info.isVirtualEntity();
    }

    /**
     * 是否可以使用缓存，一般包含关联查询就不使用缓存
     *
     * @param node          过滤条件
     * @param entityApplyer 函数
     *
     * @return boolean
     */
    protected boolean isCacheUseable(FilterNode node, Function<Class, EntityInfo> entityApplyer) {
        return node.isCacheUseable(entityApplyer);
    }

    /**
     * 生成过滤函数
     *
     * @param <T>   泛型
     * @param <E>   泛型
     * @param node  过滤条件
     * @param cache 缓存
     *
     * @return Predicate
     */
    protected <T, E> Predicate<T> createPredicate(FilterNode node, EntityCache<T> cache) {
        return node.createPredicate(cache);
    }

    /**
     * 根据ResultSet获取对象
     *
     * @param <T>  泛型
     * @param info EntityInfo
     * @param sels 过滤字段
     * @param row  ResultSet
     *
     * @return 对象
     */
    protected <T> T getEntityValue(EntityInfo<T> info, final SelectColumn sels, final EntityInfo.DataResultSetRow row) {
        return sels == null ? info.getFullEntityValue(row) : info.getEntityValue(sels, row);
    }

    /**
     * 根据ResultSet获取对象
     *
     * @param <T>                泛型
     * @param info               EntityInfo
     * @param constructorAttrs   构造函数字段
     * @param unconstructorAttrs 非构造函数字段
     * @param row                ResultSet
     *
     * @return 对象
     */
    protected <T> T getEntityValue(EntityInfo<T> info, final Attribute<T, Serializable>[] constructorAttrs, final Attribute<T, Serializable>[] unconstructorAttrs, final EntityInfo.DataResultSetRow row) {
        return info.getEntityValue(constructorAttrs, unconstructorAttrs, row);
    }

    /**
     * 根据翻页参数构建排序SQL
     *
     * @param <T>     泛型
     * @param info    EntityInfo
     * @param flipper 翻页参数
     *
     * @return SQL
     */
    protected <T> String createSQLOrderby(EntityInfo<T> info, Flipper flipper) {
        return info.createSQLOrderby(flipper);
    }

    /**
     * 根据过滤条件生成关联表与别名的映射关系
     *
     * @param node 过滤条件
     *
     * @return Map
     */
    protected Map<Class, String> getJoinTabalis(FilterNode node) {
        return node == null ? null : node.getJoinTabalis();
    }

    /**
     * 加载指定类的EntityInfo
     *
     * @param <T>            泛型
     * @param clazz          类
     * @param cacheForbidden 是否屏蔽缓存
     * @param props          配置信息
     * @param fullloader     加载器
     *
     * @return EntityInfo
     */
    protected <T> EntityInfo<T> loadEntityInfo(Class<T> clazz, final boolean cacheForbidden, final Properties props, BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader) {
        return EntityInfo.load(clazz, cacheForbidden, props, this, fullloader);
    }

    /**
     * 检查对象是否都是同一个Entity类
     *
     * @param <T>     泛型
     * @param action  操作
     * @param async   是否异步
     * @param entitys 对象集合
     *
     * @return CompletableFuture
     */
    protected <T> CompletableFuture checkEntity(String action, boolean async, T... entitys) {
        if (entitys.length < 1) return null;
        Class clazz = null;
        for (T val : entitys) {
            if (clazz == null) {
                clazz = val.getClass();
                if (clazz.getAnnotation(Entity.class) == null) throw new RuntimeException("Entity Class " + clazz + " must be on annotation @Entity");
                continue;
            }
            if (clazz != val.getClass()) {
                if (async) {
                    CompletableFuture future = new CompletableFuture<>();
                    future.completeExceptionally(new RuntimeException("DataSource." + action + " must the same Class Entity, but diff is " + clazz + " and " + val.getClass()));
                    return future;
                }
                throw new RuntimeException("DataSource." + action + " must the same Class Entity, but diff is " + clazz + " and " + val.getClass());
            }
        }
        return null;
    }

    @Override
    public final <T> int insert(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) return 0;
        return insert(entitys.toArray());
    }

    @Override
    public final <T> int insert(final Stream<T> entitys) {
        if (entitys == null) return 0;
        return insert(entitys.toArray());
    }

    @Override
    public final <T> CompletableFuture<Integer> insertAsync(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) return CompletableFuture.completedFuture(0);
        return insertAsync(entitys.toArray());
    }

    @Override
    public final <T> CompletableFuture<Integer> insertAsync(final Stream<T> entitys) {
        if (entitys == null) return CompletableFuture.completedFuture(0);
        return insertAsync(entitys.toArray());
    }

    @Override
    public <T> CompletableFuture<Integer> clearTableAsync(final Class<T> clazz) {
        return clearTableAsync(clazz, (FilterNode) null);
    }

    @Override
    public <T> int dropTable(Class<T> clazz) {
        return dropTable(clazz, (FilterNode) null);
    }

    @Override
    public <T> CompletableFuture<Integer> dropTableAsync(final Class<T> clazz) {
        return dropTableAsync(clazz, (FilterNode) null);
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
    public <T> int updateColumn(final T entity, final String... columns) {
        return updateColumn(entity, SelectColumn.includes(columns));
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final String... columns) {
        return updateColumnAsync(entity, SelectColumn.includes(columns));
    }

    @Override
    public <T> int updateColumn(final T entity, final FilterNode node, final String... columns) {
        return updateColumn(entity, node, SelectColumn.includes(columns));
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final FilterNode node, final String... columns) {
        return updateColumnAsync(entity, node, SelectColumn.includes(columns));
    }

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
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn) {
        return queryColumnMap(entityClass, funcNodes, groupByColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumn, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn, final FilterBean bean) {
        return queryColumnMap(entityClass, funcNodes, groupByColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn, final FilterBean bean) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return queryColumnMap(entityClass, funcNodes, groupByColumns, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumns, (FilterNode) null);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterBean bean) {
        return queryColumnMap(entityClass, funcNodes, groupByColumns, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterBean bean) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumns, FilterNodeBean.createFilterNode(bean));
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
    public <T> T find(final Class<T> clazz, final String column, final Serializable colval) {
        return find(clazz, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final String column, final Serializable colval) {
        return findAsync(clazz, null, FilterNode.filter(column, colval));
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
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterBean bean) {
        return existsAsync(clazz, FilterNodeBean.createFilterNode(bean));
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval) {
        return queryColumnSet(selectedColumn, clazz, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval) {
        return queryColumnSetAsync(selectedColumn, clazz, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnSetAsync(selectedColumn, clazz, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnSet(selectedColumn, clazz, null, node);
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnSetAsync(selectedColumn, clazz, null, node);
    }

    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSetAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval) {
        return queryColumnList(selectedColumn, clazz, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval) {
        return queryColumnListAsync(selectedColumn, clazz, null, FilterNode.filter(column, colval));
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

    /**
     * 根据指定参数查询对象某个字段的集合
     *
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
     * 根据指定字段值查询对象集合
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final String column, final Serializable colval) {
        return querySet(clazz, (SelectColumn) null, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final String column, final Serializable colval) {
        return querySetAsync(clazz, (SelectColumn) null, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz) {
        return querySet(clazz, (SelectColumn) null, null, (FilterNode) null);
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz) {
        return querySetAsync(clazz, (SelectColumn) null, null, (FilterNode) null);
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
    public <T> Set<T> querySet(final Class<T> clazz, final FilterBean bean) {
        return querySet(clazz, (SelectColumn) null, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final FilterBean bean) {
        return querySetAsync(clazz, (SelectColumn) null, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final FilterNode node) {
        return querySet(clazz, (SelectColumn) null, null, node);
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final FilterNode node) {
        return querySetAsync(clazz, (SelectColumn) null, null, node);
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
    public <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return querySet(clazz, selects, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, SelectColumn selects, final FilterBean bean) {
        return querySetAsync(clazz, selects, null, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return querySet(clazz, selects, null, node);
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, SelectColumn selects, final FilterNode node) {
        return querySetAsync(clazz, selects, null, node);
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval) {
        return querySet(clazz, null, flipper, FilterNode.filter(column, colval));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval) {
        return querySetAsync(clazz, null, flipper, FilterNode.filter(column, colval));
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySet(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySetAsync(clazz, null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySet(clazz, null, flipper, node);
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySetAsync(clazz, null, flipper, node);
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySetAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     *
     * @return Entity对象的集合
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable colval) {
        return queryList(clazz, (SelectColumn) null, null, FilterNode.filter(column, colval));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final String column, final Serializable colval) {
        return queryListAsync(clazz, (SelectColumn) null, null, FilterNode.filter(column, colval));
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
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval) {
        return queryList(clazz, null, flipper, FilterNode.filter(column, colval));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval) {
        return queryListAsync(clazz, null, flipper, FilterNode.filter(column, colval));
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

}
