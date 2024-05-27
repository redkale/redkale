/*
 *
 */
package org.redkale.source;

import static org.redkale.source.ColumnExpress.*;

/**
 * 创建ColumnNode的工具类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class ColumnNodes {

    private ColumnNodes() {
        // do nothing
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

    public static ColumnFuncNode func(FilterFunc func, String column) {
        return new ColumnFuncNode(func, column(column));
    }

    public static ColumnFuncNode func(FilterFunc func, ColumnNode node) {
        return new ColumnFuncNode(func, node);
    }

    public static ColumnFuncNode avg(String column) {
        return new ColumnFuncNode(FilterFunc.AVG, column(column));
    }

    public static ColumnFuncNode avg(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.AVG, node);
    }

    public static ColumnFuncNode count(String column) {
        return new ColumnFuncNode(FilterFunc.COUNT, column(column));
    }

    public static ColumnFuncNode count(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.COUNT, node);
    }

    public static ColumnFuncNode distinctCount(String column) {
        return new ColumnFuncNode(FilterFunc.DISTINCTCOUNT, column(column));
    }

    public static ColumnFuncNode distinctCount(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.DISTINCTCOUNT, node);
    }

    public static ColumnFuncNode max(String column) {
        return new ColumnFuncNode(FilterFunc.MAX, column(column));
    }

    public static ColumnFuncNode max(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.MAX, node);
    }

    public static ColumnFuncNode min(String column) {
        return new ColumnFuncNode(FilterFunc.MIN, column(column));
    }

    public static ColumnFuncNode min(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.MIN, node);
    }

    public static ColumnFuncNode sum(String column) {
        return new ColumnFuncNode(FilterFunc.SUM, column(column));
    }

    public static ColumnFuncNode sum(ColumnNode node) {
        return new ColumnFuncNode(FilterFunc.SUM, node);
    }

    public static ColumnExpNode exp(ColumnNode left, ColumnExpress express, ColumnNode right) {
        return new ColumnExpNode(left, express, right);
    }

    public static ColumnExpNode set(String column) {
        return new ColumnExpNode(column, SET, null);
    }

    public static ColumnExpNode set(ColumnNameNode left) {
        return new ColumnExpNode(left, SET, null);
    }

    public static ColumnExpNode inc(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), INC, number(rightValue));
    }

    public static ColumnExpNode inc(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, INC, right);
    }

    public static ColumnExpNode inc(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, INC, column(rightColumn));
    }

    public static ColumnExpNode inc(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, INC, number(rightValue));
    }

    public static ColumnExpNode dec(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), DEC, number(rightValue));
    }

    public static ColumnExpNode dec(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, DEC, right);
    }

    public static ColumnExpNode dec(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, DEC, column(rightColumn));
    }

    public static ColumnExpNode dec(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, DEC, number(rightValue));
    }

    public static ColumnExpNode mul(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), MUL, number(rightValue));
    }

    public static ColumnExpNode mul(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, MUL, right);
    }

    public static ColumnExpNode mul(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, MUL, column(rightColumn));
    }

    public static ColumnExpNode mul(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, MUL, number(rightValue));
    }

    public static ColumnExpNode div(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), DIV, number(rightValue));
    }

    public static ColumnExpNode div(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, DIV, right);
    }

    public static ColumnExpNode div(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, DIV, column(rightColumn));
    }

    public static ColumnExpNode div(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, DIV, number(rightValue));
    }

    public static ColumnExpNode mod(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), MOD, number(rightValue));
    }

    public static ColumnExpNode mod(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, MOD, right);
    }

    public static ColumnExpNode mod(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, MOD, column(rightColumn));
    }

    public static ColumnExpNode mod(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, MOD, number(rightValue));
    }

    public static ColumnExpNode and(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), AND, number(rightValue));
    }

    public static ColumnExpNode and(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, AND, right);
    }

    public static ColumnExpNode and(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, AND, column(rightColumn));
    }

    public static ColumnExpNode and(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, AND, number(rightValue));
    }

    public static ColumnExpNode orr(String leftColumn, Number rightValue) {
        return new ColumnExpNode(column(leftColumn), ORR, number(rightValue));
    }

    public static ColumnExpNode orr(ColumnNode left, ColumnNode right) {
        return new ColumnExpNode(left, ORR, right);
    }

    public static ColumnExpNode orr(ColumnNode left, String rightColumn) {
        return new ColumnExpNode(left, ORR, column(rightColumn));
    }

    public static ColumnExpNode orr(ColumnNode left, Number rightValue) {
        return new ColumnExpNode(left, ORR, number(rightValue));
    }
}
