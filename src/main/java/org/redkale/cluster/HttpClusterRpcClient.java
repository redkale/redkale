package org.redkale.cluster;

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
import org.redkale.net.http.*;
import org.redkale.util.Utility;

/**
 * 没有配置MQ的情况下依赖ClusterAgent实现的默认HttpMessageClient实例
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
    protected java.net.http.HttpClient httpClient;

    @Resource(name = "cluster.httpClient", required = false)
    protected HttpSimpleClient httpSimpleClient;

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
    public void produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        if (topicServletMap.computeIfAbsent(topic, t -> localClient.findHttpServlet(t) != null)) {
            localClient.produceMessage(topic, userid, groupid, request);
        } else {
            httpAsync(true, userid, request);
        }
    }

    private CompletableFuture<HttpResult<byte[]>> httpAsync(boolean produce, Serializable userid, HttpSimpleRequest req) {
        String module = req.getRequestURI();
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = req.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        final String localModule = module;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "httpAsync.queryHttpAddress: module=" + localModule + ", resname=" + resname);
        }
        return clusterAgent.queryHttpAddress("http", module, resname).thenCompose(addrs -> {
            if (addrs == null || addrs.isEmpty()) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "httpAsync." + (produce ? "produceMessage" : "sendMessage") + " failed, module=" + localModule + ", resname=" + resname + ", address is empty");
                }
                return new HttpResult<byte[]>().status(404).toFuture();
            }
            final Map<String, String> clientHeaders = new LinkedHashMap<>();
            byte[] clientBody = null;
            if (req.isRpc()) {
                clientHeaders.put(Rest.REST_HEADER_RPC, "true");
            }
            if (req.isFrombody()) {
                clientHeaders.put(Rest.REST_HEADER_PARAM_FROM_BODY, "true");
            }
            if (req.getReqConvertType() != null) {
                clientHeaders.put(Rest.REST_HEADER_REQ_CONVERT_TYPE, req.getReqConvertType().toString());
            }
            if (req.getRespConvertType() != null) {
                clientHeaders.put(Rest.REST_HEADER_RESP_CONVERT_TYPE, req.getRespConvertType().toString());
            }
            if (userid != null) {
                clientHeaders.put(Rest.REST_HEADER_CURRUSERID, "" + userid);
            }
            if (headers != null) {
                boolean ws = headers.containsKey("Sec-WebSocket-Key");
                headers.forEach((n, v) -> {
                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())
                        && (!ws || (!"Connection".equals(n) && !"Sec-WebSocket-Key".equals(n)
                        && !"Sec-WebSocket-Version".equals(n)))) {
                        clientHeaders.put(n, v);
                    }
                });
            }
            clientHeaders.put("Content-Type", "x-www-form-urlencoded");
            if (req.getBody() != null && req.getBody().length > 0) {
                String paramstr = req.getParametersToString();
                if (paramstr != null) {
                    if (req.getRequestURI().indexOf('?') > 0) {
                        req.setRequestURI(req.getRequestURI() + "&" + paramstr);
                    } else {
                        req.setRequestURI(req.getRequestURI() + "?" + paramstr);
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
                logger.log(Level.FINEST, "httpAsync: module=" + localModule + ", resname=" + resname + ", enter forEachCollectionFuture");
            }
            return forEachCollectionFuture(logger.isLoggable(Level.FINEST), userid, req,
                (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + req.getRequestURI(),
                clientHeaders, clientBody, addrs.iterator());
        });
    }

    private CompletableFuture<HttpResult<byte[]>> forEachCollectionFuture(boolean finest, Serializable userid,
        HttpSimpleRequest req, String requesturi, final Map<String, String> clientHeaders, byte[] clientBody, Iterator<InetSocketAddress> it) {
        if (!it.hasNext()) {
            return CompletableFuture.completedFuture(null);
        }
        InetSocketAddress addr = it.next();
        String url = "http://" + addr.getHostString() + ":" + addr.getPort() + requesturi;
        if (finest) {
            logger.log(Level.FINEST, "forEachCollectionFuture: url=" + url + ", headers=" + clientHeaders);
        }
        if (httpSimpleClient != null) {
            return httpSimpleClient.postAsync(url, clientHeaders, clientBody);
        }
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder().uri(URI.create(url))
            .timeout(Duration.ofMillis(10_000))
            //存在sendHeader后不发送body数据的问题， java.net.http.HttpRequest的bug?
            .method("POST", clientBody == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofByteArray(clientBody));
        if (clientHeaders != null) {
            clientHeaders.forEach((n, v) -> builder.header(n, v));
        }
        return httpClient.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            .thenApply((java.net.http.HttpResponse<byte[]> resp) -> {
                final int rs = resp.statusCode();
                if (rs != 200) {
                    return new HttpResult<byte[]>().status(rs);
                }
                return new HttpResult<byte[]>(resp.body());
            });
    }

//
//    private CompletableFuture<HttpResult<byte[]>> httpAsync(Serializable userid, HttpSimpleRequest req) {
//        final boolean finest = logger.isLoggable(Level.FINEST);
//        String module = req.getRequestURI();
//        module = module.substring(1); //去掉/
//        module = module.substring(0, module.indexOf('/'));
//        Map<String, String> headers = req.getHeaders();
//        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
//        return clusterAgent.queryHttpAddress("http", module, resname).thenCompose(addrs -> {
//            if (addrs == null || addrs.isEmpty()) return new HttpResult().status(404).toAnyFuture();
//            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder().timeout(Duration.ofMillis(30000));
//            if (req.isRpc()) builder.header(Rest.REST_HEADER_RPC_NAME, "true");
//            if (req.isFrombody()) builder.header(Rest.REST_HEADER_PARAM_FROM_BODY, "true");
//            if (req.getReqConvertType() != null) builder.header(Rest.REST_HEADER_REQ_CONVERT_TYPE, req.getReqConvertType().toString());
//            if (req.getRespConvertType() != null) builder.header(Rest.REST_HEADER_RESP_CONVERT_TYPE, req.getRespConvertType().toString());
//            if (userid != 0) builder.header(Rest.REST_HEADER_CURRUSERID, "" + userid);
//            if (headers != null) headers.forEach((n, v) -> {
//                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())) builder.header(n, v);
//                });
//            builder.header("Content-Type", "x-www-form-urlencoded");
//            if (req.getBody() != null && req.getBody().length > 0) {
//                String paramstr = req.getParametersToString();
//                if (paramstr != null) {
//                    if (req.getRequestURI().indexOf('?') > 0) {
//                        req.setRequestURI(req.getRequestURI() + "&" + paramstr);
//                    } else {
//                        req.setRequestURI(req.getRequestURI() + "?" + paramstr);
//                    }
//                }
//                builder.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(req.getBody()));
//            } else {
//                String paramstr = req.getParametersToString();
//                if (paramstr != null) builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(paramstr));
//            }
//            return forEachCollectionFuture(finest, userid, req, (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + req.getRequestURI(), builder, addrs.iterator());
//        });
//    }
//
//    private CompletableFuture<HttpResult<byte[]>> forEachCollectionFuture(boolean finest, Serializable userid, HttpSimpleRequest req, String requesturi, java.net.http.HttpRequest.Builder builder, Iterator<InetSocketAddress> it) {
//        if (!it.hasNext()) return CompletableFuture.completedFuture(null);
//        InetSocketAddress addr = it.next();
//        String url = "http://" + addr.getHostString() + ":" + addr.getPort() + requesturi;
//        return httpClient.sendAsync(builder.copy().uri(URI.create(url)).build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).thenCompose(resp -> {
//            if (resp.statusCode() != 200) return forEachCollectionFuture(finest, userid, req, requesturi, builder, it);
//            HttpResult rs = new HttpResult();
//            java.net.http.HttpHeaders hs = resp.headers();
//            if (hs != null) {
//                Map<String, List<String>> hm = hs.map();
//                if (hm != null) {
//                    for (Map.Entry<String, List<String>> en : hm.entrySet()) {
//                        if ("date".equals(en.getKey()) || "content-type".equals(en.getKey())
//                            || "server".equals(en.getKey()) || "connection".equals(en.getKey())) continue;
//                        List<String> val = en.getValue();
//                        if (val != null && val.size() == 1) {
//                            rs.header(en.getKey(), val.get(0));
//                        }
//                    }
//                }
//            }
//            rs.setResult(resp.body());
//            if (finest) {
//                StringBuilder sb = new StringBuilder();
//                Map<String, String> params = req.getParams();
//                if (params != null && !params.isEmpty()) {
//                    params.forEach((n, v) -> sb.append('&').append(n).append('=').append(v));
//                }
//                logger.log(Level.FINEST, url + "?userid=" + userid + sb + ", result = " + new String(resp.body(), StandardCharsets.UTF_8));
//            }
//            return CompletableFuture.completedFuture(rs);
//        });
//    }
}
