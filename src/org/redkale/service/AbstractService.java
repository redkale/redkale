/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.util.concurrent.*;
import org.redkale.net.WorkThread;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractService implements Service {

    protected Future<?> submit(Runnable runner) {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            return ((WorkThread) thread).submit(runner);
        }
        return ForkJoinPool.commonPool().submit(runner);
    }

    protected ExecutorService getExecutor() {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            return ((WorkThread) thread).getExecutor();
        }
        return ForkJoinPool.commonPool();
    }
}
