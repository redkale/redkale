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
public interface LambdaSupplier<T extends Serializable> extends Supplier<T>, Serializable {

    public static <T extends Serializable> String readColumn(LambdaSupplier<T> func) {
        return Utility.readFieldName(func);
    }
}
