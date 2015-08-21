/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.websocket;

import com.wentch.redkale.net.http.WebServlet;
import com.wentch.redkale.net.http.WebSocketServlet;
import com.wentch.redkale.net.http.HttpRequest;
import com.wentch.redkale.net.http.WebSocket;
import com.wentch.redkale.net.http.HttpServer;
import com.wentch.redkale.util.TypeToken;
import com.wentch.redkale.util.AnyValue;
import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

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
            public String onOpen(final HttpRequest request) {
                String uri = request.getRequestURI();
                int pos = uri.indexOf("/listen/");
                uri = uri.substring(pos + "/listen/".length());
                this.repeat = sessions.get(uri) != null;
                if (!this.repeat) this.repeat = users.get(uri) == null;
                String sessionid = Long.toString(System.nanoTime());
                if (uri.indexOf('\'') >= 0 || uri.indexOf('"') >= 0) return null;
                if (!repeat) sessionid = uri;
                return sessionid;
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
                    super.getWebSocketGroup().getWebSockets().filter(x -> x != this).forEach(x -> {
                        x.send(msg);
                    });
                }
            }

            @Override
            public void onMessage(String text) {
                //System.out.println("接收到消息: " + text);
                super.getWebSocketGroup().getWebSockets().filter(x -> x != this).forEach(x -> {
                    x.send(text);
                });
            }

            @Override
            public void onClose(int code, String reason) {
                sessions.remove(this.getSessionid());
                String msg = ("{'type':'remove_user','user':{'userid':'" + this.getSessionid() + "','username':'" + users.get(this.getSessionid()) + "'}}").replace('\'', '"');
                super.getWebSocketGroup().getWebSockets().filter(x -> x != this).forEach(x -> {
                    x.send(msg);
                });
            }

            @Override
            protected Serializable createGroupid() {
                return "";
            }
        };
        return socket;
    }

    public static void main(String[] args) throws Throwable {
        CountDownLatch cdl = new CountDownLatch(1);
        AnyValue.DefaultAnyValue config = new AnyValue.DefaultAnyValue();
        config.addValue("threads", System.getProperty("threads"));
        config.addValue("bufferPoolSize", System.getProperty("bufferPoolSize"));
        config.addValue("responsePoolSize", System.getProperty("responsePoolSize"));
        config.addValue("host", System.getProperty("host", "0.0.0.0"));
        config.addValue("port", System.getProperty("port", "8070"));
        config.addValue("root", System.getProperty("root", "./root3/"));
        AnyValue.DefaultAnyValue resConf = new AnyValue.DefaultAnyValue();
        resConf.setValue("cacheMaxLength", "200M");
        resConf.setValue("cacheMaxItemLength", "10M");
        config.setValue("ResourceServlet", resConf);
        HttpServer server = new HttpServer();
        server.addHttpServlet(new VideoWebSocketServlet(), null, "/pipes/listen/*");
        server.init(config);
        server.start();
        cdl.await();
    }

}
