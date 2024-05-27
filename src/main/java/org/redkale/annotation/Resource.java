/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import java.lang.annotation.*;

/**
 * &#64;Resource(name = "@") 表示资源name采用所属对象的name <br>
 * &#64;Resource(name = "#name") 表示资源对象自身的name <br>
 * &#64;Resource(name = "#type") 表示资源对象自身的类型 <br>
 *
 * @since Common Annotations 1.0
 * @since 2.8.0
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Resource {

	public static final String PARENT_NAME = "@";

	public static final String SELF_NAME = "#name";

	public static final String SELF_TYPE = "#type";

	/**
	 * 是否必须存在
	 *
	 * @return boolean
	 * @since 2.8.0
	 */
	public boolean required() default true;

	/**
	 * 资源名称 <br>
	 *
	 * <blockquote>
	 *
	 * <pre>
	 * name规则:
	 * 1: "@"有特殊含义, 表示资源本身，"@"不能单独使用
	 * 2: "#name"、"#type"有特殊含义
	 * 3: 只能是字母、数字、(短横)-、(下划线)_、点(.)的组合
	 * </pre>
	 *
	 * </blockquote>
	 *
	 * @return String
	 */
	public String name() default "";

	/**
	 * 依赖注入的类型
	 *
	 * @return Class
	 */
	public Class<?> type() default Object.class;
}
