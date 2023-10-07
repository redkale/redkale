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
public abstract class MessageClientProducer {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final String name;

    protected final AtomicBoolean closed = new AtomicBoolean();

    protected MessageClientProducer(String name) {
        this.name = name;
    }

    public abstract CompletableFuture<Void> apply(MessageRecord message);

    public abstract void stop();

    public boolean isClosed() {
        return closed.get();
    }

}
