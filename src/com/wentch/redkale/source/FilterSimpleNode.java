/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public final class FilterSimpleNode extends FilterNode {

    private Serializable value;

    public FilterSimpleNode() {
    }

    FilterSimpleNode(String col, FilterExpress exp, Serializable val) {
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

    @Override
    protected void copyFrom(FilterNode node, boolean sign) {
        FilterSimpleNode newnode = new FilterSimpleNode(this.column, this.express, this.value);
        newnode.signand = this.signand;
        newnode.nodes = this.nodes;
        this.nodes = new FilterNode[]{newnode};
        this.column = node.column;
        this.express = node.express;
        this.signand = sign;
        if (node instanceof FilterSimpleNode) this.value = ((FilterSimpleNode) node).value;
    }

    @Override
    protected Serializable getValue(Object bean) {
        return value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (nodes == null) {
            sb.append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
        } else {
            sb.append('(').append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
            for (FilterNode node : this.nodes) {
                sb.append(signand ? " AND " : " OR ").append(node.toString());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

}
