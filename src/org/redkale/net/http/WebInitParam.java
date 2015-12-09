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
 * @author zhangjx
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebInitParam {

    String name();

    String value();

    String description() default "";
}
