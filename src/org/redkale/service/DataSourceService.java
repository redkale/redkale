/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import java.util.*;
import javax.annotation.*;
import org.redkale.net.sncp.*;
import org.redkale.source.DataSource.Reckon;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * DataSource对应的Service类， 该类主要特点是将所有含FilterBean参数的方法重载成FilterNode对应的方法。
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class DataSourceService implements DataSource, Service {

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
    public <T> void insert(@SncpCall(DataCallArrayAttribute.class) T... values) {
        source.insert(values);
    }

    @Override
    public <T> void insert(DataConnection conn, @SncpCall(DataCallArrayAttribute.class) T... values) {
        source.insert(conn, values);
    }

    @Override
    public <T> void refreshCache(Class<T> clazz) {
        source.refreshCache(clazz);
    }

    @Override
    public <T> void delete(T... values) {
        source.delete(values);
    }

    @Override
    public <T> void delete(DataConnection conn, T... values) {
        source.delete(conn, values);
    }

    @Override
    public <T> void delete(Class<T> clazz, Serializable... ids) {
        source.delete(clazz, ids);
    }

    @Override
    public <T> void delete(DataConnection conn, Class<T> clazz, Serializable... ids) {
        source.delete(conn, clazz, ids);
    }

    @Override
    public <T> void delete(Class<T> clazz, FilterNode node) {
        source.delete(clazz, node);
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
    public <T> void update(DataConnection conn, T... values) {
        source.update(conn, values);
    }

    @Override
    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value) {
        source.updateColumn(clazz, id, column, value);
    }

    @Override
    public <T> void updateColumn(DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value) {
        source.updateColumn(conn, clazz, id, column, value);
    }

    @Override
    public <T> void updateColumns(T value, String... columns) {
        source.updateColumns(value, columns);
    }

    @Override
    public <T> void updateColumns(DataConnection conn, T value, String... columns) {
        source.updateColumns(conn, value, columns);
    }

    @Override
    public <T> void updateColumnIncrement(Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnIncrement(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnIncrement(DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnIncrement(conn, clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnAnd(Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnAnd(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnAnd(DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnAnd(conn, clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnOr(Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnOr(clazz, id, column, incvalue);
    }

    @Override
    public <T> void updateColumnOr(DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        source.updateColumnOr(conn, clazz, id, column, incvalue);
    }

    @Override
    public Number getNumberResult(Class entityClass, Reckon reckon, String column) {
        return source.getNumberResult(entityClass, reckon, column);
    }

    @Override
    public final Number getNumberResult(Class entityClass, Reckon reckon, String column, FilterBean bean) {
        return getNumberResult(entityClass, reckon, column, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public Number getNumberResult(Class entityClass, Reckon reckon, String column, FilterNode node) {
        return source.getNumberResult(entityClass, reckon, column, node);
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, String keyColumn, Reckon reckon, String reckonColumn) {
        return source.queryColumnMap(entityClass, keyColumn, reckon, reckonColumn);
    }

    @Override
    public final <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, String keyColumn, Reckon reckon, String reckonColumn, FilterBean bean) {
        return queryColumnMap(entityClass, keyColumn, reckon, reckonColumn, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(Class<T> entityClass, String keyColumn, Reckon reckon, String reckonColumn, FilterNode node) {
        return source.queryColumnMap(entityClass, keyColumn, reckon, reckonColumn, node);
    }

    @Override
    public <T> T find(Class<T> clazz, Serializable pk) {
        return source.find(clazz, pk);
    }

    @Override
    public <T> T find(Class<T> clazz, SelectColumn selects, Serializable pk) {
        return source.find(clazz, selects, pk);
    }

    @Override
    public <T> T findByColumn(Class<T> clazz, String column, Serializable key) {
        return source.findByColumn(clazz, column, key);
    }

    @Override
    public <T> T find(Class<T> clazz, FilterNode node) {
        return source.find(clazz, node);
    }

    @Override
    public final <T> T find(Class<T> clazz, FilterBean bean) {
        return find(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        return source.exists(clazz, pk);
    }

    @Override
    public <T> boolean exists(Class<T> clazz, FilterNode node) {
        return source.exists(clazz, node);
    }

    @Override
    public final <T> boolean exists(Class<T> clazz, FilterBean bean) {
        return exists(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return source.queryColumnSet(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnSet(selectedColumn, clazz, node);
    }

    @Override
    public final <T, V extends Serializable> HashSet<V> queryColumnSet(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnSet(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        return source.queryColumnList(selectedColumn, clazz, column, key);
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterNode node) {
        return source.queryColumnList(selectedColumn, clazz, node);
    }

    @Override
    public final <T, V extends Serializable> List<V> queryColumnList(String selectedColumn, Class<T> clazz, FilterBean bean) {
        return queryColumnList(selectedColumn, clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, String column, Serializable key) {
        return source.queryList(clazz, column, key);
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, FilterNode node) {
        return source.queryList(clazz, node);
    }

    @Override
    public final <T> List<T> queryList(Class<T> clazz, FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, SelectColumn selects, FilterNode node) {
        return source.queryList(clazz, selects, node);
    }

    @Override
    public final <T> List<T> queryList(Class<T> clazz, SelectColumn selects, FilterBean bean) {
        return queryList(clazz, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, Flipper flipper, String column, Serializable key) {
        return source.queryList(clazz, flipper, column, key);
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.queryList(clazz, flipper, node);
    }

    @Override
    public final <T> List<T> queryList(Class<T> clazz, Flipper flipper, FilterBean bean) {
        return queryList(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        return source.queryList(clazz, selects, flipper, node);
    }

    @Override
    public final <T> List<T> queryList(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterBean bean) {
        return queryList(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
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
    public final <T> Sheet<T> querySheet(Class<T> clazz, Flipper flipper, FilterBean bean) {
        return querySheet(clazz, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, Flipper flipper, FilterNode node) {
        return source.querySheet(clazz, flipper, node);
    }

    @Override
    public final <T> Sheet<T> querySheet(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterBean bean) {
        return querySheet(clazz, selects, flipper, FilterNodeBean.createFilterNode(bean));
    }

    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        return source.querySheet(clazz, selects, flipper, node);
    }

}
