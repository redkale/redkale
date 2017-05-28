/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.websocket;

import org.redkale.net.http.WebServlet;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.net.http.HttpRequest;
import org.redkale.net.http.WebSocket;
import org.redkale.net.http.HttpServer;
import org.redkale.util.TypeToken;
import org.redkale.util.AnyValue;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author zhangjx
 */
@WebServlet({"/ws/listen"})
public class VideoWebSocketServlet extends WebSocketServlet {

    private final Map<Serializable, Entry> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    private final Map<Serializable, String> users = new HashMap<>();

    private static final class Entry {

        public WebSocket socket;

        public String username;

        public Serializable userid;

    }

    public VideoWebSocketServlet() {
        super();
        users.put("zhangjx", "xxxx");
    }

    @Override
    protected WebSocket createWebSocket() {
        WebSocket socket = new WebSocket() {

            private final TypeToken<Map<String, String>> mapToken = new TypeToken<Map<String, String>>() {
            };

            private boolean repeat = false;

            @Override
            public CompletableFuture<String> onOpen(final HttpRequest request) {
                String uri = request.getRequestURI();
                int pos = uri.indexOf("/listen/");
                uri = uri.substring(pos + "/listen/".length());
                this.repeat = sessions.get(uri) != null;
                if (!this.repeat) this.repeat = users.get(uri) == null;
                String sessionid = Long.toString(System.nanoTime());
                if (uri.indexOf('\'') >= 0 || uri.indexOf('"') >= 0) return null;
                if (!repeat) sessionid = uri;
                return CompletableFuture.completedFuture(sessionid);
            }

            @Override
            public void onConnected() {
                if (repeat) {
                    super.close();
                } else {
                    Entry entry = new Entry();
                    entry.userid = this.getSessionid();
                    entry.username = users.get(entry.userid);
                    sessions.put(this.getSessionid(), entry);
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<Serializable, Entry> en : sessions.entrySet()) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append("{'userid':'").append(en.getKey()).append("','username':'").append(en.getValue().username).append("'}");
                    }
                    super.send(("{'type':'user_list','users':[" + sb + "]}").replace('\'', '"'));
                    String msg = ("{'type':'discover_user','user':{'userid':'" + this.getSessionid() + "','username':'" + users.get(this.getSessionid()) + "'}}").replace('\'', '"');
                    super.broadcastMessage(msg);
                }
            }

            @Override
            public void onMessage(Object text, boolean last) {
                //System.out.println("接收到消息: " + text);
                super.broadcastMessage(text, last);
            }

            @Override
            public void onClose(int code, String reason) {
                sessions.remove(this.getSessionid());
                String msg = ("{'type':'remove_user','user':{'userid':'" + this.getSessionid() + "','username':'" + users.get(this.getSessionid()) + "'}}").replace('\'', '"');
                super.broadcastMessage(msg);
            } 

            @Override
            protected CompletableFuture<Serializable> createUserid() {
                return CompletableFuture.completedFuture("2");
            }
        };
        return socket;
    }

    public static void main(String[] args) throws Throwable {
        CountDownLatch cdl = new CountDownLatch(1);
        AnyValue.DefaultAnyValue config = AnyValue.create()
            .addValue("threads", System.getProperty("threads"))
            .addValue("bufferPoolSize", System.getProperty("bufferPoolSize"))
            .addValue("responsePoolSize", System.getProperty("responsePoolSize"))
            .addValue("host", System.getProperty("host", "0.0.0.0"))
            .addValue("port", System.getProperty("port", "8070"))
            .addValue("root", System.getProperty("root", "./root3/"));
        HttpServer server = new HttpServer();
        server.addHttpServlet("/pipes", new VideoWebSocketServlet(), "/listen/*");
        server.init(config);
        server.start();
        cdl.await();
    }

}
