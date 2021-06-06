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
import java.util.logging.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import static org.redkale.mq.MessageRecord.CTYPE_HTTP_REQUEST;

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

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected HttpMessageClient(MessageAgent messageAgent) {
        super(messageAgent);
        if (messageAgent != null) { // //RPC方式下无messageAgent
            this.respTopic = messageAgent.generateHttpRespTopic();
        }
    }

    //格式: http.req.user
    public String generateHttpReqTopic(String module) {
        return MessageAgent.generateHttpReqTopic(module);
    }

    //格式: http.req.user-n10
    public String generateHttpReqTopic(String module, String resname) {
        return MessageAgent.generateHttpReqTopic(module, resname);
    }

    public String generateHttpReqTopic(HttpSimpleRequest request, String path) {
        String module = request.getRequestURI();
        if (path != null && !path.isEmpty() && module.startsWith(path)) module = module.substring(path.length());
        module = module.substring(1); //去掉/
        module = module.substring(0, module.indexOf('/'));
        Map<String, String> headers = request.getHeaders();
        String resname = headers == null ? "" : headers.getOrDefault(Rest.REST_HEADER_RESOURCE_NAME, "");
        return MessageAgent.generateHttpReqTopic(module, resname);
    }

    public final void produceMessage(HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), 0, null, request, null);
    }

    public final void produceMessage(HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(generateHttpReqTopic(request, null), 0, null, request, counter);
    }

    public final void produceMessage(int userid, HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), userid, null, request, null);
    }

    public final void produceMessage(int userid, String groupid, HttpSimpleRequest request) {
        produceMessage(generateHttpReqTopic(request, null), userid, groupid, request, null);
    }

    public final void produceMessage(int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(generateHttpReqTopic(request, null), userid, groupid, request, counter);
    }

    public final void produceMessage(String topic, HttpSimpleRequest request) {
        produceMessage(topic, 0, null, request, null);
    }

    public final void produceMessage(String topic, HttpSimpleRequest request, AtomicLong counter) {
        produceMessage(topic, 0, null, request, counter);
    }

    public final void produceMessage(String topic, int userid, String groupid, HttpSimpleRequest request) {
        produceMessage(topic, userid, groupid, request, null);
    }

    public final void broadcastMessage(HttpSimpleRequest request) {
        broadcastMessage(generateHttpReqTopic(request, null), 0, null, request, null);
    }

    public final void broadcastMessage(HttpSimpleRequest request, AtomicLong counter) {
        broadcastMessage(generateHttpReqTopic(request, null), 0, null, request, counter);
    }

    public final void broadcastMessage(int userid, HttpSimpleRequest request) {
        broadcastMessage(generateHttpReqTopic(request, null), userid, null, request, null);
    }

    public final void broadcastMessage(int userid, String groupid, HttpSimpleRequest request) {
        broadcastMessage(generateHttpReqTopic(request, null), userid, groupid, request, null);
    }

    public final void broadcastMessage(int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        broadcastMessage(generateHttpReqTopic(request, null), userid, groupid, request, counter);
    }

    public final void broadcastMessage(String topic, HttpSimpleRequest request) {
        broadcastMessage(topic, 0, null, request, null);
    }

    public final void broadcastMessage(String topic, HttpSimpleRequest request, AtomicLong counter) {
        broadcastMessage(topic, 0, null, request, counter);
    }

    public final void broadcastMessage(String topic, int userid, String groupid, HttpSimpleRequest request) {
        broadcastMessage(topic, userid, groupid, request, null);
    }

    public <T> CompletableFuture<T> sendMessage(HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), 0, null, request, null).thenApply((HttpResult<byte[]> httbs) -> {
            if (httbs == null || httbs.getResult() == null) return null;
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public <T> CompletableFuture<T> sendMessage(int userid, HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), userid, null, request, null).thenApply((HttpResult<byte[]> httbs) -> {
            if (httbs == null || httbs.getResult() == null) return null;
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public <T> CompletableFuture<T> sendMessage(int userid, String groupid, HttpSimpleRequest request, Type type) {
        return sendMessage(generateHttpReqTopic(request, null), userid, groupid, request, null).thenApply((HttpResult<byte[]> httbs) -> {
            if (httbs == null || httbs.getResult() == null) return null;
            return JsonConvert.root().convertFrom(type, httbs.getResult());
        });
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), 0, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(generateHttpReqTopic(request, null), 0, null, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), userid, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(generateHttpReqTopic(request, null), userid, groupid, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(generateHttpReqTopic(request, null), userid, groupid, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, HttpSimpleRequest request) {
        return sendMessage(topic, 0, null, request, null);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, HttpSimpleRequest request, AtomicLong counter) {
        return sendMessage(topic, 0, null, request, counter);
    }

    public final CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, int userid, String groupid, HttpSimpleRequest request) {
        return sendMessage(topic, userid, null, request, (AtomicLong) null);
    }

    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        MessageRecord message = createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, HttpSimpleRequestCoder.getInstance().encode(request));
        message.userid(userid).groupid(groupid);
        //if (finest) logger.log(Level.FINEST, "HttpMessageClient.sendMessage: " + message);
        return sendMessage(message, true, counter).thenApply(r -> r.decodeContent(HttpResultCoder.getInstance()));
    }

    public void broadcastMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        MessageRecord message = createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, HttpSimpleRequestCoder.getInstance().encode(request));
        message.userid(userid).groupid(groupid);
        sendMessage(message, false, counter);
    }

    public void produceMessage(String topic, int userid, String groupid, HttpSimpleRequest request, AtomicLong counter) {
        MessageRecord message = createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, HttpSimpleRequestCoder.getInstance().encode(request));
        message.userid(userid).groupid(groupid);
        sendMessage(message, false, counter);
    }

    @Override
    protected MessageProducers getProducer() {
        return messageAgent.getHttpProducer();
    }
}
