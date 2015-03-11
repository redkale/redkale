/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 *
 * @author zhangjx
 * @param <R>
 */
@SuppressWarnings("unchecked")
public abstract class Response<R extends Request> {

    protected final Context context;

    protected final R request;

    protected AsyncConnection channel;

    protected final CompletionHandler finishHandler = new CompletionHandler<Integer, ByteBuffer>() {

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
            if (attachment.hasRemaining()) {
                channel.write(attachment, attachment, this);
            } else {
                Response.this.context.offerBuffer(attachment);
                Response.this.finish();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            Response.this.context.offerBuffer(attachment);
            Response.this.finish(true);
        }

    };

    protected Response(Context context, final R request) {
        this.context = context;
        this.request = request;
    }

    protected AsyncConnection removeChannel() {
        AsyncConnection ch = this.channel;
        this.channel = null;
        return ch;
    }

    protected void recycle() {
        boolean keepAlive = request.keepAlive;
        request.recycle();
        if (channel != null) {
            if (keepAlive) {
                this.context.submit(new PrepareRunner(context, channel, null));
            } else {
                try {
                    if (channel.isOpen()) channel.close();
                } catch (Exception e) {
                }
                channel = null;
            }
        }
    }

    protected void refuseAlive() {
        this.request.keepAlive = false;
    }

    protected void init(AsyncConnection channel) {
        this.channel = channel;
        this.request.channel = channel;
        this.request.createtime = System.currentTimeMillis();
    }

    public void finish() {
        this.finish(false);
    }

    public void finish(boolean kill) {
        //System.out.println("耗时: " + (System.currentTimeMillis() - request.createtime));
        if (kill) refuseAlive();
        this.context.responsePool.offer(this);
    }

    public void finish(ByteBuffer buffer) {
        finish(buffer, false);
    }

    public void finish(ByteBuffer buffer, boolean kill) {
        if (kill) refuseAlive();
        send(buffer, buffer, finishHandler);
    }

    public <A> void send(ByteBuffer buffer, A attachment, CompletionHandler<Integer, A> handler) {
        this.channel.write(buffer, attachment, handler);
    }

    public Context getContext() {
        return context;
    }
}
