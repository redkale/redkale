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
public class DataMemorySource extends DataSqlSource implements SearchSource {

    public DataMemorySource(String name) {
        this.name = name;
        this.cacheForbidden = false;
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

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=memory, name='" + resourceName() + "'}";
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
    public <T> int updateMapping(final Class<T> clazz, String table) {
        return 0;
    }

    @Override
    public <T> CompletableFuture<Integer> updateMappingAsync(final Class<T> clazz, String table) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected boolean isOnlyCache(EntityInfo info) {
        return true;
    }

    @Local
    @Override
    public int directExecute(String sql) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Local
    @Override
    public int[] directExecute(String... sqls) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Local
    @Override
    public <V> V directQuery(String sql, Function<DataResultSet, V> handler) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected boolean isAsync() {
        return false;
    }

    @Override
    protected String prepareParamSign(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected <T> int insertDB(EntityInfo<T> info, T... entitys) {
        return 0;
    }

    @Override
    protected <T> int deleteDB(EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, String... sqls) {
        return 0;
    }

    @Override
    protected <T> int clearTableDB(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        return 0;
    }

    @Override
    protected <T> int dropTableDB(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        return 0;
    }

    @Override
    protected <T> int updateEntityDB(EntityInfo<T> info, T... entitys) {
        return 0;
    }

    @Override
    protected <T> int updateColumnDB(EntityInfo<T> info, Flipper flipper, SqlInfo sql) {
        return 0;
    }

    @Override
    protected <T, N extends Number> Map<String, N> getNumberMapDB(EntityInfo<T> info, String[] tables, String sql, FilterFuncColumn... columns) {
        return null;
    }

    @Override
    protected <T> Number getNumberResultDB(EntityInfo<T> info, String[] tables, String sql, Number defVal, String column) {
        return defVal;
    }

    @Override
    protected <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMapDB(EntityInfo<T> info, String[] tables, String sql, String keyColumn) {
        return null;
    }

    @Override
    protected <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMapDB(final EntityInfo<T> info, String[] tables, final String sql, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return null;
    }

    @Override
    protected <T> T findDB(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, SelectColumn selects) {
        return null;
    }

    @Override
    protected <T> Serializable findColumnDB(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk, String column, Serializable defValue) {
        return null;
    }

    @Override
    protected <T> boolean existsDB(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk) {
        return false;
    }

    @Override
    protected <T> Sheet<T> querySheetDB(EntityInfo<T> info, final boolean readCache, boolean needTotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        return new Sheet<>(0, new ArrayList());
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDBAsync(EntityInfo<T> info, T... entitys) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> deleteDBAsync(EntityInfo<T> info, String[] tables, Flipper flipper, FilterNode node, Map<String, List<Serializable>> pkmap, String... sqls) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDBAsync(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDBAsync(EntityInfo<T> info, String[] tables, FilterNode node, String... sqls) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> updateEntityDBAsync(EntityInfo<T> info, T... entitys) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> updateColumnDBAsync(EntityInfo<T> info, Flipper flipper, SqlInfo sql) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBAsync(EntityInfo<T> info, String[] tables, String sql, FilterFuncColumn... columns) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDBAsync(EntityInfo<T> info, String[] tables, String sql, Number defVal, String column) {
        return CompletableFuture.completedFuture(defVal);
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBAsync(EntityInfo<T> info, String[] tables, String sql, String keyColumn) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDBAsync(final EntityInfo<T> info, String[] tables, final String sql, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<T> findDBAsync(EntityInfo<T> info, String[] tables, String sql, boolean onlypk, SelectColumn selects) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDBAsync(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk, String column, Serializable defValue) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDBAsync(EntityInfo<T> info, final String[] tables, String sql, boolean onlypk) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDBAsync(EntityInfo<T> info, final boolean readCache, boolean needTotal, final boolean distinct, SelectColumn selects, Flipper flipper, FilterNode node) {
        return CompletableFuture.completedFuture(new Sheet<>(0, new ArrayList()));
    }

}
