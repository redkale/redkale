/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 *
 * @author zhangjx
 */
public abstract class ProtocolServer {

    public abstract void open() throws IOException;

    public abstract void bind(SocketAddress local, int backlog) throws IOException;

    public abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;

    public abstract void accept();

    public abstract void close() throws IOException;

    public abstract AsynchronousChannelGroup getChannelGroup();

    //---------------------------------------------------------------------
    public static ProtocolServer create(String protocol, Context context) {
        if ("TCP".equalsIgnoreCase(protocol)) return new ProtocolTCPServer(context);
        if ("UDP".equalsIgnoreCase(protocol)) return new ProtocolUDPServer(context);
        throw new RuntimeException("ProtocolServer not support protocol " + protocol);
    }

    private static final class ProtocolUDPServer extends ProtocolServer {

        private final Context context;

        private AsynchronousChannelGroup group;

        private AsyncDatagramChannel serverChannel;

        public ProtocolUDPServer(Context context) {
            this.context = context;
        }

        @Override
        public void open() throws IOException {
            this.group = AsynchronousChannelGroup.withCachedThreadPool(context.executor, 1);
            this.serverChannel = AsyncDatagramChannel.open(group);
        }

        @Override
        public void bind(SocketAddress local, int backlog) throws IOException {
            this.serverChannel.bind(local);
        }

        @Override
        public <T> void setOption(SocketOption<T> name, T value) throws IOException {
            this.serverChannel.setOption(name, value);
        }

        @Override
        public void accept() {
            final AsyncDatagramChannel serchannel = this.serverChannel;
            final ByteBuffer buffer = this.context.pollBuffer();
            serchannel.receive(buffer, buffer, new CompletionHandler<SocketAddress, ByteBuffer>() {

                @Override
                public void completed(final SocketAddress address, ByteBuffer attachment) {
                    final ByteBuffer buffer2 = context.pollBuffer();
                    serchannel.receive(buffer2, buffer2, this);
                    attachment.flip();
                    AsyncConnection conn = AsyncConnection.create(serchannel, address, false, context.readTimeoutSecond, context.writeTimeoutSecond);
                    context.submit(new PrepareRunner(context, conn, attachment));
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    context.offerBuffer(attachment);
                    //if (exc != null) context.logger.log(Level.FINEST, AsyncDatagramChannel.class.getSimpleName() + " accept erroneous", exc);
                }
            });
        }

        @Override
        public void close() throws IOException {
            this.serverChannel.close();
        }

        @Override
        public AsynchronousChannelGroup getChannelGroup() {
            return this.group;
        }

    }

    private static final class ProtocolTCPServer extends ProtocolServer {

        private final Context context;

        private AsynchronousChannelGroup group;

        private AsynchronousServerSocketChannel serverChannel;

        public ProtocolTCPServer(Context context) {
            this.context = context;
        }

        @Override
        public void open() throws IOException {
            group = AsynchronousChannelGroup.withCachedThreadPool(context.executor, 1);
            this.serverChannel = AsynchronousServerSocketChannel.open(group);
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
        public void accept() {
            final AsynchronousServerSocketChannel serchannel = this.serverChannel;
            serchannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

                @Override
                public void completed(final AsynchronousSocketChannel channel, Void attachment) {
                    serchannel.accept(null, this);
                    context.submit(new PrepareRunner(context, AsyncConnection.create(channel, null, context.readTimeoutSecond, context.writeTimeoutSecond), null));
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

        @Override
        public AsynchronousChannelGroup getChannelGroup() {
            return this.group;
        }
    }

}
