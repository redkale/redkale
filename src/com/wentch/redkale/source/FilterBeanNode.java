/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.Attribute;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.Collection;

/**
 *
 * @author zhangjx
 */
public class FilterBeanNode extends FilterNode {

    private Attribute beanAttribute;

    private boolean array;

    private boolean collection;

    private boolean string;

    private boolean number;

    private boolean likefit;

    private boolean ignoreCase;

    private long least;

    FilterBeanNode(String col, boolean sign, Attribute beanAttr) {
        this.column = col;
        this.signand = sign;
        this.beanAttribute = beanAttr;
    }

    void setField(Field field) {
        final FilterColumn fc = field.getAnnotation(FilterColumn.class);
        if (fc != null && !fc.name().isEmpty()) this.column = fc.name();
        final Class type = field.getType();
        this.array = type.isArray();
        this.collection = Collection.class.isAssignableFrom(type);
        this.least = fc == null ? 1L : fc.least();
        this.likefit = fc == null ? true : fc.likefit();
        this.ignoreCase = fc == null ? true : fc.ignoreCase();
        this.number = type.isPrimitive() || Number.class.isAssignableFrom(type);
        this.string = CharSequence.class.isAssignableFrom(type);

        FilterExpress exp = fc == null ? null : fc.express();
        if (this.array || this.collection) {
            if (Range.class.isAssignableFrom(type.getComponentType())) {
                if (exp == null) exp = AND;
                if (AND != exp) exp = OR;
            } else {
                if (NOTIN != exp) exp = IN;
            }
        } else if (Range.class.isAssignableFrom(type)) {
            if (NOTBETWEEN != exp) exp = BETWEEN;
        }
        if (exp == null) exp = EQUAL;
        this.express = exp;
    }

    @Override
    protected void append(FilterNode node, boolean sign) {
        FilterBeanNode newnode = new FilterBeanNode(this.column, this.signand, this.beanAttribute);
        newnode.express = this.express;
        newnode.nodes = this.nodes;
        newnode.array = this.array;
        newnode.collection = this.collection;
        newnode.ignoreCase = this.ignoreCase;
        newnode.least = this.least;
        newnode.likefit = this.likefit;
        newnode.number = this.number;
        newnode.string = this.string;
        this.nodes = new FilterNode[]{newnode};
        this.column = node.column;
        this.express = node.express;
        this.signand = sign;
        this.setValue(node.getValue());
        if (node instanceof FilterBeanNode) {
            FilterBeanNode beanNode = ((FilterBeanNode) node);
            this.beanAttribute = beanNode.beanAttribute;
            this.array = beanNode.array;
            this.collection = beanNode.collection;
            this.ignoreCase = beanNode.ignoreCase;
            this.least = beanNode.least;
            this.likefit = beanNode.likefit;
            this.number = beanNode.number;
            this.string = beanNode.string;
        }
    }

    @Override
    protected Serializable getValue(FilterBean bean) {
        if (bean == null || beanAttribute == null) return null;
        Serializable rs = (Serializable) beanAttribute.get(bean);
        if (rs == null) return null;
        if (string && ((CharSequence) rs).length() == 0) return null;
        if (number && ((Number) rs).longValue() < this.least) return null;
        if (array && Array.getLength(rs) == 0) return null;
        if (collection && ((Collection) rs).isEmpty()) return null;
        return rs;
    }
}
