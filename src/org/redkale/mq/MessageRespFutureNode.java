/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;

/**
 * MQ管理器
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class MessageRespFutureNode implements Runnable {

    protected final long seqid;

    protected final long createtime;

    protected final AtomicLong counter;

    protected final CompletableFuture<MessageRecord> future;

    protected final Logger logger;

    protected final MessageRecord message;

    protected final ConcurrentHashMap<Long, MessageRespFutureNode> respNodes;

    protected ScheduledFuture<?> scheduledFuture;

    public MessageRespFutureNode(Logger logger, MessageRecord message, ConcurrentHashMap<Long, MessageRespFutureNode> respNodes, AtomicLong counter, CompletableFuture<MessageRecord> future) {
        this.logger = logger;
        this.message = message;
        this.seqid = message.getSeqid();
        this.respNodes = respNodes;
        this.counter = counter;
        this.future = future;
        this.createtime = System.currentTimeMillis();
    }

    @Override  //超时后被timeoutExecutor调用
    public void run() { //timeout
        respNodes.remove(this.seqid);
        future.completeExceptionally(new TimeoutException());
        logger.log(Level.WARNING, getClass().getSimpleName() + " wait msg: " + message + " timeout " + (System.currentTimeMillis() - createtime) + "ms"
            + (message.userid > 0 || (message.groupid != null && !message.groupid.isEmpty()) ? (message.userid > 0 ? (", userid:" + message.userid) : (", groupid:" + message.groupid)) : ""));
    }

    public long getSeqid() {
        return seqid;
    }

    public long getCreatetime() {
        return createtime;
    }

    public AtomicLong getCounter() {
        return counter;
    }

    public CompletableFuture<MessageRecord> getFuture() {
        return future;
    }
}
