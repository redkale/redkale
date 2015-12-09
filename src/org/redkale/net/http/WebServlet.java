/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;

/**
 * 功能同JSR 315 (java-servlet 3.0) 规范中的 @WebServlet
 *
 * @author zhangjx
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebServlet {

    String name() default "";

    boolean fillurl() default true;

    String[] value() default {};

    int moduleid() default 0;

    WebInitParam[] initParams() default {};
}
