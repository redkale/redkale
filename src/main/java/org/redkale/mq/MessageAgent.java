/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
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
import org.redkale.net.Servlet;
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

    protected final ReentrantLock messageProducerLock = new ReentrantLock();

    protected MessageProducer baseMessageProducer;

    protected Map<ConvertType, ConvertMessageProducer> messageProducers = new ConcurrentHashMap<>();

    //key: group, sub-key: topic
    protected final ConcurrentHashMap<String, ConcurrentHashMap<String, MessageConsumer>> consumerMap = new ConcurrentHashMap<>();

    protected final CopyOnWriteArrayList<MessageConsumer> consumerList = new CopyOnWriteArrayList<>();

    protected MessageClientProducer httpProducer;

    protected MessageClientProducer sncpProducer;

    protected HttpMessageClient httpMessageClient;

    protected SncpMessageClient sncpMessageClient;

    protected final ReentrantLock consumerLock = new ReentrantLock();

    protected final ReentrantLock producerLock = new ReentrantLock();

    protected final ReentrantLock nodesLock = new ReentrantLock();

    protected final List<MessageConsumer> consumerListeners = new CopyOnWriteArrayList<>();

    protected final AtomicLong msgSeqno = new AtomicLong(System.nanoTime());

    protected ScheduledThreadPoolExecutor timeoutExecutor;

    protected MessageCoder<MessageRecord> messageCoder = MessageRecordCoder.getInstance();

    //本地Service消息接收处理器， key:consumerid
    protected HashMap<String, MessageClientConsumerNode> clientConsumerNodes = new LinkedHashMap<>();

    public void init(AnyValue config) {
        this.name = checkName(config.getValue("name", ""));
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
                this.messageCoder = coder;
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

    public CompletableFuture<Map<String, Long>> start() {
        final LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        final List<CompletableFuture> futures = new ArrayList<>();
        this.clientConsumerNodes.values().forEach(node -> {
            long s = System.currentTimeMillis();
            futures.add(node.consumer.startup().whenComplete((r, t) -> map.put(node.consumer.consumerid, System.currentTimeMillis() - s)));
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenApply(r -> map);
    }

    //Application.shutdown  在执行server.shutdown之前执行
    public CompletableFuture<Void> stop() {
        List<CompletableFuture> futures = new ArrayList<>();
        this.clientConsumerNodes.values().forEach(node -> {
            futures.add(node.consumer.shutdown());
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
    }

    //Application.shutdown 在所有server.shutdown执行后执行
    public void destroy(AnyValue config) {
        this.httpMessageClient.close().join();
        this.sncpMessageClient.close().join();
        if (this.timeoutExecutor != null) {
            this.timeoutExecutor.shutdown();
        }
        if (this.sncpProducer != null) {
            this.sncpProducer.shutdown().join();
        }
        if (this.httpProducer != null) {
            this.httpProducer.shutdown().join();
        }
        if (this.messageCoder instanceof Service) {
            ((Service) this.messageCoder).destroy(config);
        }
    }

    @Deprecated
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

    @Deprecated
    protected List<MessageClientProducer> getMessageClientProducers() {
        List<MessageClientProducer> producers = new ArrayList<>();
        if (this.httpProducer != null) {
            producers.add(this.httpProducer);
        }
        if (this.sncpProducer != null) {
            producers.add(this.sncpProducer);
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

    @Override
    public String resourceName() {
        return name;
    }

    @Deprecated
    public MessageCoder<MessageRecord> getMessageCoder() {
        return this.messageCoder;
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

    public MessageProducer loadMessageProducer(ResourceProducer ann) {
        MessageProducer baseProducer = this.baseMessageProducer;
        if (this.baseMessageProducer == null) {
            messageProducerLock.lock();
            try {
                if (this.baseMessageProducer == null) {
                    this.baseMessageProducer = createMessageProducer();
                }
            } finally {
                messageProducerLock.unlock();
            }
            baseProducer = this.baseMessageProducer;
        }
        MessageProducer producer = baseProducer;
        Objects.requireNonNull(producer);
        return messageProducers.computeIfAbsent(ann.convertType(), t -> new ConvertMessageProducer(producer, ConvertFactory.findConvert(t)));
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

    @Deprecated
    //获取指定topic的生产处理器
    public MessageClientProducer getSncpMessageClientProducer() {
        if (this.sncpProducer == null) {
            producerLock.lock();
            try {
                if (this.sncpProducer == null) {
                    long s = System.currentTimeMillis();
                    this.sncpProducer = createMessageClientProducer("SncpProducer");
                    long e = System.currentTimeMillis() - s;
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "MessageAgent.SncpProducer startup all in " + e + "ms");
                    }
                }
            } finally {
                producerLock.unlock();
            }
        }
        return this.sncpProducer;
    }

    @Deprecated
    public MessageClientProducer getHttpMessageClientProducer() {
        if (this.httpProducer == null) {
            producerLock.lock();
            try {
                if (this.httpProducer == null) {
                    long s = System.currentTimeMillis();
                    this.httpProducer = createMessageClientProducer("HttpProducer");
                    long e = System.currentTimeMillis() - s;
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "MessageAgent.HttpProducer startup all in " + e + "ms");
                    }
                }
            } finally {
                producerLock.unlock();
            }
        }
        return this.httpProducer;
    }

    @Deprecated
    //创建指定topic的生产处理器
    protected abstract MessageClientProducer createMessageClientProducer(String producerName);

    //
    protected abstract MessageProducer createMessageProducer();

    protected abstract void closeMessageProducer(MessageProducer messageProducer) throws Exception;

    //
    public abstract boolean createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract boolean deleteTopic(String... topics);

    //查询所有topic
    public abstract List<String> queryTopic();

    //ServiceLoader时判断配置是否符合当前实现类
    public abstract boolean acceptsConf(AnyValue config);

    //创建指定topic的消费处理器
    public abstract MessageClientConsumer createMessageClientConsumer(String[] topics, String group, MessageClientProcessor processor);

    @ResourceListener
    public abstract void onResourceChange(ResourceEvent[] events);

    public void addMessageConsumer(ResourceConsumer res, MessageConsumer consumer) {
        consumerLock.lock();
        try {
            ConcurrentHashMap<String, MessageConsumer> map = consumerMap.computeIfAbsent(res.group(), g -> new ConcurrentHashMap<>());
            for (String topic : res.topics()) {
                if (!topic.trim().isEmpty()) {
                    if (map.containsKey(topic.trim())) {
                        throw new RedkaleException(MessageConsumer.class.getSimpleName()
                            + " consume topic (" + topic + ") repeat with "
                            + map.get(topic).getClass().getName() + " and " + consumer.getClass().getName());
                    }
                    map.put(topic.trim(), consumer);
                }
            }
            consumerList.add(consumer);
        } finally {
            consumerLock.unlock();
        }
    }

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
        nodesLock.lock();
        try {
            if (clientConsumerNodes.containsKey(consumerid)) {
                throw new RedkaleException("consumerid(" + consumerid + ") is repeat");
            }
            HttpMessageClientProcessor processor = new HttpMessageClientProcessor(this.logger, httpMessageClient, getHttpMessageClientProducer(), ns, service, servlet);
            this.clientConsumerNodes.put(consumerid, new MessageClientConsumerNode(ns, service, servlet, processor, createMessageClientConsumer(topics, consumerid, processor)));
        } finally {
            nodesLock.unlock();
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
        nodesLock.lock();
        try {
            if (clientConsumerNodes.containsKey(consumerid)) {
                throw new RedkaleException("consumerid(" + consumerid + ") is repeat");
            }
            SncpMessageClientProcessor processor = new SncpMessageClientProcessor(this.logger, sncpMessageClient, getSncpMessageClientProducer(), ns, service, servlet);
            this.clientConsumerNodes.put(consumerid, new MessageClientConsumerNode(ns, service, servlet, processor, createMessageClientConsumer(new String[]{topic}, consumerid, processor)));
        } finally {
            nodesLock.unlock();
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
        MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
        if (mmc != null) {
            return new String[]{generateHttpReqTopic(mmc.module()) + (resname.isEmpty() ? "" : ("-" + resname))};
        }
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
