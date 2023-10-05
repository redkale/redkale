/*
 *
 */
package org.redkale.mq;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在MessageProducer类型字段上
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface ResourceProducer {

    String mq();

    boolean required() default true;

    ConvertType convertType() default ConvertType.JSON;

}
