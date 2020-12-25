/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.boot.NodeSncpServer;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.ThreadHashExecutor;

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

    protected final boolean finest;

    protected final boolean finer;

    protected final Logger logger;

    protected final MessageProducers producer;

    protected final NodeSncpServer server;

    protected final ThreadHashExecutor workExecutor;

    protected final Service service;

    protected final SncpServlet servlet;

    protected CountDownLatch cdl;

    protected long starttime;

    protected final Runnable innerCallback = () -> {
        if (cdl != null) cdl.countDown();
    };

    public SncpMessageProcessor(Logger logger, ThreadHashExecutor workExecutor, MessageProducers producer, NodeSncpServer server, Service service, SncpServlet servlet) {
        this.logger = logger;
        this.finest = logger.isLoggable(Level.FINEST);
        this.finer = logger.isLoggable(Level.FINER);
        this.producer = producer;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
        this.workExecutor = workExecutor;
    }

    @Override
    public void begin(final int size, long starttime) {
        this.starttime = starttime;
        if (this.workExecutor != null) this.cdl = new CountDownLatch(size);
    }

    @Override
    public void process(final MessageRecord message, final Runnable callback) {
        if (this.workExecutor == null) {
            execute(message, innerCallback);
        } else {
            this.workExecutor.execute(message.hash(), () -> execute(message, innerCallback));
        }
    }

    private void execute(final MessageRecord message, final Runnable callback) {
        SncpMessageResponse response = null;
        try {
            long now = System.currentTimeMillis();
            long cha = now - message.createtime;
            long e = now - starttime;
            if (cha > 50 || e > 10 || finer) {
                logger.log(Level.FINER, "SncpMessageProcessor.process (mq.delays = " + cha + " ms, mq.blocks = " + e + " ms) message: " + message);
            } else if (finest) {
                logger.log(Level.FINEST, "SncpMessageProcessor.process (mq.delay = " + cha + " ms, mq.blocks = " + e + " ms) message: " + message);
            }
            SncpContext context = server.getSncpServer().getContext();
            SncpMessageRequest request = new SncpMessageRequest(context, message);
            response = new SncpMessageResponse(context, request, callback, null, producer.getProducer(message));
            servlet.execute(request, response);
        } catch (Throwable ex) {
            if (response != null) response.finish(SncpResponse.RETCODE_ILLSERVICEID, null);
            logger.log(Level.SEVERE, SncpMessageProcessor.class.getSimpleName() + " process error, message=" + message, ex instanceof CompletionException ? ((CompletionException) ex).getCause() : ex);
        }
    }

    @Override
    public void commit() {
        if (this.cdl != null) {
            try {
                this.cdl.await(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
            }
            this.cdl = null;
        }
    }

    public MessageProducers getProducer() {
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
