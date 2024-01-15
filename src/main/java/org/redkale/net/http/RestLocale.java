/*
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 只能注解于Service类的方法的String参数或参数内的String字段
 * <p>
 * 用于获取HTTP请求端的Locale国际化 HttpRequest.getLocale
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
@Documented
@Target({PARAMETER, FIELD})
@Retention(RUNTIME)
public @interface RestLocale {

    /**
     * 备注描述
     *
     * @return String
     */
    String comment() default "";
}
