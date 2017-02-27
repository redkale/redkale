/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;

/**
 * 功能同JSR 315 (java-servlet 3.0) 规范中的 @WebInitParam
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebInitParam {

    /**
     * 参数名
     *
     * @return String
     */
    String name();

    /**
     * 参数值
     *
     * @return String
     */
    String value();

    /**
     * 参数描述
     *
     * @return String
     */
    String description() default "";
}
