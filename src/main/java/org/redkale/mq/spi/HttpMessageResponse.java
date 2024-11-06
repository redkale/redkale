/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.convert.Convert;
import static org.redkale.mq.spi.MessageRecord.CTYPE_HTTP_RESULT;
import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class HttpMessageResponse extends HttpResponse {

    protected MessageClient messageClient;

    protected MessageRecord message;

    public HttpMessageResponse(MessageClient messageClient, HttpContext context, HttpMessageRequest request) {
        super(context, request, null);
        this.messageClient = messageClient;
        this.message = request.message;
    }

    public void prepare(MessageRecord message) {
        ((HttpMessageRequest) request).prepare(message);
        this.message = message;
    }

    public HttpMessageRequest request() {
        return (HttpMessageRequest) request;
    }

    public void finishHttpResult(Type type, HttpResult result) {
        finishHttpResult(
                messageClient.logger.isLoggable(Level.FINEST),
                ((HttpMessageRequest) this.request).getRespConvert(),
                type,
                this.message,
                this.messageClient,
                message.getRespTopic(),
                result);
    }

    public void finishHttpResult(Convert respConvert, Type type, HttpResult result) {
        finishHttpResult(
                messageClient.logger.isLoggable(Level.FINEST),
                respConvert == null ? ((HttpMessageRequest) this.request).getRespConvert() : respConvert,
                type,
                this.message,
                this.messageClient,
                message.getRespTopic(),
                result);
    }

    public static void finishHttpResult(
            boolean finest,
            Convert respConvert,
            Type type,
            MessageRecord msg,
            MessageClient messageClient,
            String respTopic,
            HttpResult result) {
        if (respTopic == null || respTopic.isEmpty()) {
            return;
        }
        if (result.getResult() instanceof RetResult) {
            RetResult ret = (RetResult) result.getResult();
            // 必须要塞入retcode， 开发者可以无需反序列化ret便可确定操作是否返回成功
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
            messageClient.logger.log(
                    Level.FINEST,
                    "HttpMessageResponse.finishHttpResult seqid=" + msg.getSeqid() + ", content: " + innerrs
                            + ", status: " + result.getStatus() + ", headers: " + result.getHeaders());
        }
        byte[] content = HttpResultCoder.getInstance().encode(result);
        messageClient
                .getProducer()
                .apply(messageClient.createMessageRecord(msg.getSeqid(), CTYPE_HTTP_RESULT, respTopic, null, content));
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
        return rs;
    }

    @Override
    public void finish(final Convert convert, Type type, RetResult ret) {
        if (message.isEmptyRespTopic()) {
            return;
        }
        finishHttpResult(convert, type, new HttpResult(ret).convert(ret == null ? null : ret.convert()));
    }

    @Override
    public void finish(final Convert convert, Type type, HttpResult result) {
        if (message.isEmptyRespTopic()) {
            return;
        }
        if (convert != null) {
            result.convert(convert);
        }
        finishHttpResult(type, result);
    }

    @Override
    public void finishJson(final Convert convert, final Type type, final Object obj) {
        finish(convert, type, obj);
    }

    @Override
    public void finish(final Convert convert, final Type type, Object obj) {
        if (obj instanceof HttpResult) {
            finish(convert, type, (HttpResult) obj);
        } else if (obj instanceof RetResult) {
            finish(convert, type, (RetResult) obj);
        } else {
            if (message.isEmptyRespTopic()) {
                return;
            }
            finishHttpResult(convert, type, new HttpResult(obj));
        }
    }

    @Override
    public void finish(String obj) {
        if (message.isEmptyRespTopic()) {
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
            messageClient.logger.log(
                    Level.WARNING,
                    "HttpMessageResponse.finish status: " + status + ", uri: " + this.request.getRequestPath()
                            + ", message: " + this.message);
        } else if (messageClient.logger.isLoggable(Level.FINEST)) {
            messageClient.logger.log(Level.FINEST, "HttpMessageResponse.finish status: " + status);
        }
        if (this.message.isEmptyRespTopic()) {
            return;
        }
        finishHttpResult(String.class, new HttpResult(msg == null ? "" : msg).status(status));
    }

    @Override
    public void finish(boolean kill, final byte[] bs, int offset, int length) {
        if (message.isEmptyRespTopic()) {
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
            return;
        }
        byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
        finishHttpResult(null, new HttpResult(rs).contentType(contentType));
    }

    @Override
    protected <A> void finish(
            boolean kill,
            final String contentType,
            final byte[] bs,
            int offset,
            int length,
            Consumer<A> consumer,
            A attachment) {
        if (message.isEmptyRespTopic()) {
            return;
        }
        byte[] rs = (offset == 0 && bs.length == length) ? bs : Arrays.copyOfRange(bs, offset, offset + length);
        finishHttpResult(null, new HttpResult(rs).contentType(contentType));
    }
}
