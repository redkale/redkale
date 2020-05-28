/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.*;
import java.util.logging.Logger;
import org.redkale.boot.MessageAgentRoot;
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

    //MQ管理器名称
    protected String name;

    //application根MQ管理器
    protected MessageAgentRoot root;

    //本地Service消息接收处理器， key:topic
    protected Map<String, Service> localConsumers;

    public void init(AnyValue config) {

    }

    public void destroy(AnyValue config) {

    }

    public String getName() {
        return name;
    }

    public MessageAgentRoot getRoot() {
        return root;
    }

    public void setRoot(MessageAgentRoot root) {
        this.root = root;
    }

    protected String checkName(String name) {  //不能含特殊字符
        if (name.isEmpty()) throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException("name only 0-9 a-z A-Z _ cannot begin 0-9");
            }
        }
        return name;
    }

    //创建topic，如果已存在则跳过
    public abstract boolean createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract boolean deleteTopic(String... topics);

    //查询所有topic
    public abstract List<String> queryTopic();
}
