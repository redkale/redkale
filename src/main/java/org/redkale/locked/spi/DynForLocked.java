/*
 *
 */
package org.redkale.locked.spi;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import org.redkale.service.LoadMode;

/**
 * {@link org.redkale.locked.Locked}注解的动态扩展版，会多一个字段信息 用于识别方法是否已经动态处理过
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface DynForLocked {

    String dynField();

    LoadMode mode() default LoadMode.ANY;
}
