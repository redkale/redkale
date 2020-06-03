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

    protected final Logger logger;

    protected final MessageProducer producer;

    protected final NodeHttpServer server;

    protected final Service service;

    protected final HttpServlet servlet;

    public HttpMessageProcessor(Logger logger, MessageProducer producer, NodeHttpServer server, Service service, HttpServlet servlet) {
        this.logger = logger;
        this.producer = producer;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
    }

    @Override
    public void process(MessageRecord message) {
        try {
            HttpContext context = server.getHttpServer().getContext();
            HttpMessageRequest request = new HttpMessageRequest(context, message);
            HttpMessageResponse response = new HttpMessageResponse(context, request, null, null, producer);
            servlet.execute(request, response);
        } catch (Exception ex) {
            HttpMessageResponse.finishHttpResult(message, producer, message.getResptopic(), new HttpResult().status(500));
            logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex);
        }
    }

    public MessageProducer getProducer() {
        return producer;
    }

    public NodeHttpServer getServer() {
        return server;
    }

    public Service getService() {
        return service;
    }

    public HttpServlet getServlet() {
        return servlet;
    }

}
