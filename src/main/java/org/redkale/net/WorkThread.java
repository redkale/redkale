/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.redkale.util.Utility;

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

    private final int index;   //WorkThread下标，从0开始

    private final int threads; //WorkThread个数

    public WorkThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, Runnable target) {
        super(g, target);
        if (name != null) {
            setName(name);
        }
        this.index = index;
        this.threads = threads;
        this.workExecutor = workExecutor;
        this.setDaemon(true);
    }

    public static WorkThread currentWorkThread() {
        Thread t = Thread.currentThread();
        return t instanceof WorkThread ? (WorkThread) t : null;
    }

    public static ExecutorService createWorkExecutor(final int threads, final String threadNameFormat) {
        final Function<String, ExecutorService> func = Utility.virtualExecutorFunction();
        return func == null ? createExecutor(threads, threadNameFormat) : func.apply(threadNameFormat);
    }

    public static ExecutorService createExecutor(final int threads, final String threadNameFormat) {
        final AtomicReference<ExecutorService> ref = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();
        final ThreadGroup g = new ThreadGroup(String.format(threadNameFormat, "Group"));
        return Executors.newFixedThreadPool(threads, (Runnable r) -> {
            int i = counter.get();
            int c = counter.incrementAndGet();
            String threadName = String.format(threadNameFormat, formatIndex(threads, c));
            Thread t = new WorkThread(g, threadName, i, threads, ref.get(), r);
            return t;
        });
    }

    public static String formatIndex(int threads, int index) {
        String v = String.valueOf(index);
        if (threads >= 100) {
            if (index < 10) {
                v = "00" + v;
            } else if (index < 100) {
                v = "0" + v;
            }
        } else if (threads >= 10) {
            if (index < 10) {
                v = "0" + v;
            }
        }
        return v;
    }

    @Override
    public void execute(Runnable command) {
        if (workExecutor == null) {
            command.run();
        } else {
            workExecutor.execute(command);
        }
    }

    //与execute的区别在于子类AsyncIOThread中execute会被重载，确保在IO线程中执行
    public final void runWork(Runnable command) {
        execute(command);
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
