/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static org.redkale.net.http.HttpRequest.parseHeaderName;

import java.lang.reflect.Type;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
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
        final String traceid = Traces.computeIfAbsent(Traces.currentTraceid());
        final WorkThread workThread = WorkThread.currentWorkThread();
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

        // 以下代码暂废弃
        final ByteArray array = new ByteArray();
        array.put((method.toUpperCase() + " " + path + " HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8));
        array.put(("Host: " + uri.getHost() + "\r\n").getBytes(StandardCharsets.UTF_8));

        array.put(WebRequest.contentLengthBytes(body));
        if (headers == null || !headers.contains("User-Agent")) {
            array.put(header_bytes_useragent);
        }
        array.put(header_bytes_connclose);
        if (headers == null || !headers.contains(Rest.REST_HEADER_TRACEID)) {
            array.put((Rest.REST_HEADER_TRACEID + ": " + traceid + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (headers != null) {
            headers.forEach(
                    k -> !k.equalsIgnoreCase("Connection") && !k.equalsIgnoreCase("Content-Length"),
                    (k, v) -> array.put((k + ": " + v + "\r\n").getBytes(StandardCharsets.UTF_8)));
        }
        array.put((byte) '\r', (byte) '\n');
        if (body != null) {
            array.put(body);
        }

        return createConnection(host, port).thenCompose(conn -> {
            Traces.currentTraceid(traceid);
            final CompletableFuture<HttpResult<T>> future = new CompletableFuture();
            conn.write(array, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    conn.readInIOThread(new ClientReadCompletionHandler(
                            conn, workThread, traceid, array.clear(), convert, valueType, future));
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    Traces.currentTraceid(traceid);
                    conn.dispose();
                    if (workThread == null) {
                        Utility.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.completeExceptionally(exc);
                        });
                    } else {
                        workThread.runWork(() -> {
                            Traces.currentTraceid(traceid);
                            future.completeExceptionally(exc);
                        });
                    }
                }
            });
            return future;
        });
    }

    protected CompletableFuture<HttpConnection> createConnection(String host, int port) {
        return asyncGroup
                .createTCPClient(
                        new InetSocketAddress(host, port),
                        connectTimeoutSeconds,
                        readTimeoutSeconds,
                        writeTimeoutSeconds)
                .thenApply(conn -> new HttpConnection(conn));
    }

    //
    //    public static void main(String[] args) throws Throwable {
    //        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
    //        asyncGroup.start();
    //        String url = "http://redkale.org";
    //        WebClient client = WebClient.createPostPath(asyncGroup);
    //        (System.out).println(client.getAsync(url).join());
    //    }
    //
    protected static class HttpConnection {

        protected final AsyncConnection channel;

        public HttpConnection(AsyncConnection channel) {
            this.channel = channel;
        }

        public void dispose() {
            this.channel.dispose();
        }

        public void setReadBuffer(ByteBuffer buffer) {
            this.channel.setReadBuffer(buffer);
        }

        public void offerReadBuffer(ByteBuffer buffer) {
            this.channel.offerReadBuffer(buffer);
        }

        public void read(CompletionHandler<Integer, ByteBuffer> handler) {
            this.channel.read(handler);
        }

        public void readInIOThread(CompletionHandler<Integer, ByteBuffer> handler) {
            this.channel.readInIOThread(handler);
        }

        public void write(ByteTuple array, CompletionHandler<Integer, Void> handler) {
            this.channel.write(array, handler);
        }
    }

    protected class ClientReadCompletionHandler<T> implements CompletionHandler<Integer, ByteBuffer> {

        protected static final int READ_STATE_ROUTE = 1;

        protected static final int READ_STATE_HEADER = 2;

        protected static final int READ_STATE_BODY = 3;

        protected static final int READ_STATE_END = 4;

        protected final HttpConnection conn;

        protected final ByteArray array;

        protected final String traceid;

        protected final WorkThread workThread;

        protected final CompletableFuture<HttpResult<T>> future;

        protected Convert convert;

        protected Type valueType;

        protected HttpResult<byte[]> responseResult;

        protected int readState = READ_STATE_ROUTE;

        protected int contentLength = -1;

        public ClientReadCompletionHandler(
                HttpConnection conn,
                WorkThread workThread,
                String traceid,
                ByteArray array,
                Convert convert,
                Type valueType,
                CompletableFuture<HttpResult<T>> future) {
            this.conn = conn;
            this.workThread = workThread;
            this.traceid = traceid;
            this.array = array;
            this.convert = convert;
            this.valueType = valueType;
            this.future = future;
        }

        @Override
        public void completed(Integer count, ByteBuffer buffer) {
            Traces.currentTraceid(traceid);
            buffer.flip();
            if (this.readState == READ_STATE_ROUTE) {
                if (this.responseResult == null) {
                    this.responseResult = new HttpResult<>();
                }
                int rs = readStatusLine(buffer);
                if (rs != 0) {
                    buffer.clear();
                    conn.setReadBuffer(buffer);
                    conn.read(this);
                    return;
                }
                this.readState = READ_STATE_HEADER;
            }
            if (this.readState == READ_STATE_HEADER) {
                int rs = readHeaderLines(buffer);
                if (rs != 0) {
                    buffer.clear();
                    conn.setReadBuffer(buffer);
                    conn.read(this);
                    return;
                }
                this.readState = READ_STATE_BODY;
            }
            if (this.readState == READ_STATE_BODY) {
                if (this.contentLength > 0) {
                    array.put(buffer, Math.min(this.contentLength, buffer.remaining()));
                    int lr = (int) this.contentLength - array.length();
                    if (lr == 0) {
                        this.readState = READ_STATE_END;
                    } else {
                        buffer.clear();
                        conn.setReadBuffer(buffer);
                        conn.read(this);
                        return;
                    }
                }
                if (buffer.hasRemaining()) {
                    array.put(buffer, buffer.remaining());
                }
                this.readState = READ_STATE_END;
            }
            if (responseResult.getStatus() <= 200) {
                this.responseResult.setResult(array.getBytes());
            }
            conn.offerReadBuffer(buffer);
            conn.dispose();

            if (this.responseResult != null && valueType != null) {
                Convert c = convert == null ? JsonConvert.root() : convert;
                HttpResult result = this.responseResult;
                try {
                    result.result(c.convertFrom(valueType, this.responseResult.getResult()));
                    if (workThread != null && workThread.getState() == Thread.State.RUNNABLE) {
                        workThread.runWork(() -> {
                            Traces.currentTraceid(traceid);
                            future.complete((HttpResult<T>) this.responseResult);
                        });
                    } else if (workExecutor != null) {
                        workExecutor.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.complete((HttpResult<T>) this.responseResult);
                        });
                    } else {
                        Utility.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.complete((HttpResult<T>) this.responseResult);
                        });
                    }
                } catch (Exception e) {
                    if (workThread != null && workThread.getState() == Thread.State.RUNNABLE) {
                        workThread.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.completeExceptionally(e);
                        });
                    } else if (workExecutor != null) {
                        workExecutor.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.completeExceptionally(e);
                        });
                    } else {
                        Utility.execute(() -> {
                            Traces.currentTraceid(traceid);
                            future.completeExceptionally(e);
                        });
                    }
                }
            } else {
                if (workThread != null && workThread.getState() == Thread.State.RUNNABLE) {
                    workThread.runWork(() -> {
                        Traces.currentTraceid(traceid);
                        future.complete((HttpResult<T>) this.responseResult);
                    });
                } else if (workExecutor != null) {
                    workExecutor.execute(() -> {
                        Traces.currentTraceid(traceid);
                        future.complete((HttpResult<T>) this.responseResult);
                    });
                } else {
                    Utility.execute(() -> {
                        Traces.currentTraceid(traceid);
                        future.complete((HttpResult<T>) this.responseResult);
                    });
                }
            }
        }

        // 解析 HTTP/1.1 200 OK
        private int readStatusLine(final ByteBuffer buffer) {
            int remain = buffer.remaining();
            ByteArray bytes = array;
            for (; ; ) {
                if (remain-- < 1) {
                    buffer.clear();
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\r') {
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put((byte) '\r');
                        return 1;
                    }
                    if (buffer.get() != '\n') {
                        return -1;
                    }
                    break;
                }
                bytes.put(b);
            }
            String value = bytes.toString(null);
            int pos = value.indexOf(' ');
            this.responseResult.setStatus(Integer.decode(value.substring(pos + 1, value.indexOf(" ", pos + 2))));
            bytes.clear();
            return 0;
        }

        // 解析Header Connection: keep-alive
        // 返回0表示解析完整，非0表示还需继续读数据
        private int readHeaderLines(final ByteBuffer buffer) {
            int remain = buffer.remaining();
            ByteArray bytes = array;
            HttpResult<byte[]> result = responseResult;
            for (; ; ) {
                bytes.clear();
                if (remain-- < 2) {
                    if (remain == 1) {
                        byte one = buffer.get();
                        buffer.clear();
                        buffer.put(one);
                        return 1;
                    }
                    buffer.clear();
                    return 1;
                }
                remain--;
                byte b1 = buffer.get();
                byte b2 = buffer.get();
                if (b1 == '\r' && b2 == '\n') {
                    return 0;
                }
                boolean latin1 = true;
                if (latin1 && (b1 < 0x20 || b1 >= 0x80)) {
                    latin1 = false;
                }
                if (latin1 && (b2 < 0x20 || b2 >= 0x80)) {
                    latin1 = false;
                }
                bytes.put(b1, b2);
                for (; ; ) { // name
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put(bytes.content(), 0, bytes.length());
                        return 1;
                    }
                    byte b = buffer.get();
                    if (b == ':') {
                        break;
                    } else if (latin1 && (b < 0x20 || b >= 0x80)) {
                        latin1 = false;
                    }
                    bytes.put(b);
                }
                String name = parseHeaderName(latin1, bytes, null);
                bytes.clear();
                boolean first = true;
                int space = 0;
                for (; ; ) { // value
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put(name.getBytes());
                        buffer.put((byte) ':');
                        if (space == 1) {
                            buffer.put((byte) ' ');
                        } else if (space > 0) {
                            for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                        }
                        buffer.put(bytes.content(), 0, bytes.length());
                        return 1;
                    }
                    byte b = buffer.get();
                    if (b == '\r') {
                        if (remain-- < 1) {
                            buffer.clear();
                            buffer.put(name.getBytes());
                            buffer.put((byte) ':');
                            if (space == 1) {
                                buffer.put((byte) ' ');
                            } else if (space > 0) {
                                for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                            }
                            buffer.put(bytes.content(), 0, bytes.length());
                            buffer.put((byte) '\r');
                            return 1;
                        }
                        if (buffer.get() != '\n') {
                            return -1;
                        }
                        break;
                    }
                    if (first) {
                        if (b <= ' ') {
                            space++;
                            continue;
                        }
                        first = false;
                    }
                    bytes.put(b);
                }
                String value;
                switch (name) {
                    case "Content-Length":
                    case "content-length":
                        value = bytes.toString(true, null);
                        this.contentLength = Integer.decode(value);
                        result.header(name, value);
                        break;
                    default:
                        value = bytes.toString(null);
                        result.header(name, value);
                }
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            conn.offerReadBuffer(attachment);
            conn.dispose();
            future.completeExceptionally(exc);
        }
    }
}
