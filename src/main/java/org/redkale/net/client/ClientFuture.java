/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.*;
import org.redkale.annotation.Nonnull;
import org.redkale.net.*;
import org.redkale.util.Traces;

/**
 * @author zhangjx
 * @since 2.3.0
 * @param <R> 泛型
 * @param <T> 泛型
 */
public class ClientFuture<R extends ClientRequest, T> extends CompletableFuture<T> implements Runnable {

    public static final ClientFuture NIL = new ClientFuture() {};

    @Nonnull
    protected final R request;

    @Nonnull
    protected final ClientConnection conn;

    private ScheduledFuture timeout;

    private ClientFuture() {
        super();
        this.conn = null;
        this.request = null;
    }

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
        if (timeout != null && !timeout.isDone()) {
            timeout.cancel(true);
        }
    }

    public static ClientFuture[] array(Collection<ClientFuture> list) {
        return list.toArray(new ClientFuture[list.size()]);
    }

    @Override // JDK9+
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
        String traceid = request != null ? request.getTraceid() : null;
        if (request != null) {
            conn.removeRespFuture(request.getRequestid(), this);
        }
        TimeoutException ex = new TimeoutException("client-request: " + request);
        WorkThread workThread = null;
        if (request != null) {
            workThread = request.workThread;
            request.workThread = null;
        }
        if (workThread == null || workThread.getWorkExecutor() == null) {
            workThread = conn.getChannel().getReadIOThread();
        }
        workThread.runWork(() -> {
            Traces.currentTraceid(traceid);
            if (!isDone()) {
                completeExceptionally(ex);
            }
            Traces.removeTraceid();
        });
        conn.dispose(ex);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hash(this) + "{conn = " + conn + ", request = " + request
                + "}";
    }
}
