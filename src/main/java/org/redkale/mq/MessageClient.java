/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import static org.redkale.mq.MessageRecord.*;
import org.redkale.net.http.*;

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

    protected final AtomicLong msgSeqno;

    protected MessageConsumer respConsumer;

    protected String respTopic;

    protected String respConsumerid;

    protected boolean finest;

    protected boolean finer;

    protected boolean fine;

    private final String clazzName;

    protected MessageClient(MessageAgent messageAgent) {
        this.messageAgent = messageAgent;
        this.msgSeqno = messageAgent == null ? new AtomicLong() : messageAgent.msgSeqno;
        this.finest = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINEST);
        this.finer = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINER);
        this.fine = messageAgent == null ? false : messageAgent.logger.isLoggable(Level.FINE);
        this.clazzName = getClass().getSimpleName();
    }

    protected CompletableFuture<Void> close() {
        if (this.respConsumer == null) return CompletableFuture.completedFuture(null);
        return this.respConsumer.shutdown();
    }

    protected CompletableFuture<MessageRecord> sendMessage(final MessageRecord message, boolean needresp, AtomicLong counter) {
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
                                messageAgent.logger.log(Level.WARNING, MessageClient.this.getClass().getSimpleName() + " process " + msg + " error， not found mqresp.futurenode");
                                return;
                            }
                            if (node.scheduledFuture != null) node.scheduledFuture.cancel(true);
                            AtomicLong ncer = node.getCounter();
                            if (ncer != null) ncer.decrementAndGet();
                            final long cha = now - msg.createtime;
                            if (finest) messageAgent.logger.log(Level.FINEST, clazzName + ".MessageRespFutureNode.receive (mq.delay = " + cha + "ms, mq.seqid = " + msg.getSeqid() + ")");
                            node.future.complete(msg);
                            long cha2 = System.currentTimeMillis() - now;
                            if ((cha > 1000 || cha2 > 1000) && fine) {
                                messageAgent.logger.log(Level.FINE, clazzName + ".MessageRespFutureNode.complete (mqs.delays = " + cha + "ms, mqs.completes = " + cha2 + "ms, mqs.counters = " + ncer + ") mqresp.msg: " + formatRespMessage(msg));
                            } else if ((cha > 50 || cha2 > 50) && finer) {
                                messageAgent.logger.log(Level.FINER, clazzName + ".MessageRespFutureNode.complete (mq.delays = " + cha + "ms, mq.completes = " + cha2 + "ms, mq.counters = " + ncer + ") mqresp.msg: " + formatRespMessage(msg));
                            } else if (finest) {
                                messageAgent.logger.log(Level.FINEST, clazzName + ".MessageRespFutureNode.complete (mq.delay = " + cha + "ms, mq.complete = " + cha2 + "ms, mq.counter = " + ncer + ") mqresp.msg: " + formatRespMessage(msg));
                            }
                        };
                        long ones = System.currentTimeMillis();
                        MessageConsumer one = messageAgent.createConsumer(new String[]{respTopic}, respConsumerid, processor);
                        one.startup().join();
                        long onee = System.currentTimeMillis() - ones;
                        if (finest) messageAgent.logger.log(Level.FINEST, clazzName + ".MessageRespFutureNode.startup " + onee + "ms ");
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
                MessageRespFutureNode node = new MessageRespFutureNode(messageAgent.logger, message, respNodes, counter, future);
                respNodes.put(message.getSeqid(), node);
                ScheduledThreadPoolExecutor executor = messageAgent.timeoutExecutor;
                if (executor != null) {
                    node.scheduledFuture = executor.schedule(node, 30, TimeUnit.SECONDS);
                }
            } else {
                future.complete(null);
            }
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        } finally {
            return future;
        }
    }

    protected MessageRecord formatRespMessage(MessageRecord message) {
        return message;
    }

    protected abstract MessageProducers getProducer();

    public MessageRecord createMessageRecord(String resptopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), 0, null, null, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(String topic, String resptopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), 0, null, topic, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(int userid, String topic, String resptopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), userid, null, topic, resptopic, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(String topic, String resptopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), 0, null, topic, resptopic, convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int userid, String topic, String resptopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), userid, null, topic, resptopic, convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int userid, String groupid, String topic, String resptopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), userid, groupid, topic, resptopic, convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int flag, int userid, String groupid, String topic, String resptopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, flag, System.currentTimeMillis(), userid, groupid, topic, resptopic, convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(String topic, String resptopic, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), (byte) 0, topic, resptopic, content);
    }

    public MessageRecord createMessageRecord(long seqid, String topic, String resptopic, byte[] content) {
        return new MessageRecord(seqid, (byte) 0, topic, resptopic, content);
    }

    protected MessageRecord createMessageRecord(byte ctype, String topic, String resptopic, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype, topic, resptopic, content);
    }

    protected MessageRecord createMessageRecord(long seqid, byte ctype, String topic, String resptopic, byte[] content) {
        return new MessageRecord(seqid, ctype, topic, resptopic, content);
    }

    private byte ctype(Convert convert, Object bean) {
        byte ctype = 0;
        if (convert instanceof JsonConvert) {
            if (bean instanceof HttpSimpleRequest) {
                ctype = CTYPE_HTTP_REQUEST;
            } else if (bean instanceof HttpResult) {
                ctype = CTYPE_HTTP_RESULT;
            }
        }
        return ctype;
    }
}
