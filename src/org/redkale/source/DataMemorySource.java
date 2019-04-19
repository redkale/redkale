/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.net.URL;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.redkale.service.Local;
import org.redkale.util.*;

/**
 *
 *
 * @author zhangjx
 */
/**
 * DataSource的Memory实现类 <br>
 * 注意: javax.persistence.jdbc.url 需要指定为 memory:source
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
public class DataMemorySource extends DataSqlSource<Void> {

    public DataMemorySource(String unitName, URL persistxml, Properties readprop, Properties writeprop) {
        super(unitName, persistxml, readprop, writeprop);
        this.cacheForbidden = false;
    }

    @Local
    @Override
    public String getType() {
        return "memory";
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
    public <V> V directQuery(String sql, Function<ResultSet, V> handler) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected boolean isAsync() {
        return true;
    }

    @Override
    protected String prepareParamSign(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected PoolSource<Void> createPoolSource(DataSource source, String rwtype, ArrayBlockingQueue queue, Semaphore semaphore, Properties prop) {
        return null;
    }

    @Override
    protected <T> CompletableFuture<Integer> insertDB(EntityInfo<T> info, T... entitys) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> deleteDB(EntityInfo<T> info, Flipper flipper, String sql) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> clearTableDB(EntityInfo<T> info, String sql) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> dropTableDB(EntityInfo<T> info, String sql) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> updateDB(EntityInfo<T> info, T... entitys) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T> CompletableFuture<Integer> updateDB(EntityInfo<T> info, Flipper flipper, String sql, boolean prepared, Object... params) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(EntityInfo<T> info, String sql, FilterFuncColumn... columns) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Number> getNumberResultDB(EntityInfo<T> info, String sql, Number defVal, String column) {
        return CompletableFuture.completedFuture(defVal);
    }

    @Override
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(EntityInfo<T> info, String sql, String keyColumn) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<T> findDB(EntityInfo<T> info, String sql, boolean onlypk, SelectColumn selects) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Serializable> findColumnDB(EntityInfo<T> info, String sql, boolean onlypk, String column, Serializable defValue) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected <T> CompletableFuture<Boolean> existsDB(EntityInfo<T> info, String sql, boolean onlypk) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    protected <T> CompletableFuture<Sheet<T>> querySheetDB(EntityInfo<T> info, final boolean readcache, boolean needtotal, SelectColumn selects, Flipper flipper, FilterNode node) {
        return CompletableFuture.completedFuture(new Sheet<>());
    }

}
