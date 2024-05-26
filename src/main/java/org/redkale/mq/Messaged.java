/*
 *
 */
package org.redkale.mq;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.redkale.convert.ConvertType;
import org.redkale.service.LoadMode;

/**
 * MQ资源注解, 只能标记在Service类方法上 1、方法必须是protected/public 2、方法不能是final/static
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.ResourceConsumer
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Messaged {

    String mq() default "";

    String group() default "";

    String[] topics();

    ConvertType convertType() default ConvertType.JSON;

    /**
     * Service加载模式
     *
     * @return 模式
     */
    LoadMode mode() default LoadMode.LOCAL;
}
