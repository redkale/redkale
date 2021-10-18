/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.redkale.net.*;
import static org.redkale.net.http.HttpRequest.parseHeaderName;
import org.redkale.util.*;

/**
 * 简单的HttpClient实现, 存在以下情况不能使用此类: <br>
 * 1、使用HTTPS；<br>
 * 2、上传下载文件；<br>
 * 3、返回超大响应包；<br>
 * 类似JDK11的 java.net.http.HttpClient <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 *
 */
public class HttpClient {

    protected final AsyncGroup asyncGroup;

    protected int readTimeoutSeconds = 6;

    protected int writeTimeoutSeconds = 6;

    protected HttpClient(AsyncGroup asyncGroup) {
        this.asyncGroup = asyncGroup;
    }

    public static HttpClient create(AsyncGroup asyncGroup) {
        return new HttpClient(asyncGroup);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url) {
        return sendAsync("GET", url, null, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url) {
        return sendAsync("POST", url, null, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, String body) {
        return sendAsync("GET", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, String body) {
        return sendAsync("POST", url, null, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, byte[] body) {
        return sendAsync("GET", url, null, body);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, byte[] body) {
        return sendAsync("POST", url, null, body);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, Map<String, String> headers) {
        return sendAsync("GET", url, headers, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, Map<String, String> headers) {
        return sendAsync("POST", url, headers, (byte[]) null);
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, Map<String, String> headers, String body) {
        return sendAsync("GET", url, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, Map<String, String> headers, String body) {
        return sendAsync("POST", url, headers, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResult<byte[]>> getAsync(String url, Map<String, String> headers, byte[] body) {
        return sendAsync("GET", url, headers, body);
    }

    public CompletableFuture<HttpResult<byte[]>> postAsync(String url, Map<String, String> headers, byte[] body) {
        return sendAsync("POST", url, headers, body);
    }

    public CompletableFuture<HttpResult<byte[]>> sendAsync(String method, String url, Map<String, String> headers, byte[] body) {
        final URI uri = URI.create(url);
        final SocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : (url.startsWith("https:") ? 443 : 80));
        return asyncGroup.createTCPClient(address, readTimeoutSeconds, writeTimeoutSeconds).thenCompose(conn -> {
            final ByteArray array = new ByteArray();
            int urlpos = url.indexOf("/", url.indexOf("//") + 3);
            array.put((method + " " + (urlpos > 0 ? url.substring(urlpos) : "/") + " HTTP/1.1\r\n"
                + "Host: " + uri.getHost() + "\r\n"
                + "Content-Length: " + (body == null ? 0 : body.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            if (headers == null || !headers.containsKey("User-Agent")) {
                array.put(("User-Agent: redkale-httpclient/" + Redkale.getDotedVersion() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            if (headers == null || !headers.containsKey("Connection")) {
                array.put(("Connection: close\r\n").getBytes(StandardCharsets.UTF_8));
            }
            if (headers != null) {
                headers.forEach((k, v) -> {
                    array.put((k + ": " + v + "\r\n").getBytes(StandardCharsets.UTF_8));
                });
            }
            array.put((byte) '\r', (byte) '\n');
            if (body != null) array.put(body);
            System.out.println(array);
            final CompletableFuture<HttpResult<byte[]>> future = new CompletableFuture();
            conn.write(array, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    conn.read(new ClientReadCompletionHandler(conn, array.clear(), future));
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    conn.dispose();
                    future.completeExceptionally(exc);
                }
            });
            return future;
        });
    }
//
//    public static void main(String[] args) throws Throwable {
//        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
//        asyncGroup.start();
//        String url = "http://redkale.org";
//        HttpClient client = HttpClient.create(asyncGroup);
//        System.out.println(client.getAsync(url).join());
//    }

    protected static class ClientReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {

        protected static final int READ_STATE_ROUTE = 1;

        protected static final int READ_STATE_HEADER = 2;

        protected static final int READ_STATE_BODY = 3;

        protected static final int READ_STATE_END = 4;

        protected final AsyncConnection conn;

        protected final ByteArray array;

        protected final CompletableFuture<HttpResult<byte[]>> future;

        protected HttpResult<byte[]> responseResult;

        protected int readState = READ_STATE_ROUTE;

        protected int contentLength = -1;

        public ClientReadCompletionHandler(AsyncConnection conn, ByteArray array, CompletableFuture<HttpResult<byte[]>> future) {
            this.conn = conn;
            this.array = array;
            this.future = future;
        }

        @Override
        public void completed(Integer count, ByteBuffer buffer) {
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
                    array.put(buffer, Math.min((int) this.contentLength, buffer.remaining()));
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
                if (buffer.hasRemaining()) array.put(buffer, buffer.remaining());
                this.readState = READ_STATE_END;
            }
            this.responseResult.setResult(array.getBytes());
            this.future.complete(this.responseResult);
            conn.offerBuffer(buffer);
            conn.dispose();
        }

        //解析 HTTP/1.1 200 OK  
        private int readStatusLine(final ByteBuffer buffer) {
            int remain = buffer.remaining();
            ByteArray bytes = array;
            for (;;) {
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
                    if (buffer.get() != '\n') return -1;
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

        //解析Header Connection: keep-alive
        private int readHeaderLines(final ByteBuffer buffer) {
            int remain = buffer.remaining();
            ByteArray bytes = array;
            HttpResult<byte[]> result = responseResult;
            for (;;) {
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
                if (b1 == '\r' && b2 == '\n') return 0;
                bytes.put(b1, b2);
                for (;;) {  // name
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put(bytes.content(), 0, bytes.length());
                        return 1;
                    }
                    byte b = buffer.get();
                    if (b == ':') break;
                    bytes.put(b);
                }
                String name = parseHeaderName(bytes, null);
                bytes.clear();
                boolean first = true;
                int space = 0;
                for (;;) {  // value
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
                        if (buffer.get() != '\n') return -1;
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
                        value = bytes.toString(null);
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
            conn.offerBuffer(attachment);
            conn.dispose();
            future.completeExceptionally(exc);
        }

    }
}
