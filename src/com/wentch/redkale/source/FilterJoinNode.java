/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 */
public class FilterJoinNode extends FilterNode {

    private String tabalis;

    private Class joinClass;

    private String joinColumn;

    public FilterJoinNode() {
    }

    protected FilterJoinNode(Class joinClass, String joinColumn, String column, Serializable value) {
        this(joinClass, joinColumn, column, null, value);
    }

    protected FilterJoinNode(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        this.joinClass = joinClass;
        this.joinColumn = joinColumn;
        this.column = column;
        this.express = express;
        this.value = value;
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumn, column, value);
    }

    public static FilterNode create(Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return new FilterJoinNode(joinClass, joinColumn, column, express, value);
    }

    @Override
    protected <T> CharSequence createSQLExpress(final Function<Class, EntityInfo> func, final EntityInfo<T> info, final FilterBean bean) {
        if (this.joinClass == null) return super.createSQLExpress(func, info, bean);
        return super.createSQLExpress(func, func.apply(joinClass), bean);
    }

    public Class getJoinClass() {
        return joinClass;
    }

    public void setJoinClass(Class joinClass) {
        this.joinClass = joinClass;
    }

    public String getJoinColumn() {
        return joinColumn;
    }

    public void setJoinColumn(String joinColumn) {
        this.joinColumn = joinColumn;
    }

    @Override
    public String getTabalis() {
        return tabalis;
    }

    public void setTabalis(String tabalis) {
        this.tabalis = tabalis;
    }

}
