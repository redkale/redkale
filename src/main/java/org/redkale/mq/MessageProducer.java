/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public abstract class MessageProducer {

    protected final Logger logger;

    protected final String name;

    protected final AtomicBoolean closed = new AtomicBoolean();

    protected MessageProducer(String name, Logger logger) {
        this.name = name;
        this.logger = logger;
    }

    public abstract CompletableFuture<Void> apply(MessageRecord message);

    public abstract CompletableFuture<Void> startup();

    public boolean isClosed() {
        return closed.get();
    }

    public abstract CompletableFuture<Void> shutdown();
}
