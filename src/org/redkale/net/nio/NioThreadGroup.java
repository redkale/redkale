/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.util.ObjectPool;

/**
 * 协议处理的IO线程组
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class NioThreadGroup {

    private NioThread[] ioThreads;

    private final AtomicInteger index = new AtomicInteger();

    private ScheduledThreadPoolExecutor timeoutExecutor;

    public NioThreadGroup(int threads, ExecutorService executor, ObjectPool<ByteBuffer> bufferPool) throws IOException {
        this.ioThreads = new NioThread[Math.max(threads, 1)];
        for (int i = 0; i < ioThreads.length; i++) {
            this.ioThreads[i] = new NioThread(Selector.open(), executor, bufferPool);
        }
    }

    public void start() {
        for (int i = 0; i < ioThreads.length; i++) {
            this.ioThreads[i].start();
        }
    }

    public void close() {
        for (int i = 0; i < ioThreads.length; i++) {
            this.ioThreads[i].close();
        }
    }

    public NioThread nextThread() {
        return ioThreads[Math.abs(index.getAndIncrement()) % ioThreads.length];
    }

    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    public void interestOpsOr(NioThread ioThread, SelectionKey key, int opt) {
        if (key == null) return;
        if ((key.interestOps() & opt) != 0) return;
        key.interestOps(key.interestOps() | opt);
        if (ioThread.inSameThread()) return;
        //非IO线程中
        key.selector().wakeup();
    }

}
