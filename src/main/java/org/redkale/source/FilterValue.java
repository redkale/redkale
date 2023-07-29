/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import org.redkale.convert.ConvertColumn;

/**
 * FilterValue主要用于复杂的表达式。<br>
 * 例如: col / 10 = 3 、MOD(col, 8) &gt; 0 这些都不是单独一个数值能表达的，因此需要FilterValue 才构建 8 、 &gt; 、0 组合值。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class FilterValue implements java.io.Serializable {

    @ConvertColumn(index = 1)
    private Number first;

    @ConvertColumn(index = 2)
    private FilterExpress express;

    @ConvertColumn(index = 3)
    private Number second;

    public FilterValue() {
    }

    public FilterValue(Number first, Number second) {
        this(first, FilterExpress.EQUAL, second);
    }

    public FilterValue(Number first, FilterExpress express) {
        this(first, express, 0);
    }

    public FilterValue(Number first, FilterExpress express, Number second) {
        this.first = first;
        this.express = express;
        this.second = second;
    }

    public Number getFirst() {
        return first == null ? 0 : first;
    }

    public void setFirst(Number first) {
        this.first = first;
    }

    public FilterExpress getExpress() {
        return express == null ? FilterExpress.EQUAL : express;
    }

    public void setExpress(FilterExpress express) {
        this.express = express;
    }

    public Number getSecond() {
        return second == null ? 0 : second;
    }

    public void setSecond(Number second) {
        this.second = second;
    }

    @Override
    public String toString() {
        return FilterValue.class.getSimpleName() + "[first=" + getFirst() + ", express=" + getExpress() + ", second=" + getSecond() + "]";
    }
}
