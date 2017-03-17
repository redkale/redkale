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

    //----------------------异步版---------------------------------
    public boolean exists(final AsyncHandler<Boolean, K> handler, final K key);

    public V get(final AsyncHandler<V, K> handler, final K key);

    public V getAndRefresh(final AsyncHandler<V, K> handler, final K key, final int expireSeconds);

    public void refresh(final AsyncHandler<Void, K> handler, final K key, final int expireSeconds);

    public void set(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void set(final AsyncHandler<Void, K> handler, final int expireSeconds, final K key, final V value);

    public void setExpireSeconds(final AsyncHandler<Void, K> handler, final K key, final int expireSeconds);

    public void remove(final AsyncHandler<Void, K> handler, final K key);

    public Collection<V> getCollection(final AsyncHandler<Collection<V>, K> handler, final K key);

    public Collection<V> getCollectionAndRefresh(final AsyncHandler<Collection<V>, K> handler, final K key, final int expireSeconds);

    public void appendListItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void removeListItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void appendSetItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    public void removeSetItem(final AsyncHandler<Void, K> handler, final K key, final V value);

    default boolean isOpen(final AsyncHandler<Boolean, Void> handler) {
        if (handler != null) handler.completed(Boolean.TRUE, null);
        return true;
    }
}
