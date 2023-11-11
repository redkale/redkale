/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.boot.*;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import org.redkale.util.RedkaleException;
import org.redkale.util.Traces;
import static org.redkale.util.Utility.isEmpty;

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
public class HttpLocalRpcClient extends HttpRpcClient {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final Application application;

    protected final String resourceName;

    protected HttpServer currServer;

    public HttpLocalRpcClient(Application application, String resourceName) {
        this.application = application;
        this.resourceName = resourceName;
    }

    private HttpServer httpServer() {
        if (this.currServer == null) {
            NodeHttpServer nodeHttpServer = null;
            List<NodeServer> nodeServers = application.getNodeServers();
            for (NodeServer n : nodeServers) {
                if (n.getClass() == NodeHttpServer.class && Objects.equals(resourceName, ((NodeHttpServer) n).getHttpServer().getName())) {
                    nodeHttpServer = (NodeHttpServer) n;
                    break;
                }
            }
            if (nodeHttpServer == null) {
                for (NodeServer n : nodeServers) {
                    if (n.getClass() == NodeHttpServer.class) {
                        nodeHttpServer = (NodeHttpServer) n;
                        break;
                    }
                }
            }
            if (nodeHttpServer == null) {
                throw new HttpException("Not found HttpServer");
            }
            this.currServer = nodeHttpServer.getServer();
        }
        return this.currServer;
    }

    @Override
    protected int getNodeid() {
        return application.getNodeid();
    }

    protected HttpContext context() {
        return httpServer().getContext();
    }

    protected HttpDispatcherServlet dispatcherServlet() {
        return (HttpDispatcherServlet) httpServer().getDispatcherServlet();
    }

    public HttpServlet findHttpServlet(String topic) {
        return dispatcherServlet().findServletByTopic(topic);
    }

    public HttpServlet findHttpServlet(HttpSimpleRequest request) {
        return dispatcherServlet().findServletByTopic(generateHttpReqTopic(request, request.getPath()));
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(HttpSimpleRequest request, Type type) {
        return sendMessage((Serializable) null, (String) null, request, type);
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(Serializable userid, HttpSimpleRequest request, Type type) {
        return sendMessage(userid, (String) null, request, type);
    }

    @Override
    public <T> CompletableFuture<T> sendMessage(Serializable userid, String groupid, HttpSimpleRequest request, Type type) {
        if (isEmpty(request.getTraceid())) {
            request.setTraceid(Traces.currentTraceid());
        }
        CompletableFuture future = new CompletableFuture();
        String topic = generateHttpReqTopic(request, request.getPath());
        HttpServlet servlet = findHttpServlet(topic);
        if (servlet == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "sendMessage: request=" + request + ", not found servlet");
            }
            future.completeExceptionally(new HttpException("404 Not Found " + topic));
            return future;
        }
        HttpRequest req = new HttpMessageLocalRequest(context(), request, userid);
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        if (isEmpty(request.getTraceid())) {
            request.setTraceid(Traces.currentTraceid());
        }
        CompletableFuture future = new CompletableFuture();
        HttpServlet servlet = findHttpServlet(topic);
        if (servlet == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "sendMessage: request=" + request + ", not found servlet");
            }
            future.complete(new HttpResult().status(404));
            return future;
        }
        HttpRequest req = new HttpMessageLocalRequest(context(), request, userid);
        HttpResponse resp = new HttpMessageLocalResponse(req, future);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future.thenApply(rs -> {
            Traces.currentTraceid(request.getTraceid());
            if (rs == null) {
                return new HttpResult();
            }
            if (rs instanceof HttpResult) {
                Object result = ((HttpResult) rs).getResult();
                if (result == null || result instanceof byte[]) {
                    return (HttpResult) rs;
                }
                return new HttpResult(JsonConvert.root().convertToBytes(result));
            }
            return new HttpResult(JsonConvert.root().convertToBytes(rs));
        });
    }

    @Override
    public CompletableFuture<Void> produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        CompletableFuture future = new CompletableFuture();
        HttpDispatcherServlet ps = dispatcherServlet();
        HttpServlet servlet = ps.findServletByTopic(topic);
        if (servlet == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "produceMessage: request=" + request + ", not found servlet");
            }
            future.completeExceptionally(new RuntimeException("404 Not Found " + topic));
            return future;
        }
        HttpRequest req = new HttpMessageLocalRequest(context(), request, userid);
        HttpResponse resp = new HttpMessageLocalResponse(req, null);
        try {
            servlet.execute(req, resp);
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
        return future.thenApply(rs -> {
            Traces.currentTraceid(request.getTraceid());
            return null;
        });
    }

    public static class HttpMessageLocalRequest extends HttpRequest {

        public HttpMessageLocalRequest(HttpContext context, HttpSimpleRequest req, Serializable userid) {
            super(context, req);
            if (userid != null) {
                this.currentUserid = userid;
            }
        }
    }

    public static class HttpMessageLocalResponse extends HttpResponse {

        private CompletableFuture future;

        public HttpMessageLocalResponse(HttpRequest req, CompletableFuture future) {
            super(req.getContext(), req, null);
            this.future = future;
        }

        @Override
        public void finishJson(final Convert convert, final Type type, final Object obj) {
            if (future == null) {
                return;
            }
            future.complete(obj);
        }

        @Override
        public void finish(final Convert convert, Type type, org.redkale.service.RetResult ret) {
            if (future == null) {
                return;
            }
            future.complete(ret);
        }

        @Override
        public void finish(final Convert convert, final Type type, Object obj) {
            if (future == null) {
                return;
            }
            if (obj instanceof CompletableFuture) {
                ((CompletableFuture) obj).whenComplete((r, t) -> {
                    if (t == null) {
                        future.complete(r);
                    } else {
                        future.completeExceptionally((Throwable) t);
                    }
                });
            } else {
                future.complete(obj);
            }
        }

        @Override
        public void finish(String obj) {
            if (future == null) {
                return;
            }
            future.complete(obj == null ? "" : obj);
        }

        @Override
        public void finish304() {
            finish(304, null);
        }

        @Override
        public void finish404() {
            finish(404, null);
        }

        @Override
        public void finish500() {
            finish(500, null);
        }

        @Override
        public void finish504() {
            finish(504, null);
        }

        @Override
        public void finish(int status, String msg) {
            if (future == null) {
                return;
            }
            if (status == 0 || status == 200) {
                future.complete(msg == null ? "" : msg);
            } else {
                future.complete(new HttpResult(msg == null ? "" : msg).status(status));
            }
        }

        @Override
        public void finish(final Convert convert, Type valueType, HttpResult result) {
            if (future == null) {
                return;
            }
            if (convert != null) {
                result.convert(convert);
            }
            future.complete(result);
        }

        @Override
        public void finish(boolean kill, final byte[] bs, int offset, int length) {
            if (future == null) {
                return;
            }
            if (offset == 0 && bs.length == length) {
                future.complete(bs);
            } else {
                future.complete(Arrays.copyOfRange(bs, offset, offset + length));
            }
        }

        @Override
        public void finish(boolean kill, final String contentType, final byte[] bs, int offset, int length) {
            if (future == null) {
                return;
            }
            byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
            future.complete(rs);
        }

        @Override
        public void finishBuffer(boolean kill, ByteBuffer buffer) {
            if (future == null) {
                return;
            }
            byte[] bs = new byte[buffer.remaining()];
            buffer.get(bs);
            future.complete(bs);
        }

        @Override
        public void finishBuffers(boolean kill, ByteBuffer... buffers) {
            if (future == null) {
                return;
            }
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
