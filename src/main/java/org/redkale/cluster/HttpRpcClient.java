/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import org.redkale.util.RedkaleException;

/**
 * 不依赖MessageRecord则可兼容RPC方式
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public abstract class HttpRpcClient implements ClusterRpcClient<HttpSimpleRequest, HttpResult<byte[]>> {

    @Override
    public final CompletableFuture<Void> produceMessage(HttpSimpleRequest request) {
        return produceMessage(generateHttpReqTopic(request, null), 0, null, request);
    }

    public final CompletableFuture<Void> produceMessage(Serializable userid, HttpSimpleRequest request) {
        return produceMessage(generateHttpReqTopic(request, null), userid, null, request);
    }

    public final CompletableFuture<Void> produceMessage(Serializable userid, String groupid, HttpSimpleRequest request) {
        return produceMessage(generateHttpReqTopic(request, null), userid, groupid, request);
    }

    public final CompletableFuture<Void> produceMessage(String topic, HttpSimpleRequest request) {
        return produceMessage(topic, 0, null, request);
    }

    @Override
    public final CompletableFuture<HttpResult<byte[]>> sendMessage(HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), 0, null, request);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(Serializable userid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), userid, null, request);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(Serializable userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), userid, groupid, request);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, HttpSimpleRequest request) {
        return sendMessage(topic, 0, null, request);
    }

    public <T> CompletableFuture<T> sendMessage(HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), 0, null, request).thenApply((HttpResult<byte[]> httbs) -> {
            if (!httbs.isSuccess()) {
                throw new RedkaleException(httbs.getHeader("retinfo", "Internal Server Error"));
            }
            if (httbs.getResult() == null) {
                return null;
            }
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public <T> CompletableFuture<T> sendMessage(Serializable userid, HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), userid, null, request).thenApply((HttpResult<byte[]> httbs) -> {
            if (!httbs.isSuccess()) {
                throw new RedkaleException(httbs.getHeader("retinfo", "Internal Server Error"));
            }
            if (httbs.getResult() == null) {
                return null;
            }
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public <T> CompletableFuture<T> sendMessage(Serializable userid, String groupid, HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), userid, groupid, request).thenApply((HttpResult<byte[]> httbs) -> {
            if (!httbs.isSuccess()) {
                throw new RedkaleException(httbs.getHeader("retinfo", "Internal Server Error"));
            }
            if (httbs.getResult() == null) {
                return null;
            }
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    //格式: http.req.user
    public String generateHttpReqTopic(String module) {
        return Rest.generateHttpReqTopic(module, getNodeid());
    }

    //格式: http.req.user-n10
    public String generateHttpReqTopic(String module, String resname) {
        return Rest.generateHttpReqTopic(module, resname, getNodeid());
    }

    public String generateHttpReqTopic(HttpSimpleRequest request, String path) {
        String module = request.getPath();
        if (path != null && !path.isEmpty() && module.startsWith(path)) {
            module = module.substring(path.length());
        }
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        String resname = request.getHeader(Rest.REST_HEADER_RESNAME, "");
        return Rest.generateHttpReqTopic(module, resname, getNodeid());
    }

    public abstract CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request);

    public abstract CompletableFuture<Void> produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request);

    protected abstract String getNodeid();

}
