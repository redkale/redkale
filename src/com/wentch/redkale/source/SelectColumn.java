/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.util.Arrays;

/**
 *
 * @author zhangjx
 */
public final class SelectColumn {

    private String[] columns;

    private boolean excludable; //是否排除

    public SelectColumn() {
    }

    private SelectColumn(String[] columns, boolean excludable) {
        this.columns = columns;
        this.excludable = excludable;
    }

    /**
     * class中的字段名
     *
     * @param columns
     * @return
     */
    public static SelectColumn createIncludes(String... columns) {
        return new SelectColumn(columns, false);
    }

    /**
     * class中的字段名
     *
     * @param columns
     * @return
     */
    public static SelectColumn createExcludes(String... columns) {
        return new SelectColumn(columns, true);
    }

    public boolean validate(String column) {
        for (String col : this.columns) {
            if (col.equalsIgnoreCase(column)) return !excludable;
        }
        return excludable;
    }

    public boolean isEmpty() {
        return this.columns == null || this.columns.length == 0;
    }

    public String[] getColumns() {
        return columns;
    }

    public void setColumns(String[] columns) {
        this.columns = columns;
    }

    public boolean isExcludable() {
        return excludable;
    }

    public void setExcludable(boolean excludable) {
        this.excludable = excludable;
    }

    @Override
    public String toString() {
        return "SelectColumn{" + "columns=" + Arrays.toString(columns) + ", excludable=" + excludable + '}';
    }

}
