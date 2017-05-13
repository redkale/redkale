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
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebServlet {

    /**
     * HttpServlet资源名
     *
     * @return String
     */
    String name() default "";

    /**
     * 是否自动添加url前缀, 对应application.xml中servlets节点的path属性
     *
     * @return boolean
     */
    boolean repair() default true;

    /**
     * url匹配规则
     *
     * @return String[]
     */
    String[] value() default {};

    /**
     * 模块ID，一个HttpServlet尽量只有提供一个模块的服务
     *
     * @return int
     */
    int moduleid() default 0;

    /**
     * 是否鉴权，默认不鉴权 <br>
     * 标记为不鉴权的HttpServlet， 其内部所有方法都将不进行鉴权
     *
     * @return boolean
     */
    boolean auth() default false;

    /**
     * 备注描述
     *
     * @return String
     */
    String comment() default "";
}
