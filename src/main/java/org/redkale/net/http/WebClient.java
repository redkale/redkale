/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.net.client.Client;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.client.ClientConnection;
import org.redkale.util.*;

/**
 * 简单的HttpClient实现, 存在以下情况不能使用此类: <br>
 * 1、使用HTTPS；<br>
 * 2、上传下载文件；<br>
 * 3、返回超大响应包；<br>
 * 类似JDK11的 java.net.http.HttpClient <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.net.http.WebCodec
 * @see org.redkale.net.http.WebConnection
 * @see org.redkale.net.http.WebRequest
 * @see org.redkale.net.http.WebResult
 *
 * @author zhangjx
 * @since 2.3.0
 */
public class WebClient extends Client<WebConnection, WebRequest, WebResult> {

    public static final String USER_AGENT = "Redkale-http-client/" + Redkale.getDotedVersion();

    static final byte[] header_bytes_useragent =
            ("User-Agent: " + USER_AGENT + "\r\n").getBytes(StandardCharsets.UTF_8);

    static final byte[] header_bytes_connclose = ("Connection: close\r\n").getBytes(StandardCharsets.UTF_8);

    static final byte[] header_bytes_connalive = ("Connection: keep-alive\r\n").getBytes(StandardCharsets.UTF_8);

    protected final AsyncGroup asyncGroup;

    protected ExecutorService workExecutor;

    protected WebClient(ExecutorService workExecutor, AsyncGroup asyncGroup) {
        super("Redkale-http-client", asyncGroup, new ClientAddress(new InetSocketAddress("127.0.0.1", 0)));
        this.workExecutor = workExecutor;
        this.asyncGroup = asyncGroup;
        this.connectTimeoutSeconds = 6;
        this.readTimeoutSeconds = 6;
        this.writeTimeoutSeconds = 6;
    }

    public static WebClient create(ExecutorService workExecutor, AsyncGroup asyncGroup) {
        return new WebClient(workExecutor, asyncGroup);
    }

    public static WebClient create(AsyncGroup asyncGroup) {
        return create(null, asyncGroup);
    }

    @Override
    protected WebConnection createClientConnection(AsyncConnection channel) {
        return new WebConnection(this, channel);
    }

    @Override
    protected CompletableFuture<WebResult> writeChannel(ClientConnection conn, WebRequest request) {
        return super.writeChannel(conn, request);
    }

    public WebClient readTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
        return this;
    }

    public WebClient writeTimeoutSeconds(int writeTimeoutSeconds) {
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        return this;
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url) {
        return sendAsync("GET", url, null, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, String body) {
        return sendAsync("GET", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, Type valueType) {
        return sendAsync("GET", url, null, (byte[]) null, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, String body, Type valueType) {
        return sendAsync(
                "GET",
                url,
                null,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                (Convert) null,
                valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, Convert convert, Type valueType) {
        return sendAsync("GET", url, null, (byte[]) null, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, String body, Convert convert, Type valueType) {
        return sendAsync(
                "GET", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8), convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, byte[] body) {
        return sendAsync("GET", url, null, body);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, byte[] body, Type valueType) {
        return sendAsync("GET", url, null, body, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, byte[] body, Convert convert, Type valueType) {
        return sendAsync("GET", url, null, body, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, HttpHeaders headers) {
        return sendAsync("GET", url, headers, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, HttpHeaders headers, Type valueType) {
        return sendAsync("GET", url, headers, null, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(
            String url, HttpHeaders headers, Convert convert, Type valueType) {
        return sendAsync("GET", url, headers, null, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, HttpHeaders headers, String body) {
        return sendAsync("GET", url, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, HttpHeaders headers, byte[] body) {
        return sendAsync("GET", url, headers, body);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url) {
        return sendAsync("POST", url, null, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, String body) {
        return sendAsync("POST", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, Type valueType) {
        return sendAsync("POST", url, null, (byte[]) null, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, String body, Type valueType) {
        return sendAsync(
                "POST",
                url,
                null,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                (Convert) null,
                valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, Convert convert, Type valueType) {
        return sendAsync("POST", url, null, (byte[]) null, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, String body, Convert convert, Type valueType) {
        return sendAsync(
                "POST", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8), convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, byte[] body) {
        return sendAsync("POST", url, null, body);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, byte[] body, Type valueType) {
        return sendAsync("POST", url, null, body, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, byte[] body, Convert convert, Type valueType) {
        return sendAsync("POST", url, null, body, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, HttpHeaders headers) {
        return sendAsync("POST", url, headers, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, HttpHeaders headers, Type valueType) {
        return sendAsync("POST", url, headers, null, (Convert) null, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(
            String url, HttpHeaders headers, Convert convert, Type valueType) {
        return sendAsync("POST", url, headers, null, convert, valueType);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, HttpHeaders headers, String body) {
        return sendAsync("POST", url, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, HttpHeaders headers, byte[] body) {
        return sendAsync("POST", url, headers, body);
    }

    public CompletableFuture<HttpResult<byte[]>> sendAsync(String url, WebRequest req) {
        return sendAsync(req.getMethod(), url, req.getHeaders(), req.getBody(), (Convert) null, null);
    }

    public <T> CompletableFuture<HttpResult<T>> sendAsync(String url, WebRequest req, Type valueType) {
        return sendAsync(req.getMethod(), url, req.getHeaders(), req.getBody(), (Convert) null, null);
    }

    public CompletableFuture<HttpResult<byte[]>> sendAsync(
            String method, String url, HttpHeaders headers, byte[] body) {
        return sendAsync(method, url, headers, body, (Convert) null, null);
    }

    public <T> CompletableFuture<HttpResult<T>> sendAsync(
            String method, String url, HttpHeaders headers, byte[] body, Type valueType) {
        return sendAsync(method, url, headers, body, (Convert) null, valueType);
    }

    public <T> CompletableFuture<HttpResult<T>> sendAsync(
            String method, String url, HttpHeaders headers, byte[] body, Convert convert, Type valueType) {
        // final String traceid = Traces.computeIfAbsent(Traces.currentTraceid());
        // final WorkThread workThread = WorkThread.currentWorkThread();
        if (method.indexOf(' ') >= 0 || method.indexOf('\r') >= 0 || method.indexOf('\n') >= 0) {
            throw new RedkaleException("http-method(" + method + ") is illegal");
        }
        if (url.indexOf(' ') >= 0 || url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0) {
            throw new RedkaleException("http-url(" + url + ") is illegal");
        }
        final URI uri = URI.create(url);
        final String host = uri.getHost();
        final int port = uri.getPort() > 0 ? uri.getPort() : (url.startsWith("https:") ? 443 : 80);
        int urlpos = url.indexOf("/", url.indexOf("//") + 3);
        final String path = (urlpos > 0 ? url.substring(urlpos) : "/");
        if (!url.startsWith("https:")) {
            WebRequest req = WebRequest.createPath(path, headers).method(method).body(body);
            return (CompletableFuture)
                    sendAsync(new InetSocketAddress(host, port), req).thenApply((WebResult rs) -> {
                        if (valueType == null) {
                            return rs;
                        } else {
                            Convert c = convert == null ? JsonConvert.root() : convert;
                            return rs.result(c.convertToBytes(valueType, (byte[]) rs.result));
                        }
                    });
        }
        return CompletableFuture.failedFuture(new RedkaleException("Not supported https"));
    }
}
