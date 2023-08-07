/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.function.*;
import static org.redkale.source.DataResultSet.formatColumnValue;
import org.redkale.util.Copier;

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

    public int[] nativeUpdates(String... sqls);

    //----------------------------- 无参数 -----------------------------
    public int nativeUpdate(String sql);

    //BiConsumer 参数1: connection, 参数2: statement
    public <V> V nativeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler);

    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler) {
        return nativeQuery(sql, null, handler);
    }

    default <V> V nativeQueryOne(Class<V> type, String sql) {
        return nativeQuery(sql, rset -> {
            if (!rset.next()) {
                return null;
            }
            if (type == byte[].class || type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                return (V) formatColumnValue(type, rset.getObject(1));
            }
            return EntityBuilder.load(type).getObjectValue(rset);
        });
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql) {
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
        });
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

    default Map<String, String> nativeQueryStrStrMap(String sql) {
        return nativeQueryMap(String.class, String.class, sql);
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql) {
        return nativeQueryMap(Integer.class, String.class, sql);
    }

    //----------------------------- Map<String, Object> -----------------------------
    public int nativeUpdate(String sql, Map<String, Object> params);

    //BiConsumer 参数1: connection, 参数2: statement
    public <V> V nativeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler, Map<String, Object> params);

    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler, Map<String, Object> params) {
        return nativeQuery(sql, null, handler, params);
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

    default Map<String, String> nativeQueryStrStrMap(String sql, Map<String, Object> params) {
        return nativeQueryMap(String.class, String.class, sql, params);
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql, Map<String, Object> params) {
        return nativeQueryMap(Integer.class, String.class, sql, params);
    }

    //----------------------------- JavaBean -----------------------------
    default int nativeUpdate(String sql, Serializable bean) {
        return nativeUpdate(sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default <V> V nativeQuery(String sql, Function<DataResultSet, V> handler, Serializable bean) {
        return nativeQuery(sql, null, handler, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default <V> V nativeQueryOne(Class<V> type, String sql, Serializable bean) {
        return nativeQueryOne(type, sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql, Serializable bean) {
        return nativeQueryList(type, sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default <K, V> Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Serializable bean) {
        return nativeQueryMap(keyType, valType, sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default Map<String, String> nativeQueryStrStrMap(String sql, Serializable bean) {
        return nativeQueryMap(String.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }

    default Map<Integer, String> nativeQueryIntStrMap(String sql, Serializable bean) {
        return nativeQueryMap(Integer.class, String.class, sql, (Map<String, Object>) Copier.copyToMap(bean, false));
    }
}
