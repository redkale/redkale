/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;

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

    public <T> T refreshAndGet(final Serializable key);

    public void refresh(final Serializable key);

    public <T> void set(final Serializable key, final T value);

    public <T> void set(final int expireSeconds, final Serializable key, final T value);

    public void remove(final Serializable key);

    public <V> void appendListItem(final Serializable key, final V value);

    public <V> void removeListItem(final Serializable key, final V value);

    public <V> void appendSetItem(final Serializable key, final V value);

    public <V> void removeSetItem(final Serializable key, final V value);

}
