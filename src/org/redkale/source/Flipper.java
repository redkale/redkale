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
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public final class Flipper implements Serializable {

    public static int DEFAULT_PAGESIZE = 20;

    private int size = DEFAULT_PAGESIZE;

    private int page = 1;

    private String sort = "";

    public Flipper() {
    }

    public Flipper(int pageSize) {
        this.size = pageSize;
    }

    public Flipper(String sortColumn) {
        this.sort = sortColumn;
    }

    public Flipper(int pageSize, int pageNo) {
        this.size = pageSize;
        this.page = pageNo;
    }

    public Flipper(int pageSize, int pageNo, String sortColumn) {
        this.size = pageSize;
        this.page = pageNo;
        this.sort = sortColumn;
    }

    public void copyTo(Flipper copy) {
        if (copy == null) return;
        copy.page = this.page;
        copy.size = this.size;
        copy.sort = this.sort;
    }

    public void copyFrom(Flipper copy) {
        if (copy == null) return;
        this.page = copy.page;
        this.size = copy.size;
        this.sort = copy.sort;
    }

    public Flipper next() {
        this.page++;
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Flipper clone() {
        return new Flipper(this.size, this.page, this.sort);
    }

    public int index() {
        return (getPage() - 1) * getSize();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{page:" + this.page + ", size=" + this.size + ", sort=" + this.sort + "}";
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size > 0) {
            this.size = size;
        }
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        if (page >= 0) {
            this.page = page;
        }
    }

    public String getSort() {
        return sort;
    }

    public Flipper putSortIfEmpty(String sort) {
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
