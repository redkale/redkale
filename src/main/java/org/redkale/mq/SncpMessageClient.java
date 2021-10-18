/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class SncpMessageClient extends MessageClient {

    protected SncpMessageClient(MessageAgent messageAgent) {
        super(messageAgent);
        this.respTopic = messageAgent.generateSncpRespTopic();
    }

    @Override
    protected MessageProducers getProducer() {
        return messageAgent.getSncpProducer();
    }

    public String getRespTopic() {
        return this.respTopic;
    }

    //只发送消息，不需要响应
    public final void produceMessage(MessageRecord message) {
        produceMessage(message, null);
    }

    //只发送消息，不需要响应
    public final void produceMessage(MessageRecord message, AtomicLong counter) {
        sendMessage(message, false, counter);
    }

    //发送消息，需要响应
    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message) {
        return sendMessage(message, null);
    }

    //发送消息，需要响应
    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message, AtomicLong counter) {
        return sendMessage(message, true, counter);
    }

    @Override
    protected MessageRecord formatRespMessage(MessageRecord message) {
        if (message != null) message.ctype = MessageRecord.CTYPE_BSON_RESULT;
        return message;
    }
}
