/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import org.redkale.convert.ConvertColumn;

/**
 * 与ColumnNameNode、ColumnExpNode组合，用于复杂的字段表达式 。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.0.0
 */
public class ColumnFuncNode implements ColumnNode {

    @ConvertColumn(index = 1)
    protected FilterFunc func;

    @ConvertColumn(index = 2)
    protected ColumnNode value;//类型只能是ColumnNameNode、ColumnExpNode、ColumnFuncNode

    public ColumnFuncNode() {
    }

    protected ColumnFuncNode(FilterFunc func, Serializable node) {
        this.func = func;
        this.value = createColumnNode(node);
    }

    protected ColumnNode createColumnNode(Serializable value) {
        if (value instanceof String) {
            return new ColumnNameNode(value.toString());
        } else if (value instanceof ColumnNameNode) {
            return (ColumnNode) value;
        } else if (value instanceof ColumnExpNode) {
            return (ColumnNode) value;
        } else if (value instanceof ColumnFuncNode) {
            return (ColumnNode) value;
        } else {
            throw new IllegalArgumentException("value must be ColumnNameNode or ColumnExpNode or ColumnFuncNode");
        }
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

    public ColumnNode getValue2() {
        return value;
    }

    public void setValue(ColumnNode value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "{\"func\":\"" + func + "\", \"value\":" + value + "}";
    }
}
