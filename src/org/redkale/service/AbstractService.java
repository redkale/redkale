/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.util.concurrent.*;
import javax.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.*;
import org.redkale.util.ThreadHashExecutor;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractService implements Service {

    @Resource(name = Application.RESNAME_APP_EXECUTOR)
    private ExecutorService workExecutor;

    protected void runAsync(Runnable command) {
        if (workExecutor != null) {
            workExecutor.execute(command);
        } else {
            Thread thread = Thread.currentThread();
            if (thread instanceof WorkThread) {
                ((WorkThread) thread).runAsync(command);
            } else {
                ForkJoinPool.commonPool().execute(command);
            }
        }
    }

    protected void runAsync(int hash, Runnable command) {
        if (workExecutor != null) {
            if (workExecutor instanceof ThreadHashExecutor) {
                ((ThreadHashExecutor) workExecutor).execute(hash, command);
            } else {
                Thread thread = Thread.currentThread();
                if (thread instanceof WorkThread) {
                    ((WorkThread) thread).runAsync(hash, command);
                } else {
                    workExecutor.execute(command);
                }
            }
        } else {
            Thread thread = Thread.currentThread();
            if (thread instanceof WorkThread) {
                ((WorkThread) thread).runAsync(hash, command);
            } else {
                ForkJoinPool.commonPool().execute(command);
            }
        }
    }

    protected ExecutorService getExecutor() {
        if (workExecutor != null) return workExecutor;
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            ExecutorService e = ((WorkThread) thread).getWorkExecutor();
            if (e != null) return e;
        }
        return ForkJoinPool.commonPool();
    }
}
