package org.redkale.cluster.spi;

import java.io.Serializable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.cluster.HttpRpcClient;
import org.redkale.net.http.*;
import org.redkale.util.Traces;
import org.redkale.util.Utility;
import static org.redkale.util.Utility.isEmpty;
import static org.redkale.util.Utility.isNotEmpty;

/**
 * 没有配置MQ的情况下依赖ClusterAgent实现的默认HttpRpcClient实例
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpClusterRpcClient extends HttpRpcClient {

    //jdk.internal.net.http.common.Utils.DISALLOWED_HEADERS_SET
    private static final Set<String> DISALLOWED_HEADERS_SET = Utility.ofSet("connection", "content-length",
        "date", "expect", "from", "host", "origin", "referer", "upgrade", "via", "warning");

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final HttpLocalRpcClient localClient;

    protected final ConcurrentHashMap<String, Boolean> topicServletMap = new ConcurrentHashMap<>();

    protected ClusterAgent clusterAgent;

    @Resource(name = "cluster.httpClient", required = false)
    protected HttpSimpleClient httpSimpleClient;

    @Resource(name = "cluster.httpClient", required = false)
    protected java.net.http.HttpClient httpClient;

    public HttpClusterRpcClient(Application application, String resourceName, ClusterAgent clusterAgent) {
        Objects.requireNonNull(clusterAgent);
        this.localClient = new HttpLocalRpcClient(application, resourceName);
        this.clusterAgent = clusterAgent;
    }

    @Override
    protected int getNodeid() {
        return localClient.getNodeid();
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        if (topicServletMap.computeIfAbsent(topic, t -> localClient.findHttpServlet(t) != null)) {
            return localClient.sendMessage(topic, userid, groupid, request);
        } else {
            return httpAsync(false, userid, request);
        }
    }

    @Override
    public CompletableFuture<Void> produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        if (topicServletMap.computeIfAbsent(topic, t -> localClient.findHttpServlet(t) != null)) {
            return localClient.produceMessage(topic, userid, groupid, request);
        } else {
            return httpAsync(true, userid, request).thenApply(v -> null);
        }
    }

    private CompletableFuture<HttpResult<byte[]>> httpAsync(boolean produce, Serializable userid, HttpSimpleRequest req) {
        req.setTraceid(Traces.computeIfAbsent(req.getTraceid(), Traces.currentTraceid()));
        String module = req.getPath();
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        HttpHeaders headers = req.getHeaders();
        String resname = req.getHeader(Rest.REST_HEADER_RESNAME, "");
        final String localModule = module;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "httpAsync.queryHttpAddress: module=" + localModule + ", resname=" + resname);
        }
        return clusterAgent.queryHttpAddress("http", module, resname).thenCompose(addrs -> {
            Traces.currentTraceid(req.getTraceid());
            if (isEmpty(addrs)) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "httpAsync." + (produce ? "produceMessage" : "sendMessage")
                        + " failed, module=" + localModule + ", resname=" + resname + ", address is empty");
                }
                return new HttpResult<byte[]>().status(404).toFuture();
            }
            final HttpHeaders clientHeaders = HttpHeaders.create();
            if (headers != null) {
                boolean ws = headers.contains("Sec-WebSocket-Key");
                headers.forEach((n, v) -> {
                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())
                        && (!ws || (!"Connection".equals(n) && !"Sec-WebSocket-Key".equals(n)
                        && !"Sec-WebSocket-Version".equals(n)))) {
                        clientHeaders.add(n, v);
                    }
                });
            }
            clientHeaders.set("Content-Type", "x-www-form-urlencoded");
            if (req.isRpc()) {
                clientHeaders.set(Rest.REST_HEADER_RPC, "true");
            }
            if (isNotEmpty(req.getTraceid())) {
                clientHeaders.set(Rest.REST_HEADER_TRACEID, req.getTraceid());
            }
            if (userid != null) {
                clientHeaders.set(Rest.REST_HEADER_CURRUSERID, String.valueOf(userid));
            }
            if (req.getReqConvertType() != null) {
                clientHeaders.set(Rest.REST_HEADER_REQ_CONVERT, req.getReqConvertType().toString());
            }
            if (req.getRespConvertType() != null) {
                clientHeaders.set(Rest.REST_HEADER_RESP_CONVERT, req.getRespConvertType().toString());
            }

            if (httpSimpleClient != null) {
                HttpSimpleRequest newReq = req.copy().headers(clientHeaders);
                InetSocketAddress addr = randomAddress(newReq, addrs);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "httpAsync: module=" + localModule + ", resname=" + resname + ", addr=" + addr);
                }
                return (CompletableFuture) httpSimpleClient.sendAsync(addr, newReq);
            }
            byte[] clientBody = null;
            if (isNotEmpty(req.getBody())) {
                String paramstr = req.getParametersToString();
                if (paramstr != null) {
                    if (req.getPath().indexOf('?') > 0) {
                        req.setPath(req.getPath() + "&" + paramstr);
                    } else {
                        req.setPath(req.getPath() + "?" + paramstr);
                    }
                }
                clientBody = req.getBody();
            } else {
                String paramstr = req.getParametersToString();
                if (paramstr != null) {
                    clientBody = paramstr.getBytes(StandardCharsets.UTF_8);
                }
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "httpAsync: module=" + localModule + ", resname=" + resname + ", enter sendEachAddressAsync");
            }
            return sendEachAddressAsync(req, req.requestPath(), clientHeaders, clientBody, addrs.iterator());
        });
    }

    protected InetSocketAddress randomAddress(HttpSimpleRequest req, Set<InetSocketAddress> addrs) {
        InetSocketAddress[] array = addrs.toArray(new InetSocketAddress[addrs.size()]);
        return array[ThreadLocalRandom.current().nextInt(array.length)];
    }

    protected CompletableFuture<HttpResult<byte[]>> sendEachAddressAsync(HttpSimpleRequest req,
        String requestPath, final HttpHeaders clientHeaders, byte[] clientBody, Iterator<InetSocketAddress> it) {
        if (!it.hasNext()) {
            return new HttpResult<byte[]>().status(404).toFuture();
        }
        InetSocketAddress addr = it.next();
        String host = addr.getPort() > 0 && addr.getPort() != 80 ? (addr.getHostString() + ":" + addr.getPort()) : addr.getHostString();
        String url = "http://" + host + requestPath;
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "sendEachAddressAsync: url: " + url
                + ", body: " + (clientBody != null ? new String(clientBody, StandardCharsets.UTF_8) : "") + ", headers: " + clientHeaders);
        }
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(10_000))
            //存在sendHeader后不发送body数据的问题， java.net.http.HttpRequest的bug?
            .method("POST", createBodyPublisher(clientBody));
        clientHeaders.forEach(builder::header);
        return httpClient.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            .thenApply((java.net.http.HttpResponse<byte[]> resp) -> {
                Traces.currentTraceid(req.getTraceid());
                final int rs = resp.statusCode();
                if (rs != 200) {
                    return new HttpResult<byte[]>().status(rs);
                }
                return new HttpResult<>(resp.body());
            });
    }

    private static java.net.http.HttpRequest.BodyPublisher createBodyPublisher(byte[] clientBody) {
        return clientBody == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofByteArray(clientBody);
    }

}
