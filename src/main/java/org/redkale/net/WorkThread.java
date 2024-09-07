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
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WorkThread extends Thread implements Executor {

    public static final int DEFAULT_WORK_POOL_SIZE = Utility.cpus() * 8;

    protected final ExecutorService workExecutor;

    // WorkThread下标，从0开始
    private final int index;

    // WorkThread个数
    private final int threads;

    public WorkThread(
            ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, Runnable target) {
        super(g, target);
        if (name != null) {
            setName(name);
        }
        this.index = index;
        this.threads = threads;
        this.workExecutor = workExecutor;
        this.setDaemon(true);
    }

    /**
     * 当前WorkThread线程，不是WorkThread返回null
     *
     * @return WorkThread线程
     */
    public static WorkThread currentWorkThread() {
        Thread t = Thread.currentThread();
        return t instanceof WorkThread ? (WorkThread) t : null;
    }

    /**
     * 创建线程池，当前JDK若支持虚拟线程池，返回虚拟线程池
     *
     * @param threads 线程数
     * @param threadNameFormat 格式化线程名
     * @return 线程池
     */
    public static ExecutorService createWorkExecutor(final int threads, final String threadNameFormat) {
        final Function<String, ExecutorService> func = Utility.virtualExecutorFunction();
        return func == null ? createExecutor(threads, threadNameFormat) : func.apply(threadNameFormat);
    }

    /**
     * 创建线程池
     *
     * @param threads 线程数
     * @param threadNameFormat 格式化线程名
     * @return 线程池
     */
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

    /**
     * 根据线程池大小补位序号
     *
     * @param threads 线程池大小
     * @param index 序号
     * @return 返回固定长度的序号
     */
    static String formatIndex(int threads, int index) {
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

    /**
     * 按以下优先级顺序的线程池执行给定的任务: <br>
     * 1、work线程池 2、虚拟线程 3、当前线程
     *
     * @param command 任务
     */
    @Override
    public void execute(Runnable command) {
        if (workExecutor == null) {
            Utility.execute(command);
        } else {
            workExecutor.execute(command);
        }
    }

    /**
     * 按以下优先级顺序的线程池执行给定的任务集合: <br>
     * 1、work线程池 2、虚拟线程 3、当前线程
     *
     * @param commands 任务集合
     */
    public void execute(Runnable... commands) {
        if (workExecutor == null) {
            for (Runnable command : commands) {
                Utility.execute(command);
            }
        } else {
            for (Runnable command : commands) {
                workExecutor.execute(command);
            }
        }
    }

    /**
     * 按以下优先级顺序的线程池执行给定的任务集合: <br>
     * 1、work线程池 2、虚拟线程 3、当前线程
     *
     * @param commands 任务集合
     */
    public void execute(Collection<Runnable> commands) {
        if (commands == null) {
            return;
        }
        if (workExecutor == null) {
            for (Runnable command : commands) {
                Utility.execute(command);
            }
        } else {
            for (Runnable command : commands) {
                workExecutor.execute(command);
            }
        }
    }

    /**
     * 按以下优先级顺序的线程池执行给定的任务: <br>
     * 1、work线程池 2、虚拟线程 3、当前线程 <br>
     * <b>与execute的区别：子类AsyncIOThread中execute会被重载，确保优先在IO线程中执行</b>
     *
     * @param command 任务
     */
    public final void runWork(Runnable command) {
        if (workExecutor == null) {
            Utility.execute(command);
        } else {
            workExecutor.execute(command);
        }
    }

    /**
     * 获取work线程池
     *
     * @return work线程池
     */
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
     * 判断当前线程是否为当前对象
     *
     * @return 是否一致
     */
    public boolean inCurrThread() {
        return this == Thread.currentThread();
    }

    /**
     * 判断当前线程是否为指定线程
     *
     * @param thread 线程
     * @return 是否一致
     */
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
