/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.redkale.util.*;

/**
 * 待开发……
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class WebSocketClient {

    protected final boolean ssl;

    protected final URI uri;

    protected final Map<String, String> headers = new HashMap<String, String>();

    private Proxy proxy;

    private SSLContext sslContext;

    private Socket socket;

    private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

    private WebSocketClient(URI uri, Map<String, String> headers0) {
        this.uri = uri;
        this.ssl = "wss".equalsIgnoreCase(uri.getScheme());
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
        return ssl ? 443 : 80;
    }

    public void connect() throws IOException {
        if (ssl) {
            if (proxy == null) {
                this.socket = (sslContext == null ? Utility.getDefaultSSLContext() : sslContext).getSocketFactory().createSocket(uri.getHost(), getPort());
            } else {
                Socket s = new Socket(proxy);
                this.socket.setSoTimeout(3000);
                s.connect(new InetSocketAddress(uri.getHost(), getPort()));
                this.socket = (sslContext == null ? Utility.getDefaultSSLContext() : sslContext).getSocketFactory().createSocket(s, uri.getHost(), getPort(), true);
            }
        } else {
            this.socket = proxy == null ? new Socket() : new Socket(proxy);
            this.socket.setSoTimeout(3000);
            this.socket.connect(new InetSocketAddress(uri.getHost(), getPort()));
        }
    }

    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://10.28.2.207:5050/pipes/ws/listen?test=aa");
        WebSocketClient client = WebSocketClient.create(uri);
        client.connect();
        System.out.println();
    }

    public void onConnected() {
    }

    public void onMessage(String text) {
    }

    public void onPing(byte[] bytes) {
    }

    public void onPong(byte[] bytes) {
    }

    public void onMessage(byte[] bytes) {
    }

    public void onFragment(String text, boolean last) {
    }

    public void onFragment(byte[] bytes, boolean last) {
    }

    public void onClose(int code, String reason) {
    }

    /**
     * 获取当前WebSocket下的属性
     * <p>
     * @param <T>
     * @param name
     * @return
     */
    @SuppressWarnings("unchecked")
    public final <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    /**
     * 移出当前WebSocket下的属性
     * <p>
     * @param <T>
     * @param name
     * @return
     */
    public final <T> T removeAttribute(String name) {
        return (T) attributes.remove(name);
    }

    /**
     * 给当前WebSocket下的增加属性
     * <p>
     * @param name
     * @param value
     */
    public final void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
}
