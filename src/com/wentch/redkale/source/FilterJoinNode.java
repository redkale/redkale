/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 */
public class FilterJoinNode extends FilterNode {

    private Class joinClass;

    private EntityInfo joinEntity;  //在调用  createSQLJoin  和 isCacheUseable 时会注入

    private String[] joinColumns;

    public FilterJoinNode() {
    }

    protected FilterJoinNode(Class joinClass, String[] joinColumns, String column, Serializable value) {
        this(joinClass, joinColumns, column, null, value);
    }

    protected FilterJoinNode(Class joinClass, String[] joinColumns, String column, FilterExpress express, Serializable value) {
        Objects.requireNonNull(joinClass);
        Objects.requireNonNull(joinColumns);
        this.joinClass = joinClass;
        this.joinColumns = joinColumns;
        this.column = column;
        this.express = express;
        this.value = value;
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, Serializable value) {
        return new FilterJoinNode(joinClass, new String[]{joinColumn}, column, value);
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return new FilterJoinNode(joinClass, new String[]{joinColumn}, column, express, value);
    }

    public static FilterNode create(Class joinClass, String[] joinColumns, String column, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumns, column, value);
    }

    public static FilterNode create(Class joinClass, String[] joinColumns, String column, FilterExpress express, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumns, column, express, value);
    }

    @Override
    protected void check(FilterNode node) {
        Objects.requireNonNull(node);
        if (!(node instanceof FilterJoinNode)) throw new IllegalArgumentException(this + " check " + String.valueOf(node) + "is not a " + FilterJoinNode.class.getSimpleName());
    }

    @Override
    protected <T> CharSequence createSQLExpress(final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return super.createSQLExpress(this.joinEntity, joinTabalis);
    }

    @Override
    protected <T, E> Predicate<T> createPredicate(final EntityCache<T> cache) {
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createChildPredicate();
        if (filter == null) return null;

        final Predicate<E> inner = filter;
        return new Predicate<T>() {

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

    private <E> Predicate<E> createChildPredicate() {
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createElementPredicate(joinCache, true);
        if (this.nodes != null) {
            for (FilterNode node : this.nodes) {
                Predicate<E> f = ((FilterJoinNode) node).createChildPredicate();
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
        if (this.joinEntity == null) {
            this.joinEntity = func.apply(this.joinClass);
            if (this.nodes != null) {
                for (FilterNode node : this.nodes) {
                    if (node instanceof FilterJoinNode) {
                        ((FilterJoinNode) node).joinEntity = func.apply(((FilterJoinNode) node).joinClass);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(" INNER JOIN ").append(joinEntity.getTable()).append(" ").append(joinTabalis.get(this.joinClass))
                .append(" ON ").append(info.getSQLColumn("a", joinColumns[0])).append(" = ").append(this.joinEntity.getSQLColumn(joinTabalis.get(this.joinClass), joinColumns[0]));
        for (int i = 1; i < joinColumns.length; i++) {
            sb.append(" AND ").append(info.getSQLColumn("a", joinColumns[i])).append(" = ").append(this.joinEntity.getSQLColumn(joinTabalis.get(this.joinClass), joinColumns[i]));
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
        if (!map.containsKey(this.joinClass)) map.put(joinClass, String.valueOf((char) ('b' + map.size())));
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
