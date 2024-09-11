/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;
import org.redkale.persistence.*;
import static org.redkale.source.FilterFunc.*;
import org.redkale.util.*;

/**
 * Entity数据的缓存类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Entity类的泛型
 */
@SuppressWarnings("unchecked")
public final class EntityCache<T> {

    // 日志
    private static final Logger logger = Logger.getLogger(EntityCache.class.getName());

    // 主键与对象的键值对
    private ConcurrentHashMap<Serializable, T> map = new ConcurrentHashMap();

    // CopyOnWriteArrayList 插入慢、查询快; 10w数据插入需要3.2秒; ConcurrentLinkedQueue 插入快、查询慢；10w数据查询需要 0.062秒，  查询慢40%;
    private Collection<T> list = new ConcurrentLinkedQueue();

    // Flipper.sort转换成Comparator的缓存
    private final Map<String, Comparator<T>> sortComparators = new ConcurrentHashMap<>();

    private final ReentrantLock tableLock = new ReentrantLock();

    // Entity类
    private final Class<T> type;

    // 接口返回的对象是否需要复制一份
    private final boolean needCopy;

    // Entity构建器
    private final Creator<T> creator;

    // Entity数值构建器
    private final IntFunction<T[]> arrayer;

    // 主键字段
    private final Attribute<T, Serializable> primary;

    // 新增时的复制器， 排除了标记为&#064;Transient的字段
    private final Copier<T, T> newCopier;

    // 修改时的复制器， 排除了标记为&#064;Transient或&#064;Column(updatable=false)的字段
    private final Copier<T, T> uptCopier;

    // 是否已经全量加载过
    private volatile boolean fullloaded;

    private final AtomicBoolean loading = new AtomicBoolean();

    // Entity信息
    final EntityInfo<T> info;

    // &#064;Cacheable的定时更新秒数，为0表示不定时更新
    final int interval;

    // &#064;Cacheable的定时器
    private ScheduledThreadPoolExecutor scheduler;

    private CompletableFuture<List<T>> loadFuture;

    public EntityCache(final EntityInfo<T> info, final Entity c) {
        this(info, c != null && c.cacheable() ? c.cacheInterval() : 0, c != null && c.cacheable() && c.cacheDirect());
    }

    EntityCache(final EntityInfo<T> info, final int cacheInterval, final boolean cacheDirect) {
        this.info = info;
        this.interval = cacheInterval < 0 ? 0 : cacheInterval;
        this.type = info.getType();
        this.arrayer = info.getArrayer();
        this.creator = info.getCreator();
        this.primary = info.primary;
        org.redkale.persistence.VirtualEntity ve =
                info.getType().getAnnotation(org.redkale.persistence.VirtualEntity.class);
        boolean direct = cacheDirect;
        if (!direct) {
            direct = ve != null && ve.direct();
        }
        { // 兼容废弃类
            org.redkale.source.VirtualEntity ve2 = info.getType().getAnnotation(org.redkale.source.VirtualEntity.class);
            if (!direct && ve2 != null) {
                direct = ve2.direct();
            }
        }
        this.needCopy = !direct;
        this.newCopier = Copier.create(type, type, (e, c) -> {
            try {
                return e.getAnnotation(Transient.class) == null
                        && e.getAnnotation(javax.persistence.Transient.class) == null;
            } catch (Exception ex) {
                return true;
            }
        });
        this.uptCopier = Copier.create(type, type, (e, c) -> {
            try {
                if (e.getAnnotation(Transient.class) != null) {
                    return false;
                }
                if (e.getAnnotation(javax.persistence.Transient.class) != null) {
                    return false;
                }
                Column column = e.getAnnotation(Column.class);
                return (column == null || column.updatable());
            } catch (Exception ex) {
                return true;
            }
        });
    }

    public CompletableFuture<List<T>> fullLoadAsync() {
        if (this.fullloaded) {
            return this.loadFuture;
        }
        if (loading.getAndSet(true)) {
            return this.loadFuture;
        }
        if (info.fullLoader == null) {
            this.list = new ConcurrentLinkedQueue();
            this.map = new ConcurrentHashMap();
            this.fullloaded = true;
            loading.set(false);
            return this.loadFuture;
        }
        this.fullloaded = false;
        CompletableFuture<List> allFuture = info.fullLoader.apply(info.source, info);
        this.loadFuture = (CompletableFuture) allFuture;
        if (allFuture == null) {
            this.list = new ConcurrentLinkedQueue();
            this.map = new ConcurrentHashMap();
            this.fullloaded = true;
            loading.set(false);
            return this.loadFuture;
        }
        if (this.interval > 0 && this.scheduler == null && info.fullLoader != null) {
            this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                final Thread t = new Thread(r, "Redkale-EntityCache-" + type.getSimpleName() + "-Thread");
                t.setDaemon(true);
                return t;
            });
            this.scheduler.setRemoveOnCancelPolicy(true);
            this.scheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            ConcurrentHashMap newmap2 = new ConcurrentHashMap();
                            List<T> all2 =
                                    info.fullLoader.apply(info.source, info).join();
                            if (all2 != null) {
                                all2.stream().filter(Objects::nonNull).forEach(x -> {
                                    newmap2.put(this.primary.get(x), x);
                                });
                            }
                            this.list = all2 == null ? new ConcurrentLinkedQueue() : new ConcurrentLinkedQueue(all2);
                            this.map = newmap2;
                        } catch (Throwable t) {
                            logger.log(Level.SEVERE, type + " schedule(interval=" + interval + "s) Cacheable error", t);
                        }
                    },
                    interval - System.currentTimeMillis() / 1000 % interval,
                    interval,
                    TimeUnit.SECONDS);
        }
        allFuture.whenComplete((l, t) -> {
            if (t != null) {
                loading.set(false);
                return;
            }
            List<T> all = l;
            ConcurrentHashMap newmap = new ConcurrentHashMap();
            if (all != null) {
                all.stream().filter(Objects::nonNull).forEach(x -> {
                    newmap.put(this.primary.get(x), x);
                });
            }
            this.list = new ConcurrentLinkedQueue(all);
            this.map = newmap;
            this.fullloaded = true;
            loading.set(false);
        });
        return this.loadFuture;
    }

    public Class<T> getType() {
        return type;
    }

    public int clear() {
        this.fullloaded = false;
        this.list = new ConcurrentLinkedQueue();
        this.map = new ConcurrentHashMap();
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
            this.scheduler = null;
        }
        return 1;
    }

    public boolean isFullLoaded() {
        return fullloaded;
    }

    public int size() {
        return map.size();
    }

    public T find(Serializable pk) {
        if (pk == null) {
            return null;
        }
        T rs = map.get(pk);
        return rs == null ? null : (needCopy ? newCopier.apply(rs, this.creator.create()) : rs);
    }

    public T[] finds(Serializable... pks) {
        if (pks == null || pks.length == 0) {
            return arrayer.apply(0);
        }
        if (pks.length == 1) {
            Class t = pks[0].getClass();
            if (t == int[].class) {
                int[] ids = (int[]) pks[0];
                T[] result = arrayer.apply(ids.length);
                for (int i = 0; i < result.length; i++) {
                    T rs = map.get(ids[i]);
                    result[i] = rs == null ? null : (needCopy ? newCopier.apply(rs, this.creator.create()) : rs);
                }
                return result;
            } else if (t == long[].class) {
                long[] ids = (long[]) pks[0];
                T[] result = arrayer.apply(ids.length);
                for (int i = 0; i < result.length; i++) {
                    T rs = map.get(ids[i]);
                    result[i] = rs == null ? null : (needCopy ? newCopier.apply(rs, this.creator.create()) : rs);
                }
                return result;
            }
        }
        T[] result = arrayer.apply(pks.length);
        if (needCopy) {
            for (int i = 0; i < result.length; i++) {
                T rs = map.get(pks[i]);
                result[i] = rs == null ? null : newCopier.apply(rs, this.creator.create());
            }
        } else {
            for (int i = 0; i < result.length; i++) {
                result[i] = map.get(pks[i]);
            }
        }
        return result;
    }

    public T find(final SelectColumn selects, final Serializable pk) {
        if (pk == null) {
            return null;
        }
        T rs = map.get(pk);
        if (rs == null) {
            return null;
        }
        if (selects == null) {
            return needCopy ? newCopier.apply(rs, this.creator.create()) : rs;
        }
        T t = this.creator.create();
        for (Attribute attr : this.info.attributes) {
            if (selects.test(attr.field())) {
                attr.set(t, attr.get(rs));
            }
        }
        return t;
    }

    public T[] finds(final SelectColumn selects, Serializable... pks) {
        if (selects == null) {
            return finds(pks);
        }
        if (pks == null || pks.length == 0) {
            return arrayer.apply(0);
        }
        final Creator<T> ctr = this.creator;
        final Attribute<T, Serializable>[] attrs = this.info.attributes;
        int size = pks.length;
        int[] ids1 = null;
        long[] ids2 = null;
        if (size == 1) {
            if (pks[0].getClass() == int[].class) {
                ids1 = (int[]) pks[0];
            } else if (pks[0].getClass() == long[].class) {
                ids2 = (long[]) pks[0];
            }
        }
        T[] result = arrayer.apply(size);
        for (int i = 0; i < result.length; i++) {
            Serializable id = ids1 == null ? (ids2 == null ? pks[i] : ids2[i]) : ids1[i];
            T rs = map.get(id);
            if (rs == null) {
                continue;
            }
            if (selects == null) {
                if (needCopy) {
                    rs = newCopier.apply(rs, ctr.create());
                }
            } else {
                T t = ctr.create();
                for (Attribute attr : attrs) {
                    if (selects.test(attr.field())) {
                        attr.set(t, attr.get(rs));
                    }
                }
                rs = t;
            }
            result[i] = rs;
        }
        return result;
    }

    public T find(final SelectColumn selects, FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        Optional<T> opt = stream.findFirst();
        if (!opt.isPresent()) {
            return null;
        }
        if (selects == null) {
            return needCopy ? newCopier.apply(opt.get(), this.creator.create()) : opt.get();
        }
        T rs = opt.get();
        T t = this.creator.create();
        for (Attribute attr : this.info.attributes) {
            if (selects.test(attr.field())) {
                attr.set(t, attr.get(rs));
            }
        }
        return t;
    }

    public Serializable findColumn(final String column, final Serializable defValue, final Serializable pk) {
        if (pk == null) {
            return defValue;
        }
        T rs = map.get(pk);
        if (rs == null) {
            return defValue;
        }
        for (Attribute attr : this.info.attributes) {
            if (column.equals(attr.field())) {
                Serializable val = (Serializable) attr.get(rs);
                return val == null ? defValue : val;
            }
        }
        return defValue;
    }

    public Serializable findColumn(final String column, final Serializable defValue, FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        Optional<T> opt = stream.findFirst();
        if (!opt.isPresent()) {
            return defValue;
        }
        T rs = opt.get();
        for (Attribute attr : this.info.attributes) {
            if (column.equals(attr.field())) {
                Serializable val = (Serializable) attr.get(rs);
                return val == null ? defValue : val;
            }
        }
        return defValue;
    }

    public boolean exists(Serializable pk) {
        if (pk == null) {
            return false;
        }
        final Class atype = this.primary.type();
        if (pk.getClass() != atype && pk instanceof Number) {
            if (atype == int.class || atype == Integer.class) {
                pk = ((Number) pk).intValue();
            } else if (atype == long.class || atype == Long.class) {
                pk = ((Number) pk).longValue();
            } else if (atype == short.class || atype == Short.class) {
                pk = ((Number) pk).shortValue();
            } else if (atype == float.class || atype == Float.class) {
                pk = ((Number) pk).floatValue();
            } else if (atype == byte.class || atype == Byte.class) {
                pk = ((Number) pk).byteValue();
            } else if (atype == double.class || atype == Double.class) {
                pk = ((Number) pk).doubleValue();
            } else if (atype == AtomicInteger.class) {
                pk = new AtomicInteger(((Number) pk).intValue());
            } else if (atype == AtomicLong.class) {
                pk = new AtomicLong(((Number) pk).longValue());
            } else if (atype == LongAdder.class) {
                LongAdder la = new LongAdder();
                la.add(((Number) pk).longValue());
                pk = la;
            }
        }
        return this.map.containsKey(pk);
    }

    public boolean exists(FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        return stream.findFirst().isPresent();
    }

    public boolean exists(final Predicate<T> filter) {
        return (filter != null) && this.list.stream().anyMatch(filter);
    }

    public Map<Serializable, Number> queryColumnMap(
            final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final Attribute<T, Serializable> keyAttr = info.getAttribute(keyColumn);
        final Predicate filter = node == null ? null : node.createPredicate(this);
        final Attribute funcAttr = funcColumn == null ? null : info.getAttribute(funcColumn);
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        Collector<T, Map, ?> collector = null;
        final Class valtype = funcAttr == null ? null : funcAttr.type();
        if (func != null) {
            switch (func) {
                case AVG:
                    if (valtype == float.class
                            || valtype == Float.class
                            || valtype == double.class
                            || valtype == Double.class) {
                        collector = (Collector<T, Map, ?>)
                                Collectors.averagingDouble((T t) -> ((Number) funcAttr.get(t)).doubleValue());
                    } else {
                        collector = (Collector<T, Map, ?>)
                                Collectors.averagingLong((T t) -> ((Number) funcAttr.get(t)).longValue());
                    }
                    break;
                case COUNT:
                    collector = (Collector<T, Map, ?>) Collectors.counting();
                    break;
                case DISTINCTCOUNT:
                    collector = (Collector<T, Map, ?>) Collectors.mapping(funcAttr::get, Collectors.toSet());
                    break;
                case MAX:
                case MIN:
                    Comparator<T> comp = (o1, o2) -> o1 == null
                            ? (o2 == null ? 0 : -1)
                            : ((Comparable) funcAttr.get(o1)).compareTo(funcAttr.get(o2));
                    collector =
                            (Collector<T, Map, ?>) ((func == MAX) ? Collectors.maxBy(comp) : Collectors.minBy(comp));
                    break;
                case SUM:
                    if (valtype == float.class
                            || valtype == Float.class
                            || valtype == double.class
                            || valtype == Double.class) {
                        collector = (Collector<T, Map, ?>)
                                Collectors.summingDouble((T t) -> ((Number) funcAttr.get(t)).doubleValue());
                    } else {
                        collector = (Collector<T, Map, ?>)
                                Collectors.summingLong((T t) -> ((Number) funcAttr.get(t)).longValue());
                    }
                    break;
            }
        }
        Map rs = collector == null
                ? stream.collect(Collectors.toMap(keyAttr::get, funcAttr::get, (key1, key2) -> key2))
                : stream.collect(Collectors.groupingBy(keyAttr::get, LinkedHashMap::new, collector));
        if (func == MAX || func == MIN) {
            Map rs2 = new LinkedHashMap();
            rs.forEach((x, y) -> {
                if (((Optional) y).isPresent()) {
                    rs2.put(x, funcAttr.get((T) ((Optional) y).get()));
                }
            });
            rs = rs2;
        } else if (func == DISTINCTCOUNT) {
            Map rs2 = new LinkedHashMap();
            rs.forEach((x, y) -> rs2.put(x, ((Set) y).size() + 0L));
            rs = rs2;
        }
        return rs;
    }

    public Map<Serializable[], Number[]> queryColumnMap(
            final ColumnNode[] funcNodes, final String[] groupByColumns, FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        final Attribute<T, Serializable>[] attrs = new Attribute[groupByColumns.length];
        for (int i = 0; i < groupByColumns.length; i++) {
            attrs[i] = info.getAttribute(groupByColumns[i]);
        }
        final Map<String, Serializable[]> valmap = new HashMap<>();
        Function<T, Serializable[]> func = t -> {
            StringBuilder sb = new StringBuilder();
            final Serializable[] vals = new Serializable[attrs.length];
            for (int i = 0; i < attrs.length; i++) {
                vals[i] = attrs[i].get(t);
                sb.append((char) 20).append(vals[i]);
            }
            final String key = sb.toString();
            if (!valmap.containsKey(key)) {
                valmap.put(key, vals);
            }
            return valmap.get(key);
        };
        Map<Serializable[], List<T>> listmap = stream.collect(Collectors.groupingBy(func));
        final Map<Serializable[], Number[]> rsmap = new HashMap<>(listmap.size());
        listmap.forEach((k, l) -> rsmap.put(k, queryColumnNumbers(l, funcNodes)));
        return rsmap;
    }

    private Number[] queryColumnNumbers(final List<T> list, final ColumnNode[] funcNodes) {
        if (true) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        Number[] rs = new Number[funcNodes.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = queryColumnNumber(list, funcNodes[i]);
        }
        return rs;
    }

    private Number queryColumnNumber(final List<T> list, final ColumnNode funcNode) {
        if (funcNode instanceof ColumnFuncNode) {
            return queryColumnFuncNodeNumber(list, (ColumnFuncNode) funcNode);
        } else if (funcNode instanceof ColumnExpNode) {
            return queryColumnExpNodeNumber(list, (ColumnExpNode) funcNode);
        } else {
            return null;
        }
    }

    private Number queryColumnFuncNodeNumber(final List<T> list, final ColumnFuncNode funcNode) {
        if (funcNode.getValue() instanceof ColumnNameNode) {
            final Attribute<T, Serializable> attr =
                    info.getAttribute(((ColumnNameNode) funcNode.getValue()).getColumn());
            final Function<T, Number> attrFunc = x -> (Number) attr.get(x);
            return getNumberResult(list, funcNode.getFunc(), null, attr.type(), attrFunc, (FilterNode) null);
        }
        Number num = null;
        if (funcNode.getValue() instanceof ColumnFuncNode) {
            num = queryColumnFuncNodeNumber(list, (ColumnFuncNode) funcNode.getValue());
        } else if (funcNode.getValue() instanceof ColumnExpNode) {
            num = queryColumnExpNodeNumber(list, (ColumnExpNode) funcNode.getValue());
        }
        return num;
    }

    private Number queryColumnExpNodeNumber(final List<T> list, final ColumnExpNode nodeValue) {
        // TODO 尚未实现
        return null;
    }

    private Number getNumberResult(
            final Collection<T> entityList,
            final FilterFunc func,
            final Number defResult,
            final Class attrType,
            final Function<T, Number> attrFunc,
            final FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        Stream<T> stream = entityList.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        switch (func) {
            case AVG:
                if (attrType == int.class || attrType == Integer.class || attrType == AtomicInteger.class) {
                    OptionalDouble rs =
                            stream.mapToInt(x -> attrFunc.apply(x).intValue()).average();
                    return rs.isPresent() ? (int) rs.getAsDouble() : defResult;
                } else if (attrType == long.class
                        || attrType == Long.class
                        || attrType == AtomicLong.class
                        || attrType == LongAdder.class) {
                    OptionalDouble rs =
                            stream.mapToLong(x -> attrFunc.apply(x).longValue()).average();
                    return rs.isPresent() ? (long) rs.getAsDouble() : defResult;
                } else if (attrType == short.class || attrType == Short.class) {
                    OptionalDouble rs = stream.mapToInt(x -> ((Short) attrFunc.apply(x)).intValue())
                            .average();
                    return rs.isPresent() ? (short) rs.getAsDouble() : defResult;
                } else if (attrType == float.class || attrType == Float.class) {
                    OptionalDouble rs = stream.mapToDouble(x -> ((Float) attrFunc.apply(x)).doubleValue())
                            .average();
                    return rs.isPresent() ? (float) rs.getAsDouble() : defResult;
                } else if (attrType == double.class || attrType == Double.class) {
                    OptionalDouble rs =
                            stream.mapToDouble(x -> (Double) attrFunc.apply(x)).average();
                    return rs.isPresent() ? rs.getAsDouble() : defResult;
                }
                throw new SourceException("getNumberResult error(type:" + type + ", attr.type: " + attrType);
            case COUNT:
                return stream.count();
            case DISTINCTCOUNT:
                return stream.map(attrFunc::apply).distinct().count();

            case MAX:
                if (attrType == int.class || attrType == Integer.class || attrType == AtomicInteger.class) {
                    OptionalInt rs =
                            stream.mapToInt(x -> attrFunc.apply(x).intValue()).max();
                    return rs.isPresent() ? rs.getAsInt() : defResult;
                } else if (attrType == long.class
                        || attrType == Long.class
                        || attrType == AtomicLong.class
                        || attrType == LongAdder.class) {
                    OptionalLong rs =
                            stream.mapToLong(x -> attrFunc.apply(x).longValue()).max();
                    return rs.isPresent() ? rs.getAsLong() : defResult;
                } else if (attrType == short.class || attrType == Short.class) {
                    OptionalInt rs = stream.mapToInt(x -> ((Short) attrFunc.apply(x)).intValue())
                            .max();
                    return rs.isPresent() ? (short) rs.getAsInt() : defResult;
                } else if (attrType == float.class || attrType == Float.class) {
                    OptionalDouble rs = stream.mapToDouble(x -> ((Float) attrFunc.apply(x)).doubleValue())
                            .max();
                    return rs.isPresent() ? (float) rs.getAsDouble() : defResult;
                } else if (attrType == double.class || attrType == Double.class) {
                    OptionalDouble rs =
                            stream.mapToDouble(x -> (Double) attrFunc.apply(x)).max();
                    return rs.isPresent() ? rs.getAsDouble() : defResult;
                }
                throw new SourceException("getNumberResult error(type:" + type + ", attr.type: " + attrType);

            case MIN:
                if (attrType == int.class || attrType == Integer.class || attrType == AtomicInteger.class) {
                    OptionalInt rs =
                            stream.mapToInt(x -> attrFunc.apply(x).intValue()).min();
                    return rs.isPresent() ? rs.getAsInt() : defResult;
                } else if (attrType == long.class
                        || attrType == Long.class
                        || attrType == AtomicLong.class
                        || attrType == LongAdder.class) {
                    OptionalLong rs =
                            stream.mapToLong(x -> attrFunc.apply(x).longValue()).min();
                    return rs.isPresent() ? rs.getAsLong() : defResult;
                } else if (attrType == short.class || attrType == Short.class) {
                    OptionalInt rs = stream.mapToInt(x -> ((Short) attrFunc.apply(x)).intValue())
                            .min();
                    return rs.isPresent() ? (short) rs.getAsInt() : defResult;
                } else if (attrType == float.class || attrType == Float.class) {
                    OptionalDouble rs = stream.mapToDouble(x -> ((Float) attrFunc.apply(x)).doubleValue())
                            .min();
                    return rs.isPresent() ? (float) rs.getAsDouble() : defResult;
                } else if (attrType == double.class || attrType == Double.class) {
                    OptionalDouble rs =
                            stream.mapToDouble(x -> (Double) attrFunc.apply(x)).min();
                    return rs.isPresent() ? rs.getAsDouble() : defResult;
                }
                throw new SourceException("getNumberResult error(type:" + type + ", attr.type: " + attrType);

            case SUM:
                if (attrType == int.class || attrType == Integer.class || attrType == AtomicInteger.class) {
                    return stream.mapToInt(x -> attrFunc.apply(x).intValue()).sum();
                } else if (attrType == long.class
                        || attrType == Long.class
                        || attrType == AtomicLong.class
                        || attrType == LongAdder.class) {
                    return stream.mapToLong(x -> attrFunc.apply(x).longValue()).sum();
                } else if (attrType == short.class || attrType == Short.class) {
                    return (short) stream.mapToInt(x -> ((Short) attrFunc.apply(x)).intValue())
                            .sum();
                } else if (attrType == float.class || attrType == Float.class) {
                    return (float) stream.mapToDouble(x -> ((Float) attrFunc.apply(x)).doubleValue())
                            .sum();
                } else if (attrType == double.class || attrType == Double.class) {
                    return stream.mapToDouble(x -> (Double) attrFunc.apply(x)).sum();
                }
                throw new SourceException("getNumberResult error(type:" + type + ", attr.type: " + attrType);
        }
        return defResult;
    }

    public Number getNumberResult(
            final FilterFunc func, final Number defResult, final String column, final FilterNode node) {
        final Attribute<T, Serializable> attr = column == null ? null : info.getAttribute(column); // COUNT的column=null
        final Function<T, Number> attrFunc = attr == null ? null : x -> (Number) attr.get(x);
        return getNumberResult(this.list, func, defResult, attr == null ? null : attr.type(), attrFunc, node);
    }

    public Sheet<T> querySheet(final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheet(true, false, selects, flipper, node);
    }

    protected Stream<T> distinctStream(Stream<T> stream, final List<Attribute<T, Serializable>> keyattrs) {
        if (keyattrs == null) {
            return stream;
        }
        final Set<String> keys = new HashSet<>();
        Predicate<T> filter = t -> {
            StringBuilder sb = new StringBuilder();
            for (Attribute attr : keyattrs) {
                sb.append(attr.get(t));
            }
            String key = sb.toString();
            if (keys.contains(key)) {
                return false;
            }
            keys.add(key);
            return true;
        };
        return stream.filter(filter);
    }

    public Sheet<T> querySheet(
            final boolean needTotal,
            boolean distinct,
            final SelectColumn selects,
            final Flipper flipper,
            FilterNode node) {
        final Predicate<T> filter = node == null ? null : node.createPredicate(this);
        final Comparator<T> comparator = createComparator(flipper);
        long total = 0;
        List<Attribute<T, Serializable>> keyattrs = null;
        if (distinct) {
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            info.forEachAttribute((k, v) -> {
                if (selects == null || selects.test(k)) {
                    attrs.add(v);
                }
            });
            keyattrs = attrs;
        }
        if (needTotal) {
            Stream<T> stream = this.list.stream();
            if (filter != null) {
                stream = stream.filter(filter);
            }
            if (distinct) {
                stream = distinctStream(stream, keyattrs);
            }
            total = stream.count();
        }
        if (needTotal && total == 0) {
            return new Sheet<>(0, new ArrayList());
        }
        Stream<T> stream = this.list.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        if (distinct) {
            stream = distinctStream(stream, keyattrs);
        }
        if (comparator != null) {
            stream = stream.sorted(comparator);
        }
        if (flipper != null && flipper.getOffset() > 0) {
            stream = stream.skip(flipper.getOffset());
        }
        if (flipper != null && flipper.getLimit() > 0) {
            stream = stream.limit(flipper.getLimit());
        }
        final List<T> rs = new ArrayList<>();
        if (selects == null) {
            Consumer<? super T> action = x -> rs.add(needCopy ? newCopier.apply(x, creator.create()) : x);
            if (comparator != null) {
                stream.forEachOrdered(action);
            } else {
                stream.forEach(action);
            }
        } else {
            final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
            info.forEachAttribute((k, v) -> {
                if (selects.test(k)) {
                    attrs.add(v);
                }
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
        if (!needTotal) {
            total = rs.size();
        }
        return new Sheet<>(total, rs);
    }

    public int insert(T entity) {
        if (entity == null) {
            return 0;
        }
        final T rs = newCopier.apply(entity, this.creator.create()); // 确保同一主键值的map与list中的对象必须共用。
        T old = this.map.putIfAbsent(this.primary.get(rs), rs);
        if (old == null) {
            this.list.add(rs);
            return 1;
        } else {
            logger.log(Level.WARNING, this.type + " cache repeat insert data: " + entity);
            return 0;
        }
    }

    public int delete(final Serializable pk) {
        if (pk == null) {
            return 0;
        }
        final T rs = this.map.remove(pk);
        if (rs == null) {
            return 0;
        }
        this.list.remove(rs);
        return 1;
    }

    public Serializable[] delete(final Flipper flipper, final FilterNode node) {
        if (node == null || this.list.isEmpty()) {
            return new Serializable[0];
        }
        final Comparator<T> comparator = createComparator(flipper);
        Stream<T> stream = this.list.stream().filter(node.createPredicate(this));
        if (comparator != null) {
            stream = stream.sorted(comparator);
        }
        if (flipper != null && flipper.getOffset() > 0) {
            stream = stream.skip(flipper.getOffset());
        }
        if (flipper != null && flipper.getLimit() > 0) {
            stream = stream.limit(flipper.getLimit());
        }
        Object[] rms = stream.toArray();
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

    public int drop() {
        return clear();
    }

    public int update(final T entity) {
        if (entity == null) {
            return 0;
        }
        T rs = this.map.get(this.primary.get(entity));
        if (rs == null) {
            return 0;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            this.uptCopier.apply(entity, rs);
        } finally {
            tableLock.unlock();
        }
        return 1;
    }

    public T update(final T entity, Collection<Attribute<T, Serializable>> attrs) {
        if (entity == null) {
            return entity;
        }
        T rs = this.map.get(this.primary.get(entity));
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            for (Attribute attr : attrs) {
                attr.set(rs, attr.get(entity));
            }
        } finally {
            tableLock.unlock();
        }
        return rs;
    }

    public T[] update(final T entity, final Collection<Attribute<T, Serializable>> attrs, final FilterNode node) {
        if (entity == null || node == null) {
            return (T[]) Creator.newArray(type, 0);
        }
        T[] rms = this.list.stream().filter(node.createPredicate(this)).toArray(arrayer);
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            for (T rs : rms) {
                for (Attribute attr : attrs) {
                    attr.set(rs, attr.get(entity));
                }
            }
        } finally {
            tableLock.unlock();
        }
        return rms;
    }

    public <V> T update(final Serializable pk, Attribute<T, V> attr, final V fieldValue) {
        if (pk == null) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs != null) {
            attr.set(rs, fieldValue);
        }
        return rs;
    }

    public <V> T[] update(Attribute<T, V> attr, final V fieldValue, final FilterNode node) {
        if (attr == null || node == null) {
            return (T[]) Creator.newArray(type, 0);
        }
        T[] rms = this.list.stream().filter(node.createPredicate(this)).toArray(arrayer);
        for (T rs : rms) {
            attr.set(rs, fieldValue);
        }
        return rms;
    }

    public T updateColumn(
            final Serializable pk, List<Attribute<T, Serializable>> attrs, final List<ColumnValue> values) {
        if (pk == null || attrs == null || attrs.isEmpty()) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            for (int i = 0; i < attrs.size(); i++) {
                ColumnValue cv = values.get(i);
                updateColumn(attrs.get(i), rs, cv.getExpress(), cv.getValue());
            }
        } finally {
            tableLock.unlock();
        }
        return rs;
    }

    public T[] updateColumn(
            final FilterNode node,
            final Flipper flipper,
            List<Attribute<T, Serializable>> attrs,
            final List<ColumnValue> values) {
        if (attrs == null || attrs.isEmpty() || node == null) {
            return (T[]) Creator.newArray(type, 0);
        }
        Stream<T> stream = this.list.stream();
        final Comparator<T> comparator = createComparator(flipper);
        if (comparator != null) {
            stream = stream.sorted(comparator);
        }
        if (flipper != null && flipper.getLimit() > 0) {
            stream = stream.limit(flipper.getLimit());
        }
        T[] rms = stream.filter(node.createPredicate(this)).toArray(arrayer);
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            for (T rs : rms) {
                for (int i = 0; i < attrs.size(); i++) {
                    ColumnValue cv = values.get(i);
                    updateColumn(attrs.get(i), rs, cv.getExpress(), cv.getValue());
                }
            }
        } finally {
            tableLock.unlock();
        }
        return rms;
    }

    public <V> T updateColumnOr(final Serializable pk, Attribute<T, V> attr, final long orvalue) {
        if (pk == null) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            return updateColumn(attr, rs, ColumnExpress.ORR, orvalue);
        } finally {
            tableLock.unlock();
        }
    }

    public <V> T updateColumnAnd(final Serializable pk, Attribute<T, V> attr, final long andvalue) {
        if (pk == null) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            return updateColumn(attr, rs, ColumnExpress.AND, andvalue);
        } finally {
            tableLock.unlock();
        }
    }

    public <V> T updateColumnIncrement(final Serializable pk, Attribute<T, V> attr, final long incvalue) {
        if (pk == null) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            return updateColumn(attr, rs, ColumnExpress.INC, incvalue);
        } finally {
            tableLock.unlock();
        }
    }

    public <V> T updateColumnDecrement(final Serializable pk, Attribute<T, V> attr, final long incvalue) {
        if (pk == null) {
            return null;
        }
        T rs = this.map.get(pk);
        if (rs == null) {
            return rs;
        }
        tableLock.lock(); // 表锁, 可优化成行锁
        try {
            return updateColumn(attr, rs, ColumnExpress.DEC, incvalue);
        } finally {
            tableLock.unlock();
        }
    }

    private <V> T updateColumn(Attribute<T, V> attr, final T entity, final ColumnExpress express, Serializable val) {
        final Class ft = attr.type();
        Number numb = null;
        Serializable newVal = null;
        switch (express) {
            case INC:
            case DEC:
            case MUL:
            case DIV:
            case MOD:
            case AND:
            case ORR:
                numb = getNumberValue(
                        (Number) attr.get(entity),
                        express,
                        val instanceof ColumnNumberNode ? ((ColumnNumberNode) val).getValue() : (Number) val);
                break;
            case SET:
                if (val instanceof ColumnExpNode) {
                    val = updateColumnExpNode(attr, entity, (ColumnExpNode) val);
                }
                newVal = val;
                if (val instanceof Number) {
                    numb = (Number) val;
                }
                break;
        }
        if (numb != null) {
            if (ft == int.class || ft == Integer.class) {
                newVal = numb.intValue();
            } else if (ft == long.class || ft == Long.class) {
                newVal = numb.longValue();
            } else if (ft == short.class || ft == Short.class) {
                newVal = numb.shortValue();
            } else if (ft == float.class || ft == Float.class) {
                newVal = numb.floatValue();
            } else if (ft == double.class || ft == Double.class) {
                newVal = numb.doubleValue();
            } else if (ft == byte.class || ft == Byte.class) {
                newVal = numb.byteValue();
            } else if (ft == AtomicInteger.class) {
                newVal = new AtomicInteger(numb.intValue());
            } else if (ft == AtomicLong.class) {
                newVal = new AtomicLong(numb.longValue());
            } else if (ft == LongAdder.class) {
                LongAdder la = new LongAdder();
                la.add(numb.longValue());
                newVal = la;
            }
        } else {
            if (ft == AtomicInteger.class && newVal != null && newVal.getClass() != AtomicInteger.class) {
                newVal = new AtomicInteger(((Number) newVal).intValue());
            } else if (ft == AtomicLong.class && newVal != null && newVal.getClass() != AtomicLong.class) {
                newVal = new AtomicLong(((Number) newVal).longValue());
            } else if (ft == LongAdder.class && newVal != null && newVal.getClass() != LongAdder.class) {
                LongAdder la = new LongAdder();
                la.add(((Number) newVal).longValue());
                newVal = la;
            }
        }
        attr.set(entity, (V) newVal);
        return entity;
    }

    private <V> Serializable updateColumnExpNode(Attribute<T, V> attr, final T entity, ColumnExpNode node) {
        Serializable leftVal = null;
        ColumnNode leftNode = node.getLeft();
        // 类型只能是ColumnNameNode、ColumnNumberNode、ColumnExpNode
        if (leftNode instanceof ColumnNameNode) {
            leftVal = info.getUpdateAttribute(leftNode.toString()).get(entity);
        } else if (leftNode instanceof ColumnNumberNode) {
            leftVal = ((ColumnNumberNode) leftNode).getValue();
        } else if (leftNode instanceof ColumnExpNode) {
            leftVal = updateColumnExpNode(attr, entity, (ColumnExpNode) leftNode);
        }
        if (node.getExpress() == ColumnExpress.SET) {
            return leftVal;
        }

        Serializable rightVal = null;
        ColumnNode rightNode = node.getRight();
        // 类型只能是ColumnNameNode、ColumnNumberNode、ColumnExpNode
        if (rightNode instanceof ColumnNameNode) {
            rightVal = info.getUpdateAttribute(rightNode.toString()).get(entity);
        } else if (rightNode instanceof ColumnNumberNode) {
            rightVal = ((ColumnNumberNode) rightNode).getValue();
        } else if (rightNode instanceof ColumnExpNode) {
            rightVal = updateColumnExpNode(attr, entity, (ColumnExpNode) rightNode);
        }
        return getNumberValue((Number) leftVal, node.getExpress(), (Number) rightVal);
    }

    private Number getNumberValue(Number numb, final ColumnExpress express, Number val) {
        switch (express) {
            case INC:
                if (numb == null) {
                    numb = val;
                } else {
                    if (numb instanceof Float || val instanceof Float) {
                        numb = numb.floatValue() + val.floatValue();
                    } else if (numb instanceof Double || val instanceof Double) {
                        numb = numb.doubleValue() + val.doubleValue();
                    } else if (val != null) {
                        numb = numb.longValue() + val.longValue();
                    }
                }
                break;
            case DEC:
                if (numb == null) {
                    numb = val;
                } else {
                    if (numb instanceof Float || val instanceof Float) {
                        numb = numb.floatValue() - val.floatValue();
                    } else if (numb instanceof Double || val instanceof Double) {
                        numb = numb.doubleValue() - val.doubleValue();
                    } else if (val != null) {
                        numb = numb.longValue() - val.longValue();
                    }
                }
                break;
            case MUL:
                if (numb == null) {
                    numb = 0;
                } else {
                    numb = numb.longValue() * val.floatValue();
                }
                break;
            case DIV:
                if (numb == null) {
                    numb = 0;
                } else {
                    numb = numb.longValue() / val.floatValue();
                }
                break;
            case MOD:
                if (numb == null) {
                    numb = 0;
                } else {
                    numb = numb.longValue() % val.intValue();
                }
                break;
            case AND:
                if (numb == null) {
                    numb = 0;
                } else {
                    numb = numb.longValue() & val.longValue();
                }
                break;
            case ORR:
                if (numb == null) {
                    numb = 0;
                } else {
                    numb = numb.longValue() | val.longValue();
                }
                break;
        }
        return numb;
    }

    public Attribute<T, Serializable> getAttribute(String fieldname) {
        return info.getAttribute(fieldname);
    }

    // -------------------------------------------------------------------------------------------------------------------------------
    protected Comparator<T> createComparator(Flipper flipper) {
        if (flipper == null
                || flipper.getSort() == null
                || flipper.getSort().isEmpty()
                || flipper.getSort().indexOf(';') >= 0
                || flipper.getSort().indexOf('\n') >= 0) {
            return null;
        }
        final String sort = flipper.getSort();
        Comparator<T> comparator = this.sortComparators.get(sort);
        if (comparator != null) {
            return comparator;
        }
        for (String item : sort.split(",")) {
            if (item.trim().isEmpty()) {
                continue;
            }
            String[] sub = item.trim().split("\\s+");
            int pos = sub[0].indexOf('(');
            Attribute<T, Serializable> attr;
            if (pos <= 0) {
                attr = getAttribute(sub[0]);
            } else { // 含SQL函数
                int pos2 = sub[0].lastIndexOf(')');
                final Attribute<T, Serializable> pattr = getAttribute(sub[0].substring(pos + 1, pos2));
                final String func = sub[0].substring(0, pos);
                if ("ABS".equalsIgnoreCase(func)) {
                    Function getter = null;
                    if (pattr.type() == int.class
                            || pattr.type() == Integer.class
                            || pattr.type() == AtomicInteger.class) {
                        getter = x -> Math.abs(((Number) pattr.get((T) x)).intValue());
                    } else if (pattr.type() == long.class
                            || pattr.type() == Long.class
                            || pattr.type() == AtomicLong.class
                            || pattr.type() == LongAdder.class) {
                        getter = x -> Math.abs(((Number) pattr.get((T) x)).longValue());
                    } else if (pattr.type() == float.class || pattr.type() == Float.class) {
                        getter = x -> Math.abs(((Number) pattr.get((T) x)).floatValue());
                    } else if (pattr.type() == double.class || pattr.type() == Double.class) {
                        getter = x -> Math.abs(((Number) pattr.get((T) x)).doubleValue());
                    } else {
                        throw new SourceException(
                                "Flipper not supported sort illegal type by ABS (" + flipper.getSort() + ")");
                    }
                    attr = (Attribute<T, Serializable>) Attribute.create(
                            pattr.declaringClass(), pattr.field(), pattr.type(), getter, (o, v) -> pattr.set(o, v));
                } else if (func.isEmpty()) {
                    attr = pattr;
                } else {
                    throw new SourceException(
                            "Flipper not supported sort illegal function (" + flipper.getSort() + ")");
                }
            }
            Comparator<T> c = (sub.length > 1 && sub[1].equalsIgnoreCase("DESC"))
                    ? (T o1, T o2) -> {
                        Comparable c1 = (Comparable) attr.get(o1);
                        Comparable c2 = (Comparable) attr.get(o2);
                        return c2 == null ? -1 : c2.compareTo(c1);
                    }
                    : (T o1, T o2) -> {
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
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final UniqueSequence other = (UniqueSequence) obj;
            if (value.length != other.value.length) {
                return false;
            }
            for (int i = 0; i < value.length; i++) {
                if (!value[i].equals(other.value[i])) {
                    return false;
                }
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
                        if (node == null || node.isOr()) {
                            return false;
                        }
                        if (!attribute.field().equals(node.column)) {
                            return false;
                        }
                        if (node.nodes == null) {
                            return true;
                        }
                        for (FilterNode n : node.nodes) {
                            if (!test(n)) {
                                return false;
                            }
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
