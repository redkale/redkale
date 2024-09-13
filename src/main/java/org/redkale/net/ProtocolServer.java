/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.util.Set;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.util.*;

/**
 * 协议底层Server
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class ProtocolServer {

    protected final Context context;

    // 最大连接数，小于1表示无限制
    protected int maxConns;

    // 独立创建HttpServer时没有Application
    @Resource(required = false)
    protected Application application;

    public abstract void open(AnyValue config) throws IOException;

    public abstract void bind(SocketAddress local, int backlog) throws IOException;

    public abstract Set<SocketOption<?>> supportedOptions();

    public abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;

    public abstract void accept(Application application, Server server) throws IOException;

    public abstract SocketAddress getLocalAddress() throws IOException;

    public abstract void close() throws IOException;

    protected ProtocolServer(Context context) {
        this.context = context;
        this.maxConns = context.getMaxConns();
    }

    // ---------------------------------------------------------------------
    public static ProtocolServer create(String protocol, Context context, ClassLoader classLoader) {
        if ("TCP".equalsIgnoreCase(protocol)) {
            return new AsyncNioTcpProtocolServer(context);
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            return new AsyncNioUdpProtocolServer(context);
        } else {
            throw new RedkaleException(ProtocolServer.class.getSimpleName() + " not support protocol " + protocol);
        }
    }

    public abstract AsyncGroup getAsyncGroup();

    public abstract long getCreateConnectionCount();

    public abstract long getClosedConnectionCount();

    public abstract long getLivingConnectionCount();
}
