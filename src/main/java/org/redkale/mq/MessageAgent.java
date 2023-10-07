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
import java.util.function.IntFunction;
import java.util.logging.*;
import java.util.stream.Collectors;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceListener;
import org.redkale.boot.*;
import static org.redkale.boot.Application.RESNAME_APP_NAME;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertFactory;
import org.redkale.convert.ConvertType;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.Servlet;
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

    private ExecutorService workExecutor;

    protected final ReentrantLock messageProducerLock = new ReentrantLock();

    protected MessageProducer messageBaseProducer;

    protected Map<ConvertType, ConvertMessageProducer> messageProducerMap = new ConcurrentHashMap<>();

    protected final CopyOnWriteArrayList<MessageConsumer> messageConsumerList = new CopyOnWriteArrayList<>();

    //key: group, sub-key: topic
    protected final Map<String, Map<String, MessageConsumerWrapper>> messageConsumerMap = new HashMap<>();

    protected MessageClientProducer httpClientProducer;

    protected MessageClientProducer sncpClientProducer;

    protected HttpMessageClient httpMessageClient;

    protected SncpMessageClient sncpMessageClient;

    protected final ReentrantLock clientConsumerLock = new ReentrantLock();

    protected final ReentrantLock clientProducerLock = new ReentrantLock();

    protected final ReentrantLock serviceLock = new ReentrantLock();

    protected MessageCoder<MessageRecord> clientMessageCoder = MessageRecordCoder.getInstance();

    //本地Service消息接收处理器， key:consumerid
    protected HashMap<String, MessageClientConsumerNode> clientConsumerNodes = new LinkedHashMap<>();

    protected final AtomicLong msgSeqno = new AtomicLong(System.nanoTime());

    protected ScheduledThreadPoolExecutor timeoutExecutor;

    public void init(AnyValue config) {
        this.name = checkName(config.getValue("name", ""));
        int threads = config.getIntValue("threads", -1);
        if (threads == 0) {
            this.workExecutor = application.getWorkExecutor();
        }
        if (this.workExecutor == null) {
            this.workExecutor = threads > 0 ? WorkThread.createExecutor(threads, "Redkale-MessageConsumerThread-[" + name + "]-%s")
                : WorkThread.createWorkExecutor(Utility.cpus(), "Redkale-MessageConsumerThread-[" + name + "]-%s");
        }
        this.httpMessageClient = new HttpMessageClient(this);
        this.sncpMessageClient = new SncpMessageClient(this);
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
                this.clientMessageCoder = coder;
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
        this.timeoutExecutor.setRemoveOnCancelPolicy(true);
    }

    public Future submit(Runnable event) {
        return workExecutor.submit(event);
    }

    public Map<String, Long> start(List<MessageConsumer> consumers) {
        StringBuilder loginfo = initMessageConsumer(consumers);
        startMessageConsumer();
        if (loginfo.length() > 0) {
            logger.log(Level.INFO, loginfo.toString());
        }
        final LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        this.clientConsumerNodes.values().forEach(node -> {
            long s = System.currentTimeMillis();
            node.consumer.start();
            long e = System.currentTimeMillis() - s;
            map.put(node.consumer.consumerid, e);
        });
        return map;
    }

    //Application.stop  在执行server.shutdown之前执行
    public void stop() {
        this.stopMessageConsumer();
        this.stopMessageProducer();
        this.clientConsumerNodes.values().forEach(node -> {
            node.consumer.stop();
        });
    }

    //Application.stop 在所有server.shutdown执行后执行
    public void destroy(AnyValue config) {
        for (MessageConsumer consumer : messageConsumerList) {
            consumer.destroy(config);
        }
        this.messageConsumerList.clear();
        this.messageConsumerMap.clear();

        this.httpMessageClient.close();
        this.sncpMessageClient.close();
        if (this.sncpClientProducer != null) {
            this.sncpClientProducer.stop();
        }
        if (this.httpClientProducer != null) {
            this.httpClientProducer.stop();
        }
        if (this.clientMessageCoder instanceof Service) {
            ((Service) this.clientMessageCoder).destroy(config);
        }
        if (this.timeoutExecutor != null) {
            this.timeoutExecutor.shutdownNow();
        }
        if (this.workExecutor != null && this.workExecutor != application.getWorkExecutor()) {
            this.workExecutor.shutdownNow();
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
        return messageProducerMap.computeIfAbsent(ann.convertType(), t -> new ConvertMessageProducer(producer, ConvertFactory.findConvert(t)));
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

    static String alignString(String value, int maxlen) {
        StringBuilder sb = new StringBuilder(maxlen);
        sb.append(value);
        for (int i = 0; i < maxlen - value.length(); i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    @Override
    public String resourceName() {
        return name;
    }

    public Logger getLogger() {
        return logger;
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

    public HttpMessageClient getHttpMessageClient() {
        return httpMessageClient;
    }

    public SncpMessageClient getSncpMessageClient() {
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

    protected List<MessageClientConsumer> getMessageClientConsumers() {
        List<MessageClientConsumer> consumers = new ArrayList<>();
        MessageClientConsumer one = this.httpMessageClient == null ? null : this.httpMessageClient.respConsumer;
        if (one != null) {
            consumers.add(one);
        }
        one = this.sncpMessageClient == null ? null : this.sncpMessageClient.respConsumer;
        if (one != null) {
            consumers.add(one);
        }
        consumers.addAll(clientConsumerNodes.values().stream().map(mcn -> mcn.consumer).collect(Collectors.toList()));
        return consumers;
    }

    protected List<MessageClientProducer> getMessageClientProducers() {
        List<MessageClientProducer> producers = new ArrayList<>();
        if (this.httpClientProducer != null) {
            producers.add(this.httpClientProducer);
        }
        if (this.sncpClientProducer != null) {
            producers.add(this.sncpClientProducer);
        }
        MessageClientProducer one = this.httpMessageClient == null ? null : this.httpMessageClient.getProducer();
        if (one != null) {
            producers.add(one);
        }
        one = this.sncpMessageClient == null ? null : this.sncpMessageClient.getProducer();
        if (one != null) {
            producers.add(one);
        }
        return producers;
    }

    public MessageCoder<MessageRecord> getClientMessageCoder() {
        return this.clientMessageCoder;
    }

    //获取指定topic的生产处理器
    public MessageClientProducer getSncpMessageClientProducer() {
        if (this.sncpClientProducer == null) {
            clientProducerLock.lock();
            try {
                if (this.sncpClientProducer == null) {
                    long s = System.currentTimeMillis();
                    this.sncpClientProducer = createMessageClientProducer("SncpProducer");
                    long e = System.currentTimeMillis() - s;
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "MessageAgent.SncpProducer startup all in " + e + "ms");
                    }
                }
            } finally {
                clientProducerLock.unlock();
            }
        }
        return this.sncpClientProducer;
    }

    public MessageClientProducer getHttpMessageClientProducer() {
        if (this.httpClientProducer == null) {
            clientProducerLock.lock();
            try {
                if (this.httpClientProducer == null) {
                    long s = System.currentTimeMillis();
                    this.httpClientProducer = createMessageClientProducer("HttpProducer");
                    long e = System.currentTimeMillis() - s;
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "MessageAgent.HttpProducer startup all in " + e + "ms");
                    }
                }
            } finally {
                clientProducerLock.unlock();
            }
        }
        return this.httpClientProducer;
    }

    //
    protected abstract void startMessageConsumer();

    protected abstract void stopMessageConsumer();

    protected abstract void startMessageProducer();

    protected abstract void stopMessageProducer();

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

    //创建指定topic的生产处理器
    protected abstract MessageClientProducer createMessageClientProducer(String producerName);

    //创建指定topic的消费处理器
    public abstract MessageClientConsumer createMessageClientConsumer(String[] topics, String group, MessageClientProcessor processor);

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
        String[] topics = generateHttpReqTopics(service);
        String consumerid = generateHttpConsumerid(topics, service);
        serviceLock.lock();
        try {
            if (clientConsumerNodes.containsKey(consumerid)) {
                throw new RedkaleException("consumerid(" + consumerid + ") is repeat");
            }
            HttpMessageClientProcessor processor = new HttpMessageClientProcessor(this.logger, httpMessageClient, getHttpMessageClientProducer(), ns, service, servlet);
            this.clientConsumerNodes.put(consumerid, new MessageClientConsumerNode(ns, service, servlet, processor, createMessageClientConsumer(topics, consumerid, processor)));
        } finally {
            serviceLock.unlock();
        }
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
        String topic = generateSncpReqTopic(service);
        String consumerid = generateSncpConsumerid(topic, service);
        serviceLock.lock();
        try {
            if (clientConsumerNodes.containsKey(consumerid)) {
                throw new RedkaleException("consumerid(" + consumerid + ") is repeat");
            }
            SncpMessageClientProcessor processor = new SncpMessageClientProcessor(this.logger, sncpMessageClient, getSncpMessageClientProducer(), ns, service, servlet);
            this.clientConsumerNodes.put(consumerid, new MessageClientConsumerNode(ns, service, servlet, processor, createMessageClientConsumer(new String[]{topic}, consumerid, processor)));
        } finally {
            serviceLock.unlock();
        }
    }

    //格式: sncp.req.module.user
    public final String generateSncpReqTopic(Service service) {
        return generateSncpReqTopic(Sncp.getResourceName(service), Sncp.getResourceType(service));
    }

    //格式: sncp.req.module.user
    public final String generateSncpReqTopic(String resourceName, Class resourceType) {
        if (WebSocketNode.class.isAssignableFrom(resourceType)) {
            return "sncp.req.module.ws" + (resourceName.isEmpty() ? "" : ("-" + resourceName)) + ".node" + nodeid;
        }
        return "sncp.req.module." + resourceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase() + (resourceName.isEmpty() ? "" : ("-" + resourceName));
    }

    //格式: http.req.module.user
    public static String generateHttpReqTopic(String module) {
        return "http.req.module." + module.toLowerCase();
    }

    //格式: http.req.module.user
    public static String generateHttpReqTopic(String module, String resname) {
        return "http.req.module." + module.toLowerCase() + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: sncp.resp.app.node10
    protected String generateAppSncpRespTopic() {
        return "sncp.resp.app." + (Utility.isEmpty(nodeName) ? "node" : nodeName) + "-" + nodeid;
    }

    //格式: http.resp.app.node10
    protected String generateAppHttpRespTopic() {
        return "http.resp.app." + (Utility.isEmpty(nodeName) ? "node" : nodeName) + "-" + nodeid;
    }

    //格式: http.req.module.user
    protected String[] generateHttpReqTopics(Service service) {
        String resname = Sncp.getResourceName(service);
        String module = Rest.getRestModule(service).toLowerCase();
        return new String[]{"http.req.module." + module + (resname.isEmpty() ? "" : ("-" + resname))};
    }

    //格式: consumer-sncp.req.module.user  不提供外部使用
    protected final String generateSncpConsumerid(String topic, Service service) {
        return "consumer-" + topic;
    }

    //格式: consumer-http.req.module.user
    protected String generateHttpConsumerid(String[] topics, Service service) {
        String resname = Sncp.getResourceName(service);
        String key = Rest.getRestModule(service).toLowerCase();
        return "consumer-http.req.module." + key + (resname.isEmpty() ? "" : ("-" + resname));

    }

    public static class MessageConsumerWrapper<T> {

        private final MessageAgent messageAgent;

        private final MessageConsumer consumer;

        private final ConvertType convertType;

        private final Convert convert;

        private final Type messageType;

        private final IntFunction<T[]> arrayCreator;

        public MessageConsumerWrapper(MessageAgent messageAgent, MessageConsumer<T> consumer, ConvertType convertType) {
            Objects.requireNonNull(messageAgent);
            Objects.requireNonNull(consumer);
            Objects.requireNonNull(convertType);
            this.messageAgent = messageAgent;
            this.convertType = convertType;
            this.consumer = consumer;
            this.convert = ConvertFactory.findConvert(convertType);
            this.messageType = parseMessageType(consumer.getClass());
            this.arrayCreator = Creator.funcArray(TypeToken.typeToClass(messageType));
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
                try {
                    T[] msgs = this.arrayCreator.apply(messages.size());
                    int index = -1;
                    for (byte[] bs : messages) {
                        msgs[++index] = (T) convert.convertFrom(messageType, bs);
                    }
                    for (T msg : msgs) {
                        consumer.onMessage(context, msg);
                    }
                } catch (Throwable t) {
                    messageAgent.getLogger().log(Level.SEVERE, MessageConsumer.class.getSimpleName() + " execute error, topic: " + context.getTopic()
                        + ", messages: " + messages.stream().map(v -> new String(v, StandardCharsets.UTF_8)).collect(Collectors.toList()));
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

    protected static class ConvertMessageProducer implements MessageProducer {

        private final MessageProducer producer;

        private final Convert convert;

        public ConvertMessageProducer(MessageProducer producer, Convert convert) {
            this.producer = producer;
            this.convert = convert;
        }

        @Override
        public CompletableFuture<Void> sendMessage(String topic, Integer partition, Convert convert0, Type type, Object value) {
            return producer.sendMessage(topic, partition, convert0 == null ? this.convert : convert0, type, value);
        }

    }

    protected static class MessageClientConsumerNode {

        public final NodeServer server;

        public final Service service;

        public final Servlet servlet;

        public final MessageClientProcessor processor;

        public final MessageClientConsumer consumer;

        public MessageClientConsumerNode(NodeServer server, Service service, Servlet servlet, MessageClientProcessor processor, MessageClientConsumer consumer) {
            this.server = server;
            this.service = service;
            this.servlet = servlet;
            this.processor = processor;
            this.consumer = consumer;
        }

    }

}
