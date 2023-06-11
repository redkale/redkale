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
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.annotation.Component;
import org.redkale.convert.Convert;
import org.redkale.util.*;

/**
 * Redkale中缓存数据源的核心类。 主要供业务开发者使用， 技术开发者提供CacheSource的实现。<br>
 * CacheSource提供三种数据类型操作: String、Long、byte[]和泛型指定的数据类型。<br>
 * String统一用setString、getString等系列方法。<br>
 * Long统一用setLong、getLong、incr等系列方法。<br>
 * 其他则供自定义数据类型使用。
 *
 * param V value的类型 移除 @2.4.0
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Component
public interface CacheSource extends Resourcable {

    public String getType();

    default boolean isOpen() {
        return true;
    }

    //------------------------ get ------------------------    
    public <T> T get(final String key, final Type type);

    default String getString(final String key) {
        return get(key, String.class);
    }

    default long getLong(final String key, long defValue) {
        Long val = get(key, Long.class);
        return val == null ? defValue : val;
    }

    //------------------------ mget ------------------------    
    public <T> Map<String, T> mget(final Type componentType, final String... keys);

    default Map<String, String> mgetString(final String... keys) {
        return mget(String.class, keys);
    }

    default Map<String, Long> mgetLong(final String... keys) {
        return mget(Long.class, keys);
    }

    default <T> T[] mgets(final Type componentType, final String... keys) {
        T[] rs = (T[]) Creator.newArray(TypeToken.typeToClass(componentType), keys.length);
        Map<String, T> map = mget(componentType, keys);
        for (int i = 0; i < keys.length; i++) {
            rs[i] = map.get(keys[i]);
        }
        return rs;
    }

    default String[] mgetsString(final String... keys) {
        String[] rs = new String[keys.length];
        Map<String, String> map = mgetString(keys);
        for (int i = 0; i < keys.length; i++) {
            rs[i] = map.get(keys[i]);
        }
        return rs;
    }

    default Long[] mgetsLong(final String... keys) {
        Long[] rs = new Long[keys.length];
        Map<String, Long> map = mgetLong(keys);
        for (int i = 0; i < keys.length; i++) {
            rs[i] = map.get(keys[i]);
        }
        return rs;
    }

    //------------------------ getex ------------------------
    public <T> T getex(final String key, final int expireSeconds, final Type type);

    default String getexString(final String key, final int expireSeconds) {
        return getex(key, expireSeconds, String.class);
    }

    default long getexLong(final String key, final int expireSeconds, long defValue) {
        Long val = getex(key, expireSeconds, Long.class);
        return val == null ? defValue : val;
    }

    //------------------------ getset ------------------------
    public <T> T getSet(final String key, final Convert convert, final Type type, final T value);

    default <T> T getSet(final String key, final Type type, final T value) {
        return getSet(key, (Convert) null, type, value);
    }

    default String getSetString(final String key, final String value) {
        return getSet(key, String.class, value);
    }

    default long getSetLong(final String key, long value, long defValue) {
        Long val = getSet(key, Long.class, value);
        return val == null ? defValue : val;
    }

    //------------------------ set ------------------------
    //MSET key value [key value ...]
    public void mset(final Serializable... keyVals);

    public void mset(final Map map);

    public <T> void set(final String key, final Convert convert, final Type type, final T value);

    default <T> void set(final String key, final Type type, final T value) {
        set(key, (Convert) null, type, value);
    }

    default void setString(final String key, final String value) {
        set(key, String.class, value);
    }

    default void setLong(final String key, final long value) {
        set(key, Long.class, value);
    }

    //------------------------ setnx ------------------------
    public <T> boolean setnx(final String key, final Convert convert, final Type type, final T value);

    default <T> boolean setnx(final String key, final Type type, final T value) {
        return setnx(key, (Convert) null, type, value);
    }

    default boolean setnxString(final String key, final String value) {
        return setnx(key, String.class, value);
    }

    default boolean setnxLong(final String key, final long value) {
        return setnx(key, Long.class, value);
    }

    //------------------------ setnxex ------------------------
    public <T> boolean setnxex(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    default <T> boolean setnxex(final String key, final int expireSeconds, final Type type, final T value) {
        return setnxex(key, expireSeconds, (Convert) null, type, value);
    }

    default boolean setnxexString(final String key, final int expireSeconds, final String value) {
        return setnxex(key, expireSeconds, String.class, value);
    }

    default boolean setnxexLong(final String key, final int expireSeconds, final long value) {
        return setnxex(key, expireSeconds, Long.class, value);
    }

    //------------------------ setex ------------------------
    public <T> void setex(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    default <T> void setex(final String key, final int expireSeconds, final Type type, final T value) {
        setex(key, expireSeconds, (Convert) null, type, value);
    }

    default void setexString(final String key, final int expireSeconds, final String value) {
        setex(key, expireSeconds, String.class, value);
    }

    default void setexLong(final String key, final int expireSeconds, final long value) {
        setex(key, expireSeconds, Long.class, value);
    }

    //------------------------ xxxx ------------------------
    public boolean exists(final String key);

    public void expire(final String key, final int seconds);

    public boolean persist(final String key);

    public boolean rename(String oldKey, String newKey);

    public boolean renamenx(String oldKey, String newKey);

    public long del(final String... keys);

    public long incr(final String key);

    public long incrby(final String key, long num);

    public long decr(final String key);

    public long decrby(final String key, long num);

    public double incrbyFloat(final String key, double num);

    //------------------------ 键 Keys ------------------------    
    public List<String> keys(String pattern);

    default List<String> keys() {
        return keys(null);
    }

    default List<String> keysStartsWith(String startsWith) {
        return keys(startsWith + "*");
    }

    public List<String> scan(AtomicLong cursor, int limit, String pattern);

    default List<String> scan(AtomicLong cursor, int limit) {
        return scan(cursor, limit, null);
    }

    //------------------------ 服务器 Server  ------------------------   
    public long dbsize();

    public void flushdb();

    public void flushall();

    //------------------------ 哈希表 Hash ------------------------
    public long hdel(final String key, String... fields);

    public List<String> hkeys(final String key);

    public long hlen(final String key);

    public long hincr(final String key, String field);

    public long hincrby(final String key, String field, long num);

    public double hincrbyFloat(final String key, String field, double num);

    public long hdecr(final String key, String field);

    public long hdecrby(final String key, String field, long num);

    public boolean hexists(final String key, String field);

    public void hmset(final String key, final Serializable... values);

    public void hmset(final String key, final Map map);

    public <T> List<T> hmget(final String key, final Type type, final String... fields);

    default List<String> hmgetString(final String key, final String... fields) {
        return hmget(key, String.class, fields);
    }

    default List<Long> hmgetLong(final String key, final String... fields) {
        return hmget(key, Long.class, fields);
    }

    public <T> Map<String, T> hscan(final String key, final Type type, AtomicLong cursor, int limit, String pattern);

    default <T> Map<String, T> hscan(final String key, final Type type, AtomicLong cursor, int limit) {
        return hscan(key, type, cursor, limit, null);
    }

    public <T> T hget(final String key, final String field, final Type type);

    default String hgetString(final String key, final String field) {
        return hget(key, field, String.class);
    }

    default long hgetLong(final String key, final String field, long defValue) {
        Long val = hget(key, field, Long.class);
        return val == null ? defValue : val;
    }

    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value);

    default <T> void hset(final String key, final String field, final Type type, final T value) {
        hset(key, field, (Convert) null, type, value);
    }

    default void hsetString(final String key, final String field, final String value) {
        hset(key, field, String.class, value);
    }

    default void hsetLong(final String key, final String field, final long value) {
        hset(key, field, Long.class, value);
    }

    public <T> boolean hsetnx(final String key, final String field, final Convert convert, final Type type, final T value);

    default <T> boolean hsetnx(final String key, final String field, final Type type, final T value) {
        return hsetnx(key, field, (Convert) null, type, value);
    }

    default boolean hsetnxString(final String key, final String field, final String value) {
        return hsetnx(key, field, String.class, value);
    }

    default boolean hsetnxLong(final String key, final String field, final long value) {
        return hsetnx(key, field, Long.class, value);
    }

    public <T> Map<String, T> hgetall(final String key, final Type type);

    default Map<String, String> hgetallString(final String key) {
        return hgetall(key, String.class);
    }

    default Map<String, Long> hgetallLong(final String key) {
        return hgetall(key, Long.class);
    }

    public <T> List<T> hvals(final String key, final Type type);

    default List<String> hvalsString(final String key) {
        return hvals(key, String.class);
    }

    default List<Long> hvalsLong(final String key) {
        return hvals(key, Long.class);
    }

    //------------------------ 列表 List ------------------------
    public long llen(final String key);

    public void ltrim(final String key, int start, int stop);

    public <T> Map<String, List<T>> lrange(final Type componentType, final String... keys);

    default Map<String, List<String>> lrangeString(final String... keys) {
        return lrange(String.class, keys);
    }

    default Map<String, List<Long>> lrangeLong(final String... keys) {
        return lrange(Long.class, keys);
    }

    public <T> List<T> lrange(final String key, final Type componentType, int start, int stop);

    default List<String> lrangeString(final String key, int start, int stop) {
        return lrange(key, String.class, start, stop);
    }

    default List<Long> lrangeLong(final String key, int start, int stop) {
        return lrange(key, Long.class, start, stop);
    }

    default <T> List<T> lrange(final String key, final Type componentType) {
        return lrange(key, componentType, 0, -1);
    }

    default List<String> lrangeString(final String key) {
        return lrange(key, String.class, 0, -1);
    }

    default List<Long> lrangeLong(final String key) {
        return lrange(key, Long.class, 0, -1);
    }

    public <T> T lpop(final String key, final Type componentType);

    default String lpopString(final String key) {
        return lpop(key, String.class);
    }

    default Long lpopLong(final String key) {
        return lpop(key, Long.class);
    }

    public <T> void lpush(final String key, final Type componentType, T... values);

    default void lpushString(final String key, String... values) {
        lpush(key, String.class, values);
    }

    default void lpushLong(final String key, Long... values) {
        lpush(key, Long.class, values);
    }

    public <T> void lpushx(final String key, final Type componentType, T... values);

    default void lpushxString(final String key, String... values) {
        lpushx(key, String.class, values);
    }

    default void lpushxLong(final String key, Long... values) {
        lpushx(key, Long.class, values);
    }

    public <T> void rpushx(final String key, final Type componentType, T... values);

    default void rpushxString(final String key, String... values) {
        rpushx(key, String.class, values);
    }

    default void rpushxLong(final String key, Long... values) {
        rpushx(key, Long.class, values);
    }

    public <T> T rpop(final String key, final Type componentType);

    default String rpopString(final String key) {
        return rpop(key, String.class);
    }

    default Long rpopLong(final String key) {
        return rpop(key, Long.class);
    }

    public <T> T rpoplpush(final String key, final String key2, final Type componentType);

    default String rpoplpushString(final String key, final String key2) {
        return rpoplpush(key, key2, String.class);
    }

    default Long rpoplpushLong(final String key, final String key2) {
        return rpoplpush(key, key2, Long.class);
    }

    public <T> int lrem(final String key, final Type componentType, final T value);

    default int lremString(final String key, final String value) {
        return lrem(key, String.class, value);
    }

    default int lremLong(final String key, final long value) {
        return lrem(key, Long.class, value);
    }

    public <T> void rpush(final String key, final Type componentType, final T... values);

    default void rpushString(final String key, final String... values) {
        rpush(key, String.class, values);
    }

    default void rpushLong(final String key, final Long... values) {
        rpush(key, Long.class, values);
    }

    //------------------------ 集合 Set ------------------------   
    public <T> void sadd(final String key, final Type componentType, final T... values);

    default void saddString(final String key, final String... values) {
        sadd(key, String.class, values);
    }

    default void saddLong(final String key, final Long... values) {
        sadd(key, Long.class, values);
    }

    public <T> Set<T> sdiff(final String key, final Type componentType, final String... key2s);

    default Set<String> sdiffString(final String key, final String... key2s) {
        return sdiff(key, String.class, key2s);
    }

    default Set<Long> sdiffLong(final String key, final String... key2s) {
        return sdiff(key, Long.class, key2s);
    }

    public long sdiffstore(final String key, final String srcKey, final String... srcKey2s);

    public long scard(final String key);

    public <T> Set<T> smembers(final String key, final Type componentType);

    default Set<String> smembersString(final String key) {
        return smembers(key, String.class);
    }

    default Set<Long> smembersLong(final String key) {
        return smembers(key, Long.class);
    }

    public <T> Map<String, Set<T>> smembers(final Type componentType, final String... keys);

    default Map<String, Set<String>> smembersString(final String... keys) {
        return smembers(String.class, keys);
    }

    default Map<String, Set<Long>> smembersLong(final String... keys) {
        return smembers(Long.class, keys);
    }

    public <T> boolean sismember(final String key, final Type componentType, final T value);

    default boolean sismemberString(final String key, final String value) {
        return sismember(key, String.class, value);
    }

    default boolean sismemberLong(final String key, final long value) {
        return sismember(key, Long.class, value);
    }

    public <T> long srem(final String key, final Type componentType, final T... values);

    default long sremString(final String key, final String... values) {
        return srem(key, String.class, values);
    }

    default long sremLong(final String key, final Long... values) {
        return srem(key, Long.class, values);
    }

    public <T> T spop(final String key, final Type componentType);

    default String spopString(final String key) {
        return spop(key, String.class);
    }

    default Long spopLong(final String key) {
        return spop(key, Long.class);
    }

    public <T> Set<T> spop(final String key, final int count, final Type componentType);

    default Set<String> spopString(final String key, final int count) {
        return spop(key, count, String.class);
    }

    default Set<Long> spopLong(final String key, final int count) {
        return spop(key, count, Long.class);
    }

    public <T> Set<T> sscan(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern);

    default Set<String> sscanString(final String key, AtomicLong cursor, int limit, String pattern) {
        return sscan(key, String.class, cursor, limit, pattern);
    }

    default Set<Long> sscanLong(final String key, AtomicLong cursor, int limit, String pattern) {
        return sscan(key, Long.class, cursor, limit, pattern);
    }

    default <T> Set<T> sscan(final String key, final Type componentType, AtomicLong cursor, int limit) {
        return sscan(key, componentType, cursor, limit, null);
    }

    default Set<String> sscanString(final String key, AtomicLong cursor, int limit) {
        return sscan(key, String.class, cursor, limit, null);
    }

    default Set<Long> sscanLong(final String key, AtomicLong cursor, int limit) {
        return sscan(key, Long.class, cursor, limit, null);
    }

    //---------------------- CompletableFuture 异步版 ---------------------------------
    default CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(isOpen());
    }

    //------------------------ getAsync ------------------------  
    public <T> CompletableFuture<T> getAsync(final String key, final Type type);

    default CompletableFuture<String> getStringAsync(final String key) {
        return getAsync(key, String.class);
    }

    default CompletableFuture<Long> getLongAsync(final String key, long defValue) {
        return getAsync(key, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    //------------------------ mgetAsync ------------------------    
    public <T> CompletableFuture<Map<String, T>> mgetAsync(final Type componentType, final String... keys);

    default CompletableFuture<Map<String, String>> mgetStringAsync(final String... keys) {
        return mgetAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, Long>> mgetLongAsync(final String... keys) {
        return mgetAsync(Long.class, keys);
    }

    default <T> CompletableFuture<T[]> mgetsAsync(final Type componentType, final String... keys) {
        return mgetAsync(componentType, keys).thenApply(map -> {
            T[] rs = (T[]) Creator.newArray(TypeToken.typeToClass(componentType), keys.length);
            for (int i = 0; i < keys.length; i++) {
                rs[i] = (T) map.get(keys[i]);
            }
            return rs;
        });
    }

    default CompletableFuture<String[]> mgetsStringAsync(final String... keys) {
        return mgetStringAsync(keys).thenApply(map -> {
            String[] rs = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                rs[i] = map.get(keys[i]);
            }
            return rs;
        });
    }

    default CompletableFuture<Long[]> mgetsLongAsync(final String... keys) {
        return mgetLongAsync(keys).thenApply(map -> {
            Long[] rs = new Long[keys.length];
            for (int i = 0; i < keys.length; i++) {
                rs[i] = map.get(keys[i]);
            }
            return rs;
        });
    }

    //------------------------ getexAsync ------------------------
    public <T> CompletableFuture<T> getexAsync(final String key, final int expireSeconds, final Type type);

    default CompletableFuture<String> getexStringAsync(final String key, final int expireSeconds) {
        return getexAsync(key, expireSeconds, String.class);
    }

    default CompletableFuture<Long> getexLongAsync(final String key, final int expireSeconds, long defValue) {
        return getexAsync(key, expireSeconds, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    //------------------------ getsetAsync ------------------------
    public <T> CompletableFuture<T> getSetAsync(final String key, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<T> getSetAsync(final String key, final Type type, final T value) {
        return getSetAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<String> getSetStringAsync(final String key, final String value) {
        return getSetAsync(key, String.class, value);
    }

    default CompletableFuture<Long> getSetLongAsync(final String key, long value, long defValue) {
        return getSetAsync(key, Long.class, value).thenApply(v -> v == null ? defValue : (Long) v);
    }

    //------------------------ setAsync ------------------------
    //MSET key value [key value ...]
    public CompletableFuture<Void> msetAsync(final Serializable... keyVals);

    public CompletableFuture<Void> msetAsync(final Map map);

    public <T> CompletableFuture<Void> setAsync(final String key, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Void> setAsync(final String key, final Type type, final T value) {
        return setAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<Void> setStringAsync(final String key, final String value) {
        return setAsync(key, String.class, value);
    }

    default CompletableFuture<Void> setLongAsync(final String key, long value) {
        return setAsync(key, Long.class, value);
    }

    //------------------------ setnxAsync ------------------------
    public <T> CompletableFuture<Boolean> setnxAsync(final String key, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Boolean> setnxAsync(final String key, final Type type, final T value) {
        return setnxAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> setnxStringAsync(final String key, final String value) {
        return setnxAsync(key, String.class, value);
    }

    default CompletableFuture<Boolean> setnxLongAsync(final String key, long value) {
        return setnxAsync(key, Long.class, value);
    }

    //------------------------ setnxexAsync ------------------------
    public <T> CompletableFuture<Boolean> setnxexAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Boolean> setnxexAsync(final String key, final int expireSeconds, final Type type, final T value) {
        return setnxexAsync(key, expireSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> setnxexStringAsync(final String key, final int expireSeconds, final String value) {
        return setnxexAsync(key, expireSeconds, String.class, value);
    }

    default CompletableFuture<Boolean> setnxexLongAsync(final String key, final int expireSeconds, final long value) {
        return setnxexAsync(key, expireSeconds, Long.class, value);
    }

    //------------------------ setexAsync ------------------------
    public <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Void> setexAsync(final String key, final int expireSeconds, final Type type, final T value) {
        return setexAsync(key, expireSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Void> setexStringAsync(final String key, final int expireSeconds, final String value) {
        return setexAsync(key, expireSeconds, String.class, value);
    }

    default CompletableFuture<Void> setexLongAsync(final String key, final int expireSeconds, final long value) {
        return setexAsync(key, expireSeconds, Long.class, value);
    }

    //------------------------ xxxxAsync ------------------------
    public CompletableFuture<Boolean> existsAsync(final String key);

    public CompletableFuture<Void> expireAsync(final String key, final int seconds);

    public CompletableFuture<Boolean> persistAsync(final String key);

    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey);

    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey);

    public CompletableFuture<Long> delAsync(final String... keys);

    public CompletableFuture<Long> incrAsync(final String key);

    public CompletableFuture<Long> incrbyAsync(final String key, long num);

    public CompletableFuture<Long> decrAsync(final String key);

    public CompletableFuture<Long> decrbyAsync(final String key, long num);

    public CompletableFuture<Double> incrbyFloatAsync(final String key, double num);

    //------------------------ 键 Keys ------------------------    
    public CompletableFuture<List<String>> keysAsync(String pattern);

    default CompletableFuture<List<String>> keysAsync() {
        return keysAsync(null);
    }

    default CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        return keysAsync(startsWith + "*");
    }

    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern);

    default CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit) {
        return scanAsync(cursor, limit, null);
    }

    //------------------------ 服务器 Server  ------------------------   
    public CompletableFuture<Long> dbsizeAsync();

    public CompletableFuture<Void> flushdbAsync();

    public CompletableFuture<Void> flushallAsync();

    //------------------------ 哈希表 Hash ------------------------
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type);

    default CompletableFuture<String> hgetStringAsync(final String key, final String field) {
        return hgetAsync(key, field, String.class);
    }

    default CompletableFuture<Long> hgetLongAsync(final String key, final String field, long defValue) {
        return hgetAsync(key, field, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Type type, final T value) {
        return hsetAsync(key, field, (Convert) null, type, value);
    }

    default CompletableFuture<Void> hsetStringAsync(final String key, final String field, final String value) {
        return hsetAsync(key, field, String.class, value);
    }

    default CompletableFuture<Void> hsetLongAsync(final String key, final String field, final long value) {
        return hsetAsync(key, field, Long.class, value);
    }

    public <T> CompletableFuture<Boolean> hsetnxAsync(final String key, final String field, final Convert convert, final Type type, final T value);

    default <T> CompletableFuture<Boolean> hsetnxAsync(final String key, final String field, final Type type, final T value) {
        return hsetnxAsync(key, field, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> hsetnxStringAsync(final String key, final String field, final String value) {
        return hsetnxAsync(key, field, String.class, value);
    }

    default CompletableFuture<Boolean> hsetnxLongAsync(final String key, final String field, final long value) {
        return hsetnxAsync(key, field, Long.class, value);
    }

    public <T> CompletableFuture<Map<String, T>> hgetallAsync(final String key, final Type type);

    default CompletableFuture<Map<String, String>> hgetallStringAsync(final String key) {
        return hgetallAsync(key, String.class);
    }

    default CompletableFuture<Map<String, Long>> hgetallLongAsync(final String key) {
        return hgetallAsync(key, Long.class);
    }

    public <T> CompletableFuture<List<T>> hvalsAsync(final String key, final Type type);

    default CompletableFuture<List<String>> hvalsStringAsync(final String key) {
        return hvalsAsync(key, String.class);
    }

    default CompletableFuture<List<Long>> hvalsLongAsync(final String key) {
        return hvalsAsync(key, Long.class);
    }

    public CompletableFuture<Long> hdelAsync(final String key, String... fields);

    public CompletableFuture<List<String>> hkeysAsync(final String key);

    public CompletableFuture<Long> hlenAsync(final String key);

    public CompletableFuture<Long> hincrAsync(final String key, String field);

    public CompletableFuture<Long> hincrbyAsync(final String key, String field, long num);

    public CompletableFuture<Double> hincrbyFloatAsync(final String key, String field, double num);

    public CompletableFuture<Long> hdecrAsync(final String key, String field);

    public CompletableFuture<Long> hdecrbyAsync(final String key, String field, long num);

    public CompletableFuture<Boolean> hexistsAsync(final String key, String field);

    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values);

    public CompletableFuture<Void> hmsetAsync(final String key, final Map map);

    public <T> CompletableFuture<List<T>> hmgetAsync(final String key, final Type type, final String... fields);

    default CompletableFuture<List<String>> hmgetStringAsync(final String key, final String... fields) {
        return hmgetAsync(key, String.class, fields);
    }

    default CompletableFuture<List<Long>> hmgetLongAsync(final String key, final String... fields) {
        return hmgetAsync(key, Long.class, fields);
    }

    public <T> CompletableFuture<Map<String, T>> hscanAsync(final String key, final Type type, AtomicLong cursor, int limit, String pattern);

    default <T> CompletableFuture<Map<String, T>> hscanAsync(final String key, final Type type, AtomicLong cursor, int limit) {
        return hscanAsync(key, type, cursor, limit, null);
    }

    //------------------------ 列表 List ------------------------
    public CompletableFuture<Long> llenAsync(final String key);

    public CompletableFuture<Void> ltrimAsync(final String key, int start, int stop);

    public <T> CompletableFuture<Map<String, List<T>>> lrangeAsync(final Type componentType, final String... keys);

    default CompletableFuture<Map<String, List<String>>> lrangeStringAsync(final String... keys) {
        return lrangeAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, List<Long>>> lrangeLongAsync(final String... keys) {
        return lrangeAsync(Long.class, keys);
    }

    public <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType, int start, int stop);

    default CompletableFuture<List<String>> lrangeStringAsync(final String key, int start, int stop) {
        return lrangeAsync(key, String.class, start, stop);
    }

    default CompletableFuture<List<Long>> lrangeLongAsync(final String key, int start, int stop) {
        return lrangeAsync(key, Long.class, start, stop);
    }

    default <T> CompletableFuture<List<T>> lrangeAsync(final String key, final Type componentType) {
        return lrangeAsync(key, componentType, 0, -1);
    }

    default CompletableFuture<List<String>> lrangeStringAsync(final String key) {
        return lrangeAsync(key, String.class, 0, -1);
    }

    default CompletableFuture<List<Long>> lrangeLongAsync(final String key) {
        return lrangeAsync(key, Long.class, 0, -1);
    }

    public <T> CompletableFuture<T> lpopAsync(final String key, final Type componentType);

    default CompletableFuture<String> lpopStringAsync(final String key) {
        return lpopAsync(key, String.class);
    }

    default CompletableFuture<Long> lpopLongAsync(final String key) {
        return lpopAsync(key, Long.class);
    }

    public <T> CompletableFuture<Void> lpushAsync(final String key, final Type componentType, T... values);

    default CompletableFuture<Void> lpushStringAsync(final String key, String... values) {
        return lpushAsync(key, String.class, values);
    }

    default CompletableFuture<Void> lpushLongAsync(final String key, Long... values) {
        return lpushAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Void> lpushxAsync(final String key, final Type componentType, T... values);

    default CompletableFuture<Void> lpushxStringAsync(final String key, String... values) {
        return lpushxAsync(key, String.class, values);
    }

    default CompletableFuture<Void> lpushxLongAsync(final String key, Long... values) {
        return lpushxAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Void> rpushxAsync(final String key, final Type componentType, T... values);

    default CompletableFuture<Void> rpushxStringAsync(final String key, String... values) {
        return rpushxAsync(key, String.class, values);
    }

    default CompletableFuture<Void> rpushxLongAsync(final String key, Long... values) {
        return rpushxAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<T> rpopAsync(final String key, final Type componentType);

    default CompletableFuture<String> rpopStringAsync(final String key) {
        return rpopAsync(key, String.class);
    }

    default CompletableFuture<Long> rpopLongAsync(final String key) {
        return rpopAsync(key, Long.class);
    }

    public <T> CompletableFuture<T> rpoplpushAsync(final String key, final String key2, final Type componentType);

    default CompletableFuture<String> rpoplpushStringAsync(final String key, final String key2) {
        return rpoplpushAsync(key, key2, String.class);
    }

    default CompletableFuture<Long> rpoplpushLongAsync(final String key, final String key2) {
        return rpoplpushAsync(key, key2, Long.class);
    }

    public <T> CompletableFuture<Integer> lremAsync(final String key, final Type componentType, final T value);

    default CompletableFuture<Integer> lremStringAsync(final String key, final String value) {
        return lremAsync(key, String.class, value);
    }

    default CompletableFuture<Integer> lremLongAsync(final String key, final long value) {
        return lremAsync(key, Long.class, value);
    }

    public <T> CompletableFuture<Void> rpushAsync(final String key, final Type componentType, final T... values);

    default CompletableFuture<Void> rpushStringAsync(final String key, final String... values) {
        return rpushAsync(key, String.class, values);
    }

    default CompletableFuture<Void> rpushLongAsync(final String key, final Long... values) {
        return rpushAsync(key, Long.class, values);
    }

    //------------------------ 集合 Set ------------------------
    public <T> CompletableFuture<Void> saddAsync(final String key, final Type componentType, final T... values);

    default CompletableFuture<Void> saddStringAsync(final String key, final String... values) {
        return saddAsync(key, String.class, values);
    }

    default CompletableFuture<Void> saddLongAsync(final String key, final Long... values) {
        return saddAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Set<T>> sdiffAsync(final String key, final Type componentType, final String... key2s);

    default CompletableFuture<Set<String>> sdiffStringAsync(final String key, final String... key2s) {
        return sdiffAsync(key, String.class, key2s);
    }

    default CompletableFuture<Set<Long>> sdiffLongAsync(final String key, final String... key2s) {
        return sdiffAsync(key, Long.class, key2s);
    }

    public CompletableFuture<Long> sdiffstoreAsync(final String key, final String srcKey, final String... srcKey2s);

    public CompletableFuture<Long> scardAsync(final String key);

    public <T> CompletableFuture<Set<T>> smembersAsync(final String key, final Type componentType);

    default CompletableFuture<Set<String>> smembersStringAsync(final String key) {
        return smembersAsync(key, String.class);
    }

    default CompletableFuture<Set<Long>> smembersLongAsync(final String key) {
        return smembersAsync(key, Long.class);
    }

    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(final Type componentType, final String... keys);

    default CompletableFuture<Map<String, Set<String>>> smembersStringAsync(final String... keys) {
        return smembersAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, Set<Long>>> smembersLongAsync(final String... keys) {
        return smembersAsync(Long.class, keys);
    }

    public <T> CompletableFuture<Boolean> sismemberAsync(final String key, final Type componentType, final T value);

    default CompletableFuture<Boolean> sismemberStringAsync(final String key, final String value) {
        return sismemberAsync(key, String.class, value);
    }

    default CompletableFuture<Boolean> sismemberLongAsync(final String key, final long value) {
        return sismemberAsync(key, Long.class, value);
    }

    public <T> CompletableFuture<Long> sremAsync(final String key, final Type componentType, final T... values);

    default CompletableFuture<Long> sremStringAsync(final String key, final String... values) {
        return sremAsync(key, String.class, values);
    }

    default CompletableFuture<Long> sremLongAsync(final String key, final Long... values) {
        return sremAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<T> spopAsync(final String key, final Type componentType);

    default CompletableFuture<String> spopStringAsync(final String key) {
        return spopAsync(key, String.class);
    }

    default CompletableFuture<Long> spopLongAsync(final String key) {
        return spopAsync(key, Long.class);
    }

    public <T> CompletableFuture<Set<T>> spopAsync(final String key, final int count, final Type componentType);

    default CompletableFuture<Set<String>> spopStringAsync(final String key, final int count) {
        return spopAsync(key, count, String.class);
    }

    default CompletableFuture<Set<Long>> spopLongAsync(final String key, final int count) {
        return spopAsync(key, count, Long.class);
    }

    public <T> CompletableFuture<Set<T>> sscanAsync(final String key, final Type componentType, AtomicLong cursor, int limit, String pattern);

    default CompletableFuture<Set<String>> sscanStringAsync(final String key, AtomicLong cursor, int limit, String pattern) {
        return sscanAsync(key, String.class, cursor, limit, pattern);
    }

    default CompletableFuture<Set<Long>> sscanLongAsync(final String key, AtomicLong cursor, int limit, String pattern) {
        return sscanAsync(key, Long.class, cursor, limit, pattern);
    }

    default <T> CompletableFuture<Set<T>> sscanAsync(final String key, final Type componentType, AtomicLong cursor, int limit) {
        return sscanAsync(key, componentType, cursor, limit, null);
    }

    default CompletableFuture<Set<String>> sscanStringAsync(final String key, AtomicLong cursor, int limit) {
        return sscanAsync(key, String.class, cursor, limit);
    }

    default CompletableFuture<Set<Long>> sscanLongAsync(final String key, AtomicLong cursor, int limit) {
        return sscanAsync(key, Long.class, cursor, limit);
    }

    //-------------------------- 过期方法 ----------------------------------    
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(final String key, final Type componentType);

    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(final boolean set, final Type componentType, final String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Integer> getCollectionSizeAsync(final String key);

    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(final String key, final int expireSeconds, final Type componentType);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getStringCollectionAsync(final String key);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, final String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getexStringCollectionAsync(final String key, final int expireSeconds);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(final String key);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, final String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(final String key, final int expireSeconds);

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> refreshAsync(final String key, final int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> hremoveAsync(final String key, String... fields) {
        return hdelAsync(key, fields).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Convert convert, final T value) {
        return setexAsync(key, expireSeconds, convert, value.getClass(), value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Type type, final T value) {
        return setexAsync(key, expireSeconds, type, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        return setexAsync(key, expireSeconds, convert, type, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> setExpireSecondsAsync(final String key, final int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeAsync(final String key) {
        return delAsync(key).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default int hremove(final String key, String... fields) {
        return (int) hdel(key, fields);
    }

    @Deprecated(since = "2.8.0")
    default void refresh(final String key, final int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(final int expireSeconds, final String key, final Convert convert, final T value) {
        setex(key, expireSeconds, convert, value.getClass(), value);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(final int expireSeconds, final String key, final Type type, final T value) {
        setex(key, expireSeconds, type, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        setex(key, expireSeconds, convert, type, value);
    }

    @Deprecated(since = "2.8.0")
    default void setExpireSeconds(final String key, final int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default int remove(final String key) {
        return (int) del(key);
    }

    @Deprecated(since = "2.8.0")
    default void setString(final int expireSeconds, final String key, final String value) {
        setexString(key, expireSeconds, value);
    }

    @Deprecated(since = "2.8.0")
    default void setLong(final int expireSeconds, final String key, final long value) {
        setexLong(key, expireSeconds, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<T> getAndRefreshAsync(final String key, final int expireSeconds, final Type type) {
        return getexAsync(key, expireSeconds, type);
    }

    @Deprecated(since = "2.8.0")
    default <T> T getAndRefresh(final String key, final int expireSeconds, final Type type) {
        return getex(key, expireSeconds, type);
    }

    @Deprecated(since = "2.8.0")
    default String getStringAndRefresh(final String key, final int expireSeconds) {
        return getexString(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default long getLongAndRefresh(final String key, final int expireSeconds, long defValue) {
        return getexLong(key, expireSeconds, defValue);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long> getLongAndRefreshAsync(final String key, final int expireSeconds, long defValue) {
        return getexLongAsync(key, expireSeconds, defValue);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String> getStringAndRefreshAsync(final String key, final int expireSeconds) {
        return getexStringAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default int hsize(final String key) {
        return (int) hlen(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> hsizeAsync(final String key) {
        return hlenAsync(key).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> void appendSetItem(final String key, final Type componentType, final T value) {
        sadd(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> int removeSetItem(final String key, final Type componentType, final T value) {
        return (int) srem(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> T spopSetItem(final String key, final Type componentType) {
        return CacheSource.this.spop(key, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> Set<T> spopSetItem(final String key, final int count, final Type componentType) {
        return spop(key, count, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> boolean existsSetItem(final String key, final Type componentType, final T value) {
        return sismember(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Boolean> existsSetItemAsync(final String key, final Type componentType, final T value) {
        return sismemberAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> appendSetItemAsync(final String key, final Type componentType, final T value) {
        return saddAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Integer> removeSetItemAsync(final String key, final Type componentType, final T value) {
        return sremAsync(key, componentType, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<T> spopSetItemAsync(final String key, final Type componentType) {
        return spopAsync(key, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Set<T>> spopSetItemAsync(final String key, final int count, final Type componentType) {
        return spopAsync(key, count, componentType);
    }

    @Deprecated(since = "2.8.0")
    default boolean existsStringSetItem(final String key, final String value) {
        return sismemberString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendStringSetItem(final String key, final String value) {
        saddString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeStringSetItem(final String key, final String value) {
        return (int) sremString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default String spopStringSetItem(final String key) {
        return spopString(key);
    }

    @Deprecated(since = "2.8.0")
    default Set<String> spopStringSetItem(final String key, final int count) {
        return spopString(key, count);
    }

    @Deprecated(since = "2.8.0")
    default boolean existsLongSetItem(final String key, final long value) {
        return sismemberLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendLongSetItem(final String key, final long value) {
        saddLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeLongSetItem(final String key, final long value) {
        return (int) sremLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default Long spopLongSetItem(final String key) {
        return spopLong(key);
    }

    @Deprecated(since = "2.8.0")
    default Set<Long> spopLongSetItem(final String key, final int count) {
        return spopLong(key, count);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Boolean> existsStringSetItemAsync(final String key, final String value) {
        return sismemberStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendStringSetItemAsync(final String key, final String value) {
        return saddStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeStringSetItemAsync(final String key, final String value) {
        return sremStringAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String> spopStringSetItemAsync(final String key) {
        return spopStringAsync(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Set<String>> spopStringSetItemAsync(final String key, final int count) {
        return spopStringAsync(key, count);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Boolean> existsLongSetItemAsync(final String key, final long value) {
        return sismemberLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendLongSetItemAsync(final String key, final long value) {
        return saddLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeLongSetItemAsync(final String key, final long value) {
        return sremLongAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long> spopLongSetItemAsync(final String key) {
        return spopLongAsync(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Set<Long>> spopLongSetItemAsync(final String key, final int count) {
        return spopLongAsync(key, count);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> appendListItemAsync(final String key, final Type componentType, final T value) {
        return rpushAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Integer> removeListItemAsync(final String key, final Type componentType, final T value) {
        return lremAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendStringListItemAsync(final String key, final String value) {
        return rpushStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeStringListItemAsync(final String key, final String value) {
        return lremStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendLongListItemAsync(final String key, final long value) {
        return rpushLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeLongListItemAsync(final String key, final long value) {
        return lremLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> void appendListItem(final String key, final Type componentType, final T value) {
        rpush(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> int removeListItem(final String key, final Type componentType, final T value) {
        return lrem(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendStringListItem(final String key, final String value) {
        rpushString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeStringListItem(final String key, final String value) {
        return lremString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendLongListItem(final String key, final long value) {
        rpushLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeLongListItem(final String key, final long value) {
        return lremLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default List<String> queryKeys() {
        return keys();
    }

    @Deprecated(since = "2.8.0")
    default List<String> queryKeysStartsWith(String startsWith) {
        return keys(startsWith + "*");
    }

    @Deprecated(since = "2.8.0")
    default List<String> queryKeysEndsWith(String endsWith) {
        return keys("*" + endsWith);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<List<String>> queryKeysAsync() {
        return keysAsync();
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<List<String>> queryKeysStartsWithAsync(String startsWith) {
        return keysAsync(startsWith + "*");
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<List<String>> queryKeysEndsWithAsync(String endsWith) {
        return keysAsync("*" + endsWith);
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, T> getMap(final Type componentType, final String... keys) {
        return mget(componentType, keys);
    }

    @Deprecated(since = "2.8.0")
    default Map<String, String> getStringMap(final String... keys) {
        return mgetString(keys);
    }

    @Deprecated(since = "2.8.0")
    default Map<String, Long> getLongMap(final String... keys) {
        return mgetLong(keys);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, final String... keys) {
        return mgetAsync(componentType, keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Map<String, String>> getStringMapAsync(final String... keys) {
        return mgetStringAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Map<String, Long>> getLongMapAsync(final String... keys) {
        return mgetLongAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default long incr(final String key, long num) {
        return incrby(key, num);
    }

    @Deprecated(since = "2.8.0")
    default long decr(final String key, long num) {
        return decrby(key, num);
    }

    @Deprecated(since = "2.8.0")
    default String[] getStringArray(final String... keys) {
        return mgetsString(keys);
    }

    @Deprecated(since = "2.8.0")
    default Long[] getLongArray(final String... keys) {
        return mgetsLong(keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String[]> getStringArrayAsync(final String... keys) {
        return mgetsStringAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long[]> getLongArrayAsync(final String... keys) {
        return mgetsLongAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> getKeySizeAsync() {
        return dbsizeAsync().thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default int getKeySize() {
        return (int) dbsize();
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int start, int limit, String pattern) {
        return hscanAsync(key, type, new AtomicLong(start), limit, pattern);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int start, int limit) {
        return hscanAsync(key, type, new AtomicLong(start), limit);
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, T> hmap(final String key, final Type type, int start, int limit, String pattern) {
        return hscan(key, type, new AtomicLong(start), limit, pattern);
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, T> hmap(final String key, final Type type, int start, int limit) {
        return hscan(key, type, new AtomicLong(start), limit);
    }

    @Deprecated(since = "2.8.0")
    default Collection<String> getStringCollection(String key) {
        return getStringCollectionAsync(key).join();
    }

    @Deprecated(since = "2.8.0")
    default Map<String, Collection<String>> getStringCollectionMap(final boolean set, String... keys) {
        return getStringCollectionMapAsync(set, keys).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Collection<T> getCollection(String key, final Type componentType) {
        return (Collection) getCollectionAsync(key, componentType).join();
    }

    @Deprecated(since = "2.8.0")
    default int getCollectionSize(String key) {
        return getCollectionSizeAsync(key).join();
    }

    @Deprecated(since = "2.8.0")
    default Collection<Long> getLongCollection(String key) {
        return getLongCollectionAsync(key).join();
    }

    @Deprecated(since = "2.8.0")
    default Map<String, Collection<Long>> getLongCollectionMap(final boolean set, String... keys) {
        return getLongCollectionMapAsync(set, keys).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Collection<T> getexCollection(String key, final int expireSeconds, final Type componentType) {
        return (Collection) getexCollectionAsync(key, expireSeconds, componentType).join();
    }

    @Deprecated(since = "2.8.0")
    default Collection<String> getexStringCollection(String key, final int expireSeconds) {
        return getexStringCollectionAsync(key, expireSeconds).join();
    }

    @Deprecated(since = "2.8.0")
    default Collection<Long> getexLongCollection(String key, final int expireSeconds) {
        return getexLongCollectionAsync(key, expireSeconds).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, String... keys) {
        return (Map) getCollectionMapAsync(set, componentType, keys).join();
    }
}
