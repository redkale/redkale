/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;

/**
 * 页集合。 结构由一个total总数和一个List列表组合而成。
 *
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public class Sheet<T> implements java.io.Serializable {

    private long total = -1;

    private Collection<T> rows;

    public Sheet() {
        super();
    }

    public Sheet(int total, Collection<? extends T> data) {
        this((long) total, data);
    }

    public Sheet(long total, Collection<? extends T> data) {
        this.total = total;
        this.rows = (Collection<T>) data;
    }

    public static <E> Sheet<E> asSheet(Collection<E> data) {
        return data == null ? new Sheet<>() : new Sheet<>(data.size(), data);
    }

    public Sheet<T> copyTo(Sheet<T> copy) {
        if (copy == null) return copy;
        copy.total = this.total;
        if (this.getRows() != null) {
            copy.setRows(new ArrayList<>(this.getRows()));
        } else {
            copy.rows = null;
        }
        return copy;
    }

    /**
     * 判断数据列表是否为空
     *
     * @return
     */
    public boolean isEmpty() {
        return this.rows == null || this.rows.isEmpty();
    }

    @Override
    public String toString() {
        return "Sheet[total=" + this.total + ", rows=" + this.rows + "]";
    }

    public long getTotal() {
        return this.total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public Collection<T> getRows() {
        return this.rows;
    }

    public List<T> list() {
        return list(false);
    }

    public List<T> list(boolean created) {
        if (this.rows == null) return created ? new ArrayList<>() : null;
        return (this.rows instanceof List) ? (List<T>) this.rows : new ArrayList<>(this.rows);
    }

    public void setRows(Collection<? extends T> data) {
        this.rows = (Collection<T>) data;
    }
}
