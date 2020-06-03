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
public abstract class MessageConsumer extends Thread {

    protected final String topic;

    protected MessageAgent agent;

    protected final MessageProcessor processor;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected volatile boolean closed;

    protected MessageConsumer(MessageAgent agent, String topic, MessageProcessor processor) {
        Objects.requireNonNull(agent);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(processor);
        this.agent = agent;
        this.topic = topic;
        this.processor = processor;
    }

    public MessageProcessor getProcessor() {
        return processor;
    }

    public String getTopic() {
        return topic;
    }

    public abstract void waitFor();

    protected boolean isClosed() {
        return closed;
    }

    protected abstract void close();
}
