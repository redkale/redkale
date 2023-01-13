/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.*;
import java.util.concurrent.*;
import org.redkale.net.*;

/**
 *
 * @author zhangjx
 * 
 * @since 2.3.0
 * 
 * @param <T> 泛型
 */
public class ClientFuture<T> extends CompletableFuture<T> implements Runnable {

    protected final ClientRequest request;

    protected final ClientConnection conn;

    private ScheduledFuture timeout;

    public ClientFuture(ClientConnection conn, ClientRequest request) {
        super();
        this.conn = conn;
        this.request = request;
    }

    void setTimeout(ScheduledFuture timeout) {
        this.timeout = timeout;
    }

    void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel(true);
        }
    }

    @Override //JDK9+
    public <U> ClientFuture<U> newIncompleteFuture() {
        ClientFuture future = new ClientFuture<>(conn, request);
        future.timeout = timeout;
        return future;
    }

    public <R extends ClientRequest> R getRequest() {
        return (R) request;
    }

    @Override
    public void run() {
        if (conn == null) {
            return;
        }
        AsyncConnection channel = conn.getChannel();
        if (channel.inCurrReadThread()) {
            this.runTimeout();
        } else {
            channel.executeRead(this::runTimeout);
        }
    }

    private void runTimeout() {
        Queue<ClientFuture> responseQueue = conn.responseQueue;
        if (responseQueue != null) {
            responseQueue.remove(this);
        }
        if (request.getRequestid() != null) {
            conn.responseMap.remove(request.getRequestid());
        }

        TimeoutException ex = new TimeoutException();
        WorkThread workThread = null;
        if (request != null) {
            workThread = request.workThread;
            request.workThread = null;
        }
        if (workThread == null || workThread.getWorkExecutor() == null) {
            workThread = conn.getChannel().getReadIOThread();
        }
        workThread.runWork(() -> completeExceptionally(ex));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hash(this) + "{conn = " + conn + ", request = " + request + "}";
    }
}
