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

    private String joinColumn;

    public FilterJoinNode() {
    }

    protected FilterJoinNode(Class joinClass, String joinColumn, String column, Serializable value) {
        this(joinClass, joinColumn, column, null, value);
    }

    protected FilterJoinNode(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        Objects.requireNonNull(joinClass);
        Objects.requireNonNull(joinColumn);
        this.joinClass = joinClass;
        this.joinColumn = joinColumn;
        this.column = column;
        this.express = express;
        this.value = value;
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumn, column, value);
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumn, column, express, value);
    }

    @Override
    protected <T> CharSequence createSQLExpress(final EntityInfo<T> info, final Map<Class, String> joinTabalis, final FilterBean bean) {
        return super.createSQLExpress(this.joinEntity, joinTabalis, bean);
    }

    @Override
    protected <T, E> Predicate<T> createPredicate(final EntityCache<T> cache, final FilterBean bean) {
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createChildPredicate(bean);
        if (filter == null) return null;
        final Attribute<T, Serializable> attr = cache.getAttribute(this.joinColumn);
        final Attribute<E, Serializable> joinAttr = joinCache.getAttribute(this.joinColumn);
        final Predicate<E> inner = filter;
        return new Predicate<T>() {

            @Override
            public boolean test(T t) {
                return joinCache.exists(inner.and((e) -> attr.get(t).equals(joinAttr.get(e))));
            }

            @Override
            public String toString() {
                return " # ON " + joinColumn + "=" + (joinClass == null ? "null" : joinClass.getSimpleName()) + "." + joinColumn + " # " + inner.toString();
            }
        };
    }

    private <E> Predicate<E> createChildPredicate(final FilterBean bean) {
        if (column == null && this.nodes == null) return null;
        final EntityCache<E> joinCache = this.joinEntity.getCache();
        Predicate<E> filter = createElementPredicate(joinCache, true, bean);
        if (this.nodes != null) {
            for (FilterNode node : this.nodes) {
                Predicate<E> f = ((FilterJoinNode) node).createChildPredicate(bean);
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
        return new StringBuilder().append(" INNER JOIN ").append(joinEntity.getTable()).append(" ").append(joinTabalis.get(this.joinClass))
                .append(" ON ").append(info.getSQLColumn("a", joinColumn)).append(" = ").append(this.joinEntity.getSQLColumn(joinTabalis.get(this.joinClass), joinColumn));
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
        return toString(joinClass == null ? null : joinClass.getSimpleName(), null);
    }

    public Class getJoinClass() {
        return joinClass;
    }

    public void setJoinClass(Class joinClass) {
        this.joinClass = joinClass;
    }

    public String getJoinColumn() {
        return joinColumn;
    }

    public void setJoinColumn(String joinColumn) {
        this.joinColumn = joinColumn;
    }

}
