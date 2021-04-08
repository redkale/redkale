/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.net.*;
import java.util.*;
import javax.annotation.Resource;
import org.redkale.boot.Application;
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

    protected final Context context;

    //最大连接数，小于1表示无限制
    protected int maxconns;

    @Resource
    protected Application application;

    public abstract void open(AnyValue config) throws IOException;

    public abstract void bind(SocketAddress local, int backlog) throws IOException;

    public abstract <T> Set<SocketOption<?>> supportedOptions();

    public abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;

    public abstract void accept(Application application, Server server) throws IOException;

    public abstract void close() throws IOException;

    protected ProtocolServer(Context context) {
        this.context = context;
        this.maxconns = context.getMaxconns();
    }

    //---------------------------------------------------------------------
    public static ProtocolServer create(String protocol, Context context, ClassLoader classLoader, String netimpl) {
        if (netimpl != null) netimpl = netimpl.trim();
        if ("TCP".equalsIgnoreCase(protocol)) {
            return new AsyncNioTcpProtocolServer(context);
        } else if ("UDP".equalsIgnoreCase(protocol)) {
            return new AsyncNioUdpProtocolServer(context);
        } else if (netimpl == null || netimpl.isEmpty()) {
            throw new RuntimeException(ProtocolServer.class.getSimpleName() + " not support protocol " + protocol);
        }
        try {
            if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();
            Class clazz = classLoader.loadClass(netimpl);
            return (ProtocolServer) clazz.getDeclaredConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            throw new RuntimeException(ProtocolServer.class.getSimpleName() + "(netimple=" + netimpl + ") newinstance error", e);
        }
    }

    public abstract long getCreateConnectionCount();

    public abstract long getClosedConnectionCount();

    public abstract long getLivingConnectionCount();
}
