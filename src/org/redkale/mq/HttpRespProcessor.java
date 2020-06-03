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
public class HttpRespProcessor implements MessageProcessor {

    protected final Logger logger;

    protected final MessageAgent agent;

    protected final ConcurrentHashMap<Long, RespFutureNode> respNodes = new ConcurrentHashMap<>();

    public HttpRespProcessor(Logger logger, MessageAgent agent) {
        this.logger = logger;
        this.agent = agent;
    }

    @Override
    public void process(MessageRecord message) {
        RespFutureNode node = respNodes.get(message.getSeqid());
        if (node == null) {
            logger.log(Level.WARNING, HttpRespProcessor.class.getSimpleName() + " process " + message + " error");
            return;
        }
        node.future.complete(message);
    }

    public CompletableFuture<MessageRecord> createFuture(long seqid) {
        CompletableFuture<MessageRecord> future = new CompletableFuture<>();
        RespFutureNode node = new RespFutureNode(seqid, future);
        respNodes.put(seqid, node);
        return future;
    }

    protected static class RespFutureNode {

        public final long seqid;

        public final long createtime;

        public final CompletableFuture<MessageRecord> future;

        public RespFutureNode(long seqid, CompletableFuture<MessageRecord> future) {
            this.seqid = seqid;
            this.future = future;
            this.createtime = System.currentTimeMillis();
        }

    }
}
