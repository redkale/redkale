/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterColumn {

    /**
     * 对应Entity Class中字段的名称， 而不是SQL字段名称
     *
     * @return 字段名
     */
    String name() default "";

    /**
     * 当字段类型是Number时， 如果值&gt;=least() 则需要过滤， 否则跳过该字段
     *
     * @return 最小可过滤值
     */
    long least() default 1;

    /**
     * express的默认值根据字段类型的不同而不同:
     * 数组 --&gt; IN
     * Range --&gt; Between
     * 其他 --&gt; =
     *
     * @return 字段表达式
     */
    FilterExpress express() default FilterExpress.EQUAL;

}
