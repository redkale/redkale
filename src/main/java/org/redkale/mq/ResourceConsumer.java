/*
 *
 */
package org.redkale.mq;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在MessageConsumer子类上
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
public @interface ResourceConsumer {

    String mq();

    String group() default "";

    String[] topics();

    ConvertType convertType() default ConvertType.JSON;
}
