/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
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

    protected final boolean finest;

    protected final Logger logger;

    protected final MessageProducer producer;

    protected final NodeHttpServer server;

    protected final ThreadPoolExecutor workExecutor;

    protected final Service service;

    protected final HttpServlet servlet;

    protected final boolean multiconsumer;

    protected final String restmodule; //  前后有/, 例如: /user/

    protected final String multimodule; //  前后有/, 例如: /userstat/

    protected CountDownLatch cdl;

    protected final Runnable innerCallback = () -> {
        if (cdl != null) cdl.countDown();
    };

    public HttpMessageProcessor(Logger logger, MessageProducer producer, NodeHttpServer server, Service service, HttpServlet servlet) {
        this.logger = logger;
        this.finest = logger.isLoggable(Level.FINEST);
        this.producer = producer;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
        MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
        this.multiconsumer = mmc != null;
        this.restmodule = "/" + Rest.getRestModule(service) + "/";
        this.multimodule = mmc != null ? ("/" + mmc.module() + "/") : null;
        this.workExecutor = server.getServer().getWorkExecutor();
    }

    @Override
    public void begin(final int size) {
        if (this.workExecutor != null) this.cdl = new CountDownLatch(size);
    }

    @Override
    public void process(final MessageRecord message, final Runnable callback) {
        if (this.workExecutor == null) {
            execute(message, innerCallback);
        } else {
            this.workExecutor.execute(() -> execute(message, innerCallback));
        }
    }

    private void execute(final MessageRecord message, final Runnable callback) {
        try {
            if (finest) logger.log(Level.FINEST, "HttpMessageProcessor.process message: " + message);
            if (multiconsumer) message.setResptopic(null); //不容许有响应
            HttpContext context = server.getHttpServer().getContext();
            HttpMessageRequest request = new HttpMessageRequest(context, message);
            if (multiconsumer) {
                request.setRequestURI(request.getRequestURI().replaceFirst(this.multimodule, this.restmodule));
            }
            HttpMessageResponse response = new HttpMessageResponse(context, request, callback, null, null, producer);
            servlet.execute(request, response);
        } catch (Exception ex) {
            if (message.getResptopic() != null && !message.getResptopic().isEmpty()) {
                HttpMessageResponse.finishHttpResult(finest, message, callback, producer, message.getResptopic(), new HttpResult().status(500));
            }
            logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex);
        }
    }

    @Override
    public void commit() {
        if (this.cdl != null) {
            try {
                this.cdl.await(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
            }
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
