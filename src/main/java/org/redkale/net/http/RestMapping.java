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
 * 只能依附在Service实现类的public方法上，且方法如果throws只能是IOException  <br>
 * value默认为"/" + Service的类名去掉Service字样的小写字符串 (如HelloService，的默认路径为/hello)。  <br>
 * <p>
 * 详情见: https://redkale.org
 * 
 * @see org.redkale.net.http.RestService
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(RestMapping.RestMappings.class)
public @interface RestMapping {

    /**
     * 是否屏蔽该方法进行HttpMapping转换
     *
     * @return boolean
     */
    boolean ignore() default false;

    /**
     * 请求的方法名, 不能含特殊字符
     *
     * @return String
     */
    String name() default "";

    /**
     * 备注描述, 对应&#64;HttpMapping.comment
     *
     * @return String
     */
    String comment() default "";

    /**
     * 是否只接收RPC请求, 对应&#64;HttpMapping.rpcOnly
     *
     * @return boolean
     */
    boolean rpcOnly() default false;

    /**
     * 是否鉴权，默认需要鉴权, 对应&#64;HttpMapping.auth
     *
     * @return boolean
     */
    boolean auth() default true;

    /**
     * 操作ID值，鉴权时用到, 对应&#64;HttpMapping.actionid
     *
     * @return int
     */
    int actionid() default 0;

    /**
     * 结果缓存的秒数, 为0表示不缓存, 对应&#64;HttpMapping.cacheSeconds
     *
     * @return int
     */
    int cacheSeconds() default 0;

    /**
     * 允许方法(不区分大小写),如:GET/POST/PUT,为空表示允许所有方法, 对应&#64;HttpMapping.methods
     *
     * @return String[]
     */
    String[] methods() default {};

    /**
     * 返回结果的样例
     * for OpenAPI Specification 3.1.0
     *
     * @return String
     */
    String example() default "";

    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    @interface RestMappings {

        RestMapping[] value();
    }
}
