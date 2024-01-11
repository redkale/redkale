/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * 参数名注解，编译时加上 <b>-parameters</b> 参数可以不用此注解
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Inherited
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface Param {

    String value();

    String comment() default "";
}
