/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.Collection;
import org.redkale.util.LambdaFunction;
import org.redkale.util.LambdaSupplier;
import org.redkale.util.SelectColumn;

/**
 * DataSource批量操作对象，操作类型只能是增删改 <br>
 * 非线程安全类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataBatch {

    public static DataBatch create() {
        return new AbstractDataSource.DefaultDataBatch();
    }

    public DataBatch run(Runnable task);

    public <T> DataBatch insert(T... entitys);

    public <T> DataBatch insert(Collection<T> entitys);

    public <T> DataBatch delete(T... entitys);

    public <T> DataBatch delete(Collection<T> entitys);

    public <T> DataBatch delete(Class<T> clazz, Serializable... pks);

    public <T> DataBatch delete(Class<T> clazz, FilterNode node);

    public <T> DataBatch delete(Class<T> clazz, FilterNode node, Flipper flipper);

    public <T> DataBatch update(T... entitys);

    public <T> DataBatch update(Collection<T> entitys);

    public <T> DataBatch updateColumn(Class<T> clazz, Serializable pk, String column, Serializable value);

    public <T> DataBatch updateColumn(Class<T> clazz, Serializable pk, ColumnValue... values);

    public <T> DataBatch updateColumn(Class<T> clazz, FilterNode node, String column, Serializable value);

    public <T> DataBatch updateColumn(Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values);

    public <T> DataBatch updateColumn(T entity, final String... columns);

    public <T> DataBatch updateColumn(T entity, final FilterNode node, final String... columns);

    public <T> DataBatch updateColumn(T entity, SelectColumn selects);

    public <T> DataBatch updateColumn(T entity, final FilterNode node, SelectColumn selects);

    default <T, V extends Serializable> DataBatch updateColumn(
            final Class<T> clazz, final Serializable pk, final LambdaSupplier<V> func) {
        return updateColumn(clazz, pk, LambdaSupplier.readColumn(func), func.get());
    }

    default <T> DataBatch updateColumn(
            final Class<T> clazz, final Serializable pk, LambdaFunction<T, ?> func, Serializable value) {
        return updateColumn(clazz, pk, ColumnValue.set(func, value));
    }

    default <T> DataBatch updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return updateColumn(clazz, node, (Flipper) null, values);
    }

    default <T> DataBatch updateColumn(final T entity, final LambdaFunction<T, ?>... funcs) {
        return updateColumn(entity, (FilterNode) null, LambdaFunction.readColumns(funcs));
    }

    default <T> DataBatch updateColumn(final T entity, final FilterNode node, final LambdaFunction<T, ?>... funcs) {
        return updateColumn(entity, node, LambdaFunction.readColumns(funcs));
    }
}
