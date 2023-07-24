/*
 *
 */
package org.redkale.source;

import java.util.function.Function;

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

    public <V> V executeQuery(String sql, Function<DataResultSet, V> handler);

}
