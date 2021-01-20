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
import org.redkale.net.Response;
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

    public NioThreadGroup(final String serverName, ExecutorService workExecutor, int iothreads,
        ObjectPool<ByteBuffer> bufferPool, ObjectPool<Response> responsePool) throws IOException {
        this.threads = new NioThread[Math.max(iothreads, 1)];
        for (int i = 0; i < this.threads.length; i++) {
            ObjectPool<ByteBuffer> threadBufferPool = ObjectPool.createUnsafePool(bufferPool.getCreatCounter(),
                bufferPool.getCycleCounter(), 8,
                bufferPool.getCreator(), bufferPool.getPrepare(), bufferPool.getRecycler());

            ObjectPool<Response> threadResponsePool = ObjectPool.createUnsafePool(responsePool.getCreatCounter(),
                responsePool.getCycleCounter(), 8,
                responsePool.getCreator(), responsePool.getPrepare(), responsePool.getRecycler());
            String name = "Redkale-" + serverName + "-ServletThread" + "-" + (i >= 9 ? (i + 1) : ("0" + (i + 1)));
            this.threads[i] = new NioThread(name, workExecutor, Selector.open(), threadBufferPool, threadResponsePool);
        }
        this.timeoutExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setName("Redkale-" + serverName + "-IOTimeoutThread");
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
        if (thread.inCurrThread()) return;
        //非IO线程中
        key.selector().wakeup();
    }

}
