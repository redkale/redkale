/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class MessageProducer extends Thread {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected volatile boolean closed;

    public abstract CompletableFuture apply(MessageRecord message);

    public abstract void waitFor();

    public boolean isClosed() {
        return closed;
    }

    public abstract void close();
}
