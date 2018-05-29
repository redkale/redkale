/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.util.AnyValue;

/**
 * 协议底层Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class ProtocolServer {

    protected static final boolean supportTcpNoDelay;

    protected static final boolean supportTcpKeepAlive;

    static {
        boolean tcpNoDelay = false;
        boolean keepAlive = false;
        try {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            tcpNoDelay = channel.supportedOptions().contains(StandardSocketOptions.TCP_NODELAY);
            keepAlive = channel.supportedOptions().contains(StandardSocketOptions.SO_KEEPALIVE);
            channel.close();
        } catch (Exception e) {
        }
        supportTcpNoDelay = tcpNoDelay;
        supportTcpKeepAlive = keepAlive;
    }

    //创建数
    protected final AtomicLong createCounter = new AtomicLong();

    //关闭数
    protected final AtomicLong closedCounter = new AtomicLong();

    //在线数
    protected final AtomicLong livingCounter = new AtomicLong();

    protected final Context context;

    //最大连接数，小于1表示无限制
    protected int maxconns;

    public abstract void open(AnyValue config) throws IOException;

    public abstract void bind(SocketAddress local, int backlog) throws IOException;

    public abstract <T> Set<SocketOption<?>> supportedOptions();

    public abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;

    public abstract void accept() throws IOException;

    public abstract void close() throws IOException;

    protected ProtocolServer(Context context) {
        this.context = context;
        this.maxconns = context.getMaxconns();
    }

    public long getCreateCount() {
        return createCounter.longValue();
    }

    public long getClosedCount() {
        return closedCounter.longValue();
    }

    public long getLivingCount() {
        return livingCounter.longValue();
    }

    //---------------------------------------------------------------------
    public static ProtocolServer create(String protocol, Context context, ClassLoader classLoader, String netimpl) {
        if (netimpl != null) netimpl = netimpl.trim();
        if ("TCP".equalsIgnoreCase(protocol)) {
            if (netimpl == null || netimpl.isEmpty()) {
                return new TcpAioProtocolServer(context);
            } else if ("aio".equalsIgnoreCase(netimpl)) {
                return new TcpAioProtocolServer(context);
            } else if ("nio".equalsIgnoreCase(netimpl)) {
                return new TcpNioProtocolServer(context);
            }
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            if (netimpl == null || netimpl.isEmpty()) {
                return new UdpBioProtocolServer(context);
            } else if ("bio".equalsIgnoreCase(netimpl)) {
                return new UdpBioProtocolServer(context);
            }
        } else if (netimpl == null || netimpl.isEmpty()) {
            throw new RuntimeException("ProtocolServer not support protocol " + protocol);
        }
        try {
            if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();
            Class clazz = classLoader.loadClass(netimpl);
            return (ProtocolServer) clazz.getDeclaredConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            throw new RuntimeException("ProtocolServer(netimple=" + netimpl + ") newinstance error", e);
        }
    }

    public static boolean supportTcpNoDelay() {
        return supportTcpNoDelay;
    }

    public static boolean supportTcpKeepAlive() {
        return supportTcpKeepAlive;
    }

}
