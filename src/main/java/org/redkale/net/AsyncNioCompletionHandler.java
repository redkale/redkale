/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
class AsyncNioCompletionHandler<A> implements CompletionHandler<Integer, A>, Runnable {

    private final AsyncNioConnection conn;

    private final boolean readMode;

    CompletionHandler<Integer, A> handler;

    private A attachment;

    public ScheduledFuture timeoutFuture;

    private ByteBuffer[] buffers;

    private ByteBuffer buffer;

    public AsyncNioCompletionHandler(boolean readMode, AsyncNioConnection conn) {
        this.conn = conn;
        this.readMode = readMode;
    }

    public void handler(CompletionHandler<Integer, A> handler, A attachment) {
        this.handler = handler;
        this.attachment = attachment;
    }

    public void attachment(A attachment) {
        this.attachment = attachment;
    }

    public void buffers(ByteBuffer... buffs) {
        this.buffers = buffs;
    }

    public void buffer(ByteBuffer buff) {
        this.buffer = buff;
    }

    private void clear() {
        this.handler = null;
        this.attachment = null;
        this.timeoutFuture = null;
        this.buffers = null;
        this.buffer = null;
    }

    @Override
    public void completed(Integer result, A attach) {
        ScheduledFuture future = this.timeoutFuture;
        if (future != null && !future.isDone()) {
            this.timeoutFuture = null;
            future.cancel(true);
        }
        if (conn != null) {
            if (buffers != null) {
                if (readMode) {
                    conn.offerReadBuffers(buffers);
                } else {
                    conn.offerWriteBuffers(buffers);
                }
            } else if (buffer != null) {
                if (readMode) {
                    conn.offerReadBuffer(buffer);
                } else {
                    conn.offerWriteBuffer(buffer);
                }
            }
        }
        CompletionHandler<Integer, A> handler0 = handler;
        A attachment0 = attachment;
        clear();
        handler0.completed(result, attachment0);
    }

    @Override
    public void failed(Throwable exc, A attach) {
        ScheduledFuture future = this.timeoutFuture;
        if (future != null && !future.isDone()) {
            this.timeoutFuture = null;
            future.cancel(true);
        }
        if (conn != null) {
            if (buffers != null) {
                if (readMode) {
                    conn.offerReadBuffers(buffers);
                } else {
                    conn.offerWriteBuffers(buffers);
                }
            } else if (buffer != null) {
                if (readMode) {
                    conn.offerReadBuffer(buffer);
                } else {
                    conn.offerWriteBuffer(buffer);
                }
            }
        }
        CompletionHandler<Integer, A> handler0 = handler;
        A attachment0 = attachment;
        clear();
        if (handler0 == null) { // 可能和超时run方法同时执行
            if (exc != null) {
                exc.printStackTrace();
            }
        } else {
            handler0.failed(exc, attachment0);
        }
    }

    @Override
    public void run() {
        if (conn != null) {
            if (buffers != null) {
                if (readMode) {
                    conn.offerReadBuffers(buffers);
                } else {
                    conn.offerWriteBuffers(buffers);
                }
            } else if (buffer != null) {
                if (readMode) {
                    conn.offerReadBuffer(buffer);
                } else {
                    conn.offerWriteBuffer(buffer);
                }
            }
        }
        CompletionHandler<Integer, A> handler0 = handler;
        A attachment0 = attachment;
        clear();
        handler0.failed(new TimeoutException(), attachment0);
    }
}
