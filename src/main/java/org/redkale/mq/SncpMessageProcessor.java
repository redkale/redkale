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
public class SncpMessageProcessor implements MessageProcessor {

    protected final boolean finest;

    protected final boolean finer;

    protected final boolean fine;

    protected final Logger logger;

    protected MessageClient messageClient;

    protected final MessageProducers producer;

    protected final NodeSncpServer server;

    protected final Service service;

    protected final SncpServlet servlet;

    protected CountDownLatch cdl;

    protected long starttime;

    protected final Runnable innerCallback = () -> {
        if (cdl != null) cdl.countDown();
    };

    public SncpMessageProcessor(Logger logger, SncpMessageClient messageClient, MessageProducers producer, NodeSncpServer server, Service service, SncpServlet servlet) {
        this.logger = logger;
        this.finest = logger.isLoggable(Level.FINEST);
        this.finer = logger.isLoggable(Level.FINER);
        this.fine = logger.isLoggable(Level.FINE);
        this.messageClient = messageClient;
        this.producer = producer;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
    }

    @Override
    public void begin(final int size, long starttime) {
        this.starttime = starttime;
        this.cdl = size > 1 ? new CountDownLatch(size) : null;
    }

    @Override
    public void process(final MessageRecord message, final Runnable callback) {
        execute(message, innerCallback);
    }

    private void execute(final MessageRecord message, final Runnable callback) {
        SncpMessageResponse response = null;
        try {
            long now = System.currentTimeMillis();
            long cha = now - message.createtime;
            long e = now - starttime;
            SncpContext context = server.getSncpServer().getContext();
            SncpMessageRequest request = new SncpMessageRequest(context, message);
            response = new SncpMessageResponse(context, request, callback, messageClient, producer.getProducer(message));

            context.execute(servlet, request, response);
            long o = System.currentTimeMillis() - now;
            if ((cha > 1000 || e > 100 || o > 1000) && fine) {
                logger.log(Level.FINE, "SncpMessageProcessor.process (mqs.delays = " + cha + " ms, mqs.blocks = " + e + " ms, mqs.executes = " + o + " ms) message: " + message);
            } else if ((cha > 50 || e > 10 || o > 50) && finer) {
                logger.log(Level.FINER, "SncpMessageProcessor.process (mq.delays = " + cha + " ms, mq.blocks = " + e + " ms, mq.executes = " + o + " ms) message: " + message);
            } else if (finest) {
                logger.log(Level.FINEST, "SncpMessageProcessor.process (mq.delay = " + cha + " ms, mq.block = " + e + " ms, mq.execute = " + o + " ms) message: " + message);
            }
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
