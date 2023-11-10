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
    private Number left;

    @ConvertColumn(index = 2)
    private FilterExpress express;

    @ConvertColumn(index = 3)
    private Number right;

    public FilterValue() {
    }

    public FilterValue(Number left, Number right) {
        this(left, FilterExpress.EQ, right);
    }

    public FilterValue(Number left, FilterExpress express) {
        this(left, express, 0);
    }

    public FilterValue(Number left, FilterExpress express, Number right) {
        this.left = left;
        this.express = FilterNodes.oldExpress(express);
        this.right = right;
    }

    public Number getLeft() {
        return left == null ? 0 : left;
    }

    public void setLeft(Number left) {
        this.left = left;
    }

    public FilterExpress getExpress() {
        return express == null ? FilterExpress.EQ : express;
    }

    public void setExpress(FilterExpress express) {
        this.express = express;
    }

    public Number getRight() {
        return right == null ? 0 : right;
    }

    public void setRight(Number right) {
        this.right = right;
    }

    @Override
    public String toString() {
        return FilterValue.class.getSimpleName() + "[left=" + getLeft() + ", express=" + getExpress() + ", right=" + getRight() + "]";
    }
}
