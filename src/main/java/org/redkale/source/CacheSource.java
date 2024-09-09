/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.annotation.*;
import org.redkale.convert.Convert;
import org.redkale.convert.TextConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.Resourcable;
import org.redkale.util.*;

/**
 * Redkale中缓存数据源的核心类。 主要供业务开发者使用， 技术开发者提供CacheSource的实现。<br>
 * CacheSource提供三种数据类型操作: String、Long和泛型指定的数据类型。<br>
 * String统一用setString、getString等系列方法。<br>
 * Long统一用setLong、getLong、incr等系列方法。<br>
 * 其他则供自定义数据类型使用。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Component
public interface CacheSource extends Resourcable {

    public String getType();

    default boolean isOpen() {
        return isOpenAsync().join();
    }

    // ------------------------ 订阅发布 SUB/PUB ------------------------
    default List<String> pubsubChannels(@Nullable String pattern) {
        return pubsubChannelsAsync(pattern).join();
    }

    public CompletableFuture<List<String>> pubsubChannelsAsync(@Nullable String pattern);

    // ------------------------ 订阅 SUB ------------------------
    default <T> void subscribe(Type messageType, CacheEventListener<T> listener, String... topics) {
        subscribe(JsonConvert.root(), messageType, listener, topics);
    }

    default <T> void subscribe(Convert convert, Type messageType, CacheEventListener<T> listener, String... topics) {
        final Convert c = convert == null ? JsonConvert.root() : convert;
        subscribe((t, bs) -> listener.onMessage(t, bs == null ? null : (T) c.convertFrom(messageType, bs)), topics);
    }

    default void subscribe(CacheEventListener<byte[]> listener, String... topics) {
        subscribeAsync(listener, topics).join();
    }

    default void unsubscribe(CacheEventListener listener, String... topics) {
        unsubscribeAsync(listener, topics).join();
    }

    default <T> CompletableFuture<Void> subscribeAsync(
            Type messageType, CacheEventListener<T> listener, String... topics) {
        return subscribeAsync(JsonConvert.root(), messageType, listener, topics);
    }

    default <T> CompletableFuture<Void> subscribeAsync(
            Convert convert, Type messageType, CacheEventListener<T> listener, String... topics) {
        final Convert c = convert == null ? JsonConvert.root() : convert;
        return subscribeAsync(
                (t, bs) -> listener.onMessage(t, bs == null ? null : (T) c.convertFrom(messageType, bs)), topics);
    }

    public CompletableFuture<Void> subscribeAsync(CacheEventListener<byte[]> listener, String... topics);

    public CompletableFuture<Integer> unsubscribeAsync(CacheEventListener listener, String... topics);

    // ------------------------ 发布 PUB ------------------------
    default <T> int publish(String topic, T message) {
        return publish(topic, null, message.getClass(), message);
    }

    default <T> int publish(String topic, Convert convert, T message) {
        return publish(topic, convert, message.getClass(), message);
    }

    default <T> int publish(String topic, Type messageType, T message) {
        return publish(topic, null, messageType, message);
    }

    default <T> int publish(String topic, Convert convert, Type messageType, T message) {
        return publishAsync(topic, convert, messageType, message).join();
    }

    default int publish(String topic, byte[] message) {
        return publishAsync(topic, message).join();
    }

    default <T> CompletableFuture<Integer> publishAsync(String topic, T message) {
        return publishAsync(topic, null, message.getClass(), message);
    }

    default <T> CompletableFuture<Integer> publishAsync(String topic, Convert convert, T message) {
        return publishAsync(topic, convert, message.getClass(), message);
    }

    default <T> CompletableFuture<Integer> publishAsync(String topic, Type messageType, T message) {
        return publishAsync(topic, null, messageType, message);
    }

    default <T> CompletableFuture<Integer> publishAsync(String topic, Convert convert, Type messageType, T message) {
        if (message == null || message instanceof byte[]) {
            return publishAsync(topic, (byte[]) message);
        }
        if (messageType == String.class && (convert == null || convert instanceof TextConvert)) {
            return publishAsync(topic, message.toString().getBytes(StandardCharsets.UTF_8));
        }
        final Convert c = convert == null ? JsonConvert.root() : convert;
        return publishAsync(topic, c.convertToBytes(messageType, message));
    }

    public CompletableFuture<Integer> publishAsync(String topic, byte[] message);

    // ------------------------ 令牌桶算法限流 ------------------------
    /**
     * 令牌桶算法限流， 返回负数表示无令牌， 其他为有令牌
     *
     * <pre>
     * 每秒限制请求1次:     rate:1,     capacity:1,     requested:1
     * 每秒限制请求10次:    rate:10,    capacity:10,    requested:1
     * 每分钟限制请求1次:   rate:1,     capacity:60,    requested:60
     * 每分钟限制请求10次:  rate:1,     capacity:60,    requested:6
     * 每小时限制请求1次:   rate:1,     capacity:3600,  requested:3600
     * 每小时限制请求10次:  rate:1,     capacity:3600,  requested:360
     * </pre>
     *
     * @param key 限流的键
     * @param rate 令牌桶每秒填充平均速率
     * @param capacity 令牌桶总容量
     * @param requested 需要的令牌数
     * @return 可用令牌数
     */
    default long rateLimit(String key, long rate, long capacity, long requested) {
        return rateLimitAsync(key, rate, capacity, requested).join();
    }

    /**
     * 令牌桶算法限流， 返回负数表示无令牌， 其他为有令牌
     *
     * <pre>
     * 每秒限制请求1次:     rate:1,     capacity:1,     requested:1
     * 每秒限制请求10次:    rate:10,    capacity:10,    requested:1
     * 每分钟限制请求1次:   rate:1,     capacity:60,    requested:60
     * 每分钟限制请求10次:  rate:1,     capacity:60,    requested:6
     * 每小时限制请求1次:   rate:1,     capacity:3600,  requested:3600
     * 每小时限制请求10次:  rate:1,     capacity:3600,  requested:360
     * </pre>
     *
     * @param key 限流的键
     * @param rate 令牌桶每秒填充平均速率
     * @param capacity 令牌桶总容量
     * @return 可用令牌数
     */
    default long rateLimit(String key, long rate, long capacity) {
        return rateLimit(key, rate, capacity, 1);
    }

    /**
     * 令牌桶算法限流， 返回负数表示无令牌， 其他为有令牌
     *
     * <pre>
     * 每秒限制请求1次:     rate:1,     capacity:1,     requested:1
     * 每秒限制请求10次:    rate:10,    capacity:10,    requested:1
     * 每分钟限制请求1次:   rate:1,     capacity:60,    requested:60
     * 每分钟限制请求10次:  rate:1,     capacity:60,    requested:6
     * 每小时限制请求1次:   rate:1,     capacity:3600,  requested:3600
     * 每小时限制请求10次:  rate:1,     capacity:3600,  requested:360
     * </pre>
     *
     * @param key 限流的键
     * @param rate 令牌桶每秒填充平均速率
     * @param capacity 令牌桶总容量
     * @param requested 需要的令牌数
     * @return 可用令牌数
     */
    public CompletableFuture<Long> rateLimitAsync(String key, long rate, long capacity, long requested);

    /**
     * 令牌桶算法限流， 返回负数表示无令牌， 其他为有令牌
     *
     * <pre>
     * 每秒限制请求1次:     rate:1,     capacity:1,     requested:1
     * 每秒限制请求10次:    rate:10,    capacity:10,    requested:1
     * 每分钟限制请求1次:   rate:1,     capacity:60,    requested:60
     * 每分钟限制请求10次:  rate:1,     capacity:60,    requested:6
     * 每小时限制请求1次:   rate:1,     capacity:3600,  requested:3600
     * 每小时限制请求10次:  rate:1,     capacity:3600,  requested:360
     * </pre>
     *
     * @param key 限流的键
     * @param rate 令牌桶每秒填充平均速率
     * @param capacity 令牌桶总容量
     * @return 可用令牌数
     */
    default CompletableFuture<Long> rateLimitAsync(String key, long rate, long capacity) {
        return rateLimitAsync(key, rate, capacity, 1);
    }

    // ------------------------ Lock ------------------------
    default boolean lock(String key, int expireSeconds) {
        return setnxexString(key, expireSeconds, "1");
    }

    default void unlock(String key) {
        del(key);
    }

    default CompletableFuture<Boolean> lockAsync(String key, int expireSeconds) {
        return setnxexStringAsync(key, expireSeconds, "1");
    }

    default CompletableFuture<Void> unlockAsync(String key) {
        return delAsync(key).thenApply(v -> null);
    }

    // ------------------------ 字符串 String ------------------------
    default long incr(String key) {
        return incrAsync(key).join();
    }

    default long incrby(String key, long num) {
        return incrbyAsync(key, num).join();
    }

    default double incrbyFloat(String key, double num) {
        return incrbyFloatAsync(key, num).join();
    }

    default long decr(String key) {
        return decrAsync(key).join();
    }

    default long decrby(String key, long num) {
        return decrbyAsync(key, num).join();
    }

    // ------------------------ set ------------------------
    default <T> void set(String key, Convert convert, Type type, T value) {
        setAsync(key, convert, type, value).join();
    }

    default <T> void set(String key, Type type, T value) {
        set(key, (Convert) null, type, value);
    }

    default void setString(String key, String value) {
        set(key, String.class, value);
    }

    default void setLong(String key, long value) {
        set(key, Long.class, value);
    }

    // MSET key value [key value ...]
    default void mset(Serializable... keyVals) {
        msetAsync(keyVals).join();
    }

    default void mset(Map map) {
        msetAsync(map).join();
    }

    // MSETNX key value [key value ...]
    default boolean msetnx(Serializable... keyVals) {
        return msetnxAsync(keyVals).join();
    }

    default boolean msetnx(Map map) {
        return msetnxAsync(map).join();
    }

    // ------------------------ setnx ------------------------
    default <T> boolean setnx(String key, Convert convert, Type type, T value) {
        return setnxAsync(key, convert, type, value).join();
    }

    default <T> boolean setnx(String key, Type type, T value) {
        return setnx(key, (Convert) null, type, value);
    }

    default boolean setnxString(String key, String value) {
        return setnx(key, String.class, value);
    }

    default boolean setnxLong(String key, long value) {
        return setnx(key, Long.class, value);
    }

    // ------------------------ setex ------------------------
    default <T> void setex(String key, int expireSeconds, Convert convert, Type type, T value) {
        setexAsync(key, expireSeconds, convert, type, value).join();
    }

    default <T> void setex(String key, int expireSeconds, Type type, T value) {
        setex(key, expireSeconds, (Convert) null, type, value);
    }

    default void setexString(String key, int expireSeconds, String value) {
        setex(key, expireSeconds, String.class, value);
    }

    default void setexLong(String key, int expireSeconds, long value) {
        setex(key, expireSeconds, Long.class, value);
    }

    default <T> void psetex(String key, long milliSeconds, Convert convert, Type type, T value) {
        psetexAsync(key, milliSeconds, convert, type, value).join();
    }

    default <T> void psetex(String key, long milliSeconds, Type type, T value) {
        psetex(key, milliSeconds, (Convert) null, type, value);
    }

    default void psetexString(String key, long milliSeconds, String value) {
        psetex(key, milliSeconds, String.class, value);
    }

    default void psetexLong(String key, long milliSeconds, long value) {
        psetex(key, milliSeconds, Long.class, value);
    }

    // ------------------------ setnxex ------------------------
    default <T> boolean setnxex(String key, int expireSeconds, Convert convert, Type type, T value) {
        return setnxexAsync(key, expireSeconds, convert, type, value).join();
    }

    default <T> boolean setnxex(String key, int expireSeconds, Type type, T value) {
        return setnxex(key, expireSeconds, (Convert) null, type, value);
    }

    default boolean setnxexString(String key, int expireSeconds, String value) {
        return setnxex(key, expireSeconds, String.class, value);
    }

    default boolean setnxexLong(String key, int expireSeconds, long value) {
        return setnxex(key, expireSeconds, Long.class, value);
    }

    default <T> boolean setnxpx(String key, long milliSeconds, Convert convert, Type type, T value) {
        return setnxpxAsync(key, milliSeconds, convert, type, value).join();
    }

    default <T> boolean setnxpx(String key, long milliSeconds, Type type, T value) {
        return setnxpx(key, milliSeconds, (Convert) null, type, value);
    }

    default boolean setnxpxString(String key, long milliSeconds, String value) {
        return setnxpx(key, milliSeconds, String.class, value);
    }

    default boolean setnxpxLong(String key, long milliSeconds, long value) {
        return setnxpx(key, milliSeconds, Long.class, value);
    }

    // ------------------------ get ------------------------
    default <T> T get(String key, Type type) {
        return (T) getAsync(key, type).join();
    }

    default String getString(String key) {
        return get(key, String.class);
    }

    default long getLong(String key, long defValue) {
        Long val = get(key, Long.class);
        return val == null ? defValue : val;
    }

    default Long getLong(String key) {
        return get(key, Long.class);
    }

    // ------------------------ mget ------------------------
    default <T> List<T> mget(Type componentType, String... keys) {
        return (List) mgetAsync(componentType, keys).join();
    }

    default List<String> mgetString(String... keys) {
        return mget(String.class, keys);
    }

    default List<Long> mgetLong(String... keys) {
        return mget(Long.class, keys);
    }

    default <T> Map<String, T> mgets(Type componentType, String... keys) {
        return (Map) mgetsAsync(componentType, keys).join();
    }

    default Map<String, String> mgetsString(String... keys) {
        return mgets(String.class, keys);
    }

    default Map<String, Long> mgetsLong(String... keys) {
        return mgets(Long.class, keys);
    }

    // ------------------------ getex ------------------------
    default <T> T getex(String key, int expireSeconds, Type type) {
        return (T) getexAsync(key, expireSeconds, type).join();
    }

    default String getexString(String key, int expireSeconds) {
        return getex(key, expireSeconds, String.class);
    }

    default long getexLong(String key, int expireSeconds, long defValue) {
        Long val = getex(key, expireSeconds, Long.class);
        return val == null ? defValue : val;
    }

    default Long getexLong(String key, int expireSeconds) {
        return getex(key, expireSeconds, Long.class);
    }

    // ------------------------ getset ------------------------
    default <T> T getSet(String key, Convert convert, Type type, T value) {
        return getSetAsync(key, convert, type, value).join();
    }

    default <T> T getSet(String key, Type type, T value) {
        return getSet(key, (Convert) null, type, value);
    }

    default String getSetString(String key, String value) {
        return getSet(key, String.class, value);
    }

    default long getSetLong(String key, long value, long defValue) {
        Long val = getSet(key, Long.class, value);
        return val == null ? defValue : val;
    }

    default Long getSetLong(String key, long value) {
        return getSet(key, Long.class, value);
    }

    // ------------------------ getdel ------------------------
    default <T> T getDel(String key, Type type) {
        return (T) getDelAsync(key, type).join();
    }

    default String getDelString(String key) {
        return getDel(key, String.class);
    }

    default long getDelLong(String key, long defValue) {
        Long val = getDel(key, Long.class);
        return val == null ? defValue : val;
    }

    default Long getDelLong(String key) {
        return getDel(key, Long.class);
    }

    // ------------------------ 键 Keys ------------------------
    default long del(String... keys) {
        return delAsync(keys).join();
    }

    default long delex(String key, String expectedValue) {
        return delexAsync(key, expectedValue).join();
    }

    default boolean exists(String key) {
        return existsAsync(key).join();
    }

    default void expire(String key, int expireSeconds) {
        expireAsync(key, expireSeconds).join();
    }

    default void pexpire(String key, long milliSeconds) {
        pexpireAsync(key, milliSeconds).join();
    }

    default long ttl(String key) {
        return ttlAsync(key).join();
    }

    default long pttl(String key) {
        return pttlAsync(key).join();
    }

    default void expireAt(String key, long secondsTime) {
        expireAtAsync(key, secondsTime).join();
    }

    default void pexpireAt(String key, long milliTime) {
        pexpireAtAsync(key, milliTime).join();
    }

    default long pexpireTime(String key) {
        return pexpireTimeAsync(key).join();
    }

    default long expireTime(String key) {
        return expireTimeAsync(key).join();
    }

    default List<String> keys(String pattern) {
        return keysAsync(pattern).join();
    }

    default List<String> keys() {
        return keys(null);
    }

    default List<String> keysStartsWith(String startsWith) {
        return keys(startsWith + "*");
    }

    default boolean persist(String key) {
        return persistAsync(key).join();
    }

    default boolean rename(String oldKey, String newKey) {
        return renameAsync(oldKey, newKey).join();
    }

    default boolean renamenx(String oldKey, String newKey) {
        return renamenxAsync(oldKey, newKey).join();
    }

    default List<String> scan(AtomicLong cursor, int limit, String pattern) {
        return scanAsync(cursor, limit, pattern).join();
    }

    default List<String> scan(AtomicLong cursor, int limit) {
        return scan(cursor, limit, null);
    }

    // ------------------------ 服务器 Server  ------------------------
    default long dbsize() {
        return dbsizeAsync().join();
    }

    default void flushdb() {
        flushdbAsync().join();
    }

    default void flushall() {
        flushallAsync().join();
    }

    // ------------------------ 哈希表 Hash ------------------------
    default long hdel(String key, String... fields) {
        return hdelAsync(key, fields).join();
    }

    default List<String> hkeys(String key) {
        return hkeysAsync(key).join();
    }

    default long hlen(String key) {
        return hlenAsync(key).join();
    }

    default long hincr(String key, String field) {
        return hincrAsync(key, field).join();
    }

    default long hincrby(String key, String field, long num) {
        return hincrbyAsync(key, field, num).join();
    }

    default double hincrbyFloat(String key, String field, double num) {
        return hincrbyFloatAsync(key, field, num).join();
    }

    default long hdecr(String key, String field) {
        return hdecrAsync(key, field).join();
    }

    default long hdecrby(String key, String field, long num) {
        return hdecrbyAsync(key, field, num).join();
    }

    default boolean hexists(String key, String field) {
        return hexistsAsync(key, field).join();
    }

    default void hmset(String key, Serializable... values) {
        hmsetAsync(key, values).join();
    }

    default void hmset(String key, Map map) {
        hmsetAsync(key, map).join();
    }

    default <T> List<T> hmget(String key, Type type, String... fields) {
        return (List) hmgetAsync(key, type, fields).join();
    }

    default List<String> hmgetString(String key, String... fields) {
        return hmget(key, String.class, fields);
    }

    default List<Long> hmgetLong(String key, String... fields) {
        return hmget(key, Long.class, fields);
    }

    default <T> Map<String, T> hscan(String key, Type type, AtomicLong cursor, int limit, String pattern) {
        return (Map) hscanAsync(key, type, cursor, limit, pattern).join();
    }

    default <T> Map<String, T> hscan(String key, Type type, AtomicLong cursor, int limit) {
        return hscan(key, type, cursor, limit, null);
    }

    default <T> T hget(String key, String field, Type type) {
        return (T) hgetAsync(key, field, type).join();
    }

    default String hgetString(String key, String field) {
        return hget(key, field, String.class);
    }

    default long hgetLong(String key, String field, long defValue) {
        Long val = hget(key, field, Long.class);
        return val == null ? defValue : val;
    }

    default Long hgetLong(String key, String field) {
        return hget(key, field, Long.class);
    }

    default <T> void hset(String key, String field, Convert convert, Type type, T value) {
        hsetAsync(key, field, convert, type, value).join();
    }

    default <T> void hset(String key, String field, Type type, T value) {
        hset(key, field, (Convert) null, type, value);
    }

    default void hsetString(String key, String field, String value) {
        hset(key, field, String.class, value);
    }

    default void hsetLong(String key, String field, long value) {
        hset(key, field, Long.class, value);
    }

    default <T> boolean hsetnx(String key, String field, Convert convert, Type type, T value) {
        return hsetnxAsync(key, field, convert, type, value).join();
    }

    default <T> boolean hsetnx(String key, String field, Type type, T value) {
        return hsetnx(key, field, (Convert) null, type, value);
    }

    default boolean hsetnxString(String key, String field, String value) {
        return hsetnx(key, field, String.class, value);
    }

    default boolean hsetnxLong(String key, String field, long value) {
        return hsetnx(key, field, Long.class, value);
    }

    default long hstrlen(String key, String field) {
        return hstrlenAsync(key, field).join();
    }

    default <T> Map<String, T> hgetall(String key, Type type) {
        return (Map) hgetallAsync(key, type).join();
    }

    default Map<String, String> hgetallString(String key) {
        return hgetall(key, String.class);
    }

    default Map<String, Long> hgetallLong(String key) {
        return hgetall(key, Long.class);
    }

    default <T> List<T> hvals(String key, Type type) {
        return (List) hvalsAsync(key, type).join();
    }

    default List<String> hvalsString(String key) {
        return hvals(key, String.class);
    }

    default List<Long> hvalsLong(String key) {
        return hvals(key, Long.class);
    }

    // ------------------------ 列表 List ------------------------
    default <T> T lindex(String key, Type componentType, int index) {
        return (T) lindexAsync(key, componentType, index).join();
    }

    default String lindexString(String key, int index) {
        return lindex(key, String.class, index);
    }

    default Long lindexLong(String key, int index) {
        return lindex(key, Long.class, index);
    }

    default <T> long linsertBefore(String key, Type componentType, T pivot, T value) {
        return linsertBeforeAsync(key, componentType, pivot, value).join();
    }

    default long linsertBeforeString(String key, String pivot, String value) {
        return linsertBefore(key, String.class, pivot, value);
    }

    default long linsertBeforeLong(String key, Long pivot, Long value) {
        return linsertBefore(key, Long.class, pivot, value);
    }

    default <T> long linsertAfter(String key, Type componentType, T pivot, T value) {
        return linsertAfterAsync(key, componentType, pivot, value).join();
    }

    default long linsertAfterString(String key, String pivot, String value) {
        return linsertAfter(key, String.class, pivot, value);
    }

    default long linsertAfterLong(String key, Long pivot, Long value) {
        return linsertAfter(key, Long.class, pivot, value);
    }

    default long llen(String key) {
        return llenAsync(key).join();
    }

    default void ltrim(String key, int start, int stop) {
        ltrimAsync(key, start, stop).join();
    }

    default <T> Map<String, List<T>> lranges(Type componentType, String... keys) {
        return (Map) lrangesAsync(componentType, keys).join();
    }

    default Map<String, List<String>> lrangesString(String... keys) {
        return lranges(String.class, keys);
    }

    default Map<String, List<Long>> lrangesLong(String... keys) {
        return lranges(Long.class, keys);
    }

    default <T> List<T> lrange(String key, Type componentType, int start, int stop) {
        return (List) lrangeAsync(key, componentType, start, stop).join();
    }

    default List<String> lrangeString(String key, int start, int stop) {
        return lrange(key, String.class, start, stop);
    }

    default List<Long> lrangeLong(String key, int start, int stop) {
        return lrange(key, Long.class, start, stop);
    }

    default <T> List<T> lrange(String key, Type componentType) {
        return lrange(key, componentType, 0, -1);
    }

    default List<String> lrangeString(String key) {
        return lrange(key, String.class, 0, -1);
    }

    default List<Long> lrangeLong(String key) {
        return lrange(key, Long.class, 0, -1);
    }

    default <T> T lpop(String key, Type componentType) {
        return (T) lpopAsync(key, componentType).join();
    }

    default String lpopString(String key) {
        return lpop(key, String.class);
    }

    default Long lpopLong(String key) {
        return lpop(key, Long.class);
    }

    default <T> void lpush(String key, Type componentType, T... values) {
        lpushAsync(key, componentType, values).join();
    }

    default void lpushString(String key, String... values) {
        lpush(key, String.class, values);
    }

    default void lpushLong(String key, Long... values) {
        lpush(key, Long.class, values);
    }

    default <T> void lpushx(String key, Type componentType, T... values) {
        lpushxAsync(key, componentType, values).join();
    }

    default void lpushxString(String key, String... values) {
        lpushx(key, String.class, values);
    }

    default void lpushxLong(String key, Long... values) {
        lpushx(key, Long.class, values);
    }

    default <T> void rpushx(String key, Type componentType, T... values) {
        rpushxAsync(key, componentType, values).join();
    }

    default void rpushxString(String key, String... values) {
        rpushx(key, String.class, values);
    }

    default void rpushxLong(String key, Long... values) {
        rpushx(key, Long.class, values);
    }

    default <T> T rpop(String key, Type componentType) {
        return (T) rpopAsync(key, componentType).join();
    }

    default String rpopString(String key) {
        return rpop(key, String.class);
    }

    default Long rpopLong(String key) {
        return rpop(key, Long.class);
    }

    default <T> T rpoplpush(String key, String key2, Type componentType) {
        return (T) rpoplpushAsync(key, key2, componentType).join();
    }

    default String rpoplpushString(String key, String key2) {
        return rpoplpush(key, key2, String.class);
    }

    default Long rpoplpushLong(String key, String key2) {
        return rpoplpush(key, key2, Long.class);
    }

    default <T> long lrem(String key, Type componentType, T value) {
        return lremAsync(key, componentType, value).join();
    }

    default long lremString(String key, String value) {
        return lrem(key, String.class, value);
    }

    default long lremLong(String key, long value) {
        return lrem(key, Long.class, value);
    }

    default <T> void rpush(String key, Type componentType, T... values) {
        rpushAsync(key, componentType, values).join();
    }

    default void rpushString(String key, String... values) {
        rpush(key, String.class, values);
    }

    default void rpushLong(String key, Long... values) {
        rpush(key, Long.class, values);
    }

    // ------------------------ 集合 Set ------------------------
    default <T> void sadd(String key, Type componentType, T... values) {
        saddAsync(key, componentType, values).join();
    }

    default void saddString(String key, String... values) {
        sadd(key, String.class, values);
    }

    default void saddLong(String key, Long... values) {
        sadd(key, Long.class, values);
    }

    default <T> boolean smove(String key, String key2, Type componentType, T member) {
        return smoveAsync(key, key2, componentType, member).join();
    }

    default boolean smoveString(String key, String key2, String member) {
        return smove(key, key2, String.class, member);
    }

    default boolean smoveLong(String key, String key2, Long member) {
        return smove(key, key2, Long.class, member);
    }

    default <T> List<T> srandmember(String key, Type componentType, int count) {
        return (List) srandmemberAsync(key, componentType, count).join();
    }

    default List<String> srandmemberString(String key, int count) {
        return srandmember(key, String.class, count);
    }

    default List<Long> srandmemberLong(String key, int count) {
        return srandmember(key, Long.class, count);
    }

    default <T> T srandmember(String key, Type componentType) {
        return (T) srandmemberAsync(key, componentType).join();
    }

    default CompletableFuture<String> srandmemberString(String key) {
        return srandmember(key, String.class);
    }

    default Long srandmemberLong(String key) {
        return srandmember(key, Long.class);
    }

    default <T> Set<T> sdiff(String key, Type componentType, String... key2s) {
        return (Set) sdiffAsync(key, componentType, key2s).join();
    }

    default Set<String> sdiffString(String key, String... key2s) {
        return sdiff(key, String.class, key2s);
    }

    default Set<Long> sdiffLong(String key, String... key2s) {
        return sdiff(key, Long.class, key2s);
    }

    default long sdiffstore(String key, String srcKey, String... srcKey2s) {
        return sdiffstoreAsync(key, srcKey, srcKey2s).join();
    }

    default <T> Set<T> sinter(String key, Type componentType, String... key2s) {
        return (Set) sinterAsync(key, componentType, key2s).join();
    }

    default Set<String> sinterString(String key, String... key2s) {
        return sinter(key, String.class, key2s);
    }

    default Set<Long> sinterLong(String key, String... key2s) {
        return sinter(key, Long.class, key2s);
    }

    default long sinterstore(String key, String srcKey, String... srcKey2s) {
        return sinterstoreAsync(key, srcKey, srcKey2s).join();
    }

    default <T> Set<T> sunion(String key, Type componentType, String... key2s) {
        return (Set) sunionAsync(key, componentType, key2s).join();
    }

    default Set<String> sunionString(String key, String... key2s) {
        return sunion(key, String.class, key2s);
    }

    default Set<Long> sunionLong(String key, String... key2s) {
        return sunion(key, Long.class, key2s);
    }

    default long sunionstore(String key, String srcKey, String... srcKey2s) {
        return sunionstoreAsync(key, srcKey, srcKey2s).join();
    }

    default long scard(String key) {
        return scardAsync(key).join();
    }

    default <T> Set<T> smembers(String key, Type componentType) {
        return (Set) smembersAsync(key, componentType).join();
    }

    default Set<String> smembersString(String key) {
        return smembers(key, String.class);
    }

    default Set<Long> smembersLong(String key) {
        return smembers(key, Long.class);
    }

    default <T> Map<String, Set<T>> smembers(Type componentType, String... keys) {
        return (Map) smembersAsync(componentType, keys).join();
    }

    default Map<String, Set<String>> smembersString(String... keys) {
        return smembers(String.class, keys);
    }

    default Map<String, Set<Long>> smembersLong(String... keys) {
        return smembers(Long.class, keys);
    }

    default <T> boolean sismember(String key, Type componentType, T value) {
        return sismemberAsync(key, componentType, value).join();
    }

    default boolean sismemberString(String key, String value) {
        return sismember(key, String.class, value);
    }

    default boolean sismemberLong(String key, long value) {
        return sismember(key, Long.class, value);
    }

    default boolean smismember(String key, String member) {
        List<Boolean> rs = smismembers(key, member);
        return rs.get(0);
    }

    default List<Boolean> smismembers(String key, String... members) {
        return smismembersAsync(key, members).join();
    }

    default <T> long srem(String key, Type componentType, T... values) {
        return sremAsync(key, componentType, values).join();
    }

    default long sremString(String key, String... values) {
        return srem(key, String.class, values);
    }

    default long sremLong(String key, Long... values) {
        return srem(key, Long.class, values);
    }

    default <T> T spop(String key, Type componentType) {
        return (T) spopAsync(key, componentType).join();
    }

    default String spopString(String key) {
        return spop(key, String.class);
    }

    default Long spopLong(String key) {
        return spop(key, Long.class);
    }

    default <T> Set<T> spop(String key, int count, Type componentType) {
        return (Set) spopAsync(key, count, componentType).join();
    }

    default Set<String> spopString(String key, int count) {
        return spop(key, count, String.class);
    }

    default Set<Long> spopLong(String key, int count) {
        return spop(key, count, Long.class);
    }

    default <T> Set<T> sscan(String key, Type componentType, AtomicLong cursor, int limit, String pattern) {
        return (Set) sscanAsync(key, componentType, cursor, limit, pattern).join();
    }

    default Set<String> sscanString(String key, AtomicLong cursor, int limit, String pattern) {
        return sscan(key, String.class, cursor, limit, pattern);
    }

    default Set<Long> sscanLong(String key, AtomicLong cursor, int limit, String pattern) {
        return sscan(key, Long.class, cursor, limit, pattern);
    }

    default <T> Set<T> sscan(String key, Type componentType, AtomicLong cursor, int limit) {
        return sscan(key, componentType, cursor, limit, null);
    }

    default Set<String> sscanString(String key, AtomicLong cursor, int limit) {
        return sscan(key, String.class, cursor, limit, null);
    }

    default Set<Long> sscanLong(String key, AtomicLong cursor, int limit) {
        return sscan(key, Long.class, cursor, limit, null);
    }

    // ------------------------ 有序集合 Sorted Set ------------------------
    default void zadd(String key, CacheScoredValue... values) {
        zaddAsync(key, values).join();
    }

    default void zadd(String key, int score, String member) {
        zadd(key, CacheScoredValue.create(score, member));
    }

    default void zadd(String key, long score, String member) {
        zadd(key, CacheScoredValue.create(score, member));
    }

    default void zadd(String key, double score, String member) {
        zadd(key, CacheScoredValue.create(score, member));
    }

    default <T extends Number> T zincrby(String key, CacheScoredValue value) {
        return (T) zincrbyAsync(key, value).join();
    }

    default int zincrby(String key, int score, String member) {
        return zincrby(key, CacheScoredValue.create(score, member));
    }

    default long zincrby(String key, long score, String member) {
        return zincrby(key, CacheScoredValue.create(score, member));
    }

    default double zincrby(String key, double score, String member) {
        return zincrby(key, CacheScoredValue.create(score, member));
    }

    default long zrem(String key, String... members) {
        return zremAsync(key, members).join();
    }

    default <T extends Number> List<T> zmscore(String key, Class<T> scoreType, String... members) {
        return zmscoreAsync(key, scoreType, members).join();
    }

    default List<Integer> zmscoreInteger(String key, String... members) {
        return zmscore(key, Integer.class, members);
    }

    default List<Long> zmscoreLong(String key, String... members) {
        return zmscore(key, Long.class, members);
    }

    default List<Double> zmscoreDouble(String key, String... members) {
        return zmscore(key, Double.class, members);
    }

    default <T extends Number> T zscore(String key, Class<T> scoreType, String member) {
        return zscoreAsync(key, scoreType, member).join();
    }

    default Integer zscoreInteger(String key, String member) {
        return zscore(key, Integer.class, member);
    }

    default Long zscoreLong(String key, String member) {
        return zscore(key, Long.class, member);
    }

    default Double zscoreDouble(String key, String member) {
        return zscore(key, Double.class, member);
    }

    default long zcard(String key) {
        return zcardAsync(key).join();
    }

    default Long zrank(String key, String member) {
        return zrankAsync(key, member).join();
    }

    default Long zrevrank(String key, String member) {
        return zrevrankAsync(key, member).join();
    }

    default List<String> zrange(String key, int start, int stop) {
        return zrangeAsync(key, start, stop).join();
    }

    default List<CacheScoredValue> zscan(String key, Type scoreType, AtomicLong cursor, int limit, String pattern) {
        return zscanAsync(key, scoreType, cursor, limit, pattern).join();
    }

    default List<CacheScoredValue> zscanInteger(String key, AtomicLong cursor, int limit, String pattern) {
        return zscan(key, Integer.class, cursor, limit, pattern);
    }

    default List<CacheScoredValue> zscanLong(String key, AtomicLong cursor, int limit, String pattern) {
        return zscan(key, Long.class, cursor, limit, pattern);
    }

    default List<CacheScoredValue> zscanDouble(String key, AtomicLong cursor, int limit, String pattern) {
        return zscan(key, Double.class, cursor, limit, pattern);
    }

    default List<CacheScoredValue> zscan(String key, Type scoreType, AtomicLong cursor, int limit) {
        return zscan(key, scoreType, cursor, limit, null);
    }

    default List<CacheScoredValue> zscanInteger(String key, AtomicLong cursor, int limit) {
        return zscan(key, Integer.class, cursor, limit, null);
    }

    default List<CacheScoredValue> zscanLong(String key, AtomicLong cursor, int limit) {
        return zscan(key, Long.class, cursor, limit, null);
    }

    default List<CacheScoredValue> zscanDouble(String key, AtomicLong cursor, int limit) {
        return zscan(key, Double.class, cursor, limit, null);
    }

    // ---------------------- CompletableFuture 异步版 ---------------------------------
    public CompletableFuture<Boolean> isOpenAsync();

    // ------------------------ 键 Keys ------------------------
    public CompletableFuture<Long> incrAsync(String key);

    public CompletableFuture<Long> incrbyAsync(String key, long num);

    public CompletableFuture<Long> decrAsync(String key);

    public CompletableFuture<Long> decrbyAsync(String key, long num);

    public CompletableFuture<Double> incrbyFloatAsync(String key, double num);

    // ------------------------ set ------------------------
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, Type type, T value);

    default <T> CompletableFuture<Void> setAsync(String key, Type type, T value) {
        return setAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<Void> setStringAsync(String key, String value) {
        return setAsync(key, String.class, value);
    }

    default CompletableFuture<Void> setLongAsync(String key, long value) {
        return setAsync(key, Long.class, value);
    }

    // MSET key value [key value ...]
    public CompletableFuture<Void> msetAsync(Serializable... keyVals);

    public CompletableFuture<Void> msetAsync(Map map);

    // MSET key value [key value ...]
    public CompletableFuture<Boolean> msetnxAsync(Serializable... keyVals);

    public CompletableFuture<Boolean> msetnxAsync(Map map);

    // ------------------------ setnx ------------------------
    public <T> CompletableFuture<Boolean> setnxAsync(String key, Convert convert, Type type, T value);

    default <T> CompletableFuture<Boolean> setnxAsync(String key, Type type, T value) {
        return setnxAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> setnxStringAsync(String key, String value) {
        return setnxAsync(key, String.class, value);
    }

    default CompletableFuture<Boolean> setnxLongAsync(String key, long value) {
        return setnxAsync(key, Long.class, value);
    }

    // ------------------------ setex ------------------------
    public <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Convert convert, Type type, T value);

    default <T> CompletableFuture<Void> setexAsync(String key, int expireSeconds, Type type, T value) {
        return setexAsync(key, expireSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Void> setexStringAsync(String key, int expireSeconds, String value) {
        return setexAsync(key, expireSeconds, String.class, value);
    }

    default CompletableFuture<Void> setexLongAsync(String key, int expireSeconds, long value) {
        return setexAsync(key, expireSeconds, Long.class, value);
    }

    public <T> CompletableFuture<Void> psetexAsync(String key, long milliSeconds, Convert convert, Type type, T value);

    default <T> CompletableFuture<Void> psetexAsync(String key, long milliSeconds, Type type, T value) {
        return psetexAsync(key, milliSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Void> psetexStringAsync(String key, long milliSeconds, String value) {
        return psetexAsync(key, milliSeconds, String.class, value);
    }

    default CompletableFuture<Void> psetexLongAsync(String key, long milliSeconds, long value) {
        return psetexAsync(key, milliSeconds, Long.class, value);
    }

    // ------------------------ setnxex ------------------------
    public <T> CompletableFuture<Boolean> setnxexAsync(
            String key, int expireSeconds, Convert convert, Type type, T value);

    default <T> CompletableFuture<Boolean> setnxexAsync(String key, int expireSeconds, Type type, T value) {
        return setnxexAsync(key, expireSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> setnxexStringAsync(String key, int expireSeconds, String value) {
        return setnxexAsync(key, expireSeconds, String.class, value);
    }

    default CompletableFuture<Boolean> setnxexLongAsync(String key, int expireSeconds, long value) {
        return setnxexAsync(key, expireSeconds, Long.class, value);
    }

    public <T> CompletableFuture<Boolean> setnxpxAsync(
            String key, long milliSeconds, Convert convert, Type type, T value);

    default <T> CompletableFuture<Boolean> setnxpxAsync(String key, long milliSeconds, Type type, T value) {
        return setnxpxAsync(key, milliSeconds, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> setnxpxStringAsync(String key, long milliSeconds, String value) {
        return setnxpxAsync(key, milliSeconds, String.class, value);
    }

    default CompletableFuture<Boolean> setnxpxLongAsync(String key, long milliSeconds, long value) {
        return setnxpxAsync(key, milliSeconds, Long.class, value);
    }

    // ------------------------ get ------------------------
    public <T> CompletableFuture<T> getAsync(String key, Type type);

    default CompletableFuture<String> getStringAsync(String key) {
        return getAsync(key, String.class);
    }

    default CompletableFuture<Long> getLongAsync(String key, long defValue) {
        return getAsync(key, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    default CompletableFuture<Long> getLongAsync(String key) {
        return getAsync(key, Long.class);
    }

    // ------------------------ mget ------------------------
    public <T> CompletableFuture<List<T>> mgetAsync(Type componentType, String... keys);

    default CompletableFuture<List<String>> mgetStringAsync(String... keys) {
        return mgetAsync(String.class, keys);
    }

    default CompletableFuture<List<Long>> mgetLongAsync(String... keys) {
        return mgetAsync(Long.class, keys);
    }

    default <T> CompletableFuture<Map<String, T>> mgetsAsync(Type componentType, String... keys) {
        return mgetAsync(componentType, keys).thenApply(list -> {
            Map<String, T> map = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                T obj = (T) list.get(i);
                if (obj != null) {
                    map.put(keys[i], obj);
                }
            }
            return map;
        });
    }

    default CompletableFuture<Map<String, String>> mgetsStringAsync(String... keys) {
        return mgetsAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, Long>> mgetsLongAsync(String... keys) {
        return mgetsAsync(Long.class, keys);
    }

    // ------------------------ getex ------------------------
    /**
     * 获取key的值并可选择设置其过期时间
     *
     * @param <T> 泛型
     * @param key 键
     * @param expireSeconds 过期秒数
     * @param type key值的类型
     * @return key的值
     */
    public <T> CompletableFuture<T> getexAsync(String key, int expireSeconds, Type type);

    default CompletableFuture<String> getexStringAsync(String key, int expireSeconds) {
        return getexAsync(key, expireSeconds, String.class);
    }

    default CompletableFuture<Long> getexLongAsync(String key, int expireSeconds, long defValue) {
        return getexAsync(key, expireSeconds, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    default CompletableFuture<Long> getexLongAsync(String key, int expireSeconds) {
        return getexAsync(key, expireSeconds, Long.class);
    }

    // ------------------------ getset ------------------------
    public <T> CompletableFuture<T> getSetAsync(String key, Convert convert, Type type, T value);

    default <T> CompletableFuture<T> getSetAsync(String key, Type type, T value) {
        return getSetAsync(key, (Convert) null, type, value);
    }

    default CompletableFuture<String> getSetStringAsync(String key, String value) {
        return getSetAsync(key, String.class, value);
    }

    default CompletableFuture<Long> getSetLongAsync(String key, long value, long defValue) {
        return getSetAsync(key, Long.class, value).thenApply(v -> v == null ? defValue : v);
    }

    default CompletableFuture<Long> getSetLongAsync(String key, long value) {
        return getSetAsync(key, Long.class, value);
    }

    // ------------------------ getdel ------------------------
    public <T> CompletableFuture<T> getDelAsync(String key, Type type);

    default CompletableFuture<String> getDelStringAsync(String key) {
        return getDelAsync(key, String.class);
    }

    default CompletableFuture<Long> getDelLongAsync(String key, long defValue) {
        return getDelAsync(key, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    default CompletableFuture<Long> getDelLongAsync(String key) {
        return getDelAsync(key, Long.class);
    }

    // ------------------------ 键 Keys ------------------------
    public CompletableFuture<Long> delexAsync(String key, String expectedValue);

    public CompletableFuture<Long> delAsync(String... keys);

    public CompletableFuture<Boolean> existsAsync(String key);

    public CompletableFuture<Void> expireAsync(String key, int seconds);

    public CompletableFuture<Void> pexpireAsync(String key, long milliSeconds);

    public CompletableFuture<Long> ttlAsync(String key);

    public CompletableFuture<Long> pttlAsync(String key);

    public CompletableFuture<Void> expireAtAsync(String key, long secondsTime);

    public CompletableFuture<Long> expireTimeAsync(String key);

    public CompletableFuture<Void> pexpireAtAsync(String key, long milliTime);

    public CompletableFuture<Long> pexpireTimeAsync(String key);

    public CompletableFuture<List<String>> keysAsync(String pattern);

    default CompletableFuture<List<String>> keysAsync() {
        return keysAsync(null);
    }

    default CompletableFuture<List<String>> keysStartsWithAsync(String startsWith) {
        return keysAsync(startsWith + "*");
    }

    public CompletableFuture<Boolean> persistAsync(String key);

    public CompletableFuture<Boolean> renameAsync(String oldKey, String newKey);

    public CompletableFuture<Boolean> renamenxAsync(String oldKey, String newKey);

    public CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit, String pattern);

    default CompletableFuture<List<String>> scanAsync(AtomicLong cursor, int limit) {
        return scanAsync(cursor, limit, null);
    }

    // ------------------------ 服务器 Server  ------------------------
    public CompletableFuture<Long> dbsizeAsync();

    public CompletableFuture<Void> flushdbAsync();

    public CompletableFuture<Void> flushallAsync();

    // ------------------------ 哈希表 Hash ------------------------
    public <T> CompletableFuture<T> hgetAsync(String key, String field, Type type);

    default CompletableFuture<String> hgetStringAsync(String key, String field) {
        return hgetAsync(key, field, String.class);
    }

    default CompletableFuture<Long> hgetLongAsync(String key, String field, long defValue) {
        return hgetAsync(key, field, Long.class).thenApply(v -> v == null ? defValue : (Long) v);
    }

    default CompletableFuture<Long> hgetLongAsync(String key, String field) {
        return hgetAsync(key, field, Long.class);
    }

    public <T> CompletableFuture<Void> hsetAsync(String key, String field, Convert convert, Type type, T value);

    default <T> CompletableFuture<Void> hsetAsync(String key, String field, Type type, T value) {
        return hsetAsync(key, field, (Convert) null, type, value);
    }

    default CompletableFuture<Void> hsetStringAsync(String key, String field, String value) {
        return hsetAsync(key, field, String.class, value);
    }

    default CompletableFuture<Void> hsetLongAsync(String key, String field, long value) {
        return hsetAsync(key, field, Long.class, value);
    }

    public <T> CompletableFuture<Boolean> hsetnxAsync(String key, String field, Convert convert, Type type, T value);

    default <T> CompletableFuture<Boolean> hsetnxAsync(String key, String field, Type type, T value) {
        return hsetnxAsync(key, field, (Convert) null, type, value);
    }

    default CompletableFuture<Boolean> hsetnxStringAsync(String key, String field, String value) {
        return hsetnxAsync(key, field, String.class, value);
    }

    default CompletableFuture<Boolean> hsetnxLongAsync(String key, String field, long value) {
        return hsetnxAsync(key, field, Long.class, value);
    }

    public CompletableFuture<Long> hstrlenAsync(String key, String field);

    public <T> CompletableFuture<Map<String, T>> hgetallAsync(String key, Type type);

    default CompletableFuture<Map<String, String>> hgetallStringAsync(String key) {
        return hgetallAsync(key, String.class);
    }

    default CompletableFuture<Map<String, Long>> hgetallLongAsync(String key) {
        return hgetallAsync(key, Long.class);
    }

    public <T> CompletableFuture<List<T>> hvalsAsync(String key, Type type);

    default CompletableFuture<List<String>> hvalsStringAsync(String key) {
        return hvalsAsync(key, String.class);
    }

    default CompletableFuture<List<Long>> hvalsLongAsync(String key) {
        return hvalsAsync(key, Long.class);
    }

    public CompletableFuture<Long> hdelAsync(String key, String... fields);

    public CompletableFuture<List<String>> hkeysAsync(String key);

    public CompletableFuture<Long> hlenAsync(String key);

    public CompletableFuture<Long> hincrAsync(String key, String field);

    public CompletableFuture<Long> hincrbyAsync(String key, String field, long num);

    public CompletableFuture<Double> hincrbyFloatAsync(String key, String field, double num);

    public CompletableFuture<Long> hdecrAsync(String key, String field);

    public CompletableFuture<Long> hdecrbyAsync(String key, String field, long num);

    public CompletableFuture<Boolean> hexistsAsync(String key, String field);

    public CompletableFuture<Void> hmsetAsync(String key, Serializable... values);

    public CompletableFuture<Void> hmsetAsync(String key, Map map);

    public <T> CompletableFuture<List<T>> hmgetAsync(String key, Type type, String... fields);

    default CompletableFuture<List<String>> hmgetStringAsync(String key, String... fields) {
        return hmgetAsync(key, String.class, fields);
    }

    default CompletableFuture<List<Long>> hmgetLongAsync(String key, String... fields) {
        return hmgetAsync(key, Long.class, fields);
    }

    public <T> CompletableFuture<Map<String, T>> hscanAsync(
            String key, Type type, AtomicLong cursor, int limit, String pattern);

    default <T> CompletableFuture<Map<String, T>> hscanAsync(String key, Type type, AtomicLong cursor, int limit) {
        return hscanAsync(key, type, cursor, limit, null);
    }

    // ------------------------ 列表 List ------------------------
    public <T> CompletableFuture<T> lindexAsync(String key, Type componentType, int index);

    default CompletableFuture<String> lindexStringAsync(String key, int index) {
        return lindexAsync(key, String.class, index);
    }

    default CompletableFuture<Long> lindexLongAsync(String key, int index) {
        return lindexAsync(key, Long.class, index);
    }

    public <T> CompletableFuture<Long> linsertBeforeAsync(String key, Type componentType, T pivot, T value);

    default CompletableFuture<Long> linsertBeforeStringAsync(String key, String pivot, String value) {
        return linsertBeforeAsync(key, String.class, pivot, value);
    }

    default CompletableFuture<Long> linsertBeforeLongAsync(String key, Long pivot, Long value) {
        return linsertBeforeAsync(key, Long.class, pivot, value);
    }

    public <T> CompletableFuture<Long> linsertAfterAsync(String key, Type componentType, T pivot, T value);

    default CompletableFuture<Long> linsertAfterStringAsync(String key, String pivot, String value) {
        return linsertAfterAsync(key, String.class, pivot, value);
    }

    default CompletableFuture<Long> linsertAfterLongAsync(String key, Long pivot, Long value) {
        return linsertAfterAsync(key, Long.class, pivot, value);
    }

    public CompletableFuture<Long> llenAsync(String key);

    public CompletableFuture<Void> ltrimAsync(String key, int start, int stop);

    public <T> CompletableFuture<Map<String, List<T>>> lrangesAsync(Type componentType, String... keys);

    default CompletableFuture<Map<String, List<String>>> lrangesStringAsync(String... keys) {
        return lrangesAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, List<Long>>> lrangesLongAsync(String... keys) {
        return lrangesAsync(Long.class, keys);
    }

    public <T> CompletableFuture<List<T>> lrangeAsync(String key, Type componentType, int start, int stop);

    default CompletableFuture<List<String>> lrangeStringAsync(String key, int start, int stop) {
        return lrangeAsync(key, String.class, start, stop);
    }

    default CompletableFuture<List<Long>> lrangeLongAsync(String key, int start, int stop) {
        return lrangeAsync(key, Long.class, start, stop);
    }

    default <T> CompletableFuture<List<T>> lrangeAsync(String key, Type componentType) {
        return lrangeAsync(key, componentType, 0, -1);
    }

    default CompletableFuture<List<String>> lrangeStringAsync(String key) {
        return lrangeAsync(key, String.class, 0, -1);
    }

    default CompletableFuture<List<Long>> lrangeLongAsync(String key) {
        return lrangeAsync(key, Long.class, 0, -1);
    }

    public <T> CompletableFuture<T> lpopAsync(String key, Type componentType);

    default CompletableFuture<String> lpopStringAsync(String key) {
        return lpopAsync(key, String.class);
    }

    default CompletableFuture<Long> lpopLongAsync(String key) {
        return lpopAsync(key, Long.class);
    }

    public <T> CompletableFuture<Void> lpushAsync(String key, Type componentType, T... values);

    default CompletableFuture<Void> lpushStringAsync(String key, String... values) {
        return lpushAsync(key, String.class, values);
    }

    default CompletableFuture<Void> lpushLongAsync(String key, Long... values) {
        return lpushAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Void> lpushxAsync(String key, Type componentType, T... values);

    default CompletableFuture<Void> lpushxStringAsync(String key, String... values) {
        return lpushxAsync(key, String.class, values);
    }

    default CompletableFuture<Void> lpushxLongAsync(String key, Long... values) {
        return lpushxAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Void> rpushxAsync(String key, Type componentType, T... values);

    default CompletableFuture<Void> rpushxStringAsync(String key, String... values) {
        return rpushxAsync(key, String.class, values);
    }

    default CompletableFuture<Void> rpushxLongAsync(String key, Long... values) {
        return rpushxAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<T> rpopAsync(String key, Type componentType);

    default CompletableFuture<String> rpopStringAsync(String key) {
        return rpopAsync(key, String.class);
    }

    default CompletableFuture<Long> rpopLongAsync(String key) {
        return rpopAsync(key, Long.class);
    }

    public <T> CompletableFuture<T> rpoplpushAsync(String key, String key2, Type componentType);

    default CompletableFuture<String> rpoplpushStringAsync(String key, String key2) {
        return rpoplpushAsync(key, key2, String.class);
    }

    default CompletableFuture<Long> rpoplpushLongAsync(String key, String key2) {
        return rpoplpushAsync(key, key2, Long.class);
    }

    public <T> CompletableFuture<Long> lremAsync(String key, Type componentType, T value);

    default CompletableFuture<Long> lremStringAsync(String key, String value) {
        return lremAsync(key, String.class, value);
    }

    default CompletableFuture<Long> lremLongAsync(String key, long value) {
        return lremAsync(key, Long.class, value);
    }

    public <T> CompletableFuture<Void> rpushAsync(String key, Type componentType, T... values);

    default CompletableFuture<Void> rpushStringAsync(String key, String... values) {
        return rpushAsync(key, String.class, values);
    }

    default CompletableFuture<Void> rpushLongAsync(String key, Long... values) {
        return rpushAsync(key, Long.class, values);
    }

    // ------------------------ 集合 Set ------------------------
    public <T> CompletableFuture<Void> saddAsync(String key, Type componentType, T... values);

    default CompletableFuture<Void> saddStringAsync(String key, String... values) {
        return saddAsync(key, String.class, values);
    }

    default CompletableFuture<Void> saddLongAsync(String key, Long... values) {
        return saddAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<Set<T>> sdiffAsync(String key, Type componentType, String... key2s);

    default CompletableFuture<Set<String>> sdiffStringAsync(String key, String... key2s) {
        return sdiffAsync(key, String.class, key2s);
    }

    default CompletableFuture<Set<Long>> sdiffLongAsync(String key, String... key2s) {
        return sdiffAsync(key, Long.class, key2s);
    }

    public <T> CompletableFuture<Boolean> smoveAsync(String key, String key2, Type componentType, T member);

    default CompletableFuture<Boolean> smoveStringAsync(String key, String key2, String member) {
        return smoveAsync(key, key2, String.class, member);
    }

    default CompletableFuture<Boolean> smoveLongAsync(String key, String key2, Long member) {
        return smoveAsync(key, key2, Long.class, member);
    }

    public <T> CompletableFuture<List<T>> srandmemberAsync(String key, Type componentType, int count);

    default CompletableFuture<List<String>> srandmemberStringAsync(String key, int count) {
        return srandmemberAsync(key, String.class, count);
    }

    default CompletableFuture<List<Long>> srandmemberLongAsync(String key, int count) {
        return srandmemberAsync(key, Long.class, count);
    }

    default <T> CompletableFuture<T> srandmemberAsync(String key, Type componentType) {
        return srandmemberAsync(key, componentType, 1)
                .thenApply(list -> Utility.isNotEmpty(list) ? (T) list.get(0) : null);
    }

    default CompletableFuture<String> srandmemberStringAsync(String key) {
        return srandmemberAsync(key, String.class);
    }

    default CompletableFuture<Long> srandmemberLongAsync(String key) {
        return srandmemberAsync(key, Long.class);
    }

    public CompletableFuture<Long> sdiffstoreAsync(String key, String srcKey, String... srcKey2s);

    public <T> CompletableFuture<Set<T>> sinterAsync(String key, Type componentType, String... key2s);

    default CompletableFuture<Set<String>> sinterStringAsync(String key, String... key2s) {
        return sinterAsync(key, String.class, key2s);
    }

    default CompletableFuture<Set<Long>> sinterLongAsync(String key, String... key2s) {
        return sinterAsync(key, Long.class, key2s);
    }

    public CompletableFuture<Long> sinterstoreAsync(String key, String srcKey, String... srcKey2s);

    public <T> CompletableFuture<Set<T>> sunionAsync(String key, Type componentType, String... key2s);

    default CompletableFuture<Set<String>> sunionStringAsync(String key, String... key2s) {
        return sunionAsync(key, String.class, key2s);
    }

    default CompletableFuture<Set<Long>> sunionLongAsync(String key, String... key2s) {
        return sunionAsync(key, Long.class, key2s);
    }

    public CompletableFuture<Long> sunionstoreAsync(String key, String srcKey, String... srcKey2s);

    public CompletableFuture<Long> scardAsync(String key);

    public <T> CompletableFuture<Set<T>> smembersAsync(String key, Type componentType);

    default CompletableFuture<Set<String>> smembersStringAsync(String key) {
        return smembersAsync(key, String.class);
    }

    default CompletableFuture<Set<Long>> smembersLongAsync(String key) {
        return smembersAsync(key, Long.class);
    }

    public <T> CompletableFuture<Map<String, Set<T>>> smembersAsync(Type componentType, String... keys);

    default CompletableFuture<Map<String, Set<String>>> smembersStringAsync(String... keys) {
        return smembersAsync(String.class, keys);
    }

    default CompletableFuture<Map<String, Set<Long>>> smembersLongAsync(String... keys) {
        return smembersAsync(Long.class, keys);
    }

    public <T> CompletableFuture<Boolean> sismemberAsync(String key, Type componentType, T value);

    default CompletableFuture<Boolean> sismemberStringAsync(String key, String value) {
        return sismemberAsync(key, String.class, value);
    }

    default CompletableFuture<Boolean> sismemberLongAsync(String key, long value) {
        return sismemberAsync(key, Long.class, value);
    }

    public <T> CompletableFuture<List<Boolean>> smismembersAsync(String key, String... members);

    public <T> CompletableFuture<Long> sremAsync(String key, Type componentType, T... values);

    default CompletableFuture<Long> sremStringAsync(String key, String... values) {
        return sremAsync(key, String.class, values);
    }

    default CompletableFuture<Long> sremLongAsync(String key, Long... values) {
        return sremAsync(key, Long.class, values);
    }

    public <T> CompletableFuture<T> spopAsync(String key, Type componentType);

    default CompletableFuture<String> spopStringAsync(String key) {
        return spopAsync(key, String.class);
    }

    default CompletableFuture<Long> spopLongAsync(String key) {
        return spopAsync(key, Long.class);
    }

    public <T> CompletableFuture<Set<T>> spopAsync(String key, int count, Type componentType);

    default CompletableFuture<Set<String>> spopStringAsync(String key, int count) {
        return spopAsync(key, count, String.class);
    }

    default CompletableFuture<Set<Long>> spopLongAsync(String key, int count) {
        return spopAsync(key, count, Long.class);
    }

    public <T> CompletableFuture<Set<T>> sscanAsync(
            String key, Type componentType, AtomicLong cursor, int limit, String pattern);

    default CompletableFuture<Set<String>> sscanStringAsync(String key, AtomicLong cursor, int limit, String pattern) {
        return sscanAsync(key, String.class, cursor, limit, pattern);
    }

    default CompletableFuture<Set<Long>> sscanLongAsync(String key, AtomicLong cursor, int limit, String pattern) {
        return sscanAsync(key, Long.class, cursor, limit, pattern);
    }

    default <T> CompletableFuture<Set<T>> sscanAsync(String key, Type componentType, AtomicLong cursor, int limit) {
        return sscanAsync(key, componentType, cursor, limit, null);
    }

    default CompletableFuture<Set<String>> sscanStringAsync(String key, AtomicLong cursor, int limit) {
        return sscanAsync(key, String.class, cursor, limit);
    }

    default CompletableFuture<Set<Long>> sscanLongAsync(String key, AtomicLong cursor, int limit) {
        return sscanAsync(key, Long.class, cursor, limit);
    }

    // ------------------------ 有序集合 Sorted Set ------------------------
    public CompletableFuture<Void> zaddAsync(String key, CacheScoredValue... values);

    default CompletableFuture<Void> zaddAsync(String key, int score, String member) {
        return zaddAsync(key, CacheScoredValue.create(score, member));
    }

    default CompletableFuture<Void> zaddAsync(String key, long score, String member) {
        return zaddAsync(key, CacheScoredValue.create(score, member));
    }

    default CompletableFuture<Void> zaddAsync(String key, double score, String member) {
        return zaddAsync(key, CacheScoredValue.create(score, member));
    }

    public <T extends Number> CompletableFuture<T> zincrbyAsync(String key, CacheScoredValue value);

    default CompletableFuture<Integer> zincrbyAsync(String key, int score, String member) {
        return zincrbyAsync(key, CacheScoredValue.create(score, member));
    }

    default CompletableFuture<Long> zincrbyAsync(String key, long score, String member) {
        return zincrbyAsync(key, CacheScoredValue.create(score, member));
    }

    default CompletableFuture<Double> zincrbyAsync(String key, double score, String member) {
        return zincrbyAsync(key, CacheScoredValue.create(score, member));
    }

    public CompletableFuture<Long> zremAsync(String key, String... members);

    public <T extends Number> CompletableFuture<List<T>> zmscoreAsync(
            String key, Class<T> scoreType, String... members);

    public <T extends Number> CompletableFuture<T> zscoreAsync(String key, Class<T> scoreType, String member);

    default CompletableFuture<List<Integer>> zmscoreIntegerAsync(String key, String... members) {
        return zmscoreAsync(key, Integer.class, members);
    }

    default CompletableFuture<List<Long>> zmscoreLongAsync(String key, String... members) {
        return zmscoreAsync(key, Long.class, members);
    }

    default CompletableFuture<List<Double>> zmscoreDoubleAsync(String key, String... members) {
        return zmscoreAsync(key, Double.class, members);
    }

    default CompletableFuture<Integer> zscoreIntegerAsync(String key, String member) {
        return zscoreAsync(key, Integer.class, member);
    }

    default CompletableFuture<Long> zscoreLongAsync(String key, String member) {
        return zscoreAsync(key, Long.class, member);
    }

    default CompletableFuture<Double> zscoreDoubleAsync(String key, String member) {
        return zscoreAsync(key, Double.class, member);
    }

    public CompletableFuture<Long> zcardAsync(String key);
    //
    //    public CompletableFuture<Long> zcountAsync(String key, Range<? extends Number> range);
    //
    //    default CompletableFuture<Long> zcountAsync(String key, long min, long max) {
    //        return zcountAsync(key, new Range.LongRange(min, max));
    //    }
    //
    //    default CompletableFuture<Long> zcountAsync(String key, double min, double max) {
    //        return zcountAsync(key, new Range.DoubleRange(min, max));
    //    }
    //
    //    public <T> CompletableFuture<Set<T>> zdiffAsync(String key, int numkeys, boolean withScores, String... key2s);
    //
    //    public CompletableFuture<Long> zdiffstoreAsync(String key, int numkeys, String srcKey, String... srcKey2s);
    //
    //    public <T> CompletableFuture<Set<T>> zinterAsync(String key, int numkeys, boolean withScores, String...
    // key2s);
    //
    //    public CompletableFuture<Long> zinterstoreAsync(String key, int numkeys, String srcKey, String... srcKey2s);
    //

    //
    public CompletableFuture<Long> zrankAsync(String key, String member);

    public CompletableFuture<Long> zrevrankAsync(String key, String member);

    public CompletableFuture<List<String>> zrangeAsync(String key, int start, int stop);

    public CompletableFuture<List<CacheScoredValue>> zscanAsync(
            String key, Type scoreType, AtomicLong cursor, int limit, String pattern);

    default CompletableFuture<List<CacheScoredValue>> zscanIntegerAsync(
            String key, AtomicLong cursor, int limit, String pattern) {
        return zscanAsync(key, Integer.class, cursor, limit, pattern);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanLongAsync(
            String key, AtomicLong cursor, int limit, String pattern) {
        return zscanAsync(key, Long.class, cursor, limit, pattern);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanDoubleAsync(
            String key, AtomicLong cursor, int limit, String pattern) {
        return zscanAsync(key, Double.class, cursor, limit, pattern);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanAsync(
            String key, Type scoreType, AtomicLong cursor, int limit) {
        return zscanAsync(key, scoreType, cursor, limit, null);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanIntegerAsync(String key, AtomicLong cursor, int limit) {
        return zscanAsync(key, Integer.class, cursor, limit, null);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanLongAsync(String key, AtomicLong cursor, int limit) {
        return zscanAsync(key, Long.class, cursor, limit, null);
    }

    default CompletableFuture<List<CacheScoredValue>> zscanDoubleAsync(String key, AtomicLong cursor, int limit) {
        return zscanAsync(key, Double.class, cursor, limit, null);
    }

    // -------------------------- 过期方法 ----------------------------------
    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(String key, Type componentType);

    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(
            boolean set, Type componentType, String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Integer> getCollectionSizeAsync(String key);

    @Deprecated(since = "2.8.0")
    public <T> CompletableFuture<Collection<T>> getexCollectionAsync(String key, int expireSeconds, Type componentType);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getStringCollectionAsync(String key);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(boolean set, String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<String>> getexStringCollectionAsync(String key, int expireSeconds);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(String key);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(boolean set, String... keys);

    @Deprecated(since = "2.8.0")
    public CompletableFuture<Collection<Long>> getexLongCollectionAsync(String key, int expireSeconds);

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> refreshAsync(String key, int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> hremoveAsync(String key, String... fields) {
        return hdelAsync(key, fields).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, T value) {
        return setexAsync(key, expireSeconds, convert, value.getClass(), value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Type type, T value) {
        return setexAsync(key, expireSeconds, type, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, Type type, T value) {
        return setexAsync(key, expireSeconds, convert, type, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> setExpireSecondsAsync(String key, int expireSeconds) {
        return expireAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeAsync(String key) {
        return delAsync(key).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default int hremove(String key, String... fields) {
        return (int) hdel(key, fields);
    }

    @Deprecated(since = "2.8.0")
    default void refresh(String key, int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(int expireSeconds, String key, Convert convert, T value) {
        setex(key, expireSeconds, convert, value.getClass(), value);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(int expireSeconds, String key, Type type, T value) {
        setex(key, expireSeconds, type, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> void set(int expireSeconds, String key, Convert convert, Type type, T value) {
        setex(key, expireSeconds, convert, type, value);
    }

    @Deprecated(since = "2.8.0")
    default void setExpireSeconds(String key, int expireSeconds) {
        expire(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default int remove(String key) {
        return (int) del(key);
    }

    @Deprecated(since = "2.8.0")
    default void setString(int expireSeconds, String key, String value) {
        setexString(key, expireSeconds, value);
    }

    @Deprecated(since = "2.8.0")
    default void setLong(int expireSeconds, String key, long value) {
        setexLong(key, expireSeconds, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<T> getAndRefreshAsync(String key, int expireSeconds, Type type) {
        return getexAsync(key, expireSeconds, type);
    }

    @Deprecated(since = "2.8.0")
    default <T> T getAndRefresh(String key, int expireSeconds, Type type) {
        return getex(key, expireSeconds, type);
    }

    @Deprecated(since = "2.8.0")
    default String getStringAndRefresh(String key, int expireSeconds) {
        return getexString(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default long getLongAndRefresh(String key, int expireSeconds, long defValue) {
        return getexLong(key, expireSeconds, defValue);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long> getLongAndRefreshAsync(String key, int expireSeconds, long defValue) {
        return getexLongAsync(key, expireSeconds, defValue);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String> getStringAndRefreshAsync(String key, int expireSeconds) {
        return getexStringAsync(key, expireSeconds);
    }

    @Deprecated(since = "2.8.0")
    default int hsize(String key) {
        return (int) hlen(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> hsizeAsync(String key) {
        return hlenAsync(key).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> void appendSetItem(String key, Type componentType, T value) {
        sadd(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> int removeSetItem(String key, Type componentType, T value) {
        return (int) srem(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> T spopSetItem(String key, Type componentType) {
        return CacheSource.this.spop(key, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> Set<T> spopSetItem(String key, int count, Type componentType) {
        return spop(key, count, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> boolean existsSetItem(String key, Type componentType, T value) {
        return sismember(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Boolean> existsSetItemAsync(String key, Type componentType, T value) {
        return sismemberAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> appendSetItemAsync(String key, Type componentType, T value) {
        return saddAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Integer> removeSetItemAsync(String key, Type componentType, T value) {
        return sremAsync(key, componentType, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<T> spopSetItemAsync(String key, Type componentType) {
        return spopAsync(key, componentType);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Set<T>> spopSetItemAsync(String key, int count, Type componentType) {
        return spopAsync(key, count, componentType);
    }

    @Deprecated(since = "2.8.0")
    default boolean existsStringSetItem(String key, String value) {
        return sismemberString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendStringSetItem(String key, String value) {
        saddString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeStringSetItem(String key, String value) {
        return (int) sremString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default String spopStringSetItem(String key) {
        return spopString(key);
    }

    @Deprecated(since = "2.8.0")
    default Set<String> spopStringSetItem(String key, int count) {
        return spopString(key, count);
    }

    @Deprecated(since = "2.8.0")
    default boolean existsLongSetItem(String key, long value) {
        return sismemberLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendLongSetItem(String key, long value) {
        saddLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeLongSetItem(String key, long value) {
        return (int) sremLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default Long spopLongSetItem(String key) {
        return spopLong(key);
    }

    @Deprecated(since = "2.8.0")
    default Set<Long> spopLongSetItem(String key, int count) {
        return spopLong(key, count);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Boolean> existsStringSetItemAsync(String key, String value) {
        return sismemberStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendStringSetItemAsync(String key, String value) {
        return saddStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeStringSetItemAsync(String key, String value) {
        return sremStringAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String> spopStringSetItemAsync(String key) {
        return spopStringAsync(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Set<String>> spopStringSetItemAsync(String key, int count) {
        return spopStringAsync(key, count);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Boolean> existsLongSetItemAsync(String key, long value) {
        return sismemberLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendLongSetItemAsync(String key, long value) {
        return saddLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeLongSetItemAsync(String key, long value) {
        return sremLongAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long> spopLongSetItemAsync(String key) {
        return spopLongAsync(key);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Set<Long>> spopLongSetItemAsync(String key, int count) {
        return spopLongAsync(key, count);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Void> appendListItemAsync(String key, Type componentType, T value) {
        return rpushAsync(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Integer> removeListItemAsync(String key, Type componentType, T value) {
        return lremAsync(key, componentType, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendStringListItemAsync(String key, String value) {
        return rpushStringAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeStringListItemAsync(String key, String value) {
        return lremStringAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Void> appendLongListItemAsync(String key, long value) {
        return rpushLongAsync(key, value);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Integer> removeLongListItemAsync(String key, long value) {
        return lremLongAsync(key, value).thenApply(v -> v.intValue());
    }

    @Deprecated(since = "2.8.0")
    default <T> void appendListItem(String key, Type componentType, T value) {
        rpush(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default <T> int removeListItem(String key, Type componentType, T value) {
        return (int) lrem(key, componentType, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendStringListItem(String key, String value) {
        rpushString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeStringListItem(String key, String value) {
        return (int) lremString(key, value);
    }

    @Deprecated(since = "2.8.0")
    default void appendLongListItem(String key, long value) {
        rpushLong(key, value);
    }

    @Deprecated(since = "2.8.0")
    default int removeLongListItem(String key, long value) {
        return (int) lremLong(key, value);
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
    default <T> Map<String, T> getMap(Type componentType, String... keys) {
        return mgets(componentType, keys);
    }

    @Deprecated(since = "2.8.0")
    default Map<String, String> getStringMap(String... keys) {
        return mgetsString(keys);
    }

    @Deprecated(since = "2.8.0")
    default Map<String, Long> getLongMap(String... keys) {
        return mgetsLong(keys);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Map<String, T>> getMapAsync(Type componentType, String... keys) {
        return mgetsAsync(componentType, keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Map<String, String>> getStringMapAsync(String... keys) {
        return mgetsStringAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Map<String, Long>> getLongMapAsync(String... keys) {
        return mgetsLongAsync(keys);
    }

    @Deprecated(since = "2.8.0")
    default long incr(String key, long num) {
        return incrby(key, num);
    }

    @Deprecated(since = "2.8.0")
    default long decr(String key, long num) {
        return decrby(key, num);
    }

    @Deprecated(since = "2.8.0")
    default String[] getStringArray(String... keys) {
        List<String> list = mgetString(keys);
        return list.toArray(new String[list.size()]);
    }

    @Deprecated(since = "2.8.0")
    default Long[] getLongArray(String... keys) {
        List<Long> list = mgetLong(keys);
        return list.toArray(new Long[list.size()]);
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<String[]> getStringArrayAsync(String... keys) {
        return mgetStringAsync(keys).thenApply(list -> list.toArray(new String[list.size()]));
    }

    @Deprecated(since = "2.8.0")
    default CompletableFuture<Long[]> getLongArrayAsync(String... keys) {
        return mgetLongAsync(keys).thenApply(list -> list.toArray(new Long[list.size()]));
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
    default <T> CompletableFuture<Map<String, T>> hmapAsync(
            String key, Type type, int start, int limit, String pattern) {
        return hscanAsync(key, type, new AtomicLong(start), limit, pattern);
    }

    @Deprecated(since = "2.8.0")
    default <T> CompletableFuture<Map<String, T>> hmapAsync(String key, Type type, int start, int limit) {
        return hscanAsync(key, type, new AtomicLong(start), limit);
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, T> hmap(String key, Type type, int start, int limit, String pattern) {
        return hscan(key, type, new AtomicLong(start), limit, pattern);
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, T> hmap(String key, Type type, int start, int limit) {
        return hscan(key, type, new AtomicLong(start), limit);
    }

    @Deprecated(since = "2.8.0")
    default Collection<String> getStringCollection(String key) {
        return getStringCollectionAsync(key).join();
    }

    @Deprecated(since = "2.8.0")
    default Map<String, Collection<String>> getStringCollectionMap(boolean set, String... keys) {
        return getStringCollectionMapAsync(set, keys).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Collection<T> getCollection(String key, Type componentType) {
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
    default Map<String, Collection<Long>> getLongCollectionMap(boolean set, String... keys) {
        return getLongCollectionMapAsync(set, keys).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Collection<T> getexCollection(String key, int expireSeconds, Type componentType) {
        return (Collection)
                getexCollectionAsync(key, expireSeconds, componentType).join();
    }

    @Deprecated(since = "2.8.0")
    default Collection<String> getexStringCollection(String key, int expireSeconds) {
        return getexStringCollectionAsync(key, expireSeconds).join();
    }

    @Deprecated(since = "2.8.0")
    default Collection<Long> getexLongCollection(String key, int expireSeconds) {
        return getexLongCollectionAsync(key, expireSeconds).join();
    }

    @Deprecated(since = "2.8.0")
    default <T> Map<String, Collection<T>> getCollectionMap(boolean set, Type componentType, String... keys) {
        return (Map) getCollectionMapAsync(set, componentType, keys).join();
    }
}
