/*
 *
 */
package org.redkale.mq;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import org.redkale.cluster.HttpRpcClient;
import static org.redkale.mq.MessageRecord.CTYPE_HTTP_REQUEST;
import org.redkale.net.http.HttpResult;
import org.redkale.net.http.HttpSimpleRequest;

/**
 *
 * @author zhangjx
 */
final class HttpRpcMessageClient extends HttpRpcClient {

    private final MessageCoder<HttpSimpleRequest> requestCoder = HttpSimpleRequestCoder.getInstance();

    private final int nodeid;

    private final MessageClient messageClient;

    public HttpRpcMessageClient(MessageClient messageClient, final int nodeid) {
        this.messageClient = messageClient;
        this.nodeid = nodeid;
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        MessageRecord message = messageClient.createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, request.getTraceid(), requestCoder.encode(request));
        message.userid(userid).groupid(groupid);
        return messageClient.sendMessage(message).thenApply(r -> r.decodeContent(HttpResultCoder.getInstance()));
    }

    @Override
    public void produceMessage(String topic, Serializable userid, String groupid, HttpSimpleRequest request) {
        MessageRecord message = messageClient.createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, request.getTraceid(), requestCoder.encode(request));
        message.userid(userid).groupid(groupid);
        messageClient.produceMessage(message);
    }

    @Override
    protected int getNodeid() {
        return nodeid;
    }

}
