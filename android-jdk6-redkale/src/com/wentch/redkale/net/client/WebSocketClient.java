/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.client;

import com.wentch.redkale.util.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;

/**
 *
 * @author zhangjx
 */
public class WebSocketClient {

    protected final URI uri;

    protected final Map<String, String> headers = new HashMap<String, String>();

    private Proxy proxy;

    private SSLContext sslContext;

    private Socket socket;

    private WebSocketClient(URI uri, Map<String, String> headers0) {
        this.uri = uri;
        if (headers0 != null) this.headers.putAll(headers0);
    }

    public URI getURI() {
        return uri;
    }

    public static WebSocketClient create(URI uri) {
        return create(uri, null);
    }

    public static WebSocketClient create(URI uri, Map<String, String> headers) {
        return new WebSocketClient(uri, headers);
    }

    public WebSocketClient setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public WebSocketClient setProxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public WebSocketClient addHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public int getPort() {
        int port = uri.getPort();
        if (port > 0) return port;
        return "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public void connect() throws IOException {
        if ("wss".equalsIgnoreCase(uri.getScheme())) {
            if (proxy == null) {
                this.socket = (sslContext == null ? Utility.getDefaultSSLContext() : sslContext).getSocketFactory().createSocket(uri.getHost(), getPort());
            } else {
                Socket s = new Socket(proxy);
                s.connect(new InetSocketAddress(uri.getHost(), getPort()));
                this.socket = (sslContext == null ? Utility.getDefaultSSLContext() : sslContext).getSocketFactory().createSocket(s, uri.getHost(), getPort(), true);
            }
        } else {
            this.socket = proxy == null ? new Socket() : new Socket(proxy);
            this.socket.connect(new InetSocketAddress(uri.getHost(), getPort()));
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(new URI("wss://talk.3wyc.com/pipes/ws/listen?test=aa").getQuery());
    }
}
