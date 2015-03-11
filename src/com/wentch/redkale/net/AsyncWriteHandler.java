/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Level;

/**
 *
 * @author zhangjx
 * @param <A>
 */
public final class AsyncWriteHandler<A> implements CompletionHandler<Integer, A> {

    protected final ByteBuffer buffer;

    protected final AsynchronousByteChannel channel;

    protected final Context context;

    protected final ByteBuffer buffer2;

    protected final A attachment;

    protected final CompletionHandler hander;

    public AsyncWriteHandler(Context context, ByteBuffer buffer, AsynchronousByteChannel channel) {
        this(context, buffer, channel, null, null, null);
    }

    public AsyncWriteHandler(Context context, ByteBuffer buffer, AsynchronousByteChannel channel, ByteBuffer buffer2, A attachment, CompletionHandler hander) {
        this.buffer = buffer;
        this.channel = channel;
        this.context = context;
        this.buffer2 = buffer2;
        this.attachment = attachment;
        this.hander = hander;
    }

    @Override
    public void completed(Integer result, A attachment) {
        if (buffer.hasRemaining()) {
            this.channel.write(buffer, attachment, this);
            return;
        }
        if (context != null) context.offerBuffer(buffer);
        if (hander != null) {
            if (buffer2 == null) {
                hander.completed(result, attachment);
            } else {
                this.channel.write(buffer2, attachment, hander);
            }
        }
    }

    @Override
    public void failed(Throwable exc, A attachment) {
        if (context != null) context.offerBuffer(buffer);
        if (hander == null) {
            try {
                this.channel.close();
            } catch (Exception e) {
                context.logger.log(Level.FINE, "AsyncWriteHandler close channel erroneous", e);
            }
        } else {
            hander.failed(exc, attachment);
        }
    }

}
