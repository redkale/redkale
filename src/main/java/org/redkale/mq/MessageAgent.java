/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceListener;
import org.redkale.boot.*;
import static org.redkale.boot.Application.RESNAME_APP_NAME;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.cluster.HttpRpcClient;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertFactory;
import org.redkale.convert.ConvertType;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.WorkThread;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

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
public abstract class MessageAgent implements Resourcable {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Resource(required = false)
    protected Application application;

    @Resource(name = RESNAME_APP_NODEID)
    protected int nodeid;

    @Resource(name = RESNAME_APP_NAME)
    protected String nodeName;

    protected String name;

    protected AnyValue config;

    @Nonnull
    private ExecutorService workExecutor;

    private int timeoutSeconds;

    final AtomicLong msgSeqno = new AtomicLong(Math.abs(System.nanoTime()));

    //-------------------------- MessageConsumer、MessageProducer --------------------------
    protected final ReentrantLock messageProducerLock = new ReentrantLock();

    protected MessageProducer messageBaseProducer;

    protected Map<ConvertType, MessageProducerWrapper> messageProducerMap = new ConcurrentHashMap<>();

    protected final CopyOnWriteArrayList<MessageConsumer> messageConsumerList = new CopyOnWriteArrayList<>();

    //key: group, sub-key: topic
    protected final Map<String, Map<String, MessageConsumerWrapper>> messageConsumerMap = new HashMap<>();

    //-------------------------- HttpRpcClient、SncpMessageClient --------------------------
    private HttpRpcMessageClient httpRpcClient;

    private String httpAppRespTopic;

    private String sncpAppRespTopic;

    protected MessageClient httpMessageClient;

    protected MessageClient sncpMessageClient;

    protected MessageClientProducer messageClientProducer;

    protected final ReentrantLock clientConsumerLock = new ReentrantLock();

    protected final ReentrantLock clientProducerLock = new ReentrantLock();

    protected MessageCoder<MessageRecord> messageRecordCoder = MessageRecordSerializer.getInstance();

    protected ScheduledThreadPoolExecutor timeoutExecutor;

    public void init(AnyValue config) {
        this.name = checkName(config.getValue("name", ""));
        this.httpAppRespTopic = generateHttpAppRespTopic();
        this.sncpAppRespTopic = generateSncpAppRespTopic();
        int threads = config.getIntValue("threads", -1);
        if (threads == 0) {
            this.workExecutor = application.getWorkExecutor();
        }
        if (this.workExecutor == null) {
            this.workExecutor = threads > 0 ? WorkThread.createExecutor(threads, "Redkale-MessageConsumerThread-[" + name + "]-%s")
                : WorkThread.createWorkExecutor(Utility.cpus(), "Redkale-MessageConsumerThread-[" + name + "]-%s");
        }
        this.httpMessageClient = new MessageClient(this, this.httpAppRespTopic);
        this.sncpMessageClient = new MessageClient(this, this.sncpAppRespTopic);

        String coderType = config.getValue("coder", "");
        if (!coderType.trim().isEmpty()) {
            try {
                Class<MessageCoder<MessageRecord>> coderClass = (Class) Thread.currentThread().getContextClassLoader().loadClass(coderType);
                RedkaleClassLoader.putReflectionPublicConstructors(coderClass, coderClass.getName());
                MessageCoder<MessageRecord> coder = coderClass.getConstructor().newInstance();
                if (application != null) {
                    application.getResourceFactory().inject(coder);
                }
                if (coder instanceof Service) {
                    ((Service) coder).init(config);
                }
                this.messageRecordCoder = coder;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                throw new RedkaleException(e);
            }
        }
        // application (it doesn't execute completion handlers).
        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r, "Redkale-MessageAgent-[" + name + "]-Timeout-Thread");
            t.setDaemon(true);
            return t;
        });
        this.timeoutSeconds = config.getIntValue("timeoutSeconds", 30);
        this.timeoutExecutor.setRemoveOnCancelPolicy(true);
    }

    public Future submit(Runnable event) {
        return workExecutor.submit(event);
    }

    public void execute(Runnable event) {
        workExecutor.execute(event);
    }

    public void start(List<MessageConsumer> consumers) {
        StringBuilder loginfo = initMessageConsumer(consumers);
        startMessageConsumer();
        if (loginfo.length() > 0) {
            logger.log(Level.INFO, loginfo.toString());
        }
        //----------------- MessageClient -----------------
        if (this.httpRpcClient != null || !this.httpMessageClient.isEmpty()) {
            this.httpMessageClient.putMessageRespProcessor();
        }
        if (!this.sncpMessageClient.isEmpty()) {
            this.sncpMessageClient.putMessageRespProcessor();
        }
        List<String> topics = new ArrayList<>();
        if (!this.httpMessageClient.isEmpty()) {
            topics.addAll(this.httpMessageClient.getTopics());
        }
        if (!this.sncpMessageClient.isEmpty()) {
            topics.addAll(this.sncpMessageClient.getTopics());
        }
        if (!topics.isEmpty()) { //存在需要订阅的主题
            this.messageClientProducer = startMessageClientProducer();
            this.startMessageClientConsumer();
            Collections.sort(topics);
            loginfo = new StringBuilder();
            loginfo.append(MessageClientConsumer.class.getSimpleName() + " subscribe topics:\r\n");
            for (String topic : topics) {
                loginfo.append("  ").append(topic).append("\r\n");
            }
            logger.log(Level.INFO, loginfo.toString());
        }
    }

    //Application.stop  在执行server.shutdown之前执行
    public void stop() {
        this.stopMessageConsumer();
        this.stopMessageProducer();
        this.stopMessageClientConsumer();
    }

    //Application.stop 在所有server.shutdown执行后执行
    public void destroy(AnyValue config) {
        for (MessageConsumer consumer : messageConsumerList) {
            consumer.destroy(config);
        }
        this.messageConsumerList.clear();
        this.messageConsumerMap.clear();
        //-------------- MessageClient --------------
        this.httpMessageClient.stop();
        this.sncpMessageClient.stop();
        if (this.messageClientProducer != null) {
            this.messageClientProducer.stop();
        }
        if (this.messageRecordCoder instanceof Service) {
            ((Service) this.messageRecordCoder).destroy(config);
        }
        if (this.timeoutExecutor != null) {
            this.timeoutExecutor.shutdownNow();
        }
        if (this.workExecutor != null && this.workExecutor != application.getWorkExecutor()) {
            this.workExecutor.shutdown();
        }
    }

    public MessageConext createMessageConext(String topic, Integer partition) {
        return new MessageConext(topic, partition);
    }

    public MessageProducer loadMessageProducer(ResourceProducer ann) {
        MessageProducer baseProducer = this.messageBaseProducer;
        if (this.messageBaseProducer == null) {
            messageProducerLock.lock();
            try {
                if (this.messageBaseProducer == null) {
                    startMessageProducer();
                }
            } finally {
                messageProducerLock.unlock();
            }
            baseProducer = this.messageBaseProducer;
        }
        MessageProducer producer = baseProducer;
        Objects.requireNonNull(producer);
        return messageProducerMap.computeIfAbsent(ann.convertType(), t -> new MessageProducerWrapper(producer, ConvertFactory.findConvert(t)));
    }

    protected StringBuilder initMessageConsumer(List<MessageConsumer> consumers) {
        final StringBuilder sb = new StringBuilder();
        clientConsumerLock.lock();
        try {
            Map<String, Map<String, MessageConsumerWrapper>> maps = new HashMap<>();
            AtomicInteger typeMax = new AtomicInteger();
            AtomicInteger topicMax = new AtomicInteger();
            Map<String, String> views = new LinkedHashMap<>();
            for (MessageConsumer consumer : consumers) {
                ResourceConsumer res = consumer.getClass().getAnnotation(ResourceConsumer.class);
                String group = application.getPropertyValue(res.group());
                if (Utility.isBlank(group)) {
                    group = consumer.getClass().getName();
                }
                Map<String, MessageConsumerWrapper> map = maps.computeIfAbsent(group, g -> new HashMap<>());
                List<String> topics = new ArrayList<>();
                for (String t : res.topics()) {
                    String topic = application.getPropertyValue(t);
                    if (!topic.trim().isEmpty()) {
                        topics.add(topic);
                        if (map.containsKey(topic.trim())) {
                            throw new RedkaleException(MessageConsumer.class.getSimpleName()
                                + " consume topic (" + topic + ") repeat with " + map.get(topic).getClass().getName() + " and " + consumer.getClass().getName());
                        }
                        for (MessageConsumerWrapper wrapper : map.values()) {
                            if (!Objects.equals(res.convertType(), wrapper.convertType)) {
                                throw new RedkaleException(MessageConsumer.class.getSimpleName()
                                    + " " + consumer.getClass().getName() + " convertType(" + res.convertType() + ") differ in "
                                    + wrapper.consumer.getClass().getName() + " convertType(" + wrapper.convertType + ")");
                            }
                        }
                        map.put(topic.trim(), new MessageConsumerWrapper(this, consumer, res.convertType()));
                    }
                }
                String typestr = consumer.getClass().getName();
                String topicstr = JsonConvert.root().convertTo(topics.size() == 1 ? topics.get(0) : topics);
                if (typestr.length() > typeMax.get()) {
                    typeMax.set(typestr.length());
                }
                if (topicstr.length() > topicMax.get()) {
                    topicMax.set(topicstr.length());
                }
                views.put(typestr, topicstr);
            }
            views.forEach((typestr, topicstr) -> {
                sb.append(MessageConsumer.class.getSimpleName())
                    .append(" (type=").append(alignString(typestr, typeMax.get()))
                    .append(", topics=").append(alignString(topicstr, topicMax.get()))
                    .append(") startuped\r\n");
            });
            messageConsumerList.addAll(consumers);
            messageConsumerMap.putAll(maps);
        } finally {
            clientConsumerLock.unlock();
        }
        return sb;
    }

    @Override
    public String resourceName() {
        return name;
    }

    public Logger getLogger() {
        return logger;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getName() {
        return name;
    }

    public AnyValue getConfig() {
        return config;
    }

    public void setConfig(AnyValue config) {
        this.config = config;
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    public HttpRpcClient getHttpRpcClient() {
        if (this.httpRpcClient == null) {
            messageProducerLock.lock();
            try {
                if (this.httpRpcClient == null) {
                    this.httpRpcClient = new HttpRpcMessageClient(this.httpMessageClient, this.nodeid);
                }
            } finally {
                messageProducerLock.unlock();
            }
        }
        return httpRpcClient;
    }

    public MessageClient getHttpMessageClient() {
        return httpMessageClient;
    }

    public MessageClient getSncpMessageClient() {
        return sncpMessageClient;
    }

    protected String checkName(String name) {  //不能含特殊字符
        if (name.isEmpty()) {
            return name;
        }
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            throw new RedkaleException("name only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RedkaleException("name only 0-9 a-z A-Z _ cannot begin 0-9");
            }
        }
        return name;
    }

    public MessageCoder<MessageRecord> getMessageRecordCoder() {
        return this.messageRecordCoder;
    }

    public MessageClientProducer getMessageClientProducer() {
        return this.messageClientProducer;
    }

    //    
    protected abstract void startMessageConsumer();

    protected abstract void stopMessageConsumer();

    protected abstract void startMessageProducer();

    protected abstract void stopMessageProducer();

    //----------------- MessageClient -----------------
    protected abstract void startMessageClientConsumer();

    protected abstract void stopMessageClientConsumer();

    protected abstract MessageClientProducer startMessageClientProducer();

    //---------------------------------------------------
    @ResourceListener
    public abstract void onResourceChange(ResourceEvent[] events);

    //
    public abstract boolean createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract boolean deleteTopic(String... topics);

    //查询所有topic
    public abstract List<String> queryTopic();

    //ServiceLoader时判断配置是否符合当前实现类
    public abstract boolean acceptsConf(AnyValue config);

    public final void putService(NodeHttpServer ns, Service service, HttpServlet servlet) {
        AutoLoad al = service.getClass().getAnnotation(AutoLoad.class);
        if (al != null && !al.value() && service.getClass().getAnnotation(Local.class) != null) {
            return;
        }
        org.redkale.util.AutoLoad al2 = service.getClass().getAnnotation(org.redkale.util.AutoLoad.class);
        if (al2 != null && !al2.value() && service.getClass().getAnnotation(Local.class) != null) {
            return;
        }
        { //标记@RestService(name = " ") 需要跳过， 一般作为模板引擎
            RestService rest = service.getClass().getAnnotation(RestService.class);
            if (rest != null && !rest.name().isEmpty() && rest.name().trim().isEmpty()) {
                return;
            }
        }
        if (WebSocketNode.class.isAssignableFrom(Sncp.getResourceType(service)) && nodeid == 0) {
            throw new RedkaleException("Application.node not config in WebSocket Cluster");
        }
        String topic = Rest.generateHttpReqTopic(service, this.nodeid);
        MessageServlet processor = new HttpMessageServlet(this.httpMessageClient, ns.getHttpServer().getContext(), service, servlet, topic);
        this.httpMessageClient.putMessageServlet(processor);
    }

    public final void putService(NodeSncpServer ns, Service service, SncpServlet servlet) {
        AutoLoad al = service.getClass().getAnnotation(AutoLoad.class);
        if (al != null && !al.value() && service.getClass().getAnnotation(Local.class) != null) {
            return;
        }
        org.redkale.util.AutoLoad al2 = service.getClass().getAnnotation(org.redkale.util.AutoLoad.class);
        if (al2 != null && !al2.value() && service.getClass().getAnnotation(Local.class) != null) {
            return;
        }
        if (WebSocketNode.class.isAssignableFrom(Sncp.getResourceType(service)) && nodeid == 0) {
            throw new RedkaleException("Application.node not config in WebSocket Cluster");
        }
        String topic = Sncp.generateSncpReqTopic(service, this.nodeid);
        MessageServlet processor = new SncpMessageServlet(this.sncpMessageClient, ns.getSncpServer().getContext(), service, servlet, topic);
        this.sncpMessageClient.putMessageServlet(processor);
    }

    //格式: sncp.resp.app.node10
    //格式参考Rest.generateHttpReqTopic
    private String generateSncpAppRespTopic() {
        return Sncp.getSncpRespTopicPrefix() + "app." + (Utility.isEmpty(nodeName) ? "node" : nodeName) + "-" + nodeid;
    }

    //格式: http.resp.app.node10
    //格式参考Rest.generateHttpReqTopic
    private String generateHttpAppRespTopic() {
        return Rest.getHttpRespTopicPrefix() + "app." + (Utility.isEmpty(nodeName) ? "node" : nodeName) + "-" + nodeid;
    }

    static String alignString(String value, int maxlen) {
        StringBuilder sb = new StringBuilder(maxlen);
        sb.append(value);
        for (int i = 0; i < maxlen - value.length(); i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public final String getHttpAppRespTopic() {
        return this.httpAppRespTopic;
    }

    public final String getSncpAppRespTopic() {
        return this.sncpAppRespTopic;
    }

    public final int getNodeid() {
        return this.nodeid;
    }

    public static class MessageConsumerWrapper<T> {

        private final MessageAgent messageAgent;

        private final MessageConsumer consumer;

        private final ConvertType convertType;

        private final Convert convert;

        private final Type messageType;

        public MessageConsumerWrapper(MessageAgent messageAgent, MessageConsumer<T> consumer, ConvertType convertType) {
            Objects.requireNonNull(messageAgent);
            Objects.requireNonNull(consumer);
            Objects.requireNonNull(convertType);
            this.messageAgent = messageAgent;
            this.convertType = convertType;
            this.consumer = consumer;
            this.convert = ConvertFactory.findConvert(convertType);
            this.messageType = parseMessageType(consumer.getClass());
        }

        private static Type parseMessageType(Class<? extends MessageConsumer> clazz) {
            if (Objects.equals(Object.class, clazz)) {
                throw new RedkaleException(clazz.getName() + " not implements " + MessageConsumer.class.getName());
            }
            Type messageType = null;
            Class[] clzs = clazz.getInterfaces();
            for (int i = 0; i < clzs.length; i++) {
                if (MessageConsumer.class.isAssignableFrom(clzs[i])) {
                    messageType = clazz.getGenericInterfaces()[i];
                    break;
                }
            }
            if (messageType == null) {
                return parseMessageType((Class) clazz.getSuperclass());
            }
            return TypeToken.getGenericType(((ParameterizedType) messageType).getActualTypeArguments()[0], clazz);
        }

        public void init(AnyValue config) {
            consumer.init(config);
        }

        public Future onMessage(MessageConext context, List<byte[]> messages) {
            return messageAgent.submit(() -> {
                Convert c = this.convert;
                MessageConsumer m = this.consumer;
                for (byte[] bs : messages) {
                    try {
                        m.onMessage(context, (T) c.convertFrom(messageType, bs));
                    } catch (Throwable t) {
                        messageAgent.getLogger().log(Level.SEVERE, m.getClass().getSimpleName()
                            + " onMessage error, topic: " + context.getTopic()
                            + ", messages: " + new String(bs, StandardCharsets.UTF_8));
                    }
                }
            });
        }

        public void destroy(AnyValue config) {
            consumer.destroy(config);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.consumer);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final MessageConsumerWrapper other = (MessageConsumerWrapper) obj;
            return Objects.equals(this.consumer.getClass(), other.consumer.getClass());
        }

    }

    protected static class MessageProducerWrapper implements MessageProducer {

        private final MessageProducer producer;

        private final Convert convert;

        public MessageProducerWrapper(MessageProducer producer, Convert convert) {
            this.producer = producer;
            this.convert = convert;
        }

        @Override
        public CompletableFuture<Void> sendMessage(String topic, Integer partition, Convert convert0, Type type, Object value) {
            return producer.sendMessage(topic, partition, convert0 == null ? this.convert : convert0, type, value);
        }

    }

}
