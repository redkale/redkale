/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class MessageProducers {

    protected final MessageProducer[] producers;

    protected final AtomicInteger index = new AtomicInteger();

    public MessageProducers(MessageProducer[] producers) {
        this.producers = producers;
    }

    public MessageProducer getProducer(MessageRecord message) {
        int hash = message.hash();
        if (hash == 0) {
            hash = index.incrementAndGet();
            if (index.get() > 1000 * producers.length) {
                synchronized (index) {
                    if (index.get() > 1000 * producers.length) {
                        index.addAndGet(-1000 * producers.length);
                    }
                }
            }
        }
        return producers[hash % producers.length];
    }

    public CompletableFuture<Void> apply(MessageRecord message) {
        return getProducer(message).apply(message);
    }

    public CompletableFuture<Void> startup() {
        CompletableFuture[] futures = new CompletableFuture[producers.length];
        for (int i = 0; i < producers.length; i++) {
            futures[i] = producers[i].startup();
        }
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<Void> shutdown() {
        CompletableFuture[] futures = new CompletableFuture[producers.length];
        for (int i = 0; i < producers.length; i++) {
            futures[i] = producers[i].shutdown();
        }
        return CompletableFuture.allOf(futures);
    }
}
