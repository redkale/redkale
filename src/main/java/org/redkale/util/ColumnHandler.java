/*

*/

package org.redkale.util;

import java.util.function.BiFunction;

/**
 * 字段值转换器，常见于脱敏操作
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 * @param <F> 泛型
 * @param <V> 泛型
 */
public interface ColumnHandler<F, V> extends BiFunction<String, F, V> {

    /**
     * 字段值转换
     * @param field 字段名
     * @param value 字段值
     * @return 新的字段值
     */
    @Override
    public V apply(String field, F value);
}
