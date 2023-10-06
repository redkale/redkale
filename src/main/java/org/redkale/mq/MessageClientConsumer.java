/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.Objects;
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
public abstract class MessageClientConsumer {

    protected final String[] topics;

    protected final String consumerid;

    protected MessageAgent messageAgent;

    protected final MessageClientProcessor processor;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected volatile boolean closed;

    protected MessageClientConsumer(MessageAgent messageAgent, String[] topics, final String consumerid, MessageClientProcessor processor) {
        Objects.requireNonNull(messageAgent);
        Objects.requireNonNull(topics);
        Objects.requireNonNull(consumerid);
        Objects.requireNonNull(processor);
        this.messageAgent = messageAgent;
        this.topics = topics;
        this.consumerid = consumerid;
        this.processor = processor;
    }

    public MessageClientProcessor getProcessor() {
        return processor;
    }

    public String[] getTopics() {
        return topics;
    }

    public abstract void start();

    public abstract void stop();

    public boolean isClosed() {
        return closed;
    }

}
