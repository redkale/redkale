/*
 *
 */
package org.redkale.source;

import java.util.function.Function;

/**
 *
 * 关系型数据库的数据源， 接口与DataSource基本一致。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataSqlSource extends DataSource {

    public int directExecute(String sql);

    public int[] directExecute(String... sqls);

    public <V> V directQuery(String sql, Function<DataResultSet, V> handler);
}
