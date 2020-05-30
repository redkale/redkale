/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;
import org.redkale.boot.*;
import org.redkale.net.http.Rest;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;

/**
 * MQ管理器
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class MessageAgent {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected String name;

    protected AnyValue config;

    protected MessageProducer producer;

    //本地Service消息接收处理器， key:topic
    protected Map<String, Service> localConsumers;

    public void init(AnyValue config) {

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
    public abstract MessageConsumer createConsumer(String topic, Consumer<MessageRecord> processor);

    //格式: sncp:req:user
    protected static String generateSncpReqTopic(NodeServer ns, Service service) {
        String resname = Sncp.getResourceName(service);
        return "sncp:req:" + Sncp.getResourceType(service).getSimpleName().replaceAll("Service.*$", "").toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: sncp:resp:node10
    protected static String generateSncpRespTopic(Application application) {
        return "sncp:resp:node" + application.getNodeid();
    }

    //格式: http:req:user
    protected static String generateHttpReqTopic(NodeServer ns, Service service) {
        String resname = Sncp.getResourceName(service);
        return "http:req:" + Rest.getWebModuleName(service.getClass()).toLowerCase() + (resname.isEmpty() ? "" : ("-" + resname));
    }

    //格式: http:resp:node10
    protected static String generateHttpRespTopic(Application application) {
        return "http:resp:node" + application.getNodeid();
    }

    //格式: ws:resp:node10
    protected static String generateWebSocketRespTopic(Application application) {
        return "ws:resp:node" + application.getNodeid();
    }
}
