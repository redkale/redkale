/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.util.*;
import org.redkale.util.*;

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

    public Collection<V> getCollectionAndRefresh(final K key, final int expireSeconds);

    public void appendListItem(final K key, final V value);

    public void removeListItem(final K key, final V value);

    public void appendSetItem(final K key, final V value);

    public void removeSetItem(final K key, final V value);

    //---------------------- CompletableFuture 异步版 ---------------------------------
    /**
    public CompletableFuture<Boolean> existsAsync(final K key);

    public CompletableFuture<V> getAsync(final K key);

    public CompletableFuture<V> getAndRefreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> refreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> setAsync(final K key, final V value);

    public CompletableFuture<Void> setAsync(final int expireSeconds, final K key, final V value);

    public CompletableFuture<Void> setExpireSecondsAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> removeAsync(final K key);

    public CompletableFuture<Collection<V>> getCollectionAsync(final K key);

    public CompletableFuture<Collection<V>> getCollectionAndRefreshAsync(final K key, final int expireSeconds);

    public CompletableFuture<Void> appendListItemAsync(final K key, final V value);

    public CompletableFuture<Void> removeListItemAsync(final K key, final V value);

    public CompletableFuture<Void> appendSetItemAsync(final K key, final V value);

    public CompletableFuture<Void> removeSetItemAsync(final K key, final V value);

    default CompletableFuture<Boolean> isOpenAsync() {
        CompletableFuture<Boolean> future = new CompletableFuture();
        future.complete(true);
        return future;
    }
    */
    //---------------------- AsyncHandler 异步版 ---------------------------------
    public void exists(final AsyncHandler<Boolean, K> handler, final K key);

    public void get(final AsyncHandler<V, K> handler, final K key);

    public void getAndRefresh(final AsyncHandler<V, K> handler, final K key, final int expireSeconds);

    public void refresh(final AsyncHandler<Void, K> handler, final K key, final int expireSeconds);

    public void set(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void set(final AsyncHandler<Void, K> handler, final int expireSeconds, final K key, final V value);

    public void setExpireSeconds(final AsyncHandler<Void, K> handler, final K key, final int expireSeconds);

    public void remove(final AsyncHandler<Void, K> handler, final K key);

    public void getCollection(final AsyncHandler<Collection<V>, K> handler, final K key);

    public void getCollectionAndRefresh(final AsyncHandler<Collection<V>, K> handler, final K key, final int expireSeconds);

    public void appendListItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void removeListItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void appendSetItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void removeSetItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    default void isOpen(final AsyncHandler<Boolean, Void> handler) {
        if (handler != null) handler.completed(Boolean.TRUE, null);
    }
}
