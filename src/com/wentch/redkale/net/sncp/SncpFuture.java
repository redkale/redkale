/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import java.util.concurrent.*;

/**
 * 简单的Future实现， set、get方法均只能一个线程调用
 *
 * @author zhangjx
 * @param <T>
 */
public class SncpFuture<T> implements Future<T> {

    private volatile boolean done;

    private T result;

    private RuntimeException ex;

    public SncpFuture() {
    }

    public SncpFuture(T result) {
        this.result = result;
        this.done = true;
    }

    public void set(T result) {
        this.result = result;
        this.done = true;
        synchronized (this) {
            notifyAll();
        }
    }

    public void set(RuntimeException ex) {
        this.ex = ex;
        this.done = true;
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (done) {
            if (ex != null) throw ex;
            return result;
        }
        synchronized (this) {
            if (!done) wait(10_000);
        }
        if (done) {
            if (ex != null) throw ex;
            return result;
        }
        throw new InterruptedException();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (done) {
            if (ex != null) throw ex;
            return result;
        }
        synchronized (this) {
            if (!done) wait(unit.toMillis(timeout));
        }
        if (done) {
            if (ex != null) throw ex;
            return result;
        }
        throw new TimeoutException();
    }
}
