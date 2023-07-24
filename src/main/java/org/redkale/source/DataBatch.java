/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import org.redkale.util.SelectColumn;

/**
 * DataSource批量操作对象，操作类型只能是增删改   <br>
 * 非线程安全类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface DataBatch {

    public static DataBatch create() {
        return new AbstractDataSource.DefaultDataBatch();
    }

    public DataBatch run(Runnable task);

    public <T> DataBatch insert(T... entitys);

    public <T> DataBatch delete(T... entitys);

    public <T> DataBatch delete(Class<T> clazz, Serializable... pks);

    public <T> DataBatch delete(Class<T> clazz, FilterNode node);

    public <T> DataBatch delete(Class<T> clazz, FilterNode node, Flipper flipper);

    public <T> DataBatch update(T... entitys);

    public <T> DataBatch update(Class<T> clazz, Serializable pk, String column, Serializable value);

    public <T> DataBatch update(Class<T> clazz, Serializable pk, ColumnValue... values);

    public <T> DataBatch update(Class<T> clazz, FilterNode node, String column, Serializable value);

    public <T> DataBatch update(Class<T> clazz, FilterNode node, ColumnValue... values);

    public <T> DataBatch update(Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values);

    public <T> DataBatch updateColumn(T entity, final String... columns);

    public <T> DataBatch updateColumn(T entity, final FilterNode node, final String... columns);

    public <T> DataBatch updateColumn(T entity, SelectColumn selects);

    public <T> DataBatch updateColumn(T entity, final FilterNode node, SelectColumn selects);

}
