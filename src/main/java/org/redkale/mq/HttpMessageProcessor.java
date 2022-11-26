/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.boot.NodeHttpServer;
import org.redkale.net.http.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 一个Service对应一个MessageProcessor
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

    protected HttpMessageClient messageClient;

    protected final MessageProducers producers;

    protected final NodeHttpServer server;

    protected final Service service;

    protected final HttpServlet servlet;

    protected final boolean multiConsumer;

    protected final String restModule; //  前后有/, 例如: /user/

    protected final String multiModule; //  前后有/, 例如: /userstat/

    protected ThreadLocal<ObjectPool<HttpMessageResponse>> respPoolThreadLocal;

    protected final Supplier<HttpMessageResponse> respSupplier;

    protected final Consumer<HttpMessageResponse> respConsumer;

    protected CountDownLatch cdl;

    protected long startTime;

    protected final Runnable innerCallback = () -> {
        if (cdl != null) cdl.countDown();
    };

    public HttpMessageProcessor(Logger logger, HttpMessageClient messageClient, MessageProducers producers, NodeHttpServer server, Service service, HttpServlet servlet) {
        this.logger = logger;
        this.messageClient = messageClient;
        this.producers = producers;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
        MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
        this.multiConsumer = mmc != null;
        this.restModule = "/" + Rest.getRestModule(service) + "/";
        this.multiModule = mmc != null ? ("/" + mmc.module() + "/") : null;
        this.respSupplier = () -> respPoolThreadLocal.get().get();
        this.respConsumer = resp -> respPoolThreadLocal.get().accept(resp);
        this.respPoolThreadLocal = ThreadLocal.withInitial(() -> ObjectPool.createUnsafePool(Utility.cpus(),
            ps -> new HttpMessageResponse(server.getHttpServer().getContext(), messageClient, respSupplier, respConsumer), HttpMessageResponse::prepare, HttpMessageResponse::recycle));
    }

    @Override
    public void begin(final int size, long starttime) {
        this.startTime = starttime;
        this.cdl = size > 1 ? new CountDownLatch(size) : null;
    }

    @Override
    public void process(final MessageRecord message, final Runnable callback) {
        execute(message, innerCallback);
    }

    private void execute(final MessageRecord message, final Runnable callback) {
        HttpMessageRequest request = null;
        try {
            Traces.currTraceid(message.getTraceid());
            long now = System.currentTimeMillis();
            long cha = now - message.createTime;
            long e = now - startTime;
            if (multiConsumer) message.setRespTopic(null); //不容许有响应

            HttpMessageResponse response = respSupplier.get();
            request = response.request();
            response.prepare(message, callback, producers.getProducer(message));
            if (multiConsumer) request.setRequestURI(request.getRequestURI().replaceFirst(this.multiModule, this.restModule));

            server.getHttpServer().getContext().execute(servlet, request, response);
            long o = System.currentTimeMillis() - now;
            if ((cha > 1000 || e > 100 || o > 1000) && logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "HttpMessageProcessor.process (mqs.delays = " + cha + " ms, mqs.blocks = " + e + " ms, mqs.executes = " + o + " ms) message: " + message);
            } else if ((cha > 50 || e > 10 || o > 50) && logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "HttpMessageProcessor.process (mq.delays = " + cha + " ms, mq.blocks = " + e + " ms, mq.executes = " + o + " ms) message: " + message);
            } else if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "HttpMessageProcessor.process (mq.delay = " + cha + " ms, mq.block = " + e + " ms, mq.execute = " + o + " ms) message: " + message);
            }
        } catch (Throwable ex) {
            if (message.getRespTopic() != null && !message.getRespTopic().isEmpty()) {
                HttpMessageResponse.finishHttpResult(logger.isLoggable(Level.FINEST), request == null ? null : request.getRespConvert(),
                    null, message, callback, messageClient, producers.getProducer(message), message.getRespTopic(), new HttpResult().status(500));
            }
            logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex instanceof CompletionException ? ((CompletionException) ex).getCause() : ex);
        }
    }

    @Override
    public void commit() {
        if (this.cdl != null) {
            try {
                this.cdl.await(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " commit error, restmodule=" + this.restModule, ex);
            }
            this.cdl = null;
        }
    }

    public MessageProducers getProducer() {
        return producers;
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
