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
public class FilterNode {

    private boolean signand = true;

    private String column;

    private FilterExpress express;

    private Serializable value;

    private FilterNode[] nodes;

    public FilterNode() {
    }

    public FilterNode(String col, FilterExpress exp, Serializable val) {
        Objects.requireNonNull(col);
        if (exp == null) {
            if (val instanceof Range) {
                exp = FilterExpress.BETWEEN;
            } else if (val instanceof Collection) {
                exp = FilterExpress.IN;
            } else if (val != null && val.getClass().isArray()) {
                exp = FilterExpress.IN;
            } else {
                exp = FilterExpress.EQUAL;
            }
        }
        this.column = col;
        this.express = exp;
        this.value = val;
    }

    public FilterNode and(FilterNode node) {
        return any(node, true);
    }

    public FilterNode and(String column, Serializable value) {
        return and(new FilterNode(column, null, value));
    }

    public FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterNode(column, express, value));
    }

    public FilterNode or(FilterNode node) {
        return any(node, false);
    }

    public FilterNode or(String column, Serializable value) {
        return or(new FilterNode(column, null, value));
    }

    public FilterNode or(String column, FilterExpress express, Serializable value) {
        return or(new FilterNode(column, express, value));
    }

    private FilterNode any(FilterNode node, boolean sign) {
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
        FilterNode newnode = new FilterNode(this.column, this.express, this.value);
        newnode.signand = this.signand;
        newnode.nodes = this.nodes;
        this.nodes = new FilterNode[]{newnode};
        this.column = node.column;
        this.express = node.express;
        this.value = node.value;
        this.signand = sign;
        return this;
    }

    public static FilterNode create(String column, Serializable value) {
        return create(column, FilterExpress.EQUAL, value);
    }

    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return new FilterNode(column, express, value);
    }

    <T> Predicate<T> createFilterPredicate(final EntityInfo<T> info) {
        if (info == null) return null;
        Predicate<T> filter = createFilterPredicate(info.getAttribute(column));
        if (nodes == null) return filter;
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createFilterPredicate(info);
            if (f == null) continue;
            filter = (filter == null) ? f : (signand ? filter.and(f) : filter.or(f));
        }
        return filter;
    }

    private <T> Predicate<T> createFilterPredicate(final Attribute<T, ?> attr) {
        if (attr == null) return null;
        if (value == null && express != ISNULL && express != ISNOTNULL) return null;
        switch (express) {
            case EQUAL: return (T t) -> value.equals(attr.get(t));
            case NOTEQUAL: return (T t) -> !value.equals(attr.get(t));
            case GREATERTHAN: return (T t) -> ((Number) attr.get(t)).longValue() > ((Number) value).longValue();
            case LESSTHAN: return (T t) -> ((Number) attr.get(t)).longValue() < ((Number) value).longValue();
            case GREATERTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() >= ((Number) value).longValue();
            case LESSTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() <= ((Number) value).longValue();
            case ISNULL: return (T t) -> attr.get(t) == null;
            case ISNOTNULL: return (T t) -> attr.get(t) != null;
            case OPAND: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) value).longValue()) > 0;
            case OPOR: return (T t) -> (((Number) attr.get(t)).longValue() | ((Number) value).longValue()) > 0;
            case OPANDNO: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) value).longValue()) == 0;
            case LIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs != null && rs.toString().contains(value.toString());
                };
            case NOTLIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs == null || !rs.toString().contains(value.toString());
                };
            case BETWEEN:
            case NOTBETWEEN:
                Range range = (Range) value;
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
                if (value instanceof Collection) {
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && ((Collection) value).contains(rs);
                    };
                } else {
                    Class type = value.getClass();
                    if (type == int[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((int[]) value, (int) rs) >= 0;
                        };
                    } else if (type == short[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((short[]) value, (short) rs) >= 0;
                        };
                    } else if (type == long[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((long[]) value, (long) rs) >= 0;
                        };
                    } else if (type == float[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((float[]) value, (float) rs) >= 0;
                        };
                    } else if (type == double[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((double[]) value, (double) rs) >= 0;
                        };
                    } else {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            return rs != null && Arrays.binarySearch((Object[]) value, rs) > -1;
                        };
                    }
                }
                if (express == NOTIN) filter = filter.negate();
                return filter;
        }
        return null;
    }

    private String formatValue() {
        if (value == null) return "null";
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof CharSequence) {
            return new StringBuilder().append('"').append(value.toString().replace("\"", "\\\"")).append('"').toString();
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (int i = 0; i < len; i++) {
                Object o = Array.get(value, i);
                if (sb.length() > 0) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('"').append(o.toString().replace("\"", "\\\"")).append('"');
                } else {
                    sb.append('"').append(o).append('"');
                }
            }
            return sb.append(')').toString();
        }
        if (value instanceof Collection) {
            Collection c = (Collection) value;
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (Object o : c) {
                if (sb.length() > 0) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('"').append(o.toString().replace("\"", "\\\"")).append('"');
                } else {
                    sb.append('"').append(o).append('"');
                }
            }
            return sb.append(')').toString();
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (nodes == null) {
            sb.append(column).append(' ').append(express.value()).append(' ').append(formatValue());
        } else {
            sb.append('(').append(column).append(' ').append(express.value()).append(' ').append(formatValue());
            for (FilterNode node : this.nodes) {
                sb.append(signand ? " AND " : " OR ").append(node.toString());
            }
            sb.append(')');
        }
        return sb.toString();
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

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

    public FilterNode[] getNodes() {
        return nodes;
    }

    public void setNodes(FilterNode[] nodes) {
        this.nodes = nodes;
    }

}
