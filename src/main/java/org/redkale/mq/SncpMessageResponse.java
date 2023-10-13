/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.convert.bson.BsonWriter;
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

    public SncpMessageResponse(MessageClient messageClient, SncpContext context, SncpMessageRequest request) {
        super(context, request);
        this.messageClient = messageClient;
        this.message = request.message;
    }

    @Override
    public void finish(final int retcode, final BsonWriter out) {
        int headerSize = SncpHeader.calcHeaderSize(request);
        if (out == null) {
            final ByteArray result = new ByteArray(headerSize).putPlaceholder(headerSize);
            writeHeader(result, 0, retcode);
            messageClient.getProducer().apply(messageClient.createMessageRecord(message.getSeqid(), MessageRecord.CTYPE_BSON, message.getRespTopic(), null, (byte[]) null));
            return;
        }
        final ByteArray result = out.toByteArray();
        writeHeader(result, result.length() - headerSize, retcode);
        messageClient.getProducer().apply(messageClient.createMessageRecord(message.getSeqid(), MessageRecord.CTYPE_BSON, message.getRespTopic(), null, result.getBytes()));
    }
}
