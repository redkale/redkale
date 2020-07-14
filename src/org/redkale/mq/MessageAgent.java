/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.redkale.boot.*;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.net.Servlet;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
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
public abstract class MessageAgent {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Resource(name = RESNAME_APP_NODEID)
    protected int nodeid;

    protected String name;

    protected AnyValue config;

    protected MessageProducer httpProducer;

    protected MessageProducer sncpProducer;

    protected final Object httpProducerLock = new Object();

    protected final Object sncpProducerLock = new Object();

    protected HttpMessageClient httpMessageClient;

    protected SncpMessageClient sncpMessageClient;

    protected ScheduledThreadPoolExecutor timeoutExecutor;

    //本地Service消息接收处理器， key:consumer
    protected HashMap<String, MessageConsumerNode> messageNodes = new LinkedHashMap<>();

    public void init(AnyValue config) {
        this.name = checkName(config.getValue("name", ""));
        this.httpMessageClient = new HttpMessageClient(this);
        this.sncpMessageClient = new SncpMessageClient(this);
        // application (it doesn't execute completion handlers).
        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setName("MessageAgent-Timeout-Thread");
            t.setDaemon(true);
            return t;
        });
        this.timeoutExecutor.setRemoveOnCancelPolicy(true);
    }

    public CompletableFuture<Map<String, Long>> start() {
        final LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        final List<CompletableFuture> futures = new ArrayList<>();
        this.messageNodes.values().forEach(node -> {
            long s = System.currentTimeMillis();
            futures.add(node.consumer.startup().whenComplete((r, t) -> map.put(node.consumer.consumerid, System.currentTimeMillis() - s)));
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenApply(r -> map);
    }

    //Application.shutdown  在执行server.shutdown之前执行
    public CompletableFuture<Void> stop() {
        List<CompletableFuture> futures = new ArrayList<>();
        this.messageNodes.values().forEach(node -> {
            futures.add(node.consumer.shutdown());
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
    }

    //Application.shutdown 在所有server.shutdown执行后执行
    public void destroy(AnyValue config) {
        this.httpMessageClient.close().join();
        this.sncpMessageClient.close().join();
        if (this.timeoutExecutor != null) this.timeoutExecutor.shutdown();
        if (this.sncpProducer != null) this.sncpProducer.shutdown().join();
        if (this.httpProducer != null) this.httpProducer.shutdown().join();
    }

    protected List<MessageConsumer> getAllMessageConsumer() {
        List<MessageConsumer> consumers = new ArrayList<>();
        MessageConsumer one = this.httpMessageClient == null ? null : this.httpMessageClient.consumer;
        if (one != null) consumers.add(one);
        one = this.sncpMessageClient == null ? null : this.sncpMessageClient.consumer;
        if (one != null) consumers.add(one);
        consumers.addAll(messageNodes.values().stream().map(mcn -> mcn.consumer).collect(Collectors.toList()));
        return consumers;
    }

    protected List<MessageProducer> getAllMessageProducer() {
        List<MessageProducer> producers = new ArrayList<>();
        if (this.httpProducer != null) producers.add(this.httpProducer);
        if (this.sncpProducer != null) producers.add(this.sncpProducer);
        MessageProducer one = this.httpMessageClient == null ? null : this.httpMessageClient.getProducer();
        if (one != null) producers.add(one);
        one = this.sncpMessageClient == null ? null : this.sncpMessageClient.getProducer();
        if (one != null) producers.add(one);
        return producers;
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

    public HttpMessageClient getHttpMessageClient() {
        return httpMessageClient;
    }

    public SncpMessageClient getSncpMessageClient() {
        return sncpMessageClient;
    }

    protected String checkName(String name) {  //不能含特殊字符
        if (name.isEmpty()) return name;
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
            }
        }
        return name;
    }

    //获取指定topic的生产处理器
    public MessageProducer getSncpProducer() {
        if (this.sncpProducer == null) {
            synchronized (sncpProducerLock) {
                if (this.sncpProducer == null) {
                    MessageProducer producer = createProducer("SncpProducer");
                    producer.startup().join();
                    this.sncpProducer = producer;
                }
            }
        }
        return this.sncpProducer;
    }

    public MessageProducer getHttpProducer() {
        if (this.httpProducer == null) {
            synchronized (httpProducerLock) {
                if (this.httpProducer == null) {
                    MessageProducer producer = createProducer("HttpProducer");
                    producer.startup().join();
                    this.httpProducer = producer;
                }
            }
        }
        return this.httpProducer;
    }

    //创建指定topic的生产处理器
    protected abstract MessageProducer createProducer(String name);

    //创建topic，如果已存在则跳过
    public abstract boolean createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract boolean deleteTopic(String... topics);

    //查询所有topic
    public abstract List<String> queryTopic();

    //ServiceLoader时判断配置是否符合当前实现类
    public abstract boolean match(AnyValue config);

    //创建指定topic的消费处理器
    public abstract MessageConsumer createConsumer(String[] topics, String group, MessageProcessor processor);

    public final synchronized void putService(NodeHttpServer ns, Service service, HttpServlet servlet) {
        String[] topics = generateHttpReqTopics(service);
        String consumerid = generateHttpConsumerid(topics, service);
        if (messageNodes.containsKey(consumerid)) throw new RuntimeException("consumerid(" + consumerid + ") is repeat");
        HttpMessageProcessor processor = new HttpMessageProcessor(this.logger, getHttpProducer(), ns, service, servlet);
        this.messageNodes.put(consumerid, new MessageConsumerNode(ns, service, servlet, processor, createConsumer(topics, consumerid, processor)));
    }

    public final synchronized void putService(NodeSncpServer ns, Service service, SncpServlet servlet) {
        String topic = generateSncpReqTopic(service);
        String consumerid = generateSncpConsumerid(topic, service);
        if (messageNodes.containsKey(consumerid)) throw new RuntimeException("consumerid(" + consumerid + ") is repeat");
        SncpMessageProcessor processor = new SncpMessageProcessor(this.logger, getSncpProducer(), ns, service, servlet);
        this.messageNodes.put(consumerid, new MessageConsumerNode(ns, service, servlet, processor, createConsumer(new String[]{topic}, consumerid, processor)));
    }

    //格式: sncp.req.user
    public final String generateSncpReqTopic(Service service) {
        if (service instanceof WebSocketNode) {
            String resname = Sncp.getResourceName(service);
            return "sncp.req.ws" + (resname.isEmpty() ? "" : ("-" + resname)) + ".node" + nodeid;
        }
        String resname = Sncp.getResourceName(service);
        return "sncp.req." + Sncp.getResourceType(service).getSimpleName().replaceAll("Service.*$", "").toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: consumer-sncp.req.user  不提供外部使用
    protected final String generateSncpConsumerid(String topic, Service service) {
        return "consumer-" + topic;
    }

    //格式: http.req.user
    public static String generateHttpReqTopic(String module) {
        return "http.req." + module.toLowerCase();
    }

    //格式: http.req.user
    public static String generateHttpReqTopic(String module, String resname) {
        return "http.req." + module.toLowerCase() + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: sncp.resp.node10
    protected String generateSncpRespTopic() {
        return "sncp.resp.node" + nodeid;
    }

    //格式: http.resp.node10
    protected String generateHttpRespTopic() {
        return "http.resp.node" + nodeid;
    }

    //格式: http.req.user
    protected String[] generateHttpReqTopics(Service service) {
        String resname = Sncp.getResourceName(service);
        String module = Rest.getRestModule(service).toLowerCase();
        MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
        if (mmc != null) return new String[]{generateHttpReqTopic(mmc.module()) + (resname.isEmpty() ? "" : ("-" + resname))};
        return new String[]{"http.req." + module + (resname.isEmpty() ? "" : ("-" + resname))};
    }

    //格式: consumer-http.req.user
    protected String generateHttpConsumerid(String[] topics, Service service) {
        String resname = Sncp.getResourceName(service);
        String key = Rest.getRestModule(service).toLowerCase();
        return "consumer-http.req." + key + (resname.isEmpty() ? "" : ("-" + resname));

    }

    //格式: xxxx.resp.node10
    protected String generateRespTopic(String protocol) {
        return protocol + ".resp.node" + nodeid;
    }

    protected static class MessageConsumerNode {

        public final NodeServer server;

        public final Service service;

        public final Servlet servlet;

        public final MessageProcessor processor;

        public final MessageConsumer consumer;

        public MessageConsumerNode(NodeServer server, Service service, Servlet servlet, MessageProcessor processor, MessageConsumer consumer) {
            this.server = server;
            this.service = service;
            this.servlet = servlet;
            this.processor = processor;
            this.consumer = consumer;
        }

    }
}
