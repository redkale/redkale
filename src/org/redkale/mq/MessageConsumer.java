/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class MessageConsumer extends Thread {

    protected final String topic;

    protected final Consumer<MessageRecord> processor;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected volatile boolean closed;

    protected MessageConsumer(String topic, Consumer<MessageRecord> processor) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(processor);
        this.topic = topic;
        this.processor = processor;
    }

    public Consumer<MessageRecord> getProcessor() {
        return processor;
    }

    public String getTopic() {
        return topic;
    }

    public abstract void waitFor();

    public boolean isClosed() {
        return closed;
    }

    public abstract void close();
}
