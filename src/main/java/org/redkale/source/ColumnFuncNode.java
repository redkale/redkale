/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 * 与ColumnExpNode 组合，用于复杂的字段表达式 。
 * String 视为 字段名
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.0.0
 */
public class ColumnFuncNode implements ColumnNode {

    protected FilterFunc func;

    protected Serializable value;//类型只能是String、ColumnExpNode

    public ColumnFuncNode() {
    }

    public ColumnFuncNode(FilterFunc func, Serializable node) {
        if (!(node instanceof String) && !(node instanceof ColumnExpNode)) {
            throw new IllegalArgumentException("value must be String or ColumnExpNode");
        }
        this.func = func;
        this.value = node;
    }

    public static ColumnFuncNode create(FilterFunc func, Serializable node) {
        return new ColumnFuncNode(func, node);
    }

    public static ColumnFuncNode avg(Serializable node) {
        return new ColumnFuncNode(FilterFunc.AVG, node);
    }

    public static ColumnFuncNode count(Serializable node) {
        return new ColumnFuncNode(FilterFunc.COUNT, node);
    }

    public static ColumnFuncNode distinctCount(Serializable node) {
        return new ColumnFuncNode(FilterFunc.DISTINCTCOUNT, node);
    }

    public static ColumnFuncNode max(Serializable node) {
        return new ColumnFuncNode(FilterFunc.MAX, node);
    }

    public static ColumnFuncNode min(Serializable node) {
        return new ColumnFuncNode(FilterFunc.MIN, node);
    }

    public static ColumnFuncNode sum(Serializable node) {
        return new ColumnFuncNode(FilterFunc.SUM, node);
    }

    public FilterFunc getFunc() {
        return func;
    }

    public void setFunc(FilterFunc func) {
        this.func = func;
    }

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "{\"func\":\"" + func + "\", \"value\":" + ((value instanceof CharSequence) ? ("\"" + value + "\"") : value) + "}";
    }
}
