/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import org.redkale.convert.json.JsonConvert;

/**
 * FilterFuncColumn用于getNumberMap获取列表似数据, getNumberResult获取单字段值， getNumberMap获取多字段值
 *
 * @author zhangjx
 */
public class FilterFuncColumn implements java.io.Serializable {

    public static final String COLUMN_NULL = "*";

    FilterFunc func;

    String column; //为null，将使用*代替

    Number defvalue;

    public FilterFuncColumn() {
    }

    public static FilterFuncColumn create(final FilterFunc func) {
        return new FilterFuncColumn(func);
    }

    public static FilterFuncColumn create(final FilterFunc func, final String column) {
        return new FilterFuncColumn(func, column);
    }

    public static FilterFuncColumn create(final FilterFunc func, final String column, final Number defvalue) {
        return new FilterFuncColumn(func, column, defvalue);
    }

    String col() {
        return column == null || column.isEmpty() ? COLUMN_NULL : column;
    }

    public FilterFuncColumn(final FilterFunc func) {
        this(func, null, null);
    }

    public FilterFuncColumn(final FilterFunc func, final String column) {
        this(func, column, null);
    }

    public FilterFuncColumn(final FilterFunc func, final String column, final Number defvalue) {
        this.func = func;
        this.column = column;
        this.defvalue = defvalue;
    }

    public FilterFunc getFunc() {
        return func;
    }

    public void setFunc(FilterFunc func) {
        this.func = func;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public Number getDefvalue() {
        return defvalue;
    }

    public void setDefvalue(Number defvalue) {
        this.defvalue = defvalue;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
