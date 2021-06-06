/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;

/**
 * 没有配置MQ且也没有ClusterAgent的情况下实现的默认HttpMessageClient实例
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.4.0
 */
public class HttpMessageLocalClient extends HttpMessageClient {

    protected final HttpServer server;

    public HttpMessageLocalClient(HttpServer server) {
        super(null);
        this.server = server;
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(HttpSimpleRequest request, Type type) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        CompletableFuture future = new CompletableFuture();
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(int userid, HttpSimpleRequest request, Type type) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        CompletableFuture future = new CompletableFuture();
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(int userid, String groupid, HttpSimpleRequest request, Type type) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        CompletableFuture future = new CompletableFuture();
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        CompletableFuture future = new CompletableFuture();
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future.thenApply(rs -> {
            if (rs == null) return new HttpResult();
            if (rs instanceof HttpResult) {
                Object result = ((HttpResult) rs).getResult();
                if (result == null || result instanceof byte[]) return (HttpResult) rs;
                return new HttpResult(JsonConvert.root().convertToBytes(result));
            }
            return new HttpResult(JsonConvert.root().convertToBytes(rs));
        });
    }

    @Override
    public void produceMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        HttpResponse resp = new HttpMessageLocalResponse(req, null);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void broadcastMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        HttpPrepareServlet servlet = (HttpPrepareServlet) server.getPrepareServlet();
        HttpRequest req = new HttpMessageLocalRequest(server.getContext(), request);
        HttpResponse resp = new HttpMessageLocalResponse(req, null);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class HttpMessageLocalRequest extends HttpRequest {

        public HttpMessageLocalRequest(HttpContext context, HttpSimpleRequest req) {
            super(context, req);
        }
    }

    public static class HttpMessageLocalResponse extends HttpResponse {

        private CompletableFuture future;

        public HttpMessageLocalResponse(HttpRequest req, CompletableFuture future) {
            super(req.getContext(), req, null);
            this.future = future;
        }

        @Override
        public void finishJson(org.redkale.service.RetResult ret) {
            if (future == null) return;
            future.complete(ret);
        }

        @Override
        public void finish(String obj) {
            if (future == null) return;
            future.complete(obj == null ? "" : obj);
        }

        @Override
        public void finish404() {
            finish(404, null);
        }

        @Override
        public void finish(int status, String msg) {
            if (future == null) return;
            if (status == 0 || status == 200) {
                future.complete(msg == null ? "" : msg);
            } else {
                future.complete(new HttpResult(msg == null ? "" : msg).status(status));
            }
        }

        @Override
        public void finish(final Convert convert, HttpResult result) {
            if (future == null) return;
            if (convert != null) result.convert(convert);
            future.complete(result);
        }

        @Override
        public void finish(boolean kill, final byte[] bs, int offset, int length) {
            if (future == null) return;
            if (offset == 0 && bs.length == length) {
                future.complete(bs);
            } else {
                future.complete(Arrays.copyOfRange(bs, offset, offset + length));
            }
        }

        @Override
        public void finish(boolean kill, final String contentType, final byte[] bs, int offset, int length) {
            if (future == null) return;
            byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
            future.complete(rs);
        }

        @Override
        public void finish(boolean kill, ByteBuffer buffer) {
            if (future == null) return;
            byte[] bs = new byte[buffer.remaining()];
            buffer.get(bs);
            future.complete(bs);
        }

        @Override
        public void finish(boolean kill, ByteBuffer... buffers) {
            if (future == null) return;
            int size = 0;
            for (ByteBuffer buf : buffers) {
                size += buf.remaining();
            }
            byte[] bs = new byte[size];
            int index = 0;
            for (ByteBuffer buf : buffers) {
                int r = buf.remaining();
                buf.get(bs, index, r);
                index += r;
            }
            future.complete(bs);
        }
    }
}
