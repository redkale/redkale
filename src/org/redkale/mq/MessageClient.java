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
public class MessageClient {

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
                        MessageProcessor processor = msg -> {
                            MessageRespFutureNode node = respNodes.get(msg.getSeqid());
                            if (node == null) {
                                messageAgent.logger.log(Level.WARNING, MessageClient.this.getClass().getSimpleName() + " process " + msg + " error");
                                return;
                            }
                            if (node.getCounter() != null) node.getCounter().decrementAndGet();
                            node.future.complete(msg);
                        };
                        this.consumer = messageAgent.createConsumer(respTopic, respConsumerid, processor);
                        this.consumer.startup().join();
                    }
                }
            }
            if (convertType != null) message.setFormat(convertType);
            if (needresp && message.getResptopic() == null) message.setResptopic(respTopic);
            messageAgent.getProducer().apply(message);
            if (counter != null) counter.incrementAndGet();
            if (needresp) {
                MessageRespFutureNode node = new MessageRespFutureNode(message.getSeqid(), counter, future);
                respNodes.put(message.getSeqid(), node);
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        } finally {
            return future;
        }
    }
}
