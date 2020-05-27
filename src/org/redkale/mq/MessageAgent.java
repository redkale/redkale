/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

/**
 * MQ管理
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class MessageAgent {

    //创建topic，如果已存在则跳过
    public abstract void createTopic(String... topics);

    //删除topic，如果不存在则跳过
    public abstract void deleteTopic(String... topics);
}
