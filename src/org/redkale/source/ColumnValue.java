/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 * ColumnValue主要用于多个字段更新的表达式。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class ColumnValue {

    private String column;

    private ColumnExpress express;

    private Serializable value;

    public ColumnValue() {
    }

    public ColumnValue(String column, Serializable value) {
        this(column, ColumnExpress.MOV, value);
    }

    public ColumnValue(String column, ColumnExpress express, Serializable value) {
        this.column = column;
        this.express = express == null ? ColumnExpress.MOV : express;
        this.value = value;
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

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "{\"column\":\"" + column + "\", \"express\":" + express + ", \"value\":" + ((value instanceof CharSequence) ? ("\"" + value + "\"") : value) + "}";
    }
}
