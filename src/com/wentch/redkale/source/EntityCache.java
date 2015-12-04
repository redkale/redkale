/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import javax.persistence.Transient;
import com.wentch.redkale.util.*;
import com.wentch.redkale.source.DataSource.Reckon;
import static com.wentch.redkale.source.DataSource.Reckon.*;
import java.util.stream.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
public final class EntityCache<T> {

    private static final Logger logger = Logger.getLogger(EntityCache.class.getName());

    private final ConcurrentHashMap<Serializable, T> map = new ConcurrentHashMap();

    private final Collection<T> list = new ConcurrentLinkedQueue(); // CopyOnWriteArrayList 插入慢、查询快; 10w数据插入需要3.2秒; ConcurrentLinkedQueue 插入快、查询慢；10w数据查询需要 0.062秒，  查询慢40%;

    private final Map<String, Comparator<T>> sortComparators = new ConcurrentHashMap<>();

    private final Class<T> type;

    private final boolean needcopy;

    private final Creator<T> creator;

    private final Attribute<T, Serializable> primary;

    private final Reproduce<T, T> reproduce;

    private boolean fullloaded;

    final EntityInfo<T> info;

    public EntityCache(final EntityInfo<T> info) {
        this.info = info;
        this.type = info.getType();
        this.creator = info.getCreator();
        this.primary = info.primary;
        this.needcopy = true;
        this.reproduce = Reproduce.create(type, type, (m) -> {
            try {
                return type.getDeclaredField(m).getAnnotation(Transient.class) == null;
            } catch (Exception e) {
                return true;
            }
        });
    }

    public void fullLoad(List<T> all) {
        if (all == null) return;
        clear();
        all.stream().filter(x -> x != null).forEach(x -> {
            this.map.put(this.primary.get(x), x);
        });
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

    public T find(final SelectColumn selects, final Serializable id) {
        if (id == null) return null;
        T rs = map.get(id);
        if (rs == null) return null;
        if (selects == null) return (needcopy ? reproduce.copy(this.creator.create(), rs) : rs);
        T t = this.creator.create();
        for (Attribute attr : this.info.attributes) {
            if (selects.test(attr.field())) attr.set(t, attr.get(rs));
        }
        return t;
    }

    public T find(final SelectColumn selects, FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) stream = stream.filter(filter);
        Optional<T> opt = stream.findFirst();
        if (!opt.isPresent()) return null;
        if (selects == null) return (needcopy ? reproduce.copy(this.creator.create(), opt.get()) : opt.get());
        T rs = opt.get();
        T t = this.creator.create();
        for (Attribute attr : this.info.attributes) {
            if (selects.test(attr.field())) attr.set(t, attr.get(rs));
        }
        return t;
    }

    public boolean exists(Serializable id) {
        if (id == null) return false;
        final Class atype = this.primary.type();
        if (id.getClass() != atype && id instanceof Number) {
            if (atype == int.class || atype == Integer.class) {
                id = ((Number) id).intValue();
            } else if (atype == long.class || atype == Long.class) {
                id = ((Number) id).longValue();
            } else if (atype == short.class || atype == Short.class) {
                id = ((Number) id).shortValue();
            } else if (atype == float.class || atype == Float.class) {
                id = ((Number) id).floatValue();
            } else if (atype == byte.class || atype == Byte.class) {
                id = ((Number) id).byteValue();
            } else if (atype == double.class || atype == Double.class) {
                id = ((Number) id).doubleValue();
            }
        }
        return this.map.containsKey(id);
    }

    public boolean exists(FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) stream = stream.filter(filter);
        return stream.findFirst().isPresent();
    }

    public boolean exists(final Predicate<T> filter) {
        return (filter != null) && this.list.stream().filter(filter).findFirst().isPresent();
    }

    public <K, V> Map<Serializable, Number> getMapResult(final String keyColumn, final Reckon reckon, final String reckonColumn, FilterNode node) {
        final Attribute<T, Serializable> keyAttr = info.getAttribute(keyColumn);
        final Predicate filter = node == null ? null : node.createPredicate(this);
        final Attribute reckonAttr = reckonColumn == null ? null : info.getAttribute(reckonColumn);
        Stream<T> stream = this.list.stream();
        if (filter != null) stream = stream.filter(filter);
        Collector<T, Map, ?> collector = null;
        final Class valtype = reckonAttr == null ? null : reckonAttr.type();
        switch (reckon) {
            case AVG:
                if (valtype == float.class || valtype == Float.class || valtype == double.class || valtype == Double.class) {
                    collector = (Collector<T, Map, ?>) Collectors.averagingDouble((T t) -> ((Number) reckonAttr.get(t)).doubleValue());
                } else {
                    collector = (Collector<T, Map, ?>) Collectors.averagingLong((T t) -> ((Number) reckonAttr.get(t)).longValue());
                }
                break;
            case COUNT: collector = (Collector<T, Map, ?>) Collectors.counting();
                break;
            case DISTINCTCOUNT:
                collector = (Collector<T, Map, ?>) Collectors.mapping((t) -> reckonAttr.get(t), Collectors.toSet());
                break;
            case MAX:
            case MIN:
                Comparator<T> comp = (o1, o2) -> o1 == null ? (o2 == null ? 0 : -1) : ((Comparable) reckonAttr.get(o1)).compareTo(reckonAttr.get(o2));
                collector = (Collector<T, Map, ?>) ((reckon == MAX) ? Collectors.maxBy(comp) : Collectors.minBy(comp));
                break;
            case SUM:
                if (valtype == float.class || valtype == Float.class || valtype == double.class || valtype == Double.class) {
                    collector = (Collector<T, Map, ?>) Collectors.summingDouble((T t) -> ((Number) reckonAttr.get(t)).doubleValue());
                } else {
                    collector = (Collector<T, Map, ?>) Collectors.summingLong((T t) -> ((Number) reckonAttr.get(t)).longValue());
                }
                break;
        }
        Map rs = stream.collect(Collectors.groupingBy(t -> keyAttr.get(t), LinkedHashMap::new, collector));
        if (reckon == MAX || reckon == MIN) {
            Map rs2 = new LinkedHashMap();
            rs.forEach((x, y) -> {
                if (((Optional) y).isPresent()) rs2.put(x, reckonAttr.get((T) ((Optional) y).get()));
            });
            rs = rs2;
        } else if (reckon == DISTINCTCOUNT) {
            Map rs2 = new LinkedHashMap();
            rs.forEach((x, y) -> rs2.put(x, ((Set) y).size()));
            rs = rs2;
        }
        return rs;
    }

    public <V> Number getNumberResult(final Reckon reckon, final String column, FilterNode node) {
        final Attribute<T, Serializable> attr = column == null ? null : info.getAttribute(column);
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) stream = stream.filter(filter);
        switch (reckon) {
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
                throw new RuntimeException("getNumberResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());
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
                throw new RuntimeException("getNumberResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());

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
                throw new RuntimeException("getNumberResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());

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
                throw new RuntimeException("getNumberResult error(type:" + type + ", attr.declaringClass: " + attr.declaringClass() + ", attr.field: " + attr.field() + ", attr.type: " + attr.type());
        }
        return -1;
    }

    public Sheet<T> querySheet(final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, selects, flipper, node);
    }

    public Sheet<T> querySheet(final boolean needtotal, final SelectColumn selects, final Flipper flipper, FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        final Comparator<T> comparator = createComparator(flipper);
        long total = 0;
        if (needtotal) {
            Stream<T> stream = this.list.stream();
            if (filter != null) stream = stream.filter(filter);
            total = stream.count();
        }
        if (needtotal && total == 0) return new Sheet<>();
        Stream<T> stream = this.list.stream();
        if (filter != null) stream = stream.filter(filter);
        if (comparator != null) stream = stream.sorted(comparator);
        if (flipper != null) stream = stream.skip(flipper.index()).limit(flipper.getSize());
        final List<T> rs = new ArrayList<>();
        if (selects == null) {
            Consumer<? super T> action = x -> rs.add(needcopy ? reproduce.copy(creator.create(), x) : x);
            if (comparator != null) {
                stream.forEachOrdered(action);
            } else {
                stream.forEach(action);
            }
        } else {
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            info.forEachAttribute((k, v) -> {
                if (selects.test(k)) attrs.add(v);
            });
            Consumer<? super T> action = x -> {
                final T item = creator.create();
                for (Attribute attr : attrs) {
                    attr.set(item, attr.get(x));
                }
                rs.add(item);
            };
            if (comparator != null) {
                stream.forEachOrdered(action);
            } else {
                stream.forEach(action);
            }
        }
        if (!needtotal) total = rs.size();
        return new Sheet<>(total, rs);
    }

    public void insert(T value) {
        if (value == null) return;
        final T rs = reproduce.copy(this.creator.create(), value);  //确保同一主键值的map与list中的对象必须共用。
        T old = this.map.put(this.primary.get(rs), rs);
        if (old == null) {
            this.list.add(rs);
        } else {
            logger.log(Level.WARNING, "cache repeat insert data: " + value);
        }
    }

    public void delete(final Serializable id) {
        if (id == null) return;
        final T rs = this.map.remove(id);
        if (rs != null) this.list.remove(rs);
    }

    public Serializable[] delete(final FilterNode node) {
        if (node == null || this.list.isEmpty()) return new Serializable[0];
        Object[] rms = this.list.stream().filter(node.createPredicate(this)).toArray();
        Serializable[] ids = new Serializable[rms.length];
        int i = -1;
        for (Object o : rms) {
            final T t = (T) o;
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

    public T update(final T value, Collection<Attribute<T, Serializable>> attrs) {
        if (value == null) return value;
        T rs = this.map.get(this.primary.get(value));
        if (rs == null) return rs;
        for (Attribute attr : attrs) {
            attr.set(rs, attr.get(value));
        }
        return rs;
    }

    public <V> T update(final Serializable id, Attribute<T, V> attr, final V fieldValue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs != null) attr.set(rs, fieldValue);
        return rs;
    }

    public <V> T updateColumnOr(final Serializable id, Attribute<T, V> attr, final long orvalue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs == null) return rs;
        Number numb = (Number) attr.get(rs);
        return updateColumnIncrAndOr(attr, rs, (numb == null) ? orvalue : (numb.longValue() | orvalue));
    }

    public <V> T updateColumnAnd(final Serializable id, Attribute<T, V> attr, final long andvalue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs == null) return rs;
        Number numb = (Number) attr.get(rs);
        return updateColumnIncrAndOr(attr, rs, (numb == null) ? 0 : (numb.longValue() & andvalue));
    }

    public <V> T updateColumnIncrement(final Serializable id, Attribute<T, V> attr, final long incvalue) {
        if (id == null) return null;
        T rs = this.map.get(id);
        if (rs == null) return rs;
        Number numb = (Number) attr.get(rs);
        return updateColumnIncrAndOr(attr, rs, (numb == null) ? incvalue : (numb.longValue() + incvalue));
    }

    private <V> T updateColumnIncrAndOr(Attribute<T, V> attr, final T rs, Number numb) {
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

    public Attribute<T, Serializable> getAttribute(String fieldname) {
        return info.getAttribute(fieldname);
    }

    //-------------------------------------------------------------------------------------------------------------------------------
    protected Comparator<T> createComparator(Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return null;
        final String sort = flipper.getSort();
        Comparator<T> comparator = this.sortComparators.get(sort);
        if (comparator != null) return comparator;
        for (String item : sort.split(",")) {
            if (item.trim().isEmpty()) continue;
            String[] sub = item.trim().split("\\s+");
            int pos = sub[0].indexOf('(');
            Attribute<T, Serializable> attr;
            if (pos <= 0) {
                attr = getAttribute(sub[0]);
            } else {  //含SQL函数
                int pos2 = sub[0].lastIndexOf(')');
                final Attribute<T, Serializable> pattr = getAttribute(sub[0].substring(pos + 1, pos2));
                final String func = sub[0].substring(0, pos);
                if ("ABS".equalsIgnoreCase(func)) {
                    if (pattr.type() == int.class || pattr.type() == Integer.class) {
                        attr = new Attribute<T, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(T obj) {
                                return Math.abs(((Number) pattr.get(obj)).intValue());
                            }

                            @Override
                            public void set(T obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == long.class || pattr.type() == Long.class) {
                        attr = new Attribute<T, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(T obj) {
                                return Math.abs(((Number) pattr.get(obj)).longValue());
                            }

                            @Override
                            public void set(T obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == float.class || pattr.type() == Float.class) {
                        attr = new Attribute<T, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(T obj) {
                                return Math.abs(((Number) pattr.get(obj)).floatValue());
                            }

                            @Override
                            public void set(T obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == double.class || pattr.type() == Double.class) {
                        attr = new Attribute<T, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(T obj) {
                                return Math.abs(((Number) pattr.get(obj)).doubleValue());
                            }

                            @Override
                            public void set(T obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else {
                        throw new RuntimeException("Flipper not supported sort illegal type by ABS (" + flipper.getSort() + ")");
                    }
                } else if (func.isEmpty()) {
                    attr = pattr;
                } else {
                    throw new RuntimeException("Flipper not supported sort illegal function (" + flipper.getSort() + ")");
                }
            }
            Comparator<T> c = (sub.length > 1 && sub[1].equalsIgnoreCase("DESC")) ? (T o1, T o2) -> {
                Comparable c1 = (Comparable) attr.get(o1);
                Comparable c2 = (Comparable) attr.get(o2);
                return c2 == null ? -1 : c2.compareTo(c1);
            } : (T o1, T o2) -> {
                Comparable c1 = (Comparable) attr.get(o1);
                Comparable c2 = (Comparable) attr.get(o2);
                return c1 == null ? -1 : c1.compareTo(c2);
            };

            if (comparator == null) {
                comparator = c;
            } else {
                comparator = comparator.thenComparing(c);
            }
        }
        this.sortComparators.put(sort, comparator);
        return comparator;
    }

    private static class UniqueSequence implements Serializable {

        private final Serializable[] value;

        public UniqueSequence(Serializable[] val) {
            this.value = val;
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(this.value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final UniqueSequence other = (UniqueSequence) obj;
            if (value.length != other.value.length) return false;
            for (int i = 0; i < value.length; i++) {
                if (!value[i].equals(other.value[i])) return false;
            }
            return true;
        }

    }

    private static interface UniqueAttribute<T> extends Predicate<FilterNode> {

        public Serializable getValue(T bean);

        @Override
        public boolean test(FilterNode node);

        public static <T> UniqueAttribute<T> create(final Attribute<T, Serializable>[] attributes) {
            if (attributes.length == 1) {
                final Attribute<T, Serializable> attribute = attributes[0];
                return new UniqueAttribute<T>() {

                    @Override
                    public Serializable getValue(T bean) {
                        return attribute.get(bean);
                    }

                    @Override
                    public boolean test(FilterNode node) {
                        if (node == null || node.isOr()) return false;
                        if (!attribute.field().equals(node.column)) return false;
                        if (node.nodes == null) return true;
                        for (FilterNode n : node.nodes) {
                            if (!test(n)) return false;
                        }
                        return true;
                    }
                };
            } else {
                return new UniqueAttribute<T>() {

                    @Override
                    public Serializable getValue(T bean) {
                        final Serializable[] rs = new Serializable[attributes.length];
                        for (int i = 0; i < rs.length; i++) {
                            rs[i] = attributes[i].get(bean);
                        }
                        return new UniqueSequence(rs);
                    }

                    @Override
                    public boolean test(FilterNode node) {
                        return true;
                    }
                };
            }
        }
    }

}
