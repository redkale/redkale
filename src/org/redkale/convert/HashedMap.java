/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;

/**
 * 只增不减的伪Map类
 *
 * @author zhangjx
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("unchecked")
public final class HashedMap<K extends Type, V> {

    protected final transient Entry<K, V>[] table;

    public HashedMap() {
        this(128);
    }

    public HashedMap(int initCapacity) {
        this.table = new Entry[Math.max(initCapacity, 16)];
    }

    public final V get(final K key) {
        final K k = key;
        final Entry<K, V>[] data = this.table;
        Entry<K, V> entry = data[k.hashCode() & (data.length - 1)];
        while (entry != null) {
            if (k == entry.key) return entry.value;
            entry = entry.next;
        }
        return null;
    }

    public final V put(K key, V value) {
        final K k = key;
        final Entry<K, V>[] data = this.table;
        final int index = k.hashCode() & (data.length - 1);
        Entry<K, V> entry = data[index];
        while (entry != null) {
            if (k == entry.key) {
                entry.value = value;
                return entry.value;
            }
            entry = entry.next;
        }
        data[index] = new Entry(key, value, data[index]);
        return null;
    }

    protected static final class Entry<K, V> {

        protected V value;

        protected final K key;

        protected final Entry<K, V> next;

        protected Entry(K key, V value, Entry<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
