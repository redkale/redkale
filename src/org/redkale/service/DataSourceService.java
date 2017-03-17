/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.function.*;
import javax.annotation.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * DataSource对应的Service类， 该类主要特点是将所有含FilterBean参数的方法重载成FilterNode对应的方法。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@AutoLoad(false)
@ResourceType({DataSourceService.class, DataSource.class})
public class DataSourceService implements DataSource, Service, AutoCloseable {

    @Resource(name = "$")
    private DataSource source;

    @Override
    public <T> void insert(@RpcCall(DataCallArrayAttribute.class) T... values) {
        source.insert(values);
    }

    @Override
    public <T> void insert(final AsyncHandler<Void, T[]> handler, @RpcAttachment @RpcCall(DataCallArrayAttribute.class) final T... values) {
        source.insert(handler, values);
    }

    @Override
    public <T> int delete(T... values) {
        return source.delete(values);
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, T[]> handler, @RpcAttachment final T... values) {
        return source.delete(handler, values);
    }

    @Override
    public <T> int delete(final Class<T> clazz, final Serializable... ids) {
        return source.delete(clazz, ids);
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, Serializable[]> handler, final Class<T> clazz, @RpcAttachment final Serializable... ids) {
        return source.delete(handler, clazz, ids);
    }

    @Override
    public <T> int delete(final Class<T> clazz, FilterNode node) {
        return source.delete(clazz, node);
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.delete(handler, clazz, node);
    }

    @Override
    public <T> int delete(final Class<T> clazz, final Flipper flipper, FilterNode node) {
        return source.delete(clazz, flipper, node);
    }

    @Override
    public <T> int delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment FilterNode node) {
        return source.delete(handler, clazz, flipper, node);
    }

    @Override
    public <T> int update(T... values) {
        return source.update(values);
    }

    @Override
    public <T> int update(final AsyncHandler<Integer, T[]> handler, @RpcAttachment final T... values) {
        return source.update(handler, values);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        return source.updateColumn(clazz, id, column, value);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable id, final String column, final Serializable value) {
        return source.updateColumn(handler, clazz, id, column, value);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final String column, final Serializable value, final FilterNode node) {
        return source.updateColumn(clazz, column, value, node);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final String column, final Serializable value, @RpcAttachment final FilterNode node) {
        return source.updateColumn(handler, clazz, column, value, node);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final ColumnValue... values) {
        return source.updateColumn(clazz, id, values);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable id, final ColumnValue... values) {
        return source.updateColumn(handler, clazz, id, values);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return source.updateColumn(clazz, node, values);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node, final ColumnValue... values) {
        return source.updateColumn(handler, clazz, node, values);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        return source.updateColumn(clazz, node, flipper, values);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        return source.updateColumn(handler, clazz, node, flipper, values);
    }

    @Override
    public <T> int updateColumn(T bean, final String... columns) {
        return source.updateColumn(bean, columns);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, T> handler, @RpcAttachment final T bean, final String... columns) {
        return source.updateColumn(handler, bean, columns);
    }

    @Override
    public <T> int updateColumn(T bean, final FilterNode node, final String... columns) {
        return source.updateColumn(bean, node, columns);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, @RpcAttachment final FilterNode node, final String... columns) {
        return source.updateColumn(handler, bean, node, columns);
    }

    @Override
    public <T> int updateColumn(T bean, final SelectColumn selects) {
        return source.updateColumn(bean, selects);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, T> handler, @RpcAttachment final T bean, final SelectColumn selects) {
        return source.updateColumn(handler, bean, selects);
    }

    @Override
    public <T> int updateColumn(T bean, final FilterNode node, final SelectColumn selects) {
        return source.updateColumn(bean, node, selects);
    }

    @Override
    public <T> int updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, @RpcAttachment final FilterNode node, final SelectColumn selects) {
        return source.updateColumn(handler, bean, node, selects);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column) {
        return source.getNumberResult(entityClass, func, column);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, @RpcAttachment final String column) {
        return source.getNumberResult(handler, entityClass, func, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <B extends FilterBean> Number getNumberResult(final AsyncHandler<Number, B> handler, final Class entityClass, final FilterFunc func, final String column, @RpcAttachment final B bean) {
        return source.getNumberResult(handler, entityClass, func, column, bean);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, column, node);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, @RpcAttachment final FilterNode node) {
        return source.getNumberResult(handler, entityClass, func, column, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column) {
        return source.getNumberResult(entityClass, func, defVal, column);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column) {
        return source.getNumberResult(handler, entityClass, func, defVal, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column, final FilterBean bean) {
        return source.getNumberResult(handler, entityClass, func, defVal, column, bean);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, defVal, column, node);
    }

    @Override
    public Number getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column, final FilterNode node) {
        return source.getNumberResult(handler, entityClass, func, defVal, column, node);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, FilterFuncColumn[]> handler, final Class entityClass, @RpcAttachment final FilterFuncColumn... columns) {
        return source.getNumberMap(handler, entityClass, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, bean, columns);
    }

    @Override
    public <N extends Number, B extends FilterBean> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, B> handler, final Class entityClass, @RpcAttachment final B bean, final FilterFuncColumn... columns) {
        return source.getNumberMap(handler, entityClass, bean, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, node, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final AsyncHandler<Map<String, N>, FilterNode> handler, final Class entityClass, @RpcAttachment final FilterNode node, final FilterFuncColumn... columns) {
        return source.getNumberMap(handler, entityClass, node, columns);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn) {
        return source.queryColumnMap(handler, entityClass, keyColumn, func, funcColumn);
    }

    @Override
    public final <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        return source.queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, bean);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterNode node) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node) {
        return source.queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final Serializable pk) {
        return source.find(clazz, pk);
    }

    @Override
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable pk) {
        return source.find(handler, clazz, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, SelectColumn selects, final Serializable pk) {
        return source.find(clazz, selects, pk);
    }

    @Override
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, SelectColumn selects, @RpcAttachment final Serializable pk) {
        return source.find(handler, clazz, selects, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return source.find(clazz, column, key);
    }

    @Override
    public <T> T find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        return source.find(handler, clazz, column, key);
    }

    @Override
    public final <T> T find(final Class<T> clazz, FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> T find(final AsyncHandler<T, B> handler, final Class<T> clazz, @RpcAttachment final B bean) {
        return source.find(handler, clazz, bean);
    }

    @Override
    public <T> T find(final Class<T> clazz, FilterNode node) {
        return source.find(clazz, node);
    }

    @Override
    public <T> T find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.find(handler, clazz, node);
    }

    @Override
    public final <T> T find(final Class<T> clazz, final SelectColumn selects, FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> T find(final AsyncHandler<T, B> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final B bean) {
        return source.find(handler, clazz, selects, bean);
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.find(clazz, selects, node);
    }

    @Override
    public <T> T find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final FilterNode node) {
        return source.find(handler, clazz, selects, node);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk) {
        return source.findColumn(clazz, column, pk);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable pk) {
        return source.findColumn(handler, clazz, column, pk);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean) {
        return source.findColumn(clazz, column, bean);
    }

    @Override
    public <T, B extends FilterBean> Serializable findColumn(final AsyncHandler<Serializable, B> handler, final Class<T> clazz, final String column, @RpcAttachment final B bean) {
        return source.findColumn(handler, clazz, column, bean);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node) {
        return source.findColumn(clazz, column, node);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, @RpcAttachment final FilterNode node) {
        return source.findColumn(handler, clazz, column, node);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        return source.findColumn(clazz, column, defValue, pk);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, final Serializable defValue, @RpcAttachment final Serializable pk) {
        return source.findColumn(handler, clazz, column, defValue, pk);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return source.findColumn(clazz, column, defValue, bean);
    }

    @Override
    public <T, B extends FilterBean> Serializable findColumn(final AsyncHandler<Serializable, B> handler, final Class<T> clazz, final String column, final Serializable defValue, @RpcAttachment final B bean) {
        return source.findColumn(handler, clazz, column, defValue, bean);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        return source.findColumn(clazz, column, defValue, node);
    }

    @Override
    public <T> Serializable findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final Serializable defValue, @RpcAttachment final FilterNode node) {
        return source.findColumn(handler, clazz, column, defValue, node);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final Serializable pk) {
        return source.exists(clazz, pk);
    }

    @Override
    public <T> boolean exists(final AsyncHandler<Boolean, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable pk) {
        return source.exists(handler, clazz, pk);
    }

    @Override
    public final <T> boolean exists(final Class<T> clazz, FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> boolean exists(final AsyncHandler<Boolean, B> handler, final Class<T> clazz, @RpcAttachment final B bean) {
        return source.exists(handler, clazz, bean);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, FilterNode node) {
        return source.exists(clazz, node);
    }

    @Override
    public <T> boolean exists(final AsyncHandler<Boolean, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.exists(handler, clazz, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnSet(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, String> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final String column, final Serializable key) {
        return source.queryColumnSet(handler, selectedColumn, clazz, column, key);

    }

    @Override
    public final <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, B> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final B bean) {
        return source.queryColumnSet(handler, selectedColumn, clazz, bean);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnSet(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final AsyncHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.queryColumnSet(handler, selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnList(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        return source.queryColumnList(handler, selectedColumn, clazz, column, key);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> List<V> queryColumnList(final AsyncHandler<List<V>, B> handler, String selectedColumn, Class<T> clazz, @RpcAttachment final B bean) {
        return source.queryColumnList(handler, selectedColumn, clazz, bean);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.queryColumnList(handler, selectedColumn, clazz, node);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> List<V> queryColumnList(final AsyncHandler<List<V>, B> handler, String selectedColumn, Class<T> clazz, Flipper flipper, @RpcAttachment final B bean) {
        return source.queryColumnList(handler, selectedColumn, clazz, flipper, bean);

    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.queryColumnList(handler, selectedColumn, clazz, flipper, node);
    }

    @Override
    public final <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable, B extends FilterBean> Sheet<V> queryColumnSheet(final AsyncHandler<Sheet<V>, B> handler, String selectedColumn, Class<T> clazz, Flipper flipper, @RpcAttachment B bean) {
        return source.queryColumnSheet(handler, selectedColumn, clazz, flipper, bean);
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnSheet(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final AsyncHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.queryColumnSheet(handler, selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return source.queryList(clazz, column, key);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        return source.queryList(handler, clazz, column, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, @RpcAttachment final B bean) {
        return source.queryList(handler, clazz, bean);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return source.queryList(clazz, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        return source.queryList(handler, clazz, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final B bean) {
        return source.queryList(handler, clazz, selects, bean);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.queryList(clazz, selects, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final FilterNode node) {
        return source.queryList(handler, clazz, selects, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return source.queryList(clazz, flipper, column, key);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, @RpcAttachment final Serializable key) {
        return source.queryList(handler, clazz, flipper, column, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final B bean) {
        return source.queryList(handler, clazz, flipper, bean);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.queryList(handler, clazz, flipper, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> List<T> queryList(final AsyncHandler<List<T>, B> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final B bean) {
        return source.queryList(handler, clazz, selects, flipper, bean);

    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, selects, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.queryList(handler, clazz, selects, flipper, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, B> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final B bean) {
        return source.querySheet(handler, clazz, flipper, bean);
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, flipper, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.querySheet(handler, clazz, flipper, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, B extends FilterBean> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, B> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final B bean) {
        return source.querySheet(handler, clazz, selects, flipper, bean);
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, selects, flipper, node);
    }

    @Override
    public <T> Sheet<T> querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final FilterNode node) {
        return source.querySheet(handler, clazz, selects, flipper, node);
    }

    @Override
    public void close() throws Exception {
        source.getClass().getMethod("close").invoke(source);
    }

    @Override
    public final void directQuery(String sql, Consumer<ResultSet> consumer) {
        source.directQuery(sql, consumer);
    }

    @Override
    public final int[] directExecute(String... sqls) {
        return source.directExecute(sqls);
    }

}
