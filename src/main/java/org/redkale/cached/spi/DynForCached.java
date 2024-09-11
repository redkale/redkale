/*
 *
 */
package org.redkale.cached.spi;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.redkale.service.LoadMode;

/**
 * {@link org.redkale.cached.Cached}注解的动态扩展版，会多一个字段信息 用于识别方法是否已经动态处理过
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface DynForCached {

    String dynField();

    String manager();

    String name();

    String key();

    String localExpire();

    String remoteExpire();

    TimeUnit timeUnit();

    boolean nullable();

    LoadMode mode() default LoadMode.ANY;
}
