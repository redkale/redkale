/*
 *
 */
package org.redkale.util;

import org.redkale.asm.AsmDepends;

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
@AsmDepends
@FunctionalInterface
public interface ThrowSupplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     * @throws java.lang.Throwable
     */
    T get() throws Throwable;

}
