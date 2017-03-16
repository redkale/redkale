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
        source.insert(values);
        if (handler != null) handler.completed(null, values);
    }

    @Override
    public <T> int delete(T... values) {
        return source.delete(values);
    }

    @Override
    public <T> void delete(final AsyncHandler<Integer, T[]> handler, @RpcAttachment final T... values) {
        int rs = source.delete(values);
        if (handler != null) handler.completed(rs, values);
    }

    @Override
    public <T> int delete(final Class<T> clazz, final Serializable... ids) {
        return source.delete(clazz, ids);
    }

    @Override
    public <T> void delete(final AsyncHandler<Integer, Serializable[]> handler, final Class<T> clazz, @RpcAttachment final Serializable... ids) {
        int rs = source.delete(clazz, ids);
        if (handler != null) handler.completed(rs, ids);
    }

    @Override
    public <T> int delete(final Class<T> clazz, FilterNode node) {
        return source.delete(clazz, node);
    }

    @Override
    public <T> void delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        int rs = source.delete(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int delete(final Class<T> clazz, final Flipper flipper, FilterNode node) {
        return source.delete(clazz, flipper, node);
    }

    @Override
    public <T> void delete(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment FilterNode node) {
        int rs = source.delete(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int update(T... values) {
        return source.update(values);
    }

    @Override
    public <T> void update(final AsyncHandler<Integer, T[]> handler, @RpcAttachment final T... values) {
        int rs = source.update(values);
        if (handler != null) handler.completed(rs, values);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        return source.updateColumn(clazz, id, column, value);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable id, final String column, final Serializable value) {
        int rs = source.updateColumn(clazz, id, column, value);
        if (handler != null) handler.completed(rs, id);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final String column, final Serializable value, final FilterNode node) {
        return source.updateColumn(clazz, column, value, node);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, final String column, final Serializable value, @RpcAttachment final FilterNode node) {
        int rs = source.updateColumn(clazz, column, value, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final ColumnValue... values) {
        return source.updateColumn(clazz, id, values);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable id, final ColumnValue... values) {
        int rs = source.updateColumn(clazz, id, values);
        if (handler != null) handler.completed(rs, id);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values) {
        return source.updateColumn(clazz, node, values);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node, final ColumnValue... values) {
        int rs = source.updateColumn(clazz, node, values);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        return source.updateColumn(clazz, node, flipper, values);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        int rs = source.updateColumn(clazz, node, flipper, values);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int updateColumn(T bean, final String... columns) {
        return source.updateColumn(bean, columns);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, T> handler, @RpcAttachment final T bean, final String... columns) {
        int rs = source.updateColumn(bean, columns);
        if (handler != null) handler.completed(rs, bean);
    }

    @Override
    public <T> int updateColumn(T bean, final FilterNode node, final String... columns) {
        return source.updateColumn(bean, node, columns);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, @RpcAttachment final FilterNode node, final String... columns) {
        int rs = source.updateColumn(bean, node, columns);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> int updateColumn(T bean, final SelectColumn selects) {
        return source.updateColumn(bean, selects);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, T> handler, @RpcAttachment final T bean, final SelectColumn selects) {
        int rs = source.updateColumn(bean, selects);
        if (handler != null) handler.completed(rs, bean);
    }

    @Override
    public <T> int updateColumn(T bean, final FilterNode node, final SelectColumn selects) {
        return source.updateColumn(bean, node, selects);
    }

    @Override
    public <T> void updateColumn(final AsyncHandler<Integer, FilterNode> handler, final T bean, @RpcAttachment final FilterNode node, final SelectColumn selects) {
        int rs = source.updateColumn(bean, node, selects);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column) {
        return source.getNumberResult(entityClass, func, column);
    }

    @Override
    public void getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, @RpcAttachment final String column) {
        Number rs = source.getNumberResult(entityClass, func, column);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final void getNumberResult(final AsyncHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        getNumberResult(handler, entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, column, node);
    }

    @Override
    public void getNumberResult(final AsyncHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, @RpcAttachment final FilterNode node) {
        Number rs = source.getNumberResult(entityClass, func, column, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column) {
        return source.getNumberResult(entityClass, func, defVal, column);
    }

    @Override
    public void getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column) {
        Number rs = source.getNumberResult(entityClass, func, defVal, column);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final void getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column, final FilterBean bean) {
        getNumberResult(handler, entityClass, func, defVal, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final Number defVal, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, defVal, column, node);
    }

    @Override
    public void getNumberResult(final AsyncHandler<Number, String> handler, final Class entityClass, final FilterFunc func, final Number defVal, @RpcAttachment final String column, final FilterNode node) {
        Number rs = source.getNumberResult(entityClass, func, defVal, column, node);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, columns);
    }

    @Override
    public <N extends Number> void getNumberMap(final AsyncHandler<Map<String, N>, FilterFuncColumn[]> handler, final Class entityClass, @RpcAttachment final FilterFuncColumn... columns) {
        Map<String, N> rs = source.getNumberMap(entityClass, columns);
        if (handler != null) handler.completed(rs, columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, bean, columns);
    }

    @Override
    public final <N extends Number> void getNumberMap(final AsyncHandler<Map<String, N>, FilterNode> handler, final Class entityClass, @RpcAttachment final FilterBean bean, final FilterFuncColumn... columns) {
        getNumberMap(handler, entityClass, FilterNodeBean.createFilterNode(bean), columns);
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        return source.getNumberMap(entityClass, node, columns);
    }

    @Override
    public <N extends Number> void getNumberMap(final AsyncHandler<Map<String, N>, FilterNode> handler, final Class entityClass, @RpcAttachment final FilterNode node, final FilterFuncColumn... columns) {
        Map<String, N> rs = source.getNumberMap(entityClass, node, columns);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn) {
        Map<K, N> rs = source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
        if (handler != null) handler.completed(rs, keyColumn);
    }

    @Override
    public final <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, K extends Serializable, N extends Number> void queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterNode node) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final AsyncHandler<Map<K, N>, String> handler, final Class<T> entityClass, @RpcAttachment final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node) {
        Map<K, N> rs = source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
        if (handler != null) handler.completed(rs, keyColumn);
    }

    @Override
    public <T> T find(final Class<T> clazz, final Serializable pk) {
        return source.find(clazz, pk);
    }

    @Override
    public <T> void find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable pk) {
        T rs = source.find(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, SelectColumn selects, final Serializable pk) {
        return source.find(clazz, selects, pk);
    }

    @Override
    public <T> void find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, SelectColumn selects, @RpcAttachment final Serializable pk) {
        T rs = source.find(clazz, selects, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, final String column, final Serializable key) {
        return source.find(clazz, column, key);
    }

    @Override
    public <T> void find(final AsyncHandler<T, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        T rs = source.find(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> T find(final Class<T> clazz, FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        find(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, FilterNode node) {
        return source.find(clazz, node);
    }

    @Override
    public <T> void find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        T rs = source.find(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> T find(final Class<T> clazz, final SelectColumn selects, FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        find(handler, clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.find(clazz, selects, node);
    }

    @Override
    public <T> void find(final AsyncHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final FilterNode node) {
        T rs = source.find(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk) {
        return source.findColumn(clazz, column, pk);
    }

    @Override
    public <T> void findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable pk) {
        Serializable rs = source.findColumn(clazz, column, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean) {
        return source.findColumn(clazz, column, bean);
    }

    @Override
    public final <T> void findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final FilterBean bean) {
        findColumn(handler, clazz, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node) {
        return source.findColumn(clazz, column, node);
    }

    @Override
    public <T> void findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, @RpcAttachment final FilterNode node) {
        Serializable rs = source.findColumn(clazz, column, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        return source.findColumn(clazz, column, defValue, pk);
    }

    @Override
    public <T> void findColumn(final AsyncHandler<Serializable, Serializable> handler, final Class<T> clazz, final String column, final Serializable defValue, @RpcAttachment final Serializable pk) {
        Serializable rs = source.findColumn(clazz, column, defValue, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        return source.findColumn(clazz, column, defValue, bean);
    }

    @Override
    public final <T> void findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean) {
        findColumn(handler, clazz, column, defValue, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        return source.findColumn(clazz, column, defValue, node);
    }

    @Override
    public <T> void findColumn(final AsyncHandler<Serializable, FilterNode> handler, final Class<T> clazz, final String column, final Serializable defValue, @RpcAttachment final FilterNode node) {
        Serializable rs = source.findColumn(clazz, column, defValue, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final Serializable pk) {
        return source.exists(clazz, pk);
    }

    @Override
    public <T> void exists(final AsyncHandler<Boolean, Serializable> handler, final Class<T> clazz, @RpcAttachment final Serializable pk) {
        boolean rs = source.exists(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public final <T> boolean exists(final Class<T> clazz, FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void exists(final AsyncHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        exists(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, FilterNode node) {
        return source.exists(clazz, node);
    }

    @Override
    public <T> void exists(final AsyncHandler<Boolean, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        boolean rs = source.exists(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnSet(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final AsyncHandler<HashSet<V>, String> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final String column, final Serializable key) {
        HashSet<V> rs = source.queryColumnSet(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public final <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnSet(final AsyncHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        queryColumnSet(handler, selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnSet(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final AsyncHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        HashSet<V> rs = source.queryColumnSet(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnList(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final AsyncHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        List<V> rs = source.queryColumnList(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, String selectedColumn, Class<T> clazz, FilterBean bean) {
        queryColumnList(handler, selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        List<V> rs = source.queryColumnList(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        queryColumnList(handler, selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final AsyncHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, Flipper flipper, @RpcAttachment final FilterNode node) {
        List<V> rs = source.queryColumnList(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnSheet(final AsyncHandler<Sheet<V>, FilterNode> handler, String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        queryColumnSheet(handler, selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnSheet(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSheet(final AsyncHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        Sheet<V> rs = source.queryColumnSheet(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return source.queryList(clazz, column, key);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, @RpcAttachment final Serializable key) {
        List<T> rs = source.queryList(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        queryList(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return source.queryList(clazz, node);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, @RpcAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        queryList(handler, clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.queryList(clazz, selects, node);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @RpcAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return source.queryList(clazz, flipper, column, key);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, @RpcAttachment final Serializable key) {
        List<T> rs = source.queryList(clazz, flipper, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        queryList(handler, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, flipper, node);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        queryList(handler, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, selects, flipper, node);
    }

    @Override
    public <T> void queryList(final AsyncHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        querySheet(handler, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, flipper, node);
    }

    @Override
    public <T> void querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @RpcAttachment final FilterNode node) {
        Sheet<T> rs = source.querySheet(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        querySheet(handler, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, selects, flipper, node);
    }

    @Override
    public <T> void querySheet(final AsyncHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @RpcAttachment final FilterNode node) {
        Sheet<T> rs = source.querySheet(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
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
