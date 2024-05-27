/*
 *
 */
package org.redkale.mq;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在{@link org.redkale.mq.MessageProducer}类型字段上
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.MessageProducer
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface ResourceProducer {

	String mq() default "";

	boolean required() default true;

	ConvertType convertType() default ConvertType.JSON;
}
