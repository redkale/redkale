/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author zhangjx
 */
public class AsyncPooledConnection implements AutoCloseable {

    private final Queue<AsyncPooledConnection> queue;

    private final AtomicLong usingCounter;

    private final AsyncConnection conn;

    public AsyncPooledConnection(Queue<AsyncPooledConnection> queue, AtomicLong usingCounter, AsyncConnection conn) {
        this.conn = conn;
        this.usingCounter = usingCounter;
        this.queue = queue;
    }

    public AsyncConnection getAsyncConnection() {
        return conn;
    }

    public void fireConnectionClosed() {
        this.queue.add(this);
    }

    @Override
    public void close() throws IOException {
        usingCounter.decrementAndGet();
        conn.close();
    }

    public void dispose() {
        try {
            this.close();
        } catch (IOException io) {
        }
    }
}
