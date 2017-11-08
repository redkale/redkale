/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonFactory;

/**
 *
 * @param <V> value的类型
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface CacheSource<V extends Object> {

    public void initValueType(Type valueType);

    public void initTransient(boolean flag);

    default boolean isOpen() {
        return true;
    }

    public boolean exists(final String key);

    public V get(final String key);

    public V getAndRefresh(final String key, final int expireSeconds);

    public void refresh(final String key, final int expireSeconds);

    public void set(final String key, final V value);

    public void set(final int expireSeconds, final String key, final V value);

    public void setExpireSeconds(final String key, final int expireSeconds);

    public void remove(final String key);

    public Collection<V> getCollection(final String key);

    public int getCollectionSize(final String key);

    public Collection<V> getCollectionAndRefresh(final String key, final int expireSeconds);

    public void appendListItem(final String key, final V value);

    public void removeListItem(final String key, final V value);

    public void appendSetItem(final String key, final V value);

    public void removeSetItem(final String key, final V value);

    public List<String> queryKeys();

    public int getKeySize();

    public List<CacheEntry<Object>> queryList();

    //---------------------- CompletableFuture 异步版 ---------------------------------
    public CompletableFuture<Boolean> existsAsync(final String key);

    public CompletableFuture<V> getAsync(final String key);

    public CompletableFuture<V> getAndRefreshAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> refreshAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> setAsync(final String key, final V value);

    public CompletableFuture<Void> setAsync(final int expireSeconds, final String key, final V value);

    public CompletableFuture<Void> setExpireSecondsAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> removeAsync(final String key);

    public CompletableFuture<Collection<V>> getCollectionAsync(final String key);

    public CompletableFuture<Integer> getCollectionSizeAsync(final String key);

    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final String key, final int expireSeconds);

    public CompletableFuture<Void> appendListItemAsync(final String key, final V value);

    public CompletableFuture<Void> removeListItemAsync(final String key, final V value);

    public CompletableFuture<Void> appendSetItemAsync(final String key, final V value);

    public CompletableFuture<Void> removeSetItemAsync(final String key, final V value);

    public CompletableFuture<List<String>> queryKeysAsync();

    public CompletableFuture<Integer> getKeySizeAsync();

    public CompletableFuture<List<CacheEntry< Object>>> queryListAsync();

    default CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(true);
    }

    public static enum CacheEntryType {
        OBJECT, SET, LIST;
    }

    public static final class CacheEntry<T> {

        static final String JSON_SET_KEY = "{\"cacheType\":\"" + CacheEntryType.SET + "\"";

        static final String JSON_LIST_KEY = "{\"cacheType\":\"" + CacheEntryType.LIST + "\"";

        final CacheEntryType cacheType;

        final String key;

        //<=0表示永久保存
        int expireSeconds;

        volatile int lastAccessed; //最后刷新时间

        T objectValue;

        CopyOnWriteArraySet<T> setValue;

        ConcurrentLinkedQueue<T> listValue;

        public CacheEntry(CacheEntryType cacheType, String key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
            this(cacheType, 0, key, objectValue, setValue, listValue);
        }

        public CacheEntry(CacheEntryType cacheType, int expireSeconds, String key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
            this(cacheType, expireSeconds, (int) (System.currentTimeMillis() / 1000), key, objectValue, setValue, listValue);
        }

        @ConstructorProperties({"cacheType", "expireSeconds", "lastAccessed", "key", "objectValue", "setValue", "listValue"})
        public CacheEntry(CacheEntryType cacheType, int expireSeconds, int lastAccessed, String key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
            this.cacheType = cacheType;
            this.expireSeconds = expireSeconds;
            this.lastAccessed = lastAccessed;
            this.key = key;
            this.objectValue = objectValue;
            this.setValue = setValue;
            this.listValue = listValue;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        @ConvertColumn(ignore = true)
        public boolean isListCacheType() {
            return cacheType == CacheEntryType.LIST;
        }

        @ConvertColumn(ignore = true)
        public boolean isSetCacheType() {
            return cacheType == CacheEntryType.SET;
        }

        @ConvertColumn(ignore = true)
        public boolean isExpired() {
            return (expireSeconds > 0 && lastAccessed + expireSeconds < (System.currentTimeMillis() / 1000));
        }

        public CacheEntryType getCacheType() {
            return cacheType;
        }

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public int getLastAccessed() {
            return lastAccessed;
        }

        public String getKey() {
            return key;
        }

        public T getObjectValue() {
            return objectValue;
        }

        public CopyOnWriteArraySet<T> getSetValue() {
            return setValue;
        }

        public ConcurrentLinkedQueue<T> getListValue() {
            return listValue;
        }

    }
}
