/*
 *
 */
package org.redkale.mq.spi;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import org.redkale.cluster.HttpRpcClient;
import static org.redkale.mq.spi.MessageRecord.CTYPE_HTTP_REQUEST;
import org.redkale.net.http.HttpResult;
import org.redkale.net.http.WebRequest;

/**
 *
 * @author zhangjx
 */
final class HttpRpcMessageClient extends HttpRpcClient {

    private final MessageCoder<WebRequest> requestCoder = WebRequestCoder.getInstance();

    private final String nodeid;

    private final MessageClient messageClient;

    public HttpRpcMessageClient(MessageClient messageClient, final String nodeid) {
        this.messageClient = messageClient;
        this.nodeid = nodeid;
    }

    @Override
    public CompletableFuture<HttpResult<byte[]>> sendMessage(String topic, Serializable userid, String groupid, WebRequest request) {
        MessageRecord message = messageClient.createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, request.getTraceid(), requestCoder.encode(request));
        message.userid(userid).groupid(groupid);
        return messageClient.sendMessage(message).thenApply(r -> r.decodeContent(HttpResultCoder.getInstance()));
    }

    @Override
    public CompletableFuture<Void> produceMessage(String topic, Serializable userid, String groupid, WebRequest request) {
        MessageRecord message = messageClient.createMessageRecord(CTYPE_HTTP_REQUEST, topic, null, request.getTraceid(), requestCoder.encode(request));
        message.userid(userid).groupid(groupid);
        return messageClient.produceMessage(message);
    }

    @Override
    protected String getNodeid() {
        return nodeid;
    }

}
