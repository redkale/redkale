/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import static org.redkale.source.ColumnExpress.*;

/**
 * 创建ColumnNode的工具类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class ColumnNodes {

    private ColumnNodes() {
        //do nothing
    }

    public static ColumnNameNode column(String column) {
        return new ColumnNameNode(column);
    }

    public static ColumnNumberNode number(Number value) {
        return new ColumnNumberNode(value);
    }

    public static ColumnStringNode string(String value) {
        return new ColumnStringNode(value);
    }

    public static ColumnFuncNode func(FilterFunc func, Serializable node) {
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

    public static ColumnExpNode exp(Serializable left, ColumnExpress express, Serializable right) {
        return new ColumnExpNode(left, express, right);
    }

    public static ColumnExpNode mov(String left) {
        return new ColumnExpNode(left, MOV, null);
    }

    public static ColumnExpNode inc(Serializable left, Serializable right) {
        return new ColumnExpNode(left, INC, right);
    }

    public static ColumnExpNode dec(Serializable left, Serializable right) {
        return new ColumnExpNode(left, DEC, right);
    }

    public static ColumnExpNode mul(Serializable left, Serializable right) {
        return new ColumnExpNode(left, MUL, right);
    }

    public static ColumnExpNode div(Serializable left, Serializable right) {
        return new ColumnExpNode(left, DIV, right);
    }

    public static ColumnExpNode mod(Serializable left, Serializable right) {
        return new ColumnExpNode(left, MOD, right);
    }

    public static ColumnExpNode and(Serializable left, Serializable right) {
        return new ColumnExpNode(left, AND, right);
    }

    public static ColumnExpNode orr(Serializable left, Serializable right) {
        return new ColumnExpNode(left, ORR, right);
    }

}
