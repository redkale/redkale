/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import org.redkale.net.http.*;

/**
 *
 * @author zhangjx
 */
@RestWebSocket(name = "chat", catalog = "ws", comment = "文字聊天", anyuser = true)
public class ChatWebSocket extends WebSocket<Integer, Object> {

    protected static final AtomicInteger idcreator = new AtomicInteger(10000);

    @Resource
    protected ChatService service;

    @Override
    protected CompletableFuture<Integer> createUserid() {
        return CompletableFuture.completedFuture(service.createUserid());
    }

    /**
     * 浏览器WebSocket请求：
     * <pre>
     * websocket.send(JSON.stringify({
     *      sendmessage:{
     *          message:{
     *              content : "这是聊天内容"
     *          },
     *          extmap:{
     *              "a":1,
     *              "b", "haha"
     *          }
     *      }
     * }));
     * </pre>
     *
     * @param message 参数1
     * @param extmap  参数2
     */
    @RestOnMessage(name = "sendmessage")
    public void onChatMessage(ChatMessage message, Map<String, String> extmap) {
        message.fromuserid = getUserid();
        message.fromusername = "用户" + getUserid();
        System.out.println("获取消息: message: " + message + ", map: " + extmap);
        super.broadcastMessage(message);
    }

    /**
     * 浏览器WebSocket请求：
     * <pre>
     * websocket.send(JSON.stringify({
     *      joinroom:{
     *          roomid: 10212
     *      }
     * }));
     * </pre>
     *
     * @param roomid 参数1
     */
    @RestOnMessage(name = "joinroom")
    public void onJoinRoom(int roomid) {
        service.joinRoom(getUserid(), roomid);
        System.out.println("加入房间: roomid: " + roomid);
    }

    public static void main(String[] args) throws Throwable {

        Method method = Arrays.asList(Rest.class.getDeclaredMethods())
            .stream().filter(m -> "createRestWebSocketServlet".equals(m.getName()))
            .findFirst().get();
        method.setAccessible(true);
        System.out.println(method.invoke(null, Thread.currentThread().getContextClassLoader(), ChatWebSocket.class));
    }

}
