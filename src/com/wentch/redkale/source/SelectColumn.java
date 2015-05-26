/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.util.*;

/**
 *
 * @author zhangjx
 */
public final class SelectColumn {

    private String[] startWithColumns;

    private String[] endWithColumns;

    private String[] columns;

    private boolean excludable; //是否排除

    public SelectColumn() {
    }

    private SelectColumn(String[] columns0, boolean excludable) {
        this.excludable = excludable;
        final int len = columns0.length;
        if (len < 1) return;
        boolean reg = false;
        int slen = 0, elen = 0;
        for (String col : columns0) {
            if (col.charAt(0) == '^') {
                slen++;
                reg = true;
            } else if (col.charAt(col.length() - 1) == '$') {
                elen++;
                reg = true;
            }
        }
        if (reg) {
            if (slen == len) {
                this.startWithColumns = new String[len];
                for (int i = 0; i < len; i++) {
                    this.startWithColumns[i] = columns0[i].substring(1);
                }
            } else if (elen == len) {
                this.endWithColumns = new String[len];
                for (int i = 0; i < len; i++) {
                    this.endWithColumns[i] = columns0[i].substring(0, columns0[i].length() - 1);
                }
            } else {
                List<String> starts = new ArrayList<>();
                List<String> ends = new ArrayList<>();
                List<String> strings = new ArrayList<>();
                for (String col : columns0) {
                    if (col.charAt(0) == '^') {
                        starts.add(col.substring(1));
                    } else if (col.charAt(col.length() - 1) == '$') {
                        ends.add(col.substring(0, col.length() - 1));
                    } else {
                        strings.add(col);
                    }
                }
                if (!starts.isEmpty()) this.startWithColumns = starts.toArray(new String[starts.size()]);
                if (!ends.isEmpty()) this.endWithColumns = ends.toArray(new String[ends.size()]);
                if (!strings.isEmpty()) this.columns = strings.toArray(new String[strings.size()]);
            }
        } else {
            this.columns = columns0;
        }
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
        if (this.columns != null) {
            for (String col : this.columns) {
                if (col.equalsIgnoreCase(column)) return !excludable;
            }
        }
        if (this.startWithColumns != null) {
            for (String col : this.startWithColumns) {
                if (column.startsWith(col)) return !excludable;
            }
        }
        if (this.endWithColumns != null) {
            for (String col : this.endWithColumns) {
                if (column.endsWith(col)) return !excludable;
            }
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
