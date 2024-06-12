/*
 *
 */
package org.redkale.mq;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在{@link org.redkale.mq.MessageConsumer}子类上
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.MessageConsumer
 * @author zhangjx
 * @since 2.8.0
 */
@ClassDepends
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ResourceConsumer {

    /**
     * {@link org.redkale.mq.MessageAgent}对象对应名称
     *
     * @return MQ名称
     */
    String mq() default "";

    /**
     * MQ客户端分组名称
     *
     * @return 组名称
     */
    String group() default "";

    /**
     * 监听的topic
     *
     * @return  topic
     */
    String[] topics();

    /**
     * 消息序列化类型
     *
     * @return  序列化类型
     */
    ConvertType convertType() default ConvertType.JSON;
}
