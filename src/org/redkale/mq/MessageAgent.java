/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.annotation.Resource;
import org.redkale.boot.*;
import static org.redkale.boot.Application.RESNAME_APP_NODEID;
import org.redkale.net.http.*;
import org.redkale.net.sncp.Sncp;
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

    protected MessageConsumer sncpRespConsumer;

    //本地Service消息接收处理器， key:topic
    protected ConcurrentHashMap<String, HttpMessageNode> httpNodes = new ConcurrentHashMap<>();

    public void init(AnyValue config) {
    }

    public CompletableFuture<Void> start() {
        this.httpNodes.values().forEach(node -> {
            node.consumer.start();
            node.consumer.waitFor();
        });
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> stop() {
        return null;
    }

    public void destroy(AnyValue config) {
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
            this.producer.start();
            this.producer.waitFor();
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

    public final synchronized void putHttpService(NodeHttpServer ns, Service service, HttpServlet servlet) {
        String topic = generateHttpReqTopic(service);
        if (httpNodes.containsKey(topic)) throw new RuntimeException("topic(" + topic + ") is repeat");
        HttpMessageProcessor processor = new HttpMessageProcessor(this.logger, this.producer, ns, service, servlet);
        this.httpNodes.put(topic, new HttpMessageNode(ns, service, servlet, processor, createConsumer(topic, processor)));
    }

    //格式: sncp.req.user
    protected String generateSncpReqTopic(Service service) {
        String resname = Sncp.getResourceName(service);
        return "sncp.req." + Sncp.getResourceType(service).getSimpleName().replaceAll("Service.*$", "").toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: sncp.resp.node10
    protected String generateSncpRespTopic() {
        return "sncp.resp.node" + nodeid;
    }

    //格式: http.req.user
    public String generateHttpReqTopic(String module) {
        return "http.req." + module.toLowerCase();
    }

    //格式: http.req.user
    protected String generateHttpReqTopic(Service service) {
        String resname = Sncp.getResourceName(service);
        return "http.req." + Rest.getRestName(service).toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: http.resp.node10
    protected String generateHttpRespTopic() {
        return "http.resp.node" + nodeid;
    }

    //格式: ws.resp.wsgame
    public String generateWebSocketRespTopic(WebSocketNode node) {
        return "ws.resp." + node.getName();
    }

    //格式: xxxx.resp.node10
    protected String generateRespTopic(String protocol) {
        return protocol + ".resp.node" + nodeid;
    }

    protected static class HttpMessageNode {

        public final NodeHttpServer server;

        public final Service service;

        public final HttpServlet servlet;

        public final HttpMessageProcessor processor;

        public final MessageConsumer consumer;

        public HttpMessageNode(NodeHttpServer server, Service service, HttpServlet servlet, HttpMessageProcessor processor, MessageConsumer consumer) {
            this.server = server;
            this.service = service;
            this.servlet = servlet;
            this.processor = processor;
            this.consumer = consumer;
        }

    }
}
