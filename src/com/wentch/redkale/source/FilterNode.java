/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class FilterNode {

    private boolean signand = true;

    private String column;

    private FilterExpress express;

    private Serializable value;

    private FilterNode[] siblings;

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
        if (siblings == null) {
            siblings = new FilterNode[]{node};
            this.signand = sign;
            return this;
        }
        if (signand == sign) {
            FilterNode[] newsiblings = new FilterNode[siblings.length + 1];
            System.arraycopy(siblings, 0, newsiblings, 0, siblings.length);
            newsiblings[siblings.length] = node;
            this.siblings = newsiblings;
            return this;
        }
        FilterNode newnode = new FilterNode(this.column, this.express, this.value);
        newnode.signand = this.signand;
        newnode.siblings = this.siblings;
        this.siblings = new FilterNode[]{newnode};
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
        if (siblings == null) {
            sb.append(column).append(' ').append(express.value()).append(' ').append(formatValue());
        } else {
            sb.append('(').append(column).append(' ').append(express.value()).append(' ').append(formatValue());
            for (FilterNode node : this.siblings) {
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

    public FilterNode[] getSiblings() {
        return siblings;
    }

    public void setSiblings(FilterNode[] siblings) {
        this.siblings = siblings;
    }

}
