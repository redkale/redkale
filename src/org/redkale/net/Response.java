/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.function.BiConsumer;

/**
 * 协议响应对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <C> Context的子类型
 * @param <R> Request的子类型
 */
@SuppressWarnings("unchecked")
public abstract class Response<C extends Context, R extends Request<C>> {

    protected final C context;

    protected final R request;

    protected AsyncConnection channel;

    private boolean inited = true;

    protected Object output; //输出的结果对象

    protected BiConsumer<R, Response<C, R>> recycleListener;

    protected Filter<C, R, ? extends Response<C, R>> filter;

    protected Servlet<C, R, ? extends Response<C, R>> servlet;

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
        public void completed(final Integer result, final ByteBuffer[] attachments) {
            int index = -1;
            for (int i = 0; i < attachments.length; i++) {
                if (attachments[i].hasRemaining()) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                channel.write(attachments, index, attachments.length - index, attachments, this);
            } else {
                for (ByteBuffer attachment : attachments) {
                    context.offerBuffer(attachment);
                }
                finish();
            }
        }

        @Override
        public void failed(Throwable exc, final ByteBuffer[] attachments) {
            for (ByteBuffer attachment : attachments) {
                context.offerBuffer(attachment);
            }
            finish(true);
        }

    };

    protected Response(C context, final R request) {
        this.context = context;
        this.request = request;
    }

    protected AsyncConnection removeChannel() {
        AsyncConnection ch = this.channel;
        this.channel = null;
        this.request.channel = null;
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
                recycleListener.accept(request, this);
            } catch (Exception e) {
                System.err.println(request);
                e.printStackTrace();
            }
            recycleListener = null;
        }
        this.output = null;
        this.filter = null;
        this.servlet = null;
        request.recycle();
        if (channel != null) {
            if (keepAlive) {
                this.context.runAsync(new PrepareRunner(context, channel, null));
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

    protected void setFilter(Filter<C, R, Response<C, R>> filter) {
        this.filter = filter;
    }

    protected void thenEvent(Servlet servlet) {
        this.servlet = servlet;
    }

    @SuppressWarnings("unchecked")
    public void nextEvent() throws IOException {
        if (this.filter != null) {
            Filter runner = this.filter;
            this.filter = this.filter._next;
            runner.doFilter(request, this);
            return;
        }
        if (this.servlet != null) {
            Servlet s = this.servlet;
            this.servlet = null;
            s.execute(request, this);
        }
    }

    /**
     * 使用 public void recycleListener(BiConsumer recycleListener) 代替
     *
     * @param recycleListener BiConsumer
     *
     * @deprecated
     */
    @Deprecated
    public void setRecycleListener(BiConsumer<R, Response<C, R>> recycleListener) {
        this.recycleListener = recycleListener;
    }

    public void recycleListener(BiConsumer<R, Response<C, R>> recycleListener) {
        this.recycleListener = recycleListener;
    }

    public Object getOutput() {
        return output;
    }

    /**
     * 是否已关闭
     *
     * @return boolean
     */
    public boolean isClosed() {
        return !this.inited;
    }

    public void finish() {
        this.finish(false);
    }

    public void finish(boolean kill) {
        if (!this.inited) return; //避免重复关闭
        //System.println("耗时: " + (System.currentTimeMillis() - request.createtime));
        if (kill) refuseAlive();
        this.context.responsePool.offer(this);
    }

    public void finish(final byte[] bs) {
        if (!this.inited) return; //避免重复关闭
        if (this.context.bufferCapacity == bs.length) {
            ByteBuffer buffer = this.context.pollBuffer();
            buffer.put(bs);
            buffer.flip();
            this.finish(buffer);
        } else {
            this.finish(ByteBuffer.wrap(bs));
        }
    }

    public void finish(ByteBuffer buffer) {
        if (!this.inited) return; //避免重复关闭
        this.channel.write(buffer, buffer, finishHandler);
    }

    public void finish(boolean kill, ByteBuffer buffer) {
        if (!this.inited) return; //避免重复关闭
        if (kill) refuseAlive();
        this.channel.write(buffer, buffer, finishHandler);
    }

    public void finish(ByteBuffer... buffers) {
        if (!this.inited) return; //避免重复关闭
        this.channel.write(buffers, buffers, finishHandler2);
    }

    public void finish(boolean kill, ByteBuffer... buffers) {
        if (!this.inited) return; //避免重复关闭
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

    protected <A> void send(final ByteBuffer[] buffers, A attachment, final CompletionHandler<Integer, A> handler) {
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
                } else if (handler != null) handler.completed(result, attachment);
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

    public C getContext() {
        return context;
    }
}
