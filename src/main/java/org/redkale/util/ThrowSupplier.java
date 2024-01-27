/*
 *
 */
package org.redkale.util;

import org.redkale.annotation.DynClassDepends;

/**
 * 抛异常版的Supplier
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
@DynClassDepends
@FunctionalInterface
public interface ThrowSupplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     * @throws java.lang.Throwable Throwable
     */
    T get() throws Throwable;

}
