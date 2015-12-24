/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import javax.annotation.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * DataSource对应的Service类， 该类主要特点是将所有含FilterBean参数的方法重载成FilterNode对应的方法。
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@AutoLoad(false)
public class DataSourceService implements DataSource, Service, AutoCloseable { 

    @Resource(name = "$")
    private DataSource source;

    @Override
    public DataConnection createReadConnection() {
        return source.createReadConnection();
    }

    @Override
    public DataConnection createWriteConnection() {
        return source.createWriteConnection();
    }

    @Override
    public <T> void insert(@DynCall(DataCallArrayAttribute.class) T... values) {
        source.insert(values);
    }

    @Override
    public <T> void insert(final CompletionHandler<Void, T[]> handler, @DynAttachment @DynCall(DataCallArrayAttribute.class) final T... values) {
        source.insert(values);
        if (handler != null) handler.completed(null, values);
    }

    @Override
    public <T> void insert(DataConnection conn, @DynCall(DataCallArrayAttribute.class) T... values) {
        source.insert(conn, values);
    }

    @Override
    public <T> void delete(T... values) {
        source.delete(values);
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, T[]> handler, @DynAttachment final T... values) {
        source.delete(values);
        if (handler != null) handler.completed(null, values);
    }

    @Override
    public <T> void delete(DataConnection conn, T... values) {
        source.delete(conn, values);
    }

    @Override
    public <T> void delete(final Class<T> clazz, final Serializable... ids) {
        source.delete(clazz, ids);
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, Serializable[]> handler, final Class<T> clazz, @DynAttachment final Serializable... ids) {
        source.delete(clazz, ids);
        if (handler != null) handler.completed(null, ids);
    }

    @Override
    public <T> void delete(DataConnection conn, Class<T> clazz, final Serializable... ids) {
        source.delete(conn, clazz, ids);
    }

    @Override
    public <T> void delete(final Class<T> clazz, FilterNode node) {
        source.delete(clazz, node);
    }

    @Override
    public <T> void delete(final CompletionHandler<Void, FilterNode> handler, final Class<T> clazz, @DynAttachment final FilterNode node) {
        source.delete(clazz, node);
        if (handler != null) handler.completed(null, node);
    }

    @Override
    public <T> void delete(DataConnection conn, Class<T> clazz, FilterNode node) {
        source.delete(conn, clazz, node);
    }

    @Override
    public <T> void update(T... values) {
        source.update(values);
    }

    @Override
    public <T> void update(final CompletionHandler<Void, T[]> handler, @DynAttachment final T... values) {
        source.update(values);
        if (handler != null) handler.completed(null, values);
    }

    @Override
    public <T> void update(DataConnection conn, T... values) {
        source.update(conn, values);
    }

    @Override
    public <T> void updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        source.updateColumn(clazz, id, column, value);
    }

    @Override
    public <T> void updateColumn(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable id, final String column, final Serializable value) {
        source.updateColumn(clazz, id, column, value);
        if (handler != null) handler.completed(null, id);
    }

    @Override
    public <T> void updateColumn(DataConnection conn, Class<T> clazz, final Serializable id, final String column, final Serializable value) {
        source.updateColumn(conn, clazz, id, column, value);
    }

    @Override
    public <T> void updateColumnIncrement(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnIncrement(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnIncrement(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable id, final String column, long incvalue) {
        source.updateColumnIncrement(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    @Override
    public <T> void updateColumnIncrement(DataConnection conn, Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnIncrement(conn, clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnAnd(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnAnd(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnAnd(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable id, final String column, long incvalue) {
        source.updateColumnAnd(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    @Override
    public <T> void updateColumnAnd(DataConnection conn, Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnAnd(conn, clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnOr(final Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnOr(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnOr(final CompletionHandler<Void, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable id, final String column, long incvalue) {
        source.updateColumnOr(clazz, id, column, incvalue);
        if (handler != null) handler.completed(null, id);
    }

    @Override
    public <T> void updateColumnOr(DataConnection conn, Class<T> clazz, final Serializable id, final String column, long incvalue) {
        source.updateColumnOr(conn, clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumns(T value, final String... columns) {
        source.updateColumns(value, columns);
    }

    @Override
    public <T> void updateColumns(final CompletionHandler<Void, T> handler, @DynAttachment final T value, final String... columns) {
        source.updateColumns(value, columns);
        if (handler != null) handler.completed(null, value);
    }

    @Override
    public <T> void updateColumns(DataConnection conn, T value, final String... columns) {
        source.updateColumns(conn, value, columns);
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column) {
        return source.getNumberResult(entityClass, func, column);
    }

    @Override
    public void getNumberResult(final CompletionHandler<Number, String> handler, final Class entityClass, final FilterFunc func, @DynAttachment final String column) {
        Number rs = source.getNumberResult(entityClass, func, column);
        if (handler != null) handler.completed(rs, column);
    }

    @Override
    public final Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterBean bean) {
        return getNumberResult(entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, final FilterBean bean) {
        getNumberResult(handler, entityClass, func, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(final Class entityClass, FilterFunc func, final String column, FilterNode node) {
        return source.getNumberResult(entityClass, func, column, node);
    }

    @Override
    public void getNumberResult(final CompletionHandler<Number, FilterNode> handler, final Class entityClass, final FilterFunc func, final String column, @DynAttachment final FilterNode node) {
        Number rs = source.getNumberResult(entityClass, func, column, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, String> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, @DynAttachment final String funcColumn) {
        Map<K, N> map = source.queryColumnMap(entityClass, keyColumn, func, funcColumn);
        if (handler != null) handler.completed(map, funcColumn);
    }

    @Override
    public final <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean) {
        queryColumnMap(handler, entityClass, keyColumn, func, funcColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, FilterFunc func, final String funcColumn, FilterNode node) {
        return source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> void queryColumnMap(final CompletionHandler<Map<K, N>, FilterNode> handler, final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, @DynAttachment final FilterNode node) {
        Map<K, N> map = source.queryColumnMap(entityClass, keyColumn, func, funcColumn, node);
        if (handler != null) handler.completed(map, node);
    }

    @Override
    public <T> T find(final Class<T> clazz, final Serializable pk) {
        return source.find(clazz, pk);
    }

    @Override
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable pk) {
        T rs = source.find(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> T find(final Class<T> clazz, SelectColumn selects, final Serializable pk) {
        return source.find(clazz, selects, pk);
    }

    @Override
    public <T> void find(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final SelectColumn selects, @DynAttachment final Serializable pk) {
        T rs = source.find(clazz, selects, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public <T> T findByColumn(final Class<T> clazz, final String column, final Serializable key) {
        return source.findByColumn(clazz, column, key);
    }

    @Override
    public <T> void findByColumn(final CompletionHandler<T, Serializable> handler, final Class<T> clazz, final String column, @DynAttachment final Serializable key) {
        T rs = source.findByColumn(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> T find(final Class<T> clazz, FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        find(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, FilterNode node) {
        return source.find(clazz, node);
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, @DynAttachment final FilterNode node) {
        T rs = source.find(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> T find(final Class<T> clazz, final SelectColumn selects, FilterBean bean) {
        return find(clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        find(handler, clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.find(clazz, selects, node);
    }

    @Override
    public <T> void find(final CompletionHandler<T, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @DynAttachment final FilterNode node) {
        T rs = source.find(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final Serializable pk) {
        return source.exists(clazz, pk);
    }

    @Override
    public <T> void exists(final CompletionHandler<Boolean, Serializable> handler, final Class<T> clazz, @DynAttachment final Serializable pk) {
        boolean rs = source.exists(clazz, pk);
        if (handler != null) handler.completed(rs, pk);
    }

    @Override
    public final <T> boolean exists(final Class<T> clazz, FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        exists(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, FilterNode node) {
        return source.exists(clazz, node);
    }

    @Override
    public <T> void exists(final CompletionHandler<Boolean, FilterNode> handler, final Class<T> clazz, @DynAttachment final FilterNode node) {
        boolean rs = source.exists(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnSet(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, @DynAttachment final Serializable key) {
        HashSet<V> rs = source.queryColumnSet(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final FilterBean bean) {
        queryColumnSet(handler, selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnSet(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSet(final CompletionHandler<HashSet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @DynAttachment final FilterNode node) {
        HashSet<V> rs = source.queryColumnSet(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, final String column, final Serializable key) {
        return source.queryColumnList(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, Serializable> handler, final String selectedColumn, final Class<T> clazz, final String column, @DynAttachment final Serializable key) {
        List<V> rs = source.queryColumnList(selectedColumn, clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @DynAttachment final FilterBean bean) {
        queryColumnList(handler, selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnList(final CompletionHandler<List<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, @DynAttachment final FilterNode node) {
        List<V> rs = source.queryColumnList(selectedColumn, clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryColumnSheet(selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        queryColumnSheet(handler, selectedColumn, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryColumnSheet(selectedColumn, clazz, flipper, node);
    }

    @Override
    public <T, V extends Serializable> void queryColumnSheet(final CompletionHandler<Sheet<V>, FilterNode> handler, final String selectedColumn, final Class<T> clazz, final Flipper flipper, @DynAttachment final FilterNode node) {
        Sheet<V> rs = source.queryColumnSheet(selectedColumn, clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key) {
        return source.queryList(clazz, column, key);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final String column, @DynAttachment final Serializable key) {
        List<T> rs = source.queryList(clazz, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final FilterBean bean) {
        queryList(handler, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node) {
        return source.queryList(clazz, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, @DynAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        queryList(handler, clazz, selects, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return source.queryList(clazz, selects, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, @DynAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, selects, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key) {
        return source.queryList(clazz, flipper, column, key);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, Serializable> handler, final Class<T> clazz, final Flipper flipper, final String column, @DynAttachment final Serializable key) {
        List<T> rs = source.queryList(clazz, flipper, column, key);
        if (handler != null) handler.completed(rs, key);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        queryList(handler, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, flipper, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @DynAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        queryList(handler, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.queryList(clazz, selects, flipper, node);
    }

    @Override
    public <T> void queryList(final CompletionHandler<List<T>, FilterNode> handler, final Class<T> clazz, SelectColumn selects, final Flipper flipper, @DynAttachment final FilterNode node) {
        List<T> rs = source.queryList(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        querySheet(handler, clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, flipper, node);
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final Flipper flipper, @DynAttachment final FilterNode node) {
        Sheet<T> rs = source.querySheet(clazz, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public final <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        querySheet(handler, clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return source.querySheet(clazz, selects, flipper, node);
    }

    @Override
    public <T> void querySheet(final CompletionHandler<Sheet<T>, FilterNode> handler, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, @DynAttachment final FilterNode node) {
        Sheet<T> rs = source.querySheet(clazz, selects, flipper, node);
        if (handler != null) handler.completed(rs, node);
    }

    @Override
    public void close() throws Exception {
        source.getClass().getMethod("close").invoke(source);
    }
}
