/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.*;
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

    private boolean inited = true;

    protected Runnable recycleListener;

    private final CompletionHandler finishHandler = new CompletionHandler<Integer, ByteBuffer>() {

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
            if (attachment.hasRemaining()) {
                channel.write(attachment, attachment, this);
            } else {
                context.offerBuffer(attachment);
                finish();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            context.offerBuffer(attachment);
            finish(true);
        }

    };

    private final CompletionHandler finishHandler2 = new CompletionHandler<Integer, ByteBuffer[]>() {

        @Override
        public void completed(Integer result, ByteBuffer[] attachments) {
            int index = -1;
            for (int i = 0; i < attachments.length; i++) {
                if (attachments[i].hasRemaining()) {
                    index = i;
                    break;
                } else {
                    context.offerBuffer(attachments[i]);
                }
            }
            if (index == 0) {
                channel.write(attachments, attachments, this);
            } else if (index > 0) {
                ByteBuffer[] newattachs = new ByteBuffer[attachments.length - index];
                System.arraycopy(attachments, index, newattachs, 0, newattachs.length);
                channel.write(newattachs, newattachs, this);
            } else {
                finish();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer[] attachments) {
            for (ByteBuffer attachment : attachments) {
                context.offerBuffer(attachment);
            }
            finish(true);
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

    protected void prepare() {
        inited = true;
    }

    protected boolean recycle() {
        if (!inited) return false;
        boolean keepAlive = request.keepAlive;
        if (recycleListener != null) {
            try {
                recycleListener.run();
            } catch (Exception e) {
                System.err.println(request);
                e.printStackTrace();
            }
            recycleListener = null;
        }
        request.recycle();
        if (channel != null) {
            if (keepAlive) {
                this.context.submit(new PrepareRunner(context, channel, null));
            } else {
                try {
                    if (channel.isOpen()) channel.close();
                } catch (Exception e) {
                }
            }
            channel = null;
        }
        this.inited = false;
        return true;
    }

    protected void refuseAlive() {
        this.request.keepAlive = false;
    }

    protected void init(AsyncConnection channel) {
        this.channel = channel;
        this.request.channel = channel;
        this.request.createtime = System.currentTimeMillis();
    }

    public void setRecycleListener(Runnable recycleListener) {
        this.recycleListener = recycleListener;
    }

    public void finish() {
        this.finish(false);
    }

    public void finish(boolean kill) {
        //System.println("耗时: " + (System.currentTimeMillis() - request.createtime));
        if (kill) refuseAlive();
        this.context.responsePool.offer(this);
    }

    public void finish(ByteBuffer buffer) {
        this.channel.write(buffer, buffer, finishHandler);
    }

    public void finish(boolean kill, ByteBuffer buffer) {
        if (kill) refuseAlive();
        this.channel.write(buffer, buffer, finishHandler);
    }

    public void finish(ByteBuffer... buffers) {
        this.channel.write(buffers, buffers, finishHandler2);
    }

    public void finish(boolean kill, ByteBuffer... buffers) {
        if (kill) refuseAlive();
        this.channel.write(buffers, buffers, finishHandler2);
    }

    protected <A> void send(final ByteBuffer buffer, final A attachment, final CompletionHandler<Integer, A> handler) {
        this.channel.write(buffer, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                if (buffer.hasRemaining()) {
                    channel.write(buffer, attachment, this);
                } else {
                    context.offerBuffer(buffer);
                    if (handler != null) handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                context.offerBuffer(buffer);
                if (handler != null) handler.failed(exc, attachment);
            }

        });
    }

    protected <A> void send(final ByteBuffer[] buffers, A attachment, CompletionHandler<Integer, A> handler) {
        this.channel.write(buffers, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                int index = -1;
                for (int i = 0; i < buffers.length; i++) {
                    if (buffers[i].hasRemaining()) {
                        index = i;
                        break;
                    }
                    context.offerBuffer(buffers[i]);
                }
                if (index == 0) {
                    channel.write(buffers, attachment, this);
                } else if (index > 0) {
                    ByteBuffer[] newattachs = new ByteBuffer[buffers.length - index];
                    System.arraycopy(buffers, index, newattachs, 0, newattachs.length);
                    channel.write(newattachs, attachment, this);
                } else {
                    if (handler != null) handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                for (ByteBuffer buffer : buffers) {
                    context.offerBuffer(buffer);
                }
                if (handler != null) handler.failed(exc, attachment);
            }

        });
    }

    public Context getContext() {
        return context;
    }
}
