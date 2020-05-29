/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

/**
 *
 * @author zhangjx
 */
public interface MessageResponse {

    public void finish(MessageRecord result);
}
