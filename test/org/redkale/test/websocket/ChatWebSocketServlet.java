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
import java.util.concurrent.atomic.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
@WebServlet("/ws/chat")
public class ChatWebSocketServlet extends WebSocketServlet {

    private final AtomicLong counter = new AtomicLong();

    private final AtomicLong icounter = new AtomicLong();

    private final boolean debug;

    public ChatWebSocketServlet() {
        debug = "true".equalsIgnoreCase(System.getProperty("debug", "true"));
        Thread t = new Thread() {

            {
                setName("Debug-ChatWebSocket-ShowCount-Thread");
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        sleep(300 * 1000);
                    } catch (Exception e) {
                        return;
                    }
                    System.out.println(Utility.now() + ": 消息总数: " + counter.get() + ",间隔消息数: " + icounter.getAndSet(0));
                }
            }
        };
        t.start();
    }

    @Override
    protected WebSocket<String, ChatMessage> createWebSocket() {

        return new WebSocket<String, ChatMessage>() {

            @Override
            public void onMessage(ChatMessage message, boolean last) {
                icounter.incrementAndGet();
                counter.incrementAndGet();
                if (debug) System.out.println("收到消息: " + message);
                super.getWebSockets().forEach(x -> x.send(message));
            }

            @Override
            protected CompletableFuture<String> createUserid() {
                return CompletableFuture.completedFuture("2");
            }

        };
    }

    public static class ChatMessage {

        public String message;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
