/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.redkale.annotation.Component;
import org.redkale.inject.Resourcable;
import org.redkale.util.*;

/**
 * DataSource 为数据库或内存数据库的数据源，提供类似JPA、Hibernate的接口与功能。 <br>
 * 返回类型为CompletableFuture的接口为异步接口 <br>
 * <br>
 * 字段类型支持： <br>
 * 1、boolean/Boolean <br>
 * 2、byte/Byte <br>
 * 3、short/Short <br>
 * 4、char/Character <br>
 * 5、int/Integer/AtomicInteger <br>
 * 6、long/Long/AtomicLong/LongAdder <br>
 * 7、float/Float <br>
 * 8、double/Double <br>
 * 9、java.math.BigInteger <br>
 * 10、java.math.BigDecimal <br>
 * 11、String <br>
 * 12、byte[] <br>
 * 13、java.time.LocalDate/java.sql.Date/java.util.Date <br>
 * 14、java.time.LocalTime/java.sql.Time <br>
 * 15、java.time.LocalDateTime/java.sql.Timestamp <br>
 * 16、JavaBean/其他可JSON化类型 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Component
@SuppressWarnings("unchecked")
public interface DataSource extends Resourcable {

    /**
     * 获取数据源类型
     *
     * @return String
     */
    public String getType();

    /**
     * 提取预编译Entity类，主要用于native-image使用
     *
     * @param <T> 泛型
     * @param clazz Entity实体类
     */
    public <T> void compile(Class<T> clazz);

    // ----------------------batchAsync-----------------------------
    /**
     * 增删改的批量操作
     *
     * @param batch 批量对象
     * @return -1表示失败，正数为成功
     */
    public int batch(final DataBatch batch);

    /**
     * 增删改的批量操作
     *
     * @param batch 批量对象
     * @return -1表示失败，正数为成功
     */
    public CompletableFuture<Integer> batchAsync(final DataBatch batch);

    // ----------------------insertAsync-----------------------------
    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    public <T> int insert(final T... entitys);

    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int insert(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return 0;
        }
        return insert(entitys.toArray());
    }

    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int insert(final Stream<T> entitys) {
        if (entitys == null) {
            return 0;
        }
        return insert(entitys.toArray());
    }

    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return CompletableFuture
     */
    public <T> CompletableFuture<Integer> insertAsync(final T... entitys);

    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return CompletableFuture
     */
    default <T> CompletableFuture<Integer> insertAsync(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return insertAsync(entitys.toArray());
    }

    /**
     * 新增记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return CompletableFuture
     */
    default <T> CompletableFuture<Integer> insertAsync(final Stream<T> entitys) {
        if (entitys == null) {
            return CompletableFuture.completedFuture(0);
        }
        return insertAsync(entitys.toArray());
    }

    // -------------------------deleteAsync--------------------------
    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    public <T> int delete(final T... entitys);

    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int delete(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return 0;
        }
        return delete(entitys.toArray());
    }

    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int delete(final Stream<T> entitys) {
        if (entitys == null) {
            return 0;
        }
        return delete(entitys.toArray());
    }

    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> deleteAsync(final T... entitys);

    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> deleteAsync(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return deleteAsync(entitys.toArray());
    }

    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {getValues.id} <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> deleteAsync(final Stream<T> entitys) {
        if (entitys == null) {
            return CompletableFuture.completedFuture(0);
        }
        return deleteAsync(entitys.toArray());
    }

    /**
     * 删除指定主键值的记录,多主键值必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {ids} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值
     * @return 影响的记录条数
     */
    public <T> int delete(final Class<T> clazz, final Serializable... pks);

    /**
     * 删除指定主键值的记录,多主键值必须在同一张表中 <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {ids} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Serializable... pks);

    /**
     * 删除符合过滤条件的记录 <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 影响的记录条数
     */
    default <T> int delete(final Class<T> clazz, final FilterNode node) {
        return delete(clazz, (Flipper) null, node);
    }

    /**
     * 删除符合过滤条件的记录 <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final FilterNode node) {
        return deleteAsync(clazz, (Flipper) null, node);
    }

    /**
     * 删除符合过滤条件且指定最大影响条数的记录 <br>
     * Flipper中offset字段将被忽略 <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 影响的记录条数
     */
    public <T> int delete(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 删除符合过滤条件且指定最大影响条数的记录 <br>
     * Flipper中offset字段将被忽略 <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> deleteAsync(
            final Class<T> clazz, final Flipper flipper, final FilterNode node);

    // ------------------------clearAsync---------------------------
    /**
     * 清空表 <br>
     * 等价SQL: TRUNCATE TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return 影响的记录条数 -1表示表不存在
     */
    default <T> int clearTable(final Class<T> clazz) {
        return clearTable(clazz, (FilterNode) null);
    }

    /**
     * 清空表 <br>
     * 等价SQL: TRUNCATE TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return 影响的记录条数CompletableFuture -1表示表不存在
     */
    default <T> CompletableFuture<Integer> clearTableAsync(final Class<T> clazz) {
        return clearTableAsync(clazz, (FilterNode) null);
    }

    /**
     * 清空表 <br>
     * 等价SQL: TRUNCATE TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 分库分表的过滤条件
     * @return 影响的记录条数 -1表示表不存在
     */
    public <T> int clearTable(final Class<T> clazz, final FilterNode node);

    /**
     * 清空表 <br>
     * 等价SQL: TRUNCATE TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 分库分表的过滤条件
     * @return 影响的记录条数CompletableFuture -1表示表不存在
     */
    public <T> CompletableFuture<Integer> clearTableAsync(final Class<T> clazz, final FilterNode node);

    // ------------------------createTable---------------------------
    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @return 建表结果
     */
    public <T> int createTable(final Class<T> clazz, final Serializable pk);

    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @return 建表结果
     */
    public <T> CompletableFuture<Integer> createTableAsync(final Class<T> clazz, final Serializable pk);

    // ------------------------dropAsync---------------------------
    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return 影响的记录条数 -1表示表不存在
     */
    default <T> int dropTable(final Class<T> clazz) {
        return dropTable(clazz, (FilterNode) null);
    }

    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return 影响的记录条数CompletableFuture -1表示表不存在
     */
    default <T> CompletableFuture<Integer> dropTableAsync(final Class<T> clazz) {
        return dropTableAsync(clazz, (FilterNode) null);
    }

    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 影响的记录条数 -1表示表不存在
     */
    public <T> int dropTable(final Class<T> clazz, final FilterNode node);

    /**
     * 删除表 <br>
     * 等价SQL: DROP TABLE {table}<br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 分库分表的过滤条件
     * @return 影响的记录条数CompletableFuture -1表示表不存在
     */
    public <T> CompletableFuture<Integer> dropTableAsync(final Class<T> clazz, final FilterNode node);

    // ------------------------updateAsync---------------------------
    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    public <T> int update(final T... entitys);

    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int update(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return 0;
        }
        return update(entitys.toArray());
    }

    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    default <T> int update(final Stream<T> entitys) {
        if (entitys == null) {
            return 0;
        }
        return update(entitys.toArray());
    }

    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateAsync(final T... entitys);

    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateAsync(final Collection<T> entitys) {
        if (entitys == null || entitys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return updateAsync(entitys.toArray());
    }

    /**
     * 更新记录， 多对象必须是同一个Entity类且必须在同一张表中 <br>
     * 等价SQL: <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
     * &#183;&#183;&#183; <br>
     *
     * @param <T> 泛型
     * @param entitys Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateAsync(final Stream<T> entitys) {
        if (entitys == null) {
            return CompletableFuture.completedFuture(0);
        }
        return updateAsync(entitys.toArray());
    }

    /**
     * 更新单个记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param column 待更新的字段名
     * @param value 更新值
     * @return 影响的记录条数
     */
    public <T> int updateColumn(
            final Class<T> clazz, final Serializable pk, final String column, final Serializable value);

    /**
     * 更新单个记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 更新值泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param func 更新值Lambda
     * @return 影响的记录条数
     */
    default <T, V extends Serializable> int updateColumn(
            final Class<T> clazz, final Serializable pk, final LambdaSupplier<V> func) {
        return updateColumn(clazz, pk, LambdaSupplier.readColumn(func), func.get());
    }

    /**
     * 更新单个记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param column 待更新的字段名
     * @param value 更新值
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final Serializable pk, final String column, final Serializable value);

    /**
     * 更新单个记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 更新值泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param func 更新值Lambda
     * @return 影响的记录条数
     */
    default <T, V extends Serializable> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final Serializable pk, final LambdaSupplier<V> func) {
        return updateColumnAsync(clazz, pk, LambdaSupplier.readColumn(func), func.get());
    }

    /**
     * 更新符合过滤条件记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 待更新的字段名
     * @param value 更新值
     * @param node 过滤条件
     * @return 影响的记录条数
     */
    public <T> int updateColumn(
            final Class<T> clazz, final String column, final Serializable value, final FilterNode node);

    /**
     * 更新符合过滤条件记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 更新值泛型
     * @param clazz Entity类
     * @param func 更新值Lambda
     * @param node 过滤条件
     * @return 影响的记录条数
     */
    default <T, V extends Serializable> int updateColumn(
            final Class<T> clazz, final LambdaSupplier<V> func, final FilterNode node) {
        return updateColumn(clazz, LambdaSupplier.readColumn(func), func.get(), node);
    }

    /**
     * 更新符合过滤条件记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 待更新的字段名
     * @param value 更新值
     * @param node 过滤条件
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final String column, final Serializable value, final FilterNode node);

    /**
     * 更新符合过滤条件记录的单个字段 <br>
     * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 更新值泛型
     * @param clazz Entity类
     * @param func 更新值Lambda
     * @param node 过滤条件
     * @return 影响的记录条数
     */
    default <T, V extends Serializable> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final LambdaSupplier<V> func, final FilterNode node) {
        return updateColumnAsync(clazz, LambdaSupplier.readColumn(func), func.get(), node);
    }

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param values 更新字段
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final Serializable pk, final ColumnValue... values);

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param values 更新字段
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final Class<T> clazz, final Serializable pk, final ColumnValues values) {
        return updateColumn(clazz, pk, values.getValues());
    }

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param func 更新字段
     * @param value 更新字段值
     * @return 影响的记录条数
     */
    default <T> int updateColumn(
            final Class<T> clazz, final Serializable pk, LambdaFunction<T, ?> func, Serializable value) {
        return updateColumn(clazz, pk, ColumnValue.set(func, value));
    }

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final Serializable pk, final ColumnValue... values);

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final Serializable pk, final ColumnValues values) {
        return updateColumnAsync(clazz, pk, values.getValues());
    }

    /**
     * 更新指定主键值记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键
     * @param func 更新字段
     * @param value 更新字段值
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final Serializable pk, LambdaFunction<T, ?> func, Serializable value) {
        return updateColumnAsync(clazz, pk, ColumnValue.set(func, value));
    }

    /**
     * 更新符合过滤条件记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param values 更新字段
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return updateColumn(clazz, node, (Flipper) null, values);
    }

    /**
     * 更新符合过滤条件记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param values 更新字段
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValues values) {
        return updateColumn(clazz, node, (Flipper) null, values.getValues());
    }

    /**
     * 更新符合过滤条件记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return updateColumnAsync(clazz, node, (Flipper) null, values);
    }

    /**
     * 更新符合过滤条件记录的部分字段 <br>
     * 字段赋值操作选项见 ColumnExpress <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final FilterNode node, final ColumnValues values) {
        return updateColumnAsync(clazz, node, (Flipper) null, values.getValues());
    }

    /**
     * 更新符合过滤条件的记录的指定字段 <br>
     * Flipper中offset字段将被忽略 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} ORDER BY {flipper.sort} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param flipper 翻页对象
     * @param values 更新字段
     * @return 影响的记录条数
     */
    public <T> int updateColumn(
            final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values);

    /**
     * 更新符合过滤条件的记录的指定字段 <br>
     * Flipper中offset字段将被忽略 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} ORDER BY {flipper.sort} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param flipper 翻页对象
     * @param values 更新字段
     * @return 影响的记录条数
     */
    default <T> int updateColumn(
            final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValues values) {
        return updateColumn(clazz, node, flipper, values.getValues());
    }

    /**
     * 更新符合过滤条件的记录的指定字段 <br>
     * Flipper中offset字段将被忽略 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} ORDER BY {flipper.sort} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param flipper 翻页对象
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values);

    /**
     * 更新符合过滤条件的记录的指定字段 <br>
     * Flipper中offset字段将被忽略 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
     * WHERE {filter node} ORDER BY {flipper.sort} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @param flipper 翻页对象
     * @param values 更新字段
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValues values) {
        return updateColumnAsync(clazz, node, flipper, values.getValues());
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param columns 需更新的字段名
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final T entity, final String... columns) {
        return updateColumn(entity, (FilterNode) null, columns);
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param funcs 需更新的字段名Lambda集合
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final T entity, final LambdaFunction<T, ?>... funcs) {
        return updateColumn(entity, (FilterNode) null, LambdaFunction.readColumns(funcs));
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param columns 需更新的字段名
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final String... columns) {
        return updateColumnAsync(entity, (FilterNode) null, columns);
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param funcs 需更新的字段名Lambda集合
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final LambdaFunction<T, ?>... funcs) {
        return updateColumnAsync(entity, (FilterNode) null, LambdaFunction.readColumns(funcs));
    }

    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param columns 需更新的字段名
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final T entity, final FilterNode node, final String... columns);

    /**
     * 更新实体对象中非null字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @return 影响的记录条数
     */
    public <T> int updateColumnNonnull(final T entity);

    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param funcs 需更新的字段名Lambda集合
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final T entity, final FilterNode node, final LambdaFunction<T, ?>... funcs) {
        return updateColumn(entity, node, LambdaFunction.readColumns(funcs));
    }

    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param columns 需更新的字段名
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final T entity, final FilterNode node, final String... columns);

    /**
     * 更新实体对象中非null字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnNonnullAsync(final T entity);
    
    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param funcs 需更新的字段名Lambda集合
     * @return 影响的记录条数
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(
            final T entity, final FilterNode node, final LambdaFunction<T, ?>... funcs) {
        return updateColumnAsync(entity, node, LambdaFunction.readColumns(funcs));
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param selects 指定字段
     * @return 影响的记录条数
     */
    default <T> int updateColumn(final T entity, final SelectColumn selects) {
        return updateColumn(entity, (FilterNode) null, selects);
    }

    /**
     * 更新单个记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {primary} = {bean.id} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param selects 指定字段
     * @return 影响的记录条数CompletableFuture
     */
    default <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final SelectColumn selects) {
        return updateColumnAsync(entity, (FilterNode) null, selects);
    }

    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param selects 指定字段
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final T entity, final FilterNode node, final SelectColumn selects);

    /**
     * 更新符合过滤条件记录的指定字段 <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
     * WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param entity 待更新的Entity对象
     * @param node 过滤条件
     * @param selects 指定字段
     * @return 影响的记录条数CompletableFuture
     */
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final T entity, final FilterNode node, final SelectColumn selects);

    // ############################################# 查询接口 #############################################
    // -----------------------getXXXXResult-----------------------------
    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null) 等价于: SELECT COUNT(*) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @return 聚合结果
     */
    default Number getNumberResult(final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResult(entityClass, func, null, column, (FilterNode) null);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null) 等价于: SELECT COUNT(*) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @return 聚合结果CompletableFuture
     */
    default CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass, final FilterFunc func, final String column) {
        return getNumberResultAsync(entityClass, func, null, column, (FilterNode) null);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null, (FilterBean)null) 等价于: SELECT COUNT(*) FROM {table}
     * <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @param bean 过滤条件
     * @return 聚合结果
     */
    default Number getNumberResult(
            final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null, (FilterBean)null) 等价于: SELECT COUNT(*) FROM {table}
     * <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @param bean 过滤条件
     * @return 聚合结果CompletableFuture
     */
    default CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        return getNumberResultAsync(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @param node 过滤条件
     * @return 聚合结果
     */
    default Number getNumberResult(
            final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResult(entityClass, func, null, column, node);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param column 指定字段
     * @param node 过滤条件
     * @return 聚合结果
     */
    default CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass, final FilterFunc func, final String column, final FilterNode node) {
        return getNumberResultAsync(entityClass, func, null, column, node);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime") 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @return 聚合结果
     */
    default Number getNumberResult(
            final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResult(entityClass, func, defVal, column, (FilterNode) null);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime") 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @return 聚合结果CompletableFuture
     */
    default CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass, final FilterFunc func, final Number defVal, final String column) {
        return getNumberResultAsync(entityClass, func, defVal, column, (FilterNode) null);
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @param bean 过滤条件
     * @return 聚合结果
     */
    default Number getNumberResult(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @param bean 过滤条件
     * @return 聚合结果CompletableFuture
     */
    default CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterBean bean) {
        return getNumberResultAsync(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @param node 过滤条件
     * @return 聚合结果
     */
    public Number getNumberResult(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
     * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func 聚合函数
     * @param defVal 默认值
     * @param column 指定字段
     * @param node 过滤条件
     * @return 聚合结果CompletableFuture
     */
    public CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node);

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} <br>
     * 如 getNumberMapAsync(User.class, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param columns 聚合字段
     * @return 聚合结果Map
     */
    default <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, (FilterNode) null, columns);
    }

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} <br>
     * 如 getNumberMapAsync(User.class, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime)
     * FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param columns 聚合字段
     * @return 聚合结果Map CompletableFuture
     */
    default <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
            final Class entityClass, final FilterFuncColumn... columns) {
        return getNumberMapAsync(entityClass, (FilterNode) null, columns);
    }

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     * 如 getNumberMapAsync(User.class, (FilterBean)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
     * MAX(createtime) FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param bean 过滤条件
     * @param columns 聚合字段
     * @return 聚合结果Map
     */
    default <N extends Number> Map<String, N> getNumberMap(
            final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return getNumberMap(entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     * 如 getNumberMapAsync(User.class, (FilterBean)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
     * MAX(createtime) FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param bean 过滤条件
     * @param columns 聚合字段
     * @return 聚合结果Map CompletableFuture
     */
    default <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
            final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return getNumberMapAsync(entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     * 如 getNumberMapAsync(User.class, (FilterNode)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
     * MAX(createtime) FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param node 过滤条件
     * @param columns 聚合字段
     * @return 聚合结果Map
     */
    public <N extends Number> Map<String, N> getNumberMap(
            final Class entityClass, final FilterNode node, final FilterFuncColumn... columns);

    /**
     * 获取符合过滤条件记录的聚合结果Map <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     * 如 getNumberMapAsync(User.class, (FilterNode)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
     * MAX(createtime) FROM {table} <br>
     *
     * @param <N> Number
     * @param entityClass Entity类
     * @param node 过滤条件
     * @param columns 聚合字段
     * @return 聚合结果Map
     */
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
            final Class entityClass, final FilterNode node, final FilterFuncColumn... columns);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime") 等价于: SELECT name, MAX(createtime) FROM
     * user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @return 聚合结果Map
     */
    default <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
            final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime") 等价于: SELECT name, MAX(createtime) FROM
     * user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
            final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn) {
        return queryColumnMapAsync(entityClass, keyColumn, func, funcColumn, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter bean} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterBean)null) 等价于: SELECT name,
     * MAX(createtime) FROM user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @param bean 过滤条件
     * @return 聚合结果Map
     */
    default <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            final FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter bean} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterBean)null) 等价于: SELECT name,
     * MAX(createtime) FROM user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @param bean 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            final FilterBean bean) {
        return queryColumnMapAsync(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter node} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT name,
     * MAX(createtime) FROM user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @param node 过滤条件
     * @return 聚合结果Map
     */
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            final FilterNode node);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter node} GROUP BY {keyColumn} <br>
     * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT name,
     * MAX(createtime) FROM user GROUP BY name<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param keyColumn Key字段
     * @param func 聚合函数
     * @param funcColumn 聚合字段
     * @param node 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            final FilterNode node);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid") 等价于: SELECT targetid, SUM(money) / 100,
     * AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
            final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn) {
        return queryColumnMap(entityClass, funcNodes, groupByColumn, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid") 等价于: SELECT targetid, SUM(money) / 100,
     * AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
            final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumn, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterBean)null) 等价于: SELECT targetid,
     * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @param bean 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterBean bean) {
        return queryColumnMap(entityClass, funcNodes, groupByColumn, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterBean)null) 等价于: SELECT targetid,
     * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @param bean 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterBean bean) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumn, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterNode)null) 等价于: SELECT targetid,
     * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @param node 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterNode node);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterNode)null) 等价于: SELECT targetid,
     * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumn GROUP BY字段
     * @param node 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterNode node);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1}, {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid")) 等价于: SELECT fromid,
     * targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
            final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return queryColumnMap(entityClass, funcNodes, groupByColumns, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1}, {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid")) 等价于: SELECT fromid,
     * targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
            final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumns, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1},
     * {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterBean)null)
     * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @param bean 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterBean bean) {
        return queryColumnMap(entityClass, funcNodes, groupByColumns, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1},
     * {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterBean)null)
     * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @param bean 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    default <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterBean bean) {
        return queryColumnMapAsync(entityClass, funcNodes, groupByColumns, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1},
     * {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterNode)null)
     * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @param node 过滤条件
     * @return 聚合结果Map
     */
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
     * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1},
     * {col2} <br>
     * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
     * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterNode)null)
     * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
     *
     * @param <T> Entity泛型
     * @param <K> Key字段的数据类型
     * @param <N> Number
     * @param entityClass Entity类
     * @param funcNodes ColumnNode[]
     * @param groupByColumns GROUP BY字段
     * @param node 过滤条件
     * @return 聚合结果Map CompletableFuture
     */
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node);

    // -----------------------findAsync----------------------------
    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @return Entity对象
     */
    default <T> T find(final Class<T> clazz, final Serializable pk) {
        return find(clazz, (SelectColumn) null, pk);
    }

    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @return Entity对象 CompletableFuture
     */
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final Serializable pk);

    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pk 主键值
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pk 主键值
     * @return Entity对象CompletableFuture
     */
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象
     */
    default <T> T[] finds(final Class<T> clazz, final Serializable... pks) {
        return finds(clazz, (SelectColumn) null, pks);
    }

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
     *
     * @param <D> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象
     */
    default <D extends Serializable, T> T[] finds(final Class<T> clazz, final Stream<D> pks) {
        return finds(clazz, (SelectColumn) null, pks.toArray(Utility.serialArrayFunc()));
    }

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象 CompletableFuture
     */
    default <T> CompletableFuture<T[]> findsAsync(final Class<T> clazz, final Serializable... pks) {
        return findsAsync(clazz, (SelectColumn) null, pks);
    }

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
     *
     * @param <D> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象 CompletableFuture
     */
    default <D extends Serializable, T> CompletableFuture<T[]> findsAsync(final Class<T> clazz, final Stream<D> pks) {
        return findsAsync(clazz, (SelectColumn) null, pks.toArray(Utility.serialArrayFunc()));
    }

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pks 主键值集合
     * @return Entity对象
     */
    public <T> T[] finds(final Class<T> clazz, final SelectColumn selects, final Serializable... pks);

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <D>主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pks 主键值集合
     * @return Entity对象
     */
    default <D extends Serializable, T> T[] finds(
            final Class<T> clazz, final SelectColumn selects, final Stream<D> pks) {
        return finds(clazz, selects, pks.toArray(Utility.serialArrayFunc()));
    }

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pks 主键值集合
     * @return Entity对象CompletableFuture
     */
    public <T> CompletableFuture<T[]> findsAsync(
            final Class<T> clazz, final SelectColumn selects, final Serializable... pks);

    /**
     * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <D>主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pks 主键值集合
     * @return Entity对象
     */
    default <D extends Serializable, T> CompletableFuture<T[]> findsAsync(
            final Class<T> clazz, final SelectColumn selects, final Stream<D> pks) {
        return findsAsync(clazz, selects, pks.toArray(Utility.serialArrayFunc()));
    }

    /**
     * 获取指定主键值的多个记录, 返回列表 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <D>主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象
     */
    public <D extends Serializable, T> List<T> findsList(final Class<T> clazz, final Stream<D> pks);

    /**
     * 获取指定主键值的多个记录, 返回列表 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
     * &#183;&#183;&#183;} <br>
     *
     * @param <D>主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pks 主键值集合
     * @return Entity对象
     */
    public <D extends Serializable, T> CompletableFuture<List<T>> findsListAsync(
            final Class<T> clazz, final Stream<D> pks);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity对象CompletableFuture
     */
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param func 更新值Lambda
     * @return Entity对象
     */
    default <T> T find(final Class<T> clazz, final LambdaSupplier<Serializable> func) {
        return find(clazz, LambdaSupplier.readColumn(func), func.get());
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param func 更新值Lambda
     * @return Entity对象
     */
    default <T> CompletableFuture<T> findAsync(final Class<T> clazz, final LambdaSupplier<Serializable> func) {
        return findAsync(clazz, LambdaSupplier.readColumn(func), func.get());
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity对象
     */
    default <T> T find(final Class<T> clazz, final FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity对象CompletableFuture
     */
    default <T> CompletableFuture<T> findAsync(final Class<T> clazz, final FilterBean bean) {
        return findAsync(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity对象
     */
    default <T> T find(final Class<T> clazz, final FilterNode node) {
        return find(clazz, (SelectColumn) null, node);
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity对象CompletableFuture
     */
    default <T> CompletableFuture<T> findAsync(final Class<T> clazz, final FilterNode node) {
        return findAsync(clazz, (SelectColumn) null, node);
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity对象
     */
    default <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity对象 CompletableFuture
     */
    default <T> CompletableFuture<T> findAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return findAsync(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity对象 CompletableFuture
     */
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param pk 主键值
     * @return Entity对象
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param pk 主键值
     * @return Entity对象 CompletableFuture
     */
    public <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param bean 过滤条件
     * @return 字段值
     */
    default <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean) {
        return findColumn(clazz, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param bean 过滤条件
     * @return 字段值 CompletableFuture
     */
    default <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final FilterBean bean) {
        return findColumnAsync(clazz, column, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param node 过滤条件
     * @return 字段值
     */
    default <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node) {
        return findColumn(clazz, column, null, node);
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param node 过滤条件
     * @return 字段值 CompletableFuture
     */
    default <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final FilterNode node) {
        return findColumnAsync(clazz, column, null, node);
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param pk 主键值
     * @return 字段值
     */
    public <T> Serializable findColumn(
            final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param pk 主键值
     * @return 字段值 CompletableFuture
     */
    public <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param bean 过滤条件
     * @return 字段值
     */
    default <T> Serializable findColumn(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return findColumn(clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param bean 过滤条件
     * @return 字段值 CompletableFuture
     */
    default <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return findColumnAsync(clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param node 过滤条件
     * @return 字段值
     */
    public <T> Serializable findColumn(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 字段名
     * @param defValue 默认值
     * @param node 过滤条件
     * @return 字段值 CompletableFuture
     */
    public <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node);

    /**
     * 判断是否存在主键值的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @return 是否存在
     */
    public <T> boolean exists(final Class<T> clazz, final Serializable pk);

    /**
     * 判断是否存在主键值的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {primary} = {id} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @return 是否存在CompletableFuture
     */
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final Serializable pk);

    /**
     * 判断是否存在符合过滤条件的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 是否存在
     */
    default <T> boolean exists(final Class<T> clazz, final FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 判断是否存在符合过滤条件的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 是否存在CompletableFuture
     */
    default <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterBean bean) {
        return existsAsync(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 判断是否存在符合过滤条件的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 是否存在
     */
    public <T> boolean exists(final Class<T> clazz, final FilterNode node);

    /**
     * 判断是否存在符合过滤条件的记录 <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 是否存在CompletableFuture
     */
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterNode node);

    // -----------------------list set----------------------------
    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return 字段值的集合
     */
    public <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return 字段值的集合CompletableFuture
     */
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnSetAsync(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnSet(selectedColumn, clazz, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnSetAsync(selectedColumn, clazz, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT
     * {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT
     * {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSetAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT
     * {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合
     */
    public <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段Set集合 <br>
     * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT
     * {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return 字段值的集合CompletableFuture
     */
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        return queryColumnListAsync(selectedColumn, clazz, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnList(selectedColumn, clazz, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param node 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final FilterNode node) {
        return queryColumnListAsync(selectedColumn, clazz, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnListAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段List集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable> Sheet<V> queryColumnSheet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param <F> 过滤类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param pageBean 过滤翻页条件
     * @return 字段值的集合
     */
    default <T, V extends Serializable, F extends FilterBean> Sheet<V> queryColumnSheet(
            final String selectedColumn, final Class<T> clazz, final PageBean<F> pageBean) {
        return queryColumnSheet(
                selectedColumn,
                clazz,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryColumnSheetAsync(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param <F> 过滤类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param pageBean 过滤翻页条件
     * @return 字段值的集合CompletableFuture
     */
    default <T, V extends Serializable, F extends FilterBean> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
            final String selectedColumn, final Class<T> clazz, final PageBean<F> pageBean) {
        return queryColumnSheetAsync(
                selectedColumn,
                clazz,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合 <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param <V> 字段类型
     * @param selectedColumn 指定字段
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return 字段值的集合CompletableFuture
     */
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param keyStream 主键Stream
     * @return Entity的集合
     */
    default <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final Stream<K> keyStream) {
        return queryMap(clazz, (SelectColumn) null, keyStream);
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param keyStream 主键Stream
     * @return Entity的集合CompletableFuture
     */
    default <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final Stream<K> keyStream) {
        return queryMapAsync(clazz, (SelectColumn) null, keyStream);
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean FilterBean
     * @return Entity的集合
     */
    default <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final FilterBean bean) {
        return queryMap(clazz, (SelectColumn) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean FilterBean
     * @return Entity的集合CompletableFuture
     */
    default <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final FilterBean bean) {
        return queryMapAsync(clazz, (SelectColumn) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node FilterNode
     * @return Entity的集合
     */
    default <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final FilterNode node) {
        return queryMap(clazz, (SelectColumn) null, node);
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node FilterNode
     * @return Entity的集合CompletableFuture
     */
    default <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final FilterNode node) {
        return queryMapAsync(clazz, (SelectColumn) null, node);
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param keyStream 主键Stream
     * @return Entity的集合
     */
    public <K extends Serializable, T> Map<K, T> queryMap(
            final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream);

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param keyStream 主键Stream
     * @return Entity的集合CompletableFuture
     */
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream);

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean FilterBean
     * @return Entity的集合
     */
    default <K extends Serializable, T> Map<K, T> queryMap(
            final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryMap(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean FilterBean
     * @return Entity的集合CompletableFuture
     */
    default <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final SelectColumn selects, FilterBean bean) {
        return queryMapAsync(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node FilterNode
     * @return Entity的集合
     */
    public <K extends Serializable, T> Map<K, T> queryMap(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node FilterNode
     * @return Entity的集合CompletableFuture
     */
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final String column, final Serializable colval) {
        return querySet(clazz, (Flipper) null, column, colval);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final String column, final Serializable colval) {
        return querySetAsync(clazz, (Flipper) null, column, colval);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final FilterBean bean) {
        return querySet(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final FilterBean bean) {
        return querySetAsync(clazz, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz) {
        return querySet(clazz, (SelectColumn) null, (Flipper) null, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final FilterNode node) {
        return querySet(clazz, (SelectColumn) null, (Flipper) null, node);
    }

    /**
     * 查询记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz) {
        return querySetAsync(clazz, (SelectColumn) null, (Flipper) null, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final FilterNode node) {
        return querySetAsync(clazz, (SelectColumn) null, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return querySet(clazz, selects, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return querySetAsync(clazz, selects, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return querySet(clazz, selects, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return querySetAsync(clazz, selects, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合
     */
    public <T> Set<T> querySet(
            final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合CompletableFuture
     */
    public <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySet(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySetAsync(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySet(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySetAsync(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY
     * {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Set<T> querySet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY
     * {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySetAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY
     * {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    public <T> Set<T> querySet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的Set集合 <br>
     * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY
     * {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    public <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable colval) {
        return queryList(clazz, (Flipper) null, column, colval);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final String column, final Serializable colval) {
        return queryListAsync(clazz, (Flipper) null, column, colval);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, (SelectColumn) null, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final FilterBean bean) {
        return queryListAsync(clazz, (SelectColumn) null, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz) {
        return queryList(clazz, (SelectColumn) null, (Flipper) null, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, (Flipper) null, node);
    }

    /**
     * 查询记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz) {
        return queryListAsync(clazz, (SelectColumn) null, (Flipper) null, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final FilterNode node) {
        return queryListAsync(clazz, (SelectColumn) null, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, selects, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryListAsync(clazz, selects, (Flipper) null, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryList(clazz, selects, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryListAsync(clazz, selects, (Flipper) null, node);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合
     */
    public <T> List<T> queryList(
            final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return Entity的集合CompletableFuture
     */
    public <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final Flipper flipper, final String column, final Serializable colval);

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final Flipper flipper) {
        return queryList(clazz, (SelectColumn) null, flipper, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final Flipper flipper) {
        return queryListAsync(clazz, (SelectColumn) null, flipper, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryListAsync(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryList(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryListAsync(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @return Entity的集合
     */
    default <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper) {
        return queryList(clazz, selects, flipper, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit}
     * <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper) {
        return queryListAsync(clazz, selects, flipper, (FilterNode) null);
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> List<T> queryList(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryListAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    public <T> List<T> queryList(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    public <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    // -----------------------sheet----------------------------
    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <F> 过滤类型
     * @param clazz Entity类
     * @param pageBean 过滤翻页条件
     * @return Entity的集合
     */
    default <T, F extends FilterBean> Sheet<T> querySheet(final Class<T> clazz, final PageBean<F> pageBean) {
        return querySheet(
                clazz,
                (SelectColumn) null,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheetAsync(clazz, (SelectColumn) null, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <F> 过滤类型
     * @param clazz Entity类
     * @param pageBean 过滤翻页条件
     * @return Entity的集合CompletableFuture
     */
    default <T, F extends FilterBean> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final PageBean<F> pageBean) {
        return querySheetAsync(
                clazz,
                (SelectColumn) null,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    default <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheet(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheetAsync(clazz, (SelectColumn) null, flipper, node);
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合
     */
    default <T> Sheet<T> querySheet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <F> 过滤类型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pageBean 过滤翻页条件
     * @return Entity的集合
     */
    default <T, F extends FilterBean> Sheet<T> querySheet(
            final Class<T> clazz, final SelectColumn selects, final PageBean<F> pageBean) {
        return querySheet(
                clazz,
                selects,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean 过滤条件
     * @return Entity的集合CompletableFuture
     */
    default <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheetAsync(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param <F> 过滤类型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param pageBean 过滤翻页条件
     * @return Entity的集合CompletableFuture
     */
    default <T, F extends FilterBean> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final SelectColumn selects, final PageBean<F> pageBean) {
        return querySheetAsync(
                clazz,
                selects,
                pageBean == null ? null : pageBean.getFlipper(),
                pageBean == null ? null : FilterNodeBean.createFilterNode(pageBean.getBean()));
    }

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合
     */
    public <T> Sheet<T> querySheet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的Sheet集合 <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
     * LIMIT {flipper.limit} <br>
     *
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node 过滤条件
     * @return Entity的集合CompletableFuture
     */
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);
}
