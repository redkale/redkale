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
public class MessageClientProducers {

    protected final MessageClientProducer[] producers;

    protected final AtomicInteger index = new AtomicInteger();

    public MessageClientProducers(MessageClientProducer[] producers) {
        this.producers = producers;
    }

    public MessageClientProducer getProducer(MessageRecord message) {
        if (this.producers.length == 1) {
            return this.producers[0];
        }
        return producers[Math.abs(index.incrementAndGet()) % producers.length];
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
