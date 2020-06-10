/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import org.redkale.convert.*;
import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.util.ObjectPool;

/**
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpMessageResponse extends HttpResponse {

    protected MessageRecord message;

    protected MessageProducer producer;

    public HttpMessageResponse(HttpContext context, HttpMessageRequest request,
        ObjectPool<Response> responsePool, HttpResponseConfig config, MessageProducer producer) {
        super(context, request, responsePool, config);
        this.message = request.message;
        this.producer = producer;
    }

    public HttpMessageResponse(HttpContext context, MessageRecord message, HttpResponseConfig config, MessageProducer producer) {
        super(context, new HttpMessageRequest(context, message), null, config);
        this.message = message;
        this.producer = producer;
    }

    public void finishHttpResult(HttpResult result) {
        finishHttpResult(this.message, this.producer, message.getResptopic(), result);
    }

    public static void finishHttpResult(MessageRecord msg, MessageProducer producer, String resptopic, HttpResult result) {
        if (resptopic == null || resptopic.isEmpty()) return;
        ConvertType format = result.convert() == null ? null : result.convert().getFactory().getConvertType();
        byte[] content = HttpResultCoder.getInstance().encode(result);
        producer.apply(new MessageRecord(msg.getSeqid(), format, resptopic, null, content));
    }

    @Override
    public void finishJson(org.redkale.service.RetResult ret) {
        if (message.isEmptyResptopic()) return;
        finishHttpResult(new HttpResult(ret.clearConvert(), ret));
    }

    @Override
    public void finish(String obj) {
        if (message.isEmptyResptopic()) return;
        finishHttpResult(new HttpResult(obj == null ? "" : obj));
    }

    @Override
    public void finish404() {
        finish(404, null);
    }

    @Override
    public void finish(int status, String message) {
        if (this.message.isEmptyResptopic()) return;
        finishHttpResult(new HttpResult(message == null ? "" : message).status(status));
    }

    @Override
    public void finish(final Convert convert, HttpResult result) {
        if (message.isEmptyResptopic()) return;
        if (convert != null) result.convert(convert);
        finishHttpResult(result);
    }

    @Override
    public void finish(final byte[] bs) {
        if (message.isEmptyResptopic()) return;
        finishHttpResult(new HttpResult(bs));
    }

    @Override
    public void finish(final String contentType, final byte[] bs) {
        if (message.isEmptyResptopic()) return;
        finishHttpResult(new HttpResult(bs).contentType(contentType));
    }

    @Override
    public void finish(boolean kill, ByteBuffer buffer) {
        if (message.isEmptyResptopic()) return;
        byte[] bs = new byte[buffer.remaining()];
        buffer.get(bs);
        finishHttpResult(new HttpResult(bs));
    }

    @Override
    public void finish(boolean kill, ByteBuffer... buffers) {
        if (message.isEmptyResptopic()) return;
        int size = 0;
        for (ByteBuffer buf : buffers) {
            size += buf.remaining();
        }
        byte[] bs = new byte[size];
        int index = 0;
        for (ByteBuffer buf : buffers) {
            int r = buf.remaining();
            buf.get(bs, index, r);
            index += r;
        }
        finishHttpResult(new HttpResult(bs));
    }

}
