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
 * <blockquote>
 * <pre>
 * &#64;ResourceConsumer(mq = "mymq", topics = "test_bean_topic")
 * public class TestMessageConsumer implements MessageConsumer&lt;TestBean&gt; {
 *
 *     &#64;Override
 *     public void init(AnyValue config) {
 *         System.out.println("执行 TestMessageConsumer.init");
 *     }
 *
 *     &#64;Override
 *     public void onMessage(MessageEvent&lt;TestBean&gt;[] events) {
 *        for(MessageEvent&lt;TestBean&gt; event : events) {
 *          System.out.println("TestMessageConsumer消费消息, message: " + event.getMessage());
 *        }
 *     }
 *
 *     &#64;Override
 *     public void destroy(AnyValue config) {
 *         System.out.println("执行 TestMessageConsumer.destroy");
 *     }
 * }
 * </pre>
 * </blockquote>
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
     * {@link org.redkale.mq.spi.MessageAgent}对象对应名称
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
     * 是否必须要加载，为ture时若mq()值对应{@link org.redkale.mq.spi.MessageAgent}对象不存在的情况下会抛异常
     *
     * @return 是否必须要加载
     */
    boolean required() default true;

    /**
     * 监听的topic, 当{@link #regexTopic() }值不为空时忽略此值
     *
     * @return  topic
     */
    String[] topics() default {};

    /**
     * 监听的topic， 与 {@link  #topics() }的值必须二选一，优先级高
     *
     * @return  topic正则表达式
     */
    String regexTopic() default "";

    /**
     * 消息序列化类型
     *
     * @return  序列化类型
     */
    ConvertType convertType() default ConvertType.JSON;
}
