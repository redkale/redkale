/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.cluster.ClusterAgent;
import org.redkale.convert.ConvertType;
import org.redkale.net.http.*;

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
    private static final Set<String> DISALLOWED_HEADERS_SET = Set.of("connection", "content-length",
        "date", "expect", "from", "host", "origin",
        "referer", "upgrade", "via", "warning");

    protected ClusterAgent clusterAgent;

    protected java.net.http.HttpClient httpClient;

    public HttpMessageClusterClient(ClusterAgent clusterAgent) {
        super(null);
        Objects.requireNonNull(clusterAgent);
        this.clusterAgent = clusterAgent;
        this.httpClient = java.net.http.HttpClient.newHttpClient();
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        return httpAsync(userid, request);
    }

    @Override
    public void produceMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        httpAsync(userid, request);
    }

    protected CompletableFuture<HttpResult<byte[]>> httpAsync(int userid, HttpSimpleRequest req) {
        String module = req.getRequestURI();
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = req.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        return clusterAgent.queryHttpAddress("http", module, resname).thenCompose(addrs -> {
            if (addrs == null || addrs.isEmpty()) return new HttpResult().status(404).toAnyFuture();
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder().timeout(Duration.ofMillis(6000));
            if (req.isRpc()) builder.header(Rest.REST_HEADER_RPC_NAME, "true");
            if (userid != 0) builder.header(Rest.REST_HEADER_CURRUSERID_NAME, "" + userid);
            if (headers != null) headers.forEach((n, v) -> {
                    if (!DISALLOWED_HEADERS_SET.contains(n.toLowerCase())) builder.header(n, v);
                });
            builder.header("Content-Type", "x-www-form-urlencoded");
            String paramstr = req.getParametersToString();
            if (paramstr != null) builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(paramstr));
            return forEachCollectionFuture(userid, req, builder, addrs.iterator());
        });
    }

    private CompletableFuture<HttpResult<byte[]>> forEachCollectionFuture(int userid, HttpSimpleRequest req, java.net.http.HttpRequest.Builder builder, Iterator<InetSocketAddress> it) {
        if (!it.hasNext()) return CompletableFuture.completedFuture(null);
        InetSocketAddress addr = it.next();
        String url = "http://" + addr.getHostString() + ":" + addr.getPort() + (req.getPath() != null && !req.getPath().isEmpty() ? req.getPath() : "") + req.getRequestURI();
        builder.uri(URI.create(url));
        return httpClient.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).thenCompose(resp -> {
            if (resp.statusCode() != 200) return forEachCollectionFuture(userid, req, builder, it);
            HttpResult rs = new HttpResult();
            java.net.http.HttpHeaders hs = resp.headers();
            if (hs != null) {
                Map<String, List<String>> hm = hs.map();
                if (hm != null) {
                    for (Map.Entry<String, List<String>> en : hm.entrySet()) {
                        List<String> val = en.getValue();
                        if (val != null && val.size() == 1) {
                            rs.header(en.getKey(), val.get(0));
                        }
                    }
                }
            }
            rs.setResult(resp.body());
            return CompletableFuture.completedFuture(rs);
        });
    }
}
