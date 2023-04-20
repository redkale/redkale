/*
 *
 */
package org.redkale.mq;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

/**
 * MQ资源注解
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface MessageConsumer {

    String mq();

    String group() default "";

    String[] topics();
}
