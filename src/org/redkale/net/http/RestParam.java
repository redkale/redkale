/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 只能依附在Service类的方法的参数上
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented 
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface RestParam {

    String value(); //参数名

    /**
     * 参数是否从header取， 默认使用 request.getJsonParameter， 设置为true则使用 request.getJsonHeader 取值
     *
     * @return 是否从header取
     */
    boolean header() default false;
}
