/*
 *
 */
package org.redkale.source;

import java.util.*;
import java.util.function.*;
import static org.redkale.source.DataResultSet.formatColumnValue;

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

    public int nativeUpdate(String sql);

    public int[] nativeUpdate(String... sqls);

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
            if (type.isPrimitive() || type == byte[].class || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
                return (V) formatColumnValue(type, rset.getObject(1));
            }
            return EntityBuilder.load(type).getObjectValue(rset);
        });
    }

    default <V> List<V> nativeQueryList(Class<V> type, String sql) {
        return nativeQuery(sql, rset -> {
            if (type.isPrimitive() || type == byte[].class || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
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
}
