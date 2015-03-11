/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
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
     * @return
     */
    String name() default "";

    /**
     * 当字段类型是Number时， 如果值>=least() 则需要过滤， 否则跳过该字段
     *
     * @return
     */
    long least() default 1;

    /**
     * 当express="like" 是否把非空值首尾加上%
     *
     * @return
     */
    boolean likefit() default true;

    /**
     * LIKE、NOT LIKE时是否区分大小写
     * <p>
     * @return
     */
    boolean ignoreCase() default false;

    /**
     * express的默认值根据字段类型的不同而不同:
     * 数组 --> IN
     * Range --> Between
     * 其他 --> =
     *
     * @return
     */
    FilterExpress express() default FilterExpress.EQUAL;

}
