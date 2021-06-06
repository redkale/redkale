/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.util.ByteTuple;

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

    protected Supplier<Response> responseSupplier; //虚拟构建的Response可能不存在responseSupplier

    protected Consumer<Response> responseConsumer; //虚拟构建的Response可能不存在responseConsumer

    protected final R request;

    protected AsyncConnection channel;

    private volatile boolean inited = true;

    protected Object output; //输出的结果对象

    protected BiConsumer<R, Response<C, R>> recycleListener;

    protected Filter<C, R, ? extends Response<C, R>> filter;

    protected Servlet<C, R, ? extends Response<C, R>> servlet;

    private final CompletionHandler finishBytesHandler = new CompletionHandler<Integer, Void>() {

        @Override
        public void completed(Integer result, Void attachment) {
            finish();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            finish(true);
        }

    };

    private final CompletionHandler finishBufferHandler = new CompletionHandler<Integer, ByteBuffer>() {

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
            channel.offerBuffer(attachment);
            finish();
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            channel.offerBuffer(attachment);
            finish(true);
        }

    };

    private final CompletionHandler finishBuffersHandler = new CompletionHandler<Integer, ByteBuffer[]>() {

        @Override
        public void completed(final Integer result, final ByteBuffer[] attachments) {
            if (attachments != null) {
                for (ByteBuffer attachment : attachments) {
                    channel.offerBuffer(attachment);
                }
            }
            finish();
        }

        @Override
        public void failed(Throwable exc, final ByteBuffer[] attachments) {
            if (attachments != null) {
                for (ByteBuffer attachment : attachments) {
                    channel.offerBuffer(attachment);
                }
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
        request.prepare();
    }

    protected boolean recycle() {
        if (!inited) return false;
        this.output = null;
        this.filter = null;
        this.servlet = null;
        boolean notpipeline = request.pipelineIndex == 0 || request.pipelineOver;
        request.recycle();
        if (channel != null) {
            if (notpipeline) channel.dispose();
            channel = null;
        }
        this.responseSupplier = null;
        this.responseConsumer = null;
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
        if (this.recycleListener != null) {
            try {
                this.recycleListener.accept(request, this);
            } catch (Exception e) {
                context.logger.log(Level.WARNING, "Response.recycleListener error, request = " + request, e);
            }
            this.recycleListener = null;
        }
        if (request.keepAlive && (request.pipelineIndex == 0 || request.pipelineOver)) {
            AsyncConnection conn = removeChannel();
            if (conn != null && conn.protocolCodec != null) {
                this.responseConsumer.accept(this);
                conn.read(conn.protocolCodec);
            } else {
                Supplier<Response> poolSupplier = this.responseSupplier;
                Consumer<Response> poolConsumer = this.responseConsumer;
                this.recycle();
                new ProtocolCodec(context, poolSupplier, poolConsumer, conn).response(this).run(null);
            }
        } else {
            this.responseConsumer.accept(this);
        }
    }

    public final void finish(final byte[] bs) {
        finish(false, bs, 0, bs.length);
    }

    public final void finish(final byte[] bs, int offset, int length) {
        finish(false, bs, offset, length);
    }

    public final void finish(final ByteTuple array) {
        finish(false, array.content(), array.offset(), array.length());
    }

    public final void finish(boolean kill, final byte[] bs) {
        finish(kill, bs, 0, bs.length);
    }

    public final void finish(boolean kill, final ByteTuple array) {
        finish(kill, array.content(), array.offset(), array.length());
    }

    public void finish(boolean kill, final byte[] bs, int offset, int length) {
        if (!this.inited) return; //避免重复关闭
        if (kill) refuseAlive();
        if (this.channel.hasPipelineData()) {
            this.channel.flushPipelineData(null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    channel.write(bs, offset, length, finishBytesHandler);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    finishBytesHandler.failed(exc, attachment);
                }
            });
        } else {
            this.channel.write(bs, offset, length, finishBytesHandler);
        }
    }

    public <A> void finish(boolean kill, final byte[] bs, int offset, int length, final byte[] bs2, int offset2, int length2, Consumer<A> callback, A attachment) {
        if (!this.inited) return; //避免重复关闭
        if (kill) refuseAlive();
        if (this.channel.hasPipelineData()) {
            this.channel.flushPipelineData(null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    channel.write(bs, offset, length, bs2, offset2, length2, callback, attachment, finishBytesHandler);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    finishBytesHandler.failed(exc, attachment);
                }
            });
        } else {
            this.channel.write(bs, offset, length, bs2, offset2, length2, callback, attachment, finishBytesHandler);
        }
    }

    protected final void finish(ByteBuffer buffer) {
        finish(false, buffer);
    }

    protected final void finish(ByteBuffer... buffers) {
        finish(false, buffers);
    }

    protected void finish(boolean kill, ByteBuffer buffer) {
        if (!this.inited) return; //避免重复关闭
        if (kill) refuseAlive();
        if (this.channel.hasPipelineData()) {
            this.channel.flushPipelineData(null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    channel.write(buffer, buffer, finishBufferHandler);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    finishBufferHandler.failed(exc, buffer);
                }
            });
        } else {
            this.channel.write(buffer, buffer, finishBufferHandler);
        }
    }

    protected void finish(boolean kill, ByteBuffer... buffers) {
        if (!this.inited) return; //避免重复关闭
        if (kill) refuseAlive();
        if (this.channel.hasPipelineData()) {
            this.channel.flushPipelineData(null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    channel.write(buffers, buffers, finishBuffersHandler);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    finishBuffersHandler.failed(exc, buffers);
                }
            });
        } else {
            this.channel.write(buffers, buffers, finishBuffersHandler);
        }
    }

    protected <A> void send(final ByteTuple array, final CompletionHandler<Integer, Void> handler) {
        this.channel.write(array, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (handler != null) handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (handler != null) handler.failed(exc, attachment);
            }

        });
    }

    protected <A> void send(final ByteBuffer buffer, final A attachment, final CompletionHandler<Integer, A> handler) {
        this.channel.write(buffer, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                channel.offerBuffer(buffer);
                if (handler != null) handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                channel.offerBuffer(buffer);
                if (handler != null) handler.failed(exc, attachment);
            }

        });
    }

    protected <A> void send(final ByteBuffer[] buffers, A attachment, final CompletionHandler<Integer, A> handler) {
        this.channel.write(buffers, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                channel.offerBuffer(buffers);
                if (handler != null) handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                for (ByteBuffer buffer : buffers) {
                    channel.offerBuffer(buffer);
                }
                if (handler != null) handler.failed(exc, attachment);
            }

        });
    }

    public C getContext() {
        return context;
    }
}
