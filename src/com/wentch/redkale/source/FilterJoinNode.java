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
public final class FilterJoinNode extends FilterNode {

    private Class joinClass;

    private String joinColumn;

    private String foreignColumn;

    public FilterJoinNode() {
    }

    protected FilterJoinNode(Class joinClass, String joinColumn, String foreignColumn) {
        this.joinClass = joinClass;
        this.joinColumn = joinColumn;
        this.foreignColumn = foreignColumn;
    }

    public static FilterJoinNode create(Class joinClass, String joinColumn) {
        return create(joinClass, joinColumn, joinColumn);
    }

    public static FilterJoinNode create(Class joinClass, String joinColumn, String foreignColumn) {
        Objects.requireNonNull(joinClass);
        Objects.requireNonNull(joinColumn);
        Objects.requireNonNull(foreignColumn);
        return new FilterJoinNode(joinClass, joinColumn, foreignColumn);
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

    public String getForeignColumn() {
        return foreignColumn;
    }

    public void setForeignColumn(String foreignColumn) {
        this.foreignColumn = foreignColumn;
    }

}
