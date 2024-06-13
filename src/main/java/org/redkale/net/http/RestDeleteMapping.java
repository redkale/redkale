/*

*/

package org.redkale.net.http;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * DELETE方法版{@link org.redkale.net.http.RestMapping}
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.8.0
 * @see org.redkale.net.http.RestMapping
 * @see org.redkale.net.http.RestService
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(RestDeleteMapping.RestDeleteMappings.class)
public @interface RestDeleteMapping {

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
     * 返回结果的样例 for OpenAPI Specification 3.1.0
     *
     * @return String
     */
    String example() default "";

    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    @interface RestDeleteMappings {

        RestDeleteMapping[] value();
    }
}
