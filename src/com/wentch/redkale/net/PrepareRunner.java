/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.util.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class PrepareRunner implements Runnable {

    private final AsyncConnection channel;

    private final Context context;

    private ByteBuffer data;

    public PrepareRunner(Context context, AsyncConnection channel, ByteBuffer data) {
        this.context = context;
        this.channel = channel;
        this.data = data;
    }

    @Override
    public void run() {
        final PrepareServlet prepare = context.prepare;
        final ObjectPool<? extends Response> responsePool = context.responsePool;
        if (data != null) {
            final Response response = responsePool.poll();
            response.init(channel);
            try {
                prepare.prepare(data, response.request, response);
            } catch (Throwable t) {
                context.logger.log(Level.WARNING, "prepare servlet abort, forece to close channel ", t);
                response.finish(true);
            }
            return;
        }
        final ByteBuffer buffer = context.pollBuffer();
        try {
            channel.read(buffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer count, Void attachment1) {
                    if (count < 1 && buffer.remaining() == buffer.limit()) {
                        try {
                            context.offerBuffer(buffer);
                            channel.close();
                        } catch (Exception e) {
                            context.logger.log(Level.FINEST, "PrepareRunner close channel erroneous on no read bytes", e);
                        }
                        return;
                    }
//                    {  //测试
//                        buffer.flip();
//                        byte[] bs = new byte[buffer.remaining()];
//                        buffer.get(bs);
//                        System.println(new String(bs));
//                    }
                    buffer.flip();
                    final Response response = responsePool.poll();
                    response.init(channel);
                    try {
                        prepare.prepare(buffer, response.request, response);
                    } catch (Throwable t) {  //此处不可  context.offerBuffer(buffer); 以免prepare.prepare内部异常导致重复 offerBuffer
                        context.logger.log(Level.WARNING, "prepare servlet abort, forece to close channel ", t);
                        response.finish(true);
                    }
                }

                @Override
                public void failed(Throwable exc, Void attachment2) {
                    context.offerBuffer(buffer);
                    try {
                        channel.close();
                    } catch (Exception e) {
                    }
                    if (exc != null) context.logger.log(Level.FINEST, "Servlet Handler read channel erroneous, forece to close channel ", exc);
                }
            });
        } catch (Exception te) {
            context.offerBuffer(buffer);
            try {
                channel.close();
            } catch (Exception e) {
            }
            if (te != null) context.logger.log(Level.FINEST, "Servlet read channel erroneous, forece to close channel ", te);
        }
    }

}
