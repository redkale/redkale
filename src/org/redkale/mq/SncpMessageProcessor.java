/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.logging.*;
import org.redkale.boot.NodeSncpServer;
import org.redkale.net.sncp.*;
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
public class SncpMessageProcessor implements MessageProcessor {

    protected final Logger logger;

    protected final MessageProducer producer;

    protected final NodeSncpServer server;

    protected final Service service;

    protected final SncpServlet servlet;

    public SncpMessageProcessor(Logger logger, MessageProducer producer, NodeSncpServer server, Service service, SncpServlet servlet) {
        this.logger = logger;
        this.producer = producer;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
    }

    @Override
    public void process(MessageRecord message, Runnable callback) {
        SncpContext context = server.getSncpServer().getContext();
        SncpMessageRequest request = new SncpMessageRequest(context, message);
        SncpMessageResponse response = new SncpMessageResponse(context, request, callback, null, producer);
        try {
            servlet.execute(request, response);
        } catch (Exception ex) {
            response.finish(SncpResponse.RETCODE_ILLSERVICEID, null);
            logger.log(Level.SEVERE, SncpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex);
        }
    }

    public MessageProducer getProducer() {
        return producer;
    }

    public NodeSncpServer getServer() {
        return server;
    }

    public Service getService() {
        return service;
    }

    public SncpServlet getServlet() {
        return servlet;
    }

}
