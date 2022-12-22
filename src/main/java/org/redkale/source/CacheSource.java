/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.Convert;
import org.redkale.util.Resourcable;

/**
 * Redkale中缓存数据源的核心类。 主要供业务开发者使用， 技术开发者提供CacheSource的实现。<br>
 * CacheSource提供三种数据类型操作: String、Long、byte[]和泛型指定的数据类型。<br>
 * String统一用setString、getString等系列方法。<br>
 * Long统一用setLong、getLong、incr等系列方法。<br>
 * byte[]统一用setBytes、getBytes等系列方法。<br>
 * 其他则供自定义数据类型使用。
 *
 * param V value的类型 移除 @2.4.0
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface CacheSource extends Resourcable {

    public String getType();

    default boolean isOpen() {
        return true;
    }

    //------------------------ get ------------------------    
    public <T> T get(final String key, final Type type);

    public String getString(final String key);

    public long getLong(final String key, long defValue);

    public byte[] getBytes(final String key);

    //------------------------ getex ------------------------
    public <T> T getex(final String key, final int expireSeconds, final Type type);

    public String getexString(final String key, final int expireSeconds);

    public long getexLong(final String key, final int expireSeconds, long defValue);

    public byte[] getexBytes(final String key, final int expireSeconds);

    //------------------------ getset ------------------------
    public <T> T getSet(final String key, final Type type, final T value);

    public <T> T getSet(final String key, final Convert convert, final Type type, final T value);

    public long getSetLong(final String key, long value, long defValue);

    public String getSetString(final String key, final String value);

    public byte[] getSetBytes(final String key, final byte[] value);

    //------------------------ set ------------------------
    public <T> void set(final String key, final Convert convert, final T value);

    public <T> void set(final String key, final Type type, final T value);

    public <T> void set(final String key, final Convert convert, final Type type, final T value);

    public void setString(final String key, final String value);

    public void setLong(final String key, final long value);

    public void setBytes(final String key, final byte[] value);

    public <T> void setBytes(final String key, final Convert convert, final Type type, final T value);

    //------------------------ setnx ------------------------
    public <T> void setnx(final String key, final Convert convert, final T value);

    public <T> void setnx(final String key, final Type type, final T value);

    public <T> void setnx(final String key, final Convert convert, final Type type, final T value);

    public void setnxString(final String key, final String value);

    public void setnxLong(final String key, final long value);

    public void setexLong(final String key, final int expireSeconds, final long value);

    //------------------------ setex ------------------------
    public <T> void setex(final String key, final int expireSeconds, final Convert convert, final T value);

    public <T> void setex(final String key, final int expireSeconds, final Type type, final T value);

    public <T> void setex(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public void setexString(final String key, final int expireSeconds, final String value);

    public void setexBytes(final String key, final int expireSeconds, final byte[] value);

    public <T> void setexBytes(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    //------------------------ xxxx ------------------------
    public boolean exists(final String key);

    public void expire(final String key, final int seconds);

    public int del(final String key);

    public long incr(final String key);

    public long incr(final String key, long num);

    public long decr(final String key);

    public long decr(final String key, long num);

    //------------------------ hget ------------------------
    public <T> T hget(final String key, final String field, final Type type);

    public String hgetString(final String key, final String field);

    public long hgetLong(final String key, final String field, long defValue);

    //------------------------ hset ------------------------
    public <T> void hset(final String key, final String field, final Convert convert, final T value);

    public <T> void hset(final String key, final String field, final Type type, final T value);

    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value);

    public void hsetString(final String key, final String field, final String value);

    public void hsetLong(final String key, final String field, final long value);

    //------------------------ hsetnx ------------------------
    public <T> void hsetnx(final String key, final String field, final Convert convert, final T value);

    public <T> void hsetnx(final String key, final String field, final Type type, final T value);

    public <T> void hsetnx(final String key, final String field, final Convert convert, final Type type, final T value);

    public void hsetnxString(final String key, final String field, final String value);

    public void hsetnxLong(final String key, final String field, final long value);

    //------------------------ hxxx ------------------------
    public int hdel(final String key, String... fields);

    public List<String> hkeys(final String key);

    public int hlen(final String key);

    public long hincr(final String key, String field);

    public long hincr(final String key, String field, long num);

    public long hdecr(final String key, String field);

    public long hdecr(final String key, String field, long num);

    public boolean hexists(final String key, String field);

    //------------------------ hmxx ------------------------
    public void hmset(final String key, final Serializable... values);

    public <T> List<T> hmget(final String key, final Type type, final String... fields);

    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit);

    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit, String pattern);

    //------------------------ list ------------------------
    public <T> List<T> lrange(final String key, final Type componentType);

    public <T> Map<String, List<T>> lrange(final Type componentType, final String... keys);

    public <T> void rpush(final String key, final Type componentType, final T value);

    public <T> int lrem(final String key, final Type componentType, final T value);

    //---------- list-string ----------
    public void rpushString(final String key, final String value);

    public int lremString(final String key, final String value);

    //---------- list-long ----------
    public void rpushLong(final String key, final long value);

    public int lremLong(final String key, final long value);

    //------------------------ set ------------------------     
    public <T> Set<T> smembers(final String key, final Type componentType);

    public <T> Map<String, Set<T>> smembers(final Type componentType, final String... keys);

    public <T> boolean sismember(final String key, final Type componentType, final T value);

    public <T> void sadd(final String key, final Type componentType, final T value);

    public <T> int srem(final String key, final Type componentType, final T value);

    public <T> T spop(final String key, final Type componentType);

    public <T> Set<T> spop(final String key, final int count, final Type componentType);

    //---------- set-string ----------
    public boolean sismemberString(final String key, final String value);

    public void saddString(final String key, final String value);

    public int sremString(final String key, final String value);

    public String spopString(final String key);

    public Set<String> spopString(final String key, final int count);

    //---------- set-long ----------
    public boolean sismemberLong(final String key, final long value);

    public void saddLong(final String key, final long value);

    public int sremLong(final String key, final long value);

    public Long spopLong(final String key);

    public Set<Long> spopLong(final String key, final int count);

    //------------------------ collection ------------------------
    @Deprecated
    public <T> Collection<T> getCollection(final String key, final Type componentType);

    @Deprecated
    public <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, final String... keys);

    @Deprecated
    public int getCollectionSize(final String key);

    @Deprecated
    public <T> Collection<T> getexCollection(final String key, final int expireSeconds, final Type componentType);

    @Deprecated
    public Map<String, Collection<String>> getStringCollectionMap(final boolean set, final String... keys);

    @Deprecated
    public Collection<String> getStringCollection(final String key);

    @Deprecated
    public Collection<String> getexStringCollection(final String key, final int expireSeconds);

    @Deprecated
    public Collection<Long> getLongCollection(final String key);

    @Deprecated
    public Map<String, Collection<Long>> getLongCollectionMap(final boolean set, final String... keys);

    @Deprecated
    public Collection<Long> getexLongCollection(final String key, final int expireSeconds);

    //------------------------ other ------------------------
    public List<String> queryKeys();

    public List<String> queryKeysStartsWith(String startsWith);

    public List<String> queryKeysEndsWith(String endsWith);

    public int getKeySize();

    public <T> Map<String, T> getMap(final Type componentType, final String... keys);

    public Map<String, String> getStringMap(final String... keys);

    public String[] getStringArray(final String... keys);

    public Map<String, Long> getLongMap(final String... keys);

    public Long[] getLongArray(final String... keys);

    //---------------------- CompletableFuture 异步版 ---------------------------------
    default CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(isOpen());
    }

    //------------------------ getAsync ------------------------  
    public <T> CompletableFuture<T> getAsync(final String key, final Type type);

    public CompletableFuture<String> getStringAsync(final String key);

    public CompletableFuture<Long> getLongAsync(final String key, long defValue);

    public CompletableFuture<byte[]> getBytesAsync(final String key);

    //------------------------ getexAsync ------------------------
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type);

    public CompletableFuture<String> getexStringAsync(final String key, final int expireSeconds);

    public CompletableFuture<Long> getexLongAsync(final String key, final int expireSeconds, long defValue);

    public CompletableFuture<byte[]> getexBytesAsync(final String key, final int expireSeconds);

    //------------------------ getsetAsync ------------------------
    public <T> CompletableFuture<T> getSetAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<T> getSetAsync(final String key, final Convert convert, final Type type, final T value);

    public CompletableFuture<Long> getSetLongAsync(final String key, long value, long defValue);

    public CompletableFuture<String> getSetStringAsync(final String key, final String value);

    public CompletableFuture<byte[]> getSetBytesAsync(final String key, final byte[] value);

    //------------------------ setAsync ------------------------
    public <T> CompletableFuture<Void> setAsync(final String key, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<Void> setAsync(final String key, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> setStringAsync(final String key, final String value);

    public CompletableFuture<Void> setLongAsync(final String key, long value);

    public CompletableFuture<Void> setBytesAsync(final String key, final byte[] value);

    public <T> CompletableFuture<Void> setBytesAsync(final String key, final Convert convert, final Type type, final T value);

    //------------------------ setnxAsync ------------------------
    public <T> CompletableFuture<Void> setnxAsync(final String key, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setnxAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<Void> setnxAsync(final String key, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> setnxStringAsync(final String key, final String value);

    public CompletableFuture<Void> setnxLongAsync(final String key, long value);

    //------------------------ setexAsync ------------------------
    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Type type, final T value);

    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> setexStringAsync(final String key, final int expireSeconds, final String value);

    public CompletableFuture<Void> setexLongAsync(final String key, final int expireSeconds, final long value);

    public CompletableFuture<Void> setexBytesAsync(final String key, final int expireSeconds, final byte[] value);

    public <T> CompletableFuture<Void> setexBytesAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    //------------------------ xxxxAsync ------------------------
    public CompletableFuture<Boolean> existsAsync(final String key);

    public CompletableFuture<Void> expireAsync(final String key, final int seconds);

    public CompletableFuture<Integer> delAsync(final String key);

    public CompletableFuture<Long> incrAsync(final String key);

    public CompletableFuture<Long> incrAsync(final String key, long num);

    public CompletableFuture<Long> decrAsync(final String key);

    public CompletableFuture<Long> decrAsync(final String key, long num);

    //------------------------ hgetAsync ------------------------
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type);

    public CompletableFuture<String> hgetStringAsync(final String key, final String field);

    public CompletableFuture<Long> hgetLongAsync(final String key, final String field, long defValue);

    //------------------------ hsetAsync ------------------------
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final T value);

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Type type, final T value);

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> hsetStringAsync(final String key, final String field, final String value);

    public CompletableFuture<Void> hsetLongAsync(final String key, final String field, final long value);

    //------------------------ hsetnxAsync ------------------------
    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Convert convert, final T value);

    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Type type, final T value);

    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> hsetnxStringAsync(final String key, final String field, final String value);

    public CompletableFuture<Void> hsetnxLongAsync(final String key, final String field, final long value);

    //------------------------ hxxxAsync ------------------------
    public CompletableFuture<Integer> hdelAsync(final String key, String... fields);

    public CompletableFuture<List<String>> hkeysAsync(final String key);

    public CompletableFuture<Integer> hlenAsync(final String key);

    public CompletableFuture<Long> hincrAsync(final String key, String field);

    public CompletableFuture<Long> hincrAsync(final String key, String field, long num);

    public CompletableFuture<Long> hdecrAsync(final String key, String field);

    public CompletableFuture<Long> hdecrAsync(final String key, String field, long num);

    public CompletableFuture<Boolean> hexistsAsync(final String key, String field);

    //------------------------ hmxxAsync ------------------------
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values);

    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields);

    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit);

    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit, String pattern);

    //------------------------ listAsync ------------------------    
    public <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType);

    public <T> CompletableFuture<Map<String, List<T>>> lrangeAsync(final Type componentType, final String... keys);

    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Integer> lremAsync(final String key, final Type componentType, final T value);

    //---------- list-string ----------
    public CompletableFuture<Void> rpushStringAsync(final String key, final String value);

    public CompletableFuture<Integer> lremStringAsync(final String key, final String value);

    //---------- list-long ----------
    public CompletableFuture<Void> rpushLongAsync(final String key, final long value);

    public CompletableFuture<Integer> lremLongAsync(final String key, final long value);

    //------------------------ setAsync ------------------------
    public <T> CompletableFuture<Set<T>> smembersAsync(final String key, final Type componentType);

    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(final Type componentType, final String... keys);

    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Integer> sremAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<T> spopAsync(final String key, final Type componentType);

    public <T> CompletableFuture<Set<T>> spopAsync(final String key, final int count, final Type componentType);

    //---------- set-string ----------
    public CompletableFuture<Boolean> sismemberStringAsync(final String key, final String value);

    public CompletableFuture<Void> saddStringAsync(final String key, final String value);

    public CompletableFuture<Integer> sremStringAsync(final String key, final String value);

    public CompletableFuture<String> spopStringAsync(final String key);

    public CompletableFuture<Set<String>> spopStringAsync(final String key, final int count);

    //---------- set-long ----------
    public CompletableFuture<Boolean> sismemberLongAsync(final String key, final long value);

    public CompletableFuture<Void> saddLongAsync(final String key, final long value);

    public CompletableFuture<Integer> sremLongAsync(final String key, final long value);

    public CompletableFuture<Long> spopLongAsync(final String key);

    public CompletableFuture<Set<Long>> spopLongAsync(final String key, final int count);

    //------------------------ collectionAsync ------------------------
    @Deprecated
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(final String key, final Type componentType);

    @Deprecated
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(final boolean set, final Type componentType, final String... keys);

    @Deprecated
    public CompletableFuture<Integer> getCollectionSizeAsync(final String key);

    @Deprecated
    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(final String key, final int expireSeconds, final Type componentType);

    @Deprecated
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key);

    @Deprecated
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys);

    @Deprecated
    public CompletableFuture<Collection<String>> getexStringCollectionAsync(final String key, final int expireSeconds);

    @Deprecated
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key);

    @Deprecated
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys);

    @Deprecated
    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(final String key, final int expireSeconds);

    //------------------------ other-Async ------------------------
    public <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, final String... keys);

    public CompletableFuture<List<String>> queryKeysAsync();

    public CompletableFuture<List<String>> queryKeysStartsWithAsync(String startsWith);

    public CompletableFuture<List<String>> queryKeysEndsWithAsync(String endsWith);

    public CompletableFuture<Integer> getKeySizeAsync();

    public CompletableFuture<Map<String, String>> getStringMapAsync(final String... keys);

    public CompletableFuture<String[]> getStringArrayAsync(final String... keys);

    public CompletableFuture<Map<String, Long>> getLongMapAsync(final String... keys);

    public CompletableFuture<Long[]> getLongArrayAsync(final String... keys);

    //-------------------------- 过期方法 ----------------------------------
    @Deprecated
    default CompletableFuture<Void> refreshAsync(final String key, final int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated
    default CompletableFuture<Integer> hremoveAsync(final String key, String... fields) {
        return hdelAsync(key, fields);
    }

    @Deprecated
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Convert convert, final T value) {
        return setexAsync(key, expireSeconds, convert, value);
    }

    @Deprecated
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Type type, final T value) {
        return setexAsync(key, expireSeconds, type, value);
    }

    @Deprecated
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        return setexAsync(key, expireSeconds, convert, type, value);
    }

    @Deprecated
    default CompletableFuture<Void> setExpireSecondsAsync(final String key, final int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated
    default CompletableFuture<Integer> removeAsync(final String key) {
        return delAsync(key);
    }

    @Deprecated
    default int hremove(final String key, String... fields) {
        return hdel(key, fields);
    }

    @Deprecated
    default void refresh(final String key, final int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated
    default <T> void set(final int expireSeconds, final String key, final Convert convert, final T value) {
        setex(key, expireSeconds, convert, value);
    }

    @Deprecated
    default <T> void set(final int expireSeconds, final String key, final Type type, final T value) {
        setex(key, expireSeconds, type, value);
    }

    @Deprecated
    default <T> void set(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        setex(key, expireSeconds, convert, type, value);
    }

    @Deprecated
    default void setExpireSeconds(final String key, final int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated
    default int remove(final String key) {
        return del(key);
    }

    @Deprecated
    default void setString(final int expireSeconds, final String key, final String value) {
        setexString(key, expireSeconds, value);
    }

    @Deprecated
    default void setLong(final int expireSeconds, final String key, final long value) {
        setexLong(key, expireSeconds, value);
    }

    @Deprecated
    default void setBytes(final int expireSeconds, final String key, final byte[] value) {
        setexBytes(key, expireSeconds, value);
    }

    @Deprecated
    default <T> CompletableFuture<T> getAndRefreshAsync(final String key, final int expireSeconds, final Type type) {
        return getexAsync(key, expireSeconds, type);
    }

    @Deprecated
    default <T> T getAndRefresh(final String key, final int expireSeconds, final Type type) {
        return getex(key, expireSeconds, type);
    }

    @Deprecated
    default String getStringAndRefresh(final String key, final int expireSeconds) {
        return getexString(key, expireSeconds);
    }

    @Deprecated
    default long getLongAndRefresh(final String key, final int expireSeconds, long defValue) {
        return getexLong(key, expireSeconds, defValue);
    }

    @Deprecated
    default byte[] getBytesAndRefresh(final String key, final int expireSeconds) {
        return getexBytes(key, expireSeconds);
    }

    @Deprecated
    default CompletableFuture<Long> getLongAndRefreshAsync(final String key, final int expireSeconds, long defValue) {
        return getexLongAsync(key, expireSeconds, defValue);
    }

    @Deprecated
    default CompletableFuture<String> getStringAndRefreshAsync(final String key, final int expireSeconds) {
        return getexStringAsync(key, expireSeconds);
    }

    @Deprecated
    default CompletableFuture<byte[]> getBytesAndRefreshAsync(final String key, final int expireSeconds) {
        return getexBytesAsync(key, expireSeconds);
    }

    @Deprecated
    default int hsize(final String key) {
        return hlen(key);
    }

    @Deprecated
    default CompletableFuture<Integer> hsizeAsync(final String key) {
        return hlenAsync(key);
    }

    @Deprecated
    default <T> void appendSetItem(final String key, final Type componentType, final T value) {
        sadd(key, componentType, value);
    }

    @Deprecated
    default <T> int removeSetItem(final String key, final Type componentType, final T value) {
        return srem(key, componentType, value);
    }

    @Deprecated
    default <T> T spopSetItem(final String key, final Type componentType) {
        return CacheSource.this.spop(key, componentType);
    }

    @Deprecated
    default <T> Set<T> spopSetItem(final String key, final int count, final Type componentType) {
        return spop(key, count, componentType);
    }

    @Deprecated
    default <T> boolean existsSetItem(final String key, final Type componentType, final T value) {
        return sismember(key, componentType, value);
    }

    @Deprecated
    default <T> CompletableFuture<Boolean> existsSetItemAsync(final String key, final Type componentType, final T value) {
        return sismemberAsync(key, componentType, value);
    }

    @Deprecated
    default <T> CompletableFuture<Void> appendSetItemAsync(final String key, final Type componentType, final T value) {
        return saddAsync(key, componentType, value);
    }

    @Deprecated
    default <T> CompletableFuture<Integer> removeSetItemAsync(final String key, final Type componentType, final T value) {
        return sremAsync(key, componentType, value);
    }

    @Deprecated
    default <T> CompletableFuture<T> spopSetItemAsync(final String key, final Type componentType) {
        return spopAsync(key, componentType);
    }

    @Deprecated
    default <T> CompletableFuture<Set<T>> spopSetItemAsync(final String key, final int count, final Type componentType) {
        return spopAsync(key, count, componentType);
    }

    @Deprecated
    default boolean existsStringSetItem(final String key, final String value) {
        return sismemberString(key, value);
    }

    @Deprecated
    default void appendStringSetItem(final String key, final String value) {
        saddString(key, value);
    }

    @Deprecated
    default int removeStringSetItem(final String key, final String value) {
        return sremString(key, value);
    }

    @Deprecated
    default String spopStringSetItem(final String key) {
        return spopString(key);
    }

    @Deprecated
    default Set<String> spopStringSetItem(final String key, final int count) {
        return spopString(key, count);
    }

    @Deprecated
    default boolean existsLongSetItem(final String key, final long value) {
        return sismemberLong(key, value);
    }

    @Deprecated
    default void appendLongSetItem(final String key, final long value) {
        saddLong(key, value);
    }

    @Deprecated
    default int removeLongSetItem(final String key, final long value) {
        return sremLong(key, value);
    }

    @Deprecated
    default Long spopLongSetItem(final String key) {
        return spopLong(key);
    }

    @Deprecated
    default Set<Long> spopLongSetItem(final String key, final int count) {
        return spopLong(key, count);
    }

    @Deprecated
    default CompletableFuture<Boolean> existsStringSetItemAsync(final String key, final String value) {
        return sismemberStringAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Void> appendStringSetItemAsync(final String key, final String value) {
        return saddStringAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Integer> removeStringSetItemAsync(final String key, final String value) {
        return sremStringAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<String> spopStringSetItemAsync(final String key) {
        return spopStringAsync(key);
    }

    @Deprecated
    default CompletableFuture<Set<String>> spopStringSetItemAsync(final String key, final int count) {
        return spopStringAsync(key, count);
    }

    @Deprecated
    default CompletableFuture<Boolean> existsLongSetItemAsync(final String key, final long value) {
        return sismemberLongAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Void> appendLongSetItemAsync(final String key, final long value) {
        return saddLongAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Integer> removeLongSetItemAsync(final String key, final long value) {
        return sremLongAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Long> spopLongSetItemAsync(final String key) {
        return spopLongAsync(key);
    }

    @Deprecated
    default CompletableFuture<Set<Long>> spopLongSetItemAsync(final String key, final int count) {
        return spopLongAsync(key, count);
    }

    @Deprecated
    default <T> CompletableFuture<Void> appendListItemAsync(final String key, final Type componentType, final T value) {
        return rpushAsync(key, componentType, value);
    }

    @Deprecated
    default <T> CompletableFuture<Integer> removeListItemAsync(final String key, final Type componentType, final T value) {
        return lremAsync(key, componentType, value);
    }

    @Deprecated
    default CompletableFuture<Void> appendStringListItemAsync(final String key, final String value) {
        return rpushStringAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Integer> removeStringListItemAsync(final String key, final String value) {
        return lremStringAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Void> appendLongListItemAsync(final String key, final long value) {
        return rpushLongAsync(key, value);
    }

    @Deprecated
    default CompletableFuture<Integer> removeLongListItemAsync(final String key, final long value) {
        return lremLongAsync(key, value);
    }

    @Deprecated
    default <T> void appendListItem(final String key, final Type componentType, final T value) {
        rpush(key, componentType, value);
    }

    @Deprecated
    default <T> int removeListItem(final String key, final Type componentType, final T value) {
        return lrem(key, componentType, value);
    }

    @Deprecated
    default void appendStringListItem(final String key, final String value) {
        rpushString(key, value);
    }

    @Deprecated
    default int removeStringListItem(final String key, final String value) {
        return lremString(key, value);
    }

    @Deprecated
    default void appendLongListItem(final String key, final long value) {
        rpushLong(key, value);
    }

    @Deprecated
    default int removeLongListItem(final String key, final long value) {
        return lremLong(key, value);
    }
}
