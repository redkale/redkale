/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.redkale.convert.*;
import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
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

    protected boolean finest;

    protected Runnable callback;

    public HttpMessageResponse(HttpContext context, HttpMessageRequest request, Runnable callback,
        ObjectPool<Response> responsePool, HttpResponseConfig config, MessageProducer producer) {
        super(context, request, responsePool, config);
        this.message = request.message;
        this.callback = callback;
        this.producer = producer;
        this.finest = producer.logger.isLoggable(Level.FINEST);
    }

    public HttpMessageResponse(HttpContext context, MessageRecord message, Runnable callback, HttpResponseConfig config, MessageProducer producer) {
        super(context, new HttpMessageRequest(context, message), null, config);
        this.message = message;
        this.callback = callback;
        this.producer = producer;
    }

    public void finishHttpResult(HttpResult result) {
        finishHttpResult(this.finest, this.message, this.callback, this.producer, message.getResptopic(), result);
    }

    public static void finishHttpResult(boolean finest, MessageRecord msg, Runnable callback, MessageProducer producer, String resptopic, HttpResult result) {
        if (callback != null) callback.run();
        if (resptopic == null || resptopic.isEmpty()) return;
        if (result.getResult() instanceof RetResult) {
            RetResult ret = (RetResult) result.getResult();
            //必须要塞入retcode， 开发者可以无需反序列化ret便可确定操作是否返回成功
            if (!ret.isSuccess()) result.header("retcode", String.valueOf(ret.getRetcode()));
        }
        ConvertType format = result.convert() == null ? null : result.convert().getFactory().getConvertType();
        if (finest) {
            Object innerrs = result.getResult();
            if (innerrs instanceof byte[]) innerrs = new String((byte[]) innerrs, StandardCharsets.UTF_8);
            producer.logger.log(Level.FINEST, "HttpMessageProcessor.process seqid=" + msg.getSeqid() + ", content: " + innerrs + ", status: " + result.getStatus() + ", headers: " + result.getHeaders());
        }
        byte[] content = HttpResultCoder.getInstance().encode(result);
        producer.apply(new MessageRecord(msg.getSeqid(), format, resptopic, null, content));
    }

    @Override
    public void finishJson(org.redkale.service.RetResult ret) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        finishHttpResult(new HttpResult(ret.clearConvert(), ret));
    }

    @Override
    public void finish(String obj) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        finishHttpResult(new HttpResult(obj == null ? "" : obj));
    }

    @Override
    public void finish404() {
        finish(404, null);
    }

    @Override
    public void finish(int status, String message) {
        if (finest) producer.logger.log(Level.FINEST, "HttpMessageResponse.finish status: " + status);
        if (this.message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        finishHttpResult(new HttpResult(message == null ? "" : message).status(status));
    }

    @Override
    public void finish(final Convert convert, HttpResult result) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        if (convert != null) result.convert(convert);
        finishHttpResult(result);
    }

    @Override
    public void finish(final byte[] bs) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        finishHttpResult(new HttpResult(bs));
    }

    @Override
    public void finish(final String contentType, final byte[] bs) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        finishHttpResult(new HttpResult(bs).contentType(contentType));
    }

    @Override
    public void finish(boolean kill, ByteBuffer buffer) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
        byte[] bs = new byte[buffer.remaining()];
        buffer.get(bs);
        finishHttpResult(new HttpResult(bs));
    }

    @Override
    public void finish(boolean kill, ByteBuffer... buffers) {
        if (message.isEmptyResptopic()) {
            if (callback != null) callback.run();
            return;
        }
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
