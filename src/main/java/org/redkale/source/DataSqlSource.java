/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static org.redkale.source.DataResultSet.formatColumnValue;
import org.redkale.util.*;

/**
 *
 * 关系型sql数据库的数据源， 比DataSource多了操作sql语句的接口。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataSqlSource extends DataSource {

    /**
     * 执行多条原生无参数的sql
     *
     * @param sqls 无参数的sql语句
     *
     * @return 执行条数
     */
    public int[] nativeUpdates(String... sqls);

    /**
     * 执行多条原生无参数的sql
     *
     * @param sqls 无参数的sql语句
     *
     * @return 执行条数
     */
    public CompletableFuture<int[]> nativeUpdatesAsync(String... sqls);

    /**
     * 执行原生无参数的sql
     *
     * @param sql 无参数的sql语句
     *
     * @return 执行条数
     */
    public int nativeUpdate(String sql);

    /**
     * 执行原生无参数的sql
     *
     * @param sql 无参数的sql语句
     *
     * @return 执行条数
     */
    public CompletableFuture<Integer> nativeUpdateAsync(String sql);

    /**
     * 执行原生带参数的sql
     *
     * @param sql    带参数的sql语句
     * @param params 参数值集合
     *
     * @return 执行条数
     */
    public int nativeUpdate(String sql, Map<String, Object> params);

    /**
     * 执行原生带参数的sql
     *
     * @param sql    带参数的sql语句
     * @param params 参数值集合
     *
     * @return 执行条数
     */
    public CompletableFuture<Integer> nativeUpdateAsync(String sql, Map<String, Object> params);

    /**
     * 通过原生的sql查询结果
     *
     * @param <V>      泛型
     * @param sql      无参数的sql语句
     * @param consumer BiConsumer 参数1: connection, 参数2: statement
     * @param handler  DataResultSet的回调函数
     *
     * @return 结果对象
     */
    public <V> V nativeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler);

    /**
     * 通过原生的sql查询结果
     *
     * @param <V>      泛型
     * @param sql      无参数的sql语句
     * @param consumer BiConsumer 参数1: connection, 参数2: statement
     * @param handler  DataResultSet的回调函数
     *
     * @return 结果对象
     */
    public <V> CompletableFuture<V> nativeQueryAsync(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler);

    /**
     * 通过原生带参数的sql查询结果
     *
     * @param <V>      泛型
     * @param sql      带参数的sql语句
     * @param consumer BiConsumer 参数1: connection, 参数2: statement
     * @param handler  DataResultSet的回调函数
     * @param params   参数值集合
     *
     * @return 结果对象
     */
    public <V> V nativeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler, Map<String, Object> params);

    /**
     * 通过原生带参数的sql查询结果
     *
     * @param <V>      泛型
     * @param sql      带参数的sql语句
     * @param consumer BiConsumer 参数1: connection, 参数2: statement
     * @param handler  DataResultSet的回调函数
     * @param params   参数值集合
     *
     * @return 结果对象
     */
    public <V> CompletableFuture<V> nativeQueryAsync(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler, Map<String, Object> params);

    public <V> Sheet<V> nativeQuerySheet(Class<V> type, String sql, Flipper flipper, Map<String, Object> params);

    public <V> CompletableFuture<Sheet<V>> nativeQuerySheetAsync(Class<V> type, String sql, Flipper flipper, Map<String, Object> params);

    //----------------------------- 无参数 -----------------------------
    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler) {
        return nativeQuery(sql, null, handler);
    }

    default <V> CompletableFuture<V> nativeQueryAsync(String sql, Function<DataResultSet, V> handler) {
        return nativeQueryAsync(sql, null, handler);
    }

    default <V> V nativeQueryOne(Class<V> type, String sql) {
        return nativeQuery(sql, rset -> EntityBuilder.getOneValue(type, rset));
    }

    default <V> CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql) {
        return nativeQueryAsync(sql, rset -> EntityBuilder.getOneValue(type, rset));
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql) {
        return nativeQuery(sql, rset -> EntityBuilder.getListValue(type, rset));
    }

    default <V> CompletableFuture<List<V>> nativeQueryListAsync(Class<V> type, String sql) {
        return nativeQueryAsync(sql, rset -> EntityBuilder.getListValue(type, rset));
    }

    default <V> Sheet<V> nativeQuerySheet(Class<V> type, String sql, Flipper flipper) {
        return nativeQuerySheet(type, sql, flipper, Collections.emptyMap());
    }

    default <V> CompletableFuture<Sheet<V>> nativeQuerySheetAsync(Class<V> type, String sql, Flipper flipper) {
        return nativeQuerySheetAsync(type, sql, flipper, Collections.emptyMap());
    }

    default <K, V> Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql) {
        return nativeQuery(sql, rset -> {
            Map<K, V> map = new LinkedHashMap<K, V>();
            while (rset.next()) {
                if (!rset.wasNull()) {
                    map.put((K) formatColumnValue(keyType, rset.getObject(1)), (V) formatColumnValue(valType, rset.getObject(2)));
                }
            }
            return map;
        });
    }

    default <K, V> CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql) {
        return nativeQueryAsync(sql, rset -> {
            Map<K, V> map = new LinkedHashMap<K, V>();
            while (rset.next()) {
                if (!rset.wasNull()) {
                    map.put((K) formatColumnValue(keyType, rset.getObject(1)), (V) formatColumnValue(valType, rset.getObject(2)));
                }
            }
            return map;
        });
    }

    default Map<String, String> nativeQueryStrStrMap(String sql) {
        return nativeQueryMap(String.class, String.class, sql);
    }

    default CompletableFuture<Map<String, String>> nativeQueryStrStrMapAsync(String sql) {
        return nativeQueryMapAsync(String.class, String.class, sql);
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql) {
        return nativeQueryMap(Integer.class, String.class, sql);
    }

    default CompletableFuture<Map<Integer, String>> nativeQueryIntStrMapAsync(String sql) {
        return nativeQueryMapAsync(Integer.class, String.class, sql);
    }

    //----------------------------- Map<String, Object> -----------------------------
    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler, Map<String, Object> params) {
        return nativeQuery(sql, null, handler, params);
    }

    default <V> CompletableFuture<V> nativeQueryAsync(String sql, Function<DataResultSet, V> handler, Map<String, Object> params) {
        return nativeQueryAsync(sql, null, handler, params);
    }

    default <V> V nativeQueryOne(Class<V> type, String sql, Map<String, Object> params) {
        return nativeQuery(sql, rset -> {
            if (!rset.next()) {
                return null;
            }
            if (type == byte[].class || type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                return (V) formatColumnValue(type, rset.getObject(1));
            }
            return EntityBuilder.load(type).getObjectValue(rset);
        }, params);
    }

    default <V> CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql, Map<String, Object> params) {
        return nativeQueryAsync(sql, rset -> {
            if (!rset.next()) {
                return null;
            }
            if (type == byte[].class || type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                return (V) formatColumnValue(type, rset.getObject(1));
            }
            return EntityBuilder.load(type).getObjectValue(rset);
        }, params);
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql, Map<String, Object> params) {
        return nativeQuery(sql, rset -> {
            if (type == byte[].class || type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                List<V> list = new ArrayList<>();
                while (rset.next()) {
                    list.add(rset.wasNull() ? null : (V) formatColumnValue(type, rset.getObject(1)));
                }
                return list;
            }
            return EntityBuilder.load(type).getObjectList(rset);
        }, params);
    }

    default <V> CompletableFuture<List<V>> nativeQueryListAsync(Class<V> type, String sql, Map<String, Object> params) {
        return nativeQueryAsync(sql, rset -> {
            if (type == byte[].class || type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                List<V> list = new ArrayList<>();
                while (rset.next()) {
                    list.add(rset.wasNull() ? null : (V) formatColumnValue(type, rset.getObject(1)));
                }
                return list;
            }
            return EntityBuilder.load(type).getObjectList(rset);
        }, params);
    }

    default <K, V> Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params) {
        return nativeQuery(sql, rset -> {
            Map<K, V> map = new LinkedHashMap<K, V>();
            while (rset.next()) {
                if (!rset.wasNull()) {
                    map.put((K) formatColumnValue(keyType, rset.getObject(1)), (V) formatColumnValue(valType, rset.getObject(2)));
                }
            }
            return map;
        }, params);
    }

    default <K, V> CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params) {
        return nativeQueryAsync(sql, rset -> {
            Map<K, V> map = new LinkedHashMap<K, V>();
            while (rset.next()) {
                if (!rset.wasNull()) {
                    map.put((K) formatColumnValue(keyType, rset.getObject(1)), (V) formatColumnValue(valType, rset.getObject(2)));
                }
            }
            return map;
        }, params);
    }

    default Map<String, String> nativeQueryStrStrMap(String sql, Map<String, Object> params) {
        return nativeQueryMap(String.class, String.class, sql, params);
    }

    default CompletableFuture<Map<String, String>> nativeQueryStrStrMapAsync(String sql, Map<String, Object> params) {
        return nativeQueryMapAsync(String.class, String.class, sql, params);
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql, Map<String, Object> params) {
        return nativeQueryMap(Integer.class, String.class, sql, params);
    }

    default CompletableFuture<Map<Integer, String>> nativeQueryIntStrMapAsync(String sql, Map<String, Object> params) {
        return nativeQueryMapAsync(Integer.class, String.class, sql, params);
    }

    //----------------------------- JavaBean -----------------------------
    default int nativeUpdate(String sql, Serializable bean) {
        return nativeUpdate(sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default CompletableFuture<Integer> nativeUpdateAsync(String sql, Serializable bean) {
        return nativeUpdateAsync(sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler, Serializable bean) {
        return nativeQuery(sql, null, handler, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> CompletableFuture<V> nativeQueryAsync(String sql, Function<DataResultSet, V> handler, Serializable bean) {
        return nativeQueryAsync(sql, null, handler, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> V nativeQueryOne(Class<V> type, String sql, Serializable bean) {
        return nativeQueryOne(type, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql, Serializable bean) {
        return nativeQueryOneAsync(type, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql, Serializable bean) {
        return nativeQueryList(type, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> CompletableFuture<List<V>> nativeQueryListAsync(Class<V> type, String sql, Serializable bean) {
        return nativeQueryListAsync(type, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <K, V> Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Serializable bean) {
        return nativeQueryMap(keyType, valType, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <K, V> CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql, Serializable bean) {
        return nativeQueryMapAsync(keyType, valType, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default Map<String, String> nativeQueryStrStrMap(String sql, Serializable bean) {
        return nativeQueryMap(String.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default CompletableFuture<Map<String, String>> nativeQueryStrStrMapAsync(String sql, Serializable bean) {
        return nativeQueryMapAsync(String.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql, Serializable bean) {
        return nativeQueryMap(Integer.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default CompletableFuture<Map<Integer, String>> nativeQueryIntStrMapAsync(String sql, Serializable bean) {
        return nativeQueryMapAsync(Integer.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> Sheet<V> nativeQuerySheet(Class<V> type, String sql, Flipper flipper, Serializable bean) {
        return nativeQuerySheet(type, sql, flipper, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

    default <V> CompletableFuture<Sheet<V>> nativeQuerySheetAsync(Class<V> type, String sql, Flipper flipper, Serializable bean) {
        return nativeQuerySheetAsync(type, sql, flipper, (Map<String, Object>) Copier.copyToMap(bean, Copier.OPTION_SKIP_NULL_VALUE));
    }

}
