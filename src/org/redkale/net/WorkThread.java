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

    protected final ExecutorService workExecutor;

    public WorkThread(String name, ExecutorService workExecutor, Runnable target) {
        super(target);
        if (name != null) setName(name);
        this.workExecutor = workExecutor;
        this.setDaemon(true);
    }

    public void runAsync(Runnable runner) {
        workExecutor.execute(runner);
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    @Override
    public void run() {
        this.localThread = Thread.currentThread();
        super.run();
    }

    public boolean inCurrThread() {
        return this.localThread == Thread.currentThread();
    }

    public boolean inCurrThread(Thread thread) {
        return this.localThread == thread;
    }

}
