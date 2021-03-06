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
import org.redkale.util.ObjectPool;

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

    protected final boolean finest;

    protected final boolean finer;

    protected final boolean fine;

    protected final Logger logger;

    protected HttpMessageClient messageClient;

    protected final MessageProducers producers;

    protected final NodeHttpServer server;

    protected final Service service;

    protected final HttpServlet servlet;

    protected final boolean multiconsumer;

    protected final String restmodule; //  前后有/, 例如: /user/

    protected final String multimodule; //  前后有/, 例如: /userstat/

    protected ThreadLocal<ObjectPool<HttpMessageResponse>> respPoolThreadLocal;

    protected final Supplier<HttpMessageResponse> respSupplier;

    protected final Consumer<HttpMessageResponse> respConsumer;

    protected CountDownLatch cdl;

    protected long starttime;

    protected final Runnable innerCallback = () -> {
        if (cdl != null) cdl.countDown();
    };

    public HttpMessageProcessor(Logger logger, HttpMessageClient messageClient, MessageProducers producers, NodeHttpServer server, Service service, HttpServlet servlet) {
        this.logger = logger;
        this.finest = logger.isLoggable(Level.FINEST);
        this.finer = logger.isLoggable(Level.FINER);
        this.fine = logger.isLoggable(Level.FINE);
        this.messageClient = messageClient;
        this.producers = producers;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
        MessageMultiConsumer mmc = service.getClass().getAnnotation(MessageMultiConsumer.class);
        this.multiconsumer = mmc != null;
        this.restmodule = "/" + Rest.getRestModule(service) + "/";
        this.multimodule = mmc != null ? ("/" + mmc.module() + "/") : null;
        this.respSupplier = () -> respPoolThreadLocal.get().get();
        this.respConsumer = resp -> respPoolThreadLocal.get().accept(resp);
        this.respPoolThreadLocal = ThreadLocal.withInitial(() -> ObjectPool.createUnsafePool(Runtime.getRuntime().availableProcessors(),
            ps -> new HttpMessageResponse(server.getHttpServer().getContext(), messageClient, respSupplier, respConsumer), HttpMessageResponse::prepare, HttpMessageResponse::recycle));
    }

    @Override
    public void begin(final int size, long starttime) {
        this.starttime = starttime;
        this.cdl = new CountDownLatch(size);
    }

    @Override
    public void process(final MessageRecord message, final Runnable callback) {
        execute(message, innerCallback);
    }

    private void execute(final MessageRecord message, final Runnable callback) {
        HttpMessageRequest request = null;
        try {
            long now = System.currentTimeMillis();
            long cha = now - message.createtime;
            long e = now - starttime;
            if (multiconsumer) message.setResptopic(null); //不容许有响应

            HttpMessageResponse response = respSupplier.get();
            request = response.request();
            response.prepare(message, callback, producers.getProducer(message));
            if (multiconsumer) request.setRequestURI(request.getRequestURI().replaceFirst(this.multimodule, this.restmodule));

            server.getHttpServer().getContext().execute(servlet, request, response);
            long o = System.currentTimeMillis() - now;
            if ((cha > 1000 || e > 100 || o > 1000) && fine) {
                logger.log(Level.FINE, "HttpMessageProcessor.process (mqs.delays = " + cha + " ms, mqs.blocks = " + e + " ms, mqs.executes = " + o + " ms) message: " + message);
            } else if ((cha > 50 || e > 10 || o > 50) && finer) {
                logger.log(Level.FINER, "HttpMessageProcessor.process (mq.delays = " + cha + " ms, mq.blocks = " + e + " ms, mq.executes = " + o + " ms) message: " + message);
            } else if (finest) {
                logger.log(Level.FINEST, "HttpMessageProcessor.process (mq.delay = " + cha + " ms, mq.block = " + e + " ms, mq.execute = " + o + " ms) message: " + message);
            }
        } catch (Throwable ex) {
            if (message.getResptopic() != null && !message.getResptopic().isEmpty()) {
                HttpMessageResponse.finishHttpResult(finest, request == null ? null : request.getRespConvert(),
                    message, callback, messageClient, producers.getProducer(message), message.getResptopic(), new HttpResult().status(500));
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
                logger.log(Level.SEVERE, HttpMessageProcessor.class.getSimpleName() + " commit error, restmodule=" + this.restmodule, ex);
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
