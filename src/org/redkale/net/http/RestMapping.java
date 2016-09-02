/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * 只能依附在Service实现类的public方法上
 * value默认为"/" + Service的类名去掉Service字样的小写字符串 (如HelloService，的默认路径为/hello)。
 * <p>
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(RestMappings.class)
public @interface RestMapping {

    boolean ignore() default false; //是否屏蔽该方法的转换

    /**
     * 请求的方法名, 不能含特殊字符
     * 默认为方法名的小写(若方法名以createXXX、updateXXX、deleteXXX、queryXXX、findXXX且XXXService为Service的类名将只截取XXX之前)
     *
     * @return name
     */
    String name() default "";

    String comment() default ""; //备注描述

    boolean auth() default false; //是否鉴权，默认不鉴权

    int actionid() default 0; //操作ID值，鉴权时用到, 对应&#64;WebAction.actionid

    String[] methods() default {};//允许方法(不区分大小写),如:GET/POST/PUT,为空表示允许所有方法, 对应&#64;WebAction.methods

    String contentType() default "";  //设置Response的ContentType 默认值为 text/plain; charset=utf-8

    String jsvar() default ""; //以application/javascript输出对象是指明js的对象名，该值存在时则忽略contentType()的值
}
