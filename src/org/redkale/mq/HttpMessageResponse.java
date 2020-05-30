/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.util.function.*;
import org.redkale.convert.Convert;
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

    protected BiConsumer<MessageRecord, byte[]> resultConsumer;

    public HttpMessageResponse(HttpContext context, HttpMessageRequest request,
        ObjectPool<Response> responsePool, HttpResponseConfig config) {
        super(context, request, responsePool, config);
    }

    public HttpMessageResponse(HttpContext context, HttpSimpleRequest req, HttpResponseConfig config) {
        super(context, new HttpMessageRequest(context, req), null, config);
    }

    public HttpMessageResponse resultConsumer(MessageRecord message, BiConsumer<MessageRecord, byte[]> resultConsumer) {
        this.message = message;
        this.resultConsumer = resultConsumer;
        return this;
    }

    @Override
    public void finishJson(org.redkale.service.RetResult ret) {

    }

    @Override
    public void finish(String obj) {

    }

    @Override
    public void finish(final Convert convert, final Object obj) {

    }

    @Override
    public void finish(final byte[] bs) {

    }

    @Override
    public void finish(ByteBuffer buffer) {

    }

    @Override
    public void finish(ByteBuffer... buffers) {

    }

    @Override
    public void finish(int status, String message) {

    }
}
