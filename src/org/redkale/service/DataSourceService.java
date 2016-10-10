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
    public <T> void delete(T... values) {
        source.delete(values);
    }

    @Override
    public <T> void delete(final Class<T> clazz, final Serializable... ids) {
        source.delete(clazz, ids);
    }

    @Override
    public <T> void delete(final Class<T> clazz, FilterNode node) {
        source.delete(clazz, node);
    }

    @Override
    public <T> void update(T... values) {
        source.update(values);
    }

    @Override
    public <T> void updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        source.updateColumn(clazz, id, column, value);
    }

    @Override
    public <T> void updateColumn(final Class<T> clazz, final String column, final Serializable value, final FilterNode node) {
        source.updateColumn(clazz, column, value, node);
    }

    @Override
    public <T> void updateColumnIncrement(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnIncrement(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnAnd(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnAnd(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnOr(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnOr(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumns(T bean, final String... columns) {
        source.updateColumns(bean, columns);
    }

    @Override
    public <T> void updateColumns(T bean, final FilterNode node, final String... columns) {
        source.updateColumns(bean, node, columns);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column) {
        return source.getNumberResult(entityClass, func, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, column, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column) {
        return source.getNumberResult(entityClass, func, defVal, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, defVal, column, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
    }

    @Override
    public final <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterNode node) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final Serializable pk) {
        return source.find(clazz, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, SelectColumn selects, final Serializable pk) {
        return source.find(clazz, selects, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return source.find(clazz, column, key);
    }

    @Override
    public final <T> T find(final Class<T> clazz, FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, FilterNode node) {
        return source.find(clazz, node);
    }

    @Override
    public final <T> T find(final Class<T> clazz, final SelectColumn selects, FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.find(clazz, selects, node);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final Serializable pk) {
        return source.exists(clazz, pk);
    }

    @Override
    public final <T> boolean exists(final Class<T> clazz, FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, FilterNode node) {
        return source.exists(clazz, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnSet(selectedColumn, clazz, column, key);
    }

    @Override
    public final <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnSet(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnList(selectedColumn, clazz, column, key);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, node);
    }

    @Override
    public final <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnSheet(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return source.queryList(clazz, column, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return source.queryList(clazz, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.queryList(clazz, selects, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return source.queryList(clazz, flipper, column, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, flipper, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, selects, flipper, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, flipper, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, selects, flipper, node);
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
