/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.Collection;
import java.util.concurrent.*;
import org.redkale.util.ThreadHashExecutor;

/**
 * 协议处理的自定义线程类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WorkThread extends Thread implements Executor {

    protected final ExecutorService workExecutor;

    protected final ThreadHashExecutor hashExecutor;

    private final int index;   //WorkThread下标，从0开始

    private final int threads; //WorkThread个数

    public WorkThread(String name, int index, int threads, ExecutorService workExecutor, Runnable target) {
        super(target);
        if (name != null) {
            setName(name);
        }
        this.index = index;
        this.threads = threads;
        this.workExecutor = workExecutor;
        this.hashExecutor = workExecutor instanceof ThreadHashExecutor ? (ThreadHashExecutor) workExecutor : null;
        this.setDaemon(true);
    }

    public static WorkThread currWorkThread() {
        Thread t = Thread.currentThread();
        return t instanceof WorkThread ? (WorkThread) t : null;
    }

    @Override
    public void execute(Runnable command) {
        if (workExecutor == null) {
            command.run();
        } else {
            workExecutor.execute(command);
        }
    }

    public void execute(Runnable... commands) {
        if (workExecutor == null) {
            for (Runnable command : commands) {
                command.run();
            }
        } else {
            for (Runnable command : commands) {
                workExecutor.execute(command);
            }
        }
    }

    public void execute(Collection<Runnable> commands) {
        if (commands == null) {
            return;
        }
        if (workExecutor == null) {
            for (Runnable command : commands) {
                command.run();
            }
        } else {
            for (Runnable command : commands) {
                workExecutor.execute(command);
            }
        }
    }

    public void runAsync(Runnable command) {
        if (workExecutor == null) {
            ForkJoinPool.commonPool().execute(command);
        } else {
            workExecutor.execute(command);
        }
    }

    public void runAsync(int hash, Runnable command) {
        if (hashExecutor == null) {
            if (workExecutor == null) {
                ForkJoinPool.commonPool().execute(command);
            } else {
                workExecutor.execute(command);
            }
        } else {
            hashExecutor.execute(hash, command);
        }
    }

    public ExecutorService getWorkExecutor() {
        return workExecutor;
    }

    /**
     * 是否IO线程
     *
     * @since 2.6.0
     * @return boolean
     */
    public boolean inIO() {
        return false;
    }

    /**
     * 是否客户端的IO线程
     *
     * @since 2.8.0
     * @return boolean
     */
    public boolean inClient() {
        return false;
    }

    public boolean inCurrThread() {
        return this == Thread.currentThread();
    }

    public boolean inCurrThread(Thread thread) {
        return this == thread;
    }

    /**
     * 获取线程池数组下标， 从0开始
     *
     * @return int
     */
    public int index() {
        return index;
    }

    /**
     * 获取线程池数组大小，不属于任何数组返回0
     *
     * @return int
     */
    public int threads() {
        return threads;
    }

}
