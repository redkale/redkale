/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.concurrent.ExecutorService;

/**
 * 协议处理的IO线程类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.5.0
 */
public abstract class AsyncThread extends WorkThread {

    public AsyncThread(String name, int index, int threads, ExecutorService workExecutor, Runnable target) {
        super(name, index, threads, workExecutor, target);
    }

    public static AsyncThread currAsyncThread() {
        Thread t = Thread.currentThread();
        return t instanceof AsyncThread ? (AsyncThread) t : null;
    }

    /**
     * 是否IO线程
     *
     * @return boolean
     */
    @Override
    public final boolean inIO() {
        return true;
    }
}
