/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.websocket;

import org.redkale.net.http.WebServlet;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.test.rest.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@WebServlet("/ws/chat")
public class ChatWebSocketServlet extends WebSocketServlet {

    @Resource
    private UserService userService;

    @Override
    public void init(HttpContext context, AnyValue conf) {
        System.out.println("本实例的WebSocketNode: " + super.node);
    }

    @Override
    public void destroy(HttpContext context, AnyValue conf) {
        System.out.println("关闭了ChatWebSocketServlet");
    }

    @Override
    protected WebSocket<Integer, ChatMessage> createWebSocket() {

        return new WebSocket<Integer, ChatMessage>() {

            private UserInfo user;

            @Override
            public void onMessage(ChatMessage message, boolean last) { // text 接收的格式:  {"receiveid":200000001, "content":"Hi Redkale!"}
                message.sendid = user.getUserid(); //将当前用户设为消息的发送方
                message.sendtime = System.currentTimeMillis(); //设置消息发送时间
                //给接收方发送消息, 即使接收方在其他WebSocket进程节点上有链接，Redkale也会自动发送到其他链接进程节点上。
                super.sendMessage(message, message.receiveid);
            }

            @Override
            protected CompletableFuture<Integer> createUserid() { //创建用户ID
                this.user = userService.current(String.valueOf(super.getSessionid()));
                return CompletableFuture.completedFuture(this.user == null ? null : this.user.getUserid());
            }

            @Override
            public CompletableFuture<String> onOpen(HttpRequest request) {
                return CompletableFuture.completedFuture(request.getSessionid(false)); //以request中的sessionid字符串作为WebSocket的sessionid
            }

        };
    }

    public static class ChatMessage {

        public int sendid; //发送方用户ID

        public int receiveid; //接收方用户ID        

        public String content; //文本消息内容

        public long sendtime;  //消息发送时间
    }
}
