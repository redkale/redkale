/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.util.function.BiFunction;

/**
 * ConvertColumn 对应的实体类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class ConvertColumnEntry {

    private int index;

    private String name = "";

    private boolean ignore;

    private ConvertType convertType;

    private BiFunction<?, String, ?> fieldFunc;

    public ConvertColumnEntry() {}

    public ConvertColumnEntry(ConvertColumn column, BiFunction<?, String, ?> fieldFunc) {
        if (column == null) return;
        this.name = column.name();
        this.index = column.index();
        this.ignore = column.ignore();
        this.convertType = column.type();
        this.fieldFunc = fieldFunc;
    }

    public ConvertColumnEntry(String name) {
        this(name, false);
    }

    public ConvertColumnEntry(String name, boolean ignore) {
        this.name = name;
        this.ignore = ignore;
        this.convertType = ConvertType.ALL;
    }

    public ConvertColumnEntry(String name, boolean ignore, ConvertType convertType) {
        this.name = name;
        this.ignore = ignore;
        this.convertType = convertType;
    }

    public ConvertColumnEntry(
            String name, boolean ignore, ConvertType convertType, BiFunction<?, String, ?> fieldFunc) {
        this.name = name;
        this.ignore = ignore;
        this.convertType = convertType;
        this.fieldFunc = fieldFunc;
    }

    public ConvertColumnEntry(String name, int index, boolean ignore, ConvertType convertType) {
        this.name = name;
        this.index = index;
        this.ignore = ignore;
        this.convertType = convertType;
    }

    public ConvertColumnEntry(
            String name, int index, boolean ignore, ConvertType convertType, BiFunction<?, String, ?> fieldFunc) {
        this.name = name;
        this.index = index;
        this.ignore = ignore;
        this.convertType = convertType;
        this.fieldFunc = fieldFunc;
    }

    public BiFunction<?, String, ?> fieldFunc() {
        return fieldFunc;
    }

    public String name() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean ignore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

    public ConvertType type() {
        return convertType == null ? ConvertType.ALL : convertType;
    }

    public void setConvertType(ConvertType convertType) {
        this.convertType = convertType;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return "ConvertColumnEntry{"
                + "index=" + index
                + ", name=" + name
                + ", ignore=" + ignore
                + ", convertType=" + convertType
                + '}';
    }
}
