/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.Objects;
import java.util.concurrent.*;
import org.redkale.annotation.Nonnull;
import org.redkale.net.*;

/**
 *
 * @author zhangjx
 *
 * @since 2.3.0
 *
 * @param <R> 泛型
 * @param <T> 泛型
 */
public class ClientFuture<R extends ClientRequest, T> extends CompletableFuture<T> implements Runnable {

    @Nonnull
    protected final R request;

    @Nonnull
    protected final ClientConnection conn;

    private ScheduledFuture timeout;

    ClientFuture(ClientConnection conn, R request) {
        super();
        Objects.requireNonNull(conn);
        Objects.requireNonNull(request);
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
    public <U> ClientFuture<R, U> newIncompleteFuture() {
        ClientFuture future = new ClientFuture<>(conn, request);
        future.timeout = timeout;
        return future;
    }

    public R getRequest() {
        return request;
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
        conn.removeRespFuture(request.getRequestid(), this);
        TimeoutException ex = new TimeoutException("client-request: " + request);
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
