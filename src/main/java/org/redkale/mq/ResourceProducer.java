/*
 *
 */
package org.redkale.mq;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import org.redkale.convert.ConvertType;

/**
 * MQ资源注解, 只能标记在{@link org.redkale.mq.MessageProducer}类型字段上
 *
 * <blockquote>
 * <pre>
 * public class TestMessageService extends AbstractService {
 *
 *     &#64;ResourceProducer(mq = "mymq")
 *      private MessageProducer producer;
 *
 *      &#64;Override
 *      public void init(AnyValue config) {
 *           sendMessage();
 *      }
 *
 *      public void sendMessage() {
 *           TestBean bean = new TestBean(12345, "this is a message");
 *           System.out.println("生产消息: " + bean);
 *           producer.sendMessage("test_bean_topic", bean);
 *      }
 * }
 * </pre>
 * </blockquote>
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

    /**
     * {@link org.redkale.mq.spi.MessageAgent}对象对应名称
     *
     * @return MQ名称
     */
    String mq() default "";

    /**
     * 是否必须要加载，为ture时若mq()值对应{@link org.redkale.mq.spi.MessageAgent}对象不存在的情况下会抛异常
     *
     * @return 是否必须要加载
     */
    boolean required() default true;

    /**
     * 消息序列化类型
     *
     * @return  序列化类型
     */
    ConvertType convertType() default ConvertType.JSON;
}
