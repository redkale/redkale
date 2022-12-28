/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.convert.bson.BsonWriter;
import static org.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import org.redkale.net.sncp.*;
import org.redkale.util.ByteArray;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class SncpMessageResponse extends SncpResponse {

    protected MessageClient messageClient;

    protected MessageRecord message;

    protected MessageProducer producer;

    protected Runnable callback;

    public SncpMessageResponse(SncpContext context, SncpMessageRequest request, Runnable callback, MessageClient messageClient, MessageProducer producer) {
        super(context, request);
        this.message = request.message;
        this.callback = callback;
        this.messageClient = messageClient;
        this.producer = producer;
    }

    public SncpMessageResponse(SncpContext context, MessageRecord message, Runnable callback, MessageClient messageClient, MessageProducer producer) {
        super(context, new SncpMessageRequest(context, message));
        this.message = message;
        this.callback = callback;
        this.messageClient = messageClient;
        this.producer = producer;
    }

    @Override
    public void finish(final int retcode, final BsonWriter out) {
        if (callback != null) {
            callback.run();
        }
        if (out == null) {
            final ByteArray result = new ByteArray(SncpRequest.HEADER_SIZE);
            fillHeader(result, 0, retcode);
            producer.apply(messageClient.createMessageRecord(message.getSeqid(), message.getRespTopic(), null, (byte[]) null));
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final ByteArray result = out.toByteArray();
        fillHeader(result, respBodyLength - HEADER_SIZE, retcode);
        producer.apply(messageClient.createMessageRecord(message.getSeqid(), message.getRespTopic(), null, result.getBytes()));
    }
}
