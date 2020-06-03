/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletableFuture;

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
public class MessageRespFutureNode {

    protected final long seqid;

    protected final long createtime;

    protected final CompletableFuture<MessageRecord> future;

    public MessageRespFutureNode(long seqid, CompletableFuture<MessageRecord> future) {
        this.seqid = seqid;
        this.future = future;
        this.createtime = System.currentTimeMillis();
    }

    public long getSeqid() {
        return seqid;
    }

    public long getCreatetime() {
        return createtime;
    }

    public CompletableFuture<MessageRecord> getFuture() {
        return future;
    }
}
