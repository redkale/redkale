/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import static org.redkale.source.FilterExpress.EQ;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import org.redkale.util.*;

/**
 * &#64;FilterJoinColumn对应的FilterNode对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class FilterJoinNode extends FilterNode {

    private FilterJoinType joinType;

    private Class joinClass;

    private EntityInfo joinEntity; // 在调用createSQLJoin和isCacheUseable时会注入

    private String[] joinColumns;

    public FilterJoinNode() {}

    protected FilterJoinNode(
            FilterJoinType joinType,
            Class joinClass,
            String[] joinColumns,
            String column,
            FilterExpress express,
            Serializable value) {
        Objects.requireNonNull(joinClass);
        Objects.requireNonNull(joinColumns);
        if (express == null && value != null) {
            if (value instanceof Range) {
                express = FilterExpress.BETWEEN;
            } else if (value instanceof Collection) {
                express = FilterExpress.IN;
            } else if (value.getClass().isArray()) {
                express = FilterExpress.IN;
            }
        }
        this.joinClass = joinClass;
        this.joinType = joinType;
        this.joinColumns = joinColumns;
        this.column = column;
        this.express = express == null ? EQ : FilterNodes.oldExpress(express);
        this.value = value;
    }

    protected FilterJoinNode(FilterJoinNode node) {
        this(node.joinType, node.joinClass, node.joinColumns, node.column, node.express, node.value);
        this.joinEntity = node.joinEntity;
        this.or = node.or;
        this.nodes = node.nodes;
    }

    @Deprecated(since = "2.8.0")
    public static FilterJoinNode create(Class joinClass, String joinColumn, String column, Serializable value) {
        return FilterNodes.joinInner(joinClass, new String[] {joinColumn}, column, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterJoinNode create(
            Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return FilterNodes.joinInner(joinClass, new String[] {joinColumn}, column, express, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterJoinNode create(Class joinClass, String[] joinColumns, String column, Serializable value) {
        return FilterNodes.joinInner(joinClass, joinColumns, column, null, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterJoinNode create(
            Class joinClass, String[] joinColumns, String column, FilterExpress express, Serializable value) {
        return FilterNodes.joinInner(joinClass, joinColumns, column, express, value);
    }

    @Override
    public FilterJoinNode copy() {
        FilterJoinNode node = (FilterJoinNode) copy(new FilterJoinNode());
        node.joinClass = this.joinClass;
        node.joinEntity = this.joinEntity;
        if (this.joinColumns != null) {
            node.joinColumns = new String[this.joinColumns.length];
            System.arraycopy(this.joinColumns, 0, node.joinColumns, 0, this.joinColumns.length);
        }
        return node;
    }

    @Override
    protected FilterNode any(final FilterNode node0, boolean isOr) {
        Objects.requireNonNull(node0);
        if (!(node0 instanceof FilterJoinNode)) {
            throw new IllegalArgumentException(this + (isOr ? " or " : " and ") + " a node but " + String.valueOf(node0)
                    + " is not a " + FilterJoinNode.class.getSimpleName());
        }
        final FilterJoinNode node = (FilterJoinNode) node0;
        if (this.nodes == null) {
            this.nodes = new FilterNode[] {node};
            this.or = isOr;
            return this;
        }
        if (or == isOr || this.column == null) {
            this.nodes = Utility.append(this.nodes, node);
            if (this.column == null) {
                this.or = isOr;
            }
            return this;
        }
        this.nodes = new FilterNode[] {new FilterJoinNode(this), node};
        this.column = null;
        this.express = null;
        this.value = null;
        this.joinClass = null;
        this.joinEntity = null;
        this.joinColumns = null;
        this.or = isOr;
        return this;
    }

    @Override
    protected <T, E> Predicate<T> createPredicate(final EntityCache<T> cache) {
        if (column == null && this.nodes == null) {
            return null;
        }
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        final AtomicBoolean more = new AtomicBoolean();
        Predicate<E> filter = createJoinPredicate(more);
        Predicate<T> rs = null;
        if (filter == null && !more.get()) {
            return rs;
        }
        if (filter != null) {
            final Predicate<E> inner = filter;
            final String[][] localJoinColumns = new String[joinColumns.length][2];
            for (int i = 0; i < joinColumns.length; i++) {
                int pos = joinColumns[i].indexOf('=');
                if (pos > 0) {
                    localJoinColumns[i] =
                            new String[] {joinColumns[i].substring(0, pos), joinColumns[i].substring(pos + 1)};
                } else {
                    localJoinColumns[i] = new String[] {joinColumns[i], joinColumns[i]};
                }
            }
            rs = new Predicate<T>() {

                @Override
                public boolean test(final T t) {
                    Predicate<E> joinPredicate = null;
                    for (String[] localJoinColumn : localJoinColumns) {
                        final Serializable key =
                                cache.getAttribute(localJoinColumn[0]).get(t);
                        final Attribute<E, Serializable> joinAttr = joinCache.getAttribute(localJoinColumn[1]);
                        Predicate<E> p = (E e) -> key.equals(joinAttr.get(e));
                        joinPredicate = joinPredicate == null ? p : joinPredicate.and(p);
                    }
                    return joinCache.exists(inner.and(joinPredicate));
                }

                @Override
                public String toString() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" #-- ON ")
                            .append(localJoinColumns[0][0])
                            .append("=")
                            .append(joinClass == null ? "null" : joinClass.getSimpleName())
                            .append(".")
                            .append(localJoinColumns[0][1]);
                    for (int i = 1; i < localJoinColumns.length; i++) {
                        sb.append(" AND ")
                                .append(localJoinColumns[i][0])
                                .append("=")
                                .append(joinClass == null ? "null" : joinClass.getSimpleName())
                                .append(".")
                                .append(localJoinColumns[i][1]);
                    }
                    sb.append(" --# ").append(inner.toString());
                    return sb.toString();
                }
            };
        }
        if (more.get()) { // 存在不同Class的关联表
            if (this.nodes != null) {
                for (FilterNode node : this.nodes) {
                    if (((FilterJoinNode) node).joinClass == this.joinClass) {
                        continue;
                    }
                    Predicate<T> f = node.createPredicate(cache);
                    if (f == null) {
                        continue;
                    }
                    final Predicate<T> one = rs;
                    final Predicate<T> two = f;
                    rs = (rs == null)
                            ? f
                            : (or
                                    ? new Predicate<T>() {

                                        @Override
                                        public boolean test(T t) {
                                            return one.test(t) || two.test(t);
                                        }

                                        @Override
                                        public String toString() {
                                            return "(" + one + " OR " + two + ")";
                                        }
                                    }
                                    : new Predicate<T>() {

                                        @Override
                                        public boolean test(T t) {
                                            return one.test(t) && two.test(t);
                                        }

                                        @Override
                                        public String toString() {
                                            return "(" + one + " AND " + two + ")";
                                        }
                                    });
                }
            }
        }
        return rs;
    }

    private <E> Predicate<E> createJoinPredicate(final AtomicBoolean more) {
        if (column == null && this.nodes == null) {
            return null;
        }
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createElementPredicate(joinCache, true);
        if (this.nodes != null) {
            for (FilterNode node : this.nodes) {
                if (((FilterJoinNode) node).joinClass != this.joinClass) {
                    more.set(true);
                    continue;
                }
                Predicate<E> f = ((FilterJoinNode) node).createJoinPredicate(more);
                if (f == null) {
                    continue;
                }
                final Predicate<E> one = filter;
                final Predicate<E> two = f;
                filter = (filter == null)
                        ? f
                        : (or
                                ? new Predicate<E>() {

                                    @Override
                                    public boolean test(E t) {
                                        return one.test(t) || two.test(t);
                                    }

                                    @Override
                                    public String toString() {
                                        return "(" + one + " OR " + two + ")";
                                    }
                                }
                                : new Predicate<E>() {

                                    @Override
                                    public boolean test(E t) {
                                        return one.test(t) && two.test(t);
                                    }

                                    @Override
                                    public String toString() {
                                        return "(" + one + " AND " + two + ")";
                                    }
                                });
            }
        }
        return filter;
    }

    @Override
    protected <T> CharSequence createSQLExpress(
            AbstractDataSqlSource source, final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return super.createSQLExpress(source, this.joinEntity == null ? info : this.joinEntity, joinTabalis);
    }

    @Override
    protected <T> CharSequence createSQLJoin(
            final Function<Class, EntityInfo> func,
            final boolean update,
            final Map<Class, String> joinTabalis,
            final Set<String> haset,
            final EntityInfo<T> info) {
        boolean moreJoin = false;
        if (this.joinEntity == null) {
            if (this.joinClass != null) {
                this.joinEntity = func.apply(this.joinClass);
            }
            if (this.nodes != null) {
                for (FilterNode node : this.nodes) {
                    if (node instanceof FilterJoinNode) {
                        FilterJoinNode joinNode = ((FilterJoinNode) node);
                        if (joinNode.joinClass != null) {
                            joinNode.joinEntity = func.apply(joinNode.joinClass);
                            if (this.joinClass != null && this.joinClass != joinNode.joinClass) {
                                moreJoin = true;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (this.joinClass != null) {
            CharSequence cs = createElementSQLJoin(update, joinTabalis, haset, info, this);
            if (cs != null) {
                sb.append(cs);
            }
        }
        if (moreJoin) {
            Set<Class> set = new HashSet<>();
            if (this.joinClass != null) {
                set.add(this.joinClass);
            }
            for (FilterNode node : this.nodes) {
                if (node instanceof FilterJoinNode) {
                    FilterJoinNode joinNode = ((FilterJoinNode) node);
                    if (!set.contains(joinNode.joinClass)) {
                        CharSequence cs = createElementSQLJoin(update, joinTabalis, haset, info, joinNode);
                        if (cs != null) {
                            sb.append(cs);
                            set.add(joinNode.joinClass);
                        }
                    }
                }
            }
        }
        return sb;
    }

    private static CharSequence createElementSQLJoin(
            final boolean update,
            final Map<Class, String> joinTabalis,
            final Set<String> haset,
            final EntityInfo info,
            final FilterJoinNode node) {
        if (node.joinClass == null || (haset != null && haset.contains(joinTabalis.get(node.joinClass)))) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String[] joinCols = node.joinColumns;
        int pos = joinCols[0].indexOf('=');
        if (update) {
            sb.append('[')
                    .append(node.joinEntity.getTable(node))
                    .append(" ")
                    .append(joinTabalis.get(node.joinClass))
                    .append(']');
            sb.append('{')
                    .append(info.getSQLColumn("a", pos > 0 ? joinCols[0].substring(0, pos) : joinCols[0]))
                    .append(" = ")
                    .append(node.joinEntity.getSQLColumn(
                            joinTabalis.get(node.joinClass), pos > 0 ? joinCols[0].substring(pos + 1) : joinCols[0]));
            for (int i = 1; i < joinCols.length; i++) {
                pos = joinCols[i].indexOf('=');
                sb.append(" AND ")
                        .append(info.getSQLColumn("a", pos > 0 ? joinCols[i].substring(0, pos) : joinCols[i]))
                        .append(" = ")
                        .append(node.joinEntity.getSQLColumn(
                                joinTabalis.get(node.joinClass),
                                pos > 0 ? joinCols[i].substring(pos + 1) : joinCols[i]));
            }
            sb.append('}');
        } else {
            sb.append(" ")
                    .append(node.joinType)
                    .append(" JOIN ")
                    .append(node.joinEntity.getTables(node)[0])
                    .append(" ")
                    .append(joinTabalis.get(node.joinClass))
                    .append(" ON ")
                    .append(info.getSQLColumn("a", pos > 0 ? joinCols[0].substring(0, pos) : joinCols[0]))
                    .append(" = ")
                    .append(node.joinEntity.getSQLColumn(
                            joinTabalis.get(node.joinClass), pos > 0 ? joinCols[0].substring(pos + 1) : joinCols[0]));
            for (int i = 1; i < joinCols.length; i++) {
                pos = joinCols[i].indexOf('=');
                sb.append(" AND ")
                        .append(info.getSQLColumn("a", pos > 0 ? joinCols[i].substring(0, pos) : joinCols[i]))
                        .append(" = ")
                        .append(node.joinEntity.getSQLColumn(
                                joinTabalis.get(node.joinClass),
                                pos > 0 ? joinCols[i].substring(pos + 1) : joinCols[i]));
            }
        }
        if (haset != null) {
            haset.add(joinTabalis.get(node.joinClass));
        }
        return sb;
    }

    @Override
    protected boolean isCacheUseable(final Function<Class, EntityInfo> entityApplyer) {
        if (this.joinEntity == null) {
            this.joinEntity = entityApplyer.apply(this.joinClass);
        }
        if (!this.joinEntity.isCacheFullLoaded()) {
            return false;
        }
        if (this.nodes == null) {
            return true;
        }
        for (FilterNode node : this.nodes) {
            if (!node.isCacheUseable(entityApplyer)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void putJoinTabalis(Map<Class, String> map) {
        if (this.joinClass != null && !map.containsKey(this.joinClass)) {
            char[] chs = this.joinClass.getSimpleName().toCharArray();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chs.length; i++) {
                char ch = chs[i];
                if (i == 0 || Character.isUpperCase(ch)) {
                    sb.append(Character.toLowerCase(ch));
                }
            }
            String alis = sb.toString();
            if (map.values().contains(alis)) {
                map.put(joinClass, "jt" + map.size()); // join_table_1
            } else {
                map.put(joinClass, alis);
            }
        }
        if (this.nodes == null) {
            return;
        }
        for (FilterNode node : this.nodes) {
            node.putJoinTabalis(map);
        }
    }

    @Override
    protected final boolean isjoin() {
        return true;
    }

    @Override
    public String toString() {
        return toString(joinClass == null ? null : joinClass.getSimpleName()).toString();
    }

    public FilterJoinType getJoinType() {
        return joinType;
    }

    public Class getJoinClass() {
        return joinClass;
    }

    public void setJoinClass(Class joinClass) {
        this.joinClass = joinClass;
    }

    public String[] getJoinColumns() {
        return joinColumns;
    }

    public void setJoinColumns(String[] joinColumns) {
        this.joinColumns = joinColumns;
    }
}
