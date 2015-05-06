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
     * @param ids   主键值
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
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     */
    public <T> void deleteByColumn(Class<T> clazz, String column, Serializable... keys);

    /**
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column
     * @param keys
     */
    public <T> void deleteByColumn(final DataConnection conn, Class<T> clazz, String column, Serializable... keys);

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    public <T> void deleteByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2);

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    public <T> void deleteByTwoColumn(final DataConnection conn, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2);

    //------------------------update---------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    public <T> void update(T... values);

    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
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

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    public <T> void updateColumn(final DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value);

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

    /**
     * 根据主键值给对象的column对应的值+incvalue， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param id
     * @param column
     * @param incvalue
     */
    public <T> void updateColumnIncrement(final DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue);

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param value
     * @param columns
     */
    public <T> void updateColumns(final T value, final String... columns);

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param value
     * @param columns
     */
    public <T> void updateColumns(final DataConnection conn, final T value, final String... columns);

    //-----------------------getSingleResult-----------------------------
    //-----------------------------MAX-----------------------------
    public Number getMaxSingleResult(final Class entityClass, final String column);

    public Number getMaxSingleResult(final Class entityClass, final String column, FilterBean bean);

    //-----------------------------MIN-----------------------------
    public Number getMinSingleResult(final Class entityClass, final String column);

    public Number getMinSingleResult(final Class entityClass, final String column, FilterBean bean);

    //-----------------------------SUM-----------------------------
    public Number getSumSingleResult(final Class entityClass, final String column);

    public Number getSumSingleResult(final Class entityClass, final String column, FilterBean bean);

    //----------------------------COUNT----------------------------
    public Number getCountSingleResult(final Class entityClass);

    public Number getCountSingleResult(final Class entityClass, FilterBean bean);

    public Number getCountDistinctSingleResult(final Class entityClass, String column);

    public Number getCountDistinctSingleResult(final Class entityClass, String column, FilterBean bean);

    //-----------------------------AVG-----------------------------
    public Number getAvgSingleResult(final Class entityClass, final String column);

    public Number getAvgSingleResult(final Class entityClass, final String column, FilterBean bean);

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

    /**
     * 根据主键值集合获取对象集合
     *
     * @param <T>
     * @param clazz
     * @param ids
     * @return
     */
    public <T> T[] find(Class<T> clazz, Serializable... ids);

    /**
     * 根据唯一索引获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    public <T> T findByColumn(Class<T> clazz, String column, Serializable key);

    /**
     * 根据两个字段的值获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @return
     */
    public <T> T findByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2);

    /**
     * 根据唯一索引获取对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     * @return
     */
    public <T> T[] findByColumn(Class<T> clazz, String column, Serializable... keys);

    /**
     * 根据字段值拉去对象， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects 只拉起指定字段名或者排除指定字段名的值
     * @param column
     * @param keys
     * @return
     */
    public <T> T[] findByColumn(Class<T> clazz, final SelectColumn selects, String column, Serializable... keys);

    /**
     * 根据过滤对象FilterBean查询第一个符合条件的对象
     *
     * @param <T>
     * @param clazz
     * @param bean
     * @return
     */
    public <T> T find(final Class<T> clazz, final FilterBean bean);

    //-----------------------list set----------------------------
    public <T> int[] queryColumnIntSet(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T> long[] queryColumnLongSet(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T> int[] queryColumnIntList(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T> long[] queryColumnLongList(String selectedColumn, Class<T> clazz, String column, Serializable key);

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
    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, Serializable key);

    public <T> int[] queryColumnIntSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

    public <T> long[] queryColumnLongSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

    public <T> int[] queryColumnIntList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

    public <T> long[] queryColumnLongList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param column
     * @param express
     * @param key
     * @return
     */
    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param column
     * @param express
     * @param key
     * @return
     */
    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key);

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

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param express
     * @param key
     * @return
     */
    public <T> List<T> queryList(Class<T> clazz, String column, FilterExpress express, Serializable key);

    //-----------------------list----------------------------
    /**
     * 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param express
     * @param key
     * @return
     */
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, FilterExpress express, Serializable key);

    /**
     * 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param key
     * @return
     */
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, Serializable key);

    /**
     * 根据过滤对象FilterBean查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param bean
     * @return
     */
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean);

    /**
     * 根据过滤对象FilterBean查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param bean
     * @return
     */
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

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据
     *
     * @param <T>
     * @param clazz
     * @param flipper
     * @param bean
     * @return
     */
    public <T> Sheet<T> querySheet(Class<T> clazz, final Flipper flipper, final FilterBean bean);

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param flipper
     * @param bean
     * @return
     */
    public <T> Sheet<T> querySheet(Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

}
