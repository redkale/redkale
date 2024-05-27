/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 配合 &#64;HttpMapping 使用。 用于对&#64;HttpMapping方法中参数描述 <br>
 * 从RestService生成过来的HttpMapping，标记为&#64;RestUserid、&#64;RestAddress、&#64;RestLocale的参数不会生成HttpParam
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(HttpParam.HttpParams.class)
public @interface HttpParam {

	/**
	 * 参数名
	 *
	 * @return String
	 */
	String name();

	/**
	 * 参数的数据类型
	 *
	 * @return Class
	 */
	Class type();

	/**
	 * 参数的泛型数据类型在HttpServlet里的字段名,且字段类型必须是 java.lang.reflect.Type <br>
	 * 如果参数数据类型不是泛型，则值为空
	 *
	 * @since 2.5.0
	 * @return String
	 */
	String typeref() default "";

	/**
	 * 备注描述
	 *
	 * @return String
	 */
	String comment() default "";

	/**
	 * 参数来源类型
	 *
	 * @return HttpParameterStyle
	 */
	HttpParameterStyle style() default HttpParameterStyle.QUERY;

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
	 * 是否过期字段, only for OpenAPI Specification 3
	 *
	 * @return boolean
	 */
	boolean deprecated() default false;

	/**
	 * for OpenAPI Specification 3
	 *
	 * @return String
	 */
	String example() default "";

	/**
	 * 配合 &#64;HttpParam 使用。 用于对&#64;HttpParam中参数的来源类型
	 *
	 * <p>详情见: https://redkale.org
	 *
	 * @author zhangjx
	 */
	public enum HttpParameterStyle {
		QUERY,
		HEADER,
		COOKIE,
		BODY;
	}

	@Documented
	@Target({METHOD})
	@Retention(RUNTIME)
	@interface HttpParams {

		HttpParam[] value();
	}
}
