/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import org.redkale.convert.bson.BsonWriter;
import org.redkale.net.Response;
import org.redkale.net.sncp.*;
import static org.redkale.net.sncp.SncpRequest.HEADER_SIZE;
import org.redkale.util.ObjectPool;

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

    protected MessageRecord message;

    protected MessageProducer producer;

    protected Runnable callback;

    public SncpMessageResponse(SncpContext context, SncpMessageRequest request, Runnable callback, ObjectPool<Response> responsePool, MessageProducer producer) {
        super(context, request, responsePool);
        this.message = request.message;
        this.callback = callback;
        this.producer = producer;
    }

    public SncpMessageResponse(SncpContext context, MessageRecord message, Runnable callback, ObjectPool<Response> responsePool, MessageProducer producer) {
        super(context, new SncpMessageRequest(context, message), responsePool);
        this.message = message;
        this.callback = callback;
        this.producer = producer;
    }

    @Override
    public void finish(final int retcode, final BsonWriter out) {
        if (callback != null) callback.run();
        if (out == null) {
            final byte[] result = new byte[SncpRequest.HEADER_SIZE];
            fillHeader(ByteBuffer.wrap(result), 0, retcode);
            producer.apply(new MessageRecord(message.getSeqid(), message.getResptopic(), null, (byte[]) null));
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final byte[] result = out.toArray();
        fillHeader(ByteBuffer.wrap(result), respBodyLength - HEADER_SIZE, retcode);
        producer.apply(new MessageRecord(message.getSeqid(), message.getResptopic(), null, result));
    }
}
