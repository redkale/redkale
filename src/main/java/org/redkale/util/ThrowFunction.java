/*
 *
 */
package org.redkale.util;

import org.redkale.annotation.ClassDepends;

/**
 * 抛异常版的Function
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 * @param <R> 泛型
 * @since 2.8.0
 */
@ClassDepends
@FunctionalInterface
public interface ThrowFunction<T, R> {

    /**
     * Gets a result.
     *
     * @param t T
     * @return r R
     * @throws java.lang.Throwable Throwable
     */
    R apply(T t) throws Throwable;
}
