/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.nio.channels.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public interface CacheSource {

    default boolean isOpen() {
        return true;
    }

    public boolean exists(final Serializable key);

    public <T> T get(final Serializable key);

    public <T> T getAndRefresh(final Serializable key);

    public void refresh(final Serializable key);

    public <T> void set(final Serializable key, final T value);

    public <T> void set(final int expireSeconds, final Serializable key, final T value);

    public void setExpireSeconds(final Serializable key, final int expireSeconds);

    public void remove(final Serializable key);

    public <T> void appendListItem(final Serializable key, final T value);

    public <T> void removeListItem(final Serializable key, final T value);

    public <T> void appendSetItem(final Serializable key, final T value);

    public <T> void removeSetItem(final Serializable key, final T value);

    //----------------------异步版---------------------------------
    public void exists(final CompletionHandler<Boolean, Serializable> handler, final Serializable key);

    public <T> void get(final CompletionHandler<T, Serializable> handler, final Serializable key);

    public <T> void getAndRefresh(final CompletionHandler<T, Serializable> handler, final Serializable key);

    public <T> void refresh(final CompletionHandler<Void, Serializable> handler, final Serializable key);

    public <T> void set(final CompletionHandler<Void, Serializable> handler, final Serializable key, final T value);

    public <T> void set(final CompletionHandler<Void, Serializable> handler, final int expireSeconds, final Serializable key, final T value);

    public void setExpireSeconds(final CompletionHandler<Void, Serializable> handler, final Serializable key, final int expireSeconds);

    public void remove(final CompletionHandler<Void, Serializable> handler, final Serializable key);

    public <T> void appendListItem(final CompletionHandler<Void, Serializable> handler, final Serializable key, final T value);

    public <T> void removeListItem(final CompletionHandler<Void, Serializable> handler, final Serializable key, final T value);

    public <T> void appendSetItem(final CompletionHandler<Void, Serializable> handler, final Serializable key, final T value);

    public <T> void removeSetItem(final CompletionHandler<Void, Serializable> handler, final Serializable key, final T value);

    default void isOpen(final CompletionHandler<Boolean, Void> handler) {
        if (handler != null) handler.completed(Boolean.TRUE, null);
    }
}
