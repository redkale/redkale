/*
 *
 */
package org.redkale.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.*;

/**
 * 被标记的元素表示会被动态字节码调用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({TYPE, METHOD, FIELD})
@Retention(SOURCE)
public @interface ClassDepends {

	Class[] value() default {};
}
