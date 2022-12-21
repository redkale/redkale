/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.util.concurrent.*;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.util.ThreadHashExecutor;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractService implements Service {

    @Resource(name = Application.RESNAME_APP_EXECUTOR)
    private ExecutorService workExecutor;

    /**
     * 当前Service类的原始Service类型， 由于Service会动态重载，所以getClass()得到的不是原始Service类型
     *
     * @return Class
     */
    protected Class serviceType() {
        return Sncp.getServiceType(this);
    }

    /**
     * 异步执行任务
     *
     *
     * @param command 任务
     */
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

    /**
     * 异步执行任务
     *
     * @param hash    hash值
     * @param command 任务
     */
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

    /**
     * 获取线程池
     *
     * @return ExecutorService
     */
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
