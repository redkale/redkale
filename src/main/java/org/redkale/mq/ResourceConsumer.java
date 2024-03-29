/*
 *
 */
package org.redkale.mq;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import org.redkale.convert.ConvertType;
import org.redkale.annotation.ClassDepends;

/**
 * MQ资源注解, 只能标记在{@link org.redkale.mq.MessageConsumer}子类上
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
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
