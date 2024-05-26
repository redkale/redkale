/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public abstract class MessageClientConsumer implements MessageProcessor {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected MessageClient messageClient;

    protected MessageClientConsumer(MessageClient messageClient) {
        Objects.requireNonNull(messageClient);
        this.messageClient = messageClient;
    }

    public Collection<String> getTopics() {
        return messageClient.getTopics();
    }

    @Override
    public void process(MessageRecord message, long time) {
        messageClient.process(message, time);
    }

    public abstract void start();

    public abstract void stop();
}
