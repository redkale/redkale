/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Consumer;
import org.redkale.util.*;

/**
 *
 * DataSource 为数据库或内存数据库的数据源，提供类似JPA、Hibernate的接口与功能。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface DataSource {

    //----------------------insert-----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>    泛型
     * @param values Entity对象
     */
    public <T> void insert(final T... values);

    //-------------------------delete--------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>    泛型
     * @param values Entity对象
     *
     * @return 删除的数据条数
     */
    public <T> int delete(final T... values);

    /**
     * 根据主键值删除数据
     * 等价SQL: DELETE FROM WHERE {primary} IN {ids}
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param ids   主键值
     *
     * @return 删除的数据条数
     */
    public <T> int delete(final Class<T> clazz, final Serializable... ids);

    public <T> int delete(final Class<T> clazz, final FilterNode node);
    
    public <T> int delete(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    //------------------------update---------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>    泛型
     * @param values Entity对象
     *
     * @return 更新的数据条数
     */
    public <T> int update(final T... values);

    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value);

    public <T> int updateColumn(final Class<T> clazz, final String column, final Serializable value, final FilterNode node);

    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final ColumnValue... values);

    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values);

    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values);

    public <T> int updateColumns(final T bean, final String... columns);

    public <T> int updateColumns(final T bean, final FilterNode node, final String... columns);

    //############################################# 查询接口 #############################################
    //-----------------------getXXXXResult-----------------------------
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterBean bean);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterBean bean);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node);

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>   泛型
     * @param clazz Entity类
     * @param pk    主键值
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final Serializable pk);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    public <T> T find(final Class<T> clazz, final String column, final Serializable key);

    public <T> T find(final Class<T> clazz, final FilterBean bean);

    public <T> T find(final Class<T> clazz, final FilterNode node);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean);

    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node);

    public <T> boolean exists(final Class<T> clazz, final Serializable pk);

    public <T> boolean exists(final Class<T> clazz, final FilterBean bean);

    public <T> boolean exists(final Class<T> clazz, final FilterNode node);

    //-----------------------list set----------------------------
    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 字段名
     * @param clazz          Entity类
     * @param column         过滤字段名
     * @param key            过滤字段值
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 根据指定参数查询对象某个字段的集合
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 字段名
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param bean           过滤Bean
     *
     * @return 结果集合
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param key    过滤字段值
     *
     * @return Entity的List
     */
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key);

    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //-----------------------sheet----------------------------
    /**
     * 根据指定参数查询对象某个对象的集合页
     * <p>
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     *
     * @return Entity的Sheet
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //-----------------------direct----------------------------
    /**
     * 直接本地执行SQL语句进行查询，远程模式不可用
     * 通常用于复杂的关联查询
     *
     * @param sql      SQL语句
     * @param consumer 回调函数
     */
    public void directQuery(String sql, final Consumer<ResultSet> consumer);

    /**
     * 直接本地执行SQL语句进行增删改操作，远程模式不可用
     * 通常用于复杂的更新操作
     *
     * @param sqls SQL语句
     *
     * @return 结果数组
     */
    public int[] directExecute(String... sqls);
}
