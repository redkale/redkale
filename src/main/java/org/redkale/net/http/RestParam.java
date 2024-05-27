/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 依附在RestService类的方法的参数上 <br>
 * name='&#38;' 表示当前用户 <br>
 * name='#'表示截取uri最后一段 <br>
 * name='#xxx:'表示从uri中/pipes/xxx:v/截取xxx:的值 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface RestParam {

	// name='&'表示当前用户;
	/**
	 * 参数名 <br>
	 * name='&#38;'表示当前用户; <br>
	 * name='#'表示截取uri最后一段; <br>
	 * name='#xxx:'表示从uri中/pipes/xxx:v/截取xxx:的值 <br>
	 *
	 * @return String
	 */
	String name() default "";

	/**
	 * 转换数字byte/short/int/long时所用的进制数， 默认10进制
	 *
	 * @return int
	 */
	int radix() default 10;

	/**
	 * 参数是否必传, 框架运行中不作验证, only for OpenAPI Specification 3
	 *
	 * @return boolean
	 */
	boolean required() default true;

	/**
	 * for OpenAPI Specification 3.1.0
	 *
	 * @return String
	 */
	String example() default "";

	/**
	 * 备注描述
	 *
	 * @return String
	 */
	String comment() default "";
}
