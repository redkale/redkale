/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.charset.StandardCharsets;
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
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import static org.redkale.mq.MessageRecord.CTYPE_HTTP_REQUEST;
import static org.redkale.mq.MessageRecord.CTYPE_HTTP_RESULT;
import static org.redkale.mq.MessageRecord.CTYPE_STRING;
import org.redkale.net.http.HttpResult;
import org.redkale.net.http.HttpSimpleRequest;
import org.redkale.util.RedkaleException;
import org.redkale.util.Traces;
import org.redkale.util.Utility;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class MessageClient implements ClusterRpcClient<MessageRecord, MessageRecord>, MessageProcessor {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final MessageAgent messageAgent;

    private final String appRespTopic;

    private final String reqTopicPrefix;

    protected final ReentrantLock processorLock = new ReentrantLock();

    protected final AtomicLong msgSeqno;

    //key: reqTopic
    private final HashMap<String, MessageProcessor> messageProcessors = new HashMap<>();

    final ConcurrentHashMap<Long, MessageRespFuture> respQueue = new ConcurrentHashMap<>();

    protected MessageClient(MessageAgent messageAgent, String appRespTopic, String reqTopicPrefix) {
        this.messageAgent = messageAgent;
        this.appRespTopic = appRespTopic;
        this.reqTopicPrefix = reqTopicPrefix;
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
    public void produceMessage(MessageRecord message) {
        messageAgent.getMessageClientProducer().apply(message);
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
                respNode.scheduledFuture = executor.schedule(respNode, messageAgent.getTimeoutSeconds(), TimeUnit.SECONDS);
            }
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    //非线程安全
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

    public MessageRecord createMessageRecord(String respTopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), 0,
            null, null, respTopic, Traces.currentTraceid(), content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(String topic, String respTopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), 0,
            null, topic, respTopic, Traces.currentTraceid(), content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(String topic, String respTopic, String traceid, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), 0,
            null, topic, respTopic, traceid, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(int userid, String topic, String respTopic, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), userid,
            null, topic, respTopic, Traces.currentTraceid(), content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(int userid, String topic, String respTopic, String traceid, String content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), CTYPE_STRING, 1, 0, System.currentTimeMillis(), userid,
            null, topic, respTopic, traceid, content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    public MessageRecord createMessageRecord(String topic, String respTopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), 0,
            null, topic, respTopic, Traces.currentTraceid(), convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(String topic, String respTopic, String traceid, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), 0,
            null, topic, respTopic, traceid, convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int userid, String topic, String respTopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), userid,
            null, topic, respTopic, Traces.currentTraceid(), convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int userid, String groupid, String topic, String respTopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, 0, System.currentTimeMillis(), userid,
            groupid, topic, respTopic, Traces.currentTraceid(), convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(int flag, int userid, String groupid, String topic, String respTopic, Convert convert, Object bean) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype(convert, bean), 1, flag, System.currentTimeMillis(), userid,
            groupid, topic, respTopic, Traces.currentTraceid(), convert.convertToBytes(bean));
    }

    public MessageRecord createMessageRecord(String topic, String respTopic, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), (byte) 0, topic, respTopic, Traces.currentTraceid(), content);
    }

    public MessageRecord createMessageRecord(long seqid, String topic, String respTopic, byte[] content) {
        return new MessageRecord(seqid, (byte) 0, topic, respTopic, Traces.currentTraceid(), content);
    }

    protected MessageRecord createMessageRecord(byte ctype, String topic, String respTopic, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype, topic, respTopic, Traces.currentTraceid(), content);
    }

    protected MessageRecord createMessageRecord(byte ctype, String topic, String respTopic, String traceid, byte[] content) {
        return new MessageRecord(msgSeqno.incrementAndGet(), ctype, topic, respTopic, traceid, content);
    }

    protected MessageRecord createMessageRecord(long seqid, byte ctype, String topic, String respTopic, byte[] content) {
        return new MessageRecord(seqid, ctype, topic, respTopic, Traces.currentTraceid(), content);
    }

    protected MessageRecord createMessageRecord(long seqid, byte ctype, String topic, String respTopic, String traceid, byte[] content) {
        return new MessageRecord(seqid, ctype, topic, respTopic, traceid, content);
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

    public void start() {
    }

    public void stop() {
    }

    public MessageAgent getMessageAgent() {
        return messageAgent;
    }

    public MessageCoder<MessageRecord> getClientMessageCoder() {
        return this.messageAgent.getClientMessageCoder();
    }

    public MessageClientProducer getProducer() {
        return messageAgent.getMessageClientProducer();
    }

    public String getAppRespTopic() {
        return appRespTopic;
    }

    public String getReqTopicPrefix() {
        return reqTopicPrefix;
    }

}
