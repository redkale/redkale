/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import org.redkale.util.*;

/**
 * 协议处理的IO线程类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class IOThread extends Thread {

    protected Thread localThread;

    protected final ExecutorService executor;

    protected ObjectPool<ByteBuffer> bufferPool;

    public IOThread(ExecutorService executor, ObjectPool<ByteBuffer> bufferPool, Runnable runner) {
        super(runner);
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.setDaemon(true);
    }

    public void runAsync(Runnable runner) {
        executor.execute(runner);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ObjectPool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    @Override
    public void run() {
        this.localThread = Thread.currentThread();
        super.run();
    }

    public boolean inSameThread() {
        return this.localThread == Thread.currentThread();
    }

    public boolean inSameThread(Thread thread) {
        return this.localThread == thread;
    }

}
