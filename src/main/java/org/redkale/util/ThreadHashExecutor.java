/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class ThreadHashExecutor extends AbstractExecutorService {

    private final LinkedBlockingQueue<Runnable>[] queues;

    private final ThreadPoolExecutor[] executors;

    public ThreadHashExecutor() {
        this(Utility.cpus(), null);
    }

    public ThreadHashExecutor(int size) {
        this(size, null);
    }

    public ThreadHashExecutor(int size, ThreadFactory factory) {
        ThreadPoolExecutor[] array = new ThreadPoolExecutor[size];
        LinkedBlockingQueue[] ques = new LinkedBlockingQueue[size];
        final AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < array.length; i++) {
            LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
            ques[i] = queue;
            array[i] = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue,
                factory == null ? (Runnable r) -> {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        int c = counter.incrementAndGet();
                        t.setName("Redkale-HashThread-" + (c > 9 ? c : ("0" + c)));
                        return t;
                    } : factory);
        }
        this.queues = ques;
        this.executors = array;
    }

    private ExecutorService hashExecutor(int hash) {
        if (hash == 0) {
            int k = 0;
            int minsize = queues[0].size();
            for (int i = 1; i < queues.length; i++) {
                int size = queues[i].size();
                if (size < minsize) {
                    minsize = size;
                    k = i;
                }
            }
            return this.executors[k];
        } else {
            return this.executors[(hash < 0 ? -hash : hash) % this.executors.length];
        }
    }

    public void setThreadFactory(ThreadFactory factory) {
        for (ThreadPoolExecutor executor : this.executors) {
            executor.setThreadFactory(factory);
        }
    }

    public int size() {
        return executors.length;
    }

    @Override
    public void execute(Runnable command) {
        hashExecutor(0).execute(command);
    }

    public void execute(int hash, Runnable command) {
        hashExecutor(hash).execute(command);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return hashExecutor(0).submit(task);
    }

    public Future<?> submit(int hash, Runnable task) {
        return hashExecutor(hash).submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return hashExecutor(0).submit(task, result);
    }

    public <T> Future<T> submit(int hash, Runnable task, T result) {
        return hashExecutor(hash).submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return hashExecutor(0).submit(task);
    }

    public <T> Future<T> submit(int hash, Callable<T> task) {
        return hashExecutor(hash).submit(task);
    }

    public int waitingSize() {
        int wsize = queues[0].size();
        for (int i = 1; i < queues.length; i++) {
            wsize += queues[i].size();
        }
        return wsize;
    }

    @Override
    public void shutdown() {
        for (ExecutorService executor : this.executors) {
            executor.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> list = new ArrayList<>();
        for (ExecutorService executor : this.executors) {
            list.addAll(executor.shutdownNow());
        }
        return list;
    }

    @Override
    public boolean isShutdown() {
        return this.executors[0].isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return this.executors[0].isTerminated();
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit tu) throws InterruptedException {
        return this.executors[0].awaitTermination(l, tu);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return hashExecutor(0).invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return hashExecutor(0).invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return hashExecutor(0).invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return hashExecutor(0).invokeAll(tasks, timeout, unit);
    }
}
