/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import org.redkale.convert.ConvertColumn;
import static org.redkale.source.FilterExpress.*;
import org.redkale.util.*;

/**
 * 注意： <br>
 * column的值以#开头的视为虚拟字段，不在过滤范围内 <br>
 * 在调用 createSQLExpress 之前必须先调用 createSQLJoin <br>
 * 在调用 createPredicate 之前必须先调用 isCacheUseable  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class FilterNode {  //FilterNode 不能实现Serializable接口， 否则DataSource很多重载接口会出现冲突

    @ConvertColumn(index = 1)
    protected boolean readOnly;

    @ConvertColumn(index = 2)
    protected String column;

    @ConvertColumn(index = 3)
    protected FilterExpress express;

    @ConvertColumn(index = 4)
    protected Serializable value;

    //----------------------------------------------
    @ConvertColumn(index = 5)
    protected boolean or;

    @ConvertColumn(index = 6)
    protected FilterNode[] nodes;

    public FilterNode() {
    }

    protected FilterNode(String col, FilterExpress exp, Serializable val) {
        Objects.requireNonNull(col);
        if (exp == null) {
            if (val instanceof Range) {
                exp = FilterExpress.BETWEEN;
            } else if (val instanceof Collection) {
                if (!((Collection) val).isEmpty()) {
                    Object subval = null;
                    for (Object obj : (Collection) val) {  //取第一个值
                        subval = obj;
                        break;
                    }
                    if (subval instanceof Range) {
                        exp = FilterExpress.BETWEEN;
//                    } else if (subval instanceof Collection) {
//                        exp = FilterExpress.IN;
//                    } else if (subval != null && val.getClass().isArray()) {
//                        exp = FilterExpress.IN;
                    } else {
                        exp = FilterExpress.IN;
                    }
                } else { //空集合
                    exp = FilterExpress.IN;
                }
            } else if (val != null && val.getClass().isArray()) {
                Class comp = val.getClass().getComponentType();
                if (Range.class.isAssignableFrom(comp)) {
                    exp = FilterExpress.BETWEEN;
                } else {
                    exp = FilterExpress.IN;
                }
            } else if (val instanceof Stream) {
                val = ((Stream) val).toArray();
                exp = FilterExpress.IN;
            }
        }
        this.column = col;
        this.express = exp == null ? EQ : FilterNodes.oldExpress(exp);
        this.value = val;
    }

    protected FilterNode any(FilterNode node, boolean isOr) {
        if (this.readOnly) {
            throw new SourceException("FilterNode(" + this + ") is ReadOnly");
        }
        Objects.requireNonNull(node);
        if (this.column == null) {
            this.column = node.column;
            this.express = node.express;
            this.value = node.value;
            return this;
        }
        if (this.nodes == null) {
            this.nodes = new FilterNode[]{node};
            this.or = isOr;
            return this;
        }
        if (or == isOr) {
            this.nodes = Utility.append(this.nodes, node);
            return this;
        }
        FilterNode newNode = new FilterNode(this.column, this.express, this.value);
        newNode.or = this.or;
        newNode.nodes = this.nodes;
        this.nodes = new FilterNode[]{newNode, node};
        this.column = null;
        this.express = null;
        this.or = isOr;
        this.value = null;
        return this;
    }

    //----------------------------------------------------------------------------------------------------
    @Deprecated(since = "2.8.0")
    public static FilterNode create(String column, Serializable value) {
        return FilterNodes.create(column, null, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return FilterNodes.create(column, express, value);
    }

    @Deprecated(since = "2.8.0")
    public static <F extends Serializable> FilterNode create(LambdaSupplier<F> func) {
        return FilterNodes.create(func);
    }

    @Deprecated(since = "2.8.0")
    public static <F extends Serializable> FilterNode create(LambdaSupplier<F> func, FilterExpress express) {
        return FilterNodes.create(func, express);
    }

    @Deprecated(since = "2.8.0")
    public static <T, F extends Serializable> FilterNode create(LambdaFunction<T, F> func, F value) {
        return FilterNodes.create(func, value);
    }

    @Deprecated(since = "2.8.0")
    public static <T, F extends Serializable> FilterNode create(LambdaFunction<T, F> func, FilterExpress express, F value) {
        return FilterNodes.create(func, express, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterNode filter(String column, Serializable value) {
        return FilterNodes.create(column, null, value);
    }

    @Deprecated(since = "2.8.0")
    public static FilterNode filter(String column, FilterExpress express, Serializable value) {
        return FilterNodes.create(column, express, value);
    }

    //----------------------------------------------------------------------------------------------------
    public FilterNode copy() {
        return copy(new FilterNode());
    }

    protected FilterNode copy(FilterNode node) {
        node.readOnly = this.readOnly;
        node.column = this.column;
        node.express = this.express;
        node.value = this.value;
        node.or = this.or;
        if (this.nodes != null) {
            node.nodes = new FilterNode[this.nodes.length];
            for (int i = 0; i < node.nodes.length; i++) {
                node.nodes[i] = this.nodes[i] == null ? null : this.nodes[i].copy();
            }
        }
        return node;
    }

    public FilterNode asReadOnly() {
        this.readOnly = true;
        return this;
    }

    public FilterNode readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public long findLongValue(final String col, long defValue) {
        Serializable val = findValue(col);
        return val == null ? defValue : ((Number) val).longValue();
    }

    public int findIntValue(final String col, int defValue) {
        Serializable val = findValue(col);
        return val == null ? defValue : ((Number) val).intValue();
    }

    public String findStringValue(final String col) {
        return (String) findValue(col);
    }

    public Serializable findValue(final String col) {
        if (this.column != null && this.column.equals(col)) {
            return this.value;
        }
        if (this.nodes == null) {
            return null;
        }
        for (FilterNode n : this.nodes) {
            if (n == null) {
                continue;
            }
            Serializable val = n.findValue(col);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    public final FilterNode or(FilterNode node) {
        return any(node, true);
    }

    public final FilterNode or(String column, Serializable value) {
        return or(column, null, value);
    }

    public final FilterNode or(String column, FilterExpress express, Serializable value) {
        return or(new FilterNode(column, express, value));
    }

    public final <F extends Serializable> FilterNode or(LambdaSupplier<F> func) {
        return or(func, null);
    }

    public final <F extends Serializable> FilterNode or(LambdaSupplier<F> func, FilterExpress express) {
        return or(new FilterNode(LambdaSupplier.readColumn(func), express, func.get()));
    }

    public final <T, F extends Serializable> FilterNode or(LambdaFunction<T, F> func, F value) {
        return or(func, null, value);
    }

    public final <T, F extends Serializable> FilterNode or(LambdaFunction<T, F> func, FilterExpress express, F value) {
        return or(new FilterNode(LambdaFunction.readColumn(func), express, value));
    }

    public final FilterNode and(FilterNode node) {
        return any(node, false);
    }

    public final FilterNode and(String column, Serializable value) {
        return and(column, null, value);
    }

    public final FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterNode(column, express, value));
    }

    public final <F extends Serializable> FilterNode and(LambdaSupplier<F> func) {
        return and(func, null);
    }

    public final <F extends Serializable> FilterNode and(LambdaSupplier<F> func, FilterExpress express) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), express, func.get()));
    }

    public final <T, F extends Serializable> FilterNode and(LambdaFunction<T, F> func, F value) {
        return and(func, null, value);
    }

    public final <T, F extends Serializable> FilterNode and(LambdaFunction<T, F> func, FilterExpress express, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), express, value));
    }

    public FilterNode eq(String column, Serializable value) {
        return and(new FilterNode(column, EQ, value));
    }

    public <F extends Serializable> FilterNode eq(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), EQ, func.get()));
    }

    public <T, F extends Serializable> FilterNode eq(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), EQ, value));
    }

    public FilterNode igEq(String column, Serializable value) {
        return and(new FilterNode(column, IG_EQ, value));
    }

    public <F extends Serializable> FilterNode igEq(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_EQ, func.get()));
    }

    public <T, F extends Serializable> FilterNode igEq(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_EQ, value));
    }

    public FilterNode notEq(String column, Serializable value) {
        return and(new FilterNode(column, NE, value));
    }

    public <F extends Serializable> FilterNode notEq(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NE, func.get()));
    }

    public <T, F extends Serializable> FilterNode notEq(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NE, value));
    }

    public FilterNode igNotEq(String column, Serializable value) {
        return and(new FilterNode(column, IG_NE, value));
    }

    public <F extends Serializable> FilterNode igNotEq(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_NE, func.get()));
    }

    public <T, F extends Serializable> FilterNode igNotEq(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_NE, value));
    }

    public FilterNode gt(String column, Number value) {
        return and(new FilterNode(column, GT, value));
    }

    public <F extends Number> FilterNode gt(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), GT, func.get()));
    }

    public <T, F extends Number> FilterNode gt(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), GT, value));
    }

    public FilterNode lt(String column, Number value) {
        return and(new FilterNode(column, LT, value));
    }

    public <F extends Number> FilterNode lt(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LT, func.get()));
    }

    public <T, F extends Number> FilterNode lt(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LT, value));
    }

    public FilterNode ge(String column, Number value) {
        return and(new FilterNode(column, GE, value));
    }

    public <F extends Number> FilterNode ge(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), GE, func.get()));
    }

    public <T, F extends Number> FilterNode ge(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), GE, value));
    }

    public FilterNode le(String column, Number value) {
        return and(new FilterNode(column, LE, value));
    }

    public <F extends Number> FilterNode le(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LE, func.get()));
    }

    public <T, F extends Number> FilterNode le(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LE, value));
    }

    public FilterNode like(String column, String value) {
        return and(new FilterNode(column, LIKE, value));
    }

    public FilterNode like(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LIKE, func.get()));
    }

    public <T> FilterNode like(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LIKE, value));
    }

    public FilterNode notLike(String column, String value) {
        return and(new FilterNode(column, NOT_LIKE, value));
    }

    public FilterNode notLike(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_LIKE, func.get()));
    }

    public <T> FilterNode notLike(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_LIKE, value));
    }

    public FilterNode igLike(String column, String value) {
        return and(new FilterNode(column, IG_LIKE, value));
    }

    public FilterNode igLike(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_LIKE, func.get()));
    }

    public <T> FilterNode igLike(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_LIKE, value));
    }

    public FilterNode igNotLike(String column, String value) {
        return and(new FilterNode(column, IG_NOT_LIKE, value));
    }

    public FilterNode igNotLike(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_NOT_LIKE, func.get()));
    }

    public <T> FilterNode igNotLike(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_NOT_LIKE, value));
    }

    public FilterNode starts(String column, String value) {
        return and(new FilterNode(column, STARTS, value));
    }

    public FilterNode starts(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), STARTS, func.get()));
    }

    public <T> FilterNode starts(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), STARTS, value));
    }

    public FilterNode ends(String column, String value) {
        return and(new FilterNode(column, ENDS, value));
    }

    public FilterNode ends(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), ENDS, func.get()));
    }

    public <T> FilterNode ends(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), ENDS, value));
    }

    public FilterNode notStarts(String column, String value) {
        return and(new FilterNode(column, NOT_STARTS, value));
    }

    public FilterNode notStarts(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_STARTS, func.get()));
    }

    public <T> FilterNode notStarts(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_STARTS, value));
    }

    public FilterNode notEnds(String column, String value) {
        return and(new FilterNode(column, NOT_ENDS, value));
    }

    public FilterNode notEnds(LambdaSupplier<String> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_ENDS, func.get()));
    }

    public <T> FilterNode notEnds(LambdaFunction<T, String> func, String value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_ENDS, value));
    }

    public FilterNode lenEq(String column, Number value) {
        return and(new FilterNode(column, LEN_EQ, value));
    }

    public <F extends Number> FilterNode lenEq(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LEN_EQ, func.get()));
    }

    public <T, F extends Number> FilterNode lenEq(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LEN_EQ, value));
    }

    public FilterNode lenGt(String column, Number value) {
        return and(new FilterNode(column, LEN_GT, value));
    }

    public <F extends Number> FilterNode lenGt(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LEN_GT, func.get()));
    }

    public <T, F extends Number> FilterNode lenGt(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LEN_GT, value));
    }

    public FilterNode lenLt(String column, Number value) {
        return and(new FilterNode(column, LEN_LT, value));
    }

    public <F extends Number> FilterNode lenLt(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LEN_LT, func.get()));
    }

    public <T, F extends Number> FilterNode lenLt(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LEN_LT, value));
    }

    public FilterNode lenGe(String column, Number value) {
        return and(new FilterNode(column, LEN_GE, value));
    }

    public <F extends Number> FilterNode lenGe(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LEN_GE, func.get()));
    }

    public <T, F extends Number> FilterNode lenGe(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LEN_GE, value));
    }

    public FilterNode lenLe(String column, Number value) {
        return and(new FilterNode(column, LEN_LE, value));
    }

    public <F extends Number> FilterNode lenLe(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), LEN_LE, func.get()));
    }

    public <T, F extends Number> FilterNode lenLe(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), LEN_LE, value));
    }

    public FilterNode contain(String column, Serializable value) {
        return and(new FilterNode(column, CONTAIN, value));
    }

    public <F extends Serializable> FilterNode contain(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), CONTAIN, func.get()));
    }

    public <T, F extends Serializable> FilterNode contain(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), CONTAIN, value));
    }

    public FilterNode notContain(String column, Serializable value) {
        return and(new FilterNode(column, NOT_CONTAIN, value));
    }

    public <F extends Serializable> FilterNode notContain(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_CONTAIN, func.get()));
    }

    public <T, F extends Serializable> FilterNode notContain(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_CONTAIN, value));
    }

    public FilterNode igContain(String column, Serializable value) {
        return and(new FilterNode(column, IG_CONTAIN, value));
    }

    public <F extends Serializable> FilterNode igContain(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_CONTAIN, func.get()));
    }

    public <T, F extends Serializable> FilterNode igContain(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_CONTAIN, value));
    }

    public FilterNode igNotContain(String column, Serializable value) {
        return and(new FilterNode(column, IG_NOT_CONTAIN, value));
    }

    public <F extends Serializable> FilterNode igNotContain(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IG_NOT_CONTAIN, func.get()));
    }

    public <T, F extends Serializable> FilterNode igNotContain(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IG_NOT_CONTAIN, value));
    }

    public FilterNode between(String column, Range value) {
        return and(new FilterNode(column, BETWEEN, value));
    }

    public <F extends Range> FilterNode between(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), BETWEEN, func.get()));
    }

    public <T, F extends Range> FilterNode between(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), BETWEEN, value));
    }

    public FilterNode notBetween(String column, Range value) {
        return and(new FilterNode(column, NOT_BETWEEN, value));
    }

    public <F extends Range> FilterNode notBetween(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_BETWEEN, func.get()));
    }

    public <T, F extends Range> FilterNode notBetween(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_BETWEEN, value));
    }

    public FilterNode in(String column, Serializable value) {
        return and(new FilterNode(column, IN, value));
    }

    public FilterNode in(String column, Stream stream) {
        return and(new FilterNode(column, IN, stream == null ? null : (Serializable) stream.toArray()));
    }

    public FilterNode in(String column, Collection collection) {
        return and(new FilterNode(column, IN, (Serializable) collection));
    }

    public FilterNode in(LambdaSupplier func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IN, (Serializable) func.get()));
    }

    public <T, F extends Object> FilterNode in(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IN, (Serializable) value));
    }

    public FilterNode notIn(String column, Serializable value) {
        return and(new FilterNode(column, NOT_IN, value));
    }

    public FilterNode notIn(String column, Stream stream) {
        return and(new FilterNode(column, NOT_IN, stream == null ? null : (Serializable) stream.toArray()));
    }

    public FilterNode notIn(String column, Collection collection) {
        return and(new FilterNode(column, NOT_IN, (Serializable) collection));
    }

    public FilterNode notIn(LambdaSupplier func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_IN, (Serializable) func.get()));
    }

    public <T, F extends Object> FilterNode notIn(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_IN, (Serializable) value));
    }

    public FilterNode isNull(String column) {
        return and(new FilterNode(column, IS_NULL, null));
    }

    public <F extends Serializable> FilterNode isNull(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IS_NULL, null));
    }

    public <T, F extends Serializable> FilterNode isNull(LambdaFunction<T, F> func) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IS_NULL, null));
    }

    public FilterNode notNull(String column) {
        return and(new FilterNode(column, NOT_NULL, null));
    }

    public <F extends Serializable> FilterNode notNull(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_NULL, null));
    }

    public <T, F extends Serializable> FilterNode notNull(LambdaFunction<T, F> func) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_NULL, null));
    }

    public FilterNode isEmpty(String column) {
        return and(new FilterNode(column, IS_EMPTY, null));
    }

    public <F extends Serializable> FilterNode isEmpty(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), IS_EMPTY, null));
    }

    public <T, F extends Serializable> FilterNode isEmpty(LambdaFunction<T, F> func) {
        return and(new FilterNode(LambdaFunction.readColumn(func), IS_EMPTY, null));
    }

    public FilterNode notEmpty(String column) {
        return and(new FilterNode(column, NOT_EMPTY, null));
    }

    public <F extends Serializable> FilterNode notEmpty(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_EMPTY, null));
    }

    public <T, F extends Serializable> FilterNode notEmpty(LambdaFunction<T, F> func) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_EMPTY, null));
    }

    public FilterNode opand(String column, Number value) {
        return and(new FilterNode(column, OPAND, value));
    }

    public <F extends Number> FilterNode opand(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), OPAND, func.get()));
    }

    public <T, F extends Number> FilterNode opand(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), OPAND, value));
    }

    public FilterNode opor(String column, Number value) {
        return and(new FilterNode(column, OPOR, value));
    }

    public <F extends Number> FilterNode opor(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), OPOR, func.get()));
    }

    public <T, F extends Number> FilterNode opor(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), OPOR, value));
    }

    public FilterNode notOpand(String column, Number value) {
        return and(new FilterNode(column, NOT_OPAND, value));
    }

    public <F extends Number> FilterNode notOpand(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), NOT_OPAND, func.get()));
    }

    public <T, F extends Number> FilterNode notOpand(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), NOT_OPAND, value));
    }

    public FilterNode fvmode(String column, FilterExpValue value) {
        return and(new FilterNode(column, FV_MOD, value));
    }

    public <F extends FilterExpValue> FilterNode fvmode(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), FV_MOD, func.get()));
    }

    public <T, F extends FilterExpValue> FilterNode fvmode(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), FV_MOD, value));
    }

    public FilterNode fvdiv(String column, FilterExpValue value) {
        return and(new FilterNode(column, FV_DIV, value));
    }

    public <F extends FilterExpValue> FilterNode fvdiv(LambdaSupplier<F> func) {
        return and(new FilterNode(LambdaSupplier.readColumn(func), FV_DIV, func.get()));
    }

    public <T, F extends FilterExpValue> FilterNode fvdiv(LambdaFunction<T, F> func, F value) {
        return and(new FilterNode(LambdaFunction.readColumn(func), FV_DIV, value));
    }

    //-----------------------------------------------------------------------
    /**
     * 该方法需要重载
     *
     * @param <T>         Entity类的泛型
     * @param func        EntityInfo的加载器
     * @param update      是否用于更新的JOIN
     * @param joinTabalis 关联表集合
     * @param haset       已拼接过的字段名
     * @param info        Entity类的EntityInfo
     *
     * @return SQL的join语句 不存在返回null
     */
    protected <T> CharSequence createSQLJoin(final Function<Class, EntityInfo> func, final boolean update, final Map<Class, String> joinTabalis, final Set<String> haset, final EntityInfo<T> info) {
        if (joinTabalis == null || this.nodes == null) {
            return null;
        }
        StringBuilder sb = null;
        for (FilterNode node : this.nodes) {
            CharSequence cs = node.createSQLJoin(func, update, joinTabalis, haset, info);
            if (cs == null) {
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder();
            }
            sb.append(cs);
        }
        return sb;
    }

    /**
     * 该方法需要重载
     *
     * @return 是否存在关联表
     */
    protected boolean isjoin() {
        if (this.nodes == null) {
            return false;
        }
        for (FilterNode node : this.nodes) {
            if (node.isjoin()) {
                return true;
            }
        }
        return false;
    }

    protected final Map<Class, String> getJoinTabalis() {
        if (!isjoin()) {
            return null;
        }
        Map<Class, String> map = new HashMap<>();
        putJoinTabalis(map);
        return map;
    }

    protected void putJoinTabalis(Map<Class, String> map) {
        if (this.nodes == null) {
            return;
        }
        for (FilterNode node : this.nodes) {
            node.putJoinTabalis(map);
        }
    }

    /**
     * 该方法需要重载
     *
     * @param entityApplyer EntityInfo的加载器
     *
     * @return 是否可以使用缓存
     */
    protected boolean isCacheUseable(final Function<Class, EntityInfo> entityApplyer) {
        if (this.nodes == null) {
            return true;
        }
        for (FilterNode node : this.nodes) {
            if (!node.isCacheUseable(entityApplyer)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 该方法需要重载
     *
     * @param source      AbstractDataSqlSource
     * @param <T>         Entity类的泛型
     * @param joinTabalis 关联表的集合
     * @param info        EntityInfo
     *
     * @return JOIN的SQL语句
     */
    protected <T> CharSequence createSQLExpress(AbstractDataSqlSource source, final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        CharSequence sb0 = this.column == null || this.column.isEmpty() || this.column.charAt(0) == '#' || info == null
            ? null : createElementSQLExpress(source, info, joinTabalis == null ? null : joinTabalis.get(info.getType()));
        if (this.nodes == null) {
            return sb0;
        }
        final StringBuilder rs = new StringBuilder();
        rs.append('(');
        boolean more = false;
        if (sb0 != null && sb0.length() > 2) {
            more = true;
            rs.append(sb0);
        }
        for (FilterNode node : this.nodes) {
            CharSequence f = node.createSQLExpress(source, info, joinTabalis);
            if (f == null || f.length() < 3) {
                continue;
            }
            if (more) {
                rs.append(or ? " OR " : " AND ");
            }
            rs.append(f);
            more = true;
        }
        rs.append(')');
        if (rs.length() < 5) {
            return null;
        }
        return rs;
    }

    //----------------------------------------------------------------------------------------------------
    private boolean needSplit(final Object val0) {
        return needSplit(express, val0);
    }

    private static boolean needSplit(final FilterExpress express, final Object val0) {
        if (val0 == null) {
            return false;
        }
        boolean items = express != IN && express != NOT_IN;  //是否数组集合的表达式
        if (!items) {
            if (val0.getClass().isArray()) {
                Class comp = val0.getClass().getComponentType();
                if (Array.getLength(val0) > 0) {
                    comp = Array.get(val0, 0).getClass();
                }
                if (!(comp.isPrimitive() || CharSequence.class.isAssignableFrom(comp) || Number.class.isAssignableFrom(comp))) {
                    items = true;
                }
            } else if (val0 instanceof Collection) {
                for (Object fv : (Collection) val0) {
                    if (fv == null) {
                        continue;
                    }
                    Class comp = fv.getClass();
                    if (!(comp.isPrimitive() || CharSequence.class.isAssignableFrom(comp) || Number.class.isAssignableFrom(comp))) {
                        items = true;
                    }
                    break;  //只需检测第一个值
                }
            }
        }
        return items;
    }

    protected final <T> CharSequence createElementSQLExpress(AbstractDataSqlSource source, final EntityInfo<T> info, String talis) {
        final Object val0 = getValue();
        if (needSplit(val0)) {
            if (val0 instanceof Collection) {
                StringBuilder sb = new StringBuilder();
                boolean more = ((Collection) val0).size() > 1;
                if (more) {
                    sb.append('(');
                }
                for (Object fv : (Collection) val0) {
                    if (fv == null) {
                        continue;
                    }
                    CharSequence cs = createElementSQLExpress(source, info, talis, fv);
                    if (cs == null) {
                        continue;
                    }
                    if (sb.length() > 2) {
                        sb.append(" AND ");
                    }
                    sb.append(cs);
                }
                if (more) {
                    sb.append(')');
                }
                return sb.length() > 3 ? sb : null;  //若sb的值只是()，则不过滤
            } else if (val0.getClass().isArray()) {
                StringBuilder sb = new StringBuilder();
                Object[] fvs = (Object[]) val0;
                boolean more = fvs.length > 1;
                if (more) {
                    sb.append('(');
                }
                for (Object fv : fvs) {
                    if (fv == null) {
                        continue;
                    }
                    CharSequence cs = createElementSQLExpress(source, info, talis, fv);
                    if (cs == null) {
                        continue;
                    }
                    if (sb.length() > 2) {
                        sb.append(" AND ");
                    }
                    sb.append(cs);
                }
                if (more) {
                    sb.append(')');
                }
                return sb.length() > 3 ? sb : null;  //若sb的值只是()，则不过滤
            }
        }
        return createElementSQLExpress(source, info, talis, val0);

    }

    private <T> CharSequence createElementSQLExpress(AbstractDataSqlSource source, final EntityInfo<T> info, String talis, Object val0) {
        if (column == null || this.column.isEmpty() || this.column.charAt(0) == '#') {
            return null;
        }
        if (talis == null) {
            talis = "a";
        }
        if (express == IS_NULL || express == NOT_NULL) {
            return new StringBuilder().append(info.getSQLColumn(talis, column)).append(' ').append(express.value());
        }
        if (express == IS_EMPTY || express == NOT_EMPTY) {
            return new StringBuilder().append(info.getSQLColumn(talis, column)).append(' ').append(express.value()).append(" ''");
        }
        if (val0 == null) {
            return null;
        }
        if (express == FV_MOD || express == FV_DIV) {
            FilterExpValue fv = (FilterExpValue) val0;
            return new StringBuilder().append(info.getSQLColumn(talis, column)).append(' ').append(express.value()).append(' ').append(fv.getLeft())
                .append(' ').append(fv.getExpress().value()).append(' ').append(fv.getRight());
        }
        final boolean fk = (val0 instanceof FilterColValue);
        CharSequence val = fk ? info.getSQLColumn(talis, ((FilterColValue) val0).getColumn()) : formatToString(express, info.getSQLValue(column, (Serializable) val0));
        if (val == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(32);
        if (express == CONTAIN) {
            return source.containSQL.replace("#{column}", info.getSQLColumn(talis, column)).replace("#{keystr}", val);
        }
        if (express == IG_CONTAIN) {
            return source.containSQL.replace("#{column}", "LOWER(" + info.getSQLColumn(talis, column) + ")").replace("#{keystr}", val);
        }
        if (express == NOT_CONTAIN) {
            return source.notContainSQL.replace("#{column}", info.getSQLColumn(talis, column)).replace("#{keystr}", val);
        }
        if (express == IG_NOT_CONTAIN) {
            return source.notContainSQL.replace("#{column}", "LOWER(" + info.getSQLColumn(talis, column) + ")").replace("#{keystr}", val);
        }

        if (express == LEN_EQ || express == LEN_LT || express == LEN_LE
            || express == LEN_GT || express == LEN_GE) {
            sb.append("LENGTH(").append(info.getSQLColumn(talis, column)).append(')');
        } else if (express == IG_EQ || express == IG_NE || express == IG_LIKE || express == IG_NOT_LIKE) {
            sb.append("LOWER(").append(info.getSQLColumn(talis, column)).append(')');
            if (fk) {
                val = "LOWER(" + info.getSQLColumn(talis, ((FilterColValue) val0).getColumn()) + ')';
            }
        } else {
            sb.append(info.getSQLColumn(talis, column));
        }
        sb.append(' ');
        switch (express) {
            case OPAND:
            case OPOR:
                sb.append(express.value()).append(' ').append(val).append(" > 0");
                break;
            case NOT_OPAND:
                sb.append(express.value()).append(' ').append(val).append(" = 0");
                break;
            default:
                sb.append(express.value()).append(' ').append(val);
                break;
        }
        return sb;
    }

    protected <T, E> Predicate<T> createPredicate(final EntityCache<T> cache) {
        if (cache == null || (column == null && this.nodes == null)) {
            return null;
        }
        Predicate<T> filter = createElementPredicate(cache, false);
        if (this.nodes == null) {
            return filter;
        }
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createPredicate(cache);
            if (f == null) {
                continue;
            }
            final Predicate<T> one = filter;
            final Predicate<T> two = f;
            filter = (filter == null) ? f : (or ? new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) || two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " OR " + two + ")";
                }
            } : new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) && two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " AND " + two + ")";
                }
            });
        }
        return filter;
    }

    protected final <T> Predicate<T> createElementPredicate(final EntityCache<T> cache, final boolean join) {
        if (this.column == null || this.column.isEmpty() || this.column.charAt(0) == '#') {
            return null;
        }
        return createElementPredicate(cache, join, cache.getAttribute(column));
    }

    @SuppressWarnings("unchecked")
    protected final <T> Predicate<T> createElementPredicate(final EntityCache<T> cache, final boolean join, final Attribute<T, Serializable> attr) {
        final Object val0 = getValue();
        if (needSplit(val0)) {
            if (val0 instanceof Collection) {
                Predicate<T> filter = null;
                for (Object fv : (Collection) val0) {
                    if (fv == null) {
                        continue;
                    }
                    Predicate<T> f = createElementPredicate(cache, join, attr, fv);
                    if (f == null) {
                        continue;
                    }
                    final Predicate<T> one = filter;
                    final Predicate<T> two = f;
                    filter = (filter == null) ? f : new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return one.test(t) && two.test(t);
                        }

                        @Override
                        public String toString() {
                            return "(" + one + " AND " + two + ")";
                        }
                    };
                }
                return filter;
            } else if (val0.getClass().isArray()) {
                final Class primtype = val0.getClass();
                Object val2 = val0;
                int ix = -1;
                if (primtype == boolean[].class) {
                    boolean[] bs = (boolean[]) val0;
                    Boolean[] ns = new Boolean[bs.length];
                    for (boolean v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == byte[].class) {
                    byte[] bs = (byte[]) val0;
                    Byte[] ns = new Byte[bs.length];
                    for (byte v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == short[].class) {
                    short[] bs = (short[]) val0;
                    Short[] ns = new Short[bs.length];
                    for (short v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == char[].class) {
                    char[] bs = (char[]) val0;
                    Character[] ns = new Character[bs.length];
                    for (char v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == int[].class) {
                    int[] bs = (int[]) val0;
                    Integer[] ns = new Integer[bs.length];
                    for (int v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == float[].class) {
                    float[] bs = (float[]) val0;
                    Float[] ns = new Float[bs.length];
                    for (float v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == long[].class) {
                    long[] bs = (long[]) val0;
                    Long[] ns = new Long[bs.length];
                    for (long v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                } else if (primtype == double[].class) {
                    double[] bs = (double[]) val0;
                    Double[] ns = new Double[bs.length];
                    for (double v : bs) {
                        ns[++ix] = v;
                    }
                    val2 = ns;
                }
                Predicate<T> filter = null;
                for (Object fv : (Object[]) val2) {
                    if (fv == null) {
                        continue;
                    }
                    Predicate<T> f = createElementPredicate(cache, join, attr, fv);
                    if (f == null) {
                        continue;
                    }
                    final Predicate<T> one = filter;
                    final Predicate<T> two = f;
                    filter = (filter == null) ? f : new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return one.test(t) && two.test(t);
                        }

                        @Override
                        public String toString() {
                            return "(" + one + " AND " + two + ")";
                        }
                    };
                }
                return filter;
            }
        }
        return createElementPredicate(cache, join, attr, val0);
    }

    @SuppressWarnings("unchecked")
    protected final <T> Predicate<T> createElementPredicate(final EntityCache<T> cache, final boolean join, final Attribute<T, Serializable> attr, Object val0) {
        if (attr == null) {
            return null;
        }
        final String field = join ? (cache.getType().getSimpleName() + "." + attr.field()) : attr.field();
        if (express == IS_NULL) {
            return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return attr.get(t) == null;
                }

                @Override
                public String toString() {
                    return field + " = null";
                }
            };
        }
        if (express == NOT_NULL) {
            return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return attr.get(t) != null;
                }

                @Override
                public String toString() {
                    return field + " != null";
                }
            };
        }
        if (express == IS_EMPTY) {
            return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    Object v = attr.get(t);
                    return v == null || v.toString().isEmpty();
                }

                @Override
                public String toString() {
                    return field + " = ''";
                }
            };
        }
        if (express == NOT_EMPTY) {
            return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    Object v = attr.get(t);
                    return v != null && !v.toString().isEmpty();
                }

                @Override
                public String toString() {
                    return field + " != ''";
                }
            };
        }
        if (val0 == null) {
            return null;
        }

        final Class atype = attr.type();
        final Class valtype = val0.getClass();
        if (atype != valtype && val0 instanceof Number) {
            if (atype == int.class || atype == Integer.class) {
                val0 = ((Number) val0).intValue();
            } else if (atype == long.class || atype == Long.class) {
                val0 = ((Number) val0).longValue();
            } else if (atype == short.class || atype == Short.class) {
                val0 = ((Number) val0).shortValue();
            } else if (atype == float.class || atype == Float.class) {
                val0 = ((Number) val0).floatValue();
            } else if (atype == byte.class || atype == Byte.class) {
                val0 = ((Number) val0).byteValue();
            } else if (atype == double.class || atype == Double.class) {
                val0 = ((Number) val0).doubleValue();
            }
        } else if (valtype.isArray()) {
            final int len = Array.getLength(val0);
            if (len == 0 && express == NOT_IN) {
                return null;
            }
            final Class compType = valtype.getComponentType();
            if (atype != compType && len > 0) {
                if (!compType.isPrimitive() && Number.class.isAssignableFrom(compType)) {
                    throw new SourceException("param(" + val0 + ") type not match " + atype + " for column " + column);
                }
                if (atype == int.class || atype == Integer.class) {
                    int[] vs = new int[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).intValue();
                    }
                    val0 = vs;
                } else if (atype == long.class || atype == Long.class) {
                    long[] vs = new long[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).longValue();
                    }
                    val0 = vs;
                } else if (atype == short.class || atype == Short.class) {
                    short[] vs = new short[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).shortValue();
                    }
                    val0 = vs;
                } else if (atype == float.class || atype == Float.class) {
                    float[] vs = new float[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).floatValue();
                    }
                    val0 = vs;
                } else if (atype == byte.class || atype == Byte.class) {
                    byte[] vs = new byte[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).byteValue();
                    }
                    val0 = vs;
                } else if (atype == double.class || atype == Double.class) {
                    double[] vs = new double[len];
                    for (int i = 0; i < len; i++) {
                        vs[i] = ((Number) Array.get(val0, i)).doubleValue();
                    }
                    val0 = vs;
                }
            }
        } else if (val0 instanceof Collection) {
            final Collection collection = (Collection) val0;
            if (collection.isEmpty() && express == NOT_IN) {
                return null;
            }
            if (!collection.isEmpty()) {
                Iterator it = collection.iterator();
                it.hasNext();
                Class fs = it.next().getClass();
                Class pfs = fs;
                if (fs == Integer.class) {
                    pfs = int.class;
                } else if (fs == Long.class) {
                    pfs = long.class;
                } else if (fs == Short.class) {
                    pfs = short.class;
                } else if (fs == Float.class) {
                    pfs = float.class;
                } else if (fs == Byte.class) {
                    pfs = byte.class;
                } else if (fs == Double.class) {
                    pfs = double.class;
                }
                if (Number.class.isAssignableFrom(fs) && atype != fs && atype != pfs) { //需要转换
                    ArrayList list = new ArrayList(collection.size());
                    if (atype == int.class || atype == Integer.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.intValue());
                        }
                    } else if (atype == long.class || atype == Long.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.longValue());
                        }
                    } else if (atype == short.class || atype == Short.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.shortValue());
                        }
                    } else if (atype == float.class || atype == Float.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.floatValue());
                        }
                    } else if (atype == byte.class || atype == Byte.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.byteValue());
                        }
                    } else if (atype == double.class || atype == Double.class) {
                        for (Number num : (Collection<Number>) collection) {
                            list.add(num.doubleValue());
                        }
                    }
                    val0 = list;
                }
            }
        }
        final Serializable val = (Serializable) val0;
        final boolean fk = (val instanceof FilterColValue);
        final Attribute<T, Serializable> fkattr = fk ? cache.getAttribute(((FilterColValue) val).getColumn()) : null;
        if (fk && fkattr == null) {
            throw new SourceException(cache.getType() + " not found column(" + ((FilterColValue) val).getColumn() + ")");
        }
        switch (express) {
            case EQ:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return Objects.equals(fkattr.get(t), attr.get(t));
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return val.equals(attr.get(t));
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + formatToString(val);
                    }
                };
            case IG_EQ:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        if (rs == null && rs2 == null) {
                            return true;
                        }
                        if (rs == null || rs2 == null) {
                            return false;
                        }
                        return Objects.equals(rs.toString().toLowerCase(), rs2.toString().toLowerCase());
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + " LOWER(" + fkattr.field() + ')';
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        if (rs == null) {
                            return false;
                        }
                        return val.toString().equalsIgnoreCase(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + ' ' + formatToString(val);
                    }
                };
            case NE:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return !Objects.equals(fkattr.get(t), attr.get(t));
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return !val.equals(attr.get(t));
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + formatToString(val);
                    }
                };
            case IG_NE:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        if (rs == null && rs2 == null) {
                            return false;
                        }
                        if (rs == null || rs2 == null) {
                            return true;
                        }
                        return !Objects.equals(rs.toString().toLowerCase(), rs2.toString().toLowerCase());
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + " LOWER(" + fkattr.field() + ')';
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        if (rs == null) {
                            return true;
                        }
                        return !val.toString().equalsIgnoreCase(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + ' ' + formatToString(val);
                    }
                };
            case GT:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo((Comparable) fkattr.get(t)) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo(((Comparable) val)) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + val;
                    }
                };
            case LT:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo((Comparable) fkattr.get(t)) < 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo(((Comparable) val)) < 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + val;
                    }
                };
            case GE:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo((Comparable) fkattr.get(t)) >= 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo(((Comparable) val)) >= 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + val;
                    }
                };
            case LE:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo((Comparable) fkattr.get(t)) <= 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return ((Comparable) attr.get(t)).compareTo(((Comparable) val)) <= 0;
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + val;
                    }
                };

            case FV_MOD:
                FilterExpValue fv0 = (FilterExpValue) val;
                switch (fv0.getExpress()) {
                    case EQ:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) == fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    case NE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) != fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    case GT:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) > fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    case LT:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) < fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    case GE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) >= fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    case LE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() % fv0.getLeft().longValue()) <= fv0.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv0.getLeft() + " " + fv0.getExpress().value() + " " + fv0.getRight();
                            }
                        };
                    default:
                        throw new SourceException("(" + fv0 + ")'s express illegal, must be =, !=, <, >, <=, >=");
                }
            case FV_DIV:
                FilterExpValue fv1 = (FilterExpValue) val;
                switch (fv1.getExpress()) {
                    case EQ:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) == fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    case NE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) != fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    case GT:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) > fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    case LT:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) < fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    case GE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) >= fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    case LE:
                        return new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return (((Number) attr.get(t)).longValue() / fv1.getLeft().longValue()) <= fv1.getRight().longValue();
                            }

                            @Override
                            public String toString() {
                                return field + " " + express.value() + " " + fv1.getLeft() + " " + fv1.getExpress().value() + " " + fv1.getRight();
                            }
                        };
                    default:
                        throw new SourceException("(" + fv1 + ")'s express illegal, must be =, !=, <, >, <=, >=");
                }
            case OPAND:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() & ((Number) fkattr.get(t)).longValue()) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + " & " + fkattr.field() + " > 0";
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + " & " + val + " > 0";
                    }
                };
            case OPOR:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() | ((Number) fkattr.get(t)).longValue()) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + " | " + fkattr.field() + " > 0";
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() | ((Number) val).longValue()) > 0;
                    }

                    @Override
                    public String toString() {
                        return field + " | " + val + " > 0";
                    }
                };
            case NOT_OPAND:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() & ((Number) fkattr.get(t)).longValue()) == 0;
                    }

                    @Override
                    public String toString() {
                        return field + " & " + fkattr.field() + " = 0";
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        return (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) == 0;
                    }

                    @Override
                    public String toString() {
                        return field + " & " + val + " = 0";
                    }
                };
            case LIKE:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs != null && rs2 != null && rs.toString().contains(rs2.toString());
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().contains(val.toString());
                    }

                    @Override
                    public String toString() {
                        return field + ' ' + express.value() + ' ' + formatToString(val);
                    }
                };
            case STARTS:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs != null && rs2 != null && rs.toString().startsWith(rs2.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " STARTSWITH " + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().startsWith(val.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " STARTSWITH " + formatToString(val);
                    }
                };
            case ENDS:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs != null && rs2 != null && rs.toString().endsWith(rs2.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " ENDSWITH " + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().endsWith(val.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " ENDSWITH " + formatToString(val);
                    }
                };
            case IG_LIKE:
                if (fk) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Object rs = attr.get(t);
                            Object rs2 = fkattr.get(t);
                            return rs != null && rs2 != null && rs.toString().toLowerCase().contains(rs2.toString().toLowerCase());
                        }

                        @Override
                        public String toString() {
                            return "LOWER(" + field + ") " + express.value() + " LOWER(" + fkattr.field() + ')';
                        }
                    };
                }
                final String valstr = val.toString().toLowerCase();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().toLowerCase().contains(valstr);
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + ' ' + formatToString(valstr);
                    }
                };
            case NOT_STARTS:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs == null || rs2 == null || !rs.toString().startsWith(rs2.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " NOT STARTSWITH " + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().startsWith(val.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " NOT STARTSWITH " + formatToString(val);
                    }
                };
            case NOT_ENDS:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs == null || rs2 == null || !rs.toString().endsWith(rs2.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " NOT ENDSWITH " + fkattr.field();
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().endsWith(val.toString());
                    }

                    @Override
                    public String toString() {
                        return field + " NOT ENDSWITH " + formatToString(val);
                    }
                };
            case IG_NOT_LIKE:
                if (fk) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Object rs = attr.get(t);
                            Object rs2 = fkattr.get(t);
                            return rs == null || rs2 == null || !rs.toString().toLowerCase().contains(rs2.toString().toLowerCase());
                        }

                        @Override
                        public String toString() {
                            return "LOWER(" + field + ") " + express.value() + " LOWER(" + fkattr.field() + ')';
                        }
                    };
                }
                final String valstr2 = val.toString().toLowerCase();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().toLowerCase().contains(valstr2);
                    }

                    @Override
                    public String toString() {
                        return "LOWER(" + field + ") " + express.value() + ' ' + formatToString(valstr2);
                    }
                };
            case LEN_EQ:
                final int intval = ((Number) val).intValue();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return (rs == null && 0 == intval) || (rs != null && rs.toString().length() == intval);
                    }

                    @Override
                    public String toString() {
                        return "LENGTH(" + field + ") " + express.value() + ' ' + intval;
                    }
                };
            case LEN_LT:
                final int intval2 = ((Number) val).intValue();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return (rs == null && 0 < intval2) || (rs != null && rs.toString().length() < intval2);
                    }

                    @Override
                    public String toString() {
                        return "LENGTH(" + field + ") " + express.value() + ' ' + intval2;
                    }
                };
            case LEN_LE:
                final int intval3 = ((Number) val).intValue();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return (rs == null && 0 <= intval3) || (rs != null && rs.toString().length() <= intval3);
                    }

                    @Override
                    public String toString() {
                        return "LENGTH(" + field + ") " + express.value() + ' ' + intval3;
                    }
                };
            case LEN_GT:
                final int intval4 = ((Number) val).intValue();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return (rs == null && 0 > intval4) || (rs != null && rs.toString().length() > intval4);
                    }

                    @Override
                    public String toString() {
                        return "LENGTH(" + field + ") " + express.value() + ' ' + intval4;
                    }
                };
            case LEN_GE:
                final int intval5 = ((Number) val).intValue();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return (rs == null && 0 >= intval5) || (rs != null && rs.toString().length() >= intval5);
                    }

                    @Override
                    public String toString() {
                        return "LENGTH(" + field + ") " + express.value() + ' ' + intval5;
                    }
                };
            case CONTAIN:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs != null && rs2 != null && rs2.toString().contains(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return fkattr.field() + ' ' + express.value() + ' ' + field;
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && val.toString().contains(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return "" + formatToString(val) + ' ' + express.value() + ' ' + field;
                    }
                };
            case IG_CONTAIN:
                if (fk) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Object rs = attr.get(t);
                            Object rs2 = fkattr.get(t);
                            return rs != null && rs2 != null && rs2.toString().toLowerCase().contains(rs.toString().toLowerCase());
                        }

                        @Override
                        public String toString() {
                            return " LOWER(" + fkattr.field() + ") " + express.value() + ' ' + "LOWER(" + field + ") ";
                        }
                    };
                }
                final String valstr3 = val.toString().toLowerCase();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && valstr3.contains(rs.toString().toLowerCase());
                    }

                    @Override
                    public String toString() {
                        return "" + formatToString(valstr3) + express.value() + ' ' + "LOWER(" + field + ") ";
                    }
                };
            case NOT_CONTAIN:
                return fk ? new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        Object rs2 = fkattr.get(t);
                        return rs == null || rs2 == null || !rs2.toString().contains(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return fkattr.field() + ' ' + express.value() + ' ' + field;
                    }
                } : new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !val.toString().contains(rs.toString());
                    }

                    @Override
                    public String toString() {
                        return "" + formatToString(val) + ' ' + express.value() + ' ' + field;
                    }
                };
            case IG_NOT_CONTAIN:
                if (fk) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Object rs = attr.get(t);
                            Object rs2 = fkattr.get(t);
                            return rs == null || rs2 == null || !rs2.toString().toLowerCase().contains(rs.toString().toLowerCase());
                        }

                        @Override
                        public String toString() {
                            return " LOWER(" + fkattr.field() + ") " + express.value() + ' ' + "LOWER(" + field + ") ";
                        }
                    };
                }
                final String valstr4 = val.toString().toLowerCase();
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !valstr4.contains(rs.toString().toLowerCase());
                    }

                    @Override
                    public String toString() {
                        return "" + formatToString(valstr4) + express.value() + ' ' + "LOWER(" + field + ") ";
                    }
                };
            case BETWEEN:
            case NOT_BETWEEN:
                Range range = (Range) val;
                final Comparable min = range.getMin();
                final Comparable max = range.getMax();
                if (express == BETWEEN) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Comparable rs = (Comparable) attr.get(t);
                            if (rs == null) {
                                return false;
                            }
                            if (min != null && min.compareTo(rs) >= 0) {
                                return false;
                            }
                            return !(max != null && max.compareTo(rs) <= 0);
                        }

                        @Override
                        public String toString() {
                            return field + " BETWEEN " + min + " AND " + max;
                        }
                    };
                }
                if (express == NOT_BETWEEN) {
                    return new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Comparable rs = (Comparable) attr.get(t);
                            if (rs == null) {
                                return true;
                            }
                            if (min != null && min.compareTo(rs) >= 0) {
                                return true;
                            }
                            return (max != null && max.compareTo(rs) <= 0);
                        }

                        @Override
                        public String toString() {
                            return field + " NOT BETWEEN " + min + " AND " + max;
                        }
                    };
                }
                return null;
            case IN:
            case NOT_IN:
                Predicate<T> filter;
                if (val instanceof Collection) {
                    Collection array = (Collection) val;
                    if (array.isEmpty()) { //express 只会是 IN
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + " []";
                            }
                        };
                    } else {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                return rs != null && array.contains(rs);
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + val;
                            }
                        };
                    }
                } else {
                    Class type = val.getClass();
                    if (Array.getLength(val) == 0) {//express 只会是 IN
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + " []";
                            }
                        };
                    } else if (type == int[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                int k = (int) rs;
                                for (int v : (int[]) val) {
                                    if (v == k) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((int[]) val);
                            }
                        };
                    } else if (type == short[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                short k = (short) rs;
                                for (short v : (short[]) val) {
                                    if (v == k) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((short[]) val);
                            }
                        };
                    } else if (type == long[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                long k = (long) rs;
                                for (long v : (long[]) val) {
                                    if (v == k) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((long[]) val);
                            }
                        };
                    } else if (type == float[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                float k = (float) rs;
                                for (float v : (float[]) val) {
                                    if (v == k) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((float[]) val);
                            }
                        };
                    } else if (type == double[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                double k = (double) rs;
                                for (double v : (double[]) val) {
                                    if (v == k) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((double[]) val);
                            }
                        };
                    } else {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) {
                                    return false;
                                }
                                for (Object v : (Object[]) val) {
                                    if (rs.equals(v)) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return field + ' ' + express.value() + ' ' + Arrays.toString((Object[]) val);
                            }
                        };
                    }
                }
                if (express == NOT_IN) {
                    final Predicate<T> filter2 = filter;
                    filter = new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return !filter2.test(t);
                        }

                        @Override
                        public String toString() {
                            return filter2.toString();
                        }
                    };
                }
                return filter;
        }
        return null;
    }

    @Override
    public String toString() {
        return toString(null).toString();
    }

    protected StringBuilder toString(final String prefix) {
        StringBuilder sb = new StringBuilder();
        StringBuilder element = toElementString(prefix);
        boolean more = element != null && element.length() > 0 && this.nodes != null;
        if (more) {
            sb.append('(');
        }
        sb.append(element);
        if (this.nodes != null) {
            for (FilterNode node : this.nodes) {
                String s = node.toString();
                if (s.length() < 1) {
                    continue;
                }
                if (sb.length() > 1) {
                    sb.append(or ? " OR " : " AND ");
                }
                sb.append(s);
            }
        }
        if (more) {
            sb.append(')');
        }
        return sb;
    }

    protected final StringBuilder toElementString(final String prefix) {
        Serializable val0 = getValue();
        if (needSplit(val0)) {
            if (val0 instanceof Collection) {
                StringBuilder sb = new StringBuilder();
                boolean more = ((Collection) val0).size() > 1;
                if (more) {
                    sb.append('(');
                }
                for (Object fv : (Collection) val0) {
                    if (fv == null) {
                        continue;
                    }
                    CharSequence cs = toElementString(prefix, fv);
                    if (cs == null) {
                        continue;
                    }
                    if (sb.length() > 2) {
                        sb.append(" AND ");
                    }
                    sb.append(cs);
                }
                if (more) {
                    sb.append(')');
                }
                return sb.length() > 3 ? sb : null;  //若sb的值只是()，则不过滤
            } else if (val0.getClass().isArray()) {
                StringBuilder sb = new StringBuilder();
                Object[] fvs = (Object[]) val0;
                boolean more = fvs.length > 1;
                if (more) {
                    sb.append('(');
                }
                for (Object fv : fvs) {
                    if (fv == null) {
                        continue;
                    }
                    CharSequence cs = toElementString(prefix, fv);
                    if (cs == null) {
                        continue;
                    }
                    if (sb.length() > 2) {
                        sb.append(" AND ");
                    }
                    sb.append(cs);
                }
                if (more) {
                    sb.append(')');
                }
                return sb.length() > 3 ? sb : null;  //若sb的值只是()，则不过滤
            }
        }
        return toElementString(prefix, val0);
    }

    protected final StringBuilder toElementString(final String prefix, Object ev) {
        StringBuilder sb = new StringBuilder();
        if (column != null) {
            String col = prefix == null ? column : (prefix + "." + column);
            if (express == IS_NULL || express == NOT_NULL) {
                sb.append(col).append(' ').append(express.value());
            } else if (express == IS_EMPTY || express == NOT_EMPTY) {
                sb.append(col).append(' ').append(express.value()).append(" ''");
            } else if (ev != null) {
                boolean lower = (express == IG_LIKE || express == IG_NOT_LIKE || express == IG_CONTAIN || express == IG_NOT_CONTAIN);
                sb.append(lower ? ("LOWER(" + col + ')') : col).append(' ').append(express.value()).append(' ').append(formatToString(express, ev));
            }
        }
        return sb;
    }

    private static CharSequence formatToString(Object value) {
        CharSequence sb = formatToString(null, value);
        return sb == null ? null : sb.toString();
    }

    private static CharSequence formatToString(FilterExpress express, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof CharSequence) {
            if (express == LIKE || express == NOT_LIKE) {
                value = "%" + value + '%';
            } else if (express == STARTS || express == NOT_STARTS) {
                value = value + "%";
            } else if (express == ENDS || express == NOT_ENDS) {
                value = "%" + value;
            } else if (express == IG_LIKE || express == IG_NOT_LIKE) {
                value = "%" + value.toString().toLowerCase() + '%';
            } else if (express == IG_CONTAIN || express == IG_NOT_CONTAIN
                || express == IG_EQ || express == IG_NE) {
                value = value.toString().toLowerCase();
            }
            return new StringBuilder().append('\'').append(value.toString().replace("'", "\\'")).append('\'');
        } else if (value instanceof Range) {
            Range range = (Range) value;
            boolean rangestring = range.getClass() == Range.StringRange.class;
            StringBuilder sb = new StringBuilder();
            if (rangestring) {
                sb.append('\'').append(range.getMin().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMin());
            }
            sb.append(" AND ");
            if (rangestring) {
                sb.append('\'').append(range.getMax().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMax());
            }
            return sb;
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return express == NOT_IN ? null : new StringBuilder("(NULL)");
            }
            if (len == 1) {
                Object firstval = Array.get(value, 0);
                if (firstval != null && firstval.getClass().isArray()) {
                    return formatToString(express, firstval);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (int i = 0; i < len; i++) {
                Object o = Array.get(value, i);
                if (sb.length() > 1) {
                    sb.append(',');
                }
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        } else if (value instanceof Collection) {
            Collection c = (Collection) value;
            if (c.isEmpty()) {
                return express == NOT_IN ? null : new StringBuilder("(NULL)");
            }
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (Object o : c) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        }
        return String.valueOf(value);
    }

    public final Serializable getValue() {
        return value;
    }

    public final void setValue(Serializable value) {
        this.value = value;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public final boolean isOr() {
        return or;
    }

    public final void setOr(boolean or) {
        this.or = or;
    }

    public final String getColumn() {
        return column;
    }

    public final void setColumn(String column) {
        this.column = column;
    }

    public final FilterExpress getExpress() {
        return express;
    }

    public final void setExpress(FilterExpress express) {
        this.express = express;
    }

    public final FilterNode[] getNodes() {
        return nodes;
    }

    public final void setNodes(FilterNode[] nodes) {
        this.nodes = nodes;
    }

}
