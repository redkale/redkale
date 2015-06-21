/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.service;

import com.wentch.redkale.service.Service;

/**
 *
 * @author zhangjx
 */
public class IMService implements Service {

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    public void send(String text) {
        onSend(text);
    }

    public void onSend(String text) {
        System.out.println("接收到消息: " + text);
    }
}
