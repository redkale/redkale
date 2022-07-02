/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import org.redkale.util.ObjectPool;

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

    public static AsyncGroup create(String threadPrefixName, final ExecutorService workExecutor, final int bufferCapacity, final int bufferPoolSize) {
        return new AsyncIOGroup(true, threadPrefixName, workExecutor, bufferCapacity, bufferPoolSize);
    }

    public static AsyncGroup create(String threadPrefixName, ExecutorService workExecutor, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        return new AsyncIOGroup(true, threadPrefixName, workExecutor, bufferCapacity, safeBufferPool);
    }

    public static AsyncGroup create(boolean client, String threadPrefixName, final ExecutorService workExecutor, final int bufferCapacity, final int bufferPoolSize) {
        return new AsyncIOGroup(client, threadPrefixName, workExecutor, bufferCapacity, bufferPoolSize);
    }

    public static AsyncGroup create(boolean client, String threadPrefixName, ExecutorService workExecutor, final int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        return new AsyncIOGroup(client, threadPrefixName, workExecutor, bufferCapacity, safeBufferPool);
    }

    public CompletableFuture<AsyncConnection> createTCPClient(final SocketAddress address) {
        return createTCPClient(address, 0, 0);
    }

    public abstract CompletableFuture<AsyncConnection> createTCPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createUDPClient(final SocketAddress address) {
        return createUDPClient(address, 0, 0);
    }

    public abstract CompletableFuture<AsyncConnection> createUDPClient(final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final SocketAddress address) {
        return tcp ? createTCPClient(address) : createUDPClient(address);
    }

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final SocketAddress address, final int readTimeoutSeconds, final int writeTimeoutSeconds) {
        return tcp ? createTCPClient(address, readTimeoutSeconds, writeTimeoutSeconds) : createUDPClient(address, readTimeoutSeconds, writeTimeoutSeconds);
    }

    public abstract ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit);

    public abstract AsyncGroup start();

    public abstract AsyncGroup close();
}