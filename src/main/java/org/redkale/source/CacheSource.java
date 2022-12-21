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

    public boolean exists(final String key);

    public <T> T get(final String key, final Type type);

    public <T> T getex(final String key, final int expireSeconds, final Type type);

    //----------- hxxx --------------
    public int hdel(final String key, String... fields);

    public List<String> hkeys(final String key);

    public int hsize(final String key);

    public long hincr(final String key, String field);

    public long hincr(final String key, String field, long num);

    public long hdecr(final String key, String field);

    public long hdecr(final String key, String field, long num);

    public boolean hexists(final String key, String field);

    public <T> void hset(final String key, final String field, final Convert convert, final T value);

    public <T> void hset(final String key, final String field, final Type type, final T value);

    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value);

    public void hsetString(final String key, final String field, final String value);

    public void hsetLong(final String key, final String field, final long value);

    public <T> void hsetnx(final String key, final String field, final Convert convert, final T value);

    public <T> void hsetnx(final String key, final String field, final Type type, final T value);

    public <T> void hsetnx(final String key, final String field, final Convert convert, final Type type, final T value);

    public void hsetnxString(final String key, final String field, final String value);

    public void hsetnxLong(final String key, final String field, final long value);

    public void hmset(final String key, final Serializable... values);

    public <T> List<T> hmget(final String key, final Type type, final String... fields);

    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit);

    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit, String pattern);

    public <T> T hget(final String key, final String field, final Type type);

    public String hgetString(final String key, final String field);

    public long hgetLong(final String key, final String field, long defValue);
    //----------- hxxx --------------

    public <T> void set(final String key, final Convert convert, final T value);

    public <T> void set(final String key, final Type type, final T value);

    public <T> void set(final String key, final Convert convert, final Type type, final T value);

    public <T> void setnx(final String key, final Convert convert, final T value);

    public <T> void setnx(final String key, final Type type, final T value);

    public <T> void setnx(final String key, final Convert convert, final Type type, final T value);

    public <T> T getSet(final String key, final Type type, final T value);

    public <T> T getSet(final String key, final Convert convert, final Type type, final T value);

    public <T> void setex(final String key, final int expireSeconds, final Convert convert, final T value);

    public <T> void setex(final String key, final int expireSeconds, final Type type, final T value);

    public <T> void setex(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public void expire(final String key, final int seconds);

    public int del(final String key);

    public long incr(final String key);

    public long incr(final String key, long num);

    public long decr(final String key);

    public long decr(final String key, long num);

    public <T> Map<String, T> getMap(final Type componentType, final String... keys);

    public <T> Collection<T> getCollection(final String key, final Type componentType);

    public <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, final String... keys);

    public int getCollectionSize(final String key);

    public <T> Collection<T> getexCollection(final String key, final int expireSeconds, final Type componentType);

    public <T> void appendListItem(final String key, final Type componentType, final T value);

    public <T> int removeListItem(final String key, final Type componentType, final T value);

    public <T> boolean existsSetItem(final String key, final Type componentType, final T value);

    public <T> void appendSetItem(final String key, final Type componentType, final T value);

    public <T> int removeSetItem(final String key, final Type componentType, final T value);

    public <T> T spopSetItem(final String key, final Type componentType);

    public <T> Set<T> spopSetItem(final String key, final int count, final Type componentType);

    public byte[] getBytes(final String key);

    public byte[] getSetBytes(final String key, final byte[] value);

    public byte[] getexBytes(final String key, final int expireSeconds);

    public void setBytes(final String key, final byte[] value);

    public void setexBytes(final String key, final int expireSeconds, final byte[] value);

    public <T> void setBytes(final String key, final Convert convert, final Type type, final T value);

    public <T> void setexBytes(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public List<String> queryKeys();

    public List<String> queryKeysStartsWith(String startsWith);

    public List<String> queryKeysEndsWith(String endsWith);

    public int getKeySize();

    public String getString(final String key);

    public String getSetString(final String key, final String value);

    public String getexString(final String key, final int expireSeconds);

    public void setString(final String key, final String value);

    public void setnxString(final String key, final String value);

    public void setexString(final String key, final int expireSeconds, final String value);

    public Map<String, String> getStringMap(final String... keys);

    public String[] getStringArray(final String... keys);

    public Collection<String> getStringCollection(final String key);

    public Map<String, Collection<String>> getStringCollectionMap(final boolean set, final String... keys);

    public Collection<String> getexStringCollection(final String key, final int expireSeconds);

    public void appendStringListItem(final String key, final String value);

    public String spopStringSetItem(final String key);

    public Set<String> spopStringSetItem(final String key, final int count);

    public int removeStringListItem(final String key, final String value);

    public boolean existsStringSetItem(final String key, final String value);

    public void appendStringSetItem(final String key, final String value);

    public int removeStringSetItem(final String key, final String value);

    public long getLong(final String key, long defValue);

    public long getSetLong(final String key, long value, long defValue);

    public long getexLong(final String key, final int expireSeconds, long defValue);

    public void setLong(final String key, final long value);

    public void setnxLong(final String key, final long value);

    public void setexLong(final String key, final int expireSeconds, final long value);

    public Map<String, Long> getLongMap(final String... keys);

    public Long[] getLongArray(final String... keys);

    public Collection<Long> getLongCollection(final String key);

    public Map<String, Collection<Long>> getLongCollectionMap(final boolean set, final String... keys);

    public Collection<Long> getexLongCollection(final String key, final int expireSeconds);

    public void appendLongListItem(final String key, final long value);

    public Long spopLongSetItem(final String key);

    public Set<Long> spopLongSetItem(final String key, final int count);

    public int removeLongListItem(final String key, final long value);

    public boolean existsLongSetItem(final String key, final long value);

    public void appendLongSetItem(final String key, final long value);

    public int removeLongSetItem(final String key, final long value);

    //---------------------- CompletableFuture 异步版 ---------------------------------
    public CompletableFuture<Boolean> existsAsync(final String key);

    public <T> CompletableFuture<T> getAsync(final String key, final Type type);

    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type);

    public <T> CompletableFuture<Void> setAsync(final String key, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<Void> setAsync(final String key, final Convert convert, final Type type, final T value);

    public <T> CompletableFuture<Void> setnxAsync(final String key, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setnxAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<Void> setnxAsync(final String key, final Convert convert, final Type type, final T value);

    public <T> CompletableFuture<T> getSetAsync(final String key, final Type type, final T value);

    public <T> CompletableFuture<T> getSetAsync(final String key, final Convert convert, final Type type, final T value);

    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Convert convert, final T value);

    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Type type, final T value);

    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> expireAsync(final String key, final int seconds);

    public CompletableFuture<Integer> delAsync(final String key);

    public CompletableFuture<Long> incrAsync(final String key);

    public CompletableFuture<Long> incrAsync(final String key, long num);

    public CompletableFuture<Long> decrAsync(final String key);

    public CompletableFuture<Long> decrAsync(final String key, long num);

    //----------- hxxx --------------
    public CompletableFuture<Integer> hdelAsync(final String key, String... fields);

    public CompletableFuture<List<String>> hkeysAsync(final String key);

    public CompletableFuture<Integer> hsizeAsync(final String key);

    public CompletableFuture<Long> hincrAsync(final String key, String field);

    public CompletableFuture<Long> hincrAsync(final String key, String field, long num);

    public CompletableFuture<Long> hdecrAsync(final String key, String field);

    public CompletableFuture<Long> hdecrAsync(final String key, String field, long num);

    public CompletableFuture<Boolean> hexistsAsync(final String key, String field);

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final T value);

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Type type, final T value);

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> hsetStringAsync(final String key, final String field, final String value);

    public CompletableFuture<Void> hsetLongAsync(final String key, final String field, final long value);

    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Convert convert, final T value);

    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Type type, final T value);

    public <T> CompletableFuture<Void> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    public CompletableFuture<Void> hsetnxStringAsync(final String key, final String field, final String value);

    public CompletableFuture<Void> hsetnxLongAsync(final String key, final String field, final long value);

    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values);

    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields);

    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit);

    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit, String pattern);

    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type);

    public CompletableFuture<String> hgetStringAsync(final String key, final String field);

    public CompletableFuture<Long> hgetLongAsync(final String key, final String field, long defValue);
    //----------- hxxx --------------

    public <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, final String... keys);

    public <T> CompletableFuture<Collection<T>> getCollectionAsync(final String key, final Type componentType);

    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(final boolean set, final Type componentType, final String... keys);

    public CompletableFuture<Integer> getCollectionSizeAsync(final String key);

    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(final String key, final int expireSeconds, final Type componentType);

    public <T> CompletableFuture<T> spopSetItemAsync(final String key, final Type componentType);

    public <T> CompletableFuture<Set<T>> spopSetItemAsync(final String key, final int count, final Type componentType);

    public <T> CompletableFuture<Void> appendListItemAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Integer> removeListItemAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Boolean> existsSetItemAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Void> appendSetItemAsync(final String key, final Type componentType, final T value);

    public <T> CompletableFuture<Integer> removeSetItemAsync(final String key, final Type componentType, final T value);

    public CompletableFuture<byte[]> getBytesAsync(final String key);

    public CompletableFuture<byte[]> getSetBytesAsync(final String key, final byte[] value);

    public CompletableFuture<byte[]> getexBytesAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> setBytesAsync(final String key, final byte[] value);

    public CompletableFuture<Void> setexBytesAsync(final String key, final int expireSeconds, final byte[] value);

    public <T> CompletableFuture<Void> setBytesAsync(final String key, final Convert convert, final Type type, final T value);

    public <T> CompletableFuture<Void> setexBytesAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    public CompletableFuture<List<String>> queryKeysAsync();

    public CompletableFuture<List<String>> queryKeysStartsWithAsync(String startsWith);

    public CompletableFuture<List<String>> queryKeysEndsWithAsync(String endsWith);

    public CompletableFuture<Integer> getKeySizeAsync();

    public CompletableFuture<String> getStringAsync(final String key);

    public CompletableFuture<String> getSetStringAsync(final String key, final String value);

    public CompletableFuture<String> getexStringAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> setStringAsync(final String key, final String value);

    public CompletableFuture<Void> setnxStringAsync(final String key, final String value);

    public CompletableFuture<Void> setexStringAsync(final String key, final int expireSeconds, final String value);

    public CompletableFuture<Map<String, String>> getStringMapAsync(final String... keys);

    public CompletableFuture<String[]> getStringArrayAsync(final String... keys);

    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key);

    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys);

    public CompletableFuture<Collection<String>> getexStringCollectionAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> appendStringListItemAsync(final String key, final String value);

    public CompletableFuture<String> spopStringSetItemAsync(final String key);

    public CompletableFuture<Set<String>> spopStringSetItemAsync(final String key, final int count);

    public CompletableFuture<Integer> removeStringListItemAsync(final String key, final String value);

    public CompletableFuture<Boolean> existsStringSetItemAsync(final String key, final String value);

    public CompletableFuture<Void> appendStringSetItemAsync(final String key, final String value);

    public CompletableFuture<Integer> removeStringSetItemAsync(final String key, final String value);

    public CompletableFuture<Long> getLongAsync(final String key, long defValue);

    public CompletableFuture<Long> getSetLongAsync(final String key, long value, long defValue);

    public CompletableFuture<Long> getexLongAsync(final String key, final int expireSeconds, long defValue);

    public CompletableFuture<Void> setLongAsync(final String key, long value);

    public CompletableFuture<Void> setnxLongAsync(final String key, long value);

    public CompletableFuture<Void> setexLongAsync(final String key, final int expireSeconds, final long value);

    public CompletableFuture<Map<String, Long>> getLongMapAsync(final String... keys);

    public CompletableFuture<Long[]> getLongArrayAsync(final String... keys);

    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key);

    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys);

    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> appendLongListItemAsync(final String key, final long value);

    public CompletableFuture<Long> spopLongSetItemAsync(final String key);

    public CompletableFuture<Set<Long>> spopLongSetItemAsync(final String key, final int count);

    public CompletableFuture<Integer> removeLongListItemAsync(final String key, final long value);

    public CompletableFuture<Boolean> existsLongSetItemAsync(final String key, final long value);

    public CompletableFuture<Void> appendLongSetItemAsync(final String key, final long value);

    public CompletableFuture<Integer> removeLongSetItemAsync(final String key, final long value);

    default CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(isOpen());
    }

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
}
