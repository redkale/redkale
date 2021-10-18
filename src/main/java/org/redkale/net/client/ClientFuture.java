/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.Queue;
import java.util.concurrent.*;
import org.redkale.net.WorkThread;

/**
 *
 * @author zhangjx
 * @param <T> 泛型
 */
public class ClientFuture<T> extends CompletableFuture<T> implements Runnable {

    public static final ClientFuture EMPTY = new ClientFuture() {
        @Override
        public boolean complete(Object value) {
            return true;
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            return true;
        }
    };

    protected ClientRequest request;

    ScheduledFuture timeout;

    Queue<ClientFuture> responseQueue;

    public ClientFuture() {
        super();
    }

    public ClientFuture(ClientRequest request) {
        super();
        this.request = request;
    }

    @Override //JDK9+
    public <U> ClientFuture<U> newIncompleteFuture() {
        return new ClientFuture<>();
    }

    public <R extends ClientRequest> R getRequest() {
        return (R) request;
    }

    @Override
    public void run() {
        if (responseQueue != null) responseQueue.remove(this);
        TimeoutException ex = new TimeoutException();
        WorkThread workThread = null;
        if (request != null) {
            workThread = request.workThread;
            request.workThread = null;
        }
        if (workThread == null || workThread == Thread.currentThread()
            || workThread.getState() == Thread.State.BLOCKED
            || workThread.getState() == Thread.State.WAITING) {
            this.completeExceptionally(ex);
        } else {
            workThread.execute(() -> completeExceptionally(ex));
        }
    }
}
