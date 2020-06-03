/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.logging.*;
import org.redkale.boot.NodeHttpServer;
import org.redkale.net.http.*;
import org.redkale.service.Service;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpMessageProcessor implements MessageProcessor {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final MessageProducer producer;

    protected final NodeHttpServer ns;

    protected final Service service;

    protected final HttpServlet servlet;

    public HttpMessageProcessor(MessageProducer producer, NodeHttpServer ns, Service service, HttpServlet servlet) {
        this.producer = producer;
        this.ns = ns;
        this.service = service;
        this.servlet = servlet;
    }

    @Override
    public void process(MessageRecord message) {
        try {
            HttpContext context = ns.getHttpServer().getContext();
            HttpMessageRequest request = new HttpMessageRequest(context, message);
            HttpMessageResponse response = new HttpMessageResponse(context, request, null, null, producer);
            servlet.execute(request, response);
        } catch (Exception ex) {
            HttpMessageResponse.finishHttpResult(producer, message.getResptopic(), new HttpResult().status(500));
            logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex);
        }
    }

}
