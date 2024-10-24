/*

*/

package org.redkale.source;

import java.io.Serializable;
import org.redkale.annotation.Comment;
import org.redkale.convert.ConvertColumn;

/**
 * 翻页对象, offset从0开始, limit必须大于0
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.source.Flipper
 * @see org.redkale.source.PageBean
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class RowBound implements Serializable {

    private static int DEFAULT_LIMIT = 20;

    @ConvertColumn(index = 1)
    @Comment("记录行的偏移量，从0开始")
    protected int offset = 0;

    @ConvertColumn(index = 2)
    @Comment("每页多少行")
    protected int limit = DEFAULT_LIMIT;

    public RowBound() {}

    public RowBound(int limit) {
        this.limit = limit > 0 ? limit : 0;
    }

    public RowBound(int limit, int offset) {
        this.limit = limit > 0 ? limit : 0;
        this.offset = offset < 0 ? 0 : offset;
    }

    public RowBound copyTo(RowBound copy) {
        if (copy == null) {
            return copy;
        }
        copy.offset = this.offset;
        copy.limit = this.limit;
        return copy;
    }

    public RowBound copyFrom(RowBound copy) {
        if (copy == null) {
            return this;
        }
        this.offset = copy.offset;
        this.limit = copy.limit;
        return this;
    }

    /**
     * 翻下一页
     *
     * @return RowBound
     */
    public RowBound next() {
        this.offset = getOffset() + this.limit;
        return this;
    }

    /**
     * 设置当前页号，页号从1开始
     *
     * @param current 页号， 从1开始
     * @return Flipper
     */
    public RowBound current(int current) {
        this.offset = (current - 1) * this.limit;
        return this;
    }

    @Override
    public String toString() {
        return "{\"limit\":" + this.limit + ",\"offset\":" + this.offset + "}";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + this.offset;
        hash = 37 * hash + this.limit;
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
        final RowBound other = (RowBound) obj;
        return this.offset != other.offset && this.limit != other.limit;
    }

    public RowBound limit(int limit) {
        setLimit(limit);
        return this;
    }

    public RowBound maxLimit(int maxlimit) {
        setLimit(Math.max(1, Math.min(maxlimit, limit)));
        return this;
    }

    public RowBound unlimit() {
        this.limit = 0;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset < 0 ? 0 : offset;
    }

    public RowBound offset(int offset) {
        setOffset(offset);
        return this;
    }

    public static boolean hasLimit(RowBound round) {
        return round != null && round.getLimit() > 0;
    }

    public static int getDefaultLimit() {
        return DEFAULT_LIMIT;
    }

    public static void setDefaultLimit(int limit) {
        if (limit > 0) {
            DEFAULT_LIMIT = limit;
        }
    }
}
