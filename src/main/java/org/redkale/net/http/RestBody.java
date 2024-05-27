/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 只能注解于RestService类的方法的String/byte[]/JavaBean参数或参数内的String/byte[]/JavaBean字段
 *
 * <p>用于获取HTTP请求端的请求内容UTF-8编码字符串、byte[]、JavaBean
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({PARAMETER, FIELD})
@Retention(RUNTIME)
public @interface RestBody {

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
	 * 备注描述, 对应&#64;HttpParam.comment
	 *
	 * @return String
	 */
	String comment() default "";
}
