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
public class ThreadHashExecutor {

    private final AtomicInteger index = new AtomicInteger();

    private final ExecutorService[] executors;

    public ThreadHashExecutor(int size) {
        ExecutorService[] array = new ExecutorService[size];
        final AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < array.length; i++) {
            array[i] = Executors.newSingleThreadExecutor((Runnable r) -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("Redkale-HashThread-" + counter.incrementAndGet());
                return t;
            });
        }
        this.executors = array;
    }

    public void execute(int hash, Runnable command) {
        if (hash < 1) {
            this.executors[index.incrementAndGet() % this.executors.length].execute(command);
        } else {
            this.executors[hash % this.executors.length].execute(command);
        }
    }

    public void shutdown() {
        for (ExecutorService executor : this.executors) {
            executor.shutdown();
        }
    }

    public List<Runnable> shutdownNow() {
        List<Runnable> list = new ArrayList<>();
        for (ExecutorService executor : this.executors) {
            list.addAll(executor.shutdownNow());
        }
        return list;
    }

    public boolean isShutdown() {
        return this.executors[0].isShutdown();
    }
}
