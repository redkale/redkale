/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;

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
        this.appRespTopic = messageAgent.generateAppSncpRespTopic();
    }

    @Override
    protected MessageClientProducer getProducer() {
        return messageAgent.getSncpMessageClientProducer();
    }

    public String getAppRespTopic() {
        return this.appRespTopic;
    }

    //只发送消息，不需要响应
    public final void produceMessage(MessageRecord message) {
        sendMessage(message, false);
    }

    //发送消息，需要响应
    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message) {
        return sendMessage(message, true);
    }

    @Override
    protected MessageRecord formatRespMessage(MessageRecord message) {
        if (message != null) {
            message.ctype = MessageRecord.CTYPE_BSON_RESULT;
        }
        return message;
    }
}
