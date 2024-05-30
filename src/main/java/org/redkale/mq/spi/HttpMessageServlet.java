/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.util.logging.*;
import org.redkale.net.Context;
import org.redkale.net.Request;
import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.service.Service;

/**
 * 一个Service对应一个MessageProcessor
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class HttpMessageServlet extends MessageServlet {

    public HttpMessageServlet(
            MessageClient messageClient, Context context, Service service, HttpServlet servlet, String topic) {
        super(messageClient, context, service, servlet, topic);
    }

    @Override
    protected Request createRequest(Context context, MessageRecord message) {
        return new HttpMessageRequest((HttpContext) context, message);
    }

    @Override
    protected Response createResponse(Context context, Request request) {
        return new HttpMessageResponse(messageClient, (HttpContext) context, (HttpMessageRequest) request);
    }

    @Override
    protected void onError(Response response, MessageRecord message, Throwable t) {
        if (message.getRespTopic() != null && !message.getRespTopic().isEmpty()) {
            HttpMessageRequest request = ((HttpMessageResponse) response).request();
            HttpMessageResponse.finishHttpResult(
                    logger.isLoggable(Level.FINEST),
                    request == null ? null : request.getRespConvert(),
                    null,
                    message,
                    messageClient,
                    message.getRespTopic(),
                    new HttpResult().status(500));
        }
    }
}
