/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <P>
 */
public abstract class PrepareServlet<R extends Request, P extends Response<R>> extends Servlet<R, P> {

    protected final AtomicLong executeCounter = new AtomicLong(); //执行请求次数

    protected final AtomicLong illRequestCounter = new AtomicLong(); //错误请求次数

    public final void prepare(final ByteBuffer buffer, final R request, final P response) throws IOException {
        executeCounter.incrementAndGet();
        final int rs = request.readHeader(buffer);
        if (rs < 0) {
            response.context.offerBuffer(buffer);
            if (rs != Integer.MIN_VALUE) illRequestCounter.incrementAndGet();
            response.finish(true);
        } else if (rs == 0) {
            response.context.offerBuffer(buffer);
            request.prepare();
            this.execute(request, response);
        } else {
            buffer.clear();
            final AtomicInteger ai = new AtomicInteger(rs);
            request.channel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    buffer.flip();
                    ai.addAndGet(-request.readBody(buffer));
                    if (ai.get() > 0) {
                        buffer.clear();
                        request.channel.read(buffer, buffer, this);
                    } else {
                        response.context.offerBuffer(buffer);
                        request.prepare();
                        try {
                            execute(request, response);
                        } catch (Exception e) {
                            illRequestCounter.incrementAndGet();
                            response.finish(true);
                            request.context.logger.log(Level.WARNING, "prepare servlet abort, forece to close channel ", e);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    illRequestCounter.incrementAndGet();
                    response.context.offerBuffer(buffer);
                    response.finish(true);
                    if (exc != null) request.context.logger.log(Level.FINER, "Servlet read channel erroneous, forece to close channel ", exc);
                }
            });
        }
    }

}
