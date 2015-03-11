/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.util.Sheet;
import com.wentch.redkale.util.Reproduce;
import com.wentch.redkale.util.Creator;
import com.wentch.redkale.util.Attribute;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import javax.persistence.Transient;

/**
 *
 * @author zhangjx
 */
final class EntityCache<T> {

    private static final Logger logger = Logger.getLogger(EntityCache.class.getName());

    private final ConcurrentHashMap<Serializable, T> map = new ConcurrentHashMap();

    private final CopyOnWriteArrayList<T> list = new CopyOnWriteArrayList();

    private final Class<T> type;

    private final boolean needcopy;

    private final Creator<T> creator;

    private final Attribute<T, Object> primary;

    private final Map<String, Attribute<T, ?>> attributes;

    private final Reproduce<T, T> reproduce;

    private boolean fullloaded;

    public EntityCache(final Class<T> type, Creator<T> creator,
            Attribute<T, Object> primary, Map<String, Attribute<T, ?>> attributes) {
        this.type = type;
        this.creator = creator;
        this.primary = primary;
        this.attributes = attributes;
        this.needcopy = true;
        this.reproduce = Reproduce.create(type, type, (m) -> {
            char[] mn = m.getName().substring(3).toCharArray();
            if (mn.length < 2 || Character.isLowerCase(mn[1])) mn[0] = Character.toLowerCase(mn[0]);
            try {
                Field f = type.getDeclaredField(new String(mn));
                return f.getAnnotation(Transient.class) != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void fullLoad(List<T> all) {
        clear();
        all.stream().filter(x -> x != null).forEach(x -> this.map.put((Serializable) this.primary.get(x), x));
        this.list.addAll(all);
        this.fullloaded = true;
    }

    public Class<T> getType() {
        return type;
    }

    public void clear() {
        this.fullloaded = false;
        this.list.clear();
        this.map.clear();
    }

    public boolean isFullLoaded() {
        return fullloaded;
    }

    public T find(Serializable id) {
        if (id == null) return null;
        T rs = map.get(id);
        return rs == null ? null : (needcopy ? reproduce.copy(this.creator.create(), rs) : rs);
    }

    public T find(final Predicate<T> filter) {
        if (filter == null) return null;
        Optional<T> rs = listStream().filter(filter).findFirst();
        return rs.isPresent() ? (needcopy ? reproduce.copy(this.creator.create(), rs.get()) : rs.get()) : null;
    }

    public List<T> queryList(final SelectColumn selects, final Predicate<T> filter, final Comparator<T> sort) {
        boolean parallel = isParallel();
        final List<T> rs = parallel ? new CopyOnWriteArrayList<>() : new ArrayList<>();
        Stream<T> stream = listStream();
        if (filter != null) stream = stream.filter(filter);
        if (sort != null) stream = stream.sorted(sort);
        if (selects == null) {
            stream.forEach(x -> rs.add(needcopy ? reproduce.copy(creator.create(), x) : x));
        } else {
            final Map<String, Attribute<T, ?>> attrs = this.attributes;
            stream.forEach(x -> {
                final T item = creator.create();
                for (Map.Entry<String, Attribute<T, ?>> en : attrs.entrySet()) {
                    Attribute attr = en.getValue();
                    if (selects.validate(en.getKey())) attr.set(item, attr.get(x));
                }
                rs.add(item);
            });
        }
        return parallel ? new ArrayList<>(rs) : rs;
    }

    public Sheet<T> querySheet(final SelectColumn selects, final Predicate<T> filter, final Flipper flipper, final Comparator<T> sort) {
        Stream<T> stream = listStream();
        if (filter != null) stream = stream.filter(filter);
        long total = stream.count();
        if (total == 0) return new Sheet<>();
        stream = listStream();
        if (filter != null) stream = stream.filter(filter);
        if (sort != null) stream = stream.sorted(sort);
        if (flipper != null) {
            stream = stream.skip(flipper.index()).limit(flipper.getSize());
        }
        boolean parallel = isParallel();
        final List<T> rs = parallel ? new CopyOnWriteArrayList<>() : new ArrayList<>();
        if (selects == null) {
            stream.forEach(x -> rs.add(needcopy ? reproduce.copy(creator.create(), x) : x));
        } else {
            final Map<String, Attribute<T, ?>> attrs = this.attributes;
            stream.forEach(x -> {
                final T item = creator.create();
                for (Map.Entry<String, Attribute<T, ?>> en : attrs.entrySet()) {
                    Attribute attr = en.getValue();
                    if (selects.validate(en.getKey())) attr.set(item, attr.get(x));
                }
                rs.add(item);
            });
        }
        return new Sheet<>(total, parallel ? new ArrayList<>(rs) : rs);
    }

    public void insert(T value) {
        if (value == null) return;
        T rs = needcopy ? reproduce.copy(this.creator.create(), value) : value;  //确保同一主键值的map与list中的对象必须共用。
        T old = this.map.put((Serializable) this.primary.get(rs), rs);
        if (old != null) logger.log(Level.WARNING, "cache repeat insert data: " + value);
        this.list.add(rs);
    }

    public void delete(final Serializable id) {
        if (id == null) return;
        T rs = this.map.remove(id);
        if (rs != null) this.list.remove(rs);
    }

    public Serializable[] delete(final Predicate<T> filter) {
        if (filter == null || this.list.isEmpty()) return new Serializable[0];
        Object[] rms = listStream().filter(filter).toArray();
        Serializable[] ids = new Serializable[rms.length];
        int i = -1;
        for (Object o : rms) {
            T t = (T) o;
            ids[++i] = (Serializable) this.primary.get(t);
            this.map.remove(ids[i]);
            this.list.remove(t);
        }
        return ids;
    }

    public void update(final T value) {
        if (value == null) return;
        T rs = this.map.get((Serializable) this.primary.get(value));
        if (rs == null) return;
        this.reproduce.copy(rs, value);
    }

    public void update(final T value, Attribute<T, ?>[] attrs) {
        if (value == null) return;
        T rs = this.map.get((Serializable) this.primary.get(value));
        if (rs == null) return;
        for (Attribute attr : attrs) {
            attr.set(rs, attr.get(value));
        }
    }

    public <V> T update(final Serializable id, Attribute<T, V> attr, final V fieldValue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs != null) attr.set(rs, fieldValue);
        return rs;
    }

    public boolean isParallel() {
        return this.list.size() > 1024 * 16;
    }

    private Stream<T> listStream() {
        return isParallel() ? this.list.parallelStream() : this.list.stream();
    }
}
