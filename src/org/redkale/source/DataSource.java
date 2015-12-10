/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.util.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface DataSource {

    public static enum FuncEnum {

        AVG, COUNT, DISTINCTCOUNT, MAX, MIN, SUM;

        public String getColumn(String col) {
            if (this == DISTINCTCOUNT) return "COUNT(DISTINCT " + col + ")";
            return this.name() + "(" + col + ")";
        }
    }

    /**
     * 创建读连接
     *
     * @return
     */
    public DataConnection createReadConnection();

    /**
     * 创建写连接
     *
     * @return
     */
    public DataConnection createWriteConnection();

    //----------------------insert-----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    public <T> void insert(T... values);

    public <T> void insert(final DataConnection conn, T... values);

    /**
     * 将entity的对象全部加载到Cache中去，如果clazz没有被@javax.persistence.Cacheable注解则不做任何事
     * <p>
     * @param <T>
     * @param clazz
     */
    public <T> void refreshCache(Class<T> clazz);

    //-------------------------delete--------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    public <T> void delete(T... values);

    public <T> void delete(final DataConnection conn, T... values);

    public <T> void delete(Class<T> clazz, Serializable... ids);

    public <T> void delete(final DataConnection conn, Class<T> clazz, Serializable... ids);

    public <T> void delete(Class<T> clazz, FilterNode node);

    public <T> void delete(final DataConnection conn, Class<T> clazz, FilterNode node);

    //------------------------update---------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    public <T> void update(T... values);

    public <T> void update(final DataConnection conn, T... values);

    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value);

    public <T> void updateColumn(DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value);

    public <T> void updateColumns(final T value, final String... columns);

    public <T> void updateColumns(final DataConnection conn, final T value, final String... columns);

    public <T> void updateColumnIncrement(final Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnIncrement(final DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnAnd(final Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnAnd(final DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnOr(final Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnOr(final DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue);

    //-----------------------getXXXXResult-----------------------------
    public Number getNumberResult(final Class entityClass, final FuncEnum func, final String column);

    public Number getNumberResult(final Class entityClass, final FuncEnum func, final String column, FilterBean bean);

    public Number getNumberResult(final Class entityClass, final FuncEnum func, final String column, FilterNode node);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, final String keyColumn, FuncEnum func, final String funcColumn);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, final String keyColumn, FuncEnum func, final String funcColumn, final FilterBean bean);

    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, final String keyColumn, FuncEnum func, final String funcColumn, final FilterNode node);

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>
     * @param clazz
     * @param pk
     * @return
     */
    public <T> T find(Class<T> clazz, Serializable pk);

    public <T> T find(Class<T> clazz, final SelectColumn selects, Serializable pk);

    public <T> T findByColumn(Class<T> clazz, String column, Serializable key);

    public <T> T find(final Class<T> clazz, final FilterNode node);

    public <T> T find(final Class<T> clazz, final FilterBean bean);

    public <T> boolean exists(Class<T> clazz, Serializable pk);

    public <T> boolean exists(final Class<T> clazz, final FilterNode node);

    public <T> boolean exists(final Class<T> clazz, final FilterBean bean);

    //-----------------------list set----------------------------
    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    /**
     * 根据指定参数查询对象某个字段的集合
     * <p>
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param flipper
     * @param bean
     * @return
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key);

    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    //-----------------------sheet----------------------------
    /**
     * 根据指定参数查询对象某个对象的集合页
     * <p>
     * @param <T>
     * @param clazz
     * @param flipper
     * @param bean
     * @return
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

}
