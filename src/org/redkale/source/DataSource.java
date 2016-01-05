/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
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

    //----------------------异步版---------------------------------
    public <T> void insert(final CompletionHandler<Void, T[]> handler, final T... values);

    //-------------------------delete--------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>    泛型
     * @param values Entity对象
     */
    public <T> void delete(final T... values);

    /**
     * 根据主键值删除数据
     * 等价SQL: DELETE FROM WHERE {primary} IN {ids}
     *
     * @param <T>   Entity类的泛型
     * @param clazz Entity类
     * @param ids   主键值
     */
    public <T> void delete(final Class<T> clazz, final Serializable... ids);

    public <T> void delete(final Class<T> clazz, final FilterNode node);

    //----------------------异步版---------------------------------
    public <T> void delete(final CompletionHandler<Void, T[]> handler, final T... values);

    public <T> void delete(final CompletionHandler<Void, Serializable[]> handler, final Class<T> clazz, final Serializable... ids);

    public <T> void delete(final CompletionHandler<Void, FilterNode> handler, final Class<T> clazz, final FilterNode node);

    //------------------------update---------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>    泛型
     * @param values Entity对象
     */
    public <T> void update(final T... values);

    public <T> void updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value);

    public <T> void updateColumnIncrement(final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumnAnd(final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumnOr(final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumns(final T value, final String... columns);

    //----------------------异步版---------------------------------
    public <T> void update(final CompletionHandler<Void, T[]> handler, final T... values);

    public <T> void updateColumn(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, final Serializable value);

    public <T> void updateColumnIncrement(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumnAnd(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumnOr(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, final Serializable id, final String column, long incvalue);

    public <T> void updateColumns(final CompletionHandler<Void, T> handler, final T value, final String... columns);

    //-----------------------getXXXXResult-----------------------------
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterBean bean);

    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node);

    //----------------------异步版---------------------------------
    public void getNumberResult(final CompletionHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final String column);

    public void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterBean bean);

    public void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterNode node);

    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn);

    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean);

    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node);

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>   泛型
     * @param clazz Entity类
     * @param pk    主键值
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final Serializable pk);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    public <T> T findByColumn(final Class<T> clazz, final String column, final Serializable key);

    public <T> T find(final Class<T> clazz, final FilterBean bean);

    public <T> T find(final Class<T> clazz, final FilterNode node);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> boolean exists(final Class<T> clazz, final Serializable pk);

    public <T> boolean exists(final Class<T> clazz, final FilterBean bean);

    public <T> boolean exists(final Class<T> clazz, final FilterNode node);

    //----------------------异步版---------------------------------
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final Serializable pk);

    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    public <T> void findByColumn(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final String column, final Serializable key);

    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final FilterBean bean);

    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final FilterNode node);

    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> void exists(final CompletionHandler<Boolean, Serializable> handler, final Class<T> clazz, final Serializable pk);

    public <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterBean bean);

    public <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterNode node);

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
     * @return 字段值的集合
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    //----------------------异步版---------------------------------
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterNode node);

    /**
     * 根据指定参数查询对象某个字段的集合
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 字段名
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param bean           过滤Bean
     * @return 结果集合
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    //----------------------异步版---------------------------------
    public <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param key    过滤字段值
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

    //----------------------异步版---------------------------------
    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, final Serializable key);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterBean bean);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterNode node);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, final Serializable key);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //-----------------------sheet----------------------------
    /**
     * 根据指定参数查询对象某个对象的集合页
     * <p>
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤Bean
     * @return Entity的Sheet
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //----------------------异步版---------------------------------
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

}
