/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.SocketAddress;
import java.util.concurrent.*;

/**
 * Client模式的AsyncConnection连接构造器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 */
public abstract class AsyncGroup {

    public CompletableFuture<AsyncConnection> createTCP(final SocketAddress address) {
        return createTCP(address, 0, 0);
    }

    public abstract CompletableFuture<AsyncConnection> createTCP(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createUDP(final SocketAddress address) {
        return createUDP(address, 0, 0);
    }

    public abstract CompletableFuture<AsyncConnection> createUDP(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> create(final boolean tcp, final SocketAddress address) {
        return tcp ? createTCP(address) : createUDP(address);
    }

    public CompletableFuture<AsyncConnection> create(final boolean tcp, final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return tcp ? createTCP(address, readTimeoutSeconds, writeTimeoutSeconds) : createUDP(address, readTimeoutSeconds, writeTimeoutSeconds);
    }

    public abstract ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit);
}
