/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.SocketAddress;
import java.util.concurrent.*;
import org.redkale.util.ByteBufferPool;

/**
 * Client模式的AsyncConnection连接构造器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public abstract class AsyncGroup {

    public static final int UDP_BUFFER_CAPACITY = Integer.getInteger("redkale.udp.buffer.apacity", 1350);

    public static AsyncGroup create(
            String threadNameFormat,
            final ExecutorService workExecutor,
            final int bufferCapacity,
            final int bufferPoolSize) {
        return new AsyncIOGroup(threadNameFormat, workExecutor, bufferCapacity, bufferPoolSize);
    }

    public static AsyncGroup create(
            String threadNameFormat, ExecutorService workExecutor, ByteBufferPool safeBufferPool) {
        return new AsyncIOGroup(threadNameFormat, workExecutor, safeBufferPool);
    }

    public CompletableFuture<AsyncConnection> createTCPClient(final SocketAddress address) {
        return createTCPClient(address, 0, 0, 0);
    }

    /**
     * 创建TCP连接
     *
     * @see org.redkale.net.AsyncIOGroup#createTCPClient(java.net.SocketAddress, int, int, int)
     *
     * @param address 地址
     * @param connectTimeoutSeconds 连接超时
     * @param readTimeoutSeconds 读超时
     * @param writeTimeoutSeconds 写超时
     * @return AsyncConnection
     */
    public abstract CompletableFuture<AsyncConnection> createTCPClient(
            final SocketAddress address,
            final int connectTimeoutSeconds,
            final int readTimeoutSeconds,
            final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createUDPClient(final SocketAddress address) {
        return createUDPClient(address, 0, 0, 0);
    }

    /**
     * 创建UDP连接
     *
     * @see org.redkale.net.AsyncIOGroup#createUDPClient(java.net.SocketAddress, int, int, int)
     *
     * @param address 地址
     * @param connectTimeoutSeconds 连接超时
     * @param readTimeoutSeconds 读超时
     * @param writeTimeoutSeconds 写超时
     * @return AsyncConnection
     */
    public abstract CompletableFuture<AsyncConnection> createUDPClient(
            final SocketAddress address,
            final int connectTimeoutSeconds,
            final int readTimeoutSeconds,
            final int writeTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final SocketAddress address) {
        return tcp ? createTCPClient(address) : createUDPClient(address);
    }

    public CompletableFuture<AsyncConnection> createClient(
            final boolean tcp,
            final SocketAddress address,
            final int connectTimeoutSeconds,
            final int readTimeoutSeconds,
            final int writeTimeoutSeconds) {
        return tcp
                ? createTCPClient(address, connectTimeoutSeconds, readTimeoutSeconds, writeTimeoutSeconds)
                : createUDPClient(address, connectTimeoutSeconds, readTimeoutSeconds, writeTimeoutSeconds);
    }

    /**
     * 设置超时回调
     *
     * @see org.redkale.net.AsyncIOGroup#scheduleTimeout(java.lang.Runnable, long, java.util.concurrent.TimeUnit)
     *
     * @param callable 回调函数
     * @param delay 延迟时长
     * @param unit 时长单位
     * @return ScheduledFuture
     */
    public abstract ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit);

    /**
     * 启动
     * @see org.redkale.net.AsyncIOGroup#start()
     *
     * @return  AsyncGroup
     */
    public abstract AsyncGroup start();

    /**
     * 关闭
     * @see org.redkale.net.AsyncIOGroup#close()
     *
     * @return  AsyncGroup
     */
    public abstract AsyncGroup close();
}
