/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import org.redkale.net.*;
import org.redkale.util.AnyValue;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class TcpNioProtocolServer extends ProtocolServer {

    private ServerSocketChannel serverChannel;

    private Selector selector;

    private NioThreadGroup ioGroup;

    private Thread acceptThread;

    private boolean closed;

    public TcpNioProtocolServer(Context context) {
        super(context);
    }

    @Override
    public void open(AnyValue config) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.selector = Selector.open();
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
    public <T> Set<SocketOption<?>> supportedOptions() {
        return this.serverChannel.supportedOptions();
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        this.serverChannel.setOption(name, value);
    }

    @Override
    public void accept(Server server) throws IOException {
        this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        this.acceptThread = new Thread() {
            @Override
            public void run() {
                while (!closed) {
                    try {
                        int count = selector.select();
                        if (count == 0) continue;
                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> it = keys.iterator();
                        while (it.hasNext()) {
                            // 获取事件
                            SelectionKey key = it.next();
                            it.remove();
                            if (key.isAcceptable()) accept(key);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        };
        this.acceptThread.start();
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel channel = this.serverChannel.accept();
        channel.configureBlocking(false);
    }

    @Override
    public void close() throws IOException {
        if (this.closed) return;
        this.closed = true;
        this.serverChannel.close();
    }

}
