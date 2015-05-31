/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.util.*;
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

    private final Attribute<T, Serializable> primary;

    //key是field的name
    private final Map<String, Attribute<T, Serializable>> attributes;

    private final Reproduce<T, T> reproduce;

    private boolean fullloaded;

    public EntityCache(final Class<T> type, Creator<T> creator,
        Attribute<T, Serializable> primary, Map<String, Attribute<T, Serializable>> attributes) {
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
        all.stream().filter(x -> x != null).forEach(x -> this.map.put(this.primary.get(x), x));
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

    public <V> Number getSingleResult(final ReckonType type, final Attribute<T, V> attr, final Predicate<T> filter) {
        Stream<T> stream = listStream();
        if (filter != null) stream = stream.filter(filter);
        switch (type) {
            case AVG:
                if (attr.type() == int.class || attr.type() == Integer.class) {
                    return (int) stream.mapToInt(x -> (Integer) attr.get(x)).average().orElse(0);
                } else if (attr.type() == long.class || attr.type() == Long.class) {
                    return (long) stream.mapToLong(x -> (Long) attr.get(x)).average().orElse(0);
                } else if (attr.type() == short.class || attr.type() == Short.class) {
                    return (short) stream.mapToInt(x -> ((Short) attr.get(x)).intValue()).average().orElse(0);
                } else if (attr.type() == float.class || attr.type() == Float.class) {
                    return (float) stream.mapToDouble(x -> ((Float) attr.get(x)).doubleValue()).average().orElse(0);
                } else if (attr.type() == double.class || attr.type() == Double.class) {
                    return stream.mapToDouble(x -> (Double) attr.get(x)).average().orElse(0);
                }
                throw new RuntimeException("getSingleResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());
            case COUNT: return stream.count();
            case DISTINCTCOUNT: return stream.map(x -> attr.get(x)).distinct().count();

            case MAX:
                if (attr.type() == int.class || attr.type() == Integer.class) {
                    return stream.mapToInt(x -> (Integer) attr.get(x)).max().orElse(0);
                } else if (attr.type() == long.class || attr.type() == Long.class) {
                    return stream.mapToLong(x -> (Long) attr.get(x)).max().orElse(0);
                } else if (attr.type() == short.class || attr.type() == Short.class) {
                    return (short) stream.mapToInt(x -> ((Short) attr.get(x)).intValue()).max().orElse(0);
                } else if (attr.type() == float.class || attr.type() == Float.class) {
                    return (float) stream.mapToDouble(x -> ((Float) attr.get(x)).doubleValue()).max().orElse(0);
                } else if (attr.type() == double.class || attr.type() == Double.class) {
                    return stream.mapToDouble(x -> (Double) attr.get(x)).max().orElse(0);
                }
                throw new RuntimeException("getSingleResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());

            case MIN:
                if (attr.type() == int.class || attr.type() == Integer.class) {
                    return stream.mapToInt(x -> (Integer) attr.get(x)).min().orElse(0);
                } else if (attr.type() == long.class || attr.type() == Long.class) {
                    return stream.mapToLong(x -> (Long) attr.get(x)).min().orElse(0);
                } else if (attr.type() == short.class || attr.type() == Short.class) {
                    return (short) stream.mapToInt(x -> ((Short) attr.get(x)).intValue()).min().orElse(0);
                } else if (attr.type() == float.class || attr.type() == Float.class) {
                    return (float) stream.mapToDouble(x -> ((Float) attr.get(x)).doubleValue()).min().orElse(0);
                } else if (attr.type() == double.class || attr.type() == Double.class) {
                    return stream.mapToDouble(x -> (Double) attr.get(x)).min().orElse(0);
                }
                throw new RuntimeException("getSingleResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());

            case SUM:
                if (attr.type() == int.class || attr.type() == Integer.class) {
                    return stream.mapToInt(x -> (Integer) attr.get(x)).sum();
                } else if (attr.type() == long.class || attr.type() == Long.class) {
                    return stream.mapToLong(x -> (Long) attr.get(x)).sum();
                } else if (attr.type() == short.class || attr.type() == Short.class) {
                    return (short) stream.mapToInt(x -> ((Short) attr.get(x)).intValue()).sum();
                } else if (attr.type() == float.class || attr.type() == Float.class) {
                    return (float) stream.mapToDouble(x -> ((Float) attr.get(x)).doubleValue()).sum();
                } else if (attr.type() == double.class || attr.type() == Double.class) {
                    return stream.mapToDouble(x -> (Double) attr.get(x)).sum();
                }
                throw new RuntimeException("getSingleResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());
        }
        return -1;
    }

    public Collection<T> queryCollection(final boolean set, final SelectColumn selects, final Predicate<T> filter, final Comparator<T> sort) {
        final boolean parallel = isParallel();
        final Collection<T> rs = parallel ? (set ? new CopyOnWriteArraySet<>() : new CopyOnWriteArrayList<>()) : (set ? new LinkedHashSet<>() : new ArrayList<>());
        Stream<T> stream = listStream();
        if (filter != null) stream = stream.filter(filter);
        if (sort != null) stream = stream.sorted(sort);
        if (selects == null) {
            stream.forEach(x -> rs.add(needcopy ? reproduce.copy(creator.create(), x) : x));
        } else {
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            for (Map.Entry<String, Attribute<T, Serializable>> en : this.attributes.entrySet()) {
                if (selects.validate(en.getKey())) attrs.add(en.getValue());
            }
            stream.forEach(x -> {
                final T item = creator.create();
                for (Attribute attr : attrs) {
                    attr.set(item, attr.get(x));
                }
                rs.add(item);
            });
        }
        return parallel ? (set ? new LinkedHashSet<>(rs) : new ArrayList<>(rs)) : rs;
    }

    public Set<T> querySet(final SelectColumn selects, final Predicate<T> filter, final Comparator<T> sort) {
        return (Set<T>) queryCollection(true, selects, filter, sort);
    }

    public List<T> queryList(final SelectColumn selects, final Predicate<T> filter, final Comparator<T> sort) {
        return (List<T>) queryCollection(false, selects, filter, sort);
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
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            for (Map.Entry<String, Attribute<T, Serializable>> en : this.attributes.entrySet()) {
                if (selects.validate(en.getKey())) attrs.add(en.getValue());
            }
            stream.forEach(x -> {
                final T item = creator.create();
                for (Attribute attr : attrs) {
                    attr.set(item, attr.get(x));
                }
                rs.add(item);
            });
        }
        return new Sheet<>(total, parallel ? new ArrayList<>(rs) : rs);
    }

    public void insert(T value) {
        if (value == null) return;
        T rs = reproduce.copy(this.creator.create(), value);  //确保同一主键值的map与list中的对象必须共用。
        T old = this.map.put(this.primary.get(rs), rs);
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
            ids[++i] = this.primary.get(t);
            this.map.remove(ids[i]);
            this.list.remove(t);
        }
        return ids;
    }

    public void update(final T value) {
        if (value == null) return;
        T rs = this.map.get(this.primary.get(value));
        if (rs == null) return;
        this.reproduce.copy(rs, value);
    }

    public void update(final T value, Attribute<T, Serializable>[] attrs) {
        if (value == null) return;
        T rs = this.map.get(this.primary.get(value));
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

    public <V> T updateColumnIncrement(final Serializable id, Attribute<T, V> attr, final long incvalue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs == null) return rs;
        Number numb = (Number) attr.get(rs);
        if (numb == null) {
            numb = incvalue;
        } else {
            numb = numb.longValue() + incvalue;
        }
        final Class ft = attr.type();
        if (ft == int.class || ft == Integer.class) {
            numb = numb.intValue();
        } else if (ft == long.class || ft == Long.class) {
            numb = numb.longValue();
        } else if (ft == short.class || ft == Short.class) {
            numb = numb.shortValue();
        } else if (ft == float.class || ft == Float.class) {
            numb = numb.floatValue();
        } else if (ft == double.class || ft == Double.class) {
            numb = numb.doubleValue();
        } else if (ft == byte.class || ft == Byte.class) {
            numb = numb.byteValue();
        }
        attr.set(rs, (V) numb);
        return rs;
    }

    public boolean isParallel() {
        return this.list.size() > 1024 * 16;
    }

    private Stream<T> listStream() {
        return isParallel() ? this.list.parallelStream() : this.list.stream();
    }
}
