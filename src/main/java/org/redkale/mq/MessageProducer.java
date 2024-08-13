/*
 *
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.Convert;

/**
 * MQ消息发送器 {@link org.redkale.mq.ResourceProducer}
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
 * @see org.redkale.mq.ResourceProducer
 * @author zhangjx
 * @since 2.8.0
 */
public interface MessageProducer {

    public CompletableFuture<Void> sendMessage(
            String topic, Integer partition, Convert convert, Type type, Object value);

    default CompletableFuture<Void> sendMessage(String topic, Integer partition, Convert convert, Object value) {
        return sendMessage(topic, partition, convert, (Type) null, value);
    }

    default CompletableFuture<Void> sendMessage(String topic, Integer partition, Object value) {
        return sendMessage(topic, partition, (Convert) null, (Type) null, value);
    }

    default CompletableFuture<Void> sendMessage(String topic, Convert convert, Type type, Object value) {
        return sendMessage(topic, (Integer) null, convert, type, value);
    }

    default CompletableFuture<Void> sendMessage(String topic, Convert convert, Object value) {
        return sendMessage(topic, (Integer) null, convert, value);
    }

    default CompletableFuture<Void> sendMessage(String topic, Object value) {
        return sendMessage(topic, (Integer) null, value);
    }
}
