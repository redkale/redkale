/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 配合 HttpServlet 使用。
 * 用于对&#64;WebServlet对应的url进行细分。 其url必须是包含WebServlet中定义的前缀， 且不能是正则表达式
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface HttpMapping {

    int actionid() default 0;

    String url();

    String[] methods() default {};//允许方法(不区分大小写),如:GET/POST/PUT,为空表示允许所有方法

    String comment() default ""; //备注描述

    boolean inherited() default true; //是否能被继承, 当 HttpServlet 被继承后该方法是否能被子类继承

    String result() default "Object"; //输出结果的数据类型

    Class[] results() default {}; //输出结果的数据类型集合，由于结果类型可能是泛型而注解的参数值不支持泛型，因此加入明细数据类型集合
}
