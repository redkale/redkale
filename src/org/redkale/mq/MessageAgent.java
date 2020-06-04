/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import javax.annotation.Resource;
import org.redkale.boot.*;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.convert.ConvertType;
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

    protected MessageProducer producer;

    protected String sncpRespTopic;

    protected MessageConsumer sncpRespConsumer;

    protected SncpRespProcessor sncpRespProcessor;

    //sncpRespConsumer启动耗时， 小于0表示未启动
    protected long sncpRespStartms = -1;

    //本地Service消息接收处理器， key:topic
    protected HashMap<String, MessageNode> messageNodes = new LinkedHashMap<>();

    public void init(AnyValue config) {
    }

    public final CompletableFuture<MessageRecord> createSncpRespFuture(AtomicLong counter, MessageRecord message) {
        return this.sncpRespProcessor.createFuture(message.getSeqid(), counter);
    }

    public final synchronized void startSncpRespConsumer() {
        if (this.sncpRespStartms >= 0) return;
        long s = System.currentTimeMillis();
        if (this.sncpRespConsumer != null) {
            this.sncpRespConsumer.startup().join();
        }
        this.sncpRespStartms = System.currentTimeMillis() - s;
    }

    public CompletableFuture<Map<String, Long>> start() {
        final LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        if (this.sncpRespStartms >= 0) map.put(this.sncpRespConsumer.topic, this.sncpRespStartms);
        final List<CompletableFuture> futures = new ArrayList<>();
        this.messageNodes.values().forEach(node -> {
            long s = System.currentTimeMillis();
            futures.add(node.consumer.startup().whenComplete((r, t) -> map.put(node.consumer.topic, System.currentTimeMillis() - s)));
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
        if (this.sncpRespConsumer != null) this.sncpRespConsumer.shutdown().join();
        if (this.producer != null) this.producer.shutdown().join();
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
    public synchronized MessageProducer getProducer() {
        if (this.producer == null) {
            this.producer = createProducer();
            this.producer.startup().join();
        }
        return this.producer;
    }

    //创建指定topic的生产处理器
    protected abstract MessageProducer createProducer();

    //创建topic，如果已存在则跳过
    public abstract boolean createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract boolean deleteTopic(String... topics);

    //查询所有topic
    public abstract List<String> queryTopic();

    //创建指定topic的消费处理器
    public abstract MessageConsumer createConsumer(String topic, MessageProcessor processor);

    public final synchronized void putSncpResp(NodeSncpServer ns) {
        if (this.sncpRespConsumer != null) return;
        this.sncpRespProcessor = new SncpRespProcessor(this.logger, this);
        this.sncpRespConsumer = createConsumer(generateSncpRespTopic(), sncpRespProcessor);
    }

    public CompletableFuture<MessageRecord> sendRemoteSncp(AtomicLong counter, MessageRecord message) {
        if (this.sncpRespConsumer == null) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(new RuntimeException("Not open sncp consumer"));
            return future;
        }
        message.setFormat(ConvertType.BSON);
        message.setResptopic(generateSncpRespTopic());
        getProducer().apply(message);
        return this.sncpRespProcessor.createFuture(message.getSeqid(), counter);
    }

    public final synchronized void putService(NodeHttpServer ns, Service service, HttpServlet servlet) {
        String topic = generateHttpReqTopic(service);
        if (messageNodes.containsKey(topic)) throw new RuntimeException("topic(" + topic + ") is repeat");
        HttpMessageProcessor processor = new HttpMessageProcessor(this.logger, getProducer(), ns, service, servlet);
        this.messageNodes.put(topic, new MessageNode(ns, service, servlet, processor, createConsumer(topic, processor)));
    }

    public final synchronized void putService(NodeSncpServer ns, Service service, SncpServlet servlet) {
        String topic = generateSncpReqTopic(service);
        if (messageNodes.containsKey(topic)) throw new RuntimeException("topic(" + topic + ") is repeat");
        SncpMessageProcessor processor = new SncpMessageProcessor(this.logger, getProducer(), ns, service, servlet);
        this.messageNodes.put(topic, new MessageNode(ns, service, servlet, processor, createConsumer(topic, processor)));
    }

    //格式: sncp.req.user
    public String generateSncpReqTopic(Service service) {
        String resname = Sncp.getResourceName(service);
        if (service instanceof WebSocketNode) return "sncp.req.ws" + (resname.isEmpty() ? "" : ("-" + resname));
        return "sncp.req." + Sncp.getResourceType(service).getSimpleName().replaceAll("Service.*$", "").toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: sncp.resp.node10
    private String generateSncpRespTopic() {
        if (this.sncpRespTopic != null) return this.sncpRespTopic;
        this.sncpRespTopic = "sncp.resp.node" + nodeid;
        return this.sncpRespTopic;
    }

    //格式: http.req.user
    public String generateHttpReqTopic(String module) {
        return "http.req." + module.toLowerCase();
    }

    //格式: http.resp.node10
    public String generateHttpRespTopic() {
        return "http.resp.node" + nodeid;
    }

    //格式: http.req.user
    protected String generateHttpReqTopic(Service service) {
        String resname = Sncp.getResourceName(service);
        return "http.req." + Rest.getRestName(service).toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: ws.resp.wsgame.node100
    public String generateWebSocketRespTopic(WebSocketNode node) {
        return "ws.resp." + node.getName() + ".node" + nodeid;
    }

    //格式: xxxx.resp.node10
    protected String generateRespTopic(String protocol) {
        return protocol + ".resp.node" + nodeid;
    }

    protected static class MessageNode {

        public final NodeServer server;

        public final Service service;

        public final Servlet servlet;

        public final MessageProcessor processor;

        public final MessageConsumer consumer;

        public MessageNode(NodeServer server, Service service, Servlet servlet, MessageProcessor processor, MessageConsumer consumer) {
            this.server = server;
            this.service = service;
            this.servlet = servlet;
            this.processor = processor;
            this.consumer = consumer;
        }

    }
}
