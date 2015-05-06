/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.Attribute;
import com.wentch.redkale.util.Ignore;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
final class FilterInfo<T extends FilterBean> {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private static final ConcurrentHashMap<Class, FilterInfo> infos = new ConcurrentHashMap<>();

    private final String joinsql;

    private final boolean validCacheJoin;

    private final Class<T> type;

    private final FilterExpressNode<T> rootNode;

    public static <T> FilterInfo load(Class<T> clazz, DataSource source) {
        FilterInfo rs = infos.get(clazz);
        if (rs != null) return rs;
        synchronized (infos) {
            rs = infos.get(clazz);
            if (rs == null) {
                rs = new FilterInfo(clazz, source);
                infos.put(clazz, rs);
            }
            return rs;
        }
    }

    private FilterInfo(Class<T> type, DataSource source) {
        this.type = type;
        Class cltmp = type;
        Set<String> fields = new HashSet<>();
        StringBuilder joinsb = new StringBuilder();
        final Map<Class, String> joinTables = new HashMap<>();
        final Map<String, FilterItem> getters = new HashMap<>();
        final Map<String, List<FilterItem>> nodes = new HashMap<>();
        boolean cachejoin = true;
        int index = 0;
        do {
            for (Field field : cltmp.getDeclaredFields()) {
                if (field.getAnnotation(Ignore.class) != null) continue;
                if (field.getAnnotation(Transient.class) != null) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (fields.contains(field.getName())) continue;
                char[] chars = field.getName().toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                final Class t = field.getType();
                try {
                    type.getMethod(((t == boolean.class || t == Boolean.class) ? "is" : "get") + new String(chars));
                } catch (Exception ex) {
                    continue;
                }
                fields.add(field.getName());
                FilterItem item = new FilterItem(field, "a", null);
                FilterJoinColumn joinCol = field.getAnnotation(FilterJoinColumn.class);
                boolean again = true;
                if (joinCol != null) {
                    if (!joinTables.containsKey(joinCol.table())) {
                        again = false;
                        joinTables.put(joinCol.table(), String.valueOf((char) ('a' + (++index))));
                    }
                    String alias = joinTables.get(joinCol.table());
                    EntityInfo info = EntityInfo.load(joinCol.table(), source);
                    EntityCache cache = null;
                    if (info.getCache() != null && info.getCache().isFullLoaded()) {
                        cache = info.getCache();
                    } else {
                        cachejoin = false;
                    }
                    item = new FilterItem(field, alias, cache);
                    EntityInfo secinfo = EntityInfo.load(joinCol.table(), null);
                    if (!again) {
                        joinsb.append(" ").append(joinCol.type().name()).append(" JOIN ").append(secinfo.getTable())
                                .append(" ").append(alias).append(" ON a.# = ").append(alias).append(".")
                                .append(joinCol.column().isEmpty() ? secinfo.getPrimary().field() : joinCol.column());
                    }
                }
                getters.put(field.getName(), item);
                FilterGroup[] refs = field.getAnnotationsByType(FilterGroup.class);
                String[] groups = new String[refs.length];
                for (int i = 0; i < refs.length; i++) {
                    groups[i] = refs[i].value();
                }
                if (groups.length == 0) groups = new String[]{"[AND]"};
                for (String key : groups) {
                    if (!key.startsWith("[AND]") && !key.startsWith("[OR]")) {
                        throw new RuntimeException(field + "'s FilterGroup.value(" + key + ") illegal, must be [AND] or [OR] startsWith");
                    }
                    List<FilterItem> nd = nodes.get(key);
                    if (nd == null) {
                        nd = new ArrayList();
                        nodes.put(key, nd);
                    }
                    nd.add(item);
                }
            }
        } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        //---------------------------------------------------------------
        List<FilterExpressNode> expnodes = new ArrayList<>();
        for (Map.Entry<String, List<FilterItem>> en : nodes.entrySet()) {
            if (en.getValue().size() == 1) {
                expnodes.add(en.getValue().get(0));
            } else {
                List<FilterItem> sitems = en.getValue();
                expnodes.add(new FilterGroupNode(en.getKey(), sitems.toArray(new FilterExpressNode[sitems.size()])));
            }
        }
        rootNode = new FilterGroupNode("AND", expnodes.toArray(new FilterExpressNode[expnodes.size()]));
        //---------------------------------------------------------------
        this.validCacheJoin = cachejoin;
        if (!joinTables.isEmpty()) {
            this.joinsql = joinsb.toString();
        } else {
            this.joinsql = null;
        }
    }

    public FilterItem[] getFilters() {
        return new FilterItem[0];
    }

    public Class getType() {
        return type;
    }

    public boolean isJoin() {
        return joinsql != null;
    }

    public boolean isValidCacheJoin() {
        return validCacheJoin;
    }

    public <E> Predicate<E> getFilterPredicate(EntityInfo<E> info, T bean) {
        return rootNode.getFilterPredicate(info, bean);
    }

    public static <E> Comparator<E> getSortComparator(EntityInfo<E> info, Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return null;
        Comparator<E> comparator = null;
        for (String item : flipper.getSort().split(",")) {
            if (item.isEmpty()) continue;
            String[] sub = item.split("\\s+");
            final Attribute<E, ?> attr = info.getAttribute(sub[0]);
            Comparator<E> c = (E o1, E o2) -> {
                Comparable c1 = (Comparable) attr.get(o1);
                Comparable c2 = (Comparable) attr.get(o2);
                return c1 == null ? -1 : c1.compareTo(c2);
            };
            if (sub.length > 1 && sub[1].equalsIgnoreCase("DESC")) {
                c = c.reversed();
            }
            if (comparator == null) {
                comparator = c;
            } else {
                comparator = comparator.thenComparing(c);
            }
        }
        return comparator;
    }

    public StringBuilder createWhereSql(String primaryColumn, T obj) {
        StringBuilder sb = rootNode.getFilterExpress(obj);
        if (sb == null) return null;
        final StringBuilder all = new StringBuilder(128);
        if (this.isJoin()) {
            all.append(this.joinsql.replace("#", primaryColumn));
        }
        all.append(" WHERE ").append(sb);
        return all;
    }

    public static String formatToString(Object rs) {
        if (rs == null) return null;
        Class clazz = rs.getClass();
        if (CharSequence.class.isAssignableFrom(clazz)) {
            return "'" + rs.toString().replace("'", "\\'") + "'";
        } else if (java.util.Date.class.isAssignableFrom(clazz)) {
            return "'" + String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", (java.util.Date) rs) + "'";
        }
        return String.valueOf(rs);
    }

    static final class FilterGroupNode<T> implements FilterExpressNode<T> {

        private final String sign;

        private final boolean or;

        private final FilterExpressNode<T>[] nodes;

        public FilterGroupNode(String sign, FilterExpressNode<T>[] nodes) {
            this.sign = sign.indexOf("[OR]") == 0 || sign.equalsIgnoreCase("OR") ? "OR" : "AND";
            this.or = "OR".equals(this.sign);
            this.nodes = nodes;
        }

        @Override
        public StringBuilder getFilterExpress(T obj) {
            StringBuilder sb = null;
            int count = 0;
            for (FilterExpressNode node : nodes) {
                StringBuilder sub = node.getFilterExpress(obj);
                if (sub == null) continue;
                if (sb == null) {
                    sb = new StringBuilder();
                    sb.append(sub);
                    count++;
                } else {
                    sb.append(' ').append(sign).append(' ').append(sub);
                    count++;
                }
            }
            if (sb == null) return null;
            if (count < 2) return sb;
            return new StringBuilder(sb.length() + 2).append('(').append(sb).append(')');
        }

        @Override
        public <E> Predicate<E> getFilterPredicate(EntityInfo<E> info, T bean) {
            Predicate<E> predicate = null;
            for (FilterExpressNode node : nodes) {
                Predicate<E> p = node.getFilterPredicate(info, bean);
                if (p == null) continue;
                if (predicate == null) {
                    predicate = p;
                } else {
                    predicate = or ? predicate.or(p) : predicate.and(p);
                }
            }
            return predicate;
        }
    }

    static final class FilterItem<T, F> implements FilterExpressNode<T> {

        public final Attribute<T, F> attribute;

        public final String aliasfield;

        private final String field;

        public final FilterExpress express;

        public final boolean string;

        public final boolean number;

        public final boolean likefit;

        public final boolean ignoreCase;

        public final long least;

        public final Class<F> type;

        public final EntityCache joinCache;  //待实现

        public FilterItem(Field field, String alias, EntityCache joinCache) {
            this.joinCache = joinCache;
            FilterColumn column = field.getAnnotation(FilterColumn.class);
            String sqlfield = (column != null && !column.name().isEmpty() ? column.name() : field.getName());
            this.field = sqlfield;
            sqlfield = alias + "." + sqlfield;
            this.attribute = Attribute.create(sqlfield, field);
            this.aliasfield = this.attribute.field();
            this.type = (Class<F>) field.getType();
            FilterExpress exp = column == null ? FilterExpress.EQUAL : column.express();
            if (type.isArray() || Collection.class.isAssignableFrom(type)) {
                if (Range.class.isAssignableFrom(type.getComponentType())) {
                    if (AND != exp) exp = FilterExpress.OR;
                } else {
                    if (NOTIN != exp) exp = FilterExpress.IN;
                }
            } else if (Range.class.isAssignableFrom(type)) {
                if (NOTBETWEEN != exp) exp = FilterExpress.BETWEEN;
            }
            this.express = exp;
            this.least = column == null ? 1L : column.least();
            this.likefit = column == null ? true : column.likefit();
            this.ignoreCase = column == null ? true : column.ignoreCase();
            this.number = type.isPrimitive() || Number.class.isAssignableFrom(type);
            this.string = CharSequence.class.isAssignableFrom(type);
        }

        /**
         * 返回null表示无需过滤该字段
         * <p>
         * @param bean
         * @return
         */
        @Override
        public StringBuilder getFilterExpress(final T bean) {
            final F rs = attribute.get(bean);
            if (rs == null) return null;
            if (string && ((CharSequence) rs).length() == 0) return null;
            if (number && ((Number) rs).longValue() < this.least) return null;
            if (Range.class.isAssignableFrom(type)) return getRangeExpress((Range) rs);
            if (type.isArray() || Collection.class.isAssignableFrom(type)) {
                Object[] os;
                if (Collection.class.isAssignableFrom(type)) {
                    os = ((Collection) rs).toArray();
                } else {
                    final int len = Array.getLength(rs);
                    if (len < 1) return null;
                    if (type.getComponentType().isPrimitive()) {
                        os = new Object[len];
                        for (int i = 0; i < len; i++) {
                            os[i] = Array.get(rs, i);
                        }
                    } else {
                        os = (Object[]) rs;
                    }
                }
                if (Range.class.isAssignableFrom(os[0].getClass())) {
                    StringBuilder sb = new StringBuilder();
                    sb.append('(');
                    boolean flag = false;
                    for (Object o : os) {
                        if (flag) sb.append(' ').append(express.value()).append(' ');
                        sb.append(getRangeExpress((Range) o));
                        flag = true;
                    }
                    return sb.append(')');
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(aliasfield).append(' ').append(express.value()).append(" (");
                    boolean flag = false;
                    for (Object o : os) {
                        if (flag) sb.append(',');
                        sb.append(formatValue(o));
                        flag = true;
                    }
                    return sb.append(')');
                }
            } else if (express == OPAND || express == OPOR) {
                StringBuilder sb = new StringBuilder();
                sb.append('(').append(aliasfield).append(' ').append(express.value()).append(' ').append(formatValue(rs)).append(" > 0)");
                return sb;
            } else if (express == OPANDNO) {
                StringBuilder sb = new StringBuilder();
                sb.append('(').append(aliasfield).append(' ').append(express.value()).append(' ').append(formatValue(rs)).append(" = 0)");
                return sb;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(aliasfield).append(' ').append(express.value()).append(' ').append(formatValue(rs));
                return sb;
            }
        }

        private String formatValue(Object rs) {
            if ((LIKE == express || NOTLIKE == express) && this.likefit) return formatToString("%" + rs + "%");
            return formatToString(rs);
        }

        private StringBuilder getRangeExpress(Range range) {
            StringBuilder sb = new StringBuilder();
            return sb.append("(").append(aliasfield).append((NOTBETWEEN == express ? " NOT BETWEEN " : " BETWEEN "))
                    .append(formatToString(range.getMin())).append(" AND ").append(formatToString(range.getMax())).append(")");
        }

        private <E> Predicate<E> getRangePredicate(final Attribute<E, ?> attr, Range range) {
            final Comparable min = range.getMin();
            final Comparable max = range.getMax();
            Predicate<E> p = (E t) -> {
                Comparable rs = (Comparable) attr.get(t);
                if (rs == null) return false;
                if (min != null && min.compareTo(rs) >= 0) return false;
                return !(max != null && max.compareTo(rs) <= 0);
            };
            return (express == NOTBETWEEN) ? p.negate() : p;
        }

        private <E> Predicate<E> getArrayPredicate(final Attribute<E, ?> attr, Object beanValue) {
            if (beanValue == null) return null;
            Predicate<E> p;
            if (type.isArray()) {
                if (Array.getLength(beanValue) == 0) return null;
                final Class comp = type.getComponentType();
                if (comp == int.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((int[]) beanValue, (int) rs) >= 0;
                    };
                } else if (comp == long.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((long[]) beanValue, (long) rs) >= 0;
                    };
                } else if (comp == short.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((short[]) beanValue, (short) rs) >= 0;
                    };
                } else if (comp == float.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((float[]) beanValue, (float) rs) >= 0;
                    };
                } else if (comp == double.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((double[]) beanValue, (double) rs) >= 0;
                    };
                } else if (comp == byte.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((byte[]) beanValue, (byte) rs) >= 0;
                    };
                } else if (comp == char.class) {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((char[]) beanValue, (char) rs) >= 0;
                    };
                } else {
                    p = (E t) -> {
                        Object rs = attr.get(t);
                        if (rs == null) return false;
                        return Arrays.binarySearch((Object[]) beanValue, rs) >= 0;
                    };
                }
            } else { // Collection
                Collection collection = (Collection) beanValue;
                if (collection.isEmpty()) return null;
                p = (E t) -> {
                    Object rs = attr.get(t);
                    if (rs == null) return false;
                    return collection.contains(rs);
                };
            }
            return p == null ? null : (express == NOTIN) ? p.negate() : p;
        }

        @Override
        public <E> Predicate<E> getFilterPredicate(EntityInfo<E> info, T bean) {
            return getFilterPredicate(info.getAttribute(field), bean);
        }

        private <E> Predicate<E> getFilterPredicate(final Attribute<E, ?> attr, T bean) {
            final F beanValue = attribute.get(bean);
            if (beanValue == null) return null;
            if (string && ((CharSequence) beanValue).length() == 0) return null;
            if (number && ((Number) beanValue).longValue() < this.least) return null;
            if (Range.class.isAssignableFrom(type)) return getRangePredicate(attr, (Range) beanValue);
            if (type.isArray() || Collection.class.isAssignableFrom(type)) return getArrayPredicate(attr, (Range) beanValue);
            final long beanLongValue = number ? ((Number) beanValue).longValue() : 0L;
            switch (express) {
                case EQUAL: return (E t) -> beanValue.equals(attr.get(t));
                case NOTEQUAL: return (E t) -> !beanValue.equals(attr.get(t));
                case GREATERTHAN: return (E t) -> ((Number) attr.get(t)).longValue() > beanLongValue;
                case LESSTHAN: return (E t) -> ((Number) attr.get(t)).longValue() < beanLongValue;
                case GREATERTHANOREQUALTO: return (E t) -> ((Number) attr.get(t)).longValue() >= beanLongValue;
                case LESSTHANOREQUALTO: return (E t) -> ((Number) attr.get(t)).longValue() <= beanLongValue;
                case LIKE: if (!ignoreCase) return (E t) -> {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().contains(beanValue.toString());
                    };
                    String v1 = beanValue.toString().toLowerCase();
                    return (E t) -> {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().toLowerCase().contains(v1);
                    };
                case NOTLIKE: if (!ignoreCase) return (E t) -> {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().contains(beanValue.toString());
                    };
                    String v2 = beanValue.toString().toLowerCase();
                    return (E t) -> {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().toLowerCase().contains(v2);
                    };
                case ISNULL: return (E t) -> attr.get(t) == null;
                case ISNOTNULL: return (E t) -> attr.get(t) != null;
                case OPAND: return (E t) -> (((Number) attr.get(t)).longValue() & beanLongValue) > 0;
                case OPOR: return (E t) -> (((Number) attr.get(t)).longValue() | beanLongValue) > 0;
                case OPANDNO: return (E t) -> (((Number) attr.get(t)).longValue() & beanLongValue) == 0;
            }
            return null;
        }

    }

    static interface FilterExpressNode<T> {

        public StringBuilder getFilterExpress(final T bean);

        public <E> Predicate<E> getFilterPredicate(final EntityInfo<E> info, final T bean);
    }
}
