/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.redkale.util.*;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class TcpAioProtocolServer extends ProtocolServer {

    private AsynchronousChannelGroup group;

    private AsynchronousServerSocketChannel serverChannel;

    public TcpAioProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        //group = AsynchronousChannelGroup.withThreadPool(context.executor);
        group = AsynchronousChannelGroup.withFixedThreadPool(context.executor.getCorePoolSize(), context.executor.getThreadFactory());
        this.serverChannel = AsynchronousServerSocketChannel.open(group);

        final Set<SocketOption<?>> options = this.serverChannel.supportedOptions();
        if (options.contains(StandardSocketOptions.TCP_NODELAY)) {
            this.serverChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        }
        if (options.contains(StandardSocketOptions.SO_KEEPALIVE)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        }
        if (options.contains(StandardSocketOptions.SO_REUSEADDR)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        if (options.contains(StandardSocketOptions.SO_RCVBUF)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
        }
        if (options.contains(StandardSocketOptions.SO_SNDBUF)) {
            this.serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
        }
    }

    @Override
    public void bind(SocketAddress local, int backlog) throws IOException {
        this.serverChannel.bind(local, backlog);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        this.serverChannel.setOption(name, value);
    }

    @Override
    public <T> Set<SocketOption<?>> supportedOptions() {
        return this.serverChannel.supportedOptions();
    }

    @Override
    public void accept(Server server) throws IOException {
        AtomicLong createBufferCounter = new AtomicLong();
        AtomicLong cycleBufferCounter = new AtomicLong();
        ObjectPool<ByteBuffer> bufferPool = server.createBufferPool(createBufferCounter, cycleBufferCounter, server.bufferPoolSize);
        AtomicLong createResponseCounter = new AtomicLong();
        AtomicLong cycleResponseCounter = new AtomicLong();
        ObjectPool<Response> responsePool = server.createResponsePool(createResponseCounter, cycleResponseCounter, server.responsePoolSize);
        responsePool.setCreator(server.createResponseCreator(bufferPool, responsePool));
        final AsynchronousServerSocketChannel serchannel = this.serverChannel;
        serchannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

            @Override
            public void completed(final AsynchronousSocketChannel channel, Void attachment) {
                serchannel.accept(null, this);
                if (maxconns > 0 && livingCounter.get() >= maxconns) {
                    try {
                        channel.close();
                    } catch (Exception e) {
                    }
                    return;
                }
                createCounter.incrementAndGet();
                livingCounter.incrementAndGet();
                try {
                    channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                    channel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
                    channel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);

                    AsyncConnection conn = new TcpAioAsyncConnection(bufferPool, bufferPool, channel,
                        context.getSSLContext(), null, context.readTimeoutSeconds, context.writeTimeoutSeconds, livingCounter, closedCounter);
                    context.runAsync(new PrepareRunner(context, responsePool, conn, null, null));
                } catch (Throwable e) {
                    context.logger.log(Level.INFO, channel + " accept error", e);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                serchannel.accept(null, this);
                //if (exc != null) context.logger.log(Level.FINEST, AsynchronousServerSocketChannel.class.getSimpleName() + " accept erroneous", exc);
            }
        });
    }

    @Override
    public void close() throws IOException {
        this.serverChannel.close();
    }

}
