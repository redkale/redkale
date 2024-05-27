/*
 *
 */
package org.redkale.mq;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在{@link org.redkale.mq.MessageConsumer}子类上
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@ClassDepends
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ResourceConsumer {

    String mq() default "";

    String group() default "";

    String[] topics();

    ConvertType convertType() default ConvertType.JSON;
}
