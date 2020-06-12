/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.redkale.convert.ConvertType;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public abstract class MessageClient {

    protected final ConcurrentHashMap<Long, MessageRespFutureNode> respNodes = new ConcurrentHashMap<>();

    protected final MessageAgent messageAgent;

    protected MessageConsumer consumer;

    protected String respTopic;

    protected String respConsumerid;

    protected ConvertType convertType;

    protected MessageClient(MessageAgent messageAgent) {
        this.messageAgent = messageAgent;
    }

    protected CompletableFuture<Void> close() {
        if (this.consumer == null) return CompletableFuture.completedFuture(null);
        return this.consumer.shutdown();
    }

    public String getRespTopic() {
        return this.respTopic;
    }

    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message) {
        return sendMessage(message, true, null);
    }

    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message, AtomicLong counter) {
        return sendMessage(message, true, counter);
    }

    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message, boolean needresp) {
        return sendMessage(message, needresp, null);
    }

    public final CompletableFuture<MessageRecord> sendMessage(MessageRecord message, boolean needresp, AtomicLong counter) {
        CompletableFuture<MessageRecord> future = new CompletableFuture<>();
        try {
            if (this.consumer == null) {
                synchronized (this) {
                    if (this.respConsumerid == null) this.respConsumerid = "consumer-" + this.respTopic;
                    if (this.consumer == null) {
                        MessageProcessor processor = (msg,callback) -> {
                            MessageRespFutureNode node = respNodes.get(msg.getSeqid());
                            if (node == null) {
                                messageAgent.logger.log(Level.WARNING, MessageClient.this.getClass().getSimpleName() + " process " + msg + " error");
                                return;
                            }
                            if (node.getCounter() != null) node.getCounter().decrementAndGet();
                            node.future.complete(msg);
                        };
                        this.consumer = messageAgent.createConsumer(new String[]{respTopic}, respConsumerid, processor);
                        this.consumer.startup().join();
                    }
                }
            }
            if (convertType != null) message.setFormat(convertType);
            if (needresp && (message.getResptopic() == null || message.getResptopic().isEmpty())) {
                message.setResptopic(respTopic);
            }
            if (counter != null) counter.incrementAndGet();
            getProducer().apply(message);
            if (needresp) {
                MessageRespFutureNode node = new MessageRespFutureNode(message.getSeqid(), respNodes, counter, future);
                respNodes.put(message.getSeqid(), node);
                ScheduledThreadPoolExecutor executor = messageAgent.timeoutExecutor;
                if (executor != null) executor.schedule(node, 6, TimeUnit.SECONDS);
            } else {
                future.complete(null);
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        } finally {
            return future;
        }
    }

    protected abstract MessageProducer getProducer();
}
