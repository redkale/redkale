/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractLoop extends Thread {

    private volatile Thread localThread;

    protected volatile boolean closed;

    protected String name;

    protected AbstractLoop(String name) {
        this.name = name;
    }

    @Override
    public final void run() {
        this.localThread = Thread.currentThread();
        beforeLoop();
        while (!closed) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                doLoop();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        afterLoop();
    }

    protected void beforeLoop() {
    }

    protected abstract void doLoop();

    protected void afterLoop() {
    }

    public boolean isSameThread() {
        return localThread == Thread.currentThread();
    }

    public void shutdown() {
        this.closed = true;
    }
}
