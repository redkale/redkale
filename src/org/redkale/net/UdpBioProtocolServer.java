/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.util.*;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class UdpBioProtocolServer extends ProtocolServer {

    private boolean running;

    private DatagramChannel serverChannel;

    public UdpBioProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        DatagramChannel ch = DatagramChannel.open();
        ch.configureBlocking(true);
        this.serverChannel = ch;
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
        this.serverChannel.bind(local);
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
        final DatagramChannel serchannel = this.serverChannel;
        final int readTimeoutSeconds = this.context.readTimeoutSeconds;
        final int writeTimeoutSeconds = this.context.writeTimeoutSeconds;
        final CountDownLatch cdl = new CountDownLatch(1);
        this.running = true;
        new Thread() {
            @Override
            public void run() {
                cdl.countDown();
                while (running) {
                    final ByteBuffer buffer = bufferPool.get();
                    try {
                        SocketAddress address = serchannel.receive(buffer);
                        buffer.flip();
                        AsyncConnection conn = new UdpBioAsyncConnection(bufferPool, bufferPool, serchannel,
                            context.getSSLContext(), address, false, readTimeoutSeconds, writeTimeoutSeconds, null, null);
                        context.runAsync(new PrepareRunner(context, responsePool, conn, buffer, null));
                    } catch (Exception e) {
                        bufferPool.accept(buffer);
                    }
                }
            }
        }.start();
        try {
            cdl.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        this.running = false;
        this.serverChannel.close();
    }

    @Override
    public long getCreateCount() {
        return -1;
    }

    @Override
    public long getClosedCount() {
        return -1;
    }

    @Override
    public long getLivingCount() {
        return -1;
    }
}
