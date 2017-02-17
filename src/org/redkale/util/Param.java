/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * Param与Comment注解类主要用于Service上加上注释，便于apidoc生成API文档
 *
 * 依附在Service类的方法的参数上 ,数据结构与RestParam类似
 * name='&#38;' 表示当前用户
 * name='#'表示截取uri最后一段
 * name='#xxx:'表示从uri中/pipes/xxx:v/截取xxx:的值
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface Param {

    String name(); //参数名 name='&'表示当前用户;  name='#'表示截取uri最后一段;  name='#xxx:'表示从uri中/pipes/xxx:v/截取xxx:的值

    int radix() default 10; //转换数字byte/short/int/long时所用的进制数， 默认10进制

    boolean required() default true; //参数是否必传

    String comment() default ""; //备注描述
}
