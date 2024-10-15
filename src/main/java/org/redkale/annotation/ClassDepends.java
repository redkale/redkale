/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * 被标记的元素表示会被动态字节码调用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({TYPE, METHOD, FIELD, CONSTRUCTOR})
@Retention(SOURCE)
public @interface ClassDepends {

    Class[] value() default {};

    String comment() default "";
}
