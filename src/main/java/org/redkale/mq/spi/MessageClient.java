/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import org.redkale.cluster.ClusterRpcClient;
import org.redkale.util.RedkaleException;
import org.redkale.util.Traces;
import org.redkale.util.Utility;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class MessageClient implements ClusterRpcClient<MessageRecord, MessageRecord>, MessageProcessor {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final MessageAgent messageAgent;

    private final String appRespTopic;

    protected final ReentrantLock processorLock = new ReentrantLock();

    protected final AtomicLong msgSeqno;

    protected final String protocol;

    // key: reqTopic
    private final HashMap<String, MessageProcessor> messageProcessors = new HashMap<>();

    final ConcurrentHashMap<Long, MessageRespFuture> respQueue = new ConcurrentHashMap<>();

    protected MessageClient(String protocol, MessageAgent messageAgent, String appRespTopic) {
        this.protocol = protocol;
        this.messageAgent = messageAgent;
        this.appRespTopic = appRespTopic;
        this.msgSeqno = messageAgent.msgSeqno;
    }

    @Override
    public void process(final MessageRecord msg, long time) {
        MessageProcessor processor = messageProcessors.get(msg.getTopic());
        if (processor == null) {
            throw new RedkaleException(msg.getTopic() + " not found MessageProcessor, record=" + msg);
        } else {
            processor.process(msg, time);
        }
    }

    void putMessageRespProcessor() {
        this.messageProcessors.put(appRespTopic, new MessageRespProcessor(this));
    }

    public Collection<String> getTopics() {
        return this.messageProcessors.keySet();
    }

    @Override
    public CompletableFuture<Void> produceMessage(MessageRecord message) {
        return messageAgent.getMessageClientProducer().apply(message);
    }

    @Override
    public CompletableFuture<MessageRecord> sendMessage(final MessageRecord message) {
        CompletableFuture<MessageRecord> future = new CompletableFuture<>();
        try {
            if (Utility.isEmpty(message.getRespTopic())) {
                message.setRespTopic(appRespTopic);
            }
            messageAgent.getMessageClientProducer().apply(message);
            MessageRespFuture respNode = new MessageRespFuture(this, future, message);
            respQueue.put(message.getSeqid(), respNode);
            ScheduledThreadPoolExecutor executor = messageAgent.timeoutExecutor;
            if (executor != null && messageAgent.getTimeoutSeconds() > 0) {
                respNode.scheduledFuture =
                        executor.schedule(respNode, messageAgent.getTimeoutSeconds(), TimeUnit.SECONDS);
            }
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    // 非线程安全
    public void putMessageServlet(MessageServlet servlet) {
        String topic = servlet.getTopic();
        processorLock.lock();
        try {
            if (messageProcessors.containsKey(topic)) {
                throw new RedkaleException("req-topic(" + topic + ") is repeat");
            }
            messageProcessors.put(topic, servlet);
        } finally {
            processorLock.unlock();
        }
    }

    public boolean isEmpty() {
        return messageProcessors.size() < 1;
    }

    public MessageRecord createMessageRecord(byte ctype, String topic, String respTopic, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype, topic, respTopic, Traces.currentTraceid(), content);
    }

    public MessageRecord createMessageRecord(
            byte ctype, String topic, String respTopic, String traceid, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype, topic, respTopic, traceid, content);
    }

    public MessageRecord createMessageRecord(long seqid, byte ctype, String topic, String respTopic, byte[] content) {
        return new MessageRecord(seqid, ctype, topic, respTopic, Traces.currentTraceid(), content);
    }

    public MessageRecord createMessageRecord(
            long seqid, byte ctype, String topic, String respTopic, String traceid, byte[] content) {
        return new MessageRecord(seqid, ctype, topic, respTopic, traceid, content);
    }

    public String getProtocol() {
        return protocol;
    }

    public MessageAgent getMessageAgent() {
        return messageAgent;
    }

    public MessageCoder<MessageRecord> getClientMessageCoder() {
        return this.messageAgent.getMessageRecordCoder();
    }

    public MessageClientProducer getProducer() {
        return messageAgent.getMessageClientProducer();
    }

    public String getAppRespTopic() {
        return appRespTopic;
    }
}
