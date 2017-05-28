/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.beans.ConstructorProperties;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonFactory;

/**
 *
 * @param <K> key的类型
 * @param <V> value的类型
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface CacheSource<K extends Serializable, V extends Object> {

    default boolean isOpen() {
        return true;
    }

    public boolean exists(final K key);

    public V get(final K key);

    public V getAndRefresh(final K key, final int expireSeconds);

    public void refresh(final K key, final int expireSeconds);

    public void set(final K key, final V value);

    public void set(final int expireSeconds, final K key, final V value);

    public void setExpireSeconds(final K key, final int expireSeconds);

    public void remove(final K key);

    public Collection<V> getCollection(final K key);

    public long getCollectionSize(final K key);

    public Collection<V> getCollectionAndRefresh(final K key, final int expireSeconds);

    public void appendListItem(final K key, final V value);

    public void removeListItem(final K key, final V value);

    public void appendSetItem(final K key, final V value);

    public void removeSetItem(final K key, final V value);

    public List<K> queryKeys();

    public List<CacheEntry<K, Object>> queryList();

    //---------------------- CompletableFuture 异步版 ---------------------------------
    public CompletableFuture<Boolean> existsAsync(final K key);

    public CompletableFuture<V> getAsync(final K key);

    public CompletableFuture<V> getAndRefreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> refreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> setAsync(final K key, final V value);

    public CompletableFuture<Void> setAsync(final int expireSeconds, final K key, final V value);

    public CompletableFuture<Void> setExpireSecondsAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> removeAsync(final K key);

    public CompletableFuture<Collection<V>> getCollectionAsync(final K key);

    public CompletableFuture<Long> getCollectionSizeAsync(final K key);

    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> appendListItemAsync(final K key, final V value);

    public CompletableFuture<Void> removeListItemAsync(final K key, final V value);

    public CompletableFuture<Void> appendSetItemAsync(final K key, final V value);

    public CompletableFuture<Void> removeSetItemAsync(final K key, final V value);

    public CompletableFuture<List<K>> queryKeysAsync();

    public CompletableFuture<List<CacheEntry<K, Object>>> queryListAsync();

    default CompletableFuture<Boolean> isOpenAsync() {
        return CompletableFuture.completedFuture(true);
    }

    public static enum CacheEntryType {
        OBJECT, SET, LIST;
    }

    public static final class CacheEntry<K extends Serializable, T> {

        static final String JSON_SET_KEY = "{\"cacheType\":\"" + CacheEntryType.SET + "\"";

        static final String JSON_LIST_KEY = "{\"cacheType\":\"" + CacheEntryType.LIST + "\"";

        final CacheEntryType cacheType;

        final K key;

        //<=0表示永久保存
        int expireSeconds;

        volatile int lastAccessed; //最后刷新时间

        T objectValue;

        CopyOnWriteArraySet<T> setValue;

        ConcurrentLinkedQueue<T> listValue;

        public CacheEntry(CacheEntryType cacheType, K key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
            this(cacheType, 0, key, objectValue, setValue, listValue);
        }

        public CacheEntry(CacheEntryType cacheType, int expireSeconds, K key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
            this(cacheType, expireSeconds, (int) (System.currentTimeMillis() / 1000), key, objectValue, setValue, listValue);
        }

        @ConstructorProperties({"cacheType", "expireSeconds", "lastAccessed", "key", "objectValue", "setValue", "listValue"})
        public CacheEntry(CacheEntryType cacheType, int expireSeconds, int lastAccessed, K key, T objectValue, CopyOnWriteArraySet<T> setValue, ConcurrentLinkedQueue<T> listValue) {
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

        public K getKey() {
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
