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

    private NioThread[] threads;

    private final AtomicInteger index = new AtomicInteger();

    private ScheduledThreadPoolExecutor timeoutExecutor;

    public NioThreadGroup(int threads, ObjectPool<ByteBuffer> bufferPool) throws IOException {
        this.threads = new NioThread[Math.max(threads, 1)];
        for (int i = 0; i < this.threads.length; i++) {
            ObjectPool<ByteBuffer> threadBufferPool = ObjectPool.createUnsafePool(bufferPool.getCreatCounter(),
                bufferPool.getCycleCounter(), 8,
                bufferPool.getCreator(), bufferPool.getPrepare(), bufferPool.getRecycler());
            this.threads[i] = new NioThread(Selector.open(), threadBufferPool);
        }
        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setName(this.getClass().getSimpleName() + "-Timeout-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        for (NioThread thread : threads) {
            thread.start();
        }
    }

    public void close() {
        for (NioThread thread : threads) {
            thread.close();
        }
        this.timeoutExecutor.shutdownNow();
    }

    public NioThread nextThread() {
        return threads[Math.abs(index.getAndIncrement()) % threads.length];
    }

    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    public void interestOpsOr(NioThread thread, SelectionKey key, int opt) {
        if (key == null) return;
        if (key.selector() != thread.selector) throw new RuntimeException("NioThread.selector not the same to SelectionKey.selector");
        if ((key.interestOps() & opt) != 0) return;
        key.interestOps(key.interestOps() | opt);
        if (thread.inSameThread()) return;
        //非IO线程中
        key.selector().wakeup();
    }

}
