/*
 *
 */
package org.redkale.source;

import java.util.*;
import java.util.function.*;

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

    public int executeUpdate(String sql);

    public int[] executeUpdate(String... sqls);

    //BiConsumer 参数1: connection, 参数2: statement
    public <V> V executeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler);

    default <V> V executeQuery(String sql, Function<DataResultSet, V> handler) {
        return executeQuery(sql, null, handler);
    }

    default <V> V executeQueryOne(Class<V> type, String sql) {
        return executeQuery(sql, rset -> {
            if (!rset.next()) {
                return null;
            }
            if (type.isPrimitive() || type == byte[].class || type.getName().startsWith("java.")) {
                return (V) DataResultSet.formatColumnValue(type, rset.getObject(1));
            }
            return EntityBuilder.load(type).getObjectValue(rset);
        });
    }

    default <V> List<V> executeQueryList(Class<V> type, String sql) {
        return executeQuery(sql, rset -> {
            if (type.isPrimitive() || type == byte[].class || type.getName().startsWith("java.")) {
                List<V> list = new ArrayList<>();
                while (rset.next()) {
                    list.add(rset.wasNull() ? null : (V) DataResultSet.formatColumnValue(type, rset.getObject(1)));
                }
                return list;
            }
            return EntityBuilder.load(type).getObjectList(rset);
        });
    }
}
