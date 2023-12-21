/*
 *
 */
package org.redkale.util;

/**
 * 抛异常版的Supplier
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@FunctionalInterface
public interface ThrowSupplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get() throws Throwable;

}
