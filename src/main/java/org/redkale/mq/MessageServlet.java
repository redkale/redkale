/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.boot.NodeServer;
import org.redkale.net.Context;
import org.redkale.net.Request;
import org.redkale.net.Response;
import org.redkale.net.Servlet;
import org.redkale.service.Service;
import org.redkale.util.Traces;

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
public abstract class MessageServlet implements MessageProcessor {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final MessageClient messageClient;

    protected final NodeServer server;

    protected final Service service;

    protected final Servlet servlet;

    protected final String topic;

    public MessageServlet(MessageClient messageClient, NodeServer server, Service service, Servlet servlet, String topic) {
        this.messageClient = messageClient;
        this.server = server;
        this.service = service;
        this.servlet = servlet;
        this.topic = topic;
    }

    @Override
    public void process(final MessageRecord message, long time) {
        Response response = null;
        try {
            Traces.computeIfAbsent(message.getTraceid());
            long now = System.currentTimeMillis();
            long cha = now - message.createTime;
            long e = now - time;
            Context context = server.getServer().getContext();
            Request request = createRequest(context, message);
            response = createResponse(context, request);
            //执行逻辑
            context.execute(servlet, request, response);
            long o = System.currentTimeMillis() - now;
            if ((cha > 1000 || e > 100 || o > 1000) && logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, getClass().getSimpleName() + ".process (mqs.delays = " + cha + " ms, mqs.blocks = " + e + " ms, mqs.executes = " + o + " ms) message: " + message);
            } else if ((cha > 50 || e > 10 || o > 50) && logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, getClass().getSimpleName() + ".process (mq.delays = " + cha + " ms, mq.blocks = " + e + " ms, mq.executes = " + o + " ms) message: " + message);
            } else if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, getClass().getSimpleName() + ".process (mq.delay = " + cha + " ms, mq.block = " + e + " ms, mq.execute = " + o + " ms) message: " + message);
            }
        } catch (Throwable ex) {
            if (response != null) {
                onError(response, message, ex);
            }
            logger.log(Level.SEVERE, getClass().getSimpleName() + " process error, message=" + message, ex instanceof CompletionException ? ((CompletionException) ex).getCause() : ex);
        }
    }

    protected abstract Request createRequest(Context context, MessageRecord message);

    protected abstract Response createResponse(Context context, Request request);

    protected abstract void onError(Response response, MessageRecord message, Throwable t);

    public NodeServer getServer() {
        return server;
    }

    public Service getService() {
        return service;
    }

    public Servlet getServlet() {
        return servlet;
    }

    public String getTopic() {
        return topic;
    }

}
