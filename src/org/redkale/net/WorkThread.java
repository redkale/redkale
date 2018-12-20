/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.concurrent.*;

/**
 * 协议处理的自定义线程类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WorkThread extends Thread {

    protected Thread localThread;

    private final ExecutorService executor;

    public WorkThread(ExecutorService executor, Runnable runner) {
        super(runner);
        this.executor = executor;
        this.setDaemon(true);
    }

    public void runAsync(Runnable runner) {
        executor.execute(runner);
    }

    public ExecutorService getExecutor() {
        return executor;
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
