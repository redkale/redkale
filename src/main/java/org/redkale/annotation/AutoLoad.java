/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动加载。 使用场景： <br>
 *   1、被标记为&#64;AutoLoad(false)的Service类不会被自动加载, 当被依赖时才会被加载 <br>
 *   2、被标记为&#64;AutoLoad(false)的Servlet类不会被自动加载 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface AutoLoad {

    boolean value() default true;
}
