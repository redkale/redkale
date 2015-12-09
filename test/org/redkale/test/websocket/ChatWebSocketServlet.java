/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.websocket;

import org.redkale.net.http.WebServlet;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.net.http.WebSocket;
import java.io.*;
import static java.lang.Thread.sleep;
import java.text.*;
import java.util.concurrent.atomic.*;

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
        debug = "true".equalsIgnoreCase(System.getProperty("debug", "false"));
        Thread t = new Thread() {

            private final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            {
                setName("Debug-ChatWebSocket-ShowCount-Thread");
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        sleep(60 * 1000);
                    } catch (Exception e) {
                        return;
                    }
                    System.out.println(format.format(new java.util.Date()) + ": 消息总数: " + counter.get() + ",间隔消息数: " + icounter.getAndSet(0));
                }
            }
        };
        t.start();
    }

    @Override
    protected WebSocket createWebSocket() {

        return new WebSocket() {

            @Override
            public void onMessage(String text) {
                icounter.incrementAndGet();
                counter.incrementAndGet();
                if (debug) System.out.println("收到消息: " + text);
                super.getWebSocketGroup().getWebSockets().forEach(x -> x.send(text));
            }

            @Override
            protected Serializable createGroupid() {
                return "";
            }
        };
    }

}
