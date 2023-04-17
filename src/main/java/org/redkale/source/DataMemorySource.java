/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceListener;
import org.redkale.annotation.ResourceType;
import org.redkale.service.Local;
import org.redkale.util.*;

/**
 *
 *
 * @author zhangjx
 */
/**
 * DataSource的Memory实现类 <br>
 * 注意: url 需要指定为 memory:datasource
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public class DataMemorySource extends AbstractDataSqlSource implements SearchSource {

    public DataMemorySource(String name) {
        this.name = name;
        this.cacheForbidden = false;
    }

    @Override
    protected int readMaxConns() {
        return -1;
    }

    @Override
    protected int writeMaxConns() {
        return -1;
    }

    @Local
    @Override
    public String getType() {
        return "memory";
    }

    @Override
    @ResourceListener
    public void onResourceChange(ResourceEvent[] events) {
    }

    public static boolean acceptsConf(AnyValue config) {
        return config.getValue(DATA_SOURCE_URL).startsWith("memory:");
    }

    public static boolean isSearchType(AnyValue config) {
        return config.getValue(DATA_SOURCE_URL).startsWith("memory:search");
    }

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
    protected final boolean isAsync() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=memory, name='" + resourceName() + "'}";
    }

    @Override
    public int directExecute(String sql) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int[] directExecute(String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <V> V directQuery(String sql, Function<DataResultSet, V> handler) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected String prepareParamSign(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDBAsync(EntityInfo<T> info, T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> deleteDBAsync(EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDBAsync(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> createTableDBAsync(EntityInfo<T> info, String copyTableSql, Serializable pk, String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDBAsync(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> updateEntityDBAsync(EntityInfo<T> info, T... entitys) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Integer> updateColumnDBAsync(EntityInfo<T> info, Flipper flipper, UpdateSqlInfo sql) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBAsync(EntityInfo<T> info, String[] tables, String sql, FilterNode node, FilterFuncColumn... columns) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDBAsync(EntityInfo<T> info, String[] tables, String sql, FilterFunc func, Number defVal, String column, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBAsync(EntityInfo<T> info, String[] tables, String sql, String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDBAsync(EntityInfo<T> info, String[] tables, String sql, ColumnNode[] funcNodes, String[] groupByColumns, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<T> findDBAsync(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, SelectColumn selects, Serializable pk, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDBAsync(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, String column, Serializable defValue, Serializable pk, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDBAsync(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, Serializable pk, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDBAsync(EntityInfo<T> info, boolean readcache, boolean needtotal, boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> int updateMapping(Class<T> clazz, String table) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> CompletableFuture<Integer> updateMappingAsync(Class<T> clazz, String table) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
