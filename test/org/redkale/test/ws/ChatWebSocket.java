/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import org.redkale.net.http.*;

/**
 *
 * @author zhangjx
 */
@RestWebSocket(name = "chat", catalog = "ws", comment = "文字聊天")
public class ChatWebSocket extends WebSocket<Integer, Object> {

    protected static final AtomicInteger idcreator = new AtomicInteger(10000);

    @Resource
    protected ChatService service;

    @Override
    protected CompletableFuture<Integer> createGroupid() {
        return CompletableFuture.completedFuture(service.createGroupid());
    }

    @RestOnMessage(name = "sendmessage")
    public void onChatMessage(ChatMessage message, Map<String, String> extmap) {
        message.fromuserid = getGroupid();
        message.fromusername = "用户" + getGroupid();
        System.out.println("获取消息: message: " + message + ", map: " + extmap);
        super.broadcastEachMessage(message);
    }

    @RestOnMessage(name = "joinroom")
    public void onJoinRoom(int roomid) {
        service.joinRoom(getGroupid(), roomid);
        System.out.println("加入房间: roomid: " + roomid);
    }

}
