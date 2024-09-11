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

    public CompletableFuture<AsyncConnection> createTCPClientConnection(final SocketAddress address) {
        return createTCPClientConnection(-1, address, 0);
    }

    public CompletableFuture<AsyncConnection> createTCPClientConnection(int ioIndex, SocketAddress address) {
        return createTCPClientConnection(ioIndex, address, 0);
    }

    public CompletableFuture<AsyncConnection> createTCPClientConnection(
            SocketAddress address, int connectTimeoutSeconds) {
        return createTCPClientConnection(-1, address, connectTimeoutSeconds);
    }

    /**
     * 创建TCP连接
     *
     * @see org.redkale.net.AsyncIOGroup#createTCPClientConnection(int, java.net.SocketAddress, int)
     *
     * @param ioIndex IO线程的下坐标
     * @param address 地址
     * @param connectTimeoutSeconds 连接超时
     * @return AsyncConnection
     */
    public abstract CompletableFuture<AsyncConnection> createTCPClientConnection(
            int ioIndex, SocketAddress address, int connectTimeoutSeconds);

    /**
     * 创建UDP连接
     *
     * @see org.redkale.net.AsyncIOGroup#createUDPClientConnection(int, java.net.SocketAddress, int)
     *
     * @param ioIndex IO线程的下坐标
     * @param address 地址
     * @param connectTimeoutSeconds 连接超时
     * @return AsyncConnection
     */
    public abstract CompletableFuture<AsyncConnection> createUDPClientConnection(
            int ioIndex, SocketAddress address, int connectTimeoutSeconds);

    public CompletableFuture<AsyncConnection> createUDPClientConnection(final SocketAddress address) {
        return createUDPClientConnection(-1, address, 0);
    }

    public CompletableFuture<AsyncConnection> createUDPClientConnection(int ioIndex, SocketAddress address) {
        return createUDPClientConnection(ioIndex, address, 0);
    }

    public CompletableFuture<AsyncConnection> createUDPClientConnection(
            SocketAddress address, int connectTimeoutSeconds) {
        return createUDPClientConnection(-1, address, connectTimeoutSeconds);
    }

    public CompletableFuture<AsyncConnection> createClientConnection(
            final boolean tcp, int ioIndex, final SocketAddress address) {
        return tcp ? createTCPClientConnection(ioIndex, address) : createUDPClientConnection(ioIndex, address);
    }

    public CompletableFuture<AsyncConnection> createClientConnection(
            boolean tcp, int ioIndex, SocketAddress address, int connectTimeoutSeconds) {
        return tcp
                ? createTCPClientConnection(ioIndex, address, connectTimeoutSeconds)
                : createUDPClientConnection(ioIndex, address, connectTimeoutSeconds);
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
