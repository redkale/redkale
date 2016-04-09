/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import static org.redkale.source.FilterExpress.EQUAL;
import org.redkale.util.Attribute;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public class FilterJoinNode extends FilterNode {

    private Class joinClass;

    private EntityInfo joinEntity;  //在调用  createSQLJoin  和 isCacheUseable 时会注入

    private String[] joinColumns;

    public FilterJoinNode() {
    }

    protected FilterJoinNode(Class joinClass, String[] joinColumns, String column, FilterExpress express, boolean itemand, Serializable value) {
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
        this.joinColumns = joinColumns;
        this.column = column;
        this.express = express == null ? EQUAL : express;
        this.itemand = itemand;
        this.value = value;
    }

    protected FilterJoinNode(FilterJoinNode node) {
        this(node.joinClass, node.joinColumns, node.column, node.express, node.itemand, node.value);
        this.joinEntity = node.joinEntity;
        this.or = node.or;
        this.nodes = node.nodes;
    }

    public static FilterJoinNode create(Class joinClass, String joinColumn, String column, Serializable value) {
        return create(joinClass, new String[]{joinColumn}, column, value);
    }

    public static FilterJoinNode create(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return create(joinClass, new String[]{joinColumn}, column, express, value);
    }

    public static FilterJoinNode create(Class joinClass, String joinColumn, String column, FilterExpress express, boolean itemand, Serializable value) {
        return create(joinClass, new String[]{joinColumn}, column, express, itemand, value);
    }

    public static FilterJoinNode create(Class joinClass, String[] joinColumns, String column, Serializable value) {
        return create(joinClass, joinColumns, column, null, value);
    }

    public static FilterJoinNode create(Class joinClass, String[] joinColumns, String column, FilterExpress express, Serializable value) {
        return create(joinClass, joinColumns, column, express, true, value);
    }

    public static FilterJoinNode create(Class joinClass, String[] joinColumns, String column, FilterExpress express, boolean itemand, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumns, column, express, itemand, value);
    }

    @Override
    protected FilterNode any(final FilterNode node0, boolean signor) {
        Objects.requireNonNull(node0);
        if (!(node0 instanceof FilterJoinNode)) {
            throw new IllegalArgumentException(this + (signor ? " or " : " and ") + " a node but " + String.valueOf(node0) + "is not a " + FilterJoinNode.class.getSimpleName());
        }
        final FilterJoinNode node = (FilterJoinNode) node0;
        if (this.nodes == null) {
            this.nodes = new FilterNode[]{node};
            this.or = signor;
            return this;
        }
        if (or == signor || this.column == null) {
            FilterNode[] newsiblings = new FilterNode[nodes.length + 1];
            System.arraycopy(nodes, 0, newsiblings, 0, nodes.length);
            newsiblings[nodes.length] = node;
            this.nodes = newsiblings;
            if (this.column == null) this.or = signor;
            return this;
        }
        this.nodes = new FilterNode[]{new FilterJoinNode(node), node};
        this.column = null;
        this.express = null;
        this.itemand = true;
        this.value = null;
        this.joinClass = null;
        this.joinEntity = null;
        this.joinColumns = null;
        this.or = signor;
        return this;
    }

    @Override
    protected <T> CharSequence createSQLExpress(final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return super.createSQLExpress(this.joinEntity == null ? info : this.joinEntity, joinTabalis);
    }

    @Override
    protected <T, E> Predicate<T> createPredicate(final EntityCache<T> cache) {
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        final AtomicBoolean more = new AtomicBoolean();
        Predicate<E> filter = createJoinPredicate(more);
        Predicate<T> rs = null;
        if (filter == null && !more.get()) return rs;
        if (filter != null) {
            final Predicate<E> inner = filter;
            rs = new Predicate<T>() {

                @Override
                public boolean test(final T t) {
                    Predicate<E> joinPredicate = null;
                    for (String joinColumn : joinColumns) {
                        final Serializable key = cache.getAttribute(joinColumn).get(t);
                        final Attribute<E, Serializable> joinAttr = joinCache.getAttribute(joinColumn);
                        Predicate<E> p = (E e) -> key.equals(joinAttr.get(e));
                        joinPredicate = joinPredicate == null ? p : joinPredicate.and(p);
                    }
                    return joinCache.exists(inner.and(joinPredicate));
                }

                @Override
                public String toString() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" #-- ON ").append(joinColumns[0]).append("=").append(joinClass == null ? "null" : joinClass.getSimpleName()).append(".").append(joinColumns[0]);
                    for (int i = 1; i < joinColumns.length; i++) {
                        sb.append(" AND ").append(joinColumns[i]).append("=").append(joinClass == null ? "null" : joinClass.getSimpleName()).append(".").append(joinColumns[i]);
                    }
                    sb.append(" --# ").append(inner.toString());
                    return sb.toString();
                }
            };
        }
        if (more.get()) {  //存在不同Class的关联表
            if (this.nodes != null) {
                for (FilterNode node : this.nodes) {
                    if (((FilterJoinNode) node).joinClass == this.joinClass) continue;
                    Predicate<T> f = node.createPredicate(cache);
                    if (f == null) continue;
                    final Predicate<T> one = rs;
                    final Predicate<T> two = f;
                    rs = (rs == null) ? f : (or ? new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return one.test(t) || two.test(t);
                        }

                        @Override
                        public String toString() {
                            return "(" + one + " OR " + two + ")";
                        }
                    } : new Predicate<T>() {

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
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createElementPredicate(joinCache, true);
        if (this.nodes != null) {
            for (FilterNode node : this.nodes) {
                if (((FilterJoinNode) node).joinClass != this.joinClass) {
                    more.set(true);
                    continue;
                }
                Predicate<E> f = ((FilterJoinNode) node).createJoinPredicate(more);
                if (f == null) continue;
                final Predicate<E> one = filter;
                final Predicate<E> two = f;
                filter = (filter == null) ? f : (or ? new Predicate<E>() {

                    @Override
                    public boolean test(E t) {
                        return one.test(t) || two.test(t);
                    }

                    @Override
                    public String toString() {
                        return "(" + one + " OR " + two + ")";
                    }
                } : new Predicate<E>() {

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
    protected <T> CharSequence createSQLJoin(final Function<Class, EntityInfo> func, final Map<Class, String> joinTabalis, final EntityInfo<T> info) {
        boolean morejoin = false;
        if (this.joinEntity == null) {
            if (this.joinClass != null) this.joinEntity = func.apply(this.joinClass);
            if (this.nodes != null) {
                for (FilterNode node : this.nodes) {
                    if (node instanceof FilterJoinNode) {
                        FilterJoinNode joinNode = ((FilterJoinNode) node);
                        if (joinNode.joinClass != null) {
                            joinNode.joinEntity = func.apply(joinNode.joinClass);
                            if (this.joinClass != null && this.joinClass != joinNode.joinClass) morejoin = true;
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (this.joinClass != null) {
            sb.append(createElementSQLJoin(joinTabalis, info, this));
        }
        if (morejoin) {
            Set<Class> set = new HashSet<>();
            if (this.joinClass != null) set.add(this.joinClass);
            for (FilterNode node : this.nodes) {
                if (node instanceof FilterJoinNode) {
                    FilterJoinNode joinNode = ((FilterJoinNode) node);
                    if (!set.contains(joinNode.joinClass)) {
                        CharSequence cs = createElementSQLJoin(joinTabalis, info, joinNode);
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

    private static CharSequence createElementSQLJoin(final Map<Class, String> joinTabalis, final EntityInfo info, final FilterJoinNode node) {
        if (node.joinClass == null) return null;
        StringBuilder sb = new StringBuilder();
        String[] joinColumns = node.joinColumns;
        sb.append(" INNER JOIN ").append(node.joinEntity.getTable()).append(" ").append(joinTabalis.get(node.joinClass))
            .append(" ON ").append(info.getSQLColumn("a", joinColumns[0])).append(" = ").append(node.joinEntity.getSQLColumn(joinTabalis.get(node.joinClass), joinColumns[0]));
        for (int i = 1; i < joinColumns.length; i++) {
            sb.append(" AND ").append(info.getSQLColumn("a", joinColumns[i])).append(" = ").append(node.joinEntity.getSQLColumn(joinTabalis.get(node.joinClass), joinColumns[i]));
        }
        return sb;
    }

    @Override
    protected boolean isCacheUseable(final Function<Class, EntityInfo> entityApplyer) {
        if (this.joinEntity == null) this.joinEntity = entityApplyer.apply(this.joinClass);
        if (!this.joinEntity.isCacheFullLoaded()) return false;
        if (this.nodes == null) return true;
        for (FilterNode node : this.nodes) {
            if (!node.isCacheUseable(entityApplyer)) return false;
        }
        return true;
    }

    @Override
    protected void putJoinTabalis(Map<Class, String> map) {
        if (this.joinClass != null && !map.containsKey(this.joinClass)) map.put(joinClass, String.valueOf((char) ('b' + map.size())));
        if (this.nodes == null) return;
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
