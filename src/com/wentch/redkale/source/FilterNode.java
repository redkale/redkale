/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

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

    public FilterNode(String column, FilterExpress express, Serializable value) {
        Objects.requireNonNull(column);
        Objects.requireNonNull(express);
        this.column = column;
        this.express = express;
        this.value = value;
    }

    public FilterNode and(FilterNode node) {
        return any(node, true);
    }

    public FilterNode and(String column, Serializable value) {
        return and(new FilterNode(column, FilterExpress.EQUAL, value));
    }

    public FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterNode(column, express, value));
    }

    public FilterNode or(FilterNode node) {
        return any(node, false);
    }

    public FilterNode or(String column, Serializable value) {
        return or(new FilterNode(column, FilterExpress.EQUAL, value));
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
        if (nodes == null || filter == null) return filter;
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createFilterPredicate(info);
            if (f != null) filter = signand ? filter.and(f) : filter.or(f);
        }
        return filter;
    }

    private <T> Predicate<T> createFilterPredicate(final Attribute<T, ?> attr) {
        Predicate<T> filter = null;
        switch (express) {
            case EQUAL:
                filter = (T t) -> value.equals(attr.get(t));
                break;
            case NOTEQUAL:
                filter = (T t) -> !value.equals(attr.get(t));
                break;
            case LIKE:
                filter = (T t) -> {
                    Object rs = attr.get(t);
                    return rs != null && rs.toString().contains(value.toString());
                };
                break;
            case NOTLIKE:
                filter = (T t) -> {
                    Object rs = attr.get(t);
                    return rs == null || !rs.toString().contains(value.toString());
                };
                break;
            case GREATERTHAN:
                filter = (T t) -> ((Number) attr.get(t)).longValue() > ((Number) value).longValue();
                break;
            case LESSTHAN:
                filter = (T t) -> ((Number) attr.get(t)).longValue() < ((Number) value).longValue();
                break;
            case GREATERTHANOREQUALTO:
                filter = (T t) -> ((Number) attr.get(t)).longValue() >= ((Number) value).longValue();
                break;
            case LESSTHANOREQUALTO:
                filter = (T t) -> ((Number) attr.get(t)).longValue() <= ((Number) value).longValue();
                break;
            case IN:
            case NOTIN:
                if (value instanceof Collection) {
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && ((Collection) value).contains(rs);
                    };
                } else {
                    Serializable[] keys;
                    if (value.getClass().isArray()) {
                        Class keytype = value.getClass();
                        if (keytype.getComponentType().isPrimitive()) {
                            Object array = value;
                            Serializable[] keys0 = new Serializable[Array.getLength(array)];
                            for (int i = 0; i < keys0.length; i++) {
                                keys0[i] = (Serializable) Array.get(array, i);
                            }
                            keys = keys0;
                        } else {
                            keys = (Serializable[]) value;
                        }
                    } else {
                        keys = new Serializable[]{value};
                    }
                    Serializable[] keys0 = keys;
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && Arrays.binarySearch(keys0, rs) > -1;
                    };
                }
                if (express == FilterExpress.NOTIN) filter = filter.negate();
                break;
            case ISNULL:
                filter = (T t) -> attr.get(t) == null;
                break;
            case ISNOTNULL:
                filter = (T t) -> attr.get(t) != null;
                break;
            case OPAND:
                filter = (T t) -> (((Number) attr.get(t)).longValue() & ((Number) value).longValue()) > 0;
                break;
            case OPOR:
                filter = (T t) -> (((Number) attr.get(t)).longValue() | ((Number) value).longValue()) > 0;
                break;
            case OPANDNO:
                filter = (T t) -> (((Number) attr.get(t)).longValue() & ((Number) value).longValue()) == 0;
                break;
        }
        return filter;
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
