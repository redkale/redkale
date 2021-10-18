/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.io.Serializable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.logging.Level;
import javax.annotation.Resource;
import org.redkale.cluster.ClusterAgent;
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
public class HttpMessageClusterClient extends HttpMessageClient {

    //jdk.internal.net.http.common.Utils.DISALLOWED_HEADERS_SET
    private static final Set<String> DISALLOWED_HEADERS_SET = Utility.ofSet("connection", "content-length",
        "date", "expect", "from", "host", "origin", "referer", "upgrade", "via", "warning");

    protected ClusterAgent clusterAgent;

    @Resource(name = "cluster.httpClient")
    protected HttpClient httpClient;

    //protected java.net.http.HttpClient httpClient;
    public HttpMessageClusterClient(ClusterAgent clusterAgent) {
        super(null);
        Objects.requireNonNull(clusterAgent);
        this.clusterAgent = clusterAgent;
        //this.httpClient = java.net.http.HttpClient.newHttpClient();
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        return httpAsync(userid, request);
    }

    @Override
    public void produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        httpAsync(userid, request);
    }

    @Override
    public void broadcastMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        mqtpAsync(userid, request);
    }

    private CompletableFuture<HttpResult<byte[]>> mqtpAsync(Serializable userid, HttpSimpleRequest req) {
        final boolean finest = logger.isLoggable(Level.FINEST);
        String module = req.getRequestURI();
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = req.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        return clusterAgent.queryMqtpAddress("mqtp", module, resname).thenCompose(addrmap -> {
            if (addrmap == null || addrmap.isEmpty()) return new HttpResult().status(404).toAnyFuture();
            final Map<String, String> clientHeaders = new LinkedHashMap<>();
            byte[] clientBody = null;
            if (req.isRpc()) clientHeaders.put(Rest.REST_HEADER_RPC_NAME, "true");
            if (req.isFrombody()) clientHeaders.put(Rest.REST_HEADER_PARAM_FROM_BODY, "true");
            if (req.getReqConvertType() != null) clientHeaders.put(Rest.REST_HEADER_REQ_CONVERT_TYPE, req.getReqConvertType().toString());
            if (req.getRespConvertType() != null) clientHeaders.put(Rest.REST_HEADER_RESP_CONVERT_TYPE, req.getRespConvertType().toString());
            if (userid != null) clientHeaders.put(Rest.REST_HEADER_CURRUSERID_NAME, "" + userid);
            if (headers != null) headers.forEach((n, v) -> {
                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())) clientHeaders.put(n, v);
                });
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
                if (paramstr != null) clientBody = paramstr.getBytes(StandardCharsets.UTF_8);
            }
            List<CompletableFuture> futures = new ArrayList<>();
            for (Map.Entry<String, Collection<InetSocketAddress>> en : addrmap.entrySet()) {
                String realmodule = en.getKey();
                Collection<InetSocketAddress> addrs = en.getValue();
                if (addrs == null || addrs.isEmpty()) continue;
                String suburi = req.getRequestURI();
                suburi = suburi.substring(1); //跳过 /
                suburi = "/" + realmodule + suburi.substring(suburi.indexOf('/'));
                futures.add(forEachCollectionFuture(finest, userid, req, (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + suburi, clientHeaders, clientBody, addrs.iterator()));
            }
            if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenApply(v -> null);
        });
    }

    private CompletableFuture<HttpResult<byte[]>> httpAsync(Serializable userid, HttpSimpleRequest req) {
        final boolean finest = logger.isLoggable(Level.FINEST);
        String module = req.getRequestURI();
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = req.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        return clusterAgent.queryHttpAddress("http", module, resname).thenCompose(addrs -> {
            if (addrs == null || addrs.isEmpty()) return new HttpResult().status(404).toAnyFuture();
            final Map<String, String> clientHeaders = new LinkedHashMap<>();
            byte[] clientBody = null;
            if (req.isRpc()) clientHeaders.put(Rest.REST_HEADER_RPC_NAME, "true");
            if (req.isFrombody()) clientHeaders.put(Rest.REST_HEADER_PARAM_FROM_BODY, "true");
            if (req.getReqConvertType() != null) clientHeaders.put(Rest.REST_HEADER_REQ_CONVERT_TYPE, req.getReqConvertType().toString());
            if (req.getRespConvertType() != null) clientHeaders.put(Rest.REST_HEADER_RESP_CONVERT_TYPE, req.getRespConvertType().toString());
            if (userid != null) clientHeaders.put(Rest.REST_HEADER_CURRUSERID_NAME, "" + userid);
            if (headers != null) headers.forEach((n, v) -> {
                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())) clientHeaders.put(n, v);
                });
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
                if (paramstr != null) clientBody = paramstr.getBytes(StandardCharsets.UTF_8);
            }
            return forEachCollectionFuture(finest, userid, req, (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + req.getRequestURI(), clientHeaders, clientBody, addrs.iterator());
        });
    }

    private CompletableFuture<HttpResult<byte[]>> forEachCollectionFuture(boolean finest, Serializable userid, HttpSimpleRequest req, String requesturi, final Map<String, String> clientHeaders, byte[] clientBody, Iterator<InetSocketAddress> it) {
        if (!it.hasNext()) return CompletableFuture.completedFuture(null);
        InetSocketAddress addr = it.next();
        String url = "http://" + addr.getHostString() + ":" + addr.getPort() + requesturi;
        return httpClient.postAsync(url, clientHeaders, clientBody);
    }

//    private CompletableFuture<HttpResult<byte[]>> mqtpAsync(Serializable userid, HttpSimpleRequest req) {
//        final boolean finest = logger.isLoggable(Level.FINEST);
//        String module = req.getRequestURI();
//        module = module.substring(1); //去掉/
//        module = module.substring(0, module.indexOf('/'));
//        Map<String, String> headers = req.getHeaders();
//        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
//        return clusterAgent.queryMqtpAddress("mqtp", module, resname).thenCompose(addrmap -> {
//            if (addrmap == null || addrmap.isEmpty()) return new HttpResult().status(404).toAnyFuture();
//            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder().timeout(Duration.ofMillis(30000));
//            if (req.isRpc()) builder.header(Rest.REST_HEADER_RPC_NAME, "true");
//            if (req.isFrombody()) builder.header(Rest.REST_HEADER_PARAM_FROM_BODY, "true");
//            if (req.getReqConvertType() != null) builder.header(Rest.REST_HEADER_REQ_CONVERT_TYPE, req.getReqConvertType().toString());
//            if (req.getRespConvertType() != null) builder.header(Rest.REST_HEADER_RESP_CONVERT_TYPE, req.getRespConvertType().toString());
//            if (userid != 0) builder.header(Rest.REST_HEADER_CURRUSERID_NAME, "" + userid);
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
//            List<CompletableFuture> futures = new ArrayList<>();
//            for (Map.Entry<String, Collection<InetSocketAddress>> en : addrmap.entrySet()) {
//                String realmodule = en.getKey();
//                Collection<InetSocketAddress> addrs = en.getValue();
//                if (addrs == null || addrs.isEmpty()) continue;
//                String suburi = req.getRequestURI();
//                suburi = suburi.substring(1); //跳过 /
//                suburi = "/" + realmodule + suburi.substring(suburi.indexOf('/'));
//                futures.add(forEachCollectionFuture(finest, userid, req, (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + suburi, builder, addrs.iterator()));
//            }
//            if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
//            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenApply(v -> null);
//        });
//    }
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
//            if (userid != 0) builder.header(Rest.REST_HEADER_CURRUSERID_NAME, "" + userid);
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
