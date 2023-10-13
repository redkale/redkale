/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
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
public class MessageRespFuture implements Runnable {

    protected final long seqid;

    protected final long createTime;

    protected final CompletableFuture<MessageRecord> future;

    protected final MessageRecord message;

    protected final MessageClient messageClient;

    protected ScheduledFuture<?> scheduledFuture;

    public MessageRespFuture(MessageClient messageClient, CompletableFuture<MessageRecord> future, MessageRecord message) {
        this.messageClient = messageClient;
        this.message = message;
        this.seqid = message.getSeqid();
        this.future = future;
        this.createTime = System.currentTimeMillis();
    }

    @Override  //超时后被timeoutExecutor调用
    public void run() { //timeout
        messageClient.respQueue.remove(this.seqid);
        future.completeExceptionally(new TimeoutException("message-record: " + message));
        messageClient.logger.log(Level.WARNING, getClass().getSimpleName() + " wait msg: " + message + " timeout " + (System.currentTimeMillis() - createTime) + "ms"
            + (message.userid != null || (message.groupid != null && !message.groupid.isEmpty()) ? (message.userid != null ? (", userid:" + message.userid) : (", groupid:" + message.groupid)) : ""));
    }

    public long getSeqid() {
        return seqid;
    }

    public long getCreateTime() {
        return createTime;
    }

    public CompletableFuture<MessageRecord> getFuture() {
        return future;
    }
}
