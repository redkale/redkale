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
public class SncpRespProcessor implements MessageProcessor {

    protected final Logger logger;

    protected final MessageAgent messageAgent;

    protected final ConcurrentHashMap<Long, MessageRespFutureNode> respNodes = new ConcurrentHashMap<>();

    public SncpRespProcessor(Logger logger, MessageAgent messageAgent) {
        this.logger = logger;
        this.messageAgent = messageAgent;
    }

    @Override
    public void process(MessageRecord message) {
        MessageRespFutureNode node = respNodes.get(message.getSeqid());
        if (node == null) {
            logger.log(Level.WARNING, SncpRespProcessor.class.getSimpleName() + " process " + message + " error");
            return;
        }
        if (node.getCounter() != null) node.getCounter().decrementAndGet();
        node.future.complete(message);
    }

    public CompletableFuture<MessageRecord> createFuture(long seqid, AtomicLong counter) {
        CompletableFuture<MessageRecord> future = new CompletableFuture<>();
        MessageRespFutureNode node = new MessageRespFutureNode(seqid, counter, future);
        respNodes.put(seqid, node);
        return future;
    }

}
