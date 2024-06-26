/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import static org.redkale.source.ColumnExpress.*;

import java.io.Serializable;
import java.util.Objects;
import org.redkale.convert.ConvertColumn;
import org.redkale.util.*;

/**
 * ColumnValue主要用于多个字段更新的表达式。
 * value值为:ColumnExpNode、ColumnFuncNode、ColumnNameNode、ColumnNumberNode、ColumnStringNode、ColumnBytesNode <br>
 * 用于 DataSource.updateColumn 方法 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class ColumnValue implements Comparable<ColumnValue> {

    @ConvertColumn(index = 1)
    private String column;

    @ConvertColumn(index = 2)
    private ColumnExpress express;

    @ConvertColumn(index = 3)
    private ColumnNode value;

    public ColumnValue() {}

    protected <T extends Serializable> ColumnValue(LambdaSupplier<T> func, ColumnExpress express) {
        this(LambdaSupplier.readColumn(func), express, func.get());
    }

    protected <T> ColumnValue(LambdaFunction<T, ?> func, ColumnExpress express, Serializable value) {
        this(LambdaFunction.readColumn(func), express, value);
    }

    protected ColumnValue(String column, ColumnExpress express, Serializable value) {
        Objects.requireNonNull(column);
        Objects.requireNonNull(express);
        this.column = column;
        this.express = express;
        if (value instanceof String) {
            this.value = new ColumnStringNode(value.toString());
        } else if (value instanceof Number) {
            this.value = new ColumnNumberNode((Number) value);
        } else if (value instanceof ColumnExpNode
                || value instanceof ColumnFuncNode
                || value instanceof ColumnNameNode
                || value instanceof ColumnNumberNode
                || value instanceof ColumnStringNode
                || value instanceof ColumnBytesNode) {
            this.value = (ColumnNode) value;
        } else if (value == null) {
            this.value = null;
        } else {
            throw new IllegalArgumentException("Not supported value: " + value);
        }
    }

    /**
     * 同 set 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue create(String column, Serializable value) {
        return new ColumnValue(column, SET, value);
    }

    /**
     * @see #set(java.lang.String, java.io.Serializable)
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     * @deprecated
     */
    @Deprecated(since = "2.8.0")
    public static ColumnValue mov(String column, Serializable value) {
        return set(column, value);
    }

    /**
     * 返回 {column} = {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue set(String column, Serializable value) {
        return new ColumnValue(column, SET, value);
    }

    /**
     * 返回 {column} = {column} + {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue inc(String column, Number value) {
        return new ColumnValue(column, INC, value);
    }

    /**
     * 返回 {column} = {column} + 1 操作
     *
     * @param column 字段名
     * @return ColumnValue
     * @since 2.4.0
     */
    public static ColumnValue inc(String column) {
        return new ColumnValue(column, INC, 1);
    }

    /**
     * 返回 {column} = {column} - {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue dec(String column, Number value) {
        return new ColumnValue(column, DEC, value);
    }

    /**
     * 返回 {column} = {column} - 1 操作
     *
     * @param column 字段名
     * @return ColumnValue
     * @since 2.4.0
     */
    public static ColumnValue dec(String column) {
        return new ColumnValue(column, DEC, 1);
    }

    /**
     * 返回 {column} = {column} * {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue mul(String column, Number value) {
        return new ColumnValue(column, MUL, value);
    }

    /**
     * 返回 {column} = {column} / {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue div(String column, Number value) {
        return new ColumnValue(column, DIV, value);
    }

    /**
     * 返回 {column} = {column} % {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue mod(String column, Serializable value) {
        return new ColumnValue(column, MOD, value);
    }

    /**
     * 返回 {column} = {column} &#38; {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue and(String column, Serializable value) {
        return new ColumnValue(column, AND, value);
    }

    /**
     * 返回 {column} = {column} | {value} 操作
     *
     * @param column 字段名
     * @param value 字段值
     * @return ColumnValue
     */
    public static ColumnValue orr(String column, Serializable value) {
        return new ColumnValue(column, ORR, value);
    }

    /**
     * 同 set 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue create(LambdaSupplier<T> func) {
        return new ColumnValue(func, SET);
    }

    /**
     * 返回 {column} = {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue set(LambdaSupplier<T> func) {
        return new ColumnValue(func, SET);
    }

    /**
     * 返回 {column} = {column} + {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue inc(LambdaSupplier<T> func) {
        return new ColumnValue(func, INC);
    }

    /**
     * 返回 {column} = {column} - {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue dec(LambdaSupplier<T> func) {
        return new ColumnValue(func, DEC);
    }

    /**
     * 返回 {column} = {column} * {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue mul(LambdaSupplier<T> func) {
        return new ColumnValue(func, MUL);
    }

    /**
     * 返回 {column} = {column} / {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue div(LambdaSupplier<T> func) {
        return new ColumnValue(func, DIV);
    }

    /**
     * 返回 {column} = {column} % {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue mod(LambdaSupplier<T> func) {
        return new ColumnValue(func, MOD);
    }

    /**
     * 返回 {column} = {column} &#38; {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue and(LambdaSupplier<T> func) {
        return new ColumnValue(func, AND);
    }

    /**
     * 返回 {column} = {column} | {value} 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T extends Serializable> ColumnValue orr(LambdaSupplier<T> func) {
        return new ColumnValue(func, ORR);
    }

    /**
     * 同 set 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue create(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, SET, value);
    }

    /**
     * 返回 {column} = {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue set(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, SET, value);
    }

    /**
     * 返回 {column} = {column} + {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue inc(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, INC, value);
    }

    /**
     * 返回 {column} = {column} + 1 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue inc(LambdaFunction<T, ?> func) {
        return new ColumnValue(func, INC, 1);
    }

    /**
     * 返回 {column} = {column} - {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue dec(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, DEC, value);
    }

    /**
     * 返回 {column} = {column} - 1 操作
     *
     * @param func 字段名Lambda
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue dec(LambdaFunction<T, ?> func) {
        return new ColumnValue(func, DEC, 1);
    }

    /**
     * 返回 {column} = {column} * {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue mul(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, MUL, value);
    }

    /**
     * 返回 {column} = {column} / {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue div(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, DIV, value);
    }

    /**
     * 返回 {column} = {column} % {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue mod(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, MOD, value);
    }

    /**
     * 返回 {column} = {column} &#38; {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue and(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, AND, value);
    }

    /**
     * 返回 {column} = {column} | {value} 操作
     *
     * @param func 字段名Lambda
     * @param value 字段值
     * @param <T> 值的泛型
     * @return ColumnValue
     * @since 2.8.0
     */
    public static <T> ColumnValue orr(LambdaFunction<T, ?> func, Serializable value) {
        return new ColumnValue(func, ORR, value);
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public ColumnExpress getExpress() {
        return express;
    }

    public void setExpress(ColumnExpress express) {
        this.express = express;
    }

    public ColumnNode getValue() {
        return value;
    }

    public void setValue(ColumnNode value) {
        this.value = value;
    }

    @Override
    public int compareTo(ColumnValue o) {
        return o == null ? 1 : this.column.compareTo(o.column);
    }

    @Override
    public String toString() {
        return "{\"column\":\"" + column + "\", \"express\":" + express + ", \"value\":" + value + "}";
    }
}
