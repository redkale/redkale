/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface DataSource {

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

    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
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

    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    public <T> void delete(final DataConnection conn, T... values);

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param ids 主键值
     */
    public <T> void delete(Class<T> clazz, Serializable... ids);

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param ids
     */
    public <T> void delete(final DataConnection conn, Class<T> clazz, Serializable... ids);

    /**
     * 根据FilterNode的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param node
     */
    public <T> void delete(Class<T> clazz, FilterNode node);

    /**
     * 根据FilterNode的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param node
     */
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

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value);

    public <T> void updateColumn(DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value);

    /**
     * 根据主键值给对象的column对应的值+incvalue， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param id
     * @param column
     * @param incvalue
     */
    public <T> void updateColumnIncrement(Class<T> clazz, Serializable id, String column, long incvalue);

    public <T> void updateColumnIncrement(final DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue);

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param value
     * @param columns
     */
    public <T> void updateColumns(final T value, final String... columns);

    public <T> void updateColumns(final DataConnection conn, final T value, final String... columns);

    //-----------------------getSingleResult-----------------------------
    //-----------------------------MAX-----------------------------
    public Number getMaxSingleResult(final Class entityClass, final String column);

    public Number getMaxSingleResult(final Class entityClass, final String column, FilterBean bean);

    public Number getMaxSingleResult(final Class entityClass, final String column, FilterNode node);

    //-----------------------------MIN-----------------------------
    public Number getMinSingleResult(final Class entityClass, final String column);

    public Number getMinSingleResult(final Class entityClass, final String column, FilterBean bean);

    public Number getMinSingleResult(final Class entityClass, final String column, FilterNode node);

    //-----------------------------SUM-----------------------------
    public Number getSumSingleResult(final Class entityClass, final String column);

    public Number getSumSingleResult(final Class entityClass, final String column, FilterBean bean);

    public Number getSumSingleResult(final Class entityClass, final String column, FilterNode node);

    //----------------------------COUNT----------------------------
    public Number getCountSingleResult(final Class entityClass);

    public Number getCountSingleResult(final Class entityClass, FilterBean bean);

    public Number getCountSingleResult(final Class entityClass, FilterNode node);

    public Number getCountDistinctSingleResult(final Class entityClass, String column);

    public Number getCountDistinctSingleResult(final Class entityClass, String column, FilterBean bean);

    public Number getCountDistinctSingleResult(final Class entityClass, String column, FilterNode node);

    //-----------------------------AVG-----------------------------
    public Number getAvgSingleResult(final Class entityClass, final String column);

    public Number getAvgSingleResult(final Class entityClass, final String column, FilterBean bean);

    public Number getAvgSingleResult(final Class entityClass, final String column, FilterNode node);

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
    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node);

    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean);

    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node);

    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean);

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    public <T> List<T> queryList(Class<T> clazz, String column, Serializable key);

    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    //-----------------------sheet----------------------------
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
    public <T, V> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T, V> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> Sheet<T> querySheet(Class<T> clazz, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(Class<T> clazz, final Flipper flipper, final FilterNode node);

    public <T> Sheet<T> querySheet(Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    public <T> Sheet<T> querySheet(Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

}
