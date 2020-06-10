/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public abstract class MessageConsumer {

    protected final String topic;

    protected final String consumerid;

    protected MessageAgent messageAgent;

    protected final MessageProcessor processor;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected volatile boolean closed;

    protected MessageConsumer(MessageAgent messageAgent, String topic,final String consumerid, MessageProcessor processor) {
        Objects.requireNonNull(messageAgent);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(consumerid);
        Objects.requireNonNull(processor);
        this.messageAgent = messageAgent;
        this.topic = topic;
        this.consumerid = consumerid;
        this.processor = processor;
    }

    public MessageProcessor getProcessor() {
        return processor;
    }

    public String getTopic() {
        return topic;
    }

    public abstract CompletableFuture<Void> startup();

    public boolean isClosed() {
        return closed;
    }

    public abstract CompletableFuture<Void> shutdown();
}
