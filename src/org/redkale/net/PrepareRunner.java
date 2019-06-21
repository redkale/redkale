/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.redkale.util.*;

/**
 * 根Servlet的处理逻辑类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class PrepareRunner implements Runnable {

    private final AsyncConnection channel;

    private final Context context;

    private final ObjectPool<Response> responsePool;

    private ByteBuffer data;

    private Response response;

    public PrepareRunner(Context context, ObjectPool<Response> responsePool, AsyncConnection channel, ByteBuffer data, Response response) {
        this.context = context;
        this.responsePool = responsePool;
        this.channel = channel;
        this.data = data;
        this.response = response;
    }

    @Override
    public void run() {
        if (data != null) { //BIO模式的UDP连接创建AsyncConnection时已经获取到ByteBuffer数据了
            if (response == null) response = responsePool.get();
            try {
                response.init(channel);
                codec(data, response);
            } catch (Throwable t) {
                context.logger.log(Level.WARNING, "prepare servlet abort, force to close channel ", t);
                response.finish(true);
            }
            return;
        }
        if (response == null) response = responsePool.get();
        try {
            channel.read(new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer count, ByteBuffer buffer) {
                    if (count < 1) {
                        response.request.offerReadBuffer(buffer);
                        channel.dispose();// response.init(channel); 在调用之前异常
                        response.removeChannel();
                        response.finish(true);
                        return;
                    }
//                    {  //测试
//                        buffer.flip();
//                        byte[] bs = new byte[buffer.remaining()];
//                        buffer.get(bs);
//                        System.println(new String(bs));
//                    }
                    buffer.flip();
                    try {
                        response.init(channel);
                        codec(buffer, response);
                    } catch (Throwable t) {  //此处不可  context.offerBuffer(buffer); 以免prepare.prepare内部异常导致重复 offerBuffer
                        context.logger.log(Level.WARNING, "prepare servlet abort, force to close channel ", t);
                        response.finish(true);
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    response.request.offerReadBuffer(buffer);
                    channel.dispose();// response.init(channel); 在调用之前异常
                    response.removeChannel();
                    response.finish(true);
                    if (exc != null && context.logger.isLoggable(Level.FINEST)) {
                        context.logger.log(Level.FINEST, "Servlet Handler read channel erroneous, force to close channel ", exc);
                    }
                }
            });
        } catch (Exception te) {
            channel.dispose();// response.init(channel); 在调用之前异常
            response.removeChannel();
            response.finish(true);
            if (te != null && context.logger.isLoggable(Level.FINEST)) {
                context.logger.log(Level.FINEST, "Servlet read channel erroneous, force to close channel ", te);
            }
        }
    }

    protected void codec(final ByteBuffer buffer, final Response response) throws IOException {
        final Request request = response.request;
        final PrepareServlet preparer = context.prepare;
        preparer.executeCounter.incrementAndGet();
        final int rs = request.readHeader(buffer);
        if (rs < 0) {  //表示数据格式不正确
            channel.offerBuffer(buffer);
            if (rs != Integer.MIN_VALUE) preparer.illRequestCounter.incrementAndGet();
            response.finish(true);
        } else if (rs == 0) {
            if (buffer.hasRemaining()) {
                request.setMoredata(buffer);
            } else {
                response.request.offerReadBuffer(buffer);
            }
            preparer.prepare(request, response);
        } else {
            buffer.clear();
            channel.setReadBuffer(buffer);
            final AtomicInteger ai = new AtomicInteger(rs);
            channel.read(new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    attachment.flip();
                    ai.addAndGet(-request.readBody(attachment));
                    if (ai.get() > 0) {
                        attachment.clear();
                        channel.setReadBuffer(attachment);
                        channel.read(this);
                    } else {
                        if (attachment.hasRemaining()) {
                            request.setMoredata(attachment);
                        } else {
                            response.request.offerReadBuffer(attachment);
                        }
                        try {
                            preparer.prepare(request, response);
                        } catch (Throwable t) {  //此处不可  context.offerBuffer(buffer); 以免preparer.prepare内部异常导致重复 offerBuffer
                            context.logger.log(Level.WARNING, "prepare servlet abort, force to close channel ", t);
                            response.finish(true);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    preparer.illRequestCounter.incrementAndGet();
                    response.request.offerReadBuffer(attachment);
                    response.finish(true);
                    if (exc != null) request.context.logger.log(Level.FINER, "Servlet read channel erroneous, force to close channel ", exc);
                }
            });
        }
    }

    protected void initResponse(Response response, AsyncConnection channel) {
        response.init(channel);
    }

    protected Response pollResponse() {
        return responsePool.get();
    }

    protected Request pollRequest(Response response) {
        return response.request;
    }

    protected AsyncConnection removeChannel(Response response) {
        return response.removeChannel();
    }

    protected ByteBuffer pollReadBuffer(Request request) {
        return request.pollReadBuffer();
    }

    protected ByteBuffer pollReadBuffer(Response response) {
        return response.request.pollReadBuffer();
    }

    protected void offerReadBuffer(Request request, ByteBuffer buffer) {
        request.offerReadBuffer(buffer);
    }

    protected void offerReadBuffer(Response response, ByteBuffer buffer) {
        response.request.offerReadBuffer(buffer);
    }
}
