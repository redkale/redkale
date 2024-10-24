/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.Objects;
import org.redkale.annotation.Comment;
import org.redkale.convert.ConvertColumn;

/**
 * 翻页+排序对象, offset从0开始, limit必须大于0
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Flipper extends RowBound {

    @ConvertColumn(index = 3)
    @Comment("排序字段, 可多字段排序")
    private String sort = "";

    public Flipper() {}

    public Flipper(int limit) {
        super(limit);
    }

    public Flipper(String sortColumn) {
        this.sort = sortColumn;
    }

    public Flipper(int limit, int offset) {
        super(limit, offset);
    }

    public Flipper(int limit, String sortColumn) {
        super(limit);
        this.sort = sortColumn;
    }

    public Flipper(int limit, int offset, String sortColumn) {
        super(limit, offset);
        this.sort = sortColumn;
    }

    @Override
    public Flipper limit(int limit) {
        super.limit(limit);
        return this;
    }

    @Override
    public Flipper maxLimit(int maxlimit) {
        super.maxLimit(maxlimit);
        return this;
    }

    @Override
    public Flipper unlimit() {
        super.unlimit();
        return this;
    }

    public Flipper copyTo(Flipper copy) {
        if (copy == null) {
            return copy;
        }
        super.copyTo(copy);
        copy.sort = this.sort;
        return copy;
    }

    public Flipper copyFrom(Flipper copy) {
        if (copy == null) {
            return this;
        }
        super.copyFrom(copy);
        this.sort = copy.sort;
        return this;
    }

    /**
     * 翻下一页
     *
     * @return RowBound
     */
    @Override
    public Flipper next() {
        super.next();
        return this;
    }

    /**
     * 设置当前页号，页号从1开始
     *
     * @param current 页号， 从1开始
     * @return Flipper
     */
    @Override
    public Flipper current(int current) {
        super.current(current);
        return this;
    }

    @Override
    public String toString() {
        return "{\"limit\":" + this.limit + ",\"offset\":" + this.offset
                + ((sort == null || sort.isEmpty()) ? "" : (",\"sort\":\"" + this.sort.replace('"', '\'') + "\""))
                + "}";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + this.offset;
        hash = 37 * hash + this.limit;
        hash = 37 * hash + Objects.hashCode(this.sort);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Flipper other = (Flipper) obj;
        if (this.offset != other.offset) {
            return false;
        }
        if (this.limit != other.limit) {
            return false;
        }
        return Objects.equals(this.sort, other.sort);
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort == null ? "" : sort.trim();
    }

    public Flipper sort(String sort) {
        setSort(sort);
        return this;
    }

    public Flipper sortIfAbsent(String sort) {
        if (this.sort == null || this.sort.isEmpty()) {
            this.sort = sort;
        }
        return this;
    }

    public static Flipper sortIfAbsent(Flipper flipper, String sort) {
        if (flipper != null) {
            return flipper.sortIfAbsent(sort);
        }
        return flipper;
    }

    public static boolean hasLimit(Flipper flipper) {
        return flipper != null && flipper.getLimit() > 0;
    }
}
