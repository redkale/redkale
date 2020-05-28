/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.util.function.*;
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
 */
public class SncpMessageResponse extends SncpResponse {

    protected MessageRecord message;

    protected BiConsumer<MessageRecord, byte[]> resultConsumer;

    public SncpMessageResponse(SncpContext context, SncpMessageRequest request, ObjectPool<Response> responsePool) {
        super(context, request, responsePool);
    }

    public SncpMessageResponse resultConsumer(MessageRecord message, BiConsumer<MessageRecord, byte[]> resultConsumer) {
        this.message = message;
        this.resultConsumer = resultConsumer;
        return this;
    }

    @Override
    public void finish(final int retcode, final BsonWriter out) {
        if (out == null) {
            final byte[] result = new byte[SncpRequest.HEADER_SIZE];
            fillHeader(ByteBuffer.wrap(result), 0, retcode);
            resultConsumer.accept(message, result);
            return;
        }
        final int respBodyLength = out.count(); //body总长度
        final byte[] result = out.toArray();
        fillHeader(ByteBuffer.wrap(result), respBodyLength - HEADER_SIZE, retcode);
        resultConsumer.accept(message, result);
    }
}
