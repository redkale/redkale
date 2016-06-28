/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class Flipper implements Serializable, Cloneable {

    public static int DEFAULT_PAGESIZE = 20;

    private int size = DEFAULT_PAGESIZE;

    private int start = 0;

    private String sort = "";

    public Flipper() {
    }

    public Flipper(int pageSize) {
        this.size = pageSize;
    }

    public Flipper(String sortColumn) {
        this.sort = sortColumn;
    }

    public Flipper(int pageSize, int startIndex) {
        this.size = pageSize > 0 ? pageSize : DEFAULT_PAGESIZE;
        this.start = startIndex < 0 ? 0 : startIndex;
    }

    public Flipper(int pageSize, int startIndex, String sortColumn) {
        this.size = pageSize > 0 ? pageSize : DEFAULT_PAGESIZE;
        this.start = startIndex < 0 ? 0 : startIndex;
        this.sort = sortColumn;
    }

    public void copyTo(Flipper copy) {
        if (copy == null) return;
        copy.start = this.start;
        copy.size = this.size;
        copy.sort = this.sort;
    }

    public void copyFrom(Flipper copy) {
        if (copy == null) return;
        this.start = copy.start;
        this.size = copy.size;
        this.sort = copy.sort;
    }

    public Flipper next() {
        this.start = getStart() + this.size;
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Flipper clone() {
        return new Flipper(this.size, this.start, this.sort);
    }

    public int getStart() {
        return start;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{start:" + this.start + ", size=" + this.size + ", sort=" + this.sort + "}";
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size > 0) {
            this.size = size;
        }
    }

    public void setStart(int start) {
        this.start = start < 0 ? 0 : start;
    }

    public String getSort() {
        return sort;
    }

    public Flipper sortIfEmpty(String sort) {
        if (this.sort == null || this.sort.isEmpty()) {
            this.sort = sort;
        }
        return this;
    }

    public void setSort(String sort) {
        if (sort != null) {
            this.sort = sort.trim();
        }
    }

}
