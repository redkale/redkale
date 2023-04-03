/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import static org.redkale.mq.MessageRecord.CTYPE_HTTP_RESULT;
import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;

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

    protected final HttpMessageClient messageClient;

    protected MessageRecord message;

    protected MessageClientProducer producer;

    protected Runnable callback;

    public HttpMessageResponse(HttpContext context, HttpMessageClient messageClient, final Supplier<HttpMessageResponse> respSupplier, final Consumer<HttpMessageResponse> respConsumer) {
        super(context, new HttpMessageRequest(context), null);
        this.responseSupplier = (Supplier) respSupplier;
        this.responseConsumer = (Consumer) respConsumer;
        this.messageClient = messageClient;
    }

    public void prepare(MessageRecord message, Runnable callback, MessageClientProducer producer) {
        ((HttpMessageRequest) request).prepare(message);
        this.message = message;
        this.callback = callback;
        this.producer = producer;
    }

    public HttpMessageRequest request() {
        return (HttpMessageRequest) request;
    }

    public void finishHttpResult(Type type, HttpResult result) {
        finishHttpResult(producer.logger.isLoggable(Level.FINEST), ((HttpMessageRequest) this.request).getRespConvert(), type, this.message, this.callback, this.messageClient, this.producer, message.getRespTopic(), result);
    }

    public void finishHttpResult(Type type, Convert respConvert, HttpResult result) {
        finishHttpResult(producer.logger.isLoggable(Level.FINEST), respConvert == null ? ((HttpMessageRequest) this.request).getRespConvert() : respConvert, type, this.message, this.callback, this.messageClient, this.producer, message.getRespTopic(), result);
    }

    public static void finishHttpResult(boolean finest, Convert respConvert, Type type, MessageRecord msg, Runnable callback, MessageClient messageClient, MessageClientProducer producer, String resptopic, HttpResult result) {
        if (callback != null) {
            callback.run();
        }
        if (resptopic == null || resptopic.isEmpty()) {
            return;
        }
        if (result.getResult() instanceof RetResult) {
            RetResult ret = (RetResult) result.getResult();
            //必须要塞入retcode， 开发者可以无需反序列化ret便可确定操作是否返回成功
            if (!ret.isSuccess()) {
                result.header("retcode", String.valueOf(ret.getRetcode()));
            }
        }
        if (result.convert() == null && respConvert != null) {
            result.convert(respConvert);
        }
        if (finest) {
            Object innerrs = result.getResult();
            if (innerrs instanceof byte[]) {
                innerrs = new String((byte[]) innerrs, StandardCharsets.UTF_8);
            }
            producer.logger.log(Level.FINEST, "HttpMessageResponse.finishHttpResult seqid=" + msg.getSeqid() + ", content: " + innerrs + ", status: " + result.getStatus() + ", headers: " + result.getHeaders());
        }
        byte[] content = HttpResultCoder.getInstance().encode(result);
        producer.apply(messageClient.createMessageRecord(msg.getSeqid(), CTYPE_HTTP_RESULT, resptopic, null, content));
    }

    @Override
    protected void prepare() {
        super.prepare();
    }

    @Override
    protected boolean recycle() {
        Supplier<Response> respSupplier = this.responseSupplier;
        Consumer<Response> respConsumer = this.responseConsumer;
        boolean rs = super.recycle();
        this.responseSupplier = respSupplier;
        this.responseConsumer = respConsumer;
        this.message = null;
        this.producer = null;
        this.callback = null;
        return rs;
    }

    @Override
    public void finishJson(final Object obj) {
        finishJson((JsonConvert) null, (Type) null, obj);
    }

    @Override
    public void finishJson(final Convert convert, final Object obj) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(obj.getClass(), convert, new HttpResult(obj));

    }

    @Override
    public void finishJson(final Type type, final Object obj) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(type, new HttpResult(obj));
    }

    @Override
    public void finishJson(final Convert convert, final Type type, final Object obj) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(type, convert, new HttpResult(obj));
    }

    @Override
    public void finish(Type type, org.redkale.service.RetResult ret) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finish(type, new HttpResult(ret));

    }

    @Override
    public void finish(final Convert convert, Type type, org.redkale.service.RetResult ret) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(type, convert, new HttpResult(ret));
    }

    @Override
    public void finish(final Convert convert, final Type type, Object obj) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(type, convert, new HttpResult(obj));
    }

    @Override
    public void finish(String obj) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(String.class, new HttpResult(obj == null ? "" : obj));
    }

    @Override
    public void finish304() {
        finish(304, null);
    }

    @Override
    public void finish404() {
        finish(404, null);
    }

    @Override
    public void finish500() {
        finish(500, null);
    }

    @Override
    public void finish504() {
        finish(504, null);
    }

    @Override
    public void finish(int status, String msg) {
        if (status > 400) {
            producer.logger.log(Level.WARNING, "HttpMessageResponse.finish status: " + status + ", uri: " + this.request.getRequestURI() + ", message: " + this.message);
        } else if (producer.logger.isLoggable(Level.FINEST)) {
            producer.logger.log(Level.FINEST, "HttpMessageResponse.finish status: " + status);
        }
        if (this.message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        finishHttpResult(String.class, new HttpResult(msg == null ? "" : msg).status(status));
    }

    @Override
    public void finish(final Convert convert, Type type, HttpResult result) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        if (convert != null) {
            result.convert(convert);
        }
        finishHttpResult(type, result);
    }

    @Override
    public void finish(boolean kill, final byte[] bs, int offset, int length) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        if (offset == 0 && bs.length == length) {
            finishHttpResult(null, new HttpResult(bs));
        } else {
            finishHttpResult(null, new HttpResult(Arrays.copyOfRange(bs, offset, offset + length)));
        }
    }

    @Override
    public void finish(boolean kill, final String contentType, final byte[] bs, int offset, int length) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
        finishHttpResult(null, new HttpResult(rs).contentType(contentType));
    }

    @Override
    protected <A> void finish(boolean kill, final String contentType, final byte[] bs, int offset, int length, Consumer<A> consumer, A attachment) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
        finishHttpResult(null, new HttpResult(rs).contentType(contentType));
    }

    @Override
    public void finishBuffer(boolean kill, ByteBuffer buffer) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        byte[] bs = new byte[buffer.remaining()];
        buffer.get(bs);
        finishHttpResult(null, new HttpResult(bs));
    }

    @Override
    public void finishBuffers(boolean kill, ByteBuffer... buffers) {
        if (message.isEmptyRespTopic()) {
            if (callback != null) {
                callback.run();
            }
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
        finishHttpResult(null, new HttpResult(bs));
    }

}
