/*
 *
 */
package org.redkale.mq;

import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Component;
import org.redkale.service.Local;
import org.redkale.util.AnyValue;

/**
 * MQ消费器， 实现类必须标记{@link org.redkale.mq.ResourceConsumer}
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
 *     public void onMessage(MessageConext context, TestBean message) {
 *         System.out.println("TestMessageConsumer消费消息, context: " + context + ", message: " + message);
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
 * @see org.redkale.mq.MessageConext
 * @see org.redkale.mq.ResourceConsumer
 * @see org.redkale.mq.Messaged
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
@Local
@Component
@ClassDepends
public interface MessageConsumer<T> {

    default void init(AnyValue config) {}

    public void onMessage(MessageConext context, T message);

    default void destroy(AnyValue config) {}
}
