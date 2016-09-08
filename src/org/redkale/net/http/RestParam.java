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

    String name(); //参数名

    int radix() default 10; //转换数字byte/short/int/long时所用的进制数， 默认10进制

    String comment() default ""; //备注描述
}
