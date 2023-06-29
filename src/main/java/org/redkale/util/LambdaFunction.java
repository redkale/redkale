/*
 *
 */
package org.redkale.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Lambda的Function自定义类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 * @param <T> 泛型
 * @param <R> 泛型
 */
@FunctionalInterface
public interface LambdaFunction<T, R> extends Function<T, R>, Serializable {

    public static <T> String[] readColumns(LambdaFunction<T, ?>... funcs) {
        String[] columns = new String[funcs.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = readColumn(funcs[i]);
        }
        return columns;
    }

    public static <T> String readColumn(LambdaFunction<T, ?> func) {
        return SerializedLambda.readColumn(func);
    }

}
