/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

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

    protected MessageConsumer respConsumer;

    protected String respTopic;

    protected String respConsumerid;

    protected boolean finest;

    protected boolean finer;

    protected boolean fine;

    protected MessageClient(MessageAgent messageAgent) {
        this.messageAgent = messageAgent;
        this.finest = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINEST);
        this.finer = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINER);
        this.fine = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINE);
    }

    protected CompletableFuture<Void> close() {
        if (this.respConsumer == null) return CompletableFuture.completedFuture(null);
        return this.respConsumer.shutdown();
    }

    protected CompletableFuture<MessageRecord> sendMessage(MessageRecord message, boolean needresp, AtomicLong counter) {
        CompletableFuture<MessageRecord> future = new CompletableFuture<>();
        try {
            if (this.respConsumer == null) {
                synchronized (this) {
                    if (this.respConsumerid == null) this.respConsumerid = "consumer-" + this.respTopic;
                    if (this.respConsumer == null) {
                        MessageProcessor processor = (msg, callback) -> {
                            long now = System.currentTimeMillis();
                            MessageRespFutureNode node = respNodes.remove(msg.getSeqid());
                            if (node == null) {
                                messageAgent.logger.log(Level.WARNING, MessageClient.this.getClass().getSimpleName() + " process " + msg + " error， not found msgnode");
                                return;
                            }
                            AtomicLong ncer = node.getCounter();
                            if (ncer != null) ncer.decrementAndGet();
                            node.future.complete(msg);
                            long cha = now - msg.createtime;
                            if (cha > 1000 && fine) {
                                messageAgent.logger.log(Level.FINER, "MessageRespFutureNode.process (mqs.delays = " + cha + "ms) message: " + msg);
                            } else if (cha > 50 && finer) {
                                messageAgent.logger.log(Level.FINER, "MessageRespFutureNode.process (mq.delays = " + cha + "ms) message: " + msg);
                            } else if (finest) {
                                messageAgent.logger.log(Level.FINEST, "MessageRespFutureNode.process (mq.delay = " + cha + "ms) message: " + msg);
                            }

                        };
                        MessageConsumer one = messageAgent.createConsumer(new String[]{respTopic}, respConsumerid, processor);
                        one.startup().join();
                        this.respConsumer = one;
                    }
                }
            }
            if (needresp && (message.getResptopic() == null || message.getResptopic().isEmpty())) {
                message.setResptopic(respTopic);
            }
            if (counter != null) counter.incrementAndGet();
            getProducer().apply(message);
            if (needresp) {
                MessageRespFutureNode node = new MessageRespFutureNode(message, respNodes, counter, future);
                respNodes.put(message.getSeqid(), node);
                ScheduledThreadPoolExecutor executor = messageAgent.timeoutExecutor;
                if (executor != null) executor.schedule(node, 30, TimeUnit.SECONDS);
            } else {
                future.complete(null);
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        } finally {
            return future;
        }
    }

    protected abstract MessageProducers getProducer();
}
