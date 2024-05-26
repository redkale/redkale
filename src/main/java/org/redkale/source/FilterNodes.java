/*
 *
 */
package org.redkale.source;

import static org.redkale.source.FilterExpress.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.stream.Stream;
import org.redkale.util.LambdaFunction;
import org.redkale.util.LambdaSupplier;

/**
 * FilterNode的工具类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class FilterNodes {

    private FilterNodes() {
        // do nothing
    }

    public static FilterNode create(String column, Serializable value) {
        return new FilterNode(column, null, value);
    }

    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return new FilterNode(column, express, value);
    }

    public static <F extends Serializable> FilterNode create(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), null, func.get());
    }

    public static <F extends Serializable> FilterNode create(LambdaSupplier<F> func, FilterExpress express) {
        return new FilterNode(LambdaSupplier.readColumn(func), express, func.get());
    }

    public static <T, F extends Serializable> FilterNode create(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), null, value);
    }

    public static <T, F extends Serializable> FilterNode create(
            LambdaFunction<T, F> func, FilterExpress express, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), express, value);
    }

    public static FilterNode eq(String column, Serializable value) {
        return new FilterNode(column, EQ, value);
    }

    public static <F extends Serializable> FilterNode eq(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), EQ, func.get());
    }

    public static <T, F extends Serializable> FilterNode eq(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), EQ, value);
    }

    public static FilterNode igEq(String column, Serializable value) {
        return new FilterNode(column, IG_EQ, value);
    }

    public static <F extends Serializable> FilterNode igEq(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_EQ, func.get());
    }

    public static <T, F extends Serializable> FilterNode igEq(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_EQ, value);
    }

    public static FilterNode ne(String column, Serializable value) {
        return new FilterNode(column, NE, value);
    }

    public static <F extends Serializable> FilterNode ne(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NE, func.get());
    }

    public static <T, F extends Serializable> FilterNode ne(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), NE, value);
    }

    public static FilterNode igNe(String column, Serializable value) {
        return new FilterNode(column, IG_NE, value);
    }

    public static <F extends Serializable> FilterNode igNe(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_NE, func.get());
    }

    public static <T, F extends Serializable> FilterNode igNe(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_NE, value);
    }

    public static FilterNode gt(String column, Number value) {
        return new FilterNode(column, GT, value);
    }

    public static <F extends Number> FilterNode gt(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), GT, func.get());
    }

    public static <T, F extends Number> FilterNode gt(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), GT, value);
    }

    public static FilterNode lt(String column, Number value) {
        return new FilterNode(column, LT, value);
    }

    public static <F extends Number> FilterNode lt(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LT, func.get());
    }

    public static <T, F extends Number> FilterNode lt(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LT, value);
    }

    public static FilterNode ge(String column, Number value) {
        return new FilterNode(column, GE, value);
    }

    public static <F extends Number> FilterNode ge(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), GE, func.get());
    }

    public static <T, F extends Number> FilterNode ge(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), GE, value);
    }

    public static FilterNode le(String column, Number value) {
        return new FilterNode(column, LE, value);
    }

    public static <F extends Number> FilterNode le(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LE, func.get());
    }

    public static <T, F extends Number> FilterNode le(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LE, value);
    }

    public static FilterNode like(String column, String value) {
        return new FilterNode(column, LIKE, value);
    }

    public static FilterNode like(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LIKE, func.get());
    }

    public static <T> FilterNode like(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), LIKE, value);
    }

    public static FilterNode notLike(String column, String value) {
        return new FilterNode(column, NOT_LIKE, value);
    }

    public static FilterNode notLike(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_LIKE, func.get());
    }

    public static <T> FilterNode notLike(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_LIKE, value);
    }

    public static FilterNode igLike(String column, String value) {
        return new FilterNode(column, IG_LIKE, value);
    }

    public static FilterNode igLike(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_LIKE, func.get());
    }

    public static <T> FilterNode igLike(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_LIKE, value);
    }

    public static FilterNode igNotLike(String column, String value) {
        return new FilterNode(column, IG_NOT_LIKE, value);
    }

    public static FilterNode igNotLike(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_NOT_LIKE, func.get());
    }

    public static <T> FilterNode igNotLike(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_NOT_LIKE, value);
    }

    public static FilterNode starts(String column, String value) {
        return new FilterNode(column, STARTS, value);
    }

    public static FilterNode starts(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), STARTS, func.get());
    }

    public static <T> FilterNode starts(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), STARTS, value);
    }

    public static FilterNode ends(String column, String value) {
        return new FilterNode(column, ENDS, value);
    }

    public static FilterNode ends(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), ENDS, func.get());
    }

    public static <T> FilterNode ends(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), ENDS, value);
    }

    public static FilterNode notStarts(String column, String value) {
        return new FilterNode(column, NOT_STARTS, value);
    }

    public static FilterNode notStarts(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_STARTS, func.get());
    }

    public static <T> FilterNode notStarts(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_STARTS, value);
    }

    public static FilterNode notEnds(String column, String value) {
        return new FilterNode(column, NOT_ENDS, value);
    }

    public static FilterNode notEnds(LambdaSupplier<String> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_ENDS, func.get());
    }

    public static <T> FilterNode notEnds(LambdaFunction<T, String> func, String value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_ENDS, value);
    }

    public static FilterNode lenEq(String column, Number value) {
        return new FilterNode(column, LEN_EQ, value);
    }

    public static <F extends Number> FilterNode lenEq(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LEN_EQ, func.get());
    }

    public static <T, F extends Number> FilterNode lenEq(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LEN_EQ, value);
    }

    public static FilterNode lenGt(String column, Number value) {
        return new FilterNode(column, LEN_GT, value);
    }

    public static <F extends Number> FilterNode lenGt(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LEN_GT, func.get());
    }

    public static <T, F extends Number> FilterNode lenGt(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LEN_GT, value);
    }

    public static FilterNode lenLt(String column, Number value) {
        return new FilterNode(column, LEN_LT, value);
    }

    public static <F extends Number> FilterNode lenLt(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LEN_LT, func.get());
    }

    public static <T, F extends Number> FilterNode lenLt(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LEN_LT, value);
    }

    public static FilterNode lenGe(String column, Number value) {
        return new FilterNode(column, LEN_GE, value);
    }

    public static <F extends Number> FilterNode lenGe(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LEN_GE, func.get());
    }

    public static <T, F extends Number> FilterNode lenGe(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LEN_GE, value);
    }

    public static FilterNode lenLe(String column, Number value) {
        return new FilterNode(column, LEN_LE, value);
    }

    public static <F extends Number> FilterNode lenLe(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), LEN_LE, func.get());
    }

    public static <T, F extends Number> FilterNode lenLe(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), LEN_LE, value);
    }

    public static FilterNode contain(String column, Serializable value) {
        return new FilterNode(column, CONTAIN, value);
    }

    public static <F extends Serializable> FilterNode contain(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), CONTAIN, func.get());
    }

    public static <T, F extends Serializable> FilterNode contain(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), CONTAIN, value);
    }

    public static FilterNode notContain(String column, Serializable value) {
        return new FilterNode(column, NOT_CONTAIN, value);
    }

    public static <F extends Serializable> FilterNode notContain(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_CONTAIN, func.get());
    }

    public static <T, F extends Serializable> FilterNode notContain(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_CONTAIN, value);
    }

    public static FilterNode igContain(String column, Serializable value) {
        return new FilterNode(column, IG_CONTAIN, value);
    }

    public static <F extends Serializable> FilterNode igContain(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_CONTAIN, func.get());
    }

    public static <T, F extends Serializable> FilterNode igContain(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_CONTAIN, value);
    }

    public static FilterNode igNotContain(String column, Serializable value) {
        return new FilterNode(column, IG_NOT_CONTAIN, value);
    }

    public static <F extends Serializable> FilterNode igNotContain(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IG_NOT_CONTAIN, func.get());
    }

    public static <T, F extends Serializable> FilterNode igNotContain(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), IG_NOT_CONTAIN, value);
    }

    public static FilterNode between(String column, Range value) {
        return new FilterNode(column, BETWEEN, value);
    }

    public static <F extends Range> FilterNode between(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), BETWEEN, func.get());
    }

    public static <T, F extends Range> FilterNode between(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), BETWEEN, value);
    }

    public static FilterNode notBetween(String column, Range value) {
        return new FilterNode(column, NOT_BETWEEN, value);
    }

    public static <F extends Range> FilterNode notBetween(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_BETWEEN, func.get());
    }

    public static <T, F extends Range> FilterNode notBetween(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_BETWEEN, value);
    }

    public static FilterNode in(String column, Serializable value) {
        return new FilterNode(column, IN, value);
    }

    public static FilterNode in(String column, Stream stream) {
        return new FilterNode(column, IN, stream == null ? null : (Serializable) stream.toArray());
    }

    public static FilterNode in(String column, Collection collection) {
        return new FilterNode(column, IN, (Serializable) collection);
    }

    public static FilterNode in(LambdaSupplier func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IN, (Serializable) func.get());
    }

    public static <T, F extends Object> FilterNode in(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), IN, (Serializable) value);
    }

    public static FilterNode notIn(String column, Serializable value) {
        return new FilterNode(column, NOT_IN, value);
    }

    public static FilterNode notIn(String column, Stream stream) {
        return new FilterNode(column, NOT_IN, stream == null ? null : (Serializable) stream.toArray());
    }

    public static FilterNode notIn(String column, Collection collection) {
        return new FilterNode(column, NOT_IN, (Serializable) collection);
    }

    public static FilterNode notIn(LambdaSupplier func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_IN, (Serializable) func.get());
    }

    public static <T, F extends Object> FilterNode notIn(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_IN, (Serializable) value);
    }

    public static FilterNode isNull(String column) {
        return new FilterNode(column, IS_NULL, null);
    }

    public static <F extends Serializable> FilterNode isNull(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IS_NULL, null);
    }

    public static <T, F extends Serializable> FilterNode isNull(LambdaFunction<T, F> func) {
        return new FilterNode(LambdaFunction.readColumn(func), IS_NULL, null);
    }

    public static FilterNode notNull(String column) {
        return new FilterNode(column, NOT_NULL, null);
    }

    public static <F extends Serializable> FilterNode notNull(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_NULL, null);
    }

    public static <T, F extends Serializable> FilterNode notNull(LambdaFunction<T, F> func) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_NULL, null);
    }

    public static FilterNode isEmpty(String column) {
        return new FilterNode(column, IS_EMPTY, null);
    }

    public static <F extends Serializable> FilterNode isEmpty(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), IS_EMPTY, null);
    }

    public static <T, F extends Serializable> FilterNode isEmpty(LambdaFunction<T, F> func) {
        return new FilterNode(LambdaFunction.readColumn(func), IS_EMPTY, null);
    }

    public static FilterNode notEmpty(String column) {
        return new FilterNode(column, NOT_EMPTY, null);
    }

    public static <F extends Serializable> FilterNode notEmpty(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_EMPTY, null);
    }

    public static <T, F extends Serializable> FilterNode notEmpty(LambdaFunction<T, F> func) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_EMPTY, null);
    }

    public static FilterNode opand(String column, Number value) {
        return new FilterNode(column, OPAND, value);
    }

    public static <F extends Number> FilterNode opand(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), OPAND, func.get());
    }

    public static <T, F extends Number> FilterNode opand(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), OPAND, value);
    }

    public static FilterNode opor(String column, Number value) {
        return new FilterNode(column, OPOR, value);
    }

    public static <F extends Number> FilterNode opor(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), OPOR, func.get());
    }

    public <T, F extends Number> FilterNode opor(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), OPOR, value);
    }

    public static FilterNode notOpand(String column, Number value) {
        return new FilterNode(column, NOT_OPAND, value);
    }

    public static <F extends Number> FilterNode notOpand(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), NOT_OPAND, func.get());
    }

    public static <T, F extends Number> FilterNode notOpand(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), NOT_OPAND, value);
    }

    public static FilterNode fvmode(String column, FilterExpValue value) {
        return new FilterNode(column, FV_MOD, value);
    }

    public static <F extends FilterExpValue> FilterNode fvmode(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), FV_MOD, func.get());
    }

    public static <T, F extends FilterExpValue> FilterNode fvmode(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), FV_MOD, value);
    }

    public static FilterNode fvdiv(String column, FilterExpValue value) {
        return new FilterNode(column, FV_DIV, value);
    }

    public static <F extends FilterExpValue> FilterNode fvdiv(LambdaSupplier<F> func) {
        return new FilterNode(LambdaSupplier.readColumn(func), FV_DIV, func.get());
    }

    public static <T, F extends FilterExpValue> FilterNode fvdiv(LambdaFunction<T, F> func, F value) {
        return new FilterNode(LambdaFunction.readColumn(func), FV_DIV, value);
    }

    // ----------------------------------------------------------------------------------------------------
    public static FilterJoinNode joinInner(Class joinClass, String joinColumn, String column, Serializable value) {
        return joinInner(joinClass, new String[] {joinColumn}, column, value);
    }

    public static FilterJoinNode joinInner(
            Class joinClass, String joinColumn, String column, FilterExpress express, Serializable value) {
        return joinInner(joinClass, new String[] {joinColumn}, column, express, value);
    }

    public static FilterJoinNode joinInner(Class joinClass, String[] joinColumns, String column, Serializable value) {
        return joinInner(joinClass, joinColumns, column, null, value);
    }

    public static FilterJoinNode joinInner(
            Class joinClass, String[] joinColumns, String column, FilterExpress express, Serializable value) {
        return new FilterJoinNode(FilterJoinType.INNER, joinClass, joinColumns, column, express, value);
    }

    // ----------------------------------------------------------------------------------------------------
    static FilterExpress oldExpress(FilterExpress express) {
        switch (express) {
            case EQUAL:
                return EQ;
            case IGNORECASEEQUAL:
                return IG_EQ;
            case NOTEQUAL:
                return NE;
            case IGNORECASENOTEQUAL:
                return IG_NE;
            case GREATERTHAN:
                return GT;
            case LESSTHAN:
                return LT;
            case GREATERTHANOREQUALTO:
                return GE;
            case LESSTHANOREQUALTO:
                return LE;
            case NOTLIKE:
                return NOT_LIKE;
            case IGNORECASELIKE:
                return IG_LIKE;
            case IGNORECASENOTLIKE:
                return IG_NOT_LIKE;
            case STARTSWITH:
                return STARTS;
            case ENDSWITH:
                return ENDS;
            case NOTSTARTSWITH:
                return NOT_STARTS;
            case NOTENDSWITH:
                return NOT_ENDS;
            case LENGTH_EQUAL:
                return LEN_EQ;
            case LENGTH_GREATERTHAN:
                return LEN_GT;
            case LENGTH_LESSTHAN:
                return LEN_LT;
            case LENGTH_GREATERTHANOREQUALTO:
                return LEN_GE;
            case LENGTH_LESSTHANOREQUALTO:
                return LEN_LE;
            case NOTCONTAIN:
                return NOT_CONTAIN;
            case IGNORECASECONTAIN:
                return IG_CONTAIN;
            case IGNORECASENOTCONTAIN:
                return IG_NOT_CONTAIN;
            case NOTBETWEEN:
                return NOT_BETWEEN;
            case NOTIN:
                return NOT_IN;
            case ISNULL:
                return IS_NULL;
            case ISNOTNULL:
                return NOT_NULL;
            case ISEMPTY:
                return IS_EMPTY;
            case ISNOTEMPTY:
                return NOT_EMPTY;
            default:
                return express;
        }
    }
}
