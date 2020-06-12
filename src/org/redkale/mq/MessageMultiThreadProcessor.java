/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CountDownLatch;

/**
 *
 * @author zhangjx
 */
public class MessageMultiThreadProcessor implements MessageProcessor {

    protected final MessageAgent messageAgent;

    protected final MessageProcessor processor;

    protected CountDownLatch cdl;

    public MessageMultiThreadProcessor(MessageAgent messageAgent, MessageProcessor processor) {
        this.messageAgent = messageAgent;
        this.processor = processor;
    }

    @Override
    public void begin(int size) {
    }

    @Override
    public void process(MessageRecord message, Runnable callback) {
    }

    @Override
    public void commit() {
    }
}
