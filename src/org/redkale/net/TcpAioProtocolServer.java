/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.Set;
import org.redkale.util.AnyValue;

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
        group = AsynchronousChannelGroup.withCachedThreadPool(context.executor, 1);
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
    public void accept() throws IOException {
        final AsynchronousServerSocketChannel serchannel = this.serverChannel;
        serchannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

            private boolean supportInited;

            private boolean supportTcpLay;

            private boolean supportAlive;

            private boolean supportReuse;

            private boolean supportRcv;

            private boolean supportSnd;

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
                    if (!supportInited) {
                        synchronized (this) {
                            if (!supportInited) {
                                supportInited = true;
                                final Set<SocketOption<?>> options = channel.supportedOptions();
                                supportTcpLay = options.contains(StandardSocketOptions.TCP_NODELAY);
                                supportAlive = options.contains(StandardSocketOptions.SO_KEEPALIVE);
                                supportReuse = options.contains(StandardSocketOptions.SO_REUSEADDR);
                                supportRcv = options.contains(StandardSocketOptions.SO_RCVBUF);
                                supportSnd = options.contains(StandardSocketOptions.SO_SNDBUF);
                            }
                        }
                    }
                    if (supportTcpLay) channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    if (supportAlive) channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    if (supportReuse) channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                    if (supportRcv) channel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
                    if (supportSnd) channel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                AsyncConnection conn = new TcpAioAsyncConnection(channel, context.sslContext, null, context.readTimeoutSeconds, context.writeTimeoutSeconds, null, null);
                conn.livingCounter = livingCounter;
                conn.closedCounter = closedCounter;
                context.runAsync(new PrepareRunner(context, conn, null, null));
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
