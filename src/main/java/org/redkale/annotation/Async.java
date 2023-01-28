/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.*;

/**
 * 异步模式标记。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Async {

    boolean value() default true;
}
