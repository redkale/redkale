/*

*/

package org.redkale.convert;

/**
 * 字段值转换器，常见于脱敏操作
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 * @param <F> 字段类型
 */
public interface ConvertColumnTransfer<F> {

    /**
     * 字段值转换
     *
     * @param obj 父对象
     * @param field 字段名
     * @param value 字段值
     *
     * @return Object
     */
    public Object transfer(Object obj, String field, F value);
}
