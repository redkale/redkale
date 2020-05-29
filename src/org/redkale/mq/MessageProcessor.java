/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

/**
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface MessageProcessor {

    public void process(MessageRecord req, MessageResponse response);
}
