/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.nio.channels.*;
import java.util.*;

/**
 *
 * @param <K> key的类型
 * @param <V> value的类型
 * <p>
 * 详情见: http://www.redkale.org
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

    //----------------------异步版---------------------------------
    public void exists(final CompletionHandler<Boolean, K> handler, final K key);

    public void get(final CompletionHandler<V, K> handler, final K key);

    public void getAndRefresh(final CompletionHandler<V, K> handler, final K key, final int expireSeconds);

    public void refresh(final CompletionHandler<Void, K> handler, final K key, final int expireSeconds);

    public void set(final CompletionHandler<Void, K> handler, final K key, final V value);

    public void set(final CompletionHandler<Void, K> handler, final int expireSeconds, final K key, final V value);

    public void setExpireSeconds(final CompletionHandler<Void, K> handler, final K key, final int expireSeconds);

    public void remove(final CompletionHandler<Void, K> handler, final K key);

    public void getCollection(final CompletionHandler<Collection<V>, K> handler, final K key);

    public void getCollectionAndRefresh(final CompletionHandler<Collection<V>, K> handler, final K key, final int expireSeconds);

    public void appendListItem(final CompletionHandler<Void, K> handler, final K key, final V value);

    public void removeListItem(final CompletionHandler<Void, K> handler, final K key, final V value);

    public void appendSetItem(final CompletionHandler<Void, K> handler, final K key, final V value);

    public void removeSetItem(final CompletionHandler<Void, K> handler, final K key, final V value);

    default void isOpen(final CompletionHandler<Boolean, Void> handler) {
        if (handler != null) handler.completed(Boolean.TRUE, null);
    }
}
