/*
 *
 */
package org.redkale.util;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Lambda的Supplier自定义类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 * @param <T> 泛型
 */
@FunctionalInterface
public interface LambdaSupplier<T> extends Supplier<T>, Serializable {

    public static <V> String readColumn(LambdaSupplier<V> func) {
        return Utility.readFieldName(func);
    }

    public static <V> Class<V> readClass(LambdaSupplier<V> func) {
        return Utility.readClassName(func);
    }
}
