/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import org.redkale.net.Context;
import org.redkale.net.Request;
import org.redkale.net.Response;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;

/**
 * 一个Service对应一个MessageProcessor
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class SncpMessageServlet extends MessageServlet {

    public SncpMessageServlet(
            MessageClient messageClient, Context context, Service service, SncpServlet servlet, String topic) {
        super(messageClient, context, service, servlet, topic);
    }

    @Override
    protected Request createRequest(Context context, MessageRecord message) {
        return new SncpMessageRequest((SncpContext) context, message);
    }

    @Override
    protected Response createResponse(Context context, Request request) {
        return new SncpMessageResponse(messageClient, (SncpContext) context, (SncpMessageRequest) request);
    }

    @Override
    protected void onError(Response response, MessageRecord message, Throwable t) {
        if (response != null) {
            ((SncpMessageResponse) response).finish(SncpResponse.RETCODE_ILLSERVICEID, null);
        }
    }
}
