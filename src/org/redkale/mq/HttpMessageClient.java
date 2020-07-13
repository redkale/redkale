/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.convert.ConvertType;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;

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
public class HttpMessageClient extends MessageClient {

    protected HttpMessageClient(MessageAgent messageAgent) {
        super(messageAgent);
        this.respTopic = messageAgent.generateHttpRespTopic();
    }

    //格式: http.req.user
    public String generateHttpReqTopic(String module) {
        if (messageAgent == null) return null;  //RPC方式下无messageAgent
        return messageAgent.generateHttpReqTopic(module);
    }

    //格式: http.req.user-n10
    public String generateHttpReqTopic(String module, String resname) {
        if (messageAgent == null) return null;  //RPC方式下无messageAgent
        return messageAgent.generateHttpReqTopic(module, resname);
    }

    public String generateHttpReqTopic(HttpSimpleRequest request, String path) {
        if (messageAgent == null) return null;  //RPC方式下无messageAgent
        String module = request.getRequestURI();
        if (path != null && !path.isEmpty() && module.startsWith(path)) module = module.substring(path.length());
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = request.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        return messageAgent.generateHttpReqTopic(module, resname);
    }

    public boolean isRetSuccess(HttpResult<byte[]> result) {
        return result != null && (result.getStatus() == 0 || result.getStatus() == 200) && result.getHeader("retcode") == null;
    }

    public final void produceMessage(HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), ConvertType.JSON, 0, null, request, null);
    }

    public final void produceMessage(HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(generateHttpReqTopic(request, null), ConvertType.JSON, 0, null, request, counter);
    }

    public final void produceMessage(int userid, HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, null, request, null);
    }

    public final void produceMessage(int userid, String groupid, HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, groupid, request, null);
    }

    public final void produceMessage(int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, groupid, request, counter);
    }

    public final void produceMessage(String topic, HttpSimpleRequest request) {
        produceMessage(topic, ConvertType.JSON, 0, null, request, null);
    }

    public final void produceMessage(String topic, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(topic, ConvertType.JSON, 0, null, request, counter);
    }

    public final void produceMessage(String topic, ConvertType convertType, HttpSimpleRequest request) {
        produceMessage(topic, convertType, 0, null, request, null);
    }

    public final void produceMessage(String topic, ConvertType convertType, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(topic, convertType, 0, null, request, counter);
    }

    public final void produceMessage(String topic, int userid, String groupid, HttpSimpleRequest request) {
        produceMessage(topic, ConvertType.JSON, userid, groupid, request, null);
    }

    public final void produceMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(topic, ConvertType.JSON, userid, groupid, request, counter);
    }

    public final void produceMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request) {
        produceMessage(topic, convertType, userid, groupid, request, null);
    }

    public final <T> CompletableFuture<T> sendMessage(HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, 0, null, request, null).thenApply((HttpResult<byte[]> httbs) -> {
            if (httbs == null || httbs.getResult() == null) return null;
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public final <T> CompletableFuture<T> sendMessage(int userid, HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, null, request, null).thenApply((HttpResult<byte[]> httbs) -> {
            if (httbs == null || httbs.getResult() == null) return null;
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, 0, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, 0, null, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, groupid, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(generateHttpReqTopic(request, null), ConvertType.JSON, userid, groupid, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, HttpSimpleRequest request) {
        return sendMessage(topic, ConvertType.JSON, 0, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(topic, ConvertType.JSON, 0, null, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, ConvertType convertType, HttpSimpleRequest request) {
        return sendMessage(topic, convertType, 0, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, ConvertType convertType, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(topic, convertType, 0, null, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, int userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(topic, ConvertType.JSON, userid, groupid, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(topic, ConvertType.JSON, userid, groupid, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(topic, convertType, userid, groupid, request, null);
    }

    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        MessageRecord message = new MessageRecord(convertType, topic, null, HttpSimpleRequestCoder.getInstance().encode(request));
        message.userid(userid).groupid(groupid);
        return sendMessage(message, true, counter).thenApply(r -> r.decodeContent(HttpResultCoder.getInstance()));
    }

    public void produceMessage(String topic, ConvertType convertType, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        MessageRecord message = new MessageRecord(convertType, topic, null, HttpSimpleRequestCoder.getInstance().encode(request));
        message.userid(userid).groupid(groupid);
        sendMessage(message, false, counter);
    }

    @Override
    protected MessageProducer getProducer() {
        return messageAgent.getHttpProducer();
    }
}
