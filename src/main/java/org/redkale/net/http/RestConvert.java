/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 只能依附在Service实现类的public方法上, 当方法的返回值以JSON输出时对指定类型的转换设定。 <br>
 * 注意: 如果 type() == void.class 则无视其他参数固定返回 JsonFactory.create().skipAllIgnore(true).getConvert();
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(RestConvert.RestConverts.class)
public @interface RestConvert {

    /**
     * 配置项
     *
     * @return int
     */
    int features() default -1;

    /**
     * 是否忽略ConvertColumn.ignore=true的设置， 优先级最高 <br>
     * 值为true时会忽略onlyColumns、ignoreColumns、convertColumns的值
     *
     * @return boolean
     */
    boolean skipIgnore() default false;

    /**
     * 类型
     *
     * @return Class
     */
    Class type();

    /**
     * 仅显示的字段, 优先级低于skipIgnore <br>
     * 有值就会忽略ignoreColumns、convertColumns值
     *
     * @since 2.7.0
     * @return String[]
     */
    String[] onlyColumns() default {};

    /**
     * 屏蔽的字段
     *
     * @return String[]
     */
    String[] ignoreColumns() default {};

    /**
     * 允许输出的字段
     *
     * @return String[]
     */
    String[] convertColumns() default {};

    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    @interface RestConverts {

        RestConvert[] value();
    }
}
