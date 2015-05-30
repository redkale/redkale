/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 */
public abstract class FilterNode {

    protected boolean signand = true;

    protected String column;

    protected FilterExpress express;

    protected FilterNode[] nodes;

    public final FilterNode and(FilterNode node) {
        return any(node, true);
    }

    public final FilterNode and(String column, Serializable value) {
        return and(new FilterSimpleNode(column, null, value));
    }

    public final FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterSimpleNode(column, express, value));
    }

    public final FilterNode or(FilterNode node) {
        return any((FilterSimpleNode) node, false);
    }

    public final FilterNode or(String column, Serializable value) {
        return or(new FilterSimpleNode(column, null, value));
    }

    public final FilterNode or(String column, FilterExpress express, Serializable value) {
        return or(new FilterSimpleNode(column, express, value));
    }

    protected final FilterNode any(FilterNode node, boolean sign) {
        Objects.requireNonNull(node);
        if (nodes == null) {
            nodes = new FilterNode[]{node};
            this.signand = sign;
            return this;
        }
        if (signand == sign) {
            FilterNode[] newsiblings = new FilterNode[nodes.length + 1];
            System.arraycopy(nodes, 0, newsiblings, 0, nodes.length);
            newsiblings[nodes.length] = node;
            this.nodes = newsiblings;
            return this;
        }
        this.copyFrom(node, sign);
        return this;
    }

    protected abstract void copyFrom(FilterNode node, boolean sign);

    protected abstract Serializable getValue(Object bean);

    public static FilterNode create(String column, Serializable value) {
        return create(column, FilterExpress.EQUAL, value);
    }

    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return new FilterSimpleNode(column, express, value);
    }

    protected final <T> Predicate<T> createFilterPredicate(final EntityInfo<T> info, Object bean) {
        if (info == null) return null;
        final Serializable val = getValue(bean);
        if (val == null && express != ISNULL && express != ISNOTNULL) return null;
        Predicate<T> filter = createFilterPredicate(info.getAttribute(column), val);
        if (nodes == null) return filter;
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createFilterPredicate(info, bean);
            if (f == null) continue;
            filter = (filter == null) ? f : (signand ? filter.and(f) : filter.or(f));
        }
        return filter;
    }

    private <T> Predicate<T> createFilterPredicate(final Attribute<T, ?> attr, final Serializable val) {
        if (attr == null) return null;
        switch (express) {
            case EQUAL: return (T t) -> val.equals(attr.get(t));
            case NOTEQUAL: return (T t) -> !val.equals(attr.get(t));
            case GREATERTHAN: return (T t) -> ((Number) attr.get(t)).longValue() > ((Number) val).longValue();
            case LESSTHAN: return (T t) -> ((Number) attr.get(t)).longValue() < ((Number) val).longValue();
            case GREATERTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() >= ((Number) val).longValue();
            case LESSTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() <= ((Number) val).longValue();
            case ISNULL: return (T t) -> attr.get(t) == null;
            case ISNOTNULL: return (T t) -> attr.get(t) != null;
            case OPAND: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) > 0;
            case OPOR: return (T t) -> (((Number) attr.get(t)).longValue() | ((Number) val).longValue()) > 0;
            case OPANDNO: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) == 0;
            case LIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs != null && rs.toString().contains(val.toString());
                };
            case NOTLIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs == null || !rs.toString().contains(val.toString());
                };
            case BETWEEN:
            case NOTBETWEEN:
                Range range = (Range) val;
                final Comparable min = range.getMin();
                final Comparable max = range.getMax();
                Predicate<T> p = (T t) -> {
                    Comparable rs = (Comparable) attr.get(t);
                    if (rs == null) return false;
                    if (min != null && min.compareTo(rs) >= 0) return false;
                    return !(max != null && max.compareTo(rs) <= 0);
                };
                if (express == NOTBETWEEN) p = p.negate();
                return p;
            case IN:
            case NOTIN:
                Predicate<T> filter;
                if (val instanceof Collection) {
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && ((Collection) val).contains(rs);
                    };
                } else {
                    Class type = val.getClass();
                    if (type == int[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((int[]) val, (int) rs) >= 0;
                        };
                    } else if (type == short[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((short[]) val, (short) rs) >= 0;
                        };
                    } else if (type == long[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((long[]) val, (long) rs) >= 0;
                        };
                    } else if (type == float[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((float[]) val, (float) rs) >= 0;
                        };
                    } else if (type == double[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((double[]) val, (double) rs) >= 0;
                        };
                    } else {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            return rs != null && Arrays.binarySearch((Object[]) val, rs) > -1;
                        };
                    }
                }
                if (express == NOTIN) filter = filter.negate();
                return filter;
        }
        return null;
    }

    protected static String formatValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return value.toString();
        if (value instanceof CharSequence) {
            return new StringBuilder().append('"').append(value.toString().replace("\"", "\\\"")).append('"').toString();
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) return null;
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (int i = 0; i < len; i++) {
                Object o = Array.get(value, i);
                if (sb.length() > 0) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('"').append(o.toString().replace("\"", "\\\"")).append('"');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')').toString();
        }
        if (value instanceof Collection) {
            Collection c = (Collection) value;
            if (c.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (Object o : c) {
                if (sb.length() > 0) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('"').append(o.toString().replace("\"", "\\\"")).append('"');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')').toString();
        }
        return String.valueOf(value);
    }

    public boolean isSignand() {
        return signand;
    }

    public void setSignand(boolean signand) {
        this.signand = signand;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public FilterExpress getExpress() {
        return express;
    }

    public void setExpress(FilterExpress express) {
        this.express = express;
    }

    public FilterNode[] getNodes() {
        return nodes;
    }

    public void setNodes(FilterNode[] nodes) {
        this.nodes = nodes;
    }

}
