/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceChanged;
import org.redkale.annotation.ResourceType;
import org.redkale.inject.ResourceEvent;
import org.redkale.service.Local;
import org.redkale.util.*;

/** @author zhangjx */
/**
 * DataSource的Memory实现类 <br>
 * 注意: url 需要指定为 memory:datasource
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public class DataMemorySource extends AbstractDataSource {

    public DataMemorySource(String name) {
        this.name = name;
    }

    @Local
    @Override
    public String getType() {
        return "memory";
    }

    @Override
    @ResourceChanged
    public void onResourceChange(ResourceEvent[] events) {
        // do nothing
    }

    public static boolean acceptsConf(AnyValue config) {
        return config.getValue(DataSources.DATA_SOURCE_URL).startsWith("memory:");
    }
    //
    //    public static boolean isSearchType(AnyValue config) {
    //        return config.getValue(DATA_SOURCE_URL).startsWith("memory:search");
    //    }

    @Local
    @Override
    public <T> void compile(Class<T> clazz) {
        EntityInfo entityInfo = EntityInfo.compile(clazz, this);
        if (entityInfo.getCache() == null) {
            new EntityCache<>(entityInfo, null).clear();
        }
    }

    @Override
    protected final boolean isOnlyCache(EntityInfo info) {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hash(this) + "{type=memory, name='" + resourceName() + "'}";
    }

    @Override
    public <T> int insert(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> insertAsync(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int delete(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int delete(Class<T> clazz, Serializable... pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(Class<T> clazz, Serializable... pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int delete(Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int clearTable(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> clearTableAsync(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int createTable(Class<T> clazz, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> createTableAsync(Class<T> clazz, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int dropTable(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> dropTableAsync(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int update(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateAsync(T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumn(Class<T> clazz, Serializable pk, String column, Serializable value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, Serializable pk, String column, Serializable value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumn(Class<T> clazz, String column, Serializable value, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, String column, Serializable value, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumn(Class<T> clazz, Serializable pk, ColumnValue... values) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(Class<T> clazz, Serializable pk, ColumnValue... values) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumn(Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumn(T entity, FilterNode node, SelectColumn selects) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(T entity, FilterNode node, SelectColumn selects) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateColumnNonnull(T entity) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public <T> CompletableFuture<Integer> updateColumnNonnullAsync(T entity) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public Number getNumberResult(Class entityClass, FilterFunc func, Number defVal, String column, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(
            Class entityClass, FilterFunc func, Number defVal, String column, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <N extends Number> Map<String, N> getNumberMap(
            Class entityClass, FilterNode node, FilterFuncColumn... columns) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
            Class entityClass, FilterNode node, FilterFuncColumn... columns) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
            Class<T> entityClass, String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
            Class<T> entityClass, String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
            Class<T> entityClass, ColumnNode[] funcNodes, String groupByColumn, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
            Class<T> entityClass, ColumnNode[] funcNodes, String groupByColumn, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
            Class<T> entityClass, ColumnNode[] funcNodes, String[] groupByColumns, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
            Class<T> entityClass, ColumnNode[] funcNodes, String[] groupByColumns, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T find(Class<T> clazz, SelectColumn selects, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<T> findAsync(Class<T> clazz, SelectColumn selects, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T[] finds(Class<T> clazz, SelectColumn selects, Serializable... pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<T[]> findsAsync(Class<T> clazz, SelectColumn selects, Serializable... pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <D extends Serializable, T> List<T> findsList(Class<T> clazz, Stream<D> pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <D extends Serializable, T> CompletableFuture<List<T>> findsListAsync(Class<T> clazz, Stream<D> pks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T find(Class<T> clazz, SelectColumn selects, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<T> findAsync(Class<T> clazz, SelectColumn selects, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> Serializable findColumn(Class<T> clazz, String column, Serializable defValue, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(
            Class<T> clazz, String column, Serializable defValue, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> Serializable findColumn(Class<T> clazz, String column, Serializable defValue, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(
            Class<T> clazz, String column, Serializable defValue, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(Class<T> clazz, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> boolean exists(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(Class<T> clazz, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
            String selectedColumn, Class<T> clazz, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(Class<T> clazz, SelectColumn selects, Stream<K> keyStream) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            Class<T> clazz, SelectColumn selects, Stream<K> keyStream) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(Class<T> clazz, SelectColumn selects, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            Class<T> clazz, SelectColumn selects, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> Set<T> querySet(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(
            Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> List<T> queryList(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(
            Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(
            Class<T> clazz, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
