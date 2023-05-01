/*
 *
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.Convert;

/**
 * MQ消息发送器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public interface MessageProducerSender {

    public CompletableFuture<Void> sendMessage(String topic, Object value);

    default CompletableFuture<Void> sendMessage(String topic, Convert convert, Object value) {
        return sendMessage(topic, convert.convertToBytes(value));
    }

    default CompletableFuture<Void> sendMessage(String topic, Convert convert, Type type, Object value) {
        return sendMessage(topic, convert.convertToBytes(type, value));
    }
}
