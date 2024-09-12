/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.util.*;

/**
 * 协议响应对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <C> Context的子类型
 * @param <R> Request的子类型
 */
@SuppressWarnings("unchecked")
public abstract class Response<C extends Context, R extends Request<C>> {

    protected final C context;

    // 虚拟构建的Response可能不存在responseSupplier
    protected Supplier<Response> responseSupplier;

    // 虚拟构建的Response可能不存在responseConsumer
    protected Consumer<Response> responseConsumer;

    protected final ExecutorService workExecutor;

    protected final R request;

    protected final WorkThread thread;

    protected AsyncConnection channel;

    private volatile boolean inited = true;

    protected boolean inNonBlocking = true;

    protected boolean readRegistered;

    // 输出的结果对象
    protected Object output;

    protected BiConsumer<R, Response<C, R>> recycleListener;

    protected BiConsumer<R, Throwable> errorHandler;

    protected List<Runnable> afterFinishListeners;

    protected Filter<C, R, ? extends Response<C, R>> filter;

    protected Servlet<C, R, ? extends Response<C, R>> servlet;

    private final ByteBuffer writeBuffer;

    private final CompletionHandler finishBytesIOThreadHandler = new CompletionHandler<Integer, Void>() {

        @Override
        public void completed(Integer result, Void attachment) {
            completeInIOThread(false);
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            completeInIOThread(true);
        }
    };

    private final CompletionHandler finishBufferIOThreadHandler = new CompletionHandler<Integer, ByteBuffer>() {

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
            if (attachment != writeBuffer) {
                channel.offerWriteBuffer(attachment);
            } else {
                attachment.clear();
            }
            completeInIOThread(false);
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            if (attachment != writeBuffer) {
                channel.offerWriteBuffer(attachment);
            } else {
                attachment.clear();
            }
            completeInIOThread(true);
        }
    };

    private final CompletionHandler finishBuffersIOThreadHandler = new CompletionHandler<Integer, ByteBuffer[]>() {

        @Override
        public void completed(final Integer result, final ByteBuffer[] attachments) {
            if (attachments != null) {
                for (ByteBuffer attachment : attachments) {
                    channel.offerWriteBuffer(attachment);
                }
            }
            completeInIOThread(false);
        }

        @Override
        public void failed(Throwable exc, final ByteBuffer[] attachments) {
            if (attachments != null) {
                for (ByteBuffer attachment : attachments) {
                    channel.offerWriteBuffer(attachment);
                }
            }
            completeInIOThread(true);
        }
    };

    protected Response(C context, final R request) {
        this.context = context;
        this.request = request;
        this.thread = WorkThread.currentWorkThread();
        this.writeBuffer = context != null ? ByteBuffer.allocateDirect(context.getBufferCapacity()) : null;
        this.workExecutor =
                context == null || context.workExecutor == null ? ForkJoinPool.commonPool() : context.workExecutor;
    }

    protected AsyncConnection removeChannel() {
        AsyncConnection ch = this.channel;
        this.channel = null;
        this.request.channel = null;
        return ch;
    }

    protected void prepare() {
        inited = true;
        inNonBlocking = true;
        readRegistered = false;
        request.prepare();
    }

    protected boolean recycle() {
        if (!inited) {
            return false;
        }
        this.output = null;
        this.filter = null;
        this.servlet = null;
        boolean noPipeline = request.pipelineIndex == 0 || request.pipelineCompleted;
        request.recycle();
        if (channel != null) {
            if (noPipeline) {
                channel.dispose();
            }
            channel = null;
        }
        this.responseSupplier = null;
        this.responseConsumer = null;
        this.inited = false;
        return true;
    }

    protected ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    protected void updateNonBlocking(boolean nonBlocking) {
        this.inNonBlocking = nonBlocking;
    }

    protected boolean inNonBlocking() {
        return inNonBlocking;
    }

    protected void refuseAlive() {
        this.request.keepAlive = false;
    }

    protected void init(AsyncConnection channel) {
        this.channel = channel;
        this.request.channel = channel;
        this.request.createTime = System.currentTimeMillis();
    }

    protected void setFilter(Filter<C, R, Response<C, R>> filter) {
        this.filter = filter;
    }

    protected void thenEvent(Filter<C, R, Response<C, R>> filter) {
        if (this.filter == null) {
            this.filter = filter;
        } else {
            Filter f = this.filter;
            while (f._next != null) {
                f = f._next;
            }
            f._next = filter;
        }
    }

    protected void thenEvent(Servlet servlet) {
        this.servlet = servlet;
    }

    @SuppressWarnings("unchecked")
    public void nextEvent() throws IOException {
        if (this.filter != null) {
            Filter f = this.filter;
            this.filter = this.filter._next;
            if (inNonBlocking) {
                if (f.isNonBlocking()) {
                    f.doFilter(request, this);
                } else {
                    inNonBlocking = false;
                    workExecutor.execute(() -> {
                        try {
                            f.doFilter(request, this);
                        } catch (Throwable t) {
                            context.getLogger().log(Level.WARNING, "Filter occur exception. request = " + request, t);
                            finishError(t);
                        }
                    });
                }
            } else {
                f.doFilter(request, this);
            }
            return;
        }
        if (this.servlet != null) {
            Servlet s = this.servlet;
            this.servlet = null;
            if (inNonBlocking) {
                if (s.isNonBlocking()) {
                    s.execute(request, this);
                } else {
                    inNonBlocking = false;
                    workExecutor.execute(() -> {
                        try {
                            s.execute(request, this);
                        } catch (Throwable t) {
                            context.getLogger().log(Level.WARNING, "Servlet occur exception. request = " + request, t);
                            finishError(t);
                        }
                    });
                }
            } else {
                s.execute(request, this);
            }
        }
    }

    public void recycleListener(BiConsumer<R, Response<C, R>> recycleListener) {
        this.recycleListener = recycleListener;
    }

    public void errorHandler(BiConsumer<R, Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void addAfterFinishListener(Runnable listener) {
        if (this.afterFinishListeners == null) {
            this.afterFinishListeners = new ArrayList<>();
        }
        this.afterFinishListeners.add(listener);
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

    /**
     * Servlet.execute执行时报错
     *
     * <p>被重载后kill不一定为true
     *
     * @param t Throwable
     */
    public final void finishError(Throwable t) {
        BiConsumer<R, Throwable> handler = this.errorHandler;
        if (handler != null) {
            this.errorHandler = null;
            try {
                handler.accept(request, t);
            } catch (Throwable e) {
                context.logger.log(Level.WARNING, "Response.errorHandler error, request = " + request, e);
                defaultError(t);
            }
        } else {
            defaultError(t);
        }
    }

    protected void defaultError(Throwable t) {
        codecError(t);
    }

    /**
     * 对请求包进行编解码时报错, 非Servlet.execute执行报错
     *
     * @param t Throwable
     */
    protected void codecError(Throwable t) {
        completeInIOThread(true);
    }

    protected void completeFinishBytes(Integer result, Void attachment) {
        completeInIOThread(false);
    }

    private void completeInIOThread(boolean kill) {
        if (!this.inited) {
            return; // 避免重复关闭
        }
        // System.println("耗时: " + (System.currentTimeMillis() - request.createtime));
        if (kill) {
            refuseAlive();
        }
        if (this.afterFinishListeners != null) {
            for (Runnable listener : this.afterFinishListeners) {
                listener.run();
            }
            this.afterFinishListeners = null;
        }
        if (this.recycleListener != null) {
            try {
                this.recycleListener.accept(request, this);
            } catch (Throwable e) {
                context.logger.log(Level.WARNING, "Response.recycleListener error, request = " + request, e);
            }
            this.recycleListener = null;
        }
        boolean completed = request.completed;
        if (request.keepAlive && (request.pipelineIndex == 0 || request.pipelineCompleted)) {
            AsyncConnection conn = removeChannel();
            if (conn != null && conn.protocolCodec != null) {
                this.responseConsumer.accept(this);
                if (!completed) {
                    conn.readRegister(conn.protocolCodec);
                    this.readRegistered = true;
                }
            } else {
                Supplier<Response> poolSupplier = this.responseSupplier;
                Consumer<Response> poolConsumer = this.responseConsumer;
                this.recycle();
                new ProtocolCodec(context, poolSupplier, poolConsumer, conn)
                        .response(this)
                        .run(null);
                this.readRegistered = true;
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
        if (kill) {
            refuseAlive();
        }
        if (request.pipelineIndex > 0) {
            boolean allCompleted =
                    this.channel.appendPipeline(request.pipelineIndex, request.pipelineCount, bs, offset, length);
            if (allCompleted) {
                request.pipelineCompleted = true;
                this.channel.writePipelineInIOThread(this.finishBytesIOThreadHandler);
            } else {
                removeChannel();
                this.responseConsumer.accept(this);
            }
        } else if (this.channel.hasPipelineData()) {
            this.channel.appendPipeline(request.pipelineIndex, request.pipelineCount, bs, offset, length);
            this.channel.writePipelineInIOThread(this.finishBytesIOThreadHandler);
        } else {
            ByteBuffer buffer = this.writeBuffer;
            if (buffer != null && buffer.capacity() >= length) {
                buffer.clear();
                buffer.put(bs, offset, length);
                buffer.flip();
                this.channel.writeInIOThread(buffer, buffer, finishBufferIOThreadHandler);
            } else {
                this.channel.writeInIOThread(bs, offset, length, finishBytesIOThreadHandler);
            }
        }
    }

    public void finish(
            boolean kill, final byte[] bs1, int offset1, int length1, final byte[] bs2, int offset2, int length2) {
        if (kill) {
            refuseAlive();
        }
        if (request.pipelineIndex > 0) {
            boolean allCompleted = this.channel.appendPipeline(
                    request.pipelineIndex, request.pipelineCount, bs1, offset1, length1, bs2, offset2, length2);
            if (allCompleted) {
                request.pipelineCompleted = true;
                this.channel.writePipelineInIOThread(this.finishBytesIOThreadHandler);
            } else {
                removeChannel();
                this.responseConsumer.accept(this);
            }
        } else if (this.channel.hasPipelineData()) {
            this.channel.appendPipeline(
                    request.pipelineIndex, request.pipelineCount, bs1, offset1, length1, bs2, offset2, length2);
            this.channel.writePipelineInIOThread(this.finishBytesIOThreadHandler);
        } else {
            this.channel.writeInIOThread(bs1, offset1, length1, bs2, offset2, length2, finishBytesIOThreadHandler);
        }
    }

    protected void finishBuffers(boolean kill, ByteBuffer... buffers) {
        if (kill) {
            refuseAlive();
        }
        if (request.pipelineIndex > 0) {
            ByteArray array = new ByteArray();
            for (ByteBuffer buffer : buffers) {
                array.put(buffer);
            }
            boolean allCompleted = this.channel.appendPipeline(request.pipelineIndex, request.pipelineCount, array);
            if (allCompleted) {
                request.pipelineCompleted = true;
                this.channel.writeInIOThread(buffers, buffers, this.finishBuffersIOThreadHandler);
            } else {
                AsyncConnection conn = removeChannel();
                if (conn != null) {
                    conn.offerWriteBuffers(buffers);
                }
                this.responseConsumer.accept(this);
            }
        } else if (this.channel.hasPipelineData()) {
            // 先将pipeline数据写入完再写入buffers
            this.channel.writePipelineInIOThread(new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    channel.write(buffers, buffers, finishBuffersIOThreadHandler);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    finishBuffersIOThreadHandler.failed(exc, buffers);
                }
            });
        } else {
            this.channel.writeInIOThread(buffers, buffers, finishBuffersIOThreadHandler);
        }
    }

    protected final void finishBuffer(ByteBuffer buffer) {
        finishBuffers(false, buffer);
    }

    protected final void finishBuffers(ByteBuffer... buffers) {
        finishBuffers(false, buffers);
    }

    protected void finishBuffer(boolean kill, ByteBuffer buffer) {
        finishBuffers(kill, buffer);
    }

    protected void send(final ByteTuple array, final CompletionHandler<Integer, Void> handler) {
        ByteBuffer buffer = this.writeBuffer;
        if (buffer != null && buffer.capacity() >= array.length()) {
            buffer.clear();
            buffer.put(array.content(), array.offset(), array.length());
            buffer.flip();
            this.channel.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    attachment.clear();
                    handler.completed(result, null);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    attachment.clear();
                    handler.failed(exc, null);
                }
            });
        } else {
            this.channel.write(array, handler);
        }
    }

    protected <A> void send(final ByteBuffer buffer, final A attachment, final CompletionHandler<Integer, A> handler) {
        this.channel.write(buffer, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                if (buffer != writeBuffer) {
                    channel.offerWriteBuffer(buffer);
                } else {
                    buffer.clear();
                }
                if (handler != null) {
                    handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (buffer != writeBuffer) {
                    channel.offerWriteBuffer(buffer);
                } else {
                    buffer.clear();
                }
                if (handler != null) {
                    handler.failed(exc, attachment);
                }
            }
        });
    }

    protected <A> void send(final ByteBuffer[] buffers, A attachment, final CompletionHandler<Integer, A> handler) {
        this.channel.write(buffers, attachment, new CompletionHandler<Integer, A>() {

            @Override
            public void completed(Integer result, A attachment) {
                channel.offerWriteBuffers(buffers);
                if (handler != null) {
                    handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                for (ByteBuffer buffer : buffers) {
                    channel.offerWriteBuffer(buffer);
                }
                if (handler != null) {
                    handler.failed(exc, attachment);
                }
            }
        });
    }

    public C getContext() {
        return context;
    }
}
